/**
 * One Voice API gateway — Cloudflare Workers
 *
 * Public HTTPS API contract; private GitHub Actions executor.
 * Two distinct secrets: ONEVOICE_API_KEY is for calling applications,
 * GITHUB_API_TOKEN accesses the PRIVATE workflow repository.
 * Neither token is available to a public web page or Android APK.
 *
 * This gateway deliberately refuses to submit narration to a public repo.
 */
const VOICES = Object.freeze([
  "af_heart", "af_bella", "am_michael", "am_fenrir", "bf_emma", "bm_george"
]);
const VOICE_SET = new Set(VOICES);
const MAX_CHARS = 45_000;
const MAX_REQUEST_BYTES = 85_000;
const MAX_ARTIFACT_BYTES = 42 * 1024 * 1024;
const WORKFLOW = "cloud-narrate.yml";
const GH_VERSION = "2026-03-10";

function json(value, status = 200, extra = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      ...extra
    }
  });
}
function error(message, status = 400) {
  return json({error: message}, status);
}
function configured(env) {
  return (
    typeof env.ONEVOICE_API_KEY === "string" && env.ONEVOICE_API_KEY.length >= 32 &&
    typeof env.GITHUB_API_TOKEN === "string" && env.GITHUB_API_TOKEN.length >= 20 &&
    typeof env.GITHUB_OWNER === "string" && /^[a-zA-Z0-9-]{1,39}$/.test(env.GITHUB_OWNER) &&
    typeof env.GITHUB_REPO === "string" && /^[a-zA-Z0-9._-]{1,100}$/.test(env.GITHUB_REPO)
  );
}
async function secureMatch(a,b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length > 512 || b.length > 512) return false;
  const enc = new TextEncoder();
  const [left,right] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(a)),
    crypto.subtle.digest("SHA-256", enc.encode(b))
  ]);
  const x = new Uint8Array(left), y = new Uint8Array(right);
  let v = 0;
  for(let i=0; i<x.length; i++) v |= x[i] ^ y[i];
  return v === 0;
}
async function authorized(request,env) {
  const header = request.headers.get("authorization") || "";
  if (!header.startsWith("Bearer ")) return false;
  return secureMatch(header.slice(7), env.ONEVOICE_API_KEY);
}
function ghUrl(env,suffix="") {
  return "https://api.github.com/repos/" +
    encodeURIComponent(env.GITHUB_OWNER) + "/" +
    encodeURIComponent(env.GITHUB_REPO) + suffix;
}
async function ghRequest(env, suffix, options={}) {
  return fetch(ghUrl(env,suffix),{
    ...options,
    redirect:"manual",
    headers:{
      Accept:"application/vnd.github+json",
      Authorization:"Bearer " + env.GITHUB_API_TOKEN,
      "X-GitHub-Api-Version":GH_VERSION,
      "User-Agent":"OneVoiceNarrationGateway/1.0",
      ...(options.body ? {"Content-Type":"application/json"} : {})
    }
  });
}
async function ghJson(env,suffix,options={}) {
  const response = await ghRequest(env,suffix,options);
  if (!response.ok) {
    // Never return GitHub's upstream response body: it may contain private details.
    throw new Error("GitHub request failed (" + response.status + ")");
  }
  return response.json();
}
function normalizeInput(value) {
  if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error("Expected a JSON object");
  const text = value.text;
  if (typeof text !== "string" || !text.trim()) throw new Error("text must be a non-empty string");
  if (text.length > MAX_CHARS) throw new Error("text is too long (45,000 characters per job)");
  const voice = value.voice ?? "af_heart";
  if (!VOICE_SET.has(voice)) throw new Error("Invalid English voice");
  const speed = Number(value.speed ?? 1);
  if (!Number.isFinite(speed) || speed < 0.7 || speed > 1.5) throw new Error("speed must be between 0.70 and 1.50");
  const filename = String(value.filename ?? "onevoice-narration")
    .replace(/[^a-zA-Z0-9._-]/g,"-").replace(/^[-.]+|[-.]+$/g,"").slice(0,70) || "onevoice-narration";
  const requestId = String(value.request_id ?? (globalThis.crypto?.randomUUID?.() ?? "request"))
    .replace(/[^a-zA-Z0-9_-]/g,"").slice(0,50) || "request";
  return {text,voice,speed:speed.toFixed(2),filename,request_id:requestId};
}
function runIdFrom(path) {
  const match=path.match(/^\/v1\/narrations\/([1-9][0-9]{0,15})(?:\/(download|artifact\.zip))?$/);
  return match ? {id:match[1],subroute:match[2]??""} : null;
}
async function getRun(env,id) {
  const run=await ghJson(env,"/actions/runs/"+id);
  const path=typeof run.path === "string" ? run.path : "";
  if (run.event !== "workflow_dispatch" ||
      !path.startsWith(".github/workflows/"+WORKFLOW+"@")) {
    return null;
  }
  return run;
}
function encodeFilename(s) {
  return s.replace(/[^a-zA-Z0-9._-]/g,"-").slice(0,100) || "onevoice";
}
async function getArtifact(env,id) {
  const listing=await ghJson(env,"/actions/runs/"+id+"/artifacts?per_page=100");
  const artifacts=Array.isArray(listing.artifacts) ? listing.artifacts : [];
  return artifacts.find(a=>a.name === "onevoice-mp3-"+id && a.expired !== true) || null;
}

