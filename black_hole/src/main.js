import '@fontsource-variable/vazirmatn/wght.css';
import './styles.css';
import { BlackHoleExperience } from './black-hole.js';
import { lessons } from './lessons.js';

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const persianDigits = new Intl.NumberFormat('fa-IR', { maximumFractionDigits: 0 });
const introStartedAt = performance.now();

const app = $('#app');
const universe = $('#universe');
const visualFallback = $('#visualFallback');
const renderStatus = $('#renderStatus');
const demoPanel = $('#demoPanel');
const comparisonPanel = $('#comparisonPanel');
const settingsPanel = $('#settingsPanel');
const knowledgePanel = $('#knowledgePanel');
const panelBackdrop = $('#panelBackdrop');
const lessonNav = $('#lessonNav');
let experience = null;
let webglAvailable = false;
let fallbackRaf = 0;
let fallbackLastFrame = 0;
let lastDemoUi = 0;
let selectedLesson = lessons[0].id;
let introExitScheduled = false;

function dismissIntroSplash() {
  if (introExitScheduled) return;
  const splash = $('#introSplash');
  if (!splash) return;
  introExitScheduled = true;
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const minimumTime = reducedMotion ? 260 : 1450;
  const wait = Math.max(0, minimumTime - (performance.now() - introStartedAt));
  window.setTimeout(() => {
    splash.classList.add('is-leaving');
    window.setTimeout(() => splash.remove(), reducedMotion ? 80 : 760);
  }, wait);
}

const demo = {
  active: false,
  playing: false,
  elapsed: 0,
  finished: false,
  duration: 18,
  phase: -1,
};

const qualityCopy = {
  auto: ['خودکار', 'کیفیت با توجه به توان دستگاه تنظیم می‌شود.'],
  high: ['بالاترین', 'وضوح و جزئیات بیشتر؛ مناسب دستگاه‌های قدرتمند.'],
  balanced: ['متعادل', 'تعادل میان وضوح تصویر و روانی اجرا.'],
  low: ['اقتصادی', 'رندر سبک‌تر برای دستگاه‌های کم‌توان یا باتری کمتر.'],
};

function showFallback(error) {
  webglAvailable = false;
  visualFallback.hidden = false;
  dismissIntroSplash();
  universe.classList.add('is-fallback');
  app.dataset.rendering = 'fallback';
  renderStatus.innerHTML = '<span class="render-dot render-dot--quiet"></span><span>نمای جایگزین فعال است؛ WebGL در این دستگاه در دسترس نیست</span>';
  renderStatus.title = error?.message || 'WebGL is unavailable';
  if (demo.active && demo.playing && !fallbackRaf) fallbackRaf = requestAnimationFrame(fallbackFrame);
}

function fallbackFrame(now) {
  fallbackRaf = 0;
  if (document.hidden) {
    fallbackLastFrame = now;
    if (demo.active && demo.playing) fallbackRaf = requestAnimationFrame(fallbackFrame);
    return;
  }
  const delta = fallbackLastFrame ? Math.min((now - fallbackLastFrame) / 1000, 0.25) : 1 / 60;
  fallbackLastFrame = now;
  tickDemo(delta, now);
  if (demo.active && demo.playing) fallbackRaf = requestAnimationFrame(fallbackFrame);
  else fallbackLastFrame = 0;
}

function formatClock(seconds) {
  const value = Math.max(0, Math.floor(seconds));
  return `${persianDigits.format(Math.floor(value / 60)).padStart(2, '۰')}:${persianDigits.format(value % 60).padStart(2, '۰')}`;
}

const demoPhases = [
  {
    end: 3.5,
    label: 'نزدیک‌شدن',
    title: 'ستاره به میدان گرانشی نزدیک می‌شود',
    description: 'از دور، ستاره هنوز شکل تقریباً کروی خود را حفظ کرده است.',
  },
  {
    end: 7.3,
    label: 'کشش کشندی',
    title: 'نیروهای کشندی ستاره را می‌کشند',
    description: 'گرانشِ سمت نزدیک قوی‌تر است؛ ستاره در امتداد مسیر سقوط کش می‌آید.',
  },
  {
    end: 11.5,
    label: 'رشته‌های ماده',
    title: 'ستاره به جریان‌هایی از ماده تبدیل می‌شود',
    description: 'بخشی از گاز به دور سیاه‌چاله می‌پیچد؛ بخشی دیگر به درون می‌افتد.',
  },
  {
    end: 18,
    label: 'دیسک درخشان',
    title: 'ماده‌ی ستاره به دیسک می‌پیوندد',
    description: 'گازِ داغ در مدارهای نزدیک می‌چرخد و می‌تواند تابش شدیدی ایجاد کند.',
  },
];

