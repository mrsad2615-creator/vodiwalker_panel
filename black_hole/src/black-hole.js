import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

const STAR_VERTEX_SHADER = /* glsl */ `
  attribute float aSize;
  attribute float aTwinkle;
  attribute vec3 aColor;
  uniform float uTime;
  uniform float uLensing;
  uniform float uPixelRatio;
  varying float vTwinkle;
  varying vec3 vColor;
  void main() {
    vec4 viewPosition = modelViewMatrix * vec4(position, 1.0);
    vec4 clipPosition = projectionMatrix * viewPosition;
    vec2 projected = clipPosition.xy / max(clipPosition.w, 0.001);
    float radius = length(projected);
    vec2 radialDirection = projected / max(radius, 0.0001);
    float lensField = exp(-radius * radius * 18.0);
    float deflection = uLensing * lensField * (0.012 + 0.17 / (radius + 0.19));
    clipPosition.xy += radialDirection * deflection * clipPosition.w;
    gl_Position = clipPosition;
    gl_PointSize = clamp(aSize * uPixelRatio * (52.0 / max(12.0, -viewPosition.z)), 1.0, 7.0);
    vTwinkle = aTwinkle;
    vColor = aColor;
  }
`;

const STAR_FRAGMENT_SHADER = /* glsl */ `
  uniform float uTime;
  varying float vTwinkle;
  varying vec3 vColor;
  void main() {
    vec2 p = gl_PointCoord - 0.5;
    float r = length(p);
    float core = exp(-r * r * 48.0);
    float halo = exp(-r * r * 13.0);
    float glint = exp(-abs(p.x * p.y) * 34.0) * exp(-r * 7.0) * 0.17;
    float pulse = 0.76 + 0.24 * sin(uTime * (0.42 + vTwinkle * 0.5) + vTwinkle * 40.0);
    float alpha = (core * 0.94 + halo * 0.34 + glint) * pulse;
    gl_FragColor = vec4(vColor * (0.72 + core * 1.25) * pulse, alpha);
    #include <colorspace_fragment>
  }
`;

const DISK_VERTEX_SHADER = /* glsl */ `
  varying vec2 vLocal;
  void main() {
    vLocal = position.xy;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

const DISK_FRAGMENT_SHADER = /* glsl */ `
  uniform float uTime;
  uniform float uIntensity;
  uniform float uLayer;
  varying vec2 vLocal;

  float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
  }
  float noise21(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
  }
  void main() {
    float radius = length(vLocal);
    float angle = atan(vLocal.y, vLocal.x);
    float n = noise21(vec2(angle * 5.0 + log(max(radius, 0.02)) * 5.0 - uTime * 0.17,
                           radius * 8.5 + uTime * 0.12));
    float winding = angle * 11.0 + log(max(radius, 0.02)) * 10.5 - uTime * 0.77 + n * 3.4;
    float filaments = pow(max(0.0, 0.5 + 0.5 * sin(winding)), 7.0);
    float fineThreads = pow(max(0.0, sin(angle * 21.0 + radius * 7.0 - uTime * 0.49 + n * 5.0)), 12.0);
    float innerEdge = smoothstep(1.13, 1.39, radius);
    float outerEdge = 1.0 - smoothstep(4.45, 5.62, radius);
    float radialFade = innerEdge * outerEdge;
    float turbulent = clamp(0.34 + n * 0.60 + filaments * 0.68 + fineThreads * 0.24, 0.0, 1.72);
    float layerBoost = mix(1.0, 0.72, uLayer);
    vec3 ember = vec3(1.48, 0.25, 0.035);
    vec3 gold = vec3(1.42, 0.58, 0.17);
    vec3 pale = vec3(1.42, 0.82, 0.42);
    vec3 blue = vec3(0.08, 0.32, 0.76);
    vec3 color = mix(ember, gold, smoothstep(1.34, 2.36, radius));
    color = mix(color, pale, smoothstep(1.48, 1.93, radius) * 0.16);
    color = mix(color, blue, smoothstep(4.12, 5.56, radius) * 0.38);
    float alpha = radialFade * (0.18 + turbulent * 0.35) * layerBoost * uIntensity;
    vec3 plasma = color * (0.37 + turbulent * 0.92) * alpha;
    gl_FragColor = vec4(plasma, alpha);
    #include <colorspace_fragment>
  }
