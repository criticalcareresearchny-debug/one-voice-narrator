# One Voice Native — API Connect v1

This Android APK exposes a local app-to-app Android Intent API.
Other Android apps on the same phone can submit a script, select one of
the six Kokoro voices, set narration speed and request a callback.
The user reviews the script and taps Generate to approve.

Option A: Share text using the Android Share menu, then select One Voice Native.
Option B: Use the explicit Intent demonstrated in android-native/examples/ClientExample.kt.

EXTERNAL API CONTRACT:
- Action: com.onevoice.nativeapp.action.GENERATE_NARRATION
- Target: com.onevoice.nativeapp/com.onevoice.nativeapp.MainActivity
- com.onevoice.nativeapp.extra.TEXT: String, maximum 50,000 characters
- com.onevoice.nativeapp.extra.VOICE: String, one of af_heart af_bella am_michael am_fenrir bf_emma bm_george
- com.onevoice.nativeapp.extra.SPEED: Float between 0.70 and 1.50
- com.onevoice.nativeapp.extra.FILENAME: String, preferred file name
- com.onevoice.nativeapp.extra.REQUEST_ID: String, optional correlation ID
- com.onevoice.nativeapp.extra.CALLBACK: mutable explicit PendingIntent, optional

CALLBACK RESULT FIELDS:
- com.onevoice.nativeapp.extra.STATUS: completed | error | cancelled | busy
- com.onevoice.nativeapp.extra.REQUEST_ID: your original ID
- com.onevoice.nativeapp.extra.MESSAGE: optional short error description
- com.onevoice.nativeapp.extra.RESULT_URI: String content URI on success
- Intent.data and Intent.clipData also include the MP3 content URI
- The calling package is granted read-only URI permission after success
- Use ContentResolver.openInputStream on the returned URI, not a file path

Security: the actual NarrationService is not exported. Untrusted Android apps
cannot silently begin large jobs. All requests require user approval.
Do not embed GitHub API tokens or other credentials into the APK.
Only one narration is processed at a time.

Cloud HTTP API: cloud/API.md documents authenticated GitHub Actions dispatch,
job status, and artifact download for external software on different computers.
That cloud API is separate from the APK and cannot be served directly by the
phone when it is offline or shut down.

Limitations: workflow assembly checks are not physical device tests.
Verify callback delivery and background execution on a Samsung before relying
on the integration in production.
