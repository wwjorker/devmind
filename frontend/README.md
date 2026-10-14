# DevMind 前端

使用 Vue 3、Vite 和 TypeScript，提供文档管理、知识库问答、引用与日志、反馈评估，以及修复提案的人工审批界面。

## 本地运行

需要 Node.js 20.19+（20.x）或 22.12+。先按[后端说明](../backend/README.md#本地运行)启动服务，再从仓库根目录执行：

```powershell
cd frontend
npm ci
npm run dev
```

访问 <http://127.0.0.1:5173>。也可以在仓库根目录执行 `./start-local.ps1` 同时启动前后端；仅启动前端的脚本路径为 `./frontend/scripts/run-local.ps1`。

Vite 将 `/api` 和 `/v3` 请求代理到 `http://localhost:8081`。需要覆盖时，在启动前设置 `VITE_API_TARGET`，或写入本地忽略的 `.env.local`。页面通过 `/api/v1/health` 显示后端状态。

模型密钥只配置在后端，不写入前端环境变量。数据准备、mock 与离线提案的区别见[本地体验指南](../docs/guides/local-demo.md)。

## 构建与预览

在 `frontend` 目录执行：

```powershell
npm run build
npm run preview
```

构建执行 TypeScript 检查并输出到 `dist`；预览地址为 <http://127.0.0.1:4173>。预览主要用于检查静态产物，独立部署时仍需配置后端 API 转发，见[部署说明](../docs/operations/production-readiness.md)。
