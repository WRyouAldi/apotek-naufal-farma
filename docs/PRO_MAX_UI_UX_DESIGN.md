# Apotek Naufal Farma — PRO MAX UI/UX Design Spec

**Status:** Design specification + CSS implementation committed  
**Design direction:** Monochrome Liquid Glass / Premium Grayscale  
**Reference:** User-provided mobile UI reference (smoked-glass cards, graphite surfaces, soft silver highlights).  
**Scope:** Visual layer only. Preserve application behavior and business logic.

## 1. Design intent

Create a calm, premium pharmacy POS interface inspired by the supplied grayscale glass UI. The app should feel like a polished native mobile product rather than a stack of plain web cards.

### Visual principles
- **Graphite glass:** dark translucent controls and navigation floating above a soft silver-gray background.
- **Soft silver depth:** layered gradients, subtle borders, controlled shadows, and inset highlights.
- **Low-noise typography:** clear hierarchy, compact labels, readable numbers, and consistent weights.
- **Functional emphasis:** key figures and primary actions stand out; decorative effects never reduce legibility.
- **Responsive by default:** all cards and navigation must remain within the WebView viewport.
- **Reduced motion:** respect the operating system's `prefers-reduced-motion` setting.

## 2. Palette

| Token | Target | Use |
|---|---|---|
| Canvas | `#A7ABB3` / gradient | Main app background |
| Graphite | `#252932` | Deep controls and dark glass |
| Smoke | `rgba(57,61,70,.30)` | Translucent card tint |
| Frost | `rgba(255,255,255,.20)` | Glass highlight |
| Border | `rgba(255,255,255,.26)` | Glass edge |
| Primary text | `#F5F6F8` | Main text on dark glass |
| Secondary text | `#D5D8DE` | Descriptions and helper text |
| Silver | `#DDE1E7` | Highlights and icon surfaces |
| Primary action | Graphite gradient | Save / confirm actions |

> Important: maintain accessible contrast. If any existing page uses a light content panel for dense tables or print preview, do not force it dark if that harms readability or print output.

## 3. Component rules

### Header / brand
- Frosted silver-glass panel with rounded lower corners.
- Brand name remains high contrast and clearly readable.
- Keep existing logo and identity; do not alter business branding as part of this theme pass.

### Dashboard
- Welcome panel with soft metallic gradient and restrained highlight.
- Statistics use consistent rounded cards, equal gaps, and tabular numbers.
- Quick actions use compact icon tiles and clear action labels.
- Avoid excessive paragraphs; prioritize data and action.

### Product and search surfaces
- Search field has a distinct inner surface and visible placeholder.
- Product cards share one radius, border treatment, and spacing scale.
- Product name, category, and price must retain clear hierarchy.
- Existing product data, pricing, barcode and stock behavior must remain unchanged.

### Buttons
- Primary action: graphite gradient with a light edge and clear text.
- Secondary action: translucent smoke glass.
- Press feedback is subtle; no large bounce or layout shift.
- Keyboard/focus-visible outlines must remain visible.

### Bottom navigation
- Floating graphite glass capsule with blur, silver edge, and a clear active state.
- Exactly one equal-width slot per existing navigation item.
- Labels must remain inside their own slot; long labels may ellipsize rather than widen the bar.
- Navbar width must never exceed the viewport, including Android WebView safe-area insets.
- Use `box-sizing:border-box`, `min-width:0`, `flex:1 1 0`, clipped overflow, and left/right inset constraints.
- On wide screens, cap width and center it; on phones, use a fixed side inset.

## 4. Motion

- Card entry: short fade + 5px upward settle.
- Button press: small scale-down feedback.
- Active navigation: tonal glass fill, not a large moving indicator.
- Keep transitions around 180–240ms.
- Disable or minimize animation when `prefers-reduced-motion: reduce` is active.

## 5. Navbar overflow fix

The current CSS layer constrains the navigation to the viewport:
- Mobile: `left/right: 6px`, automatic width, safe-area-aware.
- Desktop/tablet: capped at 680px and centered.
- Flex children: equal-width, `min-width: 0`, `width: 0`.
- Labels: single line with ellipsis, no intrinsic-width expansion.
- Container: `overflow: hidden` to prevent any child from widening the bar.

If overflow persists on a device, inspect the rendered element's computed width and the actual navbar ID/class in the generated `index.html`; do not add another arbitrary width override without checking the selector.

## 6. Scope guard — do not change

This is a visual redesign only. Preserve:
- Transactions and payment calculations.
- Product database, stock, prices and purchase-cost data.
- Barcode scanning and manual product search.
- Item CRUD, import/export and synchronization.
- Reports and report calculations.
- Navigation destinations and button handlers.
- A4 PDF and thermal receipt generation/layout.

Do not delete legacy business scripts or change database schemas as part of this visual pass.

## 7. Verification checklist

### Build
- [ ] GitHub Actions build succeeds.
- [ ] Generated APK artifact exists and can be downloaded.
- [ ] Inspect generated HTML to confirm the current theme is injected once and in the expected order.

### Mobile UI
- [ ] Test narrow viewport (320–360px).
- [ ] Test standard viewport (390–430px).
- [ ] Navbar stays within both left and right screen edges.
- [ ] Every navbar item is tappable and labels are not clipped beyond their own slot.
- [ ] Check light/dark content surfaces and form contrast.
- [ ] Check home, price lookup, transaction, item list, sync and report screens.
- [ ] Check keyboard opening does not cause horizontal overflow.
- [ ] Verify reduced-motion behavior.

### Regression
- [ ] Search, barcode, add/remove item and quantity still work.
- [ ] Transaction totals and change calculations are unchanged.
- [ ] Import and sync behavior is unchanged.
- [ ] Reports remain correct.
- [ ] PDF A4 and thermal receipts retain their original paper styling and dimensions.

## 8. Implementation file

- Theme source: `tools/ui_theme_v2.css`
- Build injection: `tools/prepare_apk_v3.py`
- Main page architecture: `tools/page_architecture_v5.js`

The CSS change is committed to the repository. The actual Android viewport, generated APK, and print regressions still need to be verified on-device before declaring the release fully validated.
