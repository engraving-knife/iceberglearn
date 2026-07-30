# 提交 2412：REST-Fixture: Ensure strict mode on jdbc catalog for rest fixture (#13599)

## 提交信息

- **序号**：2412 / 4088
- **哈希**：43bd9c386d49597caaec4742277c83706cb34f40
- **短哈希**：43bd9c386
- **日期**：2025-07-24 21:38:58 -0700
- **作者**：Jayce Slesar
- **提交说明**：REST-Fixture: Ensure strict mode on jdbc catalog for rest fixture (#13599)
- **PR/Issue**：#13599

## 总体目的

本提交为 Iceberg REST Fixture 的 Docker 镜像配置添加了 JDBC Catalog 的 strict mode（严格模式）环境变量。

Iceberg REST Fixture 是一个 Docker 镜像，用于提供 Iceberg REST Catalog 的测试/演示环境。它内部使用 SQLite 作为 JDBC Catalog 的后端存储。JDBC Catalog 有一个 strict mode 选项，当开启时，catalog 会以更严格的模式运行，对元数据操作进行更严格的校验。

在此之前，REST Fixture 的 Dockerfile 没有显式启用 JDBC Catalog 的 strict mode。本提交通过添加环境变量 `CATALOG_JDBC_STRICT__MODE=true` 来确保 REST Fixture 始终以严格模式运行，使测试环境更加严谨，更接近生产环境的校验标准。

## 如何达成设计目的

设计非常简单：在 Dockerfile 中新增一行环境变量声明。Iceberg REST Fixture 通过环境变量来配置 catalog 属性，环境变量名中的双下划线 `__` 会被转换为属性路径中的点 `.`，因此 `CATALOG_JDBC_STRICT__MODE` 对应 catalog 属性 `jdbc.strict-mode`。

## 修改详情

### `docker/iceberg-rest-fixture/Dockerfile` (+1/-0 lines)

**修改目的**：为 REST Fixture 的 JDBC Catalog 启用严格模式。

**工作逻辑**：在已有的 JDBC Catalog 配置环境变量（`CATALOG_CATALOG__IMPL`、`CATALOG_URI`、`CATALOG_JDBC_USER`、`CATALOG_JDBC_PASSWORD`）之后，新增 `ENV CATALOG_JDBC_STRICT__MODE=true`。这会将 `jdbc.strict-mode=true` 属性传递给 JDBC Catalog，使其在严格模式下运行。

## 总结

本提交是一个单行配置变更，为 Iceberg REST Fixture 的 JDBC Catalog 后端启用了严格模式。这使得 REST Fixture 在测试和演示时能够以更严格的校验标准运行，有助于更早地发现潜在问题。
