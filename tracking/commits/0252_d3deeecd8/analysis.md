# 提交 0252：Build: Bump net.snowflake:snowflake-jdbc from 3.14.3 to 3.14.4 (#9257)

## 提交信息

- **序号**：0252 / 4088
- **哈希**：d3deeecd8133e8effc0eb59562808a6b7b7279d3
- **短哈希**：d3deeecd8
- **日期**：2023-12-10 11:04:45 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump net.snowflake:snowflake-jdbc from 3.14.3 to 3.14.4 (#9257)
- **PR/Issue**：#9257

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Iceberg 使用的 Snowflake JDBC 驱动 `net.snowflake:snowflake-jdbc` 从 3.14.3 升级到 3.14.4（一次 semver-patch 升级）。

Snowflake JDBC 驱动是 Iceberg 与 Snowflake 数据仓库交互的底层连接器：Iceberg 提供了对 Snowflake 目录（Snowflake Catalog）的支持，可以通过 Snowflake 的 JDBC 驱动来读写 Iceberg 表的元数据与数据。保持该驱动为最新 patch 版本可以获得 Snowflake 服务端协议的兼容性修复、连接稳定性改进以及安全漏洞修补。

作为 patch 版本升级，3.14.4 通常是 3.14.3 之上的 bug 修复与小幅增强，不会引入破坏性 API 变更，因此可以低风险合入。Dependabot 将其标记为 `direct:production` 类型依赖，表明它是直接用于生产构建的依赖，而非仅测试作用。这类周期性升级是 Iceberg 维护多存储后端、多目录后端兼容性的重要一环。

## 如何达成设计目的

设计思路简单：仅修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `snowflake-jdbc` 这一项的版本字符串，从 `3.14.3` 改为 `3.14.4`。由于 Iceberg 使用 Gradle 版本目录（Version Catalog）集中管理依赖坐标，所有引用该版本的模块都会自动获得新版本。改动规模为 1 个文件、1 行。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Snowflake JDBC 驱动的锁定版本从 3.14.3 提升至 3.14.4。

该文件是 Iceberg Gradle 构建的依赖版本目录（TOML 格式），集中声明所有第三方库的版本。`snowflake-jdbc = "3.14.x"` 这一项被下游模块（如与 Snowflake 集成的 catalog/模块）通过 `${libs.snowflake.jdbc}` 引用。本次仅把这一行的 patch 号从 3 改为 4，其余依赖项保持不变。这种集中式版本管理让依赖升级的评审与回滚都极为轻量。

## 小结

该提交通过升级 Snowflake JDBC 驱动至 3.14.4，保持 Iceberg 与 Snowflake 仓库后端连接器的最新 patch 版本同步，属于常规依赖维护。
