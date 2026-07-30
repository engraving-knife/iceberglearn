# 提交 1293：Build: Bump testcontainers from 1.20.2 to 1.20.3 (#11404)

## 提交信息

- **序号**：1293 / 4088
- **哈希**：6e911e0ea34499333c92406784b047402884bc54
- **短哈希**：6e911e0ea
- **日期**：2024-10-28（Mon Oct 28 14:16:03 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump testcontainers from 1.20.2 to 1.20.3 (#11404)
- **PR/Issue**：#11404

## 总体目的

Iceberg 仓库在集成测试中广泛使用 Testcontainers 框架（通过 Docker 容器启动 PostgreSQL、MinIO、MySQL 等外部依赖服务）。本次提交将 Testcontainers 从 1.20.2 升级到 1.20.3，属于 semver-patch 级别的补丁更新。该升级涉及三个 Testcontainers 组件：核心库 `org.testcontainers:testcontainers`、JUnit Jupiter 集成 `org.testcontainers:junit-jupiter` 以及 MinIO 模块 `org.testcontainers:minio`，三者共用同一版本号。

## 如何达成设计目的

Dependabot 检测到 `gradle/libs.versions.toml` 中 `testcontainers` 版本声明为 1.20.2，自动将其更新为 1.20.3。由于三个 Testcontainers 组件均通过 `version.ref = "testcontainers"` 引用同一版本变量，因此只需修改一处版本号即可同时升级三个组件。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Testcontainers 版本从 1.20.2 升级到 1.20.3。

**工作逻辑**：在版本目录的 `[versions]` 区块中，将：

```toml
testcontainers = "1.20.2"
```

修改为：

```toml
testcontainers = "1.20.3"
```

该版本号位于 `sqlite-jdbc` 和 `tez010` 之间。在 `[libraries]` 区块中，以下三个库均通过 `version.ref = "testcontainers"` 引用该版本：

- `testcontainers = { module = "org.testcontainers:testcontainers", ... }`
- `testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter", ... }`
- `testcontainers-minio = { module = "org.testcontainers:minio", ... }`

因此一行修改即可完成三组件同步升级。Testcontainers 仅用于测试作用域（testImplementation），不影响生产产物。

## 小结

- **成效**：Testcontainers 三组件同步升级到 1.20.3，获取上游补丁修复，提升集成测试稳定性。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，无代码逻辑变更，仅影响测试。
- **回迁到 1.4.x 的注意事项**：Testcontainers 是测试专用依赖，不影响运行时产物。1.4.x 分支若集成测试正常，无需强制回迁。但如果 1.4.x 遇到 Testcontainers 相关的测试不稳定问题（如容器启动失败、端口冲突等），可以考虑回迁此补丁升级以获取修复。回迁风险极低。
