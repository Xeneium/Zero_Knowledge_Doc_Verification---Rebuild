# Phase F2: Design System & Visual Specification

**Project:** Zero-Knowledge Document Verification Platform  
**Document ID:** ZKDOC-FE-F2-003  
**Status:** Approved Design System Specification  
**Target Implementation Phases:** F3 through F12  

---

## 1. Design Philosophy & Visual Principles

The Zero-Knowledge Document Verification Platform interface is designed to communicate **mathematical precision, institutional trust, privacy consciousness, and operational clarity**.

Because this platform handles zero-knowledge cryptographic assertions and sensitive document provenance, the visual language intentionally rejects "vibe coding", decorative fluff, visual ambiguity, and misleading security indicators.

### Core Visual Principles:
1. **Cryptographic Truth:** Visual badges and confirmation banners reflect only verified backend assertions. No positive feedback is rendered for unverified or indeterminate cryptographic states.
2. **Minimal Data Disclosure:** UI layouts prioritize displaying cryptographic proof receipts and integrity hashes over exposing raw document content or unnecessary personal data.
3. **Accessibility as a First-Class Requirement:** All components are engineered to WCAG 2.2 Level AA compliance, featuring explicit focus rings, 44x44px minimum touch targets, high-contrast typography, and full keyboard operability.
4. **Predictable & Dense Layouts:** Clean, structured cards and tabular data grids designed for rapid scanning by verification officers and system administrators.

---

## 2. Design Tokens & Styling Specification

The platform utilizes a semantic design token system mapped via CSS custom properties and integrated directly into Tailwind CSS v4.

### 2.1 Color Tokens & Contrast Standards

All color combinations have been verified against WCAG 2.2 Level AA standards (minimum contrast ratio of 4.5:1 for standard body text, 3:1 for large text and UI components):

```css
:root {
  /* Surface & Canvas */
  --color-bg-canvas: #f8fafc;        /* slate-50 */
  --color-bg-surface: #ffffff;       /* white */
  --color-bg-subtle: #f1f5f9;        /* slate-100 */
  --color-bg-muted: #e2e8f0;         /* slate-200 */

  /* Text & Foreground */
  --color-text-primary: #0f172a;     /* slate-900 (Contrast: 16.1:1 on canvas) */
  --color-text-secondary: #475569;   /* slate-600 (Contrast: 6.8:1 on canvas) */
  --color-text-muted: #64748b;       /* slate-500 (Contrast: 4.6:1 on canvas) */
  --color-text-inverse: #ffffff;

  /* Brand & Primary Actions */
  --color-brand-primary: #4f46e5;    /* indigo-600 */
  --color-brand-hover: #4338ca;      /* indigo-700 */
  --color-brand-subtle: #e0e7ff;     /* indigo-100 */
  --color-brand-text: #3730a3;       /* indigo-800 */

  /* Semantic State: Success (Verified / Active) */
  --color-success-base: #16a34a;     /* green-600 */
  --color-success-bg: #f0fdf4;       /* green-50 */
  --color-success-border: #bbf7d0;   /* green-200 */
  --color-success-text: #166534;     /* green-800 (Contrast: 5.9:1 on bg) */

  /* Semantic State: Warning / Pending */
  --color-warning-base: #d97706;     /* amber-600 */
  --color-warning-bg: #fffbeb;       /* amber-50 */
  --color-warning-border: #fde68a;   /* amber-200 */
  --color-warning-text: #92400e;     /* amber-800 (Contrast: 5.2:1 on bg) */

  /* Semantic State: Destructive / Revoked / Failed */
  --color-danger-base: #dc2626;      /* red-600 */
  --color-danger-bg: #fef2f2;        /* red-50 */
  --color-danger-border: #fecaca;    /* red-200 */
  --color-danger-text: #991b1b;      /* red-800 (Contrast: 6.5:1 on bg) */

  /* Borders & Dividers */
  --color-border-subtle: #e2e8f0;    /* slate-200 */
  --color-border-strong: #cbd5e1;    /* slate-300 */
  --color-border-focus: #4f46e5;     /* indigo-600 */

  /* Elevation Shadows */
  --shadow-sm: 0 1px 2px 0 rgb(0 0 0 / 0.05);
  --shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
  --shadow-lg: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1);

  /* Border Radii */
  --radius-sm: 0.375rem; /* 6px */
  --radius-md: 0.5rem;   /* 8px */
  --radius-lg: 0.75rem;  /* 12px */
  --radius-full: 9999px;
}
```

---

### 2.2 Typography Scale

The application uses a modern system font stack (`Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif`) with monospace font (`ui-monospace, 'JetBrains Mono', 'Fira Code', monospace`) for cryptographic hashes, proof bytes, and identifiers:

| Token | Class | Size / Line Height | Weight | Intended Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `display` | `text-4xl` | 36px / 44px | Bold (700) | Landing page hero header |
| `h1` | `text-2xl` | 24px / 32px | Semibold (600) | Page titles |
| `h2` | `text-xl` | 20px / 28px | Semibold (600) | Section headers & modal titles |
| `h3` | `text-lg` | 18px / 24px | Medium (500) | Card titles |
| `body` | `text-base` | 16px / 24px | Regular (400) | Standard reading text |
| `body-sm` | `text-sm` | 14px / 20px | Regular (400) | Table cells, helper text, form labels |
| `caption` | `text-xs` | 12px / 16px | Medium (500) | Badges, metadata timestamps |
| `code` | `font-mono text-xs` | 12px / 16px | Regular (400) | Nonces, hashes, signatures, UUIDs |

