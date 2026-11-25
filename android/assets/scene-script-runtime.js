// WallpaperMobile scene script runtime: runs Wallpaper Engine SceneScript property scripts in an isolated JS context.
// The host calls wmInit(sceneJson) once and wmFrame(inputJson) per frame; both return JSON strings.
'use strict';

class Vec2 {
  constructor(x, y) {
    if (Array.isArray(x)) { y = x[1]; x = x[0]; }
    else if (x instanceof Object && x !== null) { y = x.y; x = x.x; }
    else if (typeof x === 'string') { const p = x.trim().split(/[\s,]+/).map(Number); x = p[0]; y = p[1]; }
    else if (y === undefined) y = x;
    this.x = +x || 0; this.y = +y || 0;
  }
  copy() { return new Vec2(this.x, this.y); }
  add(v) { return typeof v === 'number' ? new Vec2(this.x + v, this.y + v) : new Vec2(this.x + v.x, this.y + v.y); }
  subtract(v) { return typeof v === 'number' ? new Vec2(this.x - v, this.y - v) : new Vec2(this.x - v.x, this.y - v.y); }
  multiply(v) { return typeof v === 'number' ? new Vec2(this.x * v, this.y * v) : new Vec2(this.x * v.x, this.y * v.y); }
  divide(v) { return typeof v === 'number' ? new Vec2(this.x / v, this.y / v) : new Vec2(this.x / v.x, this.y / v.y); }
  dot(v) { return this.x * v.x + this.y * v.y; }
  length() { return Math.hypot(this.x, this.y); }
  lengthSqr() { return this.x * this.x + this.y * this.y; }
  normalize() { const l = this.length(); return l > 0 ? this.divide(l) : new Vec2(0, 0); }
  mix(v, t) { return new Vec2(this.x + (v.x - this.x) * t, this.y + (v.y - this.y) * t); }
  abs() { return new Vec2(Math.abs(this.x), Math.abs(this.y)); }
  round() { return new Vec2(Math.round(this.x), Math.round(this.y)); }
  equals(v) { return !!v && this.x === v.x && this.y === v.y; }
  toString() { return this.x + ' ' + this.y; }
}

class Vec3 {
  constructor(x, y, z) {
    if (Array.isArray(x)) { z = x[2]; y = x[1]; x = x[0]; }
    else if (x instanceof Object && x !== null) { z = x.z; y = x.y; x = x.x; }
    else if (typeof x === 'string') { const p = x.trim().split(/[\s,]+/).map(Number); x = p[0]; y = p[1]; z = p[2]; }
    else if (y === undefined && z === undefined) { y = x; z = x; }
    this.x = +x || 0; this.y = +y || 0; this.z = +z || 0;
  }
  copy() { return new Vec3(this.x, this.y, this.z); }
  add(v) { return typeof v === 'number' ? new Vec3(this.x + v, this.y + v, this.z + v) : new Vec3(this.x + v.x, this.y + v.y, this.z + (v.z || 0)); }
  subtract(v) { return typeof v === 'number' ? new Vec3(this.x - v, this.y - v, this.z - v) : new Vec3(this.x - v.x, this.y - v.y, this.z - (v.z || 0)); }
  multiply(v) { return typeof v === 'number' ? new Vec3(this.x * v, this.y * v, this.z * v) : new Vec3(this.x * v.x, this.y * v.y, this.z * (v.z === undefined ? 1 : v.z)); }
  divide(v) { return typeof v === 'number' ? new Vec3(this.x / v, this.y / v, this.z / v) : new Vec3(this.x / v.x, this.y / v.y, this.z / (v.z === undefined ? 1 : v.z)); }
  dot(v) { return this.x * v.x + this.y * v.y + this.z * (v.z || 0); }
  cross(v) { return new Vec3(this.y * v.z - this.z * v.y, this.z * v.x - this.x * v.z, this.x * v.y - this.y * v.x); }
  length() { return Math.hypot(this.x, this.y, this.z); }
  lengthSqr() { return this.x * this.x + this.y * this.y + this.z * this.z; }
  normalize() { const l = this.length(); return l > 0 ? this.divide(l) : new Vec3(0, 0, 0); }
  mix(v, t) { return new Vec3(this.x + (v.x - this.x) * t, this.y + (v.y - this.y) * t, this.z + ((v.z || 0) - this.z) * t); }
  abs() { return new Vec3(Math.abs(this.x), Math.abs(this.y), Math.abs(this.z)); }
  round() { return new Vec3(Math.round(this.x), Math.round(this.y), Math.round(this.z)); }
  equals(v) { return !!v && this.x === v.x && this.y === v.y && this.z === (v.z || 0); }
  toString() { return this.x + ' ' + this.y + ' ' + this.z; }
}

