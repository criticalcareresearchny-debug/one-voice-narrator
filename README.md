# One Voice — free personal narration website

A **basic, six-voice, client-side text-to-speech website**. Paste your video script, click **Generate narration**, play the result, and download an MP3 file.

## What it does

- Six English narrators: American female `af_heart` and `af_bella`; American male `am_michael` and `am_fenrir`; British female `bf_emma`; British male `bm_george`
- Create a short voice sample without replacing your script; listen before recording longer narration
- Remembers the last selected voice on the device
- Long text divided automatically into small sections; no imposed character count or quota
- MP3 output at 24 kHz / 96 kbps mono
- Voice synthesizes on the user's device, avoiding paid server/API usage
- Remembers script in the current browser until cleared
- Stop after the current section and download the completed part
- Designed for Android Chrome; can also work on desktop Chrome

## Important limits (please read)

This is a **prototype**, not a verified published service. It has been statically checked and tested with a simulated speech engine; true speech synthesis **has not been end-to-end tested** on an actual Android phone or from external CDN resources in the build environment. Model download, browser support, and performance must be tested after publication. GitHub Pages publishes the static files; it does not run speech synthesis. There is no app-imposed character quota, but phone processing time, memory, browser tab suspensions, and downloaded-file sizes are real practical limits. Very long narration may be much better on a desktop computer.

First use needs network access to download the Kokoro model (~92 MB quantized model plus other files), JS libraries and WASM dependencies. Keeping the tab open while generating is important. Offline use is not guaranteed: the page and dependencies rely on browser caching and may be evicted. The generated audio and script are not sent to an external narration API, but model/library resources load from external services.

## Publish for free using GitHub Pages

**You only upload TWO files: `index.html` and `voice-worker.js`**. Do not upload the ZIP file without extracting it.

1. Sign in to [github.com](https://github.com/) and create a **new public repository**, e.g. `one-voice-narrator`.
2. In the repository, choose **Add file → Upload files**. Upload **`index.html`** and **`voice-worker.js`** into the repository's top level (not a subfolder). Commit them.
3. Open **Settings → Pages**.
4. Under **Build and deployment**, select **Deploy from a branch**, choose `main` and folder `/ (root)`, then **Save**.
5. When GitHub Pages publishes your site, it will appear at `https://YOUR-GITHUB-USERNAME.github.io/one-voice-narrator/` (replace the username and adjust if you picked a different repo name).
6. Open this HTTPS address in Android Chrome. Paste a short sample first and tap **Generate narration**. If it works, try longer content. Keep Chrome in the foreground.

Publishing source to a public repository makes code public, but **doesn't publish your narration text**. Text typed in the app stays in that browser's local storage unless you manually share it.

GitHub Pages is free for static sites hosted in public repositories on the Free plan, subject to GitHub's [terms and limits](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits). It is not a paid TTS cloud service. Using a personal hobby project is different from commercial software-as-a-service hosting, which GitHub Pages restricts.

## How the voice works

- `index.html`: website, text splitter, progress UI, MP3 encoder and audio download
- `voice-worker.js`: imports `kokoro-js` and downloads Kokoro ONNX weights (runs CPU/WASM in browser)
- MP3 uses `lamejs` 1.2.1 loaded dynamically from jsDelivr on first generation
- Voice library is loaded from esm.sh and Kokoro weights from Hugging Face's model hosting; no API key

## Audio usage and licenses

- Kokoro model: Apache 2.0 (see [model card](https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX))
- kokoro-js: check the [upstream project](https://github.com/hexgrad/kokoro/tree/main/kokoro.js)
- lamejs: LGPL 3.0; third-party library is loaded separately from CDN; preserve attribution and comply with its license

## Choose a voice

Use the **Choose your English narrator** dropdown on the website. The choice is preserved in this browser. Tap **Create a short voice sample**, then press Play in the audio player. All six voices use the same Kokoro speech model; selecting another may download a small additional voice asset the first time.

## Troubleshooting

- `Loading model` takes a long time: first download can be 100 MB or more; give Chrome time on Wi-Fi.
- MP3 library error: check internet access to jsDelivr.
- Voice failed to load: check internet access to esm.sh and Hugging Face; disable content blockers for the site.
- On Android, keep your screen on and the site visible. Browsers can suspend background tasks, and entry-level phones can run out of memory.
- To avoid losing work, start with a few sentences, then 1–2 minutes, then gradually longer scripts.
- `file://` access may not support module workers or model fetches. Publish to GitHub Pages before testing.

## Source notes

This app was newly authored for a simple English-narration workflow. It uses upstream AI speech engine/model components and an MP3 encoder. It contains no trackers, analytics, API keys, backend, or paid endpoints.