function updateDemoUI(force = false, now = performance.now()) {
  if (!demo.active) return;
  const progress = Math.min(1, demo.elapsed / demo.duration);
  $('#demoProgress').style.width = `${progress * 100}%`;
  $('#demoTime').textContent = formatClock(demo.elapsed);
  app.style.setProperty('--demo-progress', `${progress * 100}%`);

  const phase = demoPhases.findIndex((item) => demo.elapsed < item.end);
  const safePhase = phase < 0 ? demoPhases.length - 1 : phase;
  if (force || safePhase !== demo.phase) {
    const item = demoPhases[safePhase];
    demo.phase = safePhase;
    $('#demoStage').textContent = item.label;
    $('#demoTitle').textContent = item.title;
    $('#demoDescription').textContent = item.description;
  }

  if (demo.playing && demo.elapsed >= demo.duration) {
    demo.elapsed = demo.duration;
    demo.playing = false;
    demo.finished = true;
    $('#demoPlayButton').textContent = '↺';
    $('#demoPlayButton').setAttribute('aria-label', 'اجرای دوباره‌ی نمایش');
    $('#demoStage').textContent = 'پایان رویداد';
    $('#demoTitle').textContent = 'ماده در مدارِ نزدیک می‌درخشد';
    $('#demoDescription').textContent = 'یک تصویرسازی فشرده از گسیختگی کشندی؛ سرنوشت واقعی به جرم و مدار ستاره بستگی دارد.';
    $('#sceneStatus').textContent = 'پایان رویداد کشندی';
  } else if (now - lastDemoUi > 65) {
    lastDemoUi = now;
  }
}

function tickDemo(delta, now = performance.now()) {
  if (demo.active && demo.playing) {
    demo.elapsed = Math.min(demo.duration, demo.elapsed + delta);
    experience?.setDemoTime(demo.elapsed);
    updateDemoUI(false, now);
  }
}

function setModeButtons() {
  $('#cosmicMode').classList.toggle('is-active', !demo.active);
  $('#cosmicMode').setAttribute('aria-pressed', String(!demo.active));
  $('#disruptionMode').classList.toggle('is-active', demo.active);
  $('#disruptionMode').setAttribute('aria-pressed', String(demo.active));
  app.dataset.demo = demo.active ? 'active' : 'inactive';
}

function startDemo(restart = true) {
  closeFloatingPanels();
  if (restart || !demo.active) {
    demo.elapsed = 0;
    demo.finished = false;
    demo.phase = -1;
  }
  demo.active = true;
  demo.playing = true;
  experience?.setDemoTime(demo.elapsed);
  demoPanel.hidden = false;
  $('#sceneStatus').textContent = 'گسیختگی کشندی';
  $('#demoPlayButton').textContent = 'Ⅱ';
  $('#demoPlayButton').setAttribute('aria-label', 'مکث نمایش');
  setModeButtons();
  updateDemoUI(true);
  if (!webglAvailable && !fallbackRaf) fallbackRaf = requestAnimationFrame(fallbackFrame);
}

function stopDemo() {
  demo.active = false;
  demo.playing = false;
  demo.elapsed = 0;
  demo.finished = false;
  demo.phase = -1;
  experience?.setDemoTime(-1);
  demoPanel.hidden = true;
  $('#sceneStatus').textContent = 'نمای کیهانی';
  $('#demoProgress').style.width = '0%';
  $('#demoTime').textContent = formatClock(0);
  setModeButtons();
}

function toggleDemoPlayback() {
  if (!demo.active) {
    startDemo(true);
    return;
  }
  if (demo.finished) {
    startDemo(true);
    return;
  }
  demo.playing = !demo.playing;
  $('#demoPlayButton').textContent = demo.playing ? 'Ⅱ' : '▶';
  $('#demoPlayButton').setAttribute('aria-label', demo.playing ? 'مکث نمایش' : 'ادامه‌ی نمایش');
  if (demo.playing && !webglAvailable && !fallbackRaf) fallbackRaf = requestAnimationFrame(fallbackFrame);
}

