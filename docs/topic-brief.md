# 題材企画: `Map.merge`でnullを返して在庫ゼロの追跡エントリを失う

## 対象

| 項目 | 内容 |
| --- | --- |
| 対象言語 | Java 21 |
| 対象読者 | `Map.merge`を在庫の加減算や集計に使い、ゼロ値とマッピング削除の規則を切り分けたい中級者 |
| 難易度プロファイル | 実践・上級 |
| 選定理由 | `Map.merge`のremapping関数がnullを返すと、値nullを保存するのではなく、マッピング自体を削除する。数量ゼロでもSKUを追跡する契約でnullを返すと、在庫量だけでなく追跡キー・SKU件数が消える。公開APIの結果と最終Map状態を分離して観測できる。 |
| 実行基盤 | Maven、Java 21、JUnit Jupiter 5.11.4 |
| フレームワーク非依存性 | 原因は`java.util.Map`の標準ライブラリ契約である。HTTP、DB、ORM、外部サービスには依存しない。 |

## 学習する契約

> 初期在庫`tea -> 5`に調整量`-5`を適用する場合、`tea`の在庫量を`0`に更新し、追跡対象としてのキー`tea`と追跡SKU件数`1`を維持すべきだが、バグ状態ではremapping関数がnullを返し、`tea`のマッピングと追跡件数が消える。

### 対象の直接原因

`Map.merge`のremapping関数が合計0の場合に`null`を返している。`merge`はnullを値として保存せず、該当キーのマッピングを削除する。

### 対象外

このラボは在庫の永続化、負在庫ポリシー、SKU正規化、並行更新、分散ロック、外部入庫通知を扱わない。固定の`HashMap`に一つのSKUと一つの調整量を適用する狭い規則だけを扱う。

## 再現設計

| 要素 | 決定 |
| --- | --- |
| 公開境界 | `StockLedger#applyAdjustment(String, int)`、`quantityOf(String)`、`isTracked(String)`、`trackedSkuCount()`。 |
| 入力・初期状態 | `tea -> 5`を登録し、`-5`を一回だけ適用する。 |
| Redの観測 | `StockAdjustmentOutcome.ADJUSTED_TO_ZERO`を期待するが、バグ状態では`StockAdjustmentOutcome.REMOVED_FROM_TRACKING`となる。 |
| 最終観測 | `quantityOf("tea")`が`0`、`isTracked("tea")`がtrue、`trackedSkuCount()`が`1`であることを別々に検証する。 |
| 決定性 | 時刻、乱数、並行実行、`sleep`、外部I/Oを使わず、固定の文字列・整数・インメモリMapだけを使う。 |
| 固定状態の検証コマンド | `mvn --batch-mode clean test` |
| バグ状態の確認コマンド | `git checkout <bug-commit>`後に`mvn --batch-mode test -Dtest=StockLedgerTest` |

## 仮説

| 仮説 | どう検証または除外するか |
| --- | --- |
| A: 初期在庫がMapへ保存されていない | 調整前の`quantityOf`、`isTracked`、SKU件数を確認する。 |
| B: 調整数値の加算・減算方向が逆 | remapping関数の入力値と合計を直接観測する。 |
| C: 合計0を表すnullがMapの値でなく削除指示になる | `Map.merge`後の`containsKey`と値を直接観測する。 |

## 予定する履歴

| 順序 | コミットの目的 | 期待する状態 |
| --- | --- | --- |
| 1 | 在庫ゼロのSKUが追跡から消える失敗を再現する | 対象テストが`ADJUSTED_TO_ZERO`期待・`REMOVED_FROM_TRACKING`実際のアサーション差分で失敗する。 |
| 2 | 在庫ゼロを整数0として保持する | 同じ検証が成功し、全体も成功する。 |
