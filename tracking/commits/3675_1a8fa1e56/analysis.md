# 提交 3675：Docs: Document CATALOG_* env vars in iceberg-rest-fixture README (#16007)

## 提交信息

- **序号**：3675 / 4088
- **哈希**：1a8fa1e56bf81ed1780817bd63cdccfe1a71281f
- **短哈希**：1a8fa1e56
- **日期**：2026-05-09 10:19:34 -0700
- **作者**：Rexwell Minnis
- **提交说明**：Docs: Document CATALOG_* env vars in iceberg-rest-fixture README (#16007)
- **PR/Issue**：#16007（refs #14972）

## 总体目的

这个提交为 `iceberg-rest-fixture` Docker 镜像的 README 新增了配置文档，说明如何通过 `CATALOG_*` 环境变量配置后端 catalog。

iceberg-rest-fixture 是一个将现有 catalog 后端包装为 REST 接口的 Docker 镜像。它支持通过 `CATALOG_*` 环境变量配置 catalog 属性，转换规则为：去除 `CATALOG_` 前缀，单下划线 `_` 变为点 `.`，双下划线 `__` 变为连字符 `-`，名称转小写。例如 `CATALOG_JDBC_USER` 对应 `jdbc.user`，`CATALOG_CATALOG__IMPL` 对应 `catalog-impl`。

此前这一配置机制未在 README 中文档化，用户只能通过阅读源码发现。本提交新增 Configuration 章节，明确说明 `CATALOG_*` 约定、映射表、catalog 名称覆盖方式，以及默认使用内存 SQLite JdbcCatalog 的行为。

## 如何达成设计目的

在 `docker/iceberg-rest-fixture/README.md` 中新增 Configuration 章节，包含：
1. Backend catalog properties 子节：说明 `CATALOG_*` 转换规则和映射表。
2. Catalog name 子节：说明默认 catalog 名 `rest_backend` 及如何通过 `CATALOG_CATALOG_NAME` 覆盖。

## 修改详情

### `docker/iceberg-rest-fixture/README.md` (+33 lines)

**修改目的**：新增配置文档。

**工作逻辑**：
1. **Backend catalog properties** 子节：
   - 说明 `CATALOG_*` 转换规则：去除 `CATALOG_` 前缀，单 `_` → `.`, 双 `__` → `-`, 转小写。
   - 映射表：
     | Env var | Catalog property |
     |---|---|
     | `CATALOG_CATALOG_NAME` | `catalog.name` |
     | `CATALOG_WAREHOUSE` | `warehouse` |
     | `CATALOG_URI` | `uri` |
     | `CATALOG_CATALOG__IMPL` | `catalog-impl` |
     | `CATALOG_IO__IMPL` | `io-impl` |
     | `CATALOG_JDBC_USER` | `jdbc.user` |
   - 说明未设置 `catalog-impl` 和 `uri` 时默认使用内存 SQLite `JdbcCatalog`。

2. **Catalog name** 子节：
   - 说明默认 catalog 名为 `rest_backend`。
   - 提供 docker run 示例覆盖 catalog 名：
     ```bash
     docker run -e CATALOG_CATALOG_NAME=mycatalog -p 8181:8181 apache/iceberg-rest-fixture
     ```

## 总结

这是一个纯文档提交，为 iceberg-rest-fixture Docker 镜像的 README 新增了配置文档，说明如何通过 `CATALOG_*` 环境变量配置后端 catalog 属性。文档包含转换规则、映射表、catalog 名称覆盖示例和默认行为说明，使用户无需阅读源码即可配置 fixture。这解决了配置机制此前未文档化的问题。
