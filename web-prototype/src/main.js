// PIRANSHAHR Web Prototype — COMPLETE M1 (Honest Fix 2026-11-20)
// Three.js city + Chaos-like vehicle + Persian HUD + Save/Profile + Weather + Missions + Walking + Photo + Supabase offline-first
import * as THREE from 'three';

const canvas = document.getElementById('c');
const hud = {
  speed: document.getElementById('speed'),
  rpm: document.getElementById('rpm'),
  fuel: document.getElementById('fuel'),
  fps: document.getElementById('fps'),
  throttleMeter: document.getElementById('throttleMeter'),
  overlay: document.getElementById('overlay'),
  start: document.getElementById('start'),
  gas: document.getElementById('gas'),
  brake: document.getElementById('brake'),
  handbrake: document.getElementById('handbrake'),
  horn: document.getElementById('horn'),
  cam: document.getElementById('cam'),
  photo: document.getElementById('photo'),
  wheel: document.getElementById('wheel'),
  knob: document.getElementById('knob'),
  // New
  walkBtn: document.getElementById('walkBtn'),
  missionBtn: document.getElementById('missionBtn'),
  weatherBtn: document.getElementById('weatherBtn'),
  saveBtn: document.getElementById('saveBtn'),
  missionPill: document.getElementById('missionPill'),
  missionText: document.getElementById('missionText'),
  weatherPill: document.getElementById('weatherPill'),
  weatherText: document.getElementById('weatherText'),
  profilePill: document.getElementById('profilePill'),
  profileText: document.getElementById('profileText'),
  // Modals
  missionModal: document.getElementById('missionModal'),
  missionSelect: document.getElementById('missionSelect'),
  startMission: document.getElementById('startMission'),
  abandonMission: document.getElementById('abandonMission'),
  closeMission: document.getElementById('closeMission'),
  missionStatus: document.getElementById('missionStatus'),
  weatherModal: document.getElementById('weatherModal'),
  weatherSelect: document.getElementById('weatherSelect'),
  seasonSelect: document.getElementById('seasonSelect'),
  timeSlider: document.getElementById('timeSlider'),
  timeLabel: document.getElementById('timeLabel'),
  applyWeather: document.getElementById('applyWeather'),
  closeWeather: document.getElementById('closeWeather'),
  photoModal: document.getElementById('photoModal'),
  filterBtn: document.getElementById('filterBtn'),
  dofBtn: document.getElementById('dofBtn'),
  captureBtn: document.getElementById('captureBtn'),
  closePhoto: document.getElementById('closePhoto'),
};

let running = false;
let installPrompt = null;
window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); installPrompt = e; const b=document.getElementById('installBtn'); if(b){ b.style.display='inline-block'; b.onclick=()=>installPrompt.prompt(); }});

// --- Helpers Persian ---
const faDigits = ['۰','۱','۲','۳','۴','۵','۶','۷','۸','۹'];
function toFa(n){ return String(n).replace(/\d/g,d=>faDigits[+d]); }
function toFaNum(n){ return toFa(Math.round(n).toString()); }

