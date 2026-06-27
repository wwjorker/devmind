# DevMind 本地 Java 17 环境说明

## 为什么项目要求 Java 17

DevMind 后端使用 Spring Boot 3.x。Spring Boot 3.x 的最低要求是 Java 17，所以项目标准环境统一为：

```text
Java 17
Spring Boot 3.x
Maven
MySQL
Redis
```

这和 `F:\cangqiong` 项目互不影响。DevMind 在：

```text
F:\AI项目\devmind
```

苍穹外卖在：

```text
F:\cangqiong
```

两个项目是不同目录、不同 IDEA 项目、不同 Git 仓库，不需要互相改配置。

## 为什么你会看到 Java 19 或 Java 8

你电脑上可能同时存在多个 Java：

- 系统命令行里的 `java -version` 可能是 Java 8。
- IDEA 项目 SDK 里可能选中了 OpenJDK 19。
- GitHub Actions CI 使用的是 Java 17。

这不是项目被改坏了，而是不同工具使用了不同 JDK。

为了减少本地编译问题，建议把 DevMind 统一成 Java 17：

```text
IDEA Project SDK -> Java 17
Maven Runner JRE -> Java 17
Run Configuration JRE -> Java 17
```

## IDEA 里怎么设置

### 1. Project SDK

打开：

```text
File -> Project Structure -> Project
```

设置：

```text
SDK: Java 17
Language level: SDK default
```

### 2. Maven Runner JRE

打开：

```text
Settings -> Build, Execution, Deployment -> Build Tools -> Maven -> Runner
```

设置：

```text
JRE: Java 17
```

### 3. Spring Boot 运行配置

打开右上角 `DevMindApplication` 的运行配置：

```text
Run/Debug Configurations -> DevMindApplication
```

设置：

```text
JRE: Java 17
```

如果没有 Java 17，可以先安装 Temurin 17、Oracle JDK 17 或 IDEA 自动下载的 JDK 17。

## 怎么验证

在 IDEA 的 Terminal 里执行：

```bash
java -version
```

理想输出应该包含：

```text
17
```

然后在 `backend` 目录执行：

```bash
mvn test
```

如果本机 Maven 没配好，也可以先依赖 IDEA 运行后端，并以 GitHub Actions 的 CI 结果作为干净环境验证。

## 面试怎么解释

如果面试官问为什么用 Java 17，可以这样说：

```text
项目基于 Spring Boot 3.x，所以运行时统一使用 Java 17。为了避免本地和 CI 环境不一致，我把 README 和环境文档里都写清楚了 JDK 配置要求。
```
