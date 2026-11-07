// One Voice Narrator — browser-side Kokoro speech inference worker.
// English voices, selected by the user. No external paid narration API.

const MODEL = "onnx-community/Kokoro-82M-v1.0-ONNX";
const ENGLISH_VOICES = new Set(["af_heart","af_bella","am_michael","am_fenrir","bf_emma","bm_george"]);
let tts = null;
let modelPromise = null;

function reportError(err) {
  self.postMessage({type:"error",message:String(err?.message || err || "Unknown speech engine error")});
}
async function load() {
  if (tts) {self.postMessage({type:"ready"});return;}
  if (modelPromise) return modelPromise;
  self.postMessage({type:"loading"});
  modelPromise = (async()=>{
    // Model and library are downloaded on demand and inference runs on the user's device.
    // Browser-specific local bundle built on GitHub Actions, avoids Node-style fs polyfills.
    const { KokoroTTS } = await import("./vendor/kokoro-browser.js");
    tts = await KokoroTTS.from_pretrained(MODEL, {
      dtype:"q8",
      device:"wasm",
      progress_callback: info => {
        if(info && Number.isFinite(info.progress)){
          self.postMessage({type:"download-progress",progress:info.progress});
        }
      }
    });
    self.postMessage({type:"ready"});
  })();
  try {await modelPromise;}
  catch (err) {modelPromise=null;reportError(err);}
}
async function synthesize(text,id,voice,speed){
  if (!tts) {reportError(new Error("Speech model not loaded"));return;}
  try {
    if (!ENGLISH_VOICES.has(voice)) throw new Error("Unsupported narration voice");
    const playbackSpeed = Number(speed);
    if (!Number.isFinite(playbackSpeed) || playbackSpeed < 0.7 || playbackSpeed > 1.5) throw new Error("Invalid narration speed");
    const audio = await tts.generate(text,{voice,speed:playbackSpeed});
    const data=audio.audio;
    if (!(data instanceof Float32Array)) throw new Error("Voice engine returned an unsupported audio format.");
    const rate=audio.sampling_rate || 24000;
    self.postMessage({type:"audio",id,samples:data.buffer,sampleRate:rate},[data.buffer]);
  } catch(err) {reportError(err);}
}
self.addEventListener("message",({data})=>{
  if (data?.type==="load") void load();
  if (data?.type==="speak") void synthesize(data.text,data.id,data.voice,data.speed ?? 1);
});
