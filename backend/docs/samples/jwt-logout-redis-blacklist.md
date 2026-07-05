# JWT 无状态认证与登出黑名单

## JWT 是什么

JSON Web Token，三段式 `header.payload.signature`，用 `.` 连接、Base64Url 编码：

- **Header**：算法（如 HS256）和类型。
- **Payload**：claims，如 `sub`（用户）、`exp`（过期时间）、自定义字段。**只是编码不是加密**，别放敏感信息。
- **Signature**：用密钥对前两段签名，防篡改。

服务端只验签名和 `exp`，不需要查库存 session —— 这就是**无状态**，天然适合分布式 / 多实例，不用共享 session。

## 无状态带来的登出难题

正因为服务端不存 session，**用户点了"退出登录"，那个 token 在 `exp` 之前依然有效**。只删客户端的 token 挡不住别人拿到旧 token 继续用。

## 黑名单方案

登出时把 token 记进一个"已失效"名单，认证时多查一步：

1. 登出：解析 token 拿到剩余有效期，把 token（或其 SHA-256 哈希）写入 Redis，**TTL = 剩余有效期**。
2. 认证过滤器：验签 + 验 `exp` 之后，再查 Redis 是否在黑名单，命中就拒绝。

**为什么用 Redis**：读写快、适合放在过滤器里做轻校验；**TTL 到期自动清理**，token 反正也过期了，黑名单条目跟着消失，不用定时任务扫。
**为什么存哈希**：token 本身较长，存 SHA-256 定长、省内存，也避免明文 token 落在缓存里。

## 权衡

- 黑名单让"纯无状态"打了折扣（多一次 Redis 查询），但只在登出这种低频场景引入状态，可接受。
- **Redis 挂了怎么办**：一般选 fail-open（查不到就放行），保证可用性不被缓存拖垮；安全要求高的场景才 fail-close。

## 延伸

- 另一种思路：**短命 access token + refresh token**，access 很快过期，登出时废掉 refresh，不用黑名单。
- 想全局踢人（改密码后让所有旧 token 失效）：给用户存一个 `tokenVersion`，签发时写进 payload，校验时比对。