const WEMath = {
  PI: Math.PI, deg2rad: Math.PI / 180, rad2deg: 180 / Math.PI,
  mix(a, b, t) { return typeof a === 'number' ? a + (b - a) * t : a.mix(b, t); },
  lerp(a, b, t) { return WEMath.mix(a, b, t); },
  clamp(v, lo, hi) { return Math.min(hi, Math.max(lo, v)); },
  saturate(v) { return Math.min(1, Math.max(0, v)); },
  smoothStep(lo, hi, v) { const t = Math.min(1, Math.max(0, (v - lo) / (hi - lo))); return t * t * (3 - 2 * t); },
  step(edge, v) { return v < edge ? 0 : 1; },
  fract(v) { return v - Math.floor(v); },
  sign(v) { return Math.sign(v); },
};
const WEColor = {
  rgb2hsv(c) { const r = c.x, g = c.y, b = c.z, max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min; let h = 0;
    if (d > 0) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
    return new Vec3(((h / 6) + 1) % 1, max === 0 ? 0 : d / max, max); },
  hsv2rgb(c) { const h = c.x * 6, s = c.y, v = c.z, i = Math.floor(h), f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
    const k = ((i % 6) + 6) % 6; return [new Vec3(v, t, p), new Vec3(q, v, p), new Vec3(p, v, t), new Vec3(p, q, v), new Vec3(t, p, v), new Vec3(v, p, q)][k]; },
};

function createScriptProperties() {
  const values = {};
  const builder = {
    finish() { return values; },
  };
  for (const kind of ['addSlider', 'addCheckbox', 'addColor', 'addCombo', 'addText', 'addTextInput', 'addFile', 'addDirectory']) {
    builder[kind] = (spec) => { if (spec && spec.name !== undefined) values[spec.name] = kind === 'addColor' && typeof spec.value === 'string' ? new Vec3(spec.value) : spec.value; return builder; };
  }
  return builder;
}

const shared = {};
const store = new Map();
const localStorage = {
  LOCATION_GLOBAL: 0, LOCATION_SCREEN: 1,
  get(key) { return store.has(key) ? store.get(key) : undefined; },
  set(key, value) { store.set(key, value); },
  delete(key) { store.delete(key); },
  clear() { store.clear(); },
};
const console = { log() {}, warn() {}, error() {} };

const timers = [];
let timerId = 0;
const engine = {
  runtime: 0, frametime: 0, timeOfDay: 0, userProperties: {},
  canvasSize: new Vec2(1920, 1080), screenResolution: new Vec2(1080, 2400),
  AUDIO_RESOLUTION_16: 16, AUDIO_RESOLUTION_32: 32, AUDIO_RESOLUTION_64: 64,
  shared,
  setTimeout(fn, ms) { const id = ++timerId; timers.push({ id, fn, at: engine.runtime + (ms || 0) / 1000, every: 0 }); return id; },
  setInterval(fn, ms) { const id = ++timerId; const every = Math.max(0.001, (ms || 0) / 1000); timers.push({ id, fn, at: engine.runtime + every, every }); return id; },
  clearTimeout(id) { const i = timers.findIndex((t) => t.id === id); if (i >= 0) timers.splice(i, 1); },
  clearInterval(id) { engine.clearTimeout(id); },
  registerAsset(asset) { return asset; },
  registerAudioBuffers(resolution) { const n = resolution || 16; return { left: new Float32Array(n), right: new Float32Array(n), average: new Float32Array(n) }; },
  isPortrait() { return engine.screenResolution.y >= engine.screenResolution.x; }, isLandscape() { return engine.screenResolution.x > engine.screenResolution.y; },
  isDesktopDevice() { return false; }, isMobileDevice() { return true; }, isWallpaper() { return true; }, isScreensaver() { return false; }, isRunningInEditor() { return false; },
  openUserShortcut() {}, colorScheme: new Vec3(0.6, 0.6, 0.6),
};
// Desktop media integration has no source on the phone: nothing is ever playing.
const MediaPlaybackEvent = { PLAYBACK_STOPPED: 0, PLAYBACK_PLAYING: 1, PLAYBACK_PAUSED: 2 };
const input = { cursorWorldPosition: new Vec3(0, 0, 0), cursorScreenPosition: new Vec2(0, 0), cursorLeftDown: false };

