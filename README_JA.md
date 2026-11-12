<div align="center">

  <h1>LocalTune</h1>

  <p align="center">
    <a href="README.md">English</a> •
    <a href="README_JA.md">日本語</a>
  </p>

  <p align="center">
    <strong>Android 向けのローカルファースト音楽プレイヤー。</strong>
    <br />
    <em>音楽ファイルもライブラリも歌詞も端末の中に。オンライン機能は必要なときだけ。</em>
  </p>

</div>

---

LocalTune は Kotlin と Jetpack Compose で作られた、Android 向けの
ローカルファースト音楽プレイヤーです。端末に保存されている音楽ファイルを
読み取り、Material 3 のモダンなインターフェースでライブラリ、プレイリスト、
プレイヤー、ウィジェット、スリープタイマー、イコライザー、オンデバイス歌詞を
提供します。

LocalTune は rukamori による
[ArchiveTune](https://github.com/rukamori/ArchiveTune) のフォークです。
ローカル再生、ライブラリ、歌詞生成はオフラインで動作します。オプションで
[LRCLIB](https://lrclib.net) の歌詞検索と
[Deezer](https://www.deezer.com) のアーティスト情報を利用できます。どちらも
設定からオフにできます。

## 機能

- **ローカルライブラリ** – MediaStore から曲、アルバム、アーティストを
  読み取ります。デフォルトでは Music フォルダ
  (`/storage/emulated/0/Music`) が対象です。追加フォルダの指定や除外、
  最小曲長の設定ができます。アーティスト一覧には **Subscribed Only**
  フィルターがあります。
- **ローカルプレイリスト** – 作成、名前変更、並べ替え、非表示、削除が
  できます。プレイリストのカバーは端末内に保存されます。
- **再生** – メディアセッションとメディア通知を使ったバックグラウンド再生、
  キュー管理、シャッフル、リピート、クロスフェード、ラウドネス正規化、
  テンポ/ピッチ調整、スリープタイマー、システムイコライザー連携。
- **キューとバックグラウンド再生の保持** – アプリを最近使ったアプリ一覧から
  削除しても再生を継続し、次回起動時に最後のキューを復元します。
  設定 → プレイヤーには逆の動作にする **Stop music on task clear**
  スイッチもあります。
- **歌詞**:
  - 音声ファイルのタグに埋め込まれた歌詞を自動的に読み込みます。
  - 曲の隣に `.lrc` ファイルがあればそれを使用します。
  - 同梱の whisper.cpp エンジンで、任意の曲をオンデバイス文字起こしできます。
  - プレーンテキストの歌詞は **Sync with Whisper** で行単位に同期できます。
  - 歌詞メニューから LRCLIB のオンライン検索も利用できます（オプション）。
- **きれいな歌詞表示** – 行同期 LRC はプレーンテキストとして表示され、
  現在の行がハイライトされます。拡張 LRC の単語タイムスタンプ
  (`<mm:ss.xx>`) や先頭の音声ラベル (`v1:`) は表示時に除去されます。
- **一括歌詞生成** – 設定 → ローカル歌詞から、ライブラリ全体または
  プレイリスト単位で文字起こしできます。既存の歌詞を上書きするかどうかも
  選べます。曲は順番に処理され、進捗表示とキャンセルに対応します。
- **`.lrc` サイドカー** – 生成した歌詞は `Song.flac`、`Song.mp3`、
  `Song.m4a` などの隣に `Song.lrc` として保存されます（Android 10 以降は
  MediaStore、それ以前はファイルに直接書き込みます）。
- **アーティスト情報（オプション）** – オンラインアーティスト情報が有効な
  場合、アーティスト画面に Deezer の写真と最新リリースを表示します。
- **バックアップと復元** – ローカルデータベースと設定をエクスポート/インポート
  できます。
- **外観** – ダイナミックカラー、複数のプレイヤースタイルと背景、ジェスチャー
  とアニメーションの設定。
- **ホーム画面ウィジェットと AOD プレイヤー** – ローカル再生状態と連動します。

## オンライン機能（オプション）

通常の利用にアカウントやネットワーク接続は必要ありません。アプリが
`INTERNET` 権限を宣言するのは次の 2 つの機能だけで、どちらも
設定 → プレイヤーから無効にできます。

- **オンライン歌詞 (LRCLIB)** – API キー不要の歌詞データベースです。
  歌詞メニューでオンライン検索を選んだときだけ問い合わせ、曲名、アーティスト、
  アルバム（または結合した検索クエリ）を送信します。取得した歌詞は
  ローカルデータベースにキャッシュされます。
- **オンラインアーティスト情報 (Deezer)** – API キー不要のアーティスト API
  です。アーティスト画面を開いたときにアーティスト名を送信し、写真と
  リリース情報を取得します。

音声データは送信されません。アカウント、広告、分析、クラッシュレポート SDK は
ありません。詳細は [`PRIVACY.md`](PRIVACY.md) を参照してください。

## 歌詞と whisper.cpp

whisper.cpp は Android NDK と CMake でソースからビルドされ、
`armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` の 4 ABI に対応します。
ネイティブライブラリ名は `libarchivetune_whisper.so` です。

量子化された多言語 `tiny` モデル (`ggml-tiny-q5_1.bin`、約 32 MB) が
`app/src/main/assets/whisper/` に同梱されています。初回使用時にアプリの
`noBackupFilesDir` にコピーされ、遅延ロードされます。文字起こしは CPU で
最大 4 スレッド、直列に実行されます。

**文字起こし** は曲からプレーンテキストの歌詞を生成します。
**Sync with Whisper** は既存のプレーンテキスト歌詞と音声のタイミングを
照合し、行単位で同期します。タイムスタンプは検出された音声の長さの範囲内に
収められ、一致度が低い場合は曲全体に均等に分散されます。

モデルと whisper.cpp のソースは MIT ライセンスです。ライセンス全文は
`app/src/main/assets/whisper/LICENSE.txt` と
`app/src/main/cpp/whisper.cpp/LICENSE` にあります。

## 動作環境

- Android 8.0 (API 26) 以降。
- MediaStore から読み取れる音声ファイル（Android 13 以降は
  `READ_MEDIA_AUDIO`、それ以前は `READ_EXTERNAL_STORAGE`）。
- Android 11 以降では、音声ファイルの隣に `.lrc` を書き込むために
  **すべてのファイルへのアクセス** (`MANAGE_EXTERNAL_STORAGE`) を要求します。
  Android 9 以前では `WRITE_EXTERNAL_STORAGE` を使用します。
- ネットワーク接続は、オプションの LRCLIB 歌詞検索と Deezer アーティスト情報を
  使う場合のみ必要です。
- `POST_NOTIFICATIONS` は再生通知に、フォアグラウンドサービス権限は
  バックグラウンド再生の維持に使用します。

## ビルド

必要なもの:

- JDK 21 以降（CI では JDK 27）
- Android SDK（compile SDK 37）
- Android NDK `30.0.16248370`
- CMake `3.22.1`

デバッグ APK:

```bash
./gradlew :app:assembleGmsMobileUniversalDebug
```

ユニバーサル variant は 4 ABI すべてをビルドします。`arm64`、`armeabi`、
`x86`、`x86_64` の個別 variant や、`foss`、TV 向け variant もあります。
出力は `app/build/outputs/apk/` 以下に書き出されます。

署名済みリリース APK:

```bash
./gradlew :app:assembleGmsMobileUniversalRelease
```

リリースビルドは、リポジトリにコミットされている自己署名キー
`app/keystore/release.keystore` を使用します（alias、key password、
store password はすべて `archivetune`）。同じキーをリポジトリに置くことで
すべてのビルドが同じ署名になり、既存のインストールを上書き更新できます。
このキーは秘密情報ではなく、そのように扱わないでください。

GitHub Actions は push のたびにすべての APK variant をビルドします。
`app/build.gradle.kts` の `versionName` が変わると、リリースワークフローが
コミットにタグを付け、APK を
[リリースページ](https://github.com/Polaricito/LocalTune/releases) に
公開します。

## プロジェクト構成

- `app/src/main/kotlin/moe/rukamori/archivetune/` – アプリコード。
  - `localmedia/` – MediaStore スキャンとフォルダフィルター。
  - `playback/` – プレイヤーサービス、キュー永続化、バックグラウンド再生。
  - `lyrics/` – 歌詞解析、`.lrc` サイドカー、whisper 文字起こしと同期、
    オプションの LRCLIB 検索。
  - `artist/` – オプションの Deezer アーティスト情報。
- `app/src/main/cpp/` – CMake ラッパー、JNI ブリッジ、同梱の whisper.cpp。
- `app/src/main/assets/whisper/` – 同梱モデルとライセンス。
- `app/src/main/res/` – リソースと翻訳。
- `fastlane/` – ストアメタデータ。

## プライバシー

- LocalTune はローカルファーストです。ライブラリデータ、プレイリスト、
  再生履歴、歌詞、設定はアプリのローカルデータベースとファイルに保存されます。
- ネットワーク通信は、前述のオプションの LRCLIB 歌詞検索と Deezer
  アーティスト情報のみです。設定 → プレイヤーから無効にできます。
- 分析、クラッシュレポート、広告、トラッキング SDK は含まれていません。
- アカウントシステムやリモート同期はありません。
- アプリのデータベースと設定は保存時に暗号化されません。Android の
  ファイルベース暗号化と端末ロックがプラットフォームレベルの保護を提供します。

## ArchiveTune のフォーク

LocalTune は rukamori による
[ArchiveTune](https://github.com/rukamori/ArchiveTune) のフォークです。
元のアプリ、デザイン、コードベースのクレジットは ArchiveTune プロジェクトと
その貢献者に帰属します。

既存のインストールを上書き更新できるように、アプリケーション ID
(`moe.rukamori.archivetune`) とリリース署名キーは上流から引き継いでいます。
アプリ名とユーザーインターフェースは LocalTune です。

## ライセンス

LocalTune は ArchiveTune から引き継いだ **GNU General Public License v3.0**
でライセンスされています。完全な条件は [`LICENSE`](LICENSE) を参照して
ください。

同梱コンポーネント:

- whisper.cpp と ggml – MIT License、ggml 作者に帰属。
- Whisper `tiny` モデル – MIT License、OpenAI。

ArchiveTune の名称、ロゴ、アプリアイコンは GPLv3 ではライセンスされていません。
LocalTune は非公式のフォークであり、ArchiveTune のメンテナーによる承認を
意味するものではありません。
