# デバッグ記録: `Map.merge`のnull返却で在庫ゼロのSKUが削除される

## 事象と期待する契約

`StockLedger`はSKUごとの在庫量をインメモリ`Map`で追跡します。初期状態`tea -> 5`に`-5`を適用したとき、ゼロ在庫を明示しながらSKU追跡を継続することが業務契約です。ところがバグ状態では、`tea`のキーそのものが台帳から消えます。

| 観測点 | 期待 | バグ状態の実測 |
| --- | --- | --- |
| 調整結果 | `ADJUSTED_TO_ZERO` | `REMOVED_FROM_TRACKING` |
| `quantityOf("tea")` | `0` | `-1` |
| `isTracked("tea")` | `true` | `false` |
| `trackedSkuCount()` | `1` | `0` |

バグ状態の実行は、コミット[`f9dd8e1`](../../commit/f9dd8e1)で`mvn --batch-mode test -Dtest=StockLedgerTest`を実行して確認できます。出力全体は[`evidence/01-bug-service-test-output.txt`](../evidence/01-bug-service-test-output.txt)です。四つの観測点を同時に検証することで、単に戻り値が誤っているのではなく、量・キー存在・件数という状態全体が壊れていることを示します。

## 観測と仮説の切り分け

調査では最初に、公開APIの失敗を副作用の原因候補へ分解しました。

| 仮説 | 最小の検証 | 結果 | 判断 |
| --- | --- | --- | --- |
| 初期在庫が登録されていない | 調整後ではなく`recordInitialQuantity`直後の台帳状態をコードで追跡する | `put`で`tea -> 5`を登録している | 棄却 |
| 加減算の向きが逆 | remapping関数で`current + delta`を確認する | `5 + (-5) = 0` | 棄却 |
| `null`が値0として保存されるという誤認 | `Map`だけを使う直接観測テストを実行する | `merge`の戻り値は`null`、`containsKey("tea")`はfalse、件数は0 | 採用 |

直接観測は[`MapMergeNullObservationTest`](../src/test/java/jp/tonbiattack/debuglab/inventory/MapMergeNullObservationTest.java)に分離しています。これはサービス層の分岐や`quantityOf`の既定値を経由せず、標準ライブラリの契約だけを確認するためです。バグ状態でこの観測テストが成功する出力は[`evidence/02-map-merge-null-observation-output.txt`](../evidence/02-map-merge-null-observation-output.txt)にあります。

## 根本原因

`Map.merge(key, value, remappingFunction)`は、既存の非null値と引数値から新しい値を計算します。remapping関数が`null`を返す場合、`Map`はそのキーのマッピングを削除します。[1] 本件では、ドメイン上の「在庫量ゼロ」を表したい実装が`null`を返したため、API仕様上の「削除」を要求してしまいました。

> “If the remapping function returns `null`, the mapping is removed.” — Java SE 21 `Map#merge`仕様 [1]

この仕様は`HashMap`に固有の偶然の挙動ではなく、`Map`インターフェースの`merge`契約です。そのため、`HashMap`の実装詳細を変更したり、呼出し側で欠損を補ったりして直すのではなく、remapping関数の戻り値をドメイン値`0`に修正するのが適切です。

## 最小修正

修正は`StockLedger#applyAdjustment`のremapping関数だけです。条件分岐で`null`を返す処理をやめ、計算した`int`をそのまま返します。`0`は`Integer`として箱詰めされ、`Map`の値として保存されるため、SKUキーは残ります。

```diff
 quantities.merge(sku, adjustment, (current, delta) -> {
     int total = current + delta;
-    return total == 0 ? null : total;
+    return total;
 });
```

この修正はコミット[`2b516f8`](../../commit/2b516f8)に保存しています。削除を意味する`null`を返す必要がある別の契約は、このラボの対象外です。意図してSKUを追跡対象から外すなら、`Map.remove`など、削除の意図が明示された操作を別のユースケースで選ぶべきです。

## 回帰保証

修正後は、クリーンなMaven実行で二つのテストが成功します。

```bash
mvn --batch-mode clean test
```

実行結果は[`evidence/03-fixed-full-test-output.txt`](../evidence/03-fixed-full-test-output.txt)に保存しています。環境や時刻、並行実行、ネットワークを使わないため、同一ソースとJava 21環境では決定的に再現できます。

### 再発防止テスト

`StockLedgerTest#adjustmentToZero_keepsSkuTrackedAtZero`は、調整の戻り値だけでなく、保存量、キー存在、Mapの件数を独立に検証します。これにより、将来「戻り値だけ0向けに直してMapのキーは消える」ような不完全な修正を通過させません。

一方、`MapMergeNullObservationTest#nullFromRemappingFunctionRemovesTheSkuMapping`は、原因となるJDK契約を小さく固定します。これは「`null`を値0の代用品にできない」という設計判断の根拠を、実行可能な形で残すテストです。

## 対象外

このラボは負在庫の可否、未知SKUへの調整、永続化、並行更新、SKU正規化、外部通知を扱いません。いずれも別の契約・原因・テスト設計を必要とし、`Map.merge`のnull返却という単一原因から注意をそらすためです。

## 参考文献

[1]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#merge(K,V,java.util.function.BiFunction) "Java SE 21 API: Map#merge"
