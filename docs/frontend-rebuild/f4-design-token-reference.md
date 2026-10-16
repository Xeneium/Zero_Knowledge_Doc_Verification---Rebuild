# Frontend Rebuild: Design Token Reference

**Project:** Zero-Knowledge Document Verification Platform  
**Document ID:** ZKDOC-FE-F4-003  
**Status:** Approved Design Token Reference  
**CSS Source:** `frontend/src/styles/tokens.css` & `frontend/src/styles/index.css`  

---

## 1. Overview

The platform uses a CSS custom property token system integrated with Tailwind CSS v4. All values adhere to WCAG 2.2 Level AA standards, guaranteeing minimum contrast ratios of **4.5:1** for standard body text and **3:1** for large text, icons, and UI controls.

---

## 2. Color Tokens & Contrast Standards

### 2.1 Canvas & Surface Tokens

| Token Name | Light Mode Value | Dark Mode Value (`.dark`) | Purpose |
| :--- | :--- | :--- | :--- |
| `--color-bg-canvas` | `#f8fafc` (slate-50) | `#090d16` (deep slate-950) | Application root background |
| `--color-bg-surface` | `#ffffff` (white) | `#111827` (slate-900) | Cards, panels, modal dialogs |
| `--color-bg-subtle` | `#f1f5f9` (slate-100) | `#1e293b` (slate-800) | Table headers, muted button backgrounds |
| `--color-bg-muted` | `#e2e8f0` (slate-200) | `#334155` (slate-700) | Disabled backgrounds, dividers |

---

### 2.2 Text & Foreground Tokens

| Token Name | Light Value | Light Contrast on Canvas | Dark Value (`.dark`) | Dark Contrast on Surface | Purpose |
| :--- | :--- | :---: | :--- | :---: | :--- |
| `--color-text-primary` | `#0f172a` | **16.1:1** | `#f8fafc` | **15.8:1** | Headings, primary body text |
| `--color-text-secondary` | `#475569` | **6.8:1** | `#cbd5e1` | **8.9:1** | Supporting copy, table labels |
| `--color-text-muted` | `#64748b` | **4.6:1** | `#94a3b8` | **5.1:1** | Timestamps, helper text, captions |
| `--color-text-inverse` | `#ffffff` | N/A | `#0f172a` | N/A | Text on primary brand buttons |

*All foreground colors exceed the 4.5:1 WCAG AA threshold for body text.*

---

### 2.3 Brand & Primary Action Tokens

| Token Name | Light Value | Dark Value (`.dark`) | Purpose |
| :--- | :--- | :--- | :--- |
| `--color-brand-primary` | `#4f46e5` (indigo-600) | `#6366f1` (indigo-500) | Primary button background, active borders |
| `--color-brand-hover` | `#4338ca` (indigo-700) | `#4f46e5` (indigo-600) | Button hover & active interaction |
| `--color-brand-subtle` | `#e0e7ff` (indigo-100) | `#1e1b4b` (indigo-950) | Selected item badge background |
| `--color-brand-text` | `#3730a3` (indigo-800) | `#c7d2fe` (indigo-200) | Brand text, active link labels |

---

### 2.4 Semantic State Tokens

#### Success (Verified / Active)
- **Base:** `--color-success-base`: `#16a34a` (Light) / `#22c55e` (Dark)
- **Background:** `--color-success-bg`: `#f0fdf4` (Light) / `#052e16` (Dark)
- **Border:** `--color-success-border`: `#bbf7d0` (Light) / `#14532d` (Dark)
- **Text:** `--color-success-text`: `#166534` (Light, **5.9:1 contrast**) / `#86efac` (Dark)

#### Warning (Expiring / Unavailable / Recovery)
- **Base:** `--color-warning-base`: `#d97706` (Light) / `#f59e0b` (Dark)
- **Background:** `--color-warning-bg`: `#fffbeb` (Light) / `#451a03` (Dark)
- **Border:** `--color-warning-border`: `#fde68a` (Light) / `#78350f` (Dark)
- **Text:** `--color-warning-text`: `#92400e` (Light, **5.2:1 contrast**) / `#fde68a` (Dark)

