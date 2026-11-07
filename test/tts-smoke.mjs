import { chromium } from "playwright";
import fs from "node:fs/promises";

const errors=[];
const browser=await chromium.launch({headless:true,args:["--no-sandbox","--disable-dev-shm-usage"]});
try {
  const page=await browser.newPage({acceptDownloads:true});
  page.on("pageerror",err=>errors.push(String(err)));
  page.on("console",msg=>{if(msg.type()==="error") errors.push(msg.text());});
  page.on("requestfailed",req=>{if(req.url().includes("huggingface")||req.url().includes("wasm"))errors.push("request failed: "+req.url()+" "+req.failure()?.errorText);});
  await page.goto("http://127.0.0.1:8088/",{waitUntil:"domcontentloaded",timeout:30000});
  if(!(await page.locator("#speed").count())) throw new Error("Speed control is missing");
  await page.evaluate(()=>{
    const speed=document.querySelector("#speed");
    speed.value="0.90";
    speed.dispatchEvent(new Event("input",{bubbles:true}));
  });
  const label=await page.locator("#speedValue").textContent();
  if(!label.includes("0.90")) throw new Error("Narration speed UI is not updating: "+label);
  await page.locator("#previewBtn").click();
  await Promise.race([
    page.locator("#output").waitFor({state:"visible",timeout:360000}).then(()=>true),
    page.locator("#message").waitFor({state:"visible",timeout:360000}).then(async()=>{throw Error("Narration error: "+await page.locator("#message").textContent());})
  ]);
  if(errors.some(x=>x.includes("readFile")||x.includes("[unenv]"))) throw Error("Node shim error persists: "+errors.join("\n"));
  const audioUrl=await page.locator("#player").getAttribute("src");
  if(!audioUrl?.startsWith("blob:")) throw new Error("No audio object URL was generated");
  const [download]=await Promise.all([
    page.waitForEvent("download",{timeout:20000}),
    page.locator("#download").click()
  ]);
  const mp3=await fs.readFile(await download.path());
  if(mp3.byteLength < 1000) throw new Error("Downloaded MP3 is empty");
  if(!(mp3.toString("ascii",0,3)==="ID3" || mp3[0]===255)) throw new Error("Downloaded file is not an MP3");
  console.log("PASS: browser speech engine, adjustable speed and downloadable MP3 ("+mp3.byteLength+" bytes)");
} finally {
  await browser.close();
}
