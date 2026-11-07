// Browser-targeted entry point for the self-hosted speech engine bundle.
// Run through esbuild with --platform=browser. Do not use esm.sh's Node polyfills.
import { KokoroTTS } from "kokoro-js";
export { KokoroTTS };
