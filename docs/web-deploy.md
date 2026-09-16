# Web deploy (GitHub Actions + Docker over SSH)

`.github/workflows/deploy-web.yml` builds the prod web bundles on the self-hosted runner and deploys them to the
webhost as an nginx container. Every push to `main` deploys; `workflow_dispatch` reruns it by hand.

## Design

The web app is static files, so the container is `nginx:alpine` plus two directories:

| URL path | Gradle task                          | Copied from                                |
|----------|--------------------------------------|--------------------------------------------|
| `/`      | `:webApp:wasmJsBrowserDistribution`  | `webApp/build/dist/wasmJs/productionExecutable` |
| `/js/`   | `:webApp:jsBrowserDistribution`      | `webApp/build/dist/js/productionExecutable`     |

wasmJs is the primary target (see `docs/web-architecture.md`); `/js/` is a manual fallback for browsers without
wasm-GC. Both `index.html`s reference their assets relatively (`webApp.js`, `sqlite3.js`, `skiko.wasm`), so serving the
js build from a subdirectory needs no rewriting.

Two build steps, in two places:

1. **Gradle runs on the runner.** It already has the JDK / Android SDK / Gradle setup `tests.yml` uses; a multi-stage
   Docker build would re-download all of that on every run. The task is always `-Pnonogram.env=prod`, so the bundle
   carries `webApp/src/prod/.../FirebaseConfig.web.kt`.
2. **The image is built on the host.** The deploy step sets `DOCKER_HOST=ssh://<user>@<host>` and runs
   `docker compose -f webApp/compose.yml up -d --build`. The Docker CLI streams the build context to the host's daemon
   over SSH, the image is built and started there, and nothing is pushed to a registry. `webApp/.dockerignore` is a
   whitelist, so the context is just `nginx.conf` and the two dist folders (source maps excluded).

The files: `webApp/Dockerfile`, `webApp/nginx.conf`, `webApp/compose.yml`, `webApp/.dockerignore`.

`nginx.conf` serves the content-hashed `*.wasm` chunks as `immutable` and everything else — whose names are stable
across deploys — as `no-cache`. It sets no COOP/COEP headers: the `opfs-sahpool` VFS was chosen precisely so none are
needed. `application/wasm` comes from nginx's stock `mime.types`.

`compose.yml` publishes the container on `127.0.0.1:${WEB_PORT:-8080}` only. TLS is the host's reverse proxy's job —
OPFS needs a secure context, so the site must be reached over `https://`.

## One-time setup

### Runner machine

- Docker CLI (with the compose plugin) installed; the runner's user can run `docker`.
- SSH key for `<user>@<host>` in the runner user's `~/.ssh`, and the host's key already in `known_hosts`
  (`ssh <user>@<host> docker info` must work non-interactively as the runner's user).

### Webhost

- Docker Engine + compose plugin; `<user>` is in the `docker` group.
- Reverse proxy rule: `https://<domain>` → `http://127.0.0.1:8080` (or whatever `WEB_PORT` is set to). If the proxy
  runs in Docker on the same host, replace the `ports` entry in `compose.yml` with a shared external network instead.

### GitHub repository → Settings → Secrets and variables → Actions → Variables

| Variable            | Value                              |
|---------------------|------------------------------------|
| `DEPLOY_SSH_TARGET` | `<user>@<host>` (required)         |
| `WEB_PORT`          | host port to publish on (optional, default `8080`) |

### Firebase / Google console (prod project `nonogram-trainpaths`)

- Google Cloud → OAuth client `GOOGLE_WEB_CLIENT_ID` → **Authorized JavaScript origins**: add `https://<domain>`.
- Firebase Authentication → Settings → **Authorized domains**: add `<domain>`.

Without both, the site loads but Google sign-in fails.

## Verifying a deploy

On the host:

```bash
docker ps --filter name=nonogram-web
curl -I  http://127.0.0.1:8080/          # 200
curl -I  http://127.0.0.1:8080/js/       # 200
curl -sI http://127.0.0.1:8080/sqlite3.wasm | grep -i content-type   # application/wasm
```

In a browser: `https://<domain>` loads, sign-in works, a puzzle in progress survives a reload (OPFS persisted);
`https://<domain>/js/` loads the JS build.

## Alternatives not taken

- **Registry (GHCR) + `docker compose pull` on the host** — needs a token on the host and a public/private package;
  the SSH Docker context needs neither. Switch to it if the image ever needs to be deployed to more than one host.
- **Automatic wasm → js fallback in `index.html`** — a feature-detect script could redirect to `/js/`; today the
  fallback is a manual URL.
