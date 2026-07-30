# 提交 2573：Flink: Backport Improve ConfigOption Descriptions for Flink Table Maintenance Configs. (#13944)

## 提交信息

- **序号**：2573 / 4088
- **哈希**：f2e5839e8957c1936ca35b1007237aaad4856ced
- **短哈希**：f2e5839e8
- **日期**：2025-08-28 20:41:59 -0700
- **作者**：slfan1989
- **提交说明**：Flink: Backport Improve ConfigOption Descriptions for Flink Table Maintenance Configs. (#13944)
- **PR/Issue**：#13944（backport of #13832）

## 总体目的

此次提交是提交 2569（PR #13832）的 backport（回移植），将 Flink 表维护配置项描述改进同步到 Flink v1.19 和 v1.20 模块。原始 PR #13832 只修改了 `flink/v2.0` 模块，而 Iceberg 需要同时维护多个 Flink 版本分支（v1.19、v1.20、v2.0），因此需要将相同的改动应用到旧版本模块中。

backport 的动机是确保所有受支持的 Flink 版本都具备一致的配置项描述体验，避免 v1.19/v1.20 用户在配置 Flink 表维护参数时仍因缺少 `withDescription` 而无法看到参数说明。这与 2569 的目的一致：为 `ConfigOption` 补充描述、为 getter 方法补充 Javadoc，提升配置可发现性与代码可读性。

## 如何达成设计目的

- 将 2569 中对 `flink/v2.0` 模块三个配置类的改动原样应用到 `flink/v1.19` 和 `flink/v1.20` 模块对应的同名文件。
- 改动内容与 2569 完全一致：为所有 `ConfigOption` 追加 `.withDescription(...)`，为 getter 方法追加 Javadoc，并将链式调用拆分为多行格式。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/FlinkMaintenanceConfig.java` (+24/-4)

**修改目的**：为 v1.19 顶层维护配置项补全描述与 Javadoc（同 2569）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/LockConfig.java` (+53/-13)

**修改目的**：为 v1.19 锁配置（JDBC/Zookeeper）补全描述与 Javadoc（同 2569）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFilesConfig.java` (+48/-8)

**修改目的**：为 v1.19 数据文件重写配置补全描述与 Javadoc（同 2569）。

### `flink/v1.20/flink/.../FlinkMaintenanceConfig.java` (+24/-4)

**修改目的**：为 v1.20 顶层维护配置项补全描述与 Javadoc（同 2569）。

### `flink/v1.20/flink/.../LockConfig.java` (+53/-13)

**修改目的**：为 v1.20 锁配置补全描述与 Javadoc（同 2569）。

### `flink/v1.20/flink/.../RewriteDataFilesConfig.java` (+48/-8)

**修改目的**：为 v1.20 数据文件重写配置补全描述与 Javadoc（同 2569）。

## 总结

一次纯 backport 提交，将 2569（PR #13832）对 Flink 表维护配置项描述的改进从 `flink/v2.0` 同步到 `flink/v1.19` 和 `flink/v1.20` 两个版本模块，确保各 Flink 版本配置体验一致。无新增逻辑，改动内容与原 PR 完全相同。