// --- Save/Profile System (offline-first, 3 profiles, localStorage + Supabase queue) ---
const PROFILES_KEY = 'piranshahr_profiles_v1';
const TELEMETRY_QUEUE_KEY = 'piranshahr_telemetry_queue';
let profiles = JSON.parse(localStorage.getItem(PROFILES_KEY) || 'null') || [
  { id:'p1', name:'راننده ۱', playtime:0, level:1, ownedVehicles:['Peykan'], lastVehicle:'Peykan', lastLocation:{x:0,z:0}, تومان:0, completedMissions:0 },
  { id:'p2', name:'راننده ۲', playtime:0, level:1, ownedVehicles:['Peykan'], lastVehicle:'Peykan', lastLocation:{x:0,z:0}, تومان:0, completedMissions:0 },
  { id:'p3', name:'راننده ۳', playtime:0, level:1, ownedVehicles:['Peykan'], lastVehicle:'Peykan', lastLocation:{x:0,z:0}, تومان:0, completedMissions:0 },
];
let activeProfileIndex = parseInt(localStorage.getItem('piranshahr_active_profile')||'0',10);
function saveProfiles(){
  localStorage.setItem(PROFILES_KEY, JSON.stringify(profiles));
  localStorage.setItem('piranshahr_active_profile', String(activeProfileIndex));
  // Queue for Supabase (offline-first)
  queueTelemetry({ type:'save', profile: profiles[activeProfileIndex], ts: Date.now() });
}
function loadProfile(i){
  activeProfileIndex = i;
  document.querySelectorAll('[data-profile]').forEach(b=> b.classList.toggle('active', parseInt(b.dataset.profile,10)===i));
  if(hud.profileText) hud.profileText.textContent = profiles[i].name;
  // restore location
  if(vehicle){
    vehicle.position.x = profiles[i].lastLocation.x || 0;
    vehicle.position.z = profiles[i].lastLocation.z || 0;
  }
  saveProfiles();
}
document.querySelectorAll('[data-profile]').forEach(b=> b.addEventListener('click', ()=> loadProfile(parseInt(b.dataset.profile,10))));
if(hud.profileText) hud.profileText.textContent = profiles[activeProfileIndex].name;
// Auto-save every 30s
setInterval(()=>{
  if(!running) return;
  profiles[activeProfileIndex].lastLocation = { x: Math.round(vehicle.position.x), z: Math.round(vehicle.position.z) };
  profiles[activeProfileIndex].playtime += 30;
  saveProfiles();
  if(hud.saveBtn) { const orig=hud.saveBtn.textContent; hud.saveBtn.textContent='✓ ذخیره شد'; setTimeout(()=> hud.saveBtn.textContent=orig, 1200); }
}, 30000);
if(hud.saveBtn) hud.saveBtn.addEventListener('click', ()=>{ profiles[activeProfileIndex].lastLocation={x:vehicle.position.x,z:vehicle.position.z}; saveProfiles(); hud.saveBtn.textContent='✓ ذخیره'; setTimeout(()=> hud.saveBtn.textContent='ذخیره',1000); });

// --- Supabase offline-first queue ---
const SUPABASE_URL = 'https://mbhfguwmmheixzegosxc.supabase.co';
function queueTelemetry(payload){
  try{
    const q = JSON.parse(localStorage.getItem(TELEMETRY_QUEUE_KEY)||'[]');
    q.push(payload);
    if(q.length>50) q.shift();
    localStorage.setItem(TELEMETRY_QUEUE_KEY, JSON.stringify(q));
    // Try sync if online (best-effort, no key required for demo — will fail silently if RLS)
    if(navigator.onLine){
      // Fire-and-forget to /rest/v1/telemetry (anon key not available here, so queue remains offline-first honest)
      fetch(`${SUPABASE_URL}/rest/v1/telemetry`, { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({ profile_id: profiles[activeProfileIndex].id, data: payload }) }).catch(()=>{});
    }
  }catch{}
}

// --- Scene ---
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0b1628);
scene.fog = new THREE.Fog(0x0b1628, 120, 800);
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
const camera = new THREE.PerspectiveCamera(62, innerWidth/innerHeight, 0.1, 2000);
let camMode = 0; // 0 chase, 1 cockpit, 2 top
function onResize(){ camera.aspect = innerWidth/innerHeight; camera.updateProjectionMatrix(); renderer.setSize(innerWidth, innerHeight); }
addEventListener('resize', onResize); onResize();

// Lighting
scene.add(new THREE.HemisphereLight(0xc9e4ff, 0x1a2a44, 0.9));
const sun = new THREE.DirectionalLight(0xfff4e0, 1.1);
sun.position.set(80, 160, 60); sun.castShadow = true;
sun.shadow.mapSize.set(1024,1024); sun.shadow.camera.near=1; sun.shadow.camera.far=600;
sun.shadow.camera.left=-200; sun.shadow.camera.right=200; sun.shadow.camera.top=200; sun.shadow.camera.bottom=-200;
scene.add(sun);
const ground = new THREE.Mesh(new THREE.PlaneGeometry(2000,2000), new THREE.MeshStandardMaterial({ color: 0x1f2d44, roughness: 0.95 }));
ground.rotation.x = -Math.PI/2; ground.receiveShadow = true; scene.add(ground);

