# we-scene shader translation (vendored)

Unmodified files from [oneincase/webwallgl](https://github.com/oneincase/webwallgl),
directory `renderer/vendor/we-scene`, commit `ba413ac985f324a6827cbc12a81ed077d47fdab2`, MIT License (see `LICENSE`).

| File | Purpose |
| --- | --- |
| `render/hlsl-preprocessor.js` | `#include` / `#define` / combo `#if` preprocessing |
| `render/hlsl2glsl.js` | Wallpaper Engine HLSL dialect → GLSL ES 3.00 |
| `render/renderer-glsl.js` | Supplies the rebuilt `common_blending.h` text |
| `headers.ts` | Rebuilt Wallpaper Engine shared shader headers |

`tools/bundle_shader_translator.py` combines them into
`android/assets/we-shader-translator.js`, which the app runs in an isolated
JavaScript sandbox when it converts a downloaded scene. To update, replace these
files with a newer commit and record it here.
