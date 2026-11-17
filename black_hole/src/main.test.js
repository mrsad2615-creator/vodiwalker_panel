import { beforeAll, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const testEngine = vi.hoisted(() => ({ instance: null }));

vi.mock('./black-hole.js', () => {
  class MockBlackHoleExperience {
    constructor(container, { onFrame } = {}) {
      this.container = container;
      this.onFrame = onFrame;
      this.lensing = 0;
      this.particleIntensity = 0;
      this.rotationSpeed = 0;
      this.demoTime = -1;
      this.quality = 'auto';
      this.zoomCalls = [];
      this.cameraReset = false;
      this.cameraPresetCount = 0;
      testEngine.instance = this;
    }
    setLensingAmount(value) { this.lensing = value; }
    setParticleIntensity(value) { this.particleIntensity = value; }
    setRotationSpeed(value) { this.rotationSpeed = value; }
    setDemoTime(value) { this.demoTime = value; }
    cycleQuality() {
      this.quality = ({ auto: 'high', high: 'balanced', balanced: 'low', low: 'auto' })[this.quality];
      return this.quality;
    }
    zoom(direction) { this.zoomCalls.push(direction); }
    resetCamera() { this.cameraReset = true; }
    nextCameraView() { this.cameraPresetCount += 1; return this.cameraPresetCount; }
    destroy() {}
  }
  return { BlackHoleExperience: MockBlackHoleExperience };
});

function dispatchInput(element) {
  element.dispatchEvent(new Event('input', { bubbles: true }));
}

beforeAll(async () => {
  const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');
  const parsed = new DOMParser().parseFromString(html, 'text/html');
  document.documentElement.innerHTML = parsed.documentElement.innerHTML;
  await import('./main.js');
});

describe('BLACK HOLE interface', () => {
  it('shows all five lessons and starts the tidal-disruption sequence from the lesson panel', () => {
    expect(document.querySelectorAll('.lesson-tab')).toHaveLength(5);
    document.querySelector('#knowledgeButton').click();
    expect(document.querySelector('#knowledgePanel').getAttribute('aria-hidden')).toBe('false');

    document.querySelector('[data-lesson="tidal-disruption"]').click();
    expect(document.querySelector('#lessonTitle').textContent).toBe('گسیختگی کشندی');
    expect(document.querySelector('#lessonAction').hidden).toBe(false);
    document.querySelector('#lessonAction').click();

    expect(document.querySelector('#knowledgePanel').getAttribute('aria-hidden')).toBe('true');
    expect(document.querySelector('#demoPanel').hidden).toBe(false);
    expect(document.querySelector('#sceneStatus').textContent).toBe('گسیختگی کشندی');
    expect(testEngine.instance.demoTime).toBe(0);

    document.querySelector('#demoPlayButton').click();
    expect(document.querySelector('#demoPlayButton').getAttribute('aria-label')).toBe('ادامه‌ی نمایش');
    document.querySelector('#demoPlayButton').click();
    expect(document.querySelector('#demoPlayButton').getAttribute('aria-label')).toBe('مکث نمایش');
  });

  it('toggles lensing and updates the before/after comparison slider', () => {
    const lensButton = document.querySelector('#lensButton');
    expect(lensButton.getAttribute('aria-pressed')).toBe('true');
    lensButton.click();
    expect(lensButton.getAttribute('aria-pressed')).toBe('false');
    expect(document.querySelector('#lensStatus').textContent).toBe('مسیر نور بدون خمیدگی');
    lensButton.click();

    document.querySelector('#compareButton').click();
    expect(document.querySelector('#comparisonPanel').hidden).toBe(false);
    const slider = document.querySelector('#lensingRange');
    slider.value = '32';
    dispatchInput(slider);
    expect(testEngine.instance.lensing).toBeCloseTo(0.32);
    expect(document.querySelector('#lensingPercent').textContent).toBe('۳۲٪');
    document.querySelector('#closeComparison').click();
    expect(document.querySelector('#comparisonPanel').hidden).toBe(true);
  });

  it('applies camera-quality settings, camera actions, fullscreen, and cinematic mode', () => {
    document.querySelector('#settingsButton').click();
    const rotation = document.querySelector('#rotationRange');
    rotation.value = '55';
    dispatchInput(rotation);
    expect(testEngine.instance.rotationSpeed).toBeCloseTo(0.55);

    const particles = document.querySelector('#particleRange');
    particles.value = '25';
    dispatchInput(particles);
    expect(testEngine.instance.particleIntensity).toBeCloseTo(0.25);

    document.querySelector('#qualityButton').click();
    expect(document.querySelector('#qualityButton').textContent).toBe('بالاترین');
    expect(document.querySelector('#qualityDescription').textContent).toContain('وضوح');

    document.querySelector('#zoomInButton').click();
    document.querySelector('#zoomOutButton').click();
    document.querySelector('#angleButton').click();
    document.querySelector('#resetCameraButton').click();
    expect(testEngine.instance.zoomCalls).toEqual([1, -1]);
    expect(testEngine.instance.cameraPresetCount).toBe(1);
    expect(testEngine.instance.cameraReset).toBe(true);

    const app = document.querySelector('#app');
    app.requestFullscreen = vi.fn().mockResolvedValue(undefined);
    document.querySelector('#fullscreenButton').click();
    expect(app.requestFullscreen).toHaveBeenCalledOnce();

    document.querySelector('#cinematicButton').click();
    expect(document.querySelector('#app').classList.contains('cinematic')).toBe(true);
    document.querySelector('#cinematicExit').click();
    expect(document.querySelector('#app').classList.contains('cinematic')).toBe(false);
  });
});