/**
 * Extract the sole MP3 from a ZIP with STORE compression (method 0).
 * Our existing workflow uploads artifacts with compression-level: 0.
 * Uses central directory offsets; no need to decompress or copy the MP3.
 */
export function extractStoredMp3(zipBytes) {
  const bytes=zipBytes instanceof Uint8Array ? zipBytes : new Uint8Array(zipBytes);
  if(bytes.byteLength<22)throw new Error("Invalid ZIP archive");
  const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  let eocd=-1;
  for(let i=bytes.length-22;i>=Math.max(0,bytes.length-65557);i--){
    if(view.getUint32(i,true)===0x06054b50){eocd=i;break;}
  }
  if(eocd<0)throw new Error("ZIP central directory not found");
  const n=view.getUint16(eocd+10,true);
  const dirOffset=view.getUint32(eocd+16,true);
  let i=dirOffset, mp3=null;
  if(n<1||n>100||i<0||i>=bytes.length)throw new Error("Invalid ZIP directory");
  for(let entry=0;entry<n;entry++){
    if(i+46>bytes.length||view.getUint32(i,true)!==0x02014b50)throw new Error("Invalid ZIP entry");
    const compression=view.getUint16(i+10,true);
    const compressed=view.getUint32(i+20,true);
    const filenameLength=view.getUint16(i+28,true);
    const extraLength=view.getUint16(i+30,true);
    const commentLength=view.getUint16(i+32,true);
    const offset=view.getUint32(i+42,true);
    if(i+46+filenameLength>bytes.length)throw new Error("Invalid ZIP filename");
    const filename=new TextDecoder().decode(bytes.subarray(i+46,i+46+filenameLength));
    i+=46+filenameLength+extraLength+commentLength;
    if(!filename.toLowerCase().endsWith(".mp3")||filename.endsWith("/"))continue;
    if(mp3!==null)throw new Error("Archive contains multiple MP3s");
    if(compression!==0)throw new Error("MP3 was compressed; use artifact.zip instead");
    if(offset+30>bytes.length||view.getUint32(offset,true)!==0x04034b50)throw new Error("ZIP entry is incomplete");
    const localNameLength=view.getUint16(offset+26,true);
    const localExtraLength=view.getUint16(offset+28,true);
    const start=offset+30+localNameLength+localExtraLength;
    if(start+compressed>bytes.length)throw new Error("ZIP MP3 data truncated");
    mp3={filename:encodeFilename(filename.split("/").pop()),body:bytes.subarray(start,start+compressed)};
  }
  if(!mp3||mp3.body.byteLength<100)throw new Error("MP3 missing or empty");
  return mp3;
}
async function artifactZip(env,id,artifact) {
  const upstream=await ghRequest(env,"/actions/artifacts/"+artifact.id+"/zip");
  if(upstream.status!==302)throw new Error("GitHub did not return an artifact download");
  const location=upstream.headers.get("location");
  if(!location||!/^https:\/\//i.test(location))throw new Error("Artifact download URL invalid");
  const response=await fetch(location,{redirect:"follow"});
  if(!response.ok)throw new Error("Could not fetch recording ZIP");
  const contentLength=Number(response.headers.get("content-length") || 0);
  if(contentLength>MAX_ARTIFACT_BYTES)throw new Error("Recording too large for gateway download");
  const bytes=new Uint8Array(await response.arrayBuffer());
  if(bytes.length>MAX_ARTIFACT_BYTES)throw new Error("Recording exceeds gateway memory limit");
  return bytes;
}
export default {
  async fetch(request,env) {
    try {
      const url=new URL(request.url);
      if (url.pathname==="/health" && request.method==="GET") {
        return json({service:"One Voice",api:"v1",status:"available"});
      }
      if (!configured(env)) return error("API is not configured yet",503);
      if (!(await authorized(request,env))) {
        return json({error:"Unauthorized"},401,{"WWW-Authenticate":"Bearer"});
      }
      if (url.pathname==="/v1/voices" && request.method==="GET") {
        return json({voices:VOICES});
      }
      if(url.pathname==="/v1/narrations" && request.method==="POST"){
        if(!/application\/json/i.test(request.headers.get("content-type")||""))return error("Send application/json",415);
        if(Number(request.headers.get("content-length")||0)>MAX_REQUEST_BYTES)return error("Request too large",413);
        const raw=await request.text();
        if(new TextEncoder().encode(raw).byteLength>MAX_REQUEST_BYTES)return error("Request too large",413);
        let parsed, data;
        try {parsed=JSON.parse(raw);data=normalizeInput(parsed);}catch(e){return error(e.message,400);}
        // Hard security gate: unpublished narration must NEVER be dispatched
        // to the public source-code repo, even if GitHub credentials permit it.
        const repo=await ghJson(env,"");
        if(repo.private!==true)return error("Narration processor must be a private GitHub repository",503);
        const response=await ghRequest(env,"/actions/workflows/"+WORKFLOW+"/dispatches",{
          method:"POST",
          body:JSON.stringify({ref:"main",inputs:data})
        });
        if(!response.ok)throw new Error("Could not submit narration ("+response.status+")");
        const result=await response.json().catch(()=>({}));
        const id=result.workflow_run_id;
        if(!Number.isSafeInteger(id)||id<=0)throw new Error("Processor did not return a job ID");
        return json({
          id:String(id),status:"queued",
          status_url:"/v1/narrations/"+id,
          download_url:"/v1/narrations/"+id+"/download",
          artifact_url:"/v1/narrations/"+id+"/artifact.zip"
        },202);
      }
      const parsed=runIdFrom(url.pathname);
      if(parsed&&request.method==="GET"){
        const run=await getRun(env,parsed.id);
        if(!run)return error("Narration job not found",404);
        if(!parsed.subroute){
          const completed=run.status==="completed";
          return json({
            id:parsed.id,
            status:!completed?"processing":run.conclusion==="success"?"completed":run.conclusion==="cancelled"?"cancelled":"failed",
            detail:run.status,
            download_url:completed&&run.conclusion==="success"?"/v1/narrations/"+parsed.id+"/download":null,
            artifact_url:completed&&run.conclusion==="success"?"/v1/narrations/"+parsed.id+"/artifact.zip":null
          });
        }
        if(run.status!=="completed"||run.conclusion!=="success")return error("Narration is not complete",409);
        const artifact=await getArtifact(env,parsed.id);
        if(!artifact)return error("Recording artifact missing or expired",404);
        const zip=await artifactZip(env,parsed.id,artifact);
        if(parsed.subroute==="artifact.zip"){
          return new Response(zip,{
            headers:{"Content-Type":"application/zip","Cache-Control":"no-store",
              "Content-Disposition":'attachment; filename="onevoice-'+parsed.id+'.zip"'}
          });
        }
        try{
          const audio=extractStoredMp3(zip);
          return new Response(audio.body,{
            headers:{"Content-Type":"audio/mpeg","Cache-Control":"no-store",
              "X-Content-Type-Options":"nosniff",
              "Content-Disposition":'attachment; filename="'+audio.filename+'"'}
          });
        }catch(e){
          return json({error:"MP3 could not be extracted directly; use artifact.zip instead"},422);
        }
      }
      return error("Not found",404);
    } catch(e) {
      // Do not echo raw exceptions, bearer tokens, narration text, or GitHub secrets.
      return error("Narration service temporarily unavailable",502);
    }
  }
};