// ---- Scene objects ----
const layers = [];
/** Saved configuration of each object (getInitialLayerConfig). */
const initial = new Map();
/** Camera set by setCameraTransforms, else the camera object's scripted origin; {x, y, zoom}. */
let cameraSet = null, cameraLayer = null, cameraScripted = false;
function sceneCamera() {
  if (cameraSet) return cameraSet;
  if (cameraLayer && cameraScripted) return { x: cameraLayer.origin.x, y: cameraLayer.origin.y, zoom: cameraLayer.zoom };
  return { x: 0, y: 0, zoom: cameraLayer ? cameraLayer.zoom : 1 };
}
const byId = new Map();
const byName = new Map();

function controller() {
  const c = { playing: true, frame: 0, rate: 1,
    play() { c.playing = true; }, stop() { c.playing = false; c.frame = 0; }, pause() { c.playing = false; },
    isPlaying() { return c.playing; }, setFrame(f) { c.frame = f; }, getFrame() { return c.frame; }, getFrameCount() { return 1; },
    addEndedCallback() {}, removeEndedCallback() {}, getCurrentTime() { return 0; }, getDuration() { return 0; }, setCurrentTime() {}, loop: true };
  return c;
}

class Layer {
  constructor(d) {
    this.id = d.id; this.name = d.name || ''; this.parentId = d.parent === undefined ? null : d.parent;
    this.origin = new Vec3(d.origin || [0, 0, 0]); this.scale = new Vec3(d.scale || [1, 1, 1]); this.angles = new Vec3(d.angles || [0, 0, 0]);
    this.alpha = d.alpha === undefined ? 1 : d.alpha; this.visible = d.visible !== false; this.color = new Vec3(d.color || [1, 1, 1]);
    this.size = new Vec2(d.size || [0, 0]); this.text = d.text || ''; this.solid = !!d.solid;
    // Text layer fields (ITextLayer); text, pointsize and color reach the phone's text layers.
    this.pointsize = d.pointsize === undefined ? 32 : d.pointsize; this.font = d.font || ''; this.padding = d.padding || 0;
    this.horizontalalign = d.horizontalalign || 'center'; this.verticalalign = d.verticalalign || 'center'; this.anchor = d.anchor || 'none';
    this.opaquebackground = false; this.backgroundcolor = new Vec3(0, 0, 0); this.limitrows = false; this.maxrows = d.maxrows || 1; this.limitwidth = false; this.maxwidth = d.maxwidth || 500;
    this.attachment = d.attachment || ''; this.parallaxDepth = new Vec2(1, 1); this.alignment = 'center'; this.zoom = d.zoom === undefined ? 1 : d.zoom; this.fov = d.fov === undefined ? 50 : d.fov;
    this.playing = d.particle ? true : undefined; this.kind = d.kind || 'image';
    this._animation = controller(); this._texture = controller(); this._video = controller();
  }
  getParent() { return this.parentId === null ? null : byId.get(this.parentId) || null; }
  getChildren() { return layers.filter((l) => l.parentId === this.id); }
  getAnimation() { return this._animation; }
  getTextureAnimation() { return this._texture; }
  getVideoTexture() { return this._video; }
  getEffect() { return { visible: true }; }
  getEffectCount() { return 0; }
  play() { this.playing = true; }
  stop() { this.playing = false; }
  pause() { this.playing = false; }
  emit() {}
  isPlaying() { return this.playing !== false; }
  /** Scene-space origin, following parents (attachments are placed by the host). */
  worldOrigin() {
    let x = this.origin.x, y = this.origin.y, l = this, depth = 0;
    while (l.getParent() && depth++ < 16) {
      const p = l.getParent(), a = p.angles.z, c = Math.cos(a), s = Math.sin(a), lx = x * p.scale.x, ly = y * p.scale.y;
      x = p.origin.x + lx * c - ly * s; y = p.origin.y + lx * s + ly * c; l = p;
    }
    return new Vec2(x, y);
  }
  /** Scene-space [x, y, scaleX, scaleY, angle] through the parents' current (possibly scripted) transforms. */
  worldTransform() {
    // Attachment points are bones of skeletal parents the runtime does not pose; the phone places those layers itself.
    for (let l = this, depth = 0; l && depth < 17; l = l.getParent(), depth++) if (l.attachment) return null;
    let x = this.origin.x, y = this.origin.y, sx = this.scale.x, sy = this.scale.y, a = this.angles.z, l = this, depth = 0;
    while (l.getParent() && depth++ < 16) {
      const p = l.getParent(), c = Math.cos(p.angles.z), s = Math.sin(p.angles.z), lx = x * p.scale.x, ly = y * p.scale.y;
      x = p.origin.x + lx * c - ly * s; y = p.origin.y + lx * s + ly * c; sx *= p.scale.x; sy *= p.scale.y; a += p.angles.z; l = p;
    }
    return [x, y, sx, sy, a];
  }
  /** Turns the layer (2D: around z) to face a scene point. */
  lookAt(center) { const o = this.worldOrigin(); this.angles = new Vec3(this.angles.x, this.angles.y, Math.atan2(center.y - o.y, center.x - o.x)); }
  lookAtYaw(center) { this.lookAt(center); }
  setParent(parent, adjust) {
    const p = parent === null || parent === undefined ? null : typeof parent === 'object' ? parent : thisScene.getLayer(parent);
    if (adjust !== false && this.worldTransform() && (!p || p.worldTransform())) { const w = this.worldTransform(); this.parentId = p ? p.id : null; if (p) { const pw = p.worldTransform(), c = Math.cos(-pw[4]), s = Math.sin(-pw[4]), dx = w[0] - pw[0], dy = w[1] - pw[1]; this.origin = new Vec3((dx * c - dy * s) / (pw[2] || 1), (dx * s + dy * c) / (pw[3] || 1), this.origin.z); } else this.origin = new Vec3(w[0], w[1], this.origin.z); }
    else this.parentId = p ? p.id : null;
  }
  contains(point) {
    const o = this.worldOrigin(); const w = Math.abs(this.size.x * this.scale.x) / 2, h = Math.abs(this.size.y * this.scale.y) / 2;
    return w > 0 && h > 0 && Math.abs(point.x - o.x) <= w && Math.abs(point.y - o.y) <= h;
  }
}

