# One Voice — Native Android beta

This is a **real native Android test app**, separate from the existing
One Voice PWA. It does not merely open the website in a WebView.

## Why a native build?

- Local Kokoro speech generation keeps narration free, with no character credits.
- **Six English voices:** Heart, Bella, Michael, Fenrir, Emma and George.
- Narration speed **0.70x–1.50x** and automatic chapter splitting.
- An Android **foreground service** generates audio while you switch apps.
- A persistent Android notification shows progress and provides a Stop action.
- Audio is encoded locally to an MP3 with the open-source Shine encoder.
- The finished recording is saved in **Downloads/One Voice/** and can be
  opened or shared with a video editor.

The app is built for **Android 10+ ARM64** (including the user's Samsung A17).
The first run copies the bundled model to app-private storage. The APK contains
the ONNX model and native libraries and may be large. No network is needed
for local narration after installation.

## How to install a test APK

1. Open [GitHub Actions](https://github.com/criticalcareresearchny-debug/one-voice-narrator/actions/workflows/build-native-apk.yml).
2. Open a **successful** Build One Voice Native APK workflow run.
3. Under Artifacts, download `OneVoice-Native-Android-APK`.
4. Extract `app-debug.apk` from the ZIP on the Samsung phone.
5. Tap the APK, allow **Install unknown apps** for the trusted Files/Chrome
   source if Android asks, and install.
6. You will see a **second app**, called `One Voice Native`. Your existing
   PWA installation remains unchanged. Only install builds from your repo.

**Testing note:** GitHub can verify the APK assembles, but a successful APK
build does not prove background speech generation on a physical Samsung.
A short sample and screen-lock/switch-app test are required. Debug APK signing
may change between GitHub workflow builds, requiring uninstall before upgrading.

## How it is built

GitHub Actions runs Gradle and downloads verified upstream distribution paths
to the build runner. It **does not** commit any large pre-trained voice binary
to the public repository:

- Native speech engine: sherpa-onnx v1.13.8 Android arm64 JNI.
  https://github.com/k2-fsa/sherpa-onnx/
- Kokoro int8 model: kokoro-int8-multi-lang-v1_0 from sherpa-onnx
  official tts-models release. Licensing/attribution in the upstream archive.
- Native MP3 codec: Shine (LGPL library), https://github.com/toots/shine
  The Shine license text is included in the built APK.
- Java/Kotlin runtime dependencies installed from Google's and Maven Central's
  repositories.

The Android service uses the Android 15+ `mediaProcessing` foreground-service
type. The operating system may still interrupt execution, enforce time limits
or stop the app under memory, thermal, and battery restrictions. In particular,
**do not force-stop the app or uninstall it during narration**.

## Background GitHub API

This Android APK makes MP3s **on the device**. The previously developed GitHub
Actions background narration API is separate (see [cloud/API.md](../cloud/API.md)).
Do not place a GitHub access token inside Android source or a public repository.

## Privacy and limitations

- Text you paste stays on the device; no cloud request is required for local mode.
- No ad SDKs, third-party analytics, permissions for contacts, or microphone.
- The full model requires significant installation/storage space.
- Long recording is CPU-intensive. The foreground service lets you switch
  apps but does not make speech generation inherently faster.
- Debug build is a beta and not yet verified on the phone.