#### Danger / Destructive (Revoked / Invalid Proof / Error)
- **Base:** `--color-danger-base`: `#dc2626` (Light) / `#ef4444` (Dark)
- **Background:** `--color-danger-bg`: `#fef2f2` (Light) / `#450a0a` (Dark)
- **Border:** `--color-danger-border`: `#fecaca` (Light) / `#7f1d1d` (Dark)
- **Text:** `--color-danger-text`: `#991b1b` (Light, **6.5:1 contrast**) / `#fca5a5` (Dark)

#### Information (Session / Neutral Verification)
- **Base:** `--color-info-base`: `#2563eb` (Light) / `#3b82f6` (Dark)
- **Background:** `--color-info-bg`: `#eff6ff` (Light) / `#082f49` (Dark)
- **Border:** `--color-info-border`: `#bfdbfe` (Light) / `#1e3a8a` (Dark)
- **Text:** `--color-info-text`: `#1e40af` (Light) / `#93c5fd` (Dark)

---

### 2.5 Borders & Focus Rings

| Token Name | Light Value | Dark Value (`.dark`) | Purpose |
| :--- | :--- | :--- | :--- |
| `--color-border-subtle` | `#e2e8f0` (slate-200) | `#1e293b` (slate-800) | Card borders, table dividers |
| `--color-border-strong` | `#cbd5e1` (slate-300) | `#334155` (slate-700) | Input field borders, unselected tabs |
| `--color-border-focus` | `#4f46e5` (indigo-600) | `#818cf8` (indigo-400) | Keyboard focus ring (`focus-visible:ring-2`) |

---

## 3. Typography Scale

The platform relies on system fonts for maximum rendering performance, paired with monospace fonts for cryptographic proof artifacts:

```css
--font-sans: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
--font-mono: ui-monospace, 'JetBrains Mono', 'SFMono-Regular', Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
```

| Level | Size | Line Height | Weight | Tracking | Intended Usage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Display** | 3rem (48px) | 1.1 | 800 (Extrabold) | -0.025em | Landing page hero header |
| **h1** | 1.875rem (30px) | 1.25 | 700 (Bold) | -0.02em | Page headers (`PageHeader.tsx`) |
| **h2** | 1.5rem (24px) | 1.3 | 600 (Semibold) | -0.015em | Section headings, dialog titles |
| **h3** | 1.125rem (18px) | 1.4 | 600 (Semibold) | -0.01em | Card titles |
| **Body** | 1rem (16px) | 1.5 | 400 (Regular) | normal | Standard paragraphs, modal copy |
| **Body Small** | 0.875rem (14px) | 1.4 | 400 (Regular) | normal | Table cells, form input text, alerts |
| **Caption** | 0.75rem (12px) | 1.35 | 500 (Medium) | 0.025em | Badges, helper text, timestamps |
| **Code / Hash** | 0.75rem (12px) | 1.35 | 400 (Regular) | normal | SHA-256 digests, UUIDs, nonces, keys |

---

## 4. Spacing, Touch Targets & Layout Constraints

- **Minimum Touch Target:** `44px x 44px` (`min-h-[44px] min-w-[44px]`) on all interactive controls (buttons, links, form inputs, pagination, checkboxes) adhering to WCAG 2.2 SC 2.5.8.
- **Page Container Max Width:** `max-w-7xl` (`80rem` / `1280px`).
- **Form / Focused Flow Max Width:** `max-w-3xl` (`48rem` / `768px`).
- **Horizontal Padding:**
  - Mobile (`< 640px`): `px-4` (16px)
  - Tablet (`640px - 1024px`): `px-6` (24px)
  - Desktop (`> 1024px`): `px-8` (32px)

---

## 5. Elevation Shadows & Radii

### Elevation Shadows
- `--shadow-sm`: `0 1px 2px 0 rgb(0 0 0 / 0.05)` (Cards, badges)
- `--shadow-md`: `0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)` (Dropdowns, tooltips)
- `--shadow-lg`: `0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)` (Modal dialogs, slide-over drawer)

### Border Radii
- `--radius-sm`: `0.375rem` (6px) — Inputs, buttons, code blocks
- `--radius-md`: `0.5rem` (8px) — Cards, badges, alerts
- `--radius-lg`: `0.75rem` (12px) — Modal dialogs, hero panels
- `--radius-full`: `9999px` — Status pills, circular avatars

---

## 6. Motion & Accessibility

All animations are bounded at **150ms** and strictly disabled when the user has enabled reduced motion preferences:

```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}
```