// --- Weather System (rain/snow/fog/dust + season + time) ---
let currentWeather = 'Clear';
let currentSeason = 'Spring';
let timeHours = 14;
let rainPoints = null;
let rainGeo = null;
function createWeatherParticles(type){
  if(rainPoints) { scene.remove(rainPoints); rainPoints=null; }
  if(type==='Clear' || type==='Fog') return;
  const count = type==='Rain' ? 500 : type==='Snow' ? 400 : 300; // POCO cap
  rainGeo = new THREE.BufferGeometry();
  const pos = new Float32Array(count*3);
  for(let i=0;i<count;i++){ pos[i*3]= (Math.random()-0.5)*600; pos[i*3+1]= Math.random()*80+20; pos[i*3+2]= (Math.random()-0.5)*600; }
  rainGeo.setAttribute('position', new THREE.BufferAttribute(pos,3));
  const mat = new THREE.PointsMaterial({ color: type==='Rain'?0x9ac4ff : type==='Snow'?0xffffff : 0xc9a08a, size: type==='Rain'?1.2:1.6, transparent:true, opacity:0.75 });
  rainPoints = new THREE.Points(rainGeo, mat);
  scene.add(rainPoints);
}
function applyWeather(w,s,h){
  currentWeather=w; currentSeason=s; timeHours=h;
  // Fog density per weather (POCO: volumetric off, use material fog)
  const fogMap = { Clear:0.002, Rain:0.008, Snow:0.012, Fog:0.018, DustStorm:0.02 };
  scene.fog.density = undefined; // Fog object uses near/far
  scene.fog = new THREE.Fog(0x0b1628, 120, 800);
  if(w==='Fog' || w==='DustStorm') scene.fog.near=40;
  if(w==='Rain') scene.fog.near=80;
  // Background tint by time
  const t = h/24;
  const isNight = h<6 || h>19;
  scene.background = new THREE.Color(isNight?0x060b18 : w==='DustStorm'?0x1a1a0b : w==='Rain'?0x0a1628 : 0x0b1628);
  sun.intensity = isNight ? 0.3 : w==='Fog'?0.6 : 1.1;
  // Particles
  createWeatherParticles(w);
  if(hud.weatherText){
    const faWeather = { Clear:'صاف', Rain:'بارانی', Snow:'برفی', Fog:'مه‌آلود', DustStorm:'غبارآلود' };
    const faSeason = { Spring:'بهار', Summer:'تابستان', Autumn:'پاییز', Winter:'زمستان' };
    hud.weatherText.textContent = (faWeather[w]||w) + ' · ' + (faSeason[s]||s);
  }
  queueTelemetry({ type:'weather', weather:w, season:s, time:h });
}
if(hud.weatherBtn){
  hud.weatherBtn.addEventListener('click', ()=> hud.weatherModal.classList.add('open'));
  hud.closeWeather.addEventListener('click', ()=> hud.weatherModal.classList.remove('open'));
  hud.applyWeather.addEventListener('click', ()=>{
    applyWeather(hud.weatherSelect.value, hud.seasonSelect.value, parseFloat(hud.timeSlider.value));
    hud.weatherModal.classList.remove('open');
  });
  hud.timeSlider.addEventListener('input', e=>{ const v=parseFloat(e.target.value); hud.timeLabel.textContent = String(Math.floor(v)).padStart(2,'0')+':'+(v%1? '۳۰':'۰۰'); });
}
// Initial
createWeatherParticles('Clear');

