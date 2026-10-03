# Intoniva icon source

The publisher supplied `Minimalist Tuning Fork App Icon.png` on 3 October 2026.
The built-in ImageGen tool regenerated the design. No API/CLI fallback was used.

- `intoniva-icon-generated.png`: retained ImageGen design reference.
- `intoniva-icon-background.png`: ImageGen background layer.
- `../../tools/RenderStoreIcon.java`: clean native fork geometry and platform exports.
- `../intoniva-icon.png`: finished 1024px square icon.

Two transparent ImageGen exports had edge speckles and were rejected.
The final fork uses a clean native vector following the generated silhouette,
with the generated dark-green background. No rough cutouts are shipped.

## Regeneration

Run `java tools/RenderStoreIcon.java` from the repository root.
The command exports the Play/Pages PNGs, adaptive background, vector foreground,
monochrome and notification marks, banners, social preview, and mask previews.
It checks opacity, size and centered mark bounds.

The adaptive layers are 108dp. The mark is centered at 54,54 and 49dp tall.
The visible launcher viewport is 72dp; Play artwork uses the same central crop.
Play icons are full-square 512px sRGB RGBA PNGs without a baked outer mask.
Notification artwork uses a separate 24dp viewport to stay readable.

## ImageGen prompts

Master prompt:

> Regenerate the supplied icon as a clean finished square Android app icon. Preserve the single upright ivory tuning fork, rounded prong tips, symmetrical U-shaped bowl, centered rounded stem, and dark forest-green/near-black palette. Preserve the restrained curved diagonal tonal sweep. Extend the green background to all four straight edges. Remove the outer black margin, rounded tile outline, outer shadow, bevel, and mockup presentation. Keep the fork upright, symmetrical, optically centered, within the central 70% of the canvas height and about 26% width. Clean silhouette readable at 48px. No text, badges, grid, watermark, extra symbols, or device frame.

Background prompt:

> Produce the background layer only. Remove the ivory tuning fork completely and fill its location with the existing dark forest-green/near-black tonal background. Preserve the square full-bleed canvas, curved diagonal tonal sweep, colors and light direction. Fully opaque to every edge. No symbol, ghost of the fork, text, rounded corners, outer border, or drop shadow.

Platform references:
[Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
and [Google Play icon requirements](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).
