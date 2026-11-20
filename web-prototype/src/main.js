// PIRANSHAHR Web Prototype — Three.js minimal city + simple vehicle physics + Persian HUD
// Honest label: PWA verification artifact (not UE5), validates GIS layout + vehicle + UI design
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
};

let running = false;
let installPrompt = null;

window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); installPrompt = e; const b=document.getElementById('installBtn'); b.style.display='inline-block'; b.onclick=()=>installPrompt.prompt(); });

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

// Lighting — optimized for mobile
scene.add(new THREE.HemisphereLight(0xc9e4ff, 0x1a2a44, 0.9));
const sun = new THREE.DirectionalLight(0xfff4e0, 1.1);
sun.position.set(80, 160, 60); sun.castShadow = true;
sun.shadow.mapSize.set(1024,1024); sun.shadow.camera.near=1; sun.shadow.camera.far=600;
sun.shadow.camera.left=-200; sun.shadow.camera.right=200; sun.shadow.camera.top=200; sun.shadow.camera.bottom=-200;
scene.add(sun);

// Ground
const ground = new THREE.Mesh(new THREE.PlaneGeometry(2000,2000), new THREE.MeshStandardMaterial({ color: 0x1f2d44, roughness: 0.95 }));
ground.rotation.x = -Math.PI/2; ground.receiveShadow = true;
scene.add(ground);

// Grid roads (simple) + buildings from GIS sample
let geojsonCache = null;
async function loadCity(){
  try{
    const r = await fetch('/gis/Piranshahr_Center.geojson');
    if(r.ok) geojsonCache = await r.json();
  }catch{}
  if(!geojsonCache){
    // fallback fetch via relative path to PIRANSHAHR content served statically
    try{ const r2 = await fetch('/Piranshahr_Center.geojson'); if(r2.ok) geojsonCache = await r2.json(); }catch{}
  }
  if(!geojsonCache) return;
  // Center transform: lon/lat → local meters (approx, same as ue_coord_transform)
  const ORIGIN_LON=45.146, ORIGIN_LAT=36.716;
  function toLocal(lon,lat){
    const dx = (lon - ORIGIN_LON) * 89000; // meters approx
    const dy = (lat - ORIGIN_LAT) * 111000;
    return [dx, dy];
  }
  // Roads → thin boxes
  const roadMat = new THREE.MeshStandardMaterial({ color: 0x2a364d, roughness: 0.9 });
  const lineMat = new THREE.MeshBasicMaterial({ color: 0xeaf2ff });
  for(const f of geojsonCache.features){
    if(!f.geometry || f.geometry.type!=='LineString') continue;
    const coords = f.geometry.coordinates;
    if(coords.length<2) continue;
    const [x1,y1]=toLocal(coords[0][0],coords[0][1]);
    const [x2,y2]=toLocal(coords[1][0],coords[1][1]);
    const len = Math.hypot(x2-x1, y2-y1);
    const w = (f.properties.width||7) * 0.6; // scale for prototype
    const seg = new THREE.Mesh(new THREE.BoxGeometry(len, 0.1, w), roadMat);
    seg.position.set((x1+x2)/2, 0.02, (y1+y2)/2);
    seg.rotation.y = Math.atan2(y2-y1, x2-x1);
    seg.receiveShadow = true;
    scene.add(seg);
    // center line
    if(len>8){
      const line = new THREE.Mesh(new THREE.PlaneGeometry(len*0.85, 0.18), lineMat);
      line.rotation.x = -Math.PI/2; line.position.set((x1+x2)/2, 0.06, (y1+y2)/2); line.rotation.z = Math.atan2(y2-y1, x2-x1);
      scene.add(line);
    }
  }
  // Buildings → boxes with footprint
  const matBrick = new THREE.MeshStandardMaterial({ color: 0xc9a08a, roughness: 0.85 });
  const matConc = new THREE.MeshStandardMaterial({ color: 0xd6dde8, roughness: 0.92 });
  const matGlass = new THREE.MeshStandardMaterial({ color: 0x8fb8ff, roughness: 0.2, metalness: 0.3 });
  let bcount=0;
  for(const f of geojsonCache.features){
    if(!f.geometry || f.geometry.type!=='Polygon') continue;
    if(bcount>220) break; // perf cap for mobile
    const ring = f.geometry.coordinates[0];
    // centroid + approx size
    let cx=0, cy=0; for(const c of ring){ const [x,y]=toLocal(c[0],c[1]); cx+=x; cy+=y; } cx/=ring.length; cy/=ring.length;
    const [x0,y0]=toLocal(ring[0][0],ring[0][1]); const [x1,y1]=toLocal(ring[2][0],ring[2][1]);
    const sx = Math.max(3, Math.abs(x1-x0)*1.0);
    const sz = Math.max(3, Math.abs(y1-y0)*1.0);
    if(sx>40||sz>40) continue;
    const h = (f.properties.height|| (f.properties['building:levels']||2)*3.2) * 0.9;
    const m = f.properties.material==='brick' ? matBrick : f.properties.building==='commercial' ? matGlass : matConc;
    const box = new THREE.Mesh(new THREE.BoxGeometry(sx, h, sz), m);
    box.position.set(cx, h/2, cy);
    box.castShadow = true; box.receiveShadow = true;
    scene.add(box);
    bcount++;
  }
  console.log(`[Piranshahr] City loaded: ${bcount} buildings, roads rendered`);
}
loadCity();