const thisScene = {
  getLayer(key) { return (typeof key === 'number' ? byId.get(key) : byName.get(key)) || null; },
  getLayerIndex(layer) { return layers.indexOf(typeof layer === 'string' ? byName.get(layer) : layer); },
  getLayerCount() { return layers.length; },
  enumerateLayers() { return layers.slice(); },
  sortLayer() {}, createLayer() { return null; }, destroyLayer() {},
  getLayerByID(id) { return byId.get(+id) || null; },
  getInitialLayerConfig(key) { const l = typeof key === 'object' && key ? key : this.getLayer(key); return l && initial.get(l.id) ? JSON.parse(JSON.stringify(initial.get(l.id))) : null; },
  /** 2D camera (CameraTransforms): eye/center are offsets from the canvas centre in scene units; zoom is relative. */
  getCameraTransforms() { const c = sceneCamera(); return { eye: new Vec3(c.x, c.y, 0), center: new Vec3(c.x, c.y, -1), up: new Vec3(0, 1, 0), zoom: c.zoom }; },
  setCameraTransforms(t) { if (!t) return; const e = t.eye || t.center || { x: 0, y: 0 }; cameraSet = { x: +e.x || 0, y: +e.y || 0, zoom: t.zoom === undefined ? sceneCamera().zoom : +t.zoom || 1 }; },
  // Scene fields (IScene); bloom* reach the phone renderer, fov/near/far/lighting only matter for 3D models.
  bloom: false, bloomstrength: 2, bloomthreshold: 0.65, bloomtint: new Vec3(1, 1, 1), clearenabled: true, clearcolor: new Vec3(0, 0, 0), ambientcolor: new Vec3(0.3, 0.3, 0.3), skylightcolor: new Vec3(0.3, 0.3, 0.3),
  fov: 50, nearz: 0.01, farz: 10000, camerafade: true, camerashake: false, camerashakespeed: 3, camerashakeamplitude: 0.5, camerashakeroughness: 1,
  cameraparallax: false, cameraparallaxamount: 0.5, cameraparallaxdelay: 0.1, cameraparallaxmouseinfluence: 0,
};