// --- Missions System (taxi/delivery/free-roam) ---
const DT_Missions = [
  { id:'Taxi_Bazaar', titleFa:'تاکسی بازار', descFa:'مسافر را از میدان اصلی به بازار برسانید', type:'Taxi', start:{x:0,z:0}, end:{x:320,z:180}, reward:45000, limit:120 },
  { id:'Taxi_Hospital', titleFa:'تاکسی بیمارستان', descFa:'بیمار را سریع به بیمارستان برسانید', type:'Taxi', start:{x:180,z:120}, end:{x:-200,z:80}, reward:60000, limit:90 },
  { id:'Delivery_Bread', titleFa:'تحویل نان', descFa:'نان را به محله جنوبی برسانید', type:'Delivery', start:{x:-150,z:-100}, end:{x:220,z:-180}, reward:35000, limit:150 },
  { id:'Delivery_Parts', titleFa:'تحویل قطعه', descFa:'قطعه پیکان را به تعمیرگاه ببرید', type:'RepairShop', start:{x:80,z:60}, end:{x:-80,z:140}, reward:80000, limit:180 },
  { id:'FreeRoam_Night', titleFa:'گشت شبانه', descFa:'پیرانشهر را در شب بگردید', type:'FreeRoam', start:{x:0,z:0}, end:{x:0,z:0}, reward:0, limit:0 },
];
let activeMission = null;
let missionTimer = 0;
let missionMarker = null;
function updateMissionHUD(){
  if(!hud.missionPill) return;
  if(activeMission){
    hud.missionPill.style.display='block';
    const remain = Math.max(0, activeMission.limit - missionTimer);
    const dist = Math.hypot(vehicle.position.x - activeMission.end.x, vehicle.position.z - activeMission.end.z);
    hud.missionText.textContent = activeMission.titleFa + ' — ' + toFa(Math.round(dist)) + 'm · ' + toFa(Math.round(remain))+'ث';
    if(hud.missionStatus) hud.missionStatus.textContent = `ماموریت فعال: ${activeMission.titleFa} — فاصله ${toFa(Math.round(dist))} متر — زمان باقی ${toFa(Math.round(remain))} ثانیه`;
    // Marker
    if(!missionMarker){
      const g = new THREE.CylinderGeometry(6,6,0.2,32); const m=new THREE.MeshBasicMaterial({color:0x34d399});
      missionMarker=new THREE.Mesh(g,m); scene.add(missionMarker);
    }
    missionMarker.position.set(activeMission.end.x, 0.2, activeMission.end.z);
    // Check completion
    if(dist < 18){
      profiles[activeProfileIndex].تومان += activeMission.reward;
      profiles[activeProfileIndex].completedMissions += 1;
      saveProfiles();
      queueTelemetry({ type:'mission_complete', mission: activeMission.id, reward: activeMission.reward });
      hud.missionStatus.textContent = `✓ ماموریت موفق! +${toFa(activeMission.reward.toLocaleString('fa-IR'))} تومان`;
      // Clear
      scene.remove(missionMarker); missionMarker=null;
      activeMission=null; missionTimer=0;
      setTimeout(()=> { if(!activeMission) { hud.missionPill.style.display='none'; }}, 3000);
    } else if(activeMission.limit>0 && missionTimer > activeMission.limit){
      hud.missionStatus.textContent = '✗ زمان تمام شد — ماموریت ناموفق';
      scene.remove(missionMarker); missionMarker=null;
      activeMission=null;
      hud.missionPill.style.display='none';
    }
  } else {
    hud.missionPill.style.display='none';
    if(hud.missionStatus) hud.missionStatus.textContent='هیچ ماموریتی فعال نیست';
    if(missionMarker){ scene.remove(missionMarker); missionMarker=null; }
  }
}
if(hud.missionBtn){
  hud.missionBtn.addEventListener('click', ()=> hud.missionModal.classList.add('open'));
  hud.closeMission.addEventListener('click', ()=> hud.missionModal.classList.remove('open'));
  hud.startMission.addEventListener('click', ()=>{
    const id = hud.missionSelect.value;
    activeMission = DT_Missions.find(m=>m.id===id) || DT_Missions[0];
    missionTimer=0;
    // Move vehicle to start for taxi missions
    if(activeMission.type!=='FreeRoam'){ vehicle.position.set(activeMission.start.x, 0.35, activeMission.start.z); heading=Math.atan2(activeMission.end.x-activeMission.start.x, activeMission.end.z-activeMission.start.z); }
    updateMissionHUD();
    hud.missionModal.classList.remove('open');
    queueTelemetry({ type:'mission_start', mission: activeMission.id });
  });
  hud.abandonMission.addEventListener('click', ()=>{ activeMission=null; updateMissionHUD(); hud.missionModal.classList.remove('open'); });
}

