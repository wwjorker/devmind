# Redis Logout 黑名单验证

这份文档用于验证 DevMind 的 JWT logout blacklist 是否真正生效。

## 这一步在验证什么

DevMind 使用 JWT 做登录态。JWT 本身是无状态的，服务端默认不保存 session，所以用户点击退出登录后，如果不额外处理，旧 token 在过期前仍然可能继续访问接口。

项目里的做法是：

1. 用户调用 `/api/v1/auth/logout`。
2. 后端计算当前 token 距离过期还剩多久。
3. 把 token 的 SHA-256 hash 写入 Redis 黑名单，并设置相同 TTL。
4. 后续请求进入 `JwtAuthenticationFilter` 时，会检查该 token 是否在 Redis 黑名单中。
5. 如果命中黑名单，旧 token 会被拒绝。

## 当前项目边界

Redis 只影响退出登录后的旧 token 是否立刻失效。

Redis 不参与登录、文档管理、RAG 检索、AI 问答、评估统计这些主流程。

如果 Redis 没启动，DevMind 会 fail-open：后端会记录 warning 日志，但不会因为 Redis 连接失败导致主流程不可用。此时 logout 接口可能返回成功，但旧 token 不会被真正拉黑。

## 启动 Redis 的两种方式

### 方式一：Memurai 或 Windows 原生 Redis

如果 Redis 运行在本机默认端口 `6379`，DevMind 不需要额外配置，因为默认配置就是：

```text
DEVMIND_REDIS_HOST=localhost
DEVMIND_REDIS_PORT=6379
DEVMIND_REDIS_DATABASE=1
```

可以用 PowerShell 检查端口：

```powershell
Test-NetConnection 127.0.0.1 -Port 6379
```

### 方式二：Docker Compose

如果使用项目里的 Docker Compose，Redis 容器端口映射是：

```text
6380:6379
```

也就是说，本机访问端口是 `6380`。启动命令：

```powershell
docker compose -f F:\AI项目\devmind\backend\docker-compose.yml up -d redis
```

然后在 IDEA 的 `DevMindApplication` 运行配置里加：

```text
DEVMIND_REDIS_PORT=6380
```

## 端到端验证步骤

打开：

```text
F:\AI项目\devmind\backend\docs\api\devmind-api.http
```

按顺序执行：

1. `Login`
2. `Current User`
3. `Logout`
4. `Current User After Logout`

预期结果：

- Redis 已启动：第 4 步应该返回未认证或无权限，说明旧 token 已经被黑名单拦截。
- Redis 未启动：第 4 步可能仍然返回 200，这是 fail-open 的预期表现。

## 可选：检查 Redis 里的黑名单 key

如果本机有 `redis-cli`，可以查看：

```powershell
redis-cli -n 1 keys "devmind:jwt:blacklist:*"
```

也可以查看某个 key 的剩余时间：

```powershell
redis-cli -n 1 ttl "devmind:jwt:blacklist:<hash>"
```

如果使用 Docker Redis，可以进入容器后执行类似命令：

```powershell
docker exec -it <redis-container-name> redis-cli -n 1 keys "devmind:jwt:blacklist:*"
```

## 面试讲法

可以这样讲：

DevMind 使用 JWT 做无状态认证。JWT 的优点是服务端不用保存 session，但退出登录会有一个问题：旧 token 在过期前仍可能可用。为了解决这个问题，我在 logout 时把 token hash 写入 Redis 黑名单，并设置为 token 剩余有效期。认证过滤器每次解析 token 后都会检查黑名单，命中就拒绝请求。Redis 异常时项目采用 fail-open，避免 Redis 短暂不可用影响主业务，但这也意味着极端情况下旧 token 可能不会被立即吊销。