`;

const HAZE_FRAGMENT_SHADER = /* glsl */ `
  uniform float uTime;
  uniform float uLensing;
  varying vec2 vLocal;
  void main() {
    float radius = length(vLocal);
    float photonRing = exp(-pow((radius - 1.23) * 7.3, 2.0));
    float broadHalo = exp(-pow((radius - 1.63) * 1.72, 2.0));
    float outerGlow = exp(-pow((radius - 2.35) * 0.78, 2.0));
    float angle = atan(vLocal.y, vLocal.x);
    float hotArc = 0.62 + 0.38 * sin(angle * 3.0 + uTime * 0.18);
    float intensity = uLensing * (photonRing * 0.52 + broadHalo * 0.20 + outerGlow * 0.052) * hotArc;
    vec3 amber = vec3(1.0, 0.40, 0.12);
    vec3 cyan = vec3(0.22, 0.66, 1.0);
    vec3 color = mix(amber, cyan, smoothstep(-0.2, 0.8, vLocal.y / max(radius, 0.001)) * 0.36);
    gl_FragColor = vec4(color * intensity, intensity * 0.68);
    #include <colorspace_fragment>
  }
`;

const PHOTON_VERTEX_SHADER = /* glsl */ `
  varying vec2 vLocal;
  void main() {
    vLocal = position.xy;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

const PHOTON_FRAGMENT_SHADER = /* glsl */ `
  uniform float uTime;
  uniform float uLensing;
  varying vec2 vLocal;
  void main() {
    float radius = length(vLocal);
    float angle = atan(vLocal.y, vLocal.x);
    float arcs = 0.54 + 0.46 * pow(max(0.0, sin(angle * 3.0 - uTime * 0.23)), 2.0);
    float seam = 0.74 + 0.26 * sin(angle * 17.0 + uTime * 0.42);
    vec3 orange = vec3(1.18, 0.51, 0.20);
    vec3 blue = vec3(0.30, 0.79, 1.16);
    vec3 color = mix(orange, blue, smoothstep(-0.1, 0.72, vLocal.y / max(radius, 0.001)) * 0.30);
    float alpha = arcs * seam * (0.15 + 0.36 * uLensing);
    gl_FragColor = vec4(color * alpha * 0.72, alpha);
    #include <colorspace_fragment>
  }
`;

const DUST_VERTEX_SHADER = /* glsl */ `
  attribute float aSize;
  attribute float aSeed;
  attribute vec3 aColor;
  uniform float uTime;
  uniform float uIntensity;
  uniform float uPixelRatio;
  varying float vSeed;
  varying vec3 vColor;
  varying float vIntensity;
  void main() {
    float radius = length(position.xy);
    float angle = atan(position.y, position.x) + uTime * (0.045 + aSeed * 0.095);
    vec3 p = vec3(cos(angle) * radius, sin(angle) * radius,
                  position.z + sin(uTime * 0.73 + aSeed * 41.0) * 0.045);
    vec4 mv = modelViewMatrix * vec4(p, 1.0);
    gl_Position = projectionMatrix * mv;
    gl_PointSize = clamp(aSize * uPixelRatio * (10.0 / max(7.0, -mv.z)), 0.45, 8.0);
    vSeed = aSeed;
    vColor = aColor;
    vIntensity = uIntensity;
  }
`;

const DUST_FRAGMENT_SHADER = /* glsl */ `
  uniform float uTime;
  varying float vSeed;
  varying vec3 vColor;
  varying float vIntensity;
  void main() {
    float r = length(gl_PointCoord - 0.5);
    float soft = exp(-r * r * 29.0);
    float core = exp(-r * r * 112.0);
    float pulse = 0.64 + 0.36 * sin(uTime * 0.65 + vSeed * 52.0);
    float alpha = (soft * 0.31 + core * 0.95) * pulse * vIntensity;
    gl_FragColor = vec4(vColor * (0.40 + core * 1.1) * alpha, alpha);
    #include <colorspace_fragment>
  }
`;

const STAR_SURFACE_VERTEX = /* glsl */ `
  uniform float uTidal;
  varying vec3 vNormal;
  varying vec3 vPosition;
  void main() {
    vec3 p = position;
    float stretch = 1.0 + uTidal * 2.2;
    p.x *= stretch;
    p.yz *= 1.0 - uTidal * 0.47;
    p.x += uTidal * (position.x * position.x - 0.16) * 0.25;
    vPosition = p;
    vNormal = normalize(normalMatrix * normal);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.0);
  }
`;

const STAR_SURFACE_FRAGMENT = /* glsl */ `
  uniform float uTidal;
  uniform float uOpacity;
  uniform float uTime;
  varying vec3 vNormal;
  varying vec3 vPosition;
  float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
  }
  void main() {
    float n = hash3(floor(vPosition * 28.0 + uTime * 0.12));
    float facing = max(0.0, dot(normalize(vNormal), normalize(vec3(-0.35, 0.62, 0.92))));
    vec3 surface = mix(vec3(1.42, 0.22, 0.035), vec3(1.78, 1.08, 0.53), smoothstep(0.08, 0.94, facing + n * 0.13));
    float limb = 0.48 + 0.62 * facing;
    float plasma = 0.84 + n * 0.28 + 0.10 * sin(vPosition.y * 38.0 + uTime * 1.2);
    gl_FragColor = vec4(surface * limb * plasma, uOpacity);
    #include <colorspace_fragment>
  }
`;

const DEBRIS_VERTEX_SHADER = /* glsl */ `
  attribute float aSize;
  attribute float aSeed;
  uniform float uTime;
  uniform float uPixelRatio;
  varying float vSeed;
  void main() {
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mv;
    gl_PointSize = clamp(aSize * uPixelRatio * (10.0 / max(7.0, -mv.z)), 1.0, 9.0);
    vSeed = aSeed;
  }
`;

const DEBRIS_FRAGMENT_SHADER = /* glsl */ `
  uniform float uTime;
  varying float vSeed;
  void main() {
    float r = length(gl_PointCoord - 0.5);
    float core = exp(-r * r * 48.0);
    float halo = exp(-r * r * 13.0);
    float pulse = 0.63 + 0.37 * sin(uTime * 1.7 + vSeed * 55.0);
    float alpha = (core * 0.84 + halo * 0.34) * pulse;
    vec3 color = mix(vec3(1.0, 0.32, 0.08), vec3(1.0, 0.82, 0.52), vSeed);
    gl_FragColor = vec4(color * alpha * 1.5, alpha);
    #include <colorspace_fragment>
  }
`;

const clamp01 = (value) => Math.max(0, Math.min(1, value));
const easeBetween = (start, end, value) => {
  const t = clamp01((value - start) / (end - start));
  return t * t * (3 - 2 * t);
};

function glowTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const context = canvas.getContext('2d');
  const gradient = context.createRadialGradient(64, 64, 2, 64, 64, 62);
  gradient.addColorStop(0, 'rgba(255,248,232,1)');
  gradient.addColorStop(0.12, 'rgba(255,181,103,.82)');
  gradient.addColorStop(0.34, 'rgba(255,105,44,.30)');
  gradient.addColorStop(1, 'rgba(255,67,24,0)');
  context.fillStyle = gradient;
  context.fillRect(0, 0, 128, 128);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function randomUnitVector() {
  const z = Math.random() * 2 - 1;
  const angle = Math.random() * Math.PI * 2;
  const planar = Math.sqrt(1 - z * z);
  return [planar * Math.cos(angle), planar * Math.sin(angle), z];
}

export class BlackHoleExperience {
  constructor(container, { onFrame = () => {}, onUnavailable = () => {} } = {}) {
    this.container = container;
    this.onFrame = onFrame;
    this.onUnavailable = onUnavailable;
    this.running = false;
    this.raf = 0;
    this.lastFrame = 0;
    this.elapsed = 0;
    this.rotationSpeed = 1;
    this.lensingAmount = 1;
    this.particleIntensity = 0.7;
    this.demoTime = -1;
    this.viewIndex = 0;
    this.transition = null;
    this.qualityMode = 'auto';
    this.autoScale = 1;
    this.autoFrames = 0;
    this.autoElapsed = 0;
    this.autoGoodSeconds = 0;
    this.autoBadSeconds = 0;

    try {
      this.initialize();
    } catch (error) {
      this.destroyPartial();
      this.onUnavailable(error);
      throw error;
    }
  }

  initialize() {
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(43, 1, 0.1, 180);
    this.camera.position.set(0, 1.75, 8.6);
    this.defaultPosition = this.camera.position.clone();
    this.defaultTarget = new THREE.Vector3(0, 0, 0);

    this.renderer = new THREE.WebGLRenderer({
      alpha: true,
      antialias: true,
      powerPreference: 'high-performance',
      premultipliedAlpha: false,
      stencil: false,
      depth: true,
    });
    this.renderer.setClearColor(0x03050b, 0);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.12;
    this.renderer.domElement.setAttribute('aria-hidden', 'true');
    this.renderer.domElement.className = 'universe-canvas';
    this.container.prepend(this.renderer.domElement);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.target.copy(this.defaultTarget);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.055;
    this.controls.enablePan = false;
    this.controls.minDistance = 4.8;
    this.controls.maxDistance = 15.8;
    this.controls.minPolarAngle = 0.2;
    this.controls.maxPolarAngle = Math.PI * 0.83;
    this.controls.autoRotate = true;
    this.controls.autoRotateSpeed = 0.55;
    this.controls.update();

    this.buildStarField();
    this.buildDisk();
    this.buildEventHorizon();
    this.buildDisruptionDemo();
    this.applyQuality();

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.container);
    this.onVisibilityChange = () => {
      this.lastFrame = 0;
      if (!document.hidden && !this.running) this.start();
    };
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    this.onContextLost = (event) => {
      event.preventDefault();
      this.stop();
      this.onUnavailable(new Error('WebGL context lost'));
    };
    this.renderer.domElement.addEventListener('webglcontextlost', this.onContextLost, false);
    this.resize();
    this.start();
  }

  buildStarField() {
    const count = 1550;
    const positions = new Float32Array(count * 3);
    const sizes = new Float32Array(count);
    const twinkles = new Float32Array(count);
    const colors = new Float32Array(count * 3);
    const palette = [
      new THREE.Color('#eaf3ff'),
      new THREE.Color('#afcfff'),
      new THREE.Color('#ffe0bd'),
      new THREE.Color('#72dfff'),
    ];

    for (let i = 0; i < count; i += 1) {
      const [x, y, z] = randomUnitVector();
      const distance = 50 + Math.random() * 42;
      positions.set([x * distance, y * distance, z * distance], i * 3);
      sizes[i] = 0.85 + Math.random() * 2.15;
      twinkles[i] = Math.random();
      const color = palette[Math.random() < 0.74 ? 0 : Math.floor(Math.random() * palette.length)];
      colors.set([color.r, color.g, color.b], i * 3);
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('aSize', new THREE.BufferAttribute(sizes, 1));
    geometry.setAttribute('aTwinkle', new THREE.BufferAttribute(twinkles, 1));
    geometry.setAttribute('aColor', new THREE.BufferAttribute(colors, 3));
    const material = new THREE.ShaderMaterial({
      uniforms: {
        uTime: { value: 0 },
        uLensing: { value: this.lensingAmount },
        uPixelRatio: { value: 1 },
      },
      vertexShader: STAR_VERTEX_SHADER,
      fragmentShader: STAR_FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      depthTest: true,
      blending: THREE.AdditiveBlending,
      toneMapped: false,
    });
    this.starMaterial = material;
    this.starField = new THREE.Points(geometry, material);
    this.starField.frustumCulled = false;
    this.scene.add(this.starField);
  }

  buildDisk() {
    this.diskGroup = new THREE.Group();
    this.diskGroup.rotation.x = Math.PI / 2;
    this.scene.add(this.diskGroup);

    // The photon ring and diffuse lens glow face the camera, while the hot disk
    // stays in its own tilted orbital plane.
    this.lensGroup = new THREE.Group();
    this.scene.add(this.lensGroup);

    const haze = new THREE.Mesh(
      new THREE.PlaneGeometry(12, 12),
      new THREE.ShaderMaterial({
        uniforms: {
          uTime: { value: 0 },
          uLensing: { value: this.lensingAmount },
        },
        vertexShader: DISK_VERTEX_SHADER,
        fragmentShader: HAZE_FRAGMENT_SHADER,
        transparent: true,
        depthWrite: false,
        depthTest: true,
        blending: THREE.AdditiveBlending,
        side: THREE.DoubleSide,
        toneMapped: false,
      }),
    );
    haze.position.z = -0.42;
    haze.renderOrder = 1;
    this.hazeMaterial = haze.material;
    this.lensGroup.add(haze);

    const outerMaterial = this.createDiskMaterial(0);
    const outerRing = new THREE.Mesh(new THREE.RingGeometry(1.19, 5.56, 480, 3), outerMaterial);
    outerRing.renderOrder = 2;
    this.diskGroup.add(outerRing);
    this.diskOuterMaterial = outerMaterial;

    const innerMaterial = this.createDiskMaterial(1);
    const innerRing = new THREE.Mesh(new THREE.RingGeometry(1.13, 3.15, 360, 3), innerMaterial);
    innerRing.position.z = 0.015;
    innerRing.renderOrder = 3;
    this.diskGroup.add(innerRing);
    this.diskInnerMaterial = innerMaterial;

    const photonRing = new THREE.Mesh(
      new THREE.RingGeometry(1.055, 1.15, 384, 3),
      new THREE.ShaderMaterial({
        uniforms: {
          uTime: { value: 0 },
          uLensing: { value: this.lensingAmount },
        },
        vertexShader: PHOTON_VERTEX_SHADER,
        fragmentShader: PHOTON_FRAGMENT_SHADER,
        transparent: true,
        depthWrite: false,
        side: THREE.DoubleSide,
        blending: THREE.AdditiveBlending,
        toneMapped: false,
      }),
    );
    photonRing.position.z = 0.025;
    photonRing.renderOrder = 5;
    this.photonMaterial = photonRing.material;
    this.lensGroup.add(photonRing);

    this.buildDust();
  }

  createDiskMaterial(layer) {
    return new THREE.ShaderMaterial({
      uniforms: {
        uTime: { value: 0 },
        uIntensity: { value: this.particleIntensity },
        uLayer: { value: layer },
      },
      vertexShader: DISK_VERTEX_SHADER,
      fragmentShader: DISK_FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide,
      blending: THREE.AdditiveBlending,
      toneMapped: false,
    });
  }

  buildDust() {
    const count = 2100;
    const positions = new Float32Array(count * 3);
    const colors = new Float32Array(count * 3);
    const sizes = new Float32Array(count);
    const seeds = new Float32Array(count);
    const warm = new THREE.Color('#ffb56c');
    const pale = new THREE.Color('#eff5ff');
    const cool = new THREE.Color('#62caff');

    for (let i = 0; i < count; i += 1) {
      const angle = Math.random() * Math.PI * 2;
      const radius = 1.28 + Math.random() * 4.25;
      positions.set([
        Math.cos(angle) * radius,
        Math.sin(angle) * radius,
        (Math.random() - 0.5) * 0.16,
      ], i * 3);
      const selection = Math.random();
      const color = selection < 0.68 ? warm : selection < 0.88 ? pale : cool;
      colors.set([color.r, color.g, color.b], i * 3);
      sizes[i] = 1.0 + Math.random() * 2.8;
      seeds[i] = Math.random();
    }

    this.dustGeometry = new THREE.BufferGeometry();
    this.dustGeometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    this.dustGeometry.setAttribute('aColor', new THREE.BufferAttribute(colors, 3));
    this.dustGeometry.setAttribute('aSize', new THREE.BufferAttribute(sizes, 1));
    this.dustGeometry.setAttribute('aSeed', new THREE.BufferAttribute(seeds, 1));
    this.dustMaterial = new THREE.ShaderMaterial({
      uniforms: {
        uTime: { value: 0 },
        uIntensity: { value: this.particleIntensity },
        uPixelRatio: { value: 1 },
      },
      vertexShader: DUST_VERTEX_SHADER,
      fragmentShader: DUST_FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      toneMapped: false,
    });
    this.dust = new THREE.Points(this.dustGeometry, this.dustMaterial);
    this.dust.frustumCulled = false;
    this.dust.renderOrder = 4;
    this.diskGroup.add(this.dust);
  }

  buildEventHorizon() {
    this.holeGroup = new THREE.Group();
    this.scene.add(this.holeGroup);

    const texture = glowTexture();
    this.haloTexture = texture;
    const halo = new THREE.Sprite(new THREE.SpriteMaterial({
      map: texture,
      color: '#ff8a4a',
      opacity: 0.46,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      toneMapped: false,
    }));
    halo.position.set(0, 0, 0);
    halo.scale.set(4.7, 4.7, 1);
    halo.renderOrder = 0;
    this.holeGroup.add(halo);

    const shadowRim = new THREE.Mesh(
      new THREE.SphereGeometry(1.075, 96, 72),
      new THREE.MeshBasicMaterial({ color: '#010208', depthWrite: true, toneMapped: false }),
    );
    shadowRim.renderOrder = 6;
    this.holeGroup.add(shadowRim);

    const horizon = new THREE.Mesh(
      new THREE.SphereGeometry(1.045, 96, 72),
      new THREE.MeshBasicMaterial({ color: '#000105', depthWrite: true, toneMapped: false }),
    );
    horizon.renderOrder = 7;
    this.holeGroup.add(horizon);

    const edge = new THREE.Mesh(
      new THREE.RingGeometry(1.045, 1.075, 360, 3),
      new THREE.MeshBasicMaterial({
        color: '#0a1321',
        transparent: true,
        opacity: 0.74,
        side: THREE.DoubleSide,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        toneMapped: false,
      }),
    );
    edge.position.z = 0.04;
    edge.renderOrder = 8;
    this.holeGroup.add(edge);
  }

  buildDisruptionDemo() {
    this.starGroup = new THREE.Group();
    this.starGroup.visible = false;

    this.starSurfaceMaterial = new THREE.ShaderMaterial({
      uniforms: {
        uTidal: { value: 0 },
        uOpacity: { value: 1 },
        uTime: { value: 0 },
      },
      vertexShader: STAR_SURFACE_VERTEX,
      fragmentShader: STAR_SURFACE_FRAGMENT,
      transparent: true,
      depthWrite: false,
      side: THREE.FrontSide,
      blending: THREE.NormalBlending,
      toneMapped: false,
    });
    const star = new THREE.Mesh(new THREE.SphereGeometry(0.33, 48, 36), this.starSurfaceMaterial);
    this.starGroup.add(star);
    this.starMesh = star;

    const starGlow = new THREE.Sprite(new THREE.SpriteMaterial({
      map: this.haloTexture,
      color: '#ffbb72',
      opacity: 0.72,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      toneMapped: false,
    }));
    starGlow.scale.set(1.75, 1.75, 1);
    starGlow.position.z = -0.18;
    this.starGroup.add(starGlow);
    this.starGlow = starGlow;
    this.scene.add(this.starGroup);

    this.streams = [];
    for (let branch = 0; branch < 2; branch += 1) {
      const pointCount = 84;
      const geometry = new THREE.BufferGeometry();
      const position = new THREE.BufferAttribute(new Float32Array(pointCount * 3), 3);
      position.setUsage(THREE.DynamicDrawUsage);
      geometry.setAttribute('position', position);
      const material = new THREE.LineBasicMaterial({
        color: branch === 0 ? '#ffb56d' : '#ff7136',
        transparent: true,
        opacity: 0,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        toneMapped: false,
      });
      const line = new THREE.Line(geometry, material);
      line.frustumCulled = false;
      line.visible = false;
      line.renderOrder = 6;
      this.scene.add(line);
      this.streams.push({ line, branch, pointCount });
    }

    const count = 460;
    const debrisPositions = new Float32Array(count * 3);
    const debrisSizes = new Float32Array(count);
    const debrisSeeds = new Float32Array(count);
    for (let i = 0; i < count; i += 1) {
      debrisPositions[i * 3] = 2.2;
      debrisPositions[i * 3 + 1] = (Math.random() - 0.5) * 0.2;
      debrisPositions[i * 3 + 2] = 0;
      debrisSizes[i] = 2.2 + Math.random() * 5.6;
      debrisSeeds[i] = Math.random();
    }
    this.debrisGeometry = new THREE.BufferGeometry();
    this.debrisGeometry.setAttribute('position', new THREE.BufferAttribute(debrisPositions, 3));
    this.debrisGeometry.getAttribute('position').setUsage(THREE.DynamicDrawUsage);
    this.debrisGeometry.setAttribute('aSize', new THREE.BufferAttribute(debrisSizes, 1));
    this.debrisGeometry.setAttribute('aSeed', new THREE.BufferAttribute(debrisSeeds, 1));
    this.debrisMaterial = new THREE.ShaderMaterial({
      uniforms: {
        uTime: { value: 0 },
        uPixelRatio: { value: 1 },
      },
      vertexShader: DEBRIS_VERTEX_SHADER,
      fragmentShader: DEBRIS_FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      toneMapped: false,
    });
    this.debris = new THREE.Points(this.debrisGeometry, this.debrisMaterial);
    this.debris.frustumCulled = false;
    this.debris.visible = false;
    this.debris.renderOrder = 7;
    this.scene.add(this.debris);
  }

  start() {
    if (this.running || !this.renderer) return;
    this.running = true;
    this.raf = requestAnimationFrame((now) => this.animate(now));
  }

  stop() {
    this.running = false;
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
  }

  animate(now) {
    if (!this.running) return;
    const frameTime = this.lastFrame ? Math.max(0, (now - this.lastFrame) / 1000) : 1 / 60;
    const delta = Math.min(frameTime, 0.5);
    this.lastFrame = now;
    this.elapsed += delta * this.rotationSpeed;

    this.updateCameraTransition(delta);
    this.controls.update(delta);
    this.lensGroup.quaternion.copy(this.camera.quaternion);
    this.updateUniforms(delta);
    this.updateDisruption();
    this.onFrame(delta);
    this.renderer.render(this.scene, this.camera);
    this.samplePerformance(Math.min(frameTime, 1));
    this.raf = requestAnimationFrame((next) => this.animate(next));
  }

  updateUniforms() {
    const time = this.elapsed;
    this.starMaterial.uniforms.uTime.value = time;
    this.dustMaterial.uniforms.uTime.value = time;
    this.diskOuterMaterial.uniforms.uTime.value = time;
    this.diskInnerMaterial.uniforms.uTime.value = time * 1.08;
    this.hazeMaterial.uniforms.uTime.value = time;
    this.photonMaterial.uniforms.uTime.value = time;
    this.debrisMaterial.uniforms.uTime.value = time;
    this.starSurfaceMaterial.uniforms.uTime.value = time;
  }

  updateDisruption() {
    if (this.demoTime < 0) {
      this.starGroup.visible = false;
      this.debris.visible = false;
      for (const { line } of this.streams) line.visible = false;
      this.diskOuterMaterial.uniforms.uIntensity.value = this.particleIntensity;
      this.diskInnerMaterial.uniforms.uIntensity.value = this.particleIntensity * 0.82;
      return;
    }

    const time = Math.max(0, this.demoTime);
    const travel = easeBetween(0.5, 11.2, time);
    const stretch = easeBetween(3.5, 8.8, time);
    const dissolve = easeBetween(8.2, 11.9, time);
    const starX = THREE.MathUtils.lerp(7.65, 1.48, travel);
    const starY = 0.50 + Math.sin(time * 0.56) * 0.12;
    const starZ = 0.17;

    this.starGroup.position.set(starX, starY, starZ);
    this.starGroup.rotation.z = -0.08 - stretch * 0.16;
    this.starGroup.visible = time < 12.25;
    this.starSurfaceMaterial.uniforms.uTidal.value = stretch;
    this.starSurfaceMaterial.uniforms.uOpacity.value = 1.0 - dissolve;
    this.starGlow.material.opacity = (0.74 + stretch * 0.32) * (1.0 - dissolve);
    this.starGlow.scale.set(1.75 + stretch * 0.68, 1.75 + stretch * 0.2, 1);

    const streamOpacity = (easeBetween(3.3, 5.1, time) * (1.0 - easeBetween(14.2, 18.0, time))) * 0.64;
    for (const { line, branch, pointCount } of this.streams) {
      line.visible = time > 3.1 && time < 18.0;
      line.material.opacity = streamOpacity * (branch === 0 ? 1 : 0.74);
      const positions = line.geometry.getAttribute('position');
      for (let i = 0; i < pointCount; i += 1) {
        const u = i / (pointCount - 1);
        const bend = Math.sin(u * Math.PI) * (branch === 0 ? 0.27 : -0.22);
        positions.setXYZ(
          i,
          THREE.MathUtils.lerp(starX, 1.12, u),
          THREE.MathUtils.lerp(starY, 0.03, u) + bend,
          starZ + 0.025 + Math.sin(u * Math.PI * 2 + this.elapsed * 0.3) * 0.035,
        );
      }
      positions.needsUpdate = true;
      line.geometry.computeBoundingSphere();
    }

    const debrisPositions = this.debrisGeometry.getAttribute('position');
    const streamReady = easeBetween(4.2, 7.0, time);
    this.debris.visible = time > 4.2 && time < 18.0;
    for (let i = 0; i < debrisPositions.count; i += 1) {
      const seed = this.debrisGeometry.getAttribute('aSeed').getX(i);
      const flow = (seed + time * 0.19) % 1;
      const width = (1 - flow) * (0.08 + stretch * 0.14) * streamReady;
      const swirl = Math.sin(flow * Math.PI * 3 + seed * 24 + this.elapsed * 1.2) * width;
      const x = THREE.MathUtils.lerp(starX, 1.12, flow);
      const y = THREE.MathUtils.lerp(starY, 0.02, flow) + swirl;
      const z = starZ + Math.cos(flow * Math.PI * 2 + seed * 13) * width * 0.58;
      debrisPositions.setXYZ(i, x, y, z);
    }
    debrisPositions.needsUpdate = true;

    const accretionBoost = 1 + easeBetween(9.0, 15.0, time) * 0.42;
    this.diskOuterMaterial.uniforms.uIntensity.value = this.particleIntensity * accretionBoost;
    this.diskInnerMaterial.uniforms.uIntensity.value = this.particleIntensity * 0.82 * accretionBoost;
  }

  updateCameraTransition(delta) {
    if (!this.transition) return;
    const transition = this.transition;
    transition.elapsed += delta;
    const raw = clamp01(transition.elapsed / transition.duration);
    const eased = raw * raw * (3 - 2 * raw);
    this.camera.position.lerpVectors(transition.fromPosition, transition.toPosition, eased);
    this.controls.target.lerpVectors(transition.fromTarget, transition.toTarget, eased);
    if (raw >= 1) {
      this.transition = null;
      this.controls.autoRotate = this.rotationSpeed > 0.1;
    }
  }

  samplePerformance(delta) {
    if (this.qualityMode !== 'auto') return;
    this.autoElapsed += delta;
    this.autoFrames += 1;
    if (this.autoElapsed < 2.5) return;
    const fps = this.autoFrames / this.autoElapsed;
    if (fps < 42) {
      this.autoBadSeconds += this.autoElapsed;
      this.autoGoodSeconds = 0;
      if (this.autoBadSeconds > 3 && this.autoScale > 0.7) {
        this.autoScale = Math.max(0.7, this.autoScale * 0.86);
        this.applyQuality();
        this.autoBadSeconds = 0;
      }
    } else if (fps > 57) {
      this.autoGoodSeconds += this.autoElapsed;
      this.autoBadSeconds = 0;
      if (this.autoGoodSeconds > 9 && this.autoScale < 1) {
        this.autoScale = Math.min(1, this.autoScale + 0.08);
        this.applyQuality();
        this.autoGoodSeconds = 0;
      }
    } else {
      this.autoBadSeconds = 0;
      this.autoGoodSeconds = 0;
    }
    this.autoElapsed = 0;
    this.autoFrames = 0;
  }

  resize() {
    if (!this.renderer || !this.camera) return;
    const width = Math.max(1, this.container.clientWidth || window.innerWidth);
    const height = Math.max(1, this.container.clientHeight || window.innerHeight);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
    this.updatePixelUniforms();
  }

  updatePixelUniforms() {
    if (!this.renderer) return;
    const pixelRatio = this.renderer.getPixelRatio();
    this.starMaterial.uniforms.uPixelRatio.value = pixelRatio;
    this.dustMaterial.uniforms.uPixelRatio.value = pixelRatio;
    this.debrisMaterial.uniforms.uPixelRatio.value = pixelRatio;
  }

  applyQuality() {
    if (!this.renderer) return;
    const width = this.container.clientWidth || window.innerWidth;
    const mobile = width < 720;
    const dpr = window.devicePixelRatio || 1;
    const cores = navigator.hardwareConcurrency || 4;
    const caps = {
      auto: Math.min(dpr, mobile ? 1.28 : (cores <= 4 ? 1.35 : 1.72)) * this.autoScale,
      high: Math.min(dpr, 1.9),
      balanced: Math.min(dpr, 1.35),
      low: Math.min(dpr, 0.95),
    };
    const ratio = Math.max(0.7, caps[this.qualityMode] || caps.auto);
    this.renderer.setPixelRatio(ratio);
    const particleCount = { auto: mobile ? 990 : 1450, high: 1850, balanced: 1120, low: 620 }[this.qualityMode] || 1450;
    this.dustGeometry.setDrawRange(0, particleCount);
    this.resize();
  }

  setQuality(mode) {
    const validModes = ['auto', 'high', 'balanced', 'low'];
    if (!validModes.includes(mode)) return this.qualityMode;
    this.qualityMode = mode;
    this.autoScale = 1;
    this.autoGoodSeconds = 0;
    this.autoBadSeconds = 0;
    this.applyQuality();
    return this.qualityMode;
  }

  cycleQuality() {
    const modes = ['auto', 'high', 'balanced', 'low'];
    const next = (modes.indexOf(this.qualityMode) + 1) % modes.length;
    return this.setQuality(modes[next]);
  }

  setLensingAmount(amount) {
    this.lensingAmount = clamp01(amount);
    const value = this.lensingAmount;
    this.starMaterial.uniforms.uLensing.value = value;
    this.hazeMaterial.uniforms.uLensing.value = value;
    this.photonMaterial.uniforms.uLensing.value = value;
  }

  setParticleIntensity(amount) {
    this.particleIntensity = clamp01(amount);
    this.dustMaterial.uniforms.uIntensity.value = this.particleIntensity;
    if (this.demoTime < 0) {
      this.diskOuterMaterial.uniforms.uIntensity.value = this.particleIntensity;
      this.diskInnerMaterial.uniforms.uIntensity.value = this.particleIntensity * 0.82;
    }
  }

  setRotationSpeed(amount) {
    const value = clamp01(amount);
    this.rotationSpeed = value === 0 ? 0 : 0.25 + value * 2.1;
    this.controls.autoRotate = value > 0.06;
    this.controls.autoRotateSpeed = value * 2.45;
  }

  setDemoTime(time) {
    this.demoTime = Number.isFinite(time) ? time : -1;
  }

  zoom(direction) {
    if (!this.controls) return;
    if (direction > 0) this.controls.dollyIn(1.22);
    else this.controls.dollyOut(1.22);
    this.controls.update();
  }

  resetCamera() {
    this.viewIndex = 0;
    this.moveCamera(this.defaultPosition, this.defaultTarget);
  }

  nextCameraView() {
    const presets = [
      new THREE.Vector3(0, 1.75, 8.6),
      new THREE.Vector3(5.35, 2.6, 6.6),
      new THREE.Vector3(-0.35, 6.9, 5.2),
    ];
    this.viewIndex = (this.viewIndex + 1) % presets.length;
    this.moveCamera(presets[this.viewIndex], this.defaultTarget);
    return this.viewIndex;
  }

  moveCamera(position, target) {
    this.controls.autoRotate = false;
    this.transition = {
      fromPosition: this.camera.position.clone(),
      toPosition: position.clone(),
      fromTarget: this.controls.target.clone(),
      toTarget: target.clone(),
      elapsed: 0,
      duration: 1.05,
    };
  }

  get qualityName() {
    return {
      auto: 'خودکار',
      high: 'بالاترین',
      balanced: 'متعادل',
      low: 'اقتصادی',
    }[this.qualityMode] || 'خودکار';
  }

  destroyPartial() {
    if (this.resizeObserver) this.resizeObserver.disconnect();
    if (this.controls) this.controls.dispose();
    if (this.renderer) {
      this.renderer.dispose();
      this.renderer.domElement?.remove();
    }
  }

  destroy() {
    this.stop();
    if (this.resizeObserver) this.resizeObserver.disconnect();
    if (this.onVisibilityChange) document.removeEventListener('visibilitychange', this.onVisibilityChange);
    if (this.renderer?.domElement && this.onContextLost) {
      this.renderer.domElement.removeEventListener('webglcontextlost', this.onContextLost, false);
    }
    if (this.controls) this.controls.dispose();
    if (this.scene) {
      this.scene.traverse((object) => {
        object.geometry?.dispose?.();
        const materials = Array.isArray(object.material) ? object.material : [object.material];
        for (const material of materials) {
          if (!material) continue;
          for (const value of Object.values(material)) {
            if (value && value.isTexture) value.dispose();
          }
          material.dispose?.();
        }
      });
    }
    this.renderer?.dispose();
    this.renderer?.domElement?.remove();
  }
}