// ---- Script modules ----
const MODULES = { WEMath, WEColor, WEVector: { Vec2, Vec3 } };
const HANDLERS = ['init', 'update', 'cursorDown', 'cursorUp', 'cursorClick', 'cursorMove', 'cursorEnter', 'cursorLeave', 'applyUserProperties', 'destroy', 'resizeScreen'];

/** ES module syntax → a function returning the exported handlers. Imports map to the built-in modules above. */
function compile(source) {
  let code = String(source).replace(/^﻿/, '');
  code = code.replace(/import\s+\*\s+as\s+(\w+)\s+from\s+['"]([^'"]+)['"]\s*;?/g, (m, name, mod) => `const ${name} = __modules[${JSON.stringify(mod)}] || {};`);
  code = code.replace(/import\s*\{([^}]*)\}\s*from\s+['"]([^'"]+)['"]\s*;?/g, (m, names, mod) =>
    `const {${names.replace(/\bas\b/g, ':')}} = __modules[${JSON.stringify(mod)}] || {};`);
  code = code.replace(/import\s+(\w+)\s+from\s+['"]([^'"]+)['"]\s*;?/g, (m, name, mod) => `const ${name} = __modules[${JSON.stringify(mod)}] || {};`);
  code = code.replace(/\bexport\s+default\s+/g, 'const __default = ');
  code = code.replace(/\bexport\s+(?=(?:async\s+)?function|var|let|const|class)/g, '');
  const exports = HANDLERS.map((h) => `${h}: typeof ${h} === 'function' ? ${h} : undefined`).join(', ');
  // eslint-disable-next-line no-new-func
  // Globals (engine, input, shared, thisScene, Vec3, WEMath …) are this script's top-level bindings, so modules may shadow them.
  return new Function('__modules', 'thisLayer', 'thisObject',
    `${code}\nreturn { ${exports}, scriptProperties: typeof scriptProperties === 'undefined' ? undefined : scriptProperties };`);
}

const scripts = [];
let started = false;
const errors = [];

function wrap(property, value) {
  if (['origin', 'scale', 'angles', 'color'].includes(property)) return new Vec3(value);
  if (property === 'alpha') return +value;
  if (property === 'visible') return !!value;
  return value;
}
function assign(layer, property, value) {
  if (value === undefined) return;
  if (['origin', 'scale', 'angles', 'color'].includes(property)) layer[property] = new Vec3(value);
  else if (property === 'alpha') layer.alpha = +value;
  else if (property === 'visible') layer.visible = !!value;
  else layer[property] = value;
}
function guard(entry, fn) {
  try { return fn(); } catch (e) { if (errors.length < 20) errors.push(`${entry.layer.name}.${entry.property}: ${e && e.message || e}`); entry.failures = (entry.failures || 0) + 1; return undefined; }
}

