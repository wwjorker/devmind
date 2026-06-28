# JWT 退出登录与 Redis 黑名单

## 问题

JWT 是无状态 token。服务端默认不保存会话，所以用户点击退出登录后，如果不做额外处理，旧 token 在过期前仍然可能继续访问接口。

## 设计

DevMind 在退出登录时会解析当前 token 的剩余有效期，把 token 写入 Redis blacklist，并设置 TTL 为 token 的剩余过期时间。

认证过滤器在校验 JWT 时，除了验证签名和过期时间，还会查询 Redis blacklist。如果 token 已经在黑名单中，就拒绝本次请求。

## 为什么用 Redis

- Redis 读写快，适合在认证过滤器中做轻量校验。
- TTL 可以自动清理过期 token，不需要定时任务清理黑名单。
- 不破坏 JWT 的无状态设计，只是在退出登录场景增加一次黑名单检查。

## 面试表达

JWT 的优点是无状态，但退出登录会带来“未过期 token 仍可用”的问题。DevMind 使用 Redis blacklist 保存已退出 token，并设置剩余 TTL，让认证过滤器可以拦截已退出 token。
