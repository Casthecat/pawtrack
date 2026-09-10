# PawTrack frontend

React + TypeScript + Vite frontend for the PawTrack local portfolio demo.

See the [project README](../README.md) for backend startup, the demo walkthrough, API behavior and known boundaries.

```powershell
npm ci
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
npm run build
npm run lint
npx playwright install chromium
npm run test:care
```

The development proxy forwards /api and /uploads to http://127.0.0.1:9090. See .env.example for a separate API origin.

Pages: / (gallery), /cats/:id (profile and application form), /applications/:id (receipt), /staff (adoption review and new arrivals), /staff/care (health timeline and alert resolution). Staff access is open in the local demo; authentication and ownership checks are not implemented.

`test:care` starts Vite on 5175 and runs mocked-API desktop/narrow regressions. `npm run test:live` starts a fresh real demo backend on 19091 and Vite on 5173, then exercises both vertical slices. The live check requires Java 21, Maven, Chromium and both ports free. See the root README for database tests, evidence limits and all startup instructions.
