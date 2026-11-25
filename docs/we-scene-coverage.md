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
| Image layer | image, origin, scale, angles, size, alpha, color, colorBlendMode, visible, parent, attachment, parallaxDepth, effects, animationlayers | done; mirrored scale and x/y rotation planned; cropoffset planned |
| Solid layer (`models/util/solidlayer.json`) | as image | done |
| Composition layer (`composelayer.json`, passthrough) | children drawn together, its effects on the result | done |
| Fullscreen layer (`fullscreenlayer.json`, passthrough + fullscreen) | effects applied to everything drawn below it | done (GPU) |
| Text layer (presets/clock, countdown) | text {value, script}, font, pointsize, color, alpha, padding, horizontalalign, verticalalign, anchor, opaquebackground, backgroundcolor, limitrows, maxrows, limitwidth, maxwidth, size | done: box = pointsize × 25/6 × font line height per row, width = advance (matches 60+ editor-saved boxes); scripts and settings update text, pointsize, color; `padding` and `anchor` are kept but do not change the box |
| Particle system | particle, instanceoverride (alpha, size, count, speed, lifetime, rate, colorn) | sprite / spritetrail / rope done; other renderers planned |
| Puppet (skeletal) image | animation layers, bones, attachments | done for playback; script bone/blend-shape API planned |
| Video texture layer | IVideoTexture | planned |
| Model (3D) layer | model, IModelLayer | no: needs a 3D renderer |
| Sound layer | sound[], volume, playbackmode; ISoundLayer play/stop/pause | planned last (audio) |

## Scene (`general`, `camera`; IScene)

| Field | Status |
|---|---|
| orthogonalprojection | done |
| clearcolor, clearenabled | planned (3) |
| cameraparallax, amount, delay, mouseinfluence | done via gyro/pointer mapping |
| camerashake, speed, amplitude, roughness | planned (3) |
| camera eye/center/up, zoom; get/setCameraTransforms | planned (3) |
| bloom, bloomstrength, bloomthreshold | planned (4) |
| fov, nearz, farz, ambientcolor, skylightcolor | no for 2D scenes: only used by perspective/3D models |

## Script API (IEngine, IScene, ILayer, IInput)

Done: getLayer, getLayerCount, enumerateLayers, createLayer, destroyLayer, sortLayer, getLayerIndex, getEffect(Count),
getTextureAnimation, getVideoTexture (stub), registerAsset, setTimeout/Interval, openUserShortcut, screenResolution,
canvasSize, timeOfDay, frametime, runtime, isMobileDevice, cursor input, userProperties / applyUserProperties,
origin/angles/scale/visible/alpha, getParent, getChildren, text layer fields.

Planned (5): getLayerByID, getInitialLayerConfig, setCameraTransforms, scene fields above, isPortrait/isLandscape,
getTransformMatrix, lookAt, setParent, particle `instance` fields, animation layers
(getAnimationLayer, playSingleAnimation), bone and blend-shape access.

Last: registerAudioBuffers (spectrum), sound layer control.

## User properties

| Type | Status |
|---|---|
| bool, slider, combo, color, group, text (note) | done |
| textinput | done: text field, drives bound text layers and scripts |
| scenetexture | planned (6): pick an image on the phone |
| usershortcut | no: opens desktop shortcuts |

## Materials and effects

All 46 built-in effects go through the generic shader translation. `_rt_FullFrameBuffer` (the picture drawn so far) is
supplied to effects that sample it. Planned: multi-pass layer materials, non-standard
pass blending, cropoffset.