// Vehicle — simple arcade physics (validates input + HUD, real UE uses Chaos)
const vehicle = new THREE.Group();
const bodyMesh = new THREE.Mesh(new THREE.BoxGeometry(1.9,0.9,4.2), new THREE.MeshStandardMaterial({ color: 0x1e3a5f, roughness:0.55, metalness:0.15 }));
bodyMesh.castShadow=true; bodyMesh.position.y=0.7;
const cabin = new THREE.Mesh(new THREE.BoxGeometry(1.7,0.6,1.8), new THREE.MeshStandardMaterial({ color: 0x0f1e36, roughness:0.3, metalness:0.1 }));
cabin.position.set(0,1.25, -0.2); cabin.castShadow=true;
vehicle.add(bodyMesh, cabin);
// wheels (visual only)
const wheelGeo = new THREE.CylinderGeometry(0.35,0.35,0.4,16); wheelGeo.rotateZ(Math.PI/2);
const wheelMat = new THREE.MeshStandardMaterial({ color: 0x0a0a0a, roughness:0.9 });
const wheels=[]; for(const [x,z] of [[-0.95,-1.2],[0.95,-1.2],[-0.95,1.2],[0.95,1.2]]){ const w=new THREE.Mesh(wheelGeo,wheelMat); w.position.set(x,0.35,z); w.castShadow=true; vehicle.add(w); wheels.push(w); }
vehicle.position.set(0,0.35,0);
scene.add(vehicle);

// Physics state
let speed = 0; // m/s
let heading = 0; // radians
let throttle = 0, brake=0, steer=0; // -1..1
let handbrakeOn=false;
let fuel=1; // 0..1
let rpm=800;

// Input
const keys = {};
addEventListener('keydown', e=> keys[e.key.toLowerCase()]=true);
addEventListener('keyup', e=> keys[e.key.toLowerCase()]=false);

function pointerSteer(clientX, rect){
  const cx = rect.left + rect.width/2;
  const dx = (clientX - cx) / (rect.width/2);
  return Math.max(-1, Math.min(1, dx));
}
let wheelDragging=false;
hud.wheel.addEventListener('pointerdown', e=>{ wheelDragging=true; hud.wheel.setPointerCapture(e.pointerId); });
hud.wheel.addEventListener('pointerup', ()=>{ wheelDragging=false; steer=0; updateKnob(0); });
hud.wheel.addEventListener('pointermove', e=>{
  if(!wheelDragging) return;
  steer = pointerSteer(e.clientX, hud.wheel.getBoundingClientRect());
  updateKnob(steer);
});
function updateKnob(s){
  const r=54; const ang = s * Math.PI*0.55; const x = 70 + Math.sin(ang)*r; const y = 70 - Math.cos(ang)*r;
  hud.knob.style.left = x+'px'; hud.knob.style.top = y+'px';
}

function bindHold(el, onDown, onUp){
  el.addEventListener('pointerdown', e=>{ e.preventDefault(); el.setPointerCapture(e.pointerId); onDown(); el.classList.add('primary'); });
  el.addEventListener('pointerup', ()=>{ onUp(); el.classList.remove('primary'); });
  el.addEventListener('pointercancel', ()=>{ onUp(); el.classList.remove('primary'); });
  el.addEventListener('pointerleave', ()=>{ /* keep */ });
}
bindHold(hud.gas, ()=> throttle=1, ()=> throttle=0);
bindHold(hud.brake, ()=> brake=1, ()=> brake=0);
hud.handbrake.addEventListener('click', ()=>{ handbrakeOn=!handbrakeOn; hud.handbrake.classList.toggle('primary', handbrakeOn); });
hud.horn.addEventListener('click', ()=>{ playHorn(); });
hud.cam.addEventListener('click', ()=>{ camMode=(camMode+1)%3; });
hud.photo.addEventListener('click', ()=>{ takePhoto(); });

function playHorn(){
  try{
    const ac = new (window.AudioContext||window.webkitAudioContext)();
    const o = ac.createOscillator(); const g=ac.createGain(); o.frequency.value=360; o.connect(g); g.connect(ac.destination); g.gain.value=0.18; o.start(); g.gain.exponentialRampToValueAtTime(0.0001, ac.currentTime+0.35); o.stop(ac.currentTime+0.36);
  }catch{}
}
function takePhoto(){
  renderer.render(scene,camera);
  const url = renderer.domElement.toDataURL('image/png');
  const a=document.createElement('a'); a.href=url; a.download='piranshahr-photo.png'; a.click();
}

// Persians numerals helper
const faDigits = ['۰','۱','۲','۳','۴','۵','۶','۷','۸','۹'];
function toFa(n){ return String(n).replace(/\d/g,d=>faDigits[+d]); }

