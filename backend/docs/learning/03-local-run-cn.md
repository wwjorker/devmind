# 03 本地运行说明

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 5.7+/8.0+
- IntelliJ IDEA 2024.1.2 或兼容版本

## 第一步：创建数据库

在 DBeaver、DataGrip、IDEA Database 或 MySQL 命令行中执行：

```sql
CREATE DATABASE IF NOT EXISTS devmind DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

只需要创建数据库，不需要手动建表。

从引入 Flyway 之后，表结构由项目启动时自动迁移：

```text
src/main/resources/db/migration/
```

当前初始化迁移文件：

```text
V1__init_schema.sql
```

如果本地数据库已经手动建过表，项目配置了：

```yaml
spring:
  flyway:
    baseline-on-migrate: true
```

含义是：Flyway 第一次接管已有数据库时，会把当前结构记录为基线，避免重复执行 V1 建表脚本。

## 第二步：配置 IDEA 运行环境变量

打开 `DevMindApplication` 的运行配置，设置 Environment variables。

最小配置：

```text
DEVMIND_DB_URL=jdbc:mysql://localhost:3306/devmind?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DEVMIND_DB_USERNAME=your_mysql_username
DEVMIND_DB_PASSWORD=your_mysql_password
DEVMIND_JWT_SECRET=replace_with_a_long_random_secret_for_non_local_use
DEVMIND_AI_PROVIDER=mock
```

如果要调用真实 DeepSeek：

```text
DEVMIND_AI_PROVIDER=deepseek
DEVMIND_DEEPSEEK_API_KEY=your_api_key
DEVMIND_DEEPSEEK_MODEL=deepseek-v4-flash
```

不要把真实 API Key 写进代码、README、`.http` 文件或 GitHub。

## 第三步：启动后端

运行主类：

```text
com.devmind.DevMindApplication
```

启动时 Flyway 会自动检查数据库迁移。

启动成功后访问：

```text
http://localhost:8081/swagger-ui.html
```

## 第四步：用 IDEA HTTP Client 联调

打开：

```text
docs/api/devmind-api.http
```

建议按顺序运行：

1. Register
2. Login
3. Create Knowledge Document
4. Ask AI
5. Submit AI Ask Feedback
6. Evaluation Summary

## 常见错误

### Access denied for user

说明数据库用户名或密码不正确。检查：

```text
DEVMIND_DB_USERNAME
DEVMIND_DB_PASSWORD
```

### Unknown database devmind

说明还没有创建 `devmind` 数据库。Flyway 可以建表，但不会替你创建 MySQL database。

### Flyway migration failed

常见原因：

- 数据库连接账号没有建表或改表权限。
- 手动改过表结构，和迁移脚本不一致。
- 新增迁移脚本命名不符合 Flyway 规范。

Flyway 脚本命名格式：

```text
V版本号__说明.sql
```

例如：

```text
V1__init_schema.sql
V2__add_vector_columns.sql
```

### 401 unauthorized

说明没有登录，或者请求头里没有：

```text
Authorization: Bearer <token>
```

### 端口 8081 被占用

修改 `src/main/resources/application.yml`：

```yaml
server:
  port: 8081
```
