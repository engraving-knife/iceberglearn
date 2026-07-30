# 提交 2657：Flink: Add back v2.0 directory

## 提交信息

- **序号**：2657 / 4088
- **哈希**：f4d892e2cbdf766a4d15df5e387659791676282c
- **短哈希**：f4d892e2c
- **日期**：2025-09-19 07:36:48 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add back v2.0 directory
- **PR/Issue**：无（此提交是 Flink 2.1 迁移系列的一部分）

## 总体目的

本提交是 Flink 2.1 版本支持迁移工作的第二步。在前一个提交（2655）中，`flink/v2.0/` 目录被整体移动到 `flink/v2.1/`，导致 v2.0 目录消失。但由于 Iceberg 需要同时维护 Flink 2.0 和 2.1 两个版本的支持，v2.0 目录必须保留。

因此本提交将 `flink/v2.0/` 目录恢复回来，重新创建与移动前相同的 Flink 2.0 源码。这样，代码库中同时存在 `flink/v2.0/`（Flink 2.0 支持）和 `flink/v2.1/`（Flink 2.1 支持，基于 v2.0 代码副本，后续将被适配修改）两个目录。

这种"移动后恢复"的方式相比直接复制，在 Git 历史中能更清晰地表达"v2.1 是从 v2.0 派生而来"的意图，同时保持两个版本目录的独立性。

## 如何达成设计目的

将原 `flink/v2.0/` 目录下的所有文件（473 个文件，约 88428 行代码）重新创建到 `flink/v2.0/` 路径下。文件内容与移动前完全一致，包括 build.gradle、所有 Java 源码、测试代码、LICENSE、NOTICE 等。

## 修改详情

### `flink/v2.0/` (473 files, +88428 lines)

**修改目的**：恢复被移动的 Flink 2.0 源码目录，使 v2.0 和 v2.1 能同时存在。

**工作逻辑**：重新创建 `flink/v2.0/` 目录下的全部文件，内容与提交 2655 移动前的 v2.0 代码完全一致。包括：
- `build.gradle`：Flink 2.0 的构建配置
- `flink-runtime/LICENSE`、`flink-runtime/NOTICE`：运行时许可文件
- `flink/src/main/java/`：Flink-Iceberg 集成的全部主源码（FlinkCatalog、FlinkSink、IcebergSource 等）
- `flink/src/test/java/`：全部测试代码
- META-INF 服务文件和资源文件

## 总结

本提交恢复了在提交 2655 中被移动的 `flink/v2.0/` 目录，使代码库中同时存在 v2.0 和 v2.1 两个 Flink 版本的源码目录。这是实现 Flink 2.0 和 2.1 并行支持的必要步骤。新增的 v2.0 代码与原代码完全一致，v2.1 目录将在后续提交中进行适配修改。