function setLensing(amount) {
  const value = Math.max(0, Math.min(1, Number(amount) || 0));
  experience?.setLensingAmount(value);
  const active = value > 0.02;
  const button = $('#lensButton');
  button.classList.toggle('is-on', active);
  button.setAttribute('aria-pressed', String(active));
  button.setAttribute('aria-label', active ? 'خاموش‌کردن خمیدگی نور' : 'روشن‌کردن خمیدگی نور');
  $('#lensStatus').textContent = active ? 'خمیدگی نور فعال' : 'مسیر نور بدون خمیدگی';
  $('#lensingRange').value = String(Math.round(value * 100));
  $('#lensingPercent').textContent = `${persianDigits.format(Math.round(value * 100))}٪`;
  app.dataset.lensing = active ? 'on' : 'off';
}

function updateCameraDistanceLabel(value) {
  const percent = Math.max(0, Math.min(100, Math.round(Number(value) || 0)));
  $('#cameraDistanceValue').textContent = `${persianDigits.format(percent)}٪`;
}

function syncCameraDistance() {
  const value = experience?.getCameraDistancePercent();
  if (Number.isFinite(value)) {
    $('#cameraDistanceRange').value = String(value);
    updateCameraDistanceLabel(value);
  }
}

function setFeatureToggle(button, enabled, actionName) {
  button.classList.toggle('is-on', enabled);
  button.setAttribute('aria-pressed', String(enabled));
  button.setAttribute('aria-label', `${enabled ? 'خاموش‌کردن' : 'روشن‌کردن'} ${actionName}`);
}

function togglePanel(panel, trigger, isOpen) {
  panel.hidden = !isOpen;
  if (trigger) trigger.setAttribute('aria-expanded', String(isOpen));
}

function setComparison(open) {
  togglePanel(comparisonPanel, $('#compareButton'), open);
  if (open) {
    togglePanel(settingsPanel, $('#settingsButton'), false);
  }
}

function setSettings(open) {
  togglePanel(settingsPanel, $('#settingsButton'), open);
  if (open) {
    setComparison(false);
    syncCameraDistance();
  }
}

function setKnowledge(open) {
  knowledgePanel.setAttribute('aria-hidden', String(!open));
  knowledgePanel.inert = !open;
  knowledgePanel.classList.toggle('is-open', open);
  panelBackdrop.hidden = !open;
  $('#knowledgeButton').setAttribute('aria-expanded', String(open));
  app.classList.toggle('knowledge-open', open);
  if (open) {
    setComparison(false);
    setSettings(false);
    setTimeout(() => $('#closeKnowledge').focus(), 30);
  } else {
    $('#knowledgeButton').focus({ preventScroll: true });
  }
}

function closeFloatingPanels() {
  setComparison(false);
  setSettings(false);
  if (knowledgePanel.classList.contains('is-open')) setKnowledge(false);
}

const lessonVisualMarkup = {
  'event-horizon': '<div class="mini-horizon"><i class="mini-horizon__disk"></i><i class="mini-horizon__shadow"></i><i class="mini-horizon__photon"></i><span class="mini-star mini-star--a"></span><span class="mini-star mini-star--b"></span></div>',
  'accretion-disk': '<div class="mini-accretion"><i class="mini-accretion__ring mini-accretion__ring--outer"></i><i class="mini-accretion__ring mini-accretion__ring--middle"></i><i class="mini-accretion__ring mini-accretion__ring--inner"></i><i class="mini-accretion__core"></i><span class="mini-spark mini-spark--a"></span><span class="mini-spark mini-spark--b"></span></div>',
  lensing: '<div class="mini-lensing"><span class="mini-lensing__star"></span><i class="mini-lensing__arc mini-lensing__arc--one"></i><i class="mini-lensing__arc mini-lensing__arc--two"></i><i class="mini-lensing__hole"></i><span class="mini-lensing__image mini-lensing__image--a"></span><span class="mini-lensing__image mini-lensing__image--b"></span></div>',
  'tidal-disruption': '<div class="mini-tidal"><i class="mini-tidal__hole"></i><i class="mini-tidal__stream"></i><i class="mini-tidal__star"></i><span class="mini-tidal__spark mini-tidal__spark--a"></span><span class="mini-tidal__spark mini-tidal__spark--b"></span></div>',
  'relativistic-jets': '<div class="mini-jets"><i class="mini-jets__beam mini-jets__beam--top"></i><i class="mini-jets__beam mini-jets__beam--bottom"></i><i class="mini-jets__disk"></i><i class="mini-jets__hole"></i><span class="mini-jets__spark"></span></div>',
};