/** Whether an object has a script on its origin (a camera placed by script). */
function d0scripts(scene, id) { const d = scene.objects.find((o) => o.id === id); return !!(d && d.scripts && d.scripts.origin); }
function wmInit(sceneJson) {
  const scene = JSON.parse(sceneJson);
  engine.canvasSize = new Vec2(scene.width || 1920, scene.height || 1080);
  engine.userProperties = scene.properties || {};
  for (const [k, v] of Object.entries(scene.general || {})) if (k in thisScene && typeof thisScene[k] !== 'function') thisScene[k] = thisScene[k] instanceof Vec3 ? new Vec3(v) : v;
  if (scene.screen) engine.screenResolution = new Vec2(scene.screen);
  for (const d of scene.objects) { const l = new Layer(d); layers.push(l); byId.set(l.id, l); initial.set(l.id, d); if (l.name && !byName.has(l.name)) byName.set(l.name, l); if (l.kind === 'camera' && !cameraLayer) cameraLayer = l; }
  if (cameraLayer && d0scripts(scene, cameraLayer.id)) cameraScripted = true;
  // Baseline before any script code runs (module code runs while compiling): layers scripts never change are not
  // reported, so the phone keeps its own placement and setting-driven visibility for them.
  for (const l of layers) { l._visible0 = l.visible; last.set(String(l.id), snapshot(l)); }
  for (const d of scene.objects) {
    const layer = byId.get(d.id);
    for (const [property, script] of Object.entries(d.scripts || {})) {
      const entry = { layer, property, handlers: null };
      entry.handlers = guard(entry, () => compile(script.source)(MODULES, layer, layer));
      if (!entry.handlers) continue;
      const props = entry.handlers.scriptProperties, saved = script.properties || {};
      if (props && typeof props === 'object') for (const [k, v] of Object.entries(saved)) props[k] = v && typeof v === 'object' && 'value' in v ? v.value : v;
      // Script parameters bound to author properties follow them, as in Wallpaper Engine.
      entry.userBound = {};
      for (const [k, v] of Object.entries(saved)) if (v && typeof v === 'object' && typeof v.user === 'string') entry.userBound[k] = v.user;
      scripts.push(entry);
    }
  }
  return JSON.stringify({ scripts: scripts.length, errors });
}

let last = new Map();
let hovered = new Set();
function snapshot(l) {
  return [l.origin.x, l.origin.y, l.origin.z, l.scale.x, l.scale.y, l.scale.z, l.angles.x, l.angles.y, l.angles.z, l.alpha, l.visible === l._visible0 ? -1 : l.visible ? 1 : 0, l.playing === false ? 0 : 1, ...(l.worldTransform() || [])].map((v) => Math.round(v * 1e4) / 1e4);
}
function dispatch(name, event, filter) {
  for (const entry of scripts) {
    const handler = entry.handlers[name];
    if (handler && (!filter || filter(entry.layer))) guard(entry, () => handler.call(null, event));
  }
}

/** One frame: input {runtime, frametime, cursor:[x,y]|null, down, events:[{type,x,y}], timeOfDay}. Returns changed layers and particle states. */
/** Author settings changed on the phone: engine.userProperties, bound script parameters, then applyUserProperties(changed). */
function applyUser(changed) {
  Object.assign(engine.userProperties, changed);
  for (const entry of scripts) {
    const props = entry.handlers.scriptProperties;
    if (props && typeof props === 'object') for (const [k, u] of Object.entries(entry.userBound || {})) if (u in changed) props[k] = changed[u];
    if (entry.handlers.applyUserProperties) guard(entry, () => entry.handlers.applyUserProperties(changed));
  }
}

