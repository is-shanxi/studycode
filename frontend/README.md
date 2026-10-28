# 前端工程

Vue 3 + TypeScript + Vite 单页应用，通过 `/app`（用户端）和 `/admin`（管理端）分区。路由、页面和 API 客户端将在 P1 阶段接入。

## 命令

```powershell
pnpm install
pnpm dev          # http://localhost:5173
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm test:e2e     # 首次执行前运行 pnpm test:e2e:install
```

开发服务器把 `/api` 与 `/actuator` 代理到 `VITE_API_PROXY_TARGET`（默认 `http://localhost:8080`）。
