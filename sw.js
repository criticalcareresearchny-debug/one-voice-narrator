// One Voice Narration Studio — lightweight app shell cache. AI models remain managed by the browser and their hosts.
const VERSION="onevoice-shell-v2";
const SHELL=["./","./index.html","./manifest.webmanifest","./icon.svg","./icons/icon-192.png","./icons/icon-512.png","./voice-worker.js","./vendor/kokoro-browser.js"];
self.addEventListener("install",event=>{
  event.waitUntil(caches.open(VERSION).then(cache=>cache.addAll(SHELL)).then(()=>self.skipWaiting()));
});
self.addEventListener("activate",event=>{
  event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith("onevoice-shell-")&&key!==VERSION).map(key=>caches.delete(key)))).then(()=>self.clients.claim()));
});
self.addEventListener("fetch",event=>{
  const request=event.request;
  if(request.method!=="GET"||new URL(request.url).origin!==self.location.origin) return;
  event.respondWith(fetch(request).then(response=>{
    if(response.ok&&response.type==="basic"){
      const copy=response.clone();
      event.waitUntil(caches.open(VERSION).then(cache=>cache.put(request,copy)));
    }
    return response;
  }).catch(()=>caches.match(request).then(cached=>cached||Response.error())));
});