function wmFrame(inputJson) {
  const frame = JSON.parse(inputJson);
  if (frame.user && started) applyUser(frame.user);
  engine.frametime = Math.max(0, Math.min(0.5, frame.frametime || 0)); engine.runtime = frame.runtime || 0; engine.timeOfDay = frame.timeOfDay || 0;
  if (frame.cursor) { input.cursorWorldPosition = new Vec3(frame.cursor[0], frame.cursor[1], 0); input.cursorScreenPosition = new Vec2(frame.screen ? frame.screen[0] : 0, frame.screen ? frame.screen[1] : 0); }
  input.cursorLeftDown = !!frame.down;
  if (!started) {
    started = true;
    for (const entry of scripts) {
      if (entry.handlers.applyUserProperties) guard(entry, () => entry.handlers.applyUserProperties(engine.userProperties));
      if (entry.handlers.init) {
        const value = guard(entry, () => entry.handlers.init(wrap(entry.property, entry.layer[entry.property])));
        if (entry.property !== 'scene') assign(entry.layer, entry.property, value);
      }
    }
  }
  for (const e of frame.events || []) {
    const point = new Vec3(e.x, e.y, 0), event = { worldPosition: point, screenPosition: new Vec2(0, 0) };
    if (e.type === 'move') {
      dispatch('cursorMove', event);
      const now = new Set(layers.filter((l) => l.contains(point)));
      for (const l of now) if (!hovered.has(l)) dispatch('cursorEnter', event, (x) => x === l);
      for (const l of hovered) if (!now.has(l)) dispatch('cursorLeave', event, (x) => x === l);
      hovered = now;
    } else if (e.type === 'down') dispatch('cursorDown', event, (l) => l.contains(point));
    else if (e.type === 'up') { dispatch('cursorUp', event, (l) => l.contains(point)); if (e.click) dispatch('cursorClick', event, (l) => l.contains(point)); }
  }
  for (let i = 0; i < timers.length;) {
    const t = timers[i];
    if (t.at <= engine.runtime) { if (t.every > 0) { t.at += t.every; i++; } else timers.splice(i, 1); try { t.fn(); } catch (e) { if (errors.length < 20) errors.push('timer: ' + (e && e.message || e)); } }
    else i++;
  }
  for (const entry of scripts) {
    if (!entry.handlers.update || entry.property === 'scene' || (entry.failures || 0) > 30) continue;
    const value = guard(entry, () => entry.handlers.update(wrap(entry.property, entry.layer[entry.property])));
    assign(entry.layer, entry.property, value);
  }
  const changed = {};
  for (const l of layers) {
    const s = snapshot(l), key = String(l.id), before = last.get(key);
    if (!before || s.some((v, i) => v !== before[i])) { changed[key] = s; last.set(key, s); }
  }
  // Zoom relative to the camera object's saved zoom, which the phone already applies from its setting.
  const cam = sceneCamera(), cameraNow = [cam.x, cam.y, cameraSet ? cameraSet.zoom / (cameraLayer && cameraLayer.zoom ? cameraLayer.zoom : 1) : 1].map((v) => Math.round(v * 1e3) / 1e3), cameraBefore = last.get('camera');
  const camera = cameraSet || cameraScripted ? (!cameraBefore || cameraNow.some((v, i) => v !== cameraBefore[i]) ? (last.set('camera', cameraNow), cameraNow) : undefined) : undefined;
  const texts = {};
  for (const l of layers) {
    if (l.kind !== 'text') continue;
    const t = [String(l.text === undefined || l.text === null ? '' : l.text), +l.pointsize, l.color.x, l.color.y, l.color.z], key = 't' + l.id, before = last.get(key);
    if (!before || t.some((v, i) => v !== before[i])) { texts[String(l.id)] = t; last.set(key, t); }
  }
  // Scene bloom fields scripts changed (IScene.bloom, bloomstrength, bloomthreshold, bloomtint).
  const tint = thisScene.bloomtint instanceof Vec3 ? thisScene.bloomtint : new Vec3(thisScene.bloomtint);
  const bloomNow = [thisScene.bloom ? 1 : 0, +thisScene.bloomstrength, +thisScene.bloomthreshold, tint.x, tint.y, tint.z].map((v) => Math.round(v * 1e4) / 1e4);
  const bloomBefore = last.get('bloom'); let bloom;
  if (!bloomBefore) last.set('bloom', bloomNow); else if (bloomNow.some((v, i) => v !== bloomBefore[i])) { last.set('bloom', bloomNow); bloom = bloomNow; }
  const out = JSON.stringify({ layers: changed, texts, camera, bloom, errors: errors.splice(0) });
  return out;
}

globalThis.wmInit = wmInit;
globalThis.wmFrame = wmFrame;