// Loop
let lastT=performance.now(); let fpsAcc=0, fpsCount=0, fpsLast=performance.now();
function frame(t){
  requestAnimationFrame(frame);
  const dt = Math.min(0.033, (t-lastT)/1000); lastT=t;

  if(running){
    // Keyboard overrides
    if(keys['arrowup']||keys['w']) throttle = 1; else if(!hud.gas.matches(':active') && !keys['arrowup']) { if(!('ontouchstart' in window)) throttle = Math.max(0, throttle - dt*3); }
    if(keys['arrowdown']||keys['s']) brake = 1; else if(!hud.brake.matches(':active')) brake = Math.max(0, brake - dt*4);
    const kSteer = (keys['arrowleft']||keys['a']? -1:0) + (keys['arrowright']||keys['d']? 1:0);
    if(kSteer!==0) { steer = THREE.MathUtils.clamp(steer + kSteer*dt*3, -1, 1); updateKnob(steer); }
    else if(!wheelDragging) { steer = THREE.MathUtils.lerp(steer, 0, dt*3); updateKnob(steer); }
    if(keys[' ']) throttle=1; // space = gas

    // Simple longitudinal
    const maxAccel = 6.5; // m/s2 (Peykan ~0-100 in 12s)
    const maxBrake = 9.0;
    const drag = 0.02 * speed*speed + 0.35*Math.abs(speed);
    let accel = throttle*maxAccel - brake*maxBrake - Math.sign(speed)*drag;
    if(handbrakeOn) accel -= Math.sign(speed)*7.0;
    speed += accel*dt;
    if(Math.abs(speed)<0.08 && brake>0.5) speed=0;
    speed = THREE.MathUtils.clamp(speed, -8, 38); // ~137 km/h cap for prototype
    // Steering (bicycle model)
    const wheelbase=2.6;
    const steerAngle = THREE.MathUtils.degToRad(36) * steer * (1 - Math.min(0.6, Math.abs(speed)/30));
    const yawRate = speed * Math.tan(steerAngle) / wheelbase;
    heading += yawRate * dt;
    // Move
    vehicle.rotation.y = heading;
    vehicle.position.x += Math.sin(heading)*speed*dt;
    vehicle.position.z += Math.cos(heading)*speed*dt;
    // Keep in bounds
    vehicle.position.x = THREE.MathUtils.clamp(vehicle.position.x, -480,480);
    vehicle.position.z = THREE.MathUtils.clamp(vehicle.position.z, -480,480);

    // RPM & fuel (simplified torque curve)
    const absSpeed = Math.abs(speed);
    rpm = 800 + absSpeed*160 + throttle*2200;
    rpm = Math.min(6000, rpm);
    fuel = Math.max(0, fuel - Math.abs(throttle)*absSpeed*0.000004*dt*60);
    // Wheel spin visual
    wheels.forEach(w=> w.rotation.x += speed*dt*4);

    // Camera
    const chaseDist = camMode===2? 0 : 9;
    const chaseH = camMode===1? 1.3 : camMode===2? 120 : 3.2;
    const camTarget = vehicle.position.clone();
    if(camMode===1){
      // cockpit
      const fwd = new THREE.Vector3(Math.sin(heading),0,Math.cos(heading));
      camera.position.copy(vehicle.position).add(fwd.multiplyScalar(0.2)).setY(vehicle.position.y+1.35);
      const look = vehicle.position.clone().add(new THREE.Vector3(Math.sin(heading),-0.08,Math.cos(heading)).multiplyScalar(6));
      camera.lookAt(look);
    } else if(camMode===2){
      camera.position.set(vehicle.position.x, chaseH, vehicle.position.z);
      camera.lookAt(vehicle.position);
    } else {
      const back = new THREE.Vector3(-Math.sin(heading),0,-Math.cos(heading)).multiplyScalar(chaseDist);
      const desired = vehicle.position.clone().add(back); desired.y = vehicle.position.y + chaseH;
      camera.position.lerp(desired, 1 - Math.pow(0.001, dt));
      camera.lookAt(vehicle.position.clone().add(new THREE.Vector3(0,0.7,0)));
    }

    // HUD
    hud.speed.textContent = toFa(Math.round(Math.abs(speed)*3.6));
    hud.rpm.textContent = toFa(Math.round(rpm));
    hud.fuel.textContent = toFa(Math.round(fuel*100))+'٪';
    hud.throttleMeter.style.width = Math.round(Math.abs(throttle)*100)+'%';
  }

  renderer.render(scene, camera);

  // FPS
  fpsAcc += 1/(dt||0.016); fpsCount++;
  if(t - fpsLast > 500){ hud.fps.textContent = toFa(Math.round(fpsAcc/fpsCount))+' FPS'; fpsAcc=0; fpsCount=0; fpsLast=t; }
}
requestAnimationFrame(frame);

hud.start.addEventListener('click', ()=>{
  running=true; hud.overlay.style.display='none';
  // resume AudioContext for horn
});

// PWA install handled above
onResize();