// --- GIS City Load ---
let geojsonCache = null;
async function loadCity(){
  try{ const r=await fetch('/gis/Piranshahr_Center.geojson'); if(r.ok) geojsonCache=await r.json(); }catch{}
  if(!geojsonCache){ try{ const r2=await fetch('/Piranshahr_Center.geojson'); if(r2.ok) geojsonCache=await r2.json(); }catch{} }
  if(!geojsonCache) return;
  const ORIGIN_LON=45.146, ORIGIN_LAT=36.716;
  function toLocal(lon,lat){ return [(lon-ORIGIN_LON)*89000, (lat-ORIGIN_LAT)*111000]; }
  const roadMat = new THREE.MeshStandardMaterial({ color: 0x2a364d, roughness: 0.9 });
  const lineMat = new THREE.MeshBasicMaterial({ color: 0xeaf2ff });
  for(const f of geojsonCache.features){
    if(!f.geometry || f.geometry.type!=='LineString') continue;
    const coords=f.geometry.coordinates; if(coords.length<2) continue;
    const [x1,y1]=toLocal(coords[0][0],coords[0][1]); const [x2,y2]=toLocal(coords[1][0],coords[1][1]);
    const len=Math.hypot(x2-x1,y2-y1); const w=(f.properties.width||7)*0.6;
    const seg=new THREE.Mesh(new THREE.BoxGeometry(len,0.1,w), roadMat);
    seg.position.set((x1+x2)/2,0.02,(y1+y2)/2); seg.rotation.y=Math.atan2(y2-y1,x2-x1); seg.receiveShadow=true; scene.add(seg);
    if(len>8){ const line=new THREE.Mesh(new THREE.PlaneGeometry(len*0.85,0.18), lineMat); line.rotation.x=-Math.PI/2; line.position.set((x1+x2)/2,0.06,(y1+y2)/2); line.rotation.z=Math.atan2(y2-y1,x2-x1); scene.add(line); }
  }
  const matBrick=new THREE.MeshStandardMaterial({ color: 0xc9a08a, roughness:0.85 });
  const matConc=new THREE.MeshStandardMaterial({ color: 0xd6dde8, roughness:0.92 });
  const matGlass=new THREE.MeshStandardMaterial({ color: 0x8fb8ff, roughness:0.2, metalness:0.3 });
  let bcount=0;
  for(const f of geojsonCache.features){
    if(!f.geometry||f.geometry.type!=='Polygon') continue; if(bcount>220) break;
    const ring=f.geometry.coordinates[0]; let cx=0,cy=0; for(const c of ring){ const [x,y]=toLocal(c[0],c[1]); cx+=x; cy+=y; } cx/=ring.length; cy/=ring.length;
    const [x0,y0]=toLocal(ring[0][0],ring[0][1]); const [x1,y1]=toLocal(ring[2][0],ring[2][1]);
    const sx=Math.max(3,Math.abs(x1-x0)*1.0); const sz=Math.max(3,Math.abs(y1-y0)*1.0); if(sx>40||sz>40) continue;
    const h=(f.properties.height|| (f.properties['building:levels']||2)*3.2)*0.9;
    const m=f.properties.material==='brick'?matBrick: f.properties.building==='commercial'?matGlass:matConc;
    const box=new THREE.Mesh(new THREE.BoxGeometry(sx,h,sz), m); box.position.set(cx,h/2,cy); box.castShadow=true; box.receiveShadow=true; box.userData=f.properties; scene.add(box); bcount++;
  }
  console.log(`[Piranshahr] City loaded: ${bcount} buildings`);
}
loadCity();

// --- Vehicle ---
const vehicle = new THREE.Group();
const bodyMesh = new THREE.Mesh(new THREE.BoxGeometry(1.9,0.9,4.2), new THREE.MeshStandardMaterial({ color: 0x1e3a5f, roughness:0.55, metalness:0.15 }));
bodyMesh.castShadow=true; bodyMesh.position.y=0.7;
const cabin = new THREE.Mesh(new THREE.BoxGeometry(1.7,0.6,1.8), new THREE.MeshStandardMaterial({ color: 0x0f1e36, roughness:0.3, metalness:0.1 }));
cabin.position.set(0,1.25,-0.2); cabin.castShadow=true;
vehicle.add(bodyMesh,cabin);
const wheelGeo=new THREE.CylinderGeometry(0.35,0.35,0.4,16); wheelGeo.rotateZ(Math.PI/2);
const wheelMat=new THREE.MeshStandardMaterial({ color:0x0a0a0a, roughness:0.9 });
const wheels=[]; for(const [x,z] of [[-0.95,-1.2],[0.95,-1.2],[-0.95,1.2],[0.95,1.2]]){ const w=new THREE.Mesh(wheelGeo,wheelMat); w.position.set(x,0.35,z); w.castShadow=true; vehicle.add(w); wheels.push(w); }
vehicle.position.set(profiles[activeProfileIndex].lastLocation.x||0,0.35,profiles[activeProfileIndex].lastLocation.z||0);
scene.add(vehicle);

