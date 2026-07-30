# 提交 0494：Build: Bump net.snowflake:snowflake-jdbc from 3.14.4 to 3.14.5 (#9570)

## 提交信息

| 字段 | 内容 |
|------|------|
| 序号 | 0494 |
| 完整哈希 | 1272145686a3edd33eb00042840020f679ffb284 |
| 短哈希 | 127214568 |
| 日期 | 2024-02-08 19:28:44 +0100 |
| 作者 | dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com> |
| 说明 | Build: Bump net.snowflake:snowflake-jdbc from 3.14.4 to 3.14.5 (#9570) |
| PR | #9570 |

文件统计：1 个文件，1 行新增 / 1 行删除。
- gradle/libs.versions.toml：版本号变更

提交由 dependabot 自动生成，附带的元数据声明：
- dependency-name: net.snowflake:snowflake-jdbc
- dependency-type: direct:production
- update-type: version-update:semver-patch

## 总体目的

本提交是一个由 Dependabot 自动发起的依赖版本升级，将 Snowflake JDBC 驱动 `net.snowflake:snowflake-jdbc` 从 3.14.4 升级到 3.14.5。Snowflake JDBC 驱动是 `iceberg-snowflake` 模块在运行时连接 Snowflake 数据云所必需的客户端驱动，它负责建立到 Snowflake 的 JDBC 连接、执行 SQL、传输数据等底层操作。保持该驱动为最新补丁版本有助于获取 Snowflake 官方发布的缺陷修复、安全补丁与小功能改进。

由于本次升级属于 semver 语义版本中的 patch（补丁）级别（3.14.4 → 3.14.5），按照语义化版本约定，它只包含向后兼容的缺陷修复，不引入破坏性 API 变更，因此风险较低。Dependabot 在 PR 描述中给出了 Snowflake JDBC 仓库的发布说明、变更日志与提交对比链接，便于维护者评估升级内容后合并。Iceberg 项目通过 Dependabot 持续跟踪此类生产依赖的版本更新，以避免依赖长期停留在含已知缺陷的旧版本上。

该依赖在 Iceberg 中被集中管理于 Gradle version catalog（`gradle/libs.versions.toml`），并通过 `libs.snowflake.jdbc` 在 `iceberg-snowflake` 模块以 `runtimeOnly` 方式引入（`build.gradle` 中的 `runtimeOnly libs.snowflake.jdbc`），同时在各 Spark 子模块的构建中以 `exclude` 形式排除其传递依赖，避免与 Spark 自带的依赖冲突。

## 如何达成设计目的

实现路径极为简单：在 Gradle version catalog 文件 `gradle/libs.versions.toml` 中，将 `snowflake-jdbc` 版本引用从 `"3.14.4"` 改为 `"3.14.5"`。由于该版本号通过 `version.ref` 机制被 library 坐标引用，所有使用 `libs.snowflake.jdbc` 的模块会自动获得新版本，无需修改任何构建脚本或代码。

## 修改详情

### gradle/libs.versions.toml

**修改目的**：升级 Snowflake JDBC 驱动版本号。

**工作逻辑**：该文件是 Gradle 的 version catalog，集中管理项目所有依赖的版本与坐标。在 `[versions]` 区存在一行：

```toml
snowflake-jdbc = "3.14.4"
```

修改为：

```toml
snowflake-jdbc = "3.14.5"
```

而在 `[libraries]` 区通过版本引用声明坐标（本次未改动）：

```toml
snowflake-jdbc = { module = "net.snowflake:snowflake-jdbc", version.ref = "snowflake-jdbc" }
```

`version.ref = "snowflake-jdbc"` 表示该库使用 `[versions]` 中 `snowflake-jdbc` 键对应的版本字符串。因此只需修改版本号一处，所有引用 `libs.snowflake.jdbc` 的位置（如根 `build.gradle` 中 `:iceberg-snowflake` 项目的 `runtimeOnly libs.snowflake.jdbc`）都会解析到 3.14.5。这种集中式版本管理方式使依赖升级成本最小化，也保证了版本一致性。

## 小结

本次提交是 Iceberg 1.4.x 周期内由 Dependabot 自动生成的依赖维护改动，规模极小（1 文件 1 行），将 Snowflake JDBC 驱动从 3.14.4 升级到 3.14.5。该驱动是 `iceberg-snowflake` 模块连接 Snowflake 的运行时依赖，通过 Gradle version catalog 集中管理，本次仅修改版本引用一处即全项目生效。作为 semver patch 级升级，预期仅含向后兼容的缺陷修复，风险低。此类自动化的依赖升级是项目保持依赖健康、及时获取上游修复的常规维护手段。
