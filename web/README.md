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

The UI is translated with react-i18next; English is the default and the fallback, Russian is
the second language. Texts live in `src/i18n/en.json` and `src/i18n/ru.json` (the only file in
the repository that may contain Cyrillic); `src/i18n/resources.test.ts` keeps their key sets
identical. Keys are type-checked against `en.json` (`src/i18n/i18next.d.ts`).

`Dockerfile` builds the core library, the bundle and an nginx image (build context is the
repository root); `deploy/nginx.conf` serves the SPA and proxies `/api` to the `api` service.