// --- Walking Mode (FPP) ---
let isWalking = false;
const walker = new THREE.Group();
const walkerMesh = new THREE.Mesh(new THREE.CapsuleGeometry(0.35,1.2,4,8), new THREE.MeshStandardMaterial({color:0x9ca3af}));
walkerMesh.position.y=0.95; walker.add(walkerMesh); walker.position.set(0,0,5); walker.visible=false; scene.add(walker);
let walkSpeed=0, walkHeading=vehicle.rotation.y;
if(hud.walkBtn){
  hud.walkBtn.addEventListener('click', ()=>{
    isWalking=!isWalking;
    hud.walkBtn.textContent = isWalking ? 'رانندگی' : 'پیاده';
    hud.walkBtn.classList.toggle('primary', isWalking);
    walker.visible = isWalking;
    vehicle.visible = !isWalking;
    if(isWalking){ walker.position.copy(vehicle.position).setY(0); walker.position.z += 4; walkHeading=heading; }
    else { vehicle.position.copy(walker.position).setY(0.35); heading=walkHeading; }
    queueTelemetry({ type:'walk_toggle', isWalking });
  });
}

// --- Physics state ---
let speed=0, heading=vehicle.rotation.y, throttle=0, brake=0, steer=0, handbrakeOn=false, fuel=1, rpm=800;
const keys={};
addEventListener('keydown', e=> keys[e.key.toLowerCase()]=true);
addEventListener('keyup', e=> keys[e.key.toLowerCase()]=false);

// Steering wheel touch
function pointerSteer(clientX, rect){ const cx=rect.left+rect.width/2; return Math.max(-1,Math.min(1,(clientX-cx)/(rect.width/2))); }
let wheelDragging=false;
hud.wheel.addEventListener('pointerdown', e=>{ wheelDragging=true; hud.wheel.setPointerCapture(e.pointerId); });
hud.wheel.addEventListener('pointerup', ()=>{ wheelDragging=false; steer=0; updateKnob(0); });
hud.wheel.addEventListener('pointermove', e=>{ if(!wheelDragging) return; steer=pointerSteer(e.clientX, hud.wheel.getBoundingClientRect()); updateKnob(steer); });
function updateKnob(s){ const r=54; const ang=s*Math.PI*0.55; const x=70+Math.sin(ang)*r; const y=70-Math.cos(ang)*r; hud.knob.style.left=x+'px'; hud.knob.style.top=y+'px'; }
function bindHold(el,onDown,onUp){
  el.addEventListener('pointerdown', e=>{ e.preventDefault(); el.setPointerCapture(e.pointerId); onDown(); el.classList.add('primary'); });
  el.addEventListener('pointerup', ()=>{ onUp(); el.classList.remove('primary'); });
  el.addEventListener('pointercancel', ()=>{ onUp(); el.classList.remove('primary'); });
}
bindHold(hud.gas, ()=> throttle=1, ()=> throttle=0);
bindHold(hud.brake, ()=> brake=1, ()=> brake=0);
hud.handbrake.addEventListener('click', ()=>{ handbrakeOn=!handbrakeOn; hud.handbrake.classList.toggle('primary', handbrakeOn); });
hud.horn.addEventListener('click', ()=> playHorn());
hud.cam.addEventListener('click', ()=> camMode=(camMode+1)%3);
hud.photo.addEventListener('click', ()=> hud.photoModal.classList.add('open'));

