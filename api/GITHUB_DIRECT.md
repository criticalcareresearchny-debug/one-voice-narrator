# One Voice — GitHub token API (public repository, no additional host)

**This is the simplest API connection for the current setup.** It uses the
existing public repository `criticalcareresearchny-debug/one-voice-narrator`
and the verified GitHub Actions background narration workflow.

There is **no need to install another One Voice APK** or create a second
repository. There is **no separate API server** to host.

> **PUBLIC WORKFLOW WARNING:** The submitted text is part of a GitHub Actions
> workflow run, and its logs and downloaded MP3 artifacts can be accessible
> to anyone with read access to this public repo. Only submit content that
> you are comfortable being public. The API token authorizes submissions;
> it does not make the script or resulting recording private.

## One-time: generate an API token

1. Sign in to GitHub and open
   https://github.com/settings/personal-access-tokens
2. Create a **fine-grained personal access token** for just
   `criticalcareresearchny-debug/one-voice-narrator`.
3. Give it **Repository permissions → Actions → Read and write**.
4. Set an expiration date and store it in your video-making app's **secure
   secrets settings**. Do not paste the token into ChatGPT, public frontend
   JavaScript, the One Voice website, or the publicly visible GitHub repo.

GitHub uses a Bearer token in the `Authorization` header for these requests.
This is the normal GitHub REST API, with no additional API service.

## Step 1: submit text, voice and speed

Send an authenticated HTTPS POST:

```http
POST https://api.github.com/repos/criticalcareresearchny-debug/one-voice-narrator/actions/workflows/cloud-narrate.yml/dispatches
Authorization: Bearer YOUR_TOKEN
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2026-03-10
Content-Type: application/json

{
  "ref": "main",
  "inputs": {
    "text": "The wind rose as the journey began.",
    "voice": "bm_george",
    "speed": "0.95",
    "filename": "my-video-narration",
    "request_id": "video001"
  }
}
```

- `text`: 1 to 50,000 characters per job in the processor.
- `voice`: `af_heart`, `af_bella`, `am_michael`, `am_fenrir`,
  `bf_emma`, `bm_george`.
- `speed`: 0.70 to 1.50.
- `filename`: the desired name without the `.mp3` extension.
- `request_id`: optional identifier to match the narration request.

When successful, GitHub returns HTTP 200, including the
`workflow_run_id`, `run_url`, and `html_url` for the queued job.

## Step 2: check whether the job is finished

```http
GET https://api.github.com/repos/criticalcareresearchny-debug/one-voice-narrator/actions/runs/RUN_ID
Authorization: Bearer YOUR_TOKEN
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2026-03-10
```

Read `status` (look for `completed`) and `conclusion` (look for
`success`). If the job failed, use its HTML URL to inspect the error.
Avoid polling more often than necessary and respect GitHub's API limits.

## Step 3: download the result

```http
GET https://api.github.com/repos/criticalcareresearchny-debug/one-voice-narrator/actions/runs/RUN_ID/artifacts
Authorization: Bearer YOUR_TOKEN
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2026-03-10
```

Look for the artifact named `onevoice-mp3-RUN_ID`. Use its numeric
`id` in:

```http
GET https://api.github.com/repos/criticalcareresearchny-debug/one-voice-narrator/actions/artifacts/ARTIFACT_ID/zip
Authorization: Bearer YOUR_TOKEN
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2026-03-10
```

Follow the 302 redirect to download the ZIP, then extract its MP3.
The run currently retains completed artifacts for **7 days**.

## What this means for the phone APK

The One Voice Android APK (whether the original beta or the readable
voice-name update) operates separately and generates recordings on the phone.
The regular API above runs narration in GitHub's hosted environment. A
video-making program sends its request directly to GitHub and can continue
without opening the Android app. You do **not** need to replace the APK
to enable this API.

The optional Cloudflare API gateway under `api/` remains a separate,
more polished wrapper if you later want your own One Voice URL and
independent `ONEVOICE_API_KEY`. It is not necessary for the direct GitHub
token workflow and has not been deployed.

## Practical caveats

- The repository is public by user choice: token access does not
  prevent others from seeing public runs, scripts, logs or MP3 artifacts.
- Public GitHub Actions standard runners are currently free within
  platform usage policy, but GitHub can impose rate, fair-use, runtime,
  workflow input size, artifact size and retention restrictions.
- No API is truly unlimited: split unusually long manuscripts into jobs.
- The GitHub API token grants control over Actions in the selected repo;
  never embed it in a distributable client app or frontend.
- GitHub's workflow dispatch API requires Actions **write** permission;
  reading the run and downloading artifacts uses Actions **read**.
- The Cloudflare gateway source supports a public processor only after
  explicit `ALLOW_PUBLIC_PROCESSOR=true` opt-in; that code has passing
  mocked automated tests but is not a deployed service.

Official docs:
- https://docs.github.com/en/rest/actions/workflows?apiVersion=2026-03-10
- https://docs.github.com/en/rest/actions/workflow-runs?apiVersion=2026-03-10
- https://docs.github.com/en/rest/actions/artifacts?apiVersion=2026-03-10
