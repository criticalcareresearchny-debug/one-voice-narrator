# One Voice — optional background/cloud narration API (personal beta)

This is **separate from the existing installed app**. The mobile website still
generates recordings locally at no API cost. The cloud mode starts a
GitHub Actions job; you may switch apps or close Chrome while GitHub processes
the narration. A GitHub Actions worker is not a permanent, always-on API server.

**Privacy first:** This repository is PUBLIC. The dispatch inputs, workflow
logs, and generated artifacts must be considered potentially publicly visible.
Use only a **non-sensitive test script** in this repository. For your own
unpublished books and private video scripts, create a separate PRIVATE repo
containing this worker and workflow; grant the app integration access to that
repo. Do not place a token or unpublished script into website source or a
public GitHub issue.

## Quick background MP3 using GitHub's website

1. Sign in to GitHub and open
   https://github.com/criticalcareresearchny-debug/one-voice-narrator/actions/workflows/cloud-narrate.yml
2. Select **Run workflow**. Paste a non-confidential script into **text**.
3. Choose voice, speed (e.g. 0.95) and filename. Click **Run workflow**.
4. You may close GitHub and use your phone for other work. GitHub runs the
   job on a separate computer, downloads the Kokoro ONNX model, and creates
   the MP3.
5. Revisit **Actions**, open the successful run and scroll to **Artifacts**.
6. Download **onevoice-mp3-RUN_ID.zip** and extract the MP3. Artifacts expire
   after 7 days. The first cloud job can be slower while downloading model files.

There is no guaranteed turn-around time. GitHub may queue, cancel, throttle,
or restrict workflows under its applicable limits and usage policies.

## API: request -> status -> MP3 ZIP

Authentication is required. Create a fine-grained **GitHub personal access
token** limited to the processing repository with **Actions: Read and write**
permission, or use a GitHub App installation access token. Keep the token in
your calling app's secure server-side secrets. **Never paste a token into
ChatGPT, a GitHub file, a published HTML/JavaScript app, or a shared video
project.** A browser-only PWA cannot safely hold a reusable secret that other
services can use.

### 1. Submit a new narration job

```http
POST https://api.github.com/repos/criticalcareresearchny-debug/one-voice-narrator/actions/workflows/cloud-narrate.yml/dispatches
Authorization: Bearer YOUR_SECRET_TOKEN
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2026-03-10
Content-Type: application/json

{
  "ref": "main",
  "inputs": {
    "text": "The wind rose beyond the trees, and the adventure began.",
    "voice": "am_michael",
    "speed": "0.95",
    "filename": "video-001",
    "request_id": "video001"
  }
}
```

Expected current GitHub API response: **HTTP 200** including
`workflow_run_id`, `run_url`, and `html_url`. These IDs refer to an
asynchronous job, not an instantly generated MP3.

### 2. Track completion

```http
GET https://api.github.com/repos/criticalcareresearchny-debug/one-voice-narrator/actions/runs/RUN_ID
```

Look at `status` and `conclusion`. Wait for
`status="completed"` and `conclusion="success"`.

### 3. List and download the MP3 artifact

```http
GET https://api.github.com/repos/criticalcareresearchny-debug/one-voice-narrator/actions/runs/RUN_ID/artifacts
GET https://api.github.com/repos/criticalcareresearchny-debug/one-voice-narrator/actions/artifacts/ARTIFACT_ID/zip
```

Use the returned `onevoice-mp3-RUN_ID` artifact. The second endpoint
redirects to a time-limited ZIP download; extract the MP3 from the ZIP. A
third-party video program can automatically extract and attach it to a video.

## Parameters

| Parameter | Allowed values | Default |
| --- | --- | --- |
| text | UTF-8 plain text, **1–50,000 characters** per job | Required |
| voice | af_heart, af_bella, am_michael, am_fenrir, bf_emma, bm_george | af_heart |
| speed | 0.70–1.50 | 1.00 |
| filename | safe letters/numbers/-/_/. (sanitized) | my-narration |
| request_id | short correlation value, visible in public run name | manual |

The GitHub dispatch event supports up to 65,535 total input characters. The
worker imposes a lower 50,000-character cloud safety cap to leave room for
metadata; longer scripts should be submitted as separate jobs. The local
installed app is unchanged and has no character quota.

### Security and service limitations

- **Public repo is not suitable for confidential scripts or recordings.**
  A separate private processing repo is the preferred production setup.
- These endpoints are **GitHub's authenticated API**, not an unauthenticated
  public `/tts` endpoint. Do not try to call them directly using a token
  embedded in `index.html` or a mobile frontend.
- The free public-repo runner model is subject to GitHub's fair-use and
  Actions policies and may not be suitable for a high-volume commercial API.
- GitHub Actions jobs have startup overhead, a 45-minute timeout here, input
  limits and artifact retention. Neither zero cost nor unlimited processing
  is contractually guaranteed at scale.
- A private repo uses your account's private Actions compute/storage
  allowance, with possible charges above plan inclusions.
- This workflow uses a CPU quantized Kokoro model and 96 kbps MP3 export; it
  does not run on your Samsung's CPU.
- For a standalone third-party-facing API with controlled API keys and direct
  MP3 URLs, a secure gateway and private storage would be the next phase.
  That is a distinct service requiring its own account/configuration.

GitHub documentation:
- https://docs.github.com/en/rest/actions/workflows
- https://docs.github.com/en/rest/actions/artifacts
- https://docs.github.com/en/billing/concepts/product-billing/github-actions
