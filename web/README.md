# Web client

React 19 + TypeScript + Vite client for the chess platform. Requires Node 22.12 or newer
(see `.nvmrc`). The core rules library must be built first from the repository root:

```sh
./gradlew :core:jsNodeProductionLibraryDistribution
```

Then:

```sh
npm install
npm run dev        # http://localhost:5173, proxies /api to http://localhost:8080
npm run typecheck
npm run test
npm run build      # outputs dist/
npm run preview
```

`Dockerfile` builds the core library, the bundle and an nginx image (build context is the
repository root); `deploy/nginx.conf` serves the SPA and proxies `/api` to the `api` service.
