# 提交 2298：REST-Fixture: Move sqlite backend from memory to file (#13367)

## 提交信息

- **序号**：2298 / 4088
- **哈希**：45e3ae30b1ad6f80e20d6ca30163c342e7c9cd81
- **短哈希**：45e3ae30b
- **日期**：2025-07-01 11:51:37 +0200
- **作者**：Kevin Liu
- **提交说明**：REST-Fixture: Move sqlite backend from memory to file (#13367)
- **PR/Issue**：#13367

## 总体目的

本提交将 Iceberg REST Fixture Docker 镜像中的 SQLite 后端从内存模式（`jdbc:sqlite::memory:`）改为文件模式（`jdbc:sqlite:/tmp/iceberg_catalog.db`）。

Iceberg REST Fixture 是一个用于测试和演示的 Docker 镜像，包含一个运行 Iceberg REST Catalog 服务的容器。该服务使用 SQLite 作为 JDBC 后端存储目录元数据。此前使用内存模式意味着每次容器重启后所有目录数据都会丢失，改为文件模式后数据可以持久化到 `/tmp/iceberg_catalog.db` 文件中。

这个改动对于需要跨容器重启保持状态的测试和演示场景非常有用，也使得调试更加方便——可以直接检查 SQLite 数据库文件的内容。

## 如何达成设计目的

通过修改 Dockerfile 中的 `CATALOG_URI` 环境变量，将 SQLite 连接字符串从内存模式改为文件模式。这是一个单行配置修改，不涉及任何代码逻辑变更。

## 修改详情

### `docker/iceberg-rest-fixture/Dockerfile` (+1/-1 lines)

**修改目的**：将 SQLite 后端从内存模式改为文件持久化模式。

**工作逻辑**：将环境变量 `CATALOG_URI` 的值从 `jdbc:sqlite::memory:`（内存数据库，数据不持久化）修改为 `jdbc:sqlite:/tmp/iceberg_catalog.db`（文件数据库，数据持久化到 `/tmp` 目录下的文件中）。容器启动时会使用文件路径创建或打开 SQLite 数据库，数据在容器重启后仍然保留（只要 `/tmp` 目录的存储未被清理）。

## 总结

本提交是一个基础设施配置改进，将 REST Fixture 的 SQLite 后端从内存模式改为文件模式，使目录元数据能够在容器重启后持久化。这对于测试、演示和调试场景都有实际价值，特别是需要检查数据库状态或跨重启保持状态的场景。
