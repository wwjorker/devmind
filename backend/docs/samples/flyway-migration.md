# Flyway 数据库版本管理

## 解决什么问题

手动在客户端跑 SQL 建表，长期会出现**环境不一致**：本地加了字段，另一台机器 / 测试 / 生产忘了执行同一份 SQL，启动时就报"字段缺失"。数据库结构没有像代码一样进版本管理，就没法追溯"这张表什么时候、因为什么改的"。

## 核心思路

把每次结构变更写成一个**版本化脚本**，放进代码仓库，应用启动时自动比对并补齐。

- 脚本目录：`src/main/resources/db/migration`
- 命名：`V{版本}__{说明}.sql`，如 `V1__init_schema.sql`、`V2__add_vector_table.sql`
- Flyway 建一张 `flyway_schema_history` 表，记录已执行过的脚本和校验和（checksum）。
- 启动时对比历史表：只执行没跑过的、且**已执行脚本内容不能改**（checksum 变了会报错，逼你新建 V3 而不是改 V2）。

## 关键概念

- **Versioned migration（V）**：一次性、按顺序执行，改结构用它。
- **Repeatable migration（R）**：每次内容变了就重跑，适合视图、存储过程。
- **Baseline**：给已经存在数据的老库设一个起点版本，让 Flyway 从这之后开始接管。
- **不可回滚**：社区版没有自动 down/回滚，回退靠再写一个新的正向脚本（如 `V4__drop_xxx.sql`）。

## 价值

- 数据库结构随代码一起进 Git，可追溯、可 review。
- 新环境只需建空库，启动即自动建表，不依赖人工复制 SQL。
- 多人 / 多环境不再结构漂移。

## 延伸

- Flyway vs Liquibase：Flyway 用原生 SQL、上手简单；Liquibase 用 XML/YAML 抽象、支持数据库无关和自动回滚，更重。
- 生产迁移要小心大表加索引 / 改字段的锁表问题，必要时用 `pt-online-schema-change` 之类工具。
