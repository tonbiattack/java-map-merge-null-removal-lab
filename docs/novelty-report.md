# 新規性レポート: `Map.merge`のnull返却によるゼロ在庫SKU削除

## 結論

本ラボは、Java標準ライブラリの`Map.merge`で**remapping関数が`null`を返すとマッピングが削除される**規則を、在庫ゼロのSKU追跡という公開契約の破綻として再現します。先行するJavaラボにある`Map.getOrDefault`の明示的`null`マッピング、`Collectors.toMap`の重複キー、`List.remove`のオーバーロードなどとは、対象API、`null`の意味、失敗する操作、最終状態のいずれも異なります。[1]

## 監査方法

2026-08-20に`/home/ubuntu/qiita`配下のMarkdownを対象として、`Map.merge`、`map merge`、`merge.*null`、`null.*merge`、`在庫.*ゼロ`、`ゼロ.*在庫`を大小文字を区別して検索しました。また、ホームディレクトリ直下の`java-*lab`を列挙し、先行するJava教材の題材を確認しました。規定の`/home/ubuntu/repository-catalog`はこの環境に存在しなかったため、カタログ更新・検証およびカタログ用の語彙スクリーニングは実行できませんでした。

| 監査対象 | 確認結果 | 本ラボへの影響 |
| --- | --- | --- |
| Qiita原稿の`Map.merge`・null返却 | 該当なし | 同じJava API契約を主題にする既存原稿は確認されなかった。 |
| Qiita原稿の在庫ゼロ | PHPの`empty("0")`、要件定義上の在庫ゼロなどの別言語・別論点のみ | Javaの`Map.merge`がキーを削除する再現とは重複しない。 |
| 先行Java教材 | `Map.getOrDefault`、`Collectors.toMap`、`PriorityQueue`、`String.split`、`URI.resolve`、`URLDecoder`、`Scanner`、`List.remove`、正規表現置換を確認 | 各教材と失敗トリガーと修正規則が異なる。 |
| Repository Catalog | `/home/ubuntu/repository-catalog`が存在しない | カタログ未記載のローカル専用教材は、この調査では検出できない制約がある。 |

この検索は題材名・API名・症状語に基づく重複確認です。Repository Catalog不在のため、カタログ外のローカル専用リポジトリは確認対象外です。さらに、公開済み記事の文章量、閲覧数、外部Webの記事との類似性を判定するものではありません。

## 四軸比較

| 比較対象 | API・言語機構 | 根本原因 | 再現入力と観測 | 教材境界 | 重複しない理由 |
| --- | --- | --- | --- | --- | --- |
| 本ラボ | `Map.merge`のremapping関数 | `null`が保存値でなく削除指示になる | `tea -> 5`へ`-5`。在庫量、キー存在、SKU件数が同時に失われる | ゼロ在庫を保持するインメモリ台帳 | 基準 |
| `java-map-getordefault-null-lab` | `Map.getOrDefault` | 明示的`null`マッピングと既定値の区別 | `getOrDefault`の返値 | 参照時の既定値選択 | 本ラボは更新時の`merge`でキーが削除される。 |
| `java-collectors-tomap-duplicate-key-lab` | `Collectors.toMap` | マージ関数未指定で重複キー例外 | 同一SKUを含むストリーム収集 | コレクションからMapを構築する段階 | 本ラボは既存Mapの合成結果が0のときの削除であり、例外や重複キーを扱わない。 |
| `java-list-remove-integer-overload-lab` | `List<Integer>#remove` | `int`が値ではなく添字のオーバーロードを選ぶ | 削除後の要素列 | オーバーロード解決 | 本ラボは関数の戻り値規則であり、オーバーロードを扱わない。 |
| `java-priorityqueue-iteration-order-lab` | `PriorityQueue`の反復 | 反復順と取り出し優先順の混同 | 配信順序 | 順序保証 | 本ラボはMapの存在状態と数量保持であり、順序を扱わない。 |
| `java-string-split-trailing-empty-lab` | `String.split` | 末尾空列の既定除去 | CSV末尾列 | 文字列分割 | 本ラボは整数集計とMap更新であり、文字列解析を扱わない。 |
| `java-regex-replacement-literal-lab` | `String.replaceAll` | `$`を置換文字列でグループ参照として解釈 | 金額テンプレート | 正規表現置換 | 本ラボはMap更新時のnull契約であり、正規表現を扱わない。 |
| `java-uri-resolve-leading-slash-lab` | `URI.resolve` | 先頭`/`で基底パスを置換 | APIバージョンのパス | URI解決 | 本ラボはURI構文ではなく、キーのライフサイクルを扱う。 |
| `java-urldecoder-plus-token-lab` | `URLDecoder` | `+`を空白として扱うフォーム規則 | 不透明トークン照合 | URL形式復号 | 本ラボはエンコード規則ではなくMapの削除規則を扱う。 |
| `java-scanner-nextline-newline-lab` | `Scanner` | 数値読取後の改行が残る | 次行入力 | 標準入力ストリーム | 本ラボは入力カーソルではなく、Mapの状態遷移を扱う。 |
| `spring-webhook-record-array-dedup-lab` | Java `record`と`byte[]`、Spring MVC | 配列の参照比較 | 重複Webhook受理 | HTTP入力と等値性 | 本ラボはHTTP・配列・recordを使わず、JDK Mapの更新契約だけを扱う。 |

## PHPの在庫ゼロ題材との差分

Qiitaリポジトリには、PHPの`empty()`が文字列`"0"`を未入力として扱う数量更新の原稿がありました。これは同じ「ゼロを扱う」語彙を含みますが、言語・API・表現・失敗モードが異なります。

| 軸 | PHP `empty("0")`題材 | 本ラボ |
| --- | --- | --- |
| 言語・API | PHPの入力検証関数 | Java `Map.merge` |
| ゼロの表現 | 文字列`"0"` | 整数`0`と`null` |
| 失敗モード | 値が入力不足として拒否される | SKUキーのマッピングが削除される |
| 回帰観測 | APIの入力受理 | 調整結果、在庫量、キー存在、Map件数 |

したがって、本ラボはゼロをテーマにした既存原稿の言い換えではなく、**更新APIにおける`null`の制御的意味**を扱う別のデバッグ教材です。

## 採用判断

このラボは、対象を`Map.merge`の一つの戻り値規則に限定しながら、公開APIの戻り値と最終Map状態を分けて観測します。類似語である「null」「Map」「在庫ゼロ」を含む既存題材と混同しやすい一方、根本原因と最小修正は独自です。したがって、既存Qiita記事および先行十件のJava/Spring教材と重複しない追加題材として採用します。

## 参考文献

[1]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#merge(K,V,java.util.function.BiFunction) "Java SE 21 API: Map#merge"
