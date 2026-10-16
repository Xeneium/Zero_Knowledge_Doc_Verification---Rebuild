# ADR-004: Design System and Accessibility Baseline

## Status

Accepted

## Context

The platform is an enterprise-grade document verification and zero-knowledge proof console. Verification officers, issuers, and holders require a fast, clean, high-density interface with zero visual ambiguity. Furthermore, institutional compliance mandates WCAG 2.2 Level AA accessibility standards, ensuring full usability for keyboard-only operators and screen-reader users.

The legacy frontend had 91 lint problems, poor keyboard navigation, unassociated form labels, and inaccessible custom modal components.

## Decision Drivers

* **Accessibility Compliance:** WCAG 2.2 Level AA conformance (contrast >= 4.5:1, touch targets >= 44x44px, focus traps).
* **Bundle Efficiency:** Zero runtime CSS-in-JS overhead; fast compilation via modern build tools.
* **Component Ownership:** Unstyled headless primitives that allow custom semantic styling without third-party design bloat.

## Decision

1. **Styling Engine:** Adopt **Tailwind CSS v4** (`@tailwindcss/vite`) with semantic design tokens defined via CSS custom properties in `tokens.css`.
2. **Headless UI Primitives:** Adopt **Radix UI** primitives (`@radix-ui/react-dialog`, `@radix-ui/react-dropdown-menu`, `@radix-ui/react-tabs`, `@radix-ui/react-tooltip`, `@radix-ui/react-select`) for complex interactive behaviors.
3. **Accessibility Standard:** WCAG 2.2 Level AA is the hard quality gate enforced via automated `@axe-core/playwright` tests in Phase F10.
4. **Touch & Focus Standards:** All interactive controls maintain minimum 44x44px touch targets. Focus indicators use `ring-2 ring-indigo-600 ring-offset-2`.
5. **Reduced Motion:** All transitions are capped at 150ms and disabled when `prefers-reduced-motion` is detected.

## Consequences

### Positive
- Radix UI handles keyboard trapping, ARIA roles, and focus restoration automatically.
- Tailwind v4 eliminates runtime styling overhead and provides instant HMR.
- Guaranteed contrast and accessibility for compliance-sensitive deployments.

### Negative
- Requires maintaining custom styled wrappers around Radix primitives in `src/components/common/`.
