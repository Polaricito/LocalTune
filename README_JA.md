<div align="center">

  <h1>ArchiveTune</h1>

  <p align="center">
    <a href="README.md">English</a> •
    <a href="README_JA.md">日本語</a>
  </p>

  <p align="center">
    <strong>Android 向けの完全オフライン音楽プレイヤー。</strong>
    <br />
    <em>音楽ファイルもライブラリも歌詞も、すべて端末の中に。</em>
  </p>

</div>

---

ArchiveTune は Kotlin と Jetpack Compose で作られたローカル音楽プレイヤーです。
端末に保存されている音楽ファイルを読み取り、Material 3 のモダンな
インターフェースでライブラリ、プレイリスト、プレイヤー、ウィジェット、
スリープタイマー、イコライザー、オフライン歌詞を提供します。

このアプリには **インターネット権限がありません**。YouTube Music、Spotify、
Last.fm、ListenBrainz、Discord などのオンラインサービスには接続しません。
アカウント、ダウンロード、リモートカタログも存在しません。

## 機能

- **ローカルライブラリ** – MediaStore から曲、アルバム、アーティストを読み込みます。
  増分スキャンとフォルダ選択に対応しています。
- **ローカルプレイリスト** – 作成、名前変更、並べ替え、非表示、削除ができます。
  プレイリストのカバーも端末内に保存されます。
- **再生** – メディアセッションによるバックグラウンド再生、キュー管理、
  シャッフル、リピート、クロスフェード、ラウドネス正規化、テンポ/ピッチ変更、
  スリープタイマー、システムイコライザーに対応しています。
- **完全オフラインの歌詞**:
  - 音声ファイルのタグに埋め込まれた歌詞を自動で取り込みます。
  - 曲の隣に `.lrc` ファイルがあればそれを使用します。
  - 同梱の whisper.cpp エンジンで、任意の曲を端末上で文字起こしできます。
- **一括歌詞生成** – 設定 →「ローカル歌詞」から、ライブラリ全体または
  プレイリスト単位で文字起こしできます。既存歌詞の上書きも選択可能です。
  曲は順番に処理され、進捗表示とキャンセルに対応しています。
- **`.lrc` ファイル** – 生成した歌詞は `Song.flac` や `Song.mp3` の隣に
  `Song.lrc` として保存されます（Android 10 以降は MediaStore、それ以前は
  直接ファイルに書き込みます）。
- **バックアップと復元** – ローカルデータベースと設定をエクスポート/インポートできます。
- **外観** – ダイナミックカラー、複数のプレイヤースタイルと背景、
  ジェスチャーとアニメーションの設定。
- **ホーム画面ウィジェットと AOD プレイヤー** – ローカル再生状態と連動します。

## 歌詞と whisper.cpp

whisper.cpp は Android NDK と CMake でソースからビルドされ、
`armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` の 4 ABI に対応します。
ネイティブライブラリ名は `libarchivetune_whisper.so` です。

量子化された多言語 `tiny` モデル（`ggml-tiny-q5_1.bin`、約 32 MB）を
`app/src/main/assets/whisper/` に同梱しています。初回使用時にアプリの
`noBackupFilesDir` へコピーされ、その後は遅延読み込みされます。
文字起こしは CPU で最大 4 スレッド、直列実行されます。

モデルと whisper.cpp のソースは MIT ライセンスです。全文は
`app/src/main/assets/whisper/LICENSE.txt` と
`app/src/main/cpp/whisper.cpp/LICENSE` にあります。

## 動作環境

- Android 8.0（API 26）以上。
- MediaStore から読み取れる音楽ファイル
  （Android 13 以降は `READ_MEDIA_AUDIO`、以前は `READ_EXTERNAL_STORAGE`）。
- Android 9 以下では `.lrc` の書き込みに限り
  `WRITE_EXTERNAL_STORAGE` を要求します。

## ビルド

必要なもの:

- JDK 21
- Android SDK（compile SDK 37）
- Android NDK `30.0.16248370`
- CMake `3.22.1`

デバッグ APK:

```bash
./gradlew :app:assembleGmsMobileUniversalDebug
```

universal バリアントは 4 ABI すべてをビルドします。より小さい
`arm64`、`armeabi`、`x86`、`x86_64` バリアントも利用できます。

署名付きリリース APK:

```bash
./gradlew :app:assembleGmsMobileUniversalRelease
```

リリースと nightly はリポジトリに含まれる自己署名キー
`app/keystore/release.keystore` を使用します（エイリアス、キーパスワード、
ストアパスワードはすべて `archivetune`）。同じキーをリポジトリに置くことで
すべてのビルドが同じ署名になり、既存のインストールを上書き更新できます。
このキーは秘密情報ではないため、秘密として扱わないでください。

## プロジェクト構成

- `app/src/main/kotlin/moe/rukamori/archivetune/` – アプリ本体。
- `app/src/main/cpp/` – CMake、JNI ブリッジ、whisper.cpp のソース。
- `app/src/main/assets/whisper/` – 同梱モデルとライセンス。
- `app/src/main/res/` – リソースと翻訳。
- `fastlane/` – ストア用メタデータ。

## プライバシー

- `INTERNET` 権限を宣言していません。
- 分析、クラッシュレポート、広告、トラッキング SDK は含まれていません。
- アカウント機能やリモート同期はありません。
- 歌詞、プレイリスト、再生履歴、設定は端末内のデータベースとファイルに
  保存されます。

## ライセンス

ArchiveTune は **GNU General Public License v3.0** で公開されています。
全文は [`LICENSE`](LICENSE) を参照してください。

同梱コンポーネント:

- whisper.cpp と ggml – MIT License、著作権は ggml authors に帰属します。
- Whisper `tiny` モデル – MIT License、OpenAI。

ArchiveTune の名称、ロゴ、アプリアイコンは GPLv3 の対象外です。
非公式フォークは、公式リリースであるかのように表示したり、メンテナーによる
承認を暗示したりしてはいけません。

このプロジェクトに公式の Google Play 掲載はありません。非公式の配布元から
入手したビルドはサポートされません。