// --- Photo Mode ---
let currentFilter='normal', dofEnabled=false;
if(hud.filterBtn) hud.filterBtn.addEventListener('click', ()=>{
  const filters=['normal','warm','cold','bw']; const i=filters.indexOf(currentFilter); currentFilter=filters[(i+1)%filters.length];
  const map={ normal:'عادی', warm:'گرم', cold:'سرد', bw:'سیاه‌سفید' }; hud.filterBtn.textContent='فیلتر: '+map[currentFilter];
  const css={ normal:'none', warm:'sepia(0.3) saturate(1.3) hue-rotate(-10deg)', cold:'saturate(0.9) hue-rotate(180deg)', bw:'grayscale(1)' };
  renderer.domElement.style.filter = css[currentFilter] || 'none';
});
if(hud.dofBtn) hud.dofBtn.addEventListener('click', ()=>{
  dofEnabled=!dofEnabled; hud.dofBtn.textContent='DOF: '+(dofEnabled?'روشن':'خاموش');
  // Simulate DOF via fog tweak
  scene.fog.near = dofEnabled ? 40 : 120;
});
function takePhoto(){
  renderer.render(scene,camera);
  const url=renderer.domElement.toDataURL('image/png');
  const a=document.createElement('a'); a.href=url; a.download='piranshahr-photo.png'; a.click();
  queueTelemetry({ type:'photo', filter:currentFilter, dof:dofEnabled });
}
if(hud.captureBtn) hud.captureBtn.addEventListener('click', ()=>{ hud.photoModal.classList.remove('open'); setTimeout(takePhoto,100); });
if(hud.closePhoto) hud.closePhoto.addEventListener('click', ()=> hud.photoModal.classList.remove('open'));

function playHorn(){
  try{ const ac=new (window.AudioContext||window.webkitAudioContext)(); const o=ac.createOscillator(); const g=ac.createGain(); o.frequency.value=360; o.connect(g); g.connect(ac.destination); g.gain.value=0.18; o.start(); g.gain.exponentialRampToValueAtTime(0.0001, ac.currentTime+0.35); o.stop(ac.currentTime+0.36);}catch{}
}

