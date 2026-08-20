# デバッグ記録: `Map.merge`のnull返却で在庫ゼロのSKUが削除される

## 実行環境と再現境界

このラボはJava 21、Maven、JUnit Jupiter 5.11.4で実行します。外部I/O、時刻、乱数、並行実行を使わず、固定の文字列・整数とインメモリ`HashMap`だけで再現するため、実行結果は決定的です。

| 境界 | 内容 |
| --- | --- |
| Arrange | `StockLedger`へ`tea -> 5`を登録する。 |
| Act | 公開メソッド`applyAdjustment("tea", -5)`を一回だけ呼び出す。 |
| Assert | 戻り値が`ADJUSTED_TO_ZERO`であることを確認する。 |
| Observe | `quantityOf`、`isTracked`、`trackedSkuCount`で最終Map状態を独立に確認する。 |

対象は`Map.merge`のremapping関数が返す`null`の意味だけです。負在庫、未知SKU、永続化、並行更新、SKU正規化は再現境界に含めません。

## 最初に観測した事実

バグコミット[`f9dd8e1`](../../commit/f9dd8e1)で次を実行すると、`StockLedgerTest`は意図したアサーション差分で失敗します。

```bash
git switch --detach f9dd8e1
mvn --batch-mode test -Dtest=StockLedgerTest
git switch main
```

| 観測点 | 期待 | バグ状態の実測 |
| --- | --- | --- |
| 調整結果 | `ADJUSTED_TO_ZERO` | `REMOVED_FROM_TRACKING` |
| `quantityOf("tea")` | `0` | `-1` |
| `isTracked("tea")` | `true` | `false` |
| `trackedSkuCount()` | `1` | `0` |

完全な失敗出力は[`evidence/01-bug-service-test-output.txt`](../evidence/01-bug-service-test-output.txt)に保存しています。戻り値だけでなく、在庫量、キーの存在、Mapの件数が同時に壊れていることが分かります。

## 競合仮説と検証

公開APIの失敗を、最小の検証で比較しました。

| 仮説 | 最小の検証 | 結果 | 判断 |
| --- | --- | --- | --- |
| 初期在庫が登録されていない | `recordInitialQuantity`の実装と登録直後の状態を追跡する | `put`で`tea -> 5`を保存している | 棄却 |
| 加減算の向きが逆 | remapping関数の計算式を確認する | `5 + (-5) = 0` | 棄却 |
| `null`が値0として保存されるという誤認 | `Map`だけを使う直接観測テストを実行する | `merge`の戻り値は`null`、`containsKey("tea")`はfalse、件数は0 | 採用 |

[`MapMergeNullObservationTest`](../src/test/java/jp/tonbiattack/debuglab/inventory/MapMergeNullObservationTest.java)はサービスの分岐を通さず、`Map.merge`の振る舞いを直接観測します。バグ状態でもこのテストは成功し、出力は[`evidence/02-map-merge-null-observation-output.txt`](../evidence/02-map-merge-null-observation-output.txt)にあります。

## 確定した原因

`Map.merge(key, value, remappingFunction)`は、既存値と指定値から次の値を計算します。remapping関数が`null`を返す場合、`Map`はそのキーのマッピングを削除します。[1] 本件では、ドメイン上の「在庫量ゼロ」を表したい実装が`null`を返し、API仕様上の「削除」を要求していました。

> “If the remapping function returns `null`, the mapping is removed.” — Java SE 21 `Map#merge`仕様 [1]

この規則は`HashMap`固有の偶然ではなく、`Map`インターフェースの`merge`契約です。したがって、呼出し側で欠損を補うのではなく、remapping関数の戻り値をドメイン値`0`へ修正します。

## 最小修正

修正は`StockLedger#applyAdjustment`のremapping関数だけです。合計0のときに`null`を返す条件を削除し、計算した`int`をそのまま返します。`0`は`Integer`としてMapに保存されるため、SKUキーは残ります。

```diff
 quantities.merge(sku, adjustment, (current, delta) -> {
     int total = current + delta;
-    return total == 0 ? null : total;
+    return total;
 });
```

最小修正はコミット[`2b516f8`](../../commit/2b516f8)にあります。削除が必要な別ユースケースをこの条件式へ混在させず、削除の意図がある場合には別の明示的な操作として設計することは、このラボの範囲外です。

## 回帰保証

修正済みの`main`で次を実行すると、契約テストと直接観測テストがともに成功します。

```bash
mvn --batch-mode clean test
```

全出力は[`evidence/03-fixed-full-test-output.txt`](../evidence/03-fixed-full-test-output.txt)に保存しています。コミット[`2b516f8`](../../commit/2b516f8)以降は同じ公開契約が成功し、バグコミットとの比較が可能です。

### 再発防止テスト

`StockLedgerTest#adjustmentToZero_keepsSkuTrackedAtZero`は、調整の戻り値だけでなく、保存量、キー存在、Map件数を別々に検証します。将来、戻り値だけを取り繕ってキーを消したままにする不完全な修正を検出します。

`MapMergeNullObservationTest#nullFromRemappingFunctionRemovesTheSkuMapping`は、原因となるJDK契約を小さく固定します。`null`を値0の代用品にできないという設計判断を、実行可能な証拠として残すテストです。

## 再現手順

修正済み状態は、リポジトリ直下で`mvn --batch-mode clean test`を実行して確認します。バグ状態を確認する場合は、前述のとおり`f9dd8e1`へ一時的に切り替えて対象テストだけを実行し、直後に`main`へ戻します。未コミット変更がある作業ツリーで`git switch`を実行しないでください。

## スコープと注意点

このラボは「数量ゼロでもSKU追跡を続ける」というドメイン契約を前提にします。実際のシステムでゼロ在庫を削除したいなら、`null`返却が正しいこともあります。その場合は、キー削除が望ましいことを別の契約テストで明示してください。ここでの修正を、全ての`Map.merge`利用箇所へ機械的に適用するべきだとは主張しません。

## References

[1]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#merge(K,V,java.util.function.BiFunction) "Java SE 21 API: Map#merge"
