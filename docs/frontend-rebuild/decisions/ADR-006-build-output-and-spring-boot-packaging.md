# ADR-006: Build Output and Spring Boot Monolithic Packaging

## Status

Accepted

## Context

The backend has been developed through 24 phases as a self-contained Spring Boot application running OpenJDK 21. It is packaged as a standalone executable JAR containing all backend classes, dependencies, and embedded static web resources.

The packaging pipeline is defined in `zkdoc-api/pom.xml` using `frontend-maven-plugin` (v2.0.1):
1. Downloads and pins Node.js `v24.21.0` and npm `11.19.0`.
2. Executes `npm ci` in `../frontend`.
3. Executes `npm run build` in `../frontend`.
4. Outputs the compiled static SPA assets into `zkdoc-api/src/main/resources/static`.
5. Embeds the static assets into the JAR during `mvnw package`.
6. Serves the static assets via Spring Boot's resource handler and `SpaForwardController.java`.

Furthermore, `FrontendIntegrationTest.java` actively verifies that deep links like `/`, `/verify`, `/credentials`, `/verification` serve `index.html`.

## Decision Drivers

* **Operational Simplicity:** Retain single-command deployment (`java -jar zkdoc-api.jar`) without requiring external Nginx or separate web server containers.
* **CI/CD Compatibility:** Guarantee that `mvnw clean verify` continues to pass cleanly on all environments.
* **Zero Disruption to Existing Tests:** Prevent breaking `FrontendIntegrationTest` during frontend scaffolding.

## Decision

1. **Output Directory:** The rebuilt frontend in `frontend/vite.config.ts` will explicitly target:
   ```typescript
   build: {
     outDir: '../zkdoc-api/src/main/resources/static',
     emptyOutDir: true
   }
   ```
2. **Preservation During Scaffolding:** During Phase F3 scaffolding, `zkdoc-api/src/main/resources/static/index.html` will NOT be prematurely deleted. The new build will naturally overwrite static assets upon running `npm run build`.
3. **SPA Forward Controller Alignment:** All client-side routes will be mapped in `SpaForwardController.java` to forward to `/index.html`.
4. **Development Workflow:** For local development, `npm run dev` in `frontend/` runs Vite's development server with a proxy forwarding `/api` and `/actuator` calls to `http://localhost:8080`.

## Consequences

### Positive
- Fully preserves the existing Maven and Docker deployment pipeline.
- Developers can use fast Vite HMR during development while retaining monolithic single-JAR packaging for production.
- `FrontendIntegrationTest` continues to pass cleanly throughout the rebuild lifecycle.

### Negative
- Running `mvnw package` triggers the full Node build, adding build time if node modules are not cached.