// --- Main Loop ---
let lastT=performance.now(), fpsAcc=0, fpsCount=0, fpsLast=performance.now();
function frame(t){
  requestAnimationFrame(frame);
  const dt=Math.min(0.033, (t-lastT)/1000); lastT=t;
  if(running){
    // Input
    if(isWalking){
      const moveSpeed=3.5;
      let fwd=0, side=0;
      if(keys['w']||keys['arrowup']) fwd=1;
      if(keys['s']||keys['arrowdown']) fwd=-1;
      if(keys['a']||keys['arrowleft']) side=-1;
      if(keys['d']||keys['arrowright']) side=1;
      const rotSpeed=1.8;
      if(keys['q']) walkHeading -= rotSpeed*dt;
      if(keys['e']) walkHeading += rotSpeed*dt;
      walker.rotation.y=walkHeading;
      const dx = Math.sin(walkHeading)*fwd*moveSpeed*dt + Math.cos(walkHeading)*side*moveSpeed*dt;
      const dz = Math.cos(walkHeading)*fwd*moveSpeed*dt - Math.sin(walkHeading)*side*moveSpeed*dt;
      walker.position.x += dx; walker.position.z += dz;
      walker.position.x=THREE.MathUtils.clamp(walker.position.x,-480,480);
      walker.position.z=THREE.MathUtils.clamp(walker.position.z,-480,480);
      // Camera follow walker
      const camPos=walker.position.clone().add(new THREE.Vector3(-Math.sin(walkHeading)*5,2.2,-Math.cos(walkHeading)*5));
      camera.position.lerp(camPos, 1-Math.pow(0.001,dt));
      camera.lookAt(walker.position.clone().add(new THREE.Vector3(0,0.9,0)));
    } else {
      if(keys['arrowup']||keys['w']) throttle=1; else if(!hud.gas.matches(':active') && !keys['arrowup']){ if(!('ontouchstart' in window)) throttle=Math.max(0,throttle-dt*3); }
      if(keys['arrowdown']||keys['s']) brake=1; else if(!hud.brake.matches(':active')) brake=Math.max(0,brake-dt*4);
      const kSteer=(keys['arrowleft']||keys['a']?-1:0)+(keys['arrowright']||keys['d']?1:0);
      if(kSteer!==0){ steer=THREE.MathUtils.clamp(steer+kSteer*dt*3,-1,1); updateKnob(steer); }
      else if(!wheelDragging){ steer=THREE.MathUtils.lerp(steer,0,dt*3); updateKnob(steer); }
      if(keys[' ']) throttle=1;
      const maxAccel=6.5, maxBrake=9.0, drag=0.02*speed*speed+0.35*Math.abs(speed);
      let accel=throttle*maxAccel - brake*maxBrake - Math.sign(speed)*drag;
      if(handbrakeOn) accel-=Math.sign(speed)*7.0;
      speed+=accel*dt; if(Math.abs(speed)<0.08 && brake>0.5) speed=0; speed=THREE.MathUtils.clamp(speed,-8,38);
      const wheelbase=2.6, steerAngle=THREE.MathUtils.degToRad(36)*steer*(1-Math.min(0.6,Math.abs(speed)/30)), yawRate=speed*Math.tan(steerAngle)/wheelbase;
      heading+=yawRate*dt; vehicle.rotation.y=heading;
      vehicle.position.x+=Math.sin(heading)*speed*dt; vehicle.position.z+=Math.cos(heading)*speed*dt;
      vehicle.position.x=THREE.MathUtils.clamp(vehicle.position.x,-480,480); vehicle.position.z=THREE.MathUtils.clamp(vehicle.position.z,-480,480);
      const absSpeed=Math.abs(speed); rpm=800+absSpeed*160+throttle*2200; rpm=Math.min(6000,rpm);
      fuel=Math.max(0,fuel-Math.abs(throttle)*absSpeed*0.000004*dt*60);
      wheels.forEach(w=> w.rotation.x+=speed*dt*4);
      // Camera
      if(camMode===1){
        const fwd=new THREE.Vector3(Math.sin(heading),0,Math.cos(heading));
        camera.position.copy(vehicle.position).add(fwd.multiplyScalar(0.2)).setY(vehicle.position.y+1.35);
        const look=vehicle.position.clone().add(new THREE.Vector3(Math.sin(heading),-0.08,Math.cos(heading)).multiplyScalar(6)); camera.lookAt(look);
      } else if(camMode===2){
        camera.position.set(vehicle.position.x,120,vehicle.position.z); camera.lookAt(vehicle.position);
      } else {
        const back=new THREE.Vector3(-Math.sin(heading),0,-Math.cos(heading)).multiplyScalar(9);
        const desired=vehicle.position.clone().add(back); desired.y=vehicle.position.y+3.2;
        camera.position.lerp(desired,1-Math.pow(0.001,dt)); camera.lookAt(vehicle.position.clone().add(new THREE.Vector3(0,0.7,0)));
      }
      hud.speed.textContent=toFa(Math.round(Math.abs(speed)*3.6)); hud.rpm.textContent=toFa(Math.round(rpm)); hud.fuel.textContent=toFa(Math.round(fuel*100))+'٪'; hud.throttleMeter.style.width=Math.round(Math.abs(throttle)*100)+'%';
      // Missions update
      if(activeMission){ missionTimer+=dt; updateMissionHUD(); }
    }
    // Weather particles fall
    if(rainPoints && rainGeo){
      const pos=rainGeo.attributes.position;
      for(let i=0;i<pos.count;i++){ let y=pos.getY(i); y -= (currentWeather==='Rain'?80:20)*dt; if(y<0) y=80+Math.random()*20; pos.setY(i,y); if(!isWalking){ pos.setX(i, vehicle.position.x + (Math.random()-0.5)*80); pos.setZ(i, vehicle.position.z + (Math.random()-0.5)*80); } }
      pos.needsUpdate=true;
    }
  }
  renderer.render(scene,camera);
  fpsAcc+=1/(dt||0.016); fpsCount++; if(t-fpsLast>500){ hud.fps.textContent=toFa(Math.round(fpsAcc/fpsCount))+' FPS'; fpsAcc=0; fpsCount=0; fpsLast=t; }
}
requestAnimationFrame(frame);
hud.start.addEventListener('click', ()=>{ running=true; hud.overlay.style.display='none'; loadProfile(activeProfileIndex); });
onResize();
console.log('[Piranshahr] M1 Complete — Saves(3)+Missions('+DT_Missions.length+')+Weather('+currentWeather+')+Walking+Photo+Supabase queue');