---

### 2.3 Spacing, Touch Targets & Layout Constraints

- **Spacing Grid:** Standard 4px/8px modular scale (`p-1` = 4px, `p-2` = 8px, `p-4` = 16px, `p-6` = 24px, `p-8` = 32px).
- **Minimum Touch Target:** `min-h-[44px] min-w-[44px]` on all clickable controls (buttons, navigation links, tabs, icon buttons) to satisfy WCAG 2.2 Success Criterion 2.5.8.
- **Maximum Width Containers:**
  - Standard Page Container: `max-w-7xl mx-auto px-4 sm:px-6 lg:px-8`
  - Form & Focused Flow Container: `max-w-3xl mx-auto`
  - Reading / Documentation Container: `max-w-4xl mx-auto`

---

## 3. Reusable Component Inventory & Behavioral Specification

All interactive components are built using Radix UI accessible unstyled primitives and styled with Tailwind CSS v4.

### 3.1 Button Component (`src/components/common/Button.tsx`)
- **Variants:**
  - `primary`: Solid brand indigo background (`bg-indigo-600 hover:bg-indigo-700 text-white`).
  - `secondary`: Subtle slate background (`bg-slate-100 hover:bg-slate-200 text-slate-900 border border-slate-300`).
  - `outline`: White background with strong border (`bg-white hover:bg-slate-50 text-slate-700 border border-slate-300`).
  - `destructive`: Danger red background (`bg-red-600 hover:bg-red-700 text-white`) for revocations and key rotations.
  - `ghost`: Transparent background (`hover:bg-slate-100 text-slate-700`).
- **States:** `idle`, `hover`, `focus-visible`, `disabled` (`opacity-50 pointer-events-none`), `loading` (renders spinning `<Loader2>` icon with `aria-busy="true"` and maintains fixed layout dimensions).
- **Accessibility:** Explicit `<button type="button | submit">`, visible keyboard focus ring (`ring-2 ring-indigo-500 ring-offset-2`).

---

### 3.2 Badge Component (`src/components/common/Badge.tsx`)
- **Purpose:** Renders credential states, cryptographic proof status, and role tags.
- **Variants:**
  - `success`: Green background with green text and checkmark icon.
  - `warning`: Amber background with amber text and alert-triangle icon.
  - `danger`: Red background with red text and alert-circle icon.
  - `info`: Indigo background with indigo text and info icon.
  - `neutral`: Slate gray background with slate text.
- **Accessibility:** Uses semantic SVG icons alongside text labels to guarantee color independence.

---

### 3.3 Modal / Dialog Component (`src/components/common/Dialog.tsx`)
- **Technology:** Built on `@radix-ui/react-dialog`.
- **Behavior:**
  - Backdrop dimming with blur overlay.
  - Automatic focus trapping within modal when opened.
  - Dismissible via `Escape` key, backdrop click, or close button.
  - Restores focus to the triggering element upon close.
  - `aria-labelledby` linked to DialogTitle; `aria-describedby` linked to DialogDescription.

---

### 3.4 Table Component (`src/components/common/Table.tsx`)
- **Desktop Behavior:** Semantic `<table>`, `<thead>`, `<tbody>`, `<tr>`, `<th>`, `<td>` with sticky headers and sortable column buttons.
- **Mobile Responsive Fallback (< 768px):** Automatic CSS transformation into an accessible stacked card list where each row becomes an independent `<article>` card with data-label prefixes.

---

### 3.5 Input & Form Controls (`src/components/common/Input.tsx`, `Select.tsx`)
- **Behavior:**
  - Label is always permanently visible and explicitly connected via `htmlFor="field-id"`.
  - Helper text or constraints (e.g. "Nonce must be a 2–128 character hex string") displayed beneath input.
  - Error state highlights input with red border and links to error message via `aria-describedby="field-id-error"` and `aria-invalid="true"`.

---

### 3.6 QR Scanner & Code Reader (`src/features/verifier/components/QrReader.tsx`)
- **Technology:** `html5-qrcode`.
- **States:**
  - `requesting-permission`: Accessible prompt informing user of camera access.
  - `scanning`: Active video feed with animated scanning target reticle and live announcement: "Camera active. Hold QR code in frame."
  - `permission-denied`: Fallback providing camera troubleshooting instructions and direct "Upload QR Image File" input.
  - `success`: Camera pauses, plays subtle audio chime (if not muted), and submits reference to verification flow.

---

## 4. Motion, Transitions & Reduced-Motion Mandate

All animations (modal transitions, toast popups, skeleton pulses) are capped at `150ms` and strictly respect user accessibility settings via CSS media queries:

```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

---

## 5. Accessibility Testing Checklist (Phase F10 Quality Gate)

Every component must satisfy the following automated and manual criteria before acceptance:
- [ ] Contrast ratio >= 4.5:1 for body copy and >= 3:1 for large headers and icons.
- [ ] All interactive touch targets >= 44x44px.
- [ ] No information communicated by color alone (always paired with icon + text).
- [ ] Full keyboard operability (`Tab`, `Shift+Tab`, `Enter`, `Space`, `Escape`).
- [ ] Visible focus ring (`ring-2 ring-indigo-500 ring-offset-2`).
- [ ] Forms have programmatic `<label>` and `aria-describedby` error associations.
- [ ] Zero automated violations reported by `@axe-core/playwright`.
