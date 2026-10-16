# ADR-001: Framework and Language Selection

## Status

Accepted

## Context

The Zero-Knowledge Document Verification Platform requires a modern, high-performance, and type-safe frontend interface. The frontend is embedded directly into a Spring Boot 3.3.4 monolithic deployable JAR via `frontend-maven-plugin` and served as a Single Page Application (SPA). The backend enforces strict OAuth2 Resource Server constraints and exposes 22 REST endpoints exchanging complex cryptographic proofs and credential schemas.

We need to choose a client framework and language that ensures:
1. Full type safety matching backend Java DTOs.
2. Fast client-side compilation into static HTML/JS/CSS assets.
3. Compatibility with Vite and Spring Boot static resource serving.
4. Rich ecosystem for accessible UI primitives and QR/camera interaction.

## Decision Drivers

* **Type Safety:** Must eliminate runtime property mismatches and guarantee strict DTO typing.
* **Build Architecture:** Must compile to static files without requiring an active Node.js server in production.
* **Bundle Efficiency:** Fast load time and minimal JavaScript footprint.
* **Component Maturity:** Rich ecosystem for WAI-ARIA accessible components and cryptographic utilities.

## Considered Options

### Option 1: React 19 + TypeScript (Selected)
- **Pros:** Industry standard, robust static SPA bundling, full TypeScript 5.7+ integration, deep ecosystem for accessible headless primitives (Radix UI) and QR scanning (`html5-qrcode`), zero server runtime dependency in production.
- **Cons:** Requires client-side routing synchronization with Spring Boot forward controllers.

### Option 2: Next.js (App Router / SSR)
- **Pros:** Built-in routing and server optimization.
- **Cons:** Requires a persistent Node.js server container in production, conflicting with Spring Boot's single-JAR deployment model. Static export mode cripples Next.js features and adds friction.

### Option 3: Svelte 5 / SvelteKit
- **Pros:** Small bundle size, reactive syntax.
- **Cons:** Smaller enterprise ecosystem for specialized cryptographic tools and headless accessibility libraries compared to React.

### Option 4: Angular 18+
- **Pros:** Comprehensive opinionated architecture.
- **Cons:** High bundle weight, steep boilerplate overhead, and unnecessary complexity for a focused document verification client.

## Decision

We will use **React 19 with TypeScript 5.7+** and **Vite 6+** as the primary frontend framework and build system.

## Consequences

### Positive
- Strict compile-time validation prevents invalid DTO field transmissions.
- Static SPA output integrates directly into `zkdoc-api/src/main/resources/static`.
- Access to Radix UI primitives guarantees WCAG 2.2 AA accessibility.
- Rapid developer velocity via Vite HMR.

### Negative
- Client-side navigation requires explicit route forwarding in Spring Boot's `SpaForwardController.java`.

### Mitigations
- Maintain automated tests in `FrontendIntegrationTest.java` verifying that all registered SPA deep links successfully serve `index.html`.
