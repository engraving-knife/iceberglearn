# 提交 1724：Build: Bump Hive to 2.3.10 (#12253)

## 提交信息

- **序号**：1724 / 4088
- **哈希**：6604f47900c489ffb07f1a3d386effaec5002c79
- **短哈希**：6604f4790
- **日期**：2025-02-13 13:43:32 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Bump Hive to 2.3.10 (#12253)
- **PR/Issue**：#12253

## 总体目的

将 Hive 2 依赖从 2.3.9 升级到 2.3.10。Hive 2.3.10 是 Hive 2.3.x 系列的维护版本，包含 bug 修复和安全补丁。Iceberg 项目使用 Hive 2 作为 Hive 集成模块（Hive catalog、Hive metastore 等）的依赖。

版本声明使用了 Gradle 的 rich version 语法 `strictly`，表示严格锁定版本号，不允许 Gradle 自动升级到其他版本。这是为了确保所有模块使用一致的 Hive 版本，避免依赖冲突。

## 如何达成设计目的

修改 `gradle/libs.versions.toml` 中 `hive2` 的版本声明，从 `2.3.9` 改为 `2.3.10`，保留 `strictly` 严格版本约束。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 line）

**修改目的**：升级 Hive 2 依赖版本。

**工作逻辑**：将 `hive2 = { strictly = "2.3.9"}` 修改为 `hive2 = { strictly = "2.3.10"}`，保留严格版本约束和注释说明。所有引用 `hive2` 版本引用的模块（hive2-exec、hive2-metastore、hive2-service 等）都会自动使用新版本。

## 小结

- **成效**：Hive 2 依赖从 2.3.9 升级到 2.3.10，获取最新的 bug 修复和安全补丁。
- **影响范围**：所有使用 Hive 2 依赖的模块，包括 Hive catalog、Hive metastore 集成、MR 模块等。由于是补丁版本升级，API 兼容，影响面较小。
- **回迁到 1.4.x 的注意事项**：可以安全回迁，只需修改版本号。但需确认 1.4.x 分支中 Hive 2 的当前版本，以及 2.3.10 是否与 1.4.x 的其他依赖兼容。属于低风险的依赖更新，建议回迁以保持依赖安全和一致。
