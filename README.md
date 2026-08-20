# `Map.merge` が在庫ゼロのSKU追跡を消すデバッグラボ

Java標準ライブラリの`Map.merge`を使った在庫調整で、合計が0になったSKUを**追跡対象として残す**べきなのに、remapping関数が`null`を返すためキーごと削除してしまう不具合を再現・修正する教材です。Java SE 21の`Map.merge`は、remapping関数が`null`を返した場合にそのキーのマッピングを削除します。[1]

| 項目 | 内容 |
| --- | --- |
| 対象 | Java 21、Maven、JUnit Jupiter 5.11.4 |
| 原因API | `java.util.Map#merge` |
| バグコミット | [`f9dd8e1`](../../commit/f9dd8e1) — 在庫ゼロのSKUが追跡から消える失敗を再現する |
| 修正コミット | [`2b516f8`](../../commit/2b516f8) — 在庫ゼロを整数0として保持する |
| 外部I/O | 不使用。固定の文字列・整数・インメモリ`HashMap`だけを使う決定的な再現です。 |

## この題材で守る契約

初期在庫`tea -> 5`に調整量`-5`を適用した後、台帳は`tea -> 0`を保存し、`tea`を追跡対象として保持しなければなりません。調整結果は`ADJUSTED_TO_ZERO`、在庫量は`0`、追跡有無は`true`、追跡SKU件数は`1`です。

| 観測点 | 正しい状態 | バグ状態 |
| --- | --- | --- |
| `applyAdjustment("tea", -5)` | `ADJUSTED_TO_ZERO` | `REMOVED_FROM_TRACKING` |
| `quantityOf("tea")` | `0` | `-1`（未追跡を表すラボ上の値） |
| `isTracked("tea")` | `true` | `false` |
| `trackedSkuCount()` | `1` | `0` |

## 最短の開始手順

修正済みの`main`で全テストを実行するには、次の一行を使います。

```bash
mvn --batch-mode clean test
```

## バグを再現する

この節は**意図した失敗**を確認する手順です。作業ツリーに未コミット変更がない場所で実行してください。バグコミットへ移動した後は、必ず`main`へ戻します。

```bash
git switch --detach f9dd8e1
mvn --batch-mode test -Dtest=StockLedgerTest
git switch main
```

`StockLedgerTest`は四つの観測点を同時に検証するため、バグコミットでは一つの失敗として四つのアサーション差分を報告します。実際の出力は[`evidence/01-bug-service-test-output.txt`](evidence/01-bug-service-test-output.txt)に保存しています。なお、直接観測テスト`MapMergeNullObservationTest`はバグコミットでも成功し、`null`の返却が`Map`からキーを取り除くことを確認します。出力は[`evidence/02-map-merge-null-observation-output.txt`](evidence/02-map-merge-null-observation-output.txt)です。

## 修正を確認する

`main`上で次のコマンドを実行します。

```bash
mvn --batch-mode clean test
```

二つのテストが成功します。`StockLedgerTest`は公開台帳の契約を、`MapMergeNullObservationTest`は原因となる標準APIの規則を固定します。修正状態での全テスト出力は[`evidence/03-fixed-full-test-output.txt`](evidence/03-fixed-full-test-output.txt)です。

## 原因と最小修正

バグ状態では、remapping関数が合計0を表すために`null`を返します。しかし`Map.merge`における`null`は「値0」の表現ではなく「マッピングを削除する」という制御信号です。[1]

```java
// バグ状態
return total == 0 ? null : total;

// 修正状態
return total;
```

`Integer`の`0`を返すと、`tea`のマッピングは残り、ゼロ在庫と追跡継続というドメイン契約を同時に満たします。実装詳細、仮説の切り分け、回帰保証は[デバッグ記録](docs/debugging-record.md)を参照してください。既存Qiita原稿・先行ラボとの比較は[新規性レポート](docs/novelty-report.md)に記録しています。

## プロジェクト構成

| パス | 役割 |
| --- | --- |
| `src/main/java/.../StockLedger.java` | バグと最小修正を含む在庫台帳 |
| `src/test/java/.../StockLedgerTest.java` | 公開契約を固定する回帰テスト |
| `src/test/java/.../MapMergeNullObservationTest.java` | `Map.merge`の削除規則を直接観測するテスト |
| `evidence/` | バグ状態・観測・修正状態のMaven出力 |
| `docs/topic-brief.md` | 題材境界、仮説、再現設計 |
| `docs/debugging-record.md` | 調査と最小修正の記録 |
| `docs/novelty-report.md` | 既存コンテンツとの差分記録 |

## References

[1]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#merge(K,V,java.util.function.BiFunction) "Java SE 21 API: Map#merge"
