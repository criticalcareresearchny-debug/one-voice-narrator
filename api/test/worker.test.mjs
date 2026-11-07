import test from "node:test";
import assert from "node:assert/strict";
import worker, {extractStoredMp3} from "../src/worker.mjs";

const secrets={
  ONEVOICE_API_KEY:"onevoice-test-key-with-at-least-32-chars",
  GITHUB_API_TOKEN:"github-internal-token-with-enough-length",
  GITHUB_OWNER:"private-owner",
  GITHUB_REPO:"narration-private"
};
const base="https://voice.example";
const goodRun={
  id:321,status:"completed",conclusion:"success",
  path:".github/workflows/cloud-narrate.yml@refs/heads/main",
  event:"workflow_dispatch"
};
const byte=(str)=>new TextEncoder().encode(str);
function p16(n){return [n&255,n>>>8&255]}
function p32(n){return [n&255,(n>>>8)&255,(n>>>16)&255,(n>>>24)&255]}
function zipStored(name, audio){
  const n=byte(name);
  const local=new Uint8Array([...p32(0x04034b50),...p16(20),...p16(0),...p16(0),...p16(0),...p16(0),
    ...p32(0),...p32(audio.length),...p32(audio.length),...p16(n.length),...p16(0),...n,...audio]);
  const central=new Uint8Array([...p32(0x02014b50),...p16(20),...p16(20),...p16(0),...p16(0),...p16(0),...p16(0),...p32(0),
    ...p32(audio.length),...p32(audio.length),...p16(n.length),...p16(0),...p16(0),...p16(0),...p16(0),...p32(0),...p32(0),...n]);
  const end=new Uint8Array([...p32(0x06054b50),...p16(0),...p16(0),...p16(1),...p16(1),...p32(central.length),...p32(local.length),...p16(0)]);
  const result=new Uint8Array(local.length+central.length+end.length);
  result.set(local);result.set(central,local.length);result.set(end,local.length+central.length);
  return result;
}
const mp3=byte("ID3"+"x".repeat(200));
const zip=zipStored("narration.mp3",mp3);
function mocks({privateRepo=true,run=goodRun}={}){
  const records=[];
  return {records,fetch:async(url,options={})=>{
    url=String(url);records.push({url,method:options.method??"GET",body:options.body??null});
    if(url==="https://actions.example/download")return new Response(zip);
    if(url.endsWith("/actions/artifacts/777/zip"))return new Response("",{status:302,headers:{location:"https://actions.example/download"}});
    if(url.endsWith("/actions/runs/321/artifacts?per_page=100"))return Response.json({artifacts:[{id:777,name:"onevoice-mp3-321",expired:false}]});
    if(url.endsWith("/actions/runs/321"))return Response.json(run);
    if(url.endsWith("/actions/workflows/cloud-narrate.yml/dispatches"))return Response.json({
      workflow_run_id:321,html_url:"https://github.com/example/actions/runs/321"
    });
    if(url.endsWith("/repos/private-owner/narration-private"))return Response.json({private:privateRepo});
    return new Response("bad mock URL "+url,{status:404});
  }};
}
function req(path,options={}){
 const headers=new Headers(options.headers??{});
 if(!options.anonymous)headers.set("Authorization","Bearer "+(options.token??secrets.ONEVOICE_API_KEY));
 if(options.body&&!headers.has("content-type"))headers.set("Content-Type","application/json");
 return new Request(base+path,{method:options.method??"GET",headers,body:options.body});
}
async function withMock(mock,fn){
 const previous=globalThis.fetch;globalThis.fetch=mock.fetch;
 try{return await fn(mock);}finally{globalThis.fetch=previous;}
}

test("ZIP extraction returns stored MP3 bytes",()=>{
  const out=extractStoredMp3(zip);
  assert.equal(out.filename,"narration.mp3");
  assert.deepEqual(out.body,mp3);
});

test("health is available, but the rest requires a Bearer token",async()=>{
 const health=await worker.fetch(new Request(base+"/health"),secrets);
 assert.equal(health.status,200);
 const denied=await worker.fetch(req("/v1/voices",{anonymous:true}),secrets);
 assert.equal(denied.status,401);
 const wrong=await worker.fetch(req("/v1/voices",{token:"wrong-token"}),secrets);
 assert.equal(wrong.status,401);
 const yes=await worker.fetch(req("/v1/voices"),secrets);
 assert.equal(yes.status,200);
 assert.equal((await yes.json()).voices.length,6);
});

test("new narration dispatches only to a private GitHub repository",async()=>{
 const fake=mocks();
 await withMock(fake,async()=>{
   const response=await worker.fetch(req("/v1/narrations",{
      method:"POST",body:JSON.stringify({text:"The new world opened before them.",voice:"bm_george",speed:0.95,filename:"Video Narration"})
   }),secrets);
   assert.equal(response.status,202);
   assert.equal((await response.json()).id,"321");
   const call=fake.records.find(x=>x.method==="POST");
   assert.ok(call);
   const payload=JSON.parse(call.body);
   assert.equal(payload.inputs.voice,"bm_george");
   assert.equal(payload.inputs.speed,"0.95");
   assert.equal(payload.inputs.filename,"Video-Narration");
   assert.match(payload.inputs.text,/new world opened/);
 });
 const publicRepo=mocks({privateRepo:false});
 await withMock(publicRepo,async()=>{
   const response=await worker.fetch(req("/v1/narrations",{
      method:"POST",body:JSON.stringify({text:"Private unpublished script"})
   }),secrets);
   assert.equal(response.status,503);
   assert.equal(publicRepo.records.filter(x=>x.method==="POST").length,0);
 });
});

test("invalid requests cannot start a GitHub narration job",async()=>{
 const fake=mocks();
 await withMock(fake,async()=>{
   for(const body of [
      JSON.stringify({text:""}),
      JSON.stringify({text:"Valid",voice:"xx_fake"}),
      JSON.stringify({text:"Valid",speed:9}),
      JSON.stringify({text:"x".repeat(46_000)})
   ]){
      const response=await worker.fetch(req("/v1/narrations",{method:"POST",body}),secrets);
      assert.equal(response.status,400);
   }
   assert.equal(fake.records.length,0);
 });
});

test("status and direct MP3 download work using private artifacts",async()=>{
 const fake=mocks();
 await withMock(fake,async()=>{
   const status=await worker.fetch(req("/v1/narrations/321"),secrets);
   assert.equal(status.status,200);
   assert.equal((await status.json()).status,"completed");
   const audio=await worker.fetch(req("/v1/narrations/321/download"),secrets);
   assert.equal(audio.status,200);
   assert.equal(audio.headers.get("content-type"),"audio/mpeg");
   assert.deepEqual(new Uint8Array(await audio.arrayBuffer()),mp3);
   const archive=await worker.fetch(req("/v1/narrations/321/artifact.zip"),secrets);
   assert.equal(archive.status,200);
   assert.equal(archive.headers.get("content-type"),"application/zip");
   assert.deepEqual(new Uint8Array(await archive.arrayBuffer()),zip);
 });
});

test("wrong workflow run cannot be read or downloaded",async()=>{
 const fake=mocks({run:{...goodRun,path:".github/workflows/a-different-workflow.yml@refs/heads/main"}});
 await withMock(fake,async()=>{
   const out=await worker.fetch(req("/v1/narrations/321"),secrets);
   assert.equal(out.status,404);
 });
});

test("service reports configuration requirement without exposing secrets",async()=>{
 const result=await worker.fetch(req("/v1/voices"),{});
 assert.equal(result.status,503);
 assert.doesNotMatch(await result.text(),/github-internal-token/);
});