function renderLesson(id) {
  const lesson = lessons.find((item) => item.id === id) || lessons[0];
  selectedLesson = lesson.id;
  $$('.lesson-tab', lessonNav).forEach((button) => {
    const selected = button.dataset.lesson === lesson.id;
    button.classList.toggle('is-selected', selected);
    button.setAttribute('aria-current', selected ? 'true' : 'false');
  });
  $('#lessonVisual').className = `lesson-visual lesson-visual--${lesson.id}`;
  $('#lessonVisual').innerHTML = lessonVisualMarkup[lesson.id];
  $('#lessonIndex').textContent = `${persianDigits.format(lessons.indexOf(lesson) + 1).padStart(2, '۰')} / ${persianDigits.format(lessons.length).padStart(2, '۰')}`;
  $('#lessonTitle').textContent = lesson.title;
  $('#lessonDescription').textContent = lesson.description;
  $('#lessonFact').textContent = lesson.fact;
  const action = $('#lessonAction');
  if (lesson.id === 'tidal-disruption') {
    action.textContent = 'اجرای نمایش کشندی';
    action.hidden = false;
  } else if (lesson.id === 'lensing') {
    action.textContent = 'فعال‌کردن خمیدگی نور';
    action.hidden = false;
  } else {
    action.hidden = true;
  }
}

for (const [index, lesson] of lessons.entries()) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'lesson-tab';
  button.dataset.lesson = lesson.id;
  button.setAttribute('aria-current', index === 0 ? 'true' : 'false');
  button.textContent = lesson.nav;
  button.addEventListener('click', () => renderLesson(lesson.id));
  lessonNav.append(button);
}

const lessonAction = document.createElement('button');
lessonAction.id = 'lessonAction';
lessonAction.className = 'lesson-action';
lessonAction.type = 'button';
$('#lessonVisual').parentElement.append(lessonAction);
lessonAction.hidden = true;
lessonAction.addEventListener('click', () => {
  if (selectedLesson === 'tidal-disruption') {
    setKnowledge(false);
    startDemo(true);
  } else if (selectedLesson === 'lensing') {
    setLensing(1);
    setKnowledge(false);
    setComparison(true);
  }
});
renderLesson(selectedLesson);

try {
  experience = new BlackHoleExperience(universe, {
    onFrame: (delta) => tickDemo(delta),
    onReady: dismissIntroSplash,
    onUnavailable: showFallback,
  });
  webglAvailable = true;
  app.dataset.rendering = 'webgl';
  renderStatus.innerHTML = '<span class="render-dot"></span><span>تصویرسازی سه‌بعدی آماده است</span>';
} catch (error) {
  showFallback(error);
}

$('#lensButton').addEventListener('click', () => {
  const next = Number($('#lensingRange').value) < 50 ? 1 : 0;
  setLensing(next);
});
setLensing(1);

$('#compareButton').addEventListener('click', () => setComparison(comparisonPanel.hidden));
$('#closeComparison').addEventListener('click', () => setComparison(false));
$('#lensingRange').addEventListener('input', (event) => setLensing(Number(event.currentTarget.value) / 100));

$('#settingsButton').addEventListener('click', () => setSettings(settingsPanel.hidden));
$('#closeSettings').addEventListener('click', () => setSettings(false));

$('#cameraDistanceRange').addEventListener('input', (event) => {
  const value = Number(event.currentTarget.value);
  experience?.setCameraDistancePercent(value);
  updateCameraDistanceLabel(value);
});
$('#orbitIntensityRange').addEventListener('input', (event) => {
  const value = Number(event.currentTarget.value);
  experience?.setOrbitLightIntensity(value / 100);
  $('#orbitIntensityValue').textContent = `${persianDigits.format(value)}٪`;
});
$('#orbitLightToggle').addEventListener('click', (event) => {
  const button = event.currentTarget;
  const enabled = button.getAttribute('aria-pressed') !== 'true';
  experience?.setOrbitLightEnabled(enabled);
  setFeatureToggle(button, enabled, 'حلقه‌ی نور');
});
$('#jetsToggle').addEventListener('click', (event) => {
  const button = event.currentTarget;
  const enabled = button.getAttribute('aria-pressed') !== 'true';
  experience?.setJetsEnabled(enabled);
  setFeatureToggle(button, enabled, 'جت‌های نسبیتی');
});

