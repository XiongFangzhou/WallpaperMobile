# Repository guidelines

## Workspace and tools

- Resolve paths from the repository root. Keep machine-specific paths, usernames, device serials and private network addresses out of source and documentation.
- `Enter-Project.ps1` discovers JDK 21 through `WVB_JDK`, `JAVA_HOME`, `.tools/jdk-path.txt`, `.tools/jdk` or `PATH` and configures project-local caches.
- Keep tools, credentials, signing keys, downloads, device evidence and build output in ignored directories. Preserve unrelated workspaces and conversation records.

## Development and Git

- `main` develops the standalone wallpaper engine. Hydrogen is the archived official-client integration edition.
- Use focused feature/fix branches and descriptive commits. Review changes and relevant tests before merging or publishing. Keep existing release tags intact.
- Public publication starts from a clean source snapshot. Local development history may contain machine configuration; do not expose private branches. Subsequent public updates use normal commits without force-pushing.
- Review source, documentation, binary fixtures and release assets for sensitive content. Exclude downloaded workshop assets, personal references, signing material and device captures.

## Android behavior and validation

- Keep preview and wallpaper rendering consistent. Retain each screen's settings and protect user data during migration.
- Both editions use `dev.workshopvideo`. Increase versionCode for releases and retain the signing key for upgrades. Keep user data during migrations.
- Official MPKG resources marked `officialPackage` need main-engine adaptation. Replace an existing original only after successful conversion and durable save.
- Run relevant core, host, native graphics or shader checks. Distinguish these checks from a running Android system when reporting validation.
- Phone interaction is manual. USB operations are limited to selected installation and read-only diagnostics. Do not run input or wallpaper-changing automation on physical devices.
- Full emulator checks are available when requested. Restrict emulator commands to their designated virtual device.
- Use launcher offsets/page metadata when available, with aspect-based segmentation and automatic gesture compatibility enabled by default. System offsets take priority within the current gesture; a past callback must not permanently disable fallback. Preserve an explicit system-only option. Never infer committed launcher pages solely from swipe velocity. Preserve full-image scrolling and smooth motion.
- Optional precise launcher sync must subscribe only to the resolved HOME package's numeric scroll events. Never request window contents, interaction-window retrieval, touch exploration or gesture execution. Keep authorization manual and preserve operation without the service. Correct fallback estimates with observed positions, including aborted gestures and page boundaries.
- Render through one vsync-aligned frame clock, coalescing input and decoded frames. Retain manual frame budgets and pause idle/background/power-saver work. Distinguish render caps from measured phone FPS.
- Keep bitmap scene layers resident on the GPU during paging, parallax and X-ray. Verify transforms, premultiplied alpha, color adjustment and reveal against native Canvas reference frames; do not reintroduce full-frame Canvas uploads for these interactions.
- Keep battery saver policy global, outside per-wallpaper visual options. In configured battery saver mode, retain a visible frame and suspend playback, animation and sensor sampling. Resume according to visibility and saved options.

## Release

- Build with `Build-App.ps1`, then verify version, APK signature and alignment. Write release notes for end users.
- Keep detailed local build evidence in ignored output. Publish APKs and portable checksums/release notes as release assets, excluding device logs and machine paths.
