# 提交 2221：Docs: Remove obsolete version attribute in quick start docker-compose.yml (#13139)

## 提交信息

- **序号**：2221 / 4088
- **哈希**：d59ea1ea842df86c93edc022e4f364cf4764cb2d
- **短哈希**：d59ea1ea8
- **日期**：2025-06-07 04:55:50 +0530
- **作者**：Om Kenge
- **提交说明**：Docs: Remove obsolete version attribute in quick start docker-compose.yml (#13139)
- **PR/Issue**：#13139

## 总体目的

这个提交是文档修复，移除了 Spark 快速入门文档中 docker-compose.yml 示例里的 `version: "3"` 属性。在 Docker Compose V2 中，`version` 属性已被废弃（obsolete），不再需要指定。保留该属性在新版本 Docker Compose 中会产生废弃警告。此修复使文档中的 docker-compose.yml 示例符合 Docker Compose V2 的最佳实践，避免用户在使用时遇到不必要的警告信息。

## 如何达成设计目的

- 在 `site/docs/spark-quickstart.md` 文件中的 docker-compose.yml 代码块内删除 `version: "3"` 行。

## 修改详情

### `site/docs/spark-quickstart.md` (修改, -1 line)

**修改目的**：移除废弃的 version 属性。

**工作逻辑**：删除 docker-compose.yml 代码块开头的 `version: "3"` 行，使 YAML 配置直接以 `services:` 开始。这是 Docker Compose V2 推荐的写法。

## 总结

该提交是单行文档修复，移除了 Spark 快速入门文档中 docker-compose.yml 示例的废弃 `version` 属性，使文档符合 Docker Compose V2 的最佳实践。改动极小但实用，避免了用户在使用新版本 Docker Compose 时遇到废弃警告。
