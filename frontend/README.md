# CollabFlow frontend

React 19 + TypeScript + Vite. See [`../docs/frontend.md`](../docs/frontend.md) for the stack,
what's implemented, and two real bugs its own end-to-end test found; see the repo root
[`README.md`](../README.md) for how to run the whole stack.

```bash
npm install
npm run dev       # dev server on :5173
npm run build     # type-check + production build
npm run test:e2e  # Playwright, against a real running backend + dev server
```
