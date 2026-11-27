<div align="center">

# Amber

**A native music client for iPhone, iPad and Android.**<br>
YouTube Music, your own library, DJ-style Automix and Hi-Res playback.

[![License](https://img.shields.io/badge/license-GPL--3.0-E8743B?style=for-the-badge)](LICENSE)
[![Platforms](https://img.shields.io/badge/iOS_18+_·_Android-F5263F?style=for-the-badge)](#install)

[![Swift](https://img.shields.io/badge/SwiftUI-F05138?logo=swift&logoColor=white)](AppleApp)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-7F52FF?logo=kotlin&logoColor=white)](shared)
[![Rust](https://img.shields.io/badge/Rust_audio_engine-B7410E?logo=rust&logoColor=white)](native-core)

**[Features](#features)** · **[Install](#install)** · **[Build from source](#build-from-source)** · **[Credits](#credits-and-licenses)** · **[Disclaimer](#disclaimer--legal-notice)**

</div>

> [!IMPORTANT]
> Amber is an independent, unofficial project. It is not affiliated with, endorsed by, or connected to YouTube or Google in any way. Use it at your own discretion.

## Features

### Listening
- **YouTube Music** search, browsing and playback, with optional Google sign-in.
- **Your own music, too:** a linked folder that refreshes by itself, with embedded and sidecar `.lrc` lyrics, and WebDAV libraries.
- **Downloads** for offline listening, with resume, per-song progress and covers kept offline.
- **Data saver** mode, **universal pins** for playlists, albums, artists and songs, and **playlist folders**.
- **New-release notifications** for the artists you follow, and **track credits**.

### Sound
- A native **Rust playback engine** with gapless playback, crossfade, an equalizer and audio-route information.
- **Lossless** streams when a configured source provides them, and **Dolby Atmos** through Apple's renderer where the stream supports it.

### Automix
- **DJ-style transitions** that match tempo, align beats and hand over the bass line from one song to the next, with optional on-device models and built-in fallbacks.

### Lyrics
- Lyrics from several providers, **word-synced** where a source supplies them, with an Apple Music-style animation, a timing offset and a lyrics share sheet.

### Made for iPhone
- SwiftUI interface in English and French, following Apple Music's layout, with lock-screen controls and a Now Playing widget.
- **Sing mode** on Android.

Features that depend on external services, sources, audio routes or optional models may behave differently as those dependencies change.

## Install

### iPhone and iPad

1. Download the unsigned IPA (`amber-ios-unsigned-ipa`) from the latest successful run of the **Build iOS** workflow in the Actions tab.
2. Sign and install it with [AltStore](https://altstore.io), [SideStore](https://sidestore.io) or [Sideloadly](https://sideloadly.io), using your Apple account or developer team.
3. Trust the developer profile if iOS asks. To use the widget, install with a tool that registers the App Group (`group.app.amber.Amber`), then remove and add the widget again.

To update, install the new IPA over the old one with the same tool and identity. That keeps your data; don't delete the app first.

### Android

Build the APK from source with the Gradle wrapper (see below).

## Build from source

The iOS app needs a Mac with Xcode and the Apple SDKs. Install Xcode with its command-line tools, XcodeGen, a JDK supported by the Gradle wrapper, Rust with `rustup` and CMake.

```sh
git clone https://github.com/kelian-4/music.git
cd music
rustup target add aarch64-apple-ios aarch64-apple-ios-sim
./scripts/build-shared-framework.sh
./scripts/build-native-core.sh
xcodegen generate --spec AppleApp/project.yml
open AppleApp/Amber.xcodeproj
```

In Xcode, select the `Amber` scheme, choose an iPhone, iPad or Simulator destination, and set your Apple Developer Team on the app and widget targets. For device signing, enable the App Group capability on both targets.

To produce the unsigned IPA the way the CI does, run `scripts/package-ios-ipa.sh <version> --output-dir build/ipa --overwrite`.

<details>
<summary><b>Optional Listen Together server</b></summary>

<br>

The app does not ship with a hosted Listen Together service. Enter a compatible server in the app, or set `LISTEN_TOGETHER_SERVER` in the environment or in the root `local.properties` file.

</details>

<details>
<summary><b>Optional Automix models</b></summary>

<br>

Automix works without downloaded models, using its built-in tempo and DSP fallbacks. The app can download an optional beat-and-downbeat model and an optional vocal-clash model, verified against a pinned size and SHA-256. No model graph is bundled with the app.

</details>

## Contributing

Bug reports, ideas and pull requests are welcome in [Issues](https://github.com/kelian-4/music/issues) and [Pull requests](https://github.com/kelian-4/music/pulls). Please describe the device, OS version and build (shown in **Settings → About & Updates**).

## Disclaimer & Legal Notice

Amber is an independent, unofficial third-party music player and client. It is not affiliated with or endorsed by Google LLC, YouTube Music, or the providers of any configured sources.

- **No media hosting:** Amber does not operate a hosted music catalog. It plays local libraries and accesses configured sources and third-party services; downloads are stored on the listener's device.
- **Your responsibility:** you are responsible for ensuring your use complies with applicable copyright law and the terms of the services you choose to access, including YouTube and YouTube Music. Amber grants no rights to music or other content.
- **No guarantee:** third-party services can change their access rules and behavior at any time.
- **No warranty:** for GPLv3-covered portions, the software is provided without warranty and liability is limited as stated in Sections 15 and 16 of [`LICENSE`](LICENSE). Components with additional terms are listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Credits and licenses

Amber is built on the work of others, and this section keeps the notices their licenses require.

- **BitChord**, the Android and shared client by [Kushagra Singh](https://github.com/kushagrasinghx/BitChord) and contributors, licensed under GPLv3. Amber's Android app and shared Kotlin core are derived from it.
- **BitChord for Apple**, the Apple-platform port by [Martin BAGUMA](https://github.com/bagumamartin/BitChord), licensed under GPLv3. Amber's SwiftUI app, Rust audio engine and Automix are derived from it.
- Amber's own changes are Copyright © 2026 Kelian, also under GPLv3.
- The upstream source is pinned as a Git submodule in `vendor/bitchord-upstream`, with its source, per-file notices and license, for comparison and attribution.
- YouTube stream extraction uses [InnerTubeX](https://github.com/MetrolistGroup/innertubex), vendored in `vendor/innertubex` with its GPLv3 license and notices.
- Some analyzer code was ported from [Orchard](https://github.com/SFG5453/Orchard) by SFG545, through the Android implementation; the relevant files retain their AGPLv3-or-later terms. The audio clarity contour credits the [LastWave-native](https://github.com/Clash-Projects/LastWave-native) reference. The Apple-like lyrics animation credits [binimum/am-lyrics](https://github.com/binimum/am-lyrics).

## License

The project is licensed under the **GNU General Public License v3.0 (GPLv3)**; see [`LICENSE`](LICENSE). Some files and bundled dependencies carry additional or separate terms. Read [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), the license texts in `LICENSES/` and `AppleApp/Resources/AudioLicenses/`, and individual source headers before redistributing or modifying those components.
