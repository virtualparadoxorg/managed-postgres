# managed-postgres — brand assets

Logo and brand assets for **managed-postgres**, a fluent DSL for managing PostgreSQL databases.

## Files

| File | Use |
|------|-----|
| `logo.svg` | Primary horizontal lockup — **light** backgrounds (dark text) |
| `logo-dark.svg` | Horizontal lockup — **dark** backgrounds (light text). Great for GitHub README dark mode. |
| `logo-mark.svg` | Icon / mark only, no text. Favicon, social avatar, app icon. Transparent, works on light or dark. |
| `banner.png` | Hero / social banner, 1280×640. README header, OG image, repo social preview. |
| `favicon-32.png` `favicon-64.png` `favicon-180.png` | Pre-rasterized mark — favicon (32/64) and Apple touch icon (180). Transparent. |
| `example.png` | Branded Java code card, 1200×700. README / docs / social. |

## The mark

Four isometric layers — a **PostgreSQL data stack**. The top cube is hot red (`#ef4444`)
breaking out of a slate gradient that descends light → dark, reading as a live/active layer
on top of a settled foundation.

## Colors

| Token | Hex | Use |
|-------|-----|-----|
| Red — top face | `#fca5a5` | mark highlight |
| Red — left face | `#ef4444` | mark accent / primary |
| Red — right face | `#b91c1c` | mark shadow |
| Slate light | `#e2e8f0` / `#94a3b8` / `#64748b` | upper layer |
| Slate mid | `#475569` / `#334155` / `#1e293b` | middle layer |
| Slate dark | `#0f172a` / `#020617` / `#000000` | base layer |
| Ink | `#0f172a` | wordmark on light |
| Paper | `#fafaf7` | wordmark on dark / light bg |

## Typography

- **Wordmark:** Space Grotesk, 600 (SemiBold), tight tracking
- **Tagline / code:** JetBrains Mono

## GitHub README snippet

```html
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="assets/logo-dark.svg">
  <img alt="managed-postgres" src="assets/logo.svg" height="80">
</picture>
```