$('#rotationRange').addEventListener('input', (event) => {
  const value = Number(event.currentTarget.value);
  $('#rotationValue').textContent = `${persianDigits.format(value)}٪`;
  experience?.setRotationSpeed(value / 100);
});
$('#particleRange').addEventListener('input', (event) => {
  const value = Number(event.currentTarget.value);
  $('#particleValue').textContent = `${persianDigits.format(value)}٪`;
  experience?.setParticleIntensity(value / 100);
});
$('#qualityButton').addEventListener('click', () => {
  const mode = experience?.cycleQuality() || 'auto';
  const [label, description] = qualityCopy[mode];
  $('#qualityButton').textContent = label;
  $('#qualityDescription').textContent = description;
  renderStatus.innerHTML = `<span class="render-dot"></span><span>کیفیت رندر: ${label}</span>`;
});

$('#knowledgeButton').addEventListener('click', () => setKnowledge(!knowledgePanel.classList.contains('is-open')));
$('#closeKnowledge').addEventListener('click', () => setKnowledge(false));
panelBackdrop.addEventListener('click', () => setKnowledge(false));
$$('[data-open-lesson]').forEach((button) => {
  button.addEventListener('click', () => {
    renderLesson(button.dataset.openLesson);
    setKnowledge(true);
  });
});

$('#cosmicMode').addEventListener('click', stopDemo);
$('#disruptionMode').addEventListener('click', () => startDemo(true));
$('#demoPlayButton').addEventListener('click', toggleDemoPlayback);
$('#demoReplayButton').addEventListener('click', () => startDemo(true));
$('#zoomInButton').addEventListener('click', () => { experience?.zoom(1); syncCameraDistance(); });
$('#zoomOutButton').addEventListener('click', () => { experience?.zoom(-1); syncCameraDistance(); });
$('#angleButton').addEventListener('click', () => {
  experience?.nextCameraView();
  window.setTimeout(syncCameraDistance, 1200);
});
$('#resetCameraButton').addEventListener('click', () => {
  experience?.resetCamera();
  window.setTimeout(syncCameraDistance, 1200);
});

$('#cinematicButton').addEventListener('click', () => setCinematic(!app.classList.contains('cinematic')));
$('#cinematicExit').addEventListener('click', () => setCinematic(false));
function setCinematic(active) {
  app.classList.toggle('cinematic', active);
  $('#cinematicButton').setAttribute('aria-pressed', String(active));
  $('#cinematicButton').setAttribute('aria-label', active ? 'خاموش‌کردن حالت سینمایی' : 'فعال‌کردن حالت سینمایی');
}

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await (app.requestFullscreen?.() || document.documentElement.requestFullscreen());
  } catch {
    renderStatus.innerHTML = '<span class="render-dot render-dot--quiet"></span><span>تمام‌صفحه در این مرورگر در دسترس نیست</span>';
  }
}
$('#fullscreenButton').addEventListener('click', toggleFullscreen);
document.addEventListener('fullscreenchange', () => {
  const active = Boolean(document.fullscreenElement);
  $('#fullscreenButton').setAttribute('aria-label', active ? 'خروج از تمام‌صفحه' : 'تمام‌صفحه');
  $('#fullscreenButton').classList.toggle('is-on', active);
});

window.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    if (knowledgePanel.classList.contains('is-open')) setKnowledge(false);
    else if (!comparisonPanel.hidden) setComparison(false);
    else if (!settingsPanel.hidden) setSettings(false);
    else if (app.classList.contains('cinematic')) setCinematic(false);
  }
  if (event.code === 'Space' && demo.active && !['BUTTON', 'INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)) {
    event.preventDefault();
    toggleDemoPlayback();
  }
});

window.addEventListener('pagehide', () => {
  if (fallbackRaf) cancelAnimationFrame(fallbackRaf);
  experience?.destroy();
}, { once: true });

// Apply the chosen defaults to the renderer after all controls have been wired.
if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
  $('#rotationRange').value = '0';
  $('#rotationValue').textContent = '۰٪';
}
experience?.setRotationSpeed(Number($('#rotationRange').value) / 100);
experience?.setParticleIntensity(Number($('#particleRange').value) / 100);
experience?.setLensingAmount(1);
experience?.setOrbitLightIntensity(Number($('#orbitIntensityRange').value) / 100);
experience?.setOrbitLightEnabled($('#orbitLightToggle').getAttribute('aria-pressed') === 'true');
experience?.setJetsEnabled($('#jetsToggle').getAttribute('aria-pressed') === 'true');
syncCameraDistance();
