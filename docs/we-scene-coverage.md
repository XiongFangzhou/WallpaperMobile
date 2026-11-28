# Wallpaper Engine scene coverage

What WallpaperMobile parses and renders, measured against Wallpaper Engine's own definitions rather than individual
workshop items. Sources (read from a local Wallpaper Engine install; nothing from them is redistributed):

- Script API: `ui/dist/monaco/autocomplete/lib.sceneScript.d.ts`
- Scene format: the editor presets in `assets/presets/*` (complete `scene.json` objects and `general` blocks)
- Built-in models, materials, shaders, effects and fonts: `assets/models/util`, `assets/materials`, `assets/shaders`,
  `assets/effects`, `assets/fonts` (downloaded on the phone from depot 431961 by the user)
- User properties: `project.json` `general.properties` types bool, slider, combo, color, text, textinput,
  scenetexture, group, usershortcut

Status: done / partial / planned / no (with reason). Audio comes last by request.

## Objects (scene.json `objects[]`)

| Kind | Official fields | Status |
|---|---|---|
| Image layer | image, origin, scale, angles, size, alpha, color, colorBlendMode, visible, parent, attachment, parallaxDepth, effects, animationlayers | done; mirrored scale and x/y rotation are drawn with an orthographic projection (perspective approximated); `instance` textures and usertextures done; cropoffset planned |
| Solid layer (`models/util/solidlayer.json`) | as image | done |
| Composition layer (`composelayer.json`, passthrough) | children drawn together, its effects on the result | done: a layer with a size renders inside its rectangle (children outside it are cut off) and its effects work in that space; without a size it covers the canvas |
| Fullscreen layer (`fullscreenlayer.json`, passthrough + fullscreen) | effects applied to everything drawn below it | done (GPU) |
| Text layer (presets/clock, countdown) | text {value, script}, font, pointsize, color, alpha, padding, horizontalalign, verticalalign, anchor, opaquebackground, backgroundcolor, limitrows, maxrows, limitwidth, maxwidth, size | done: box = pointsize × 25/6 × font line height per row, width = advance (matches 60+ editor-saved boxes); scripts and settings update text, pointsize, color; `padding` and `anchor` are kept but do not change the box |
| Shape object (`shape: quad`, e.g. presets/lightshafts) | no image; its effects draw it (light shafts with DIRECTDRAW) | done: transparent base at most 1024 px; without a saved size it spans the canvas (assumption: not stated in the official files) |
| Particle system | particle, instanceoverride (alpha, size, count, speed, lifetime, rate, colorn) | sprite / spritetrail / rope done; other renderers planned |
| Puppet (skeletal) image | animation layers, bones, attachments | done for playback; script bone/blend-shape API planned |
| Video texture layer | IVideoTexture | done for playback: the MP4 is kept uncompressed in the converted scene and played in place, silent and looping, author effects applied to every frame; script control (IVideoTexture) planned |
| Model (3D) layer | model, IModelLayer | no: needs a 3D renderer |
| Sound layer | sound[], volume, playbackmode; ISoundLayer play/stop/pause | planned last (audio) |

## Scene (`general`, `camera`; IScene)

| Field | Status |
|---|---|
| orthogonalprojection | done |
| clearcolor, clearenabled | done |
| cameraparallax, amount, delay, mouseinfluence | done via gyro/pointer mapping |
| camerashake, speed, amplitude, roughness | done; the units of amplitude and roughness are not documented, so their scale is an estimate |
| camera eye/center/up, zoom; get/setCameraTransforms | done for 2D scenes |
| bloom, bloomstrength, bloomthreshold | done, following Wallpaper Engine's bloom passes |
| fov, nearz, farz, ambientcolor, skylightcolor | no for 2D scenes: only used by perspective/3D models |

## Script API (IEngine, IScene, ILayer, IInput)

Done: getLayer, getLayerCount, enumerateLayers, createLayer, destroyLayer, sortLayer, getLayerIndex, getEffect(Count),
getTextureAnimation, getVideoTexture (stub), registerAsset, setTimeout/Interval, openUserShortcut, screenResolution,
canvasSize, timeOfDay, frametime, runtime, isMobileDevice, cursor input, userProperties / applyUserProperties,
origin/angles/scale/visible/alpha, getParent, getChildren, text layer fields, getLayerByID, getInitialLayerConfig,
get/setCameraTransforms, isPortrait/isLandscape, lookAt, setParent, scripts on effect values and effect visibility.
Cursor events are layer events: cursorEnter/Leave/Move reach the layers under the cursor; on the phone a lifted
finger leaves them (there is no hover).

Planned: getTransformMatrix, particle `instance` fields, animation layers (getAnimationLayer, playSingleAnimation),
bone and blend-shape access, video texture control.

Last: registerAudioBuffers (spectrum), sound layer control.

## User properties

| Type | Status |
|---|---|
| bool, slider, combo, color, group, text (note) | done |
| textinput | done: text field, drives bound text layers and scripts |
| scenetexture | done: pick a picture on the phone (a private copy at most 2048 px); clearing restores the author's |
| usershortcut | no: opens desktop shortcuts |

## Materials and effects

All 46 built-in effects go through the generic shader translation, multi-pass effects included. Supplied to effects:

- `_rt_FullFrameBuffer`, the picture drawn so far, for effects that sample it; compose passes
  (effectcomposebackground) run as ordinary passes on it.
- `_rt_imageLayerComposite_<id>_a`, another layer's result; a layer hidden by default that an effect samples is kept
  and rendered only as that texture.
- g_EffectModelViewProjectionMatrix (the effect quad on screen) and g_ModelViewProjectionMatrixInverse (the pointer
  unprojected into the layer), so screen-space lookups and pointer sprites land where they do on the PC.
- Texture slots clamp or repeat as their TEX ClampUVs flag says.

X-ray: the simple two-image case uses a built-in reveal with its own settings; variants with masks, another layer's
result or custom pointer sprites run the xray shader from the package. Either follows the finger only.

Planned: non-standard pass blending, cropoffset.

Shader translation fix-ups (our entry layer, vendored translator unchanged): unconditional uniforms move above the first
`#include` (Wallpaper Engine accepts headers that use uniforms declared later), and `float(int(...))` operands of `%`
are unwrapped. The corpus check lints every output for both.
