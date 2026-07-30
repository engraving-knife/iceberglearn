# 提交 3102：Build: Bump orc from 1.9.7 to 1.9.8 (#15025)

## 提交信息

- **序号**：3102 / 4088
- **哈希**：c73116ab377261fc3875ce2194f38fd6460e5719
- **短哈希**：c73116ab3
- **日期**：2026-01-11
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump orc from 1.9.7 to 1.9.8 (#15025)
- **PR/Issue**：#15025

## 总体目的

该提交由 Dependabot 自动生成，将 Apache ORC 从 1.9.7 升级到 1.9.8。ORC 是一种高性能列式存储文件格式，Iceberg 支持将表数据以 ORC 格式写入与读取（`ORCFileIO` 读写、`ORC` 写入器、读取器及对应的数据/删除文件编解码）。本次升级同时更新两个工件：`org.apache.orc:orc-core`（核心读写库）与 `org.apache.orc:orc-tools`（ORC 工具集），二者共享 `gradle/libs.versions.toml` 中的 `orc` 版本变量。

版本号从 1.9.7 升级到 1.9.8，属于语义版本中的 patch 级别升级（`version-update:semver-patch`）。根据提交元数据，两个依赖均归类为 `direct:production`。ORC 1.9.x 是维护中的稳定分支，patch 升级通常包含读写路径的 bug 修复、类型系统边界处理改进、以及对 Hadoop/记录读取器互操作的小修正。对 Iceberg 而言，预期影响是：ORC 数据/删除文件的读写行为保持一致，但可能修复特定类型（如 decimal、timestamp、复杂嵌套类型）或压缩场景下的缺陷，使 ORC 表的读写更稳定。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中的 `orc` 版本变量，从 `1.9.7` 改为 `1.9.8`。两个 ORC 工件通过 `version.ref = "orc"` 引用该变量，单点修改即可同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 orc 版本变量，同步更新 orc-core 与 orc-tools。

**工作逻辑**：
将 `orc = "1.9.7"` 修改为 `orc = "1.9.8"`。该变量被 `orc-core = { module = "org.apache.orc:orc-core", version.ref = "orc" }` 与 `orc-tools = { module = "org.apache.orc:orc-tools", version.ref = "orc" }` 引用。升级后，Iceberg 中所有以 ORC 格式读写数据/删除文件的代码路径（`core` 模块的 ORC 写入器与读取器，以及 `iceberg-orc` 集成）统一使用 1.9.8 的核心库。作为 patch 升级，ORC 文件格式本身不变，既有文件可正常读写。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 Apache ORC 从 1.9.7 提升到 1.9.8（patch 级别），同步更新 `orc-core` 与 `orc-tools`。ORC 是 Iceberg 支持的列式存储格式之一。作为 patch 升级，预期仅含读写路径 bug 修复与类型处理改进，不改变文件格式，对 ORC 表的读写无破坏性影响。
