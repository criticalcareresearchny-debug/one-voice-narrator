# One Voice — regular API-key connection

This is the **standard HTTPS REST API** for One Voice Narration Studio, not
the Android Share/Intent interface. Another program can call it from anywhere
using an ordinary `Authorization: Bearer YOUR_ONEVOICE_API_KEY` header.

**Current status: Source and automated tests exist, but the API is NOT LIVE
until a Cloudflare Worker is deployed and connected to a private GitHub
processor. Do not assume the listed example hostname is active.**

## Architecture

```text
Other video-making app
  -> HTTPS + Bearer One Voice API key
  -> Cloudflare Worker (free-tier gateway, no AI processing)
  -> Private GitHub Actions narration processor (Kokoro)
  -> Result stored as a short-lived GitHub Actions artifact
  -> Caller polls the REST API and downloads the .mp3
```

Two separate credentials are mandatory:
- `ONEVOICE_API_KEY`: **your own secret**, presented by calling programs.
- `GITHUB_API_TOKEN`: **internal GitHub credential**, used only by the Worker
  to dispatch and inspect jobs in the **private** narration repo.

Never publish either key in the APK, PWA, GitHub source code, a screenshot,
or this chat. Store caller tokens only in a secure backend or secret manager,
not in browser JavaScript. The existing native APK works independently and
does not need an update for this cloud integration.

## Setup — one-time

### 1. Create your private processing repository

At https://github.com/new create `onevoice-private-narration`, select
**Private**, initialize it with a README, and authorize the ChatGPT GitHub app
for it. In that new repo, you need exactly the production cloud processor
files copied from the current source repository:

- `.github/workflows/cloud-narrate.yml`
- `cloud/generate.py`

The processor is currently verified in a public source repository using a
**test script only**. The REST gateway contains a hard safety interlock:
it refuses all narration submissions unless GitHub confirms its target
repository is **private**.

For the private processing repo, the current workflow can be copied as is.
Production use should remove the one-time `push` demo trigger and shorten
workflow run metadata that need not reveal script titles.

### 2. Create a narrowly scoped GitHub token

At https://github.com/settings/personal-access-tokens create a **fine-grained
personal access token**, restricted to the new private processing repository,
with **Actions: Read and write**. This allows starting a job, checking job
status and downloading its artifact. Do **not** share the token in chat.

### 3. Deploy the free Cloudflare gateway

Open https://dash.cloudflare.com/ and create or sign into a Cloudflare account.
Under **Workers & Pages → Create application → Import GitHub repository**,
select your existing `one-voice-narrator` source repo, then set the
**root directory to `api`** and deploy as a Worker.

- Worker config: `api/wrangler.toml`
- Worker name: `onevoice-narration-api`
- Deploy command in the `api` project: `npm run deploy`
- Configure `GITHUB_OWNER` and `GITHUB_REPO` to refer to the *private*
  processing repo (defaults to the suggested name).
- In the Worker's **Settings → Variables and Secrets**, add two values,
  both with type **Secret**:
  - `GITHUB_API_TOKEN`: the GitHub token from Step 2.
  - `ONEVOICE_API_KEY`: a unique random key, **at least 32 characters**,
    generated in your password manager or a secure random-key generator.

Cloudflare creates an HTTPS URL such as
`https://onevoice-narration-api.YOUR-ACCOUNT.workers.dev`. That is only a
*format example*, not a known live address.

Keep any paid Workers plan **off** unless you deliberately choose one.
Cloudflare's free-tier quota currently includes 100,000 requests/day and
a 10-ms CPU-time allowance per request. It does **not** include unlimited
GitHub private Actions compute; private GitHub job minutes and artifact
storage are subject to your account allowances and spending restrictions.

### 4. Connect a video-making application

Store the **One Voice API key** as a secret in the calling application's
server-side configuration. Use the endpoints below. Do **not** hand the
calling app your separate private GitHub token.

### Endpoints

```http
GET /health
GET /v1/voices                 Authorization: Bearer <ONEVOICE_API_KEY>
POST /v1/narrations           Authorization: Bearer <ONEVOICE_API_KEY>
GET /v1/narrations/<RUN_ID>  Authorization: Bearer <ONEVOICE_API_KEY>
GET /v1/narrations/<RUN_ID>/download     Authorization: Bearer <ONEVOICE_API_KEY>
GET /v1/narrations/<RUN_ID>/artifact.zip Authorization: Bearer <ONEVOICE_API_KEY>
```

Start a job:

```http
POST /v1/narrations
Authorization: Bearer <ONEVOICE_API_KEY>
Content-Type: application/json

{
  "text": "The ancient doors opened, and a new journey began.",
  "voice": "bm_george",
  "speed": 0.90,
  "filename": "narration-scene-01",
  "request_id": "scene-01"
}
```

Response: **HTTP 202**

```json
{
  "id": "321",
  "status": "queued",
  "status_url": "/v1/narrations/321",
  "download_url": "/v1/narrations/321/download",
  "artifact_url": "/v1/narrations/321/artifact.zip"
}
```

Poll `GET /v1/narrations/321`; `status` will be `processing`,
`completed`, `cancelled`, or `failed`. Once completed:

- `GET /v1/narrations/321/download` returns `audio/mpeg` (MP3). It
  extracts the uncompressed MP3 from the GitHub workflow artifact ZIP.
- `GET /v1/narrations/321/artifact.zip` returns the original ZIP if the
  client prefers to extract the file itself.

The current GitHub workflow uses `compression-level: 0` (stored ZIP
entries), verified against the actual successful demo artifact. Other ZIP
compression methods may require downloading `artifact.zip` directly.
Artifacts are currently retained for **7 days**, not permanently.

Maximum input to this REST gateway is **45,000 characters/job**, to stay
below GitHub workflow-dispatch input limits; longer manuscripts should be
split across separate authenticated jobs. This is a per-request technical
cap, not a paid credit or monthly quota.

## What this is NOT

- This API isn't exposed by your phone or APK. The phone can be closed or
  powered off while private GitHub Actions generates the narration.
- It is not a guaranteed unlimited, forever-free commercial hosting service.
- The API cannot be used by random websites without securely handling a
  token; **no CORS access** is offered for public browser frontends.
- **No live Cloudflare URL exists until you perform deployment.**
- This is a personal beta. Automated mocked API tests pass; live
  end-to-end deployment requires Cloudflare credentials and private GitHub
  processor configuration.

## Security/testing

`npm test` from `api/` runs unit tests for authentication, private-repo
checks, workflow dispatch, status polling, MP3 ZIP extraction, and errors.
GitHub Actions runs the same tests on changes.

For reference:

- Cloudflare setup: https://developers.cloudflare.com/workers/ci-cd/builds/
- Cloudflare secrets: https://developers.cloudflare.com/workers/configuration/secrets/
- GitHub workflow dispatch: https://docs.github.com/en/rest/actions/workflows
- GitHub artifacts: https://docs.github.com/en/rest/actions/artifacts
