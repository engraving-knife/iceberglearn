# 提交 2656：Flink: Move Flink v2.0 to v2.1 directory

## 提交信息

- **序号**：2656 / 4088
- **哈希**：ded2d0a945eb04234e592f18dba748681facc223
- **短哈希**：ded2d0a94
- **日期**：2025-09-19 07:36:48 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Move Flink v2.0 to v2.1 directory
- **PR/Issue**：无（此提交是 Flink 2.1 迁移系列的一部分，属于内部重构）

## 总体目的

本提交是 Flink 2.1 版本支持迁移工作的第一步，将整个 `flink/v2.0/` 目录重命名（移动）为 `flink/v2.1/`。这是为支持 Flink 2.1 版本做准备——以现有的 Flink 2.0 代码为基础，通过目录重命名创建 Flink 2.1 的代码副本，后续提交再对其进行适配修改。

这个迁移是 Flink 版本支持策略的一部分。Iceberg 的 Flink 集成采用多版本并行支持的架构，每个 Flink 大版本有独立的源码目录（如 `v1.19`、`v1.20`、`v2.0`、`v2.1`）。新增 Flink 2.1 支持时，最合理的方式是以最接近的 2.0 版本代码为起点。

本提交是系列迁移提交（2655-2659）中的第一个，后续提交包括：恢复 v2.0 目录（2656）、调整构建脚本（2657）、适配 Flink 2.1 API 代码变更（2658）、移除 Flink 1.19 支持（2659）。

## 如何达成设计目的

通过 `git mv` 将 `flink/v2.0/` 目录下所有文件（包括 build.gradle、flink-runtime 的 LICENSE/NOTICE、所有 Java 源码和测试文件）整体移动到 `flink/v2.1/` 目录。这是一个纯重命名操作，不修改任何文件内容——所有文件的行数变更均为 0。

## 修改详情

### `flink/v2.0/` → `flink/v2.1/` (大量文件，0 行变更)

**修改目的**：将 Flink 2.0 的源码目录重命名为 v2.1，作为 Flink 2.1 支持的代码基础。

**工作逻辑**：整个 `flink/v2.0/` 目录被移动到 `flink/v2.1/`，包括：
- `build.gradle` 构建文件
- `flink-runtime/` 子目录（含 LICENSE、NOTICE）
- `flink/src/main/java/` 下的所有 Flink-Iceberg 集成源码
- `flink/src/test/java/` 下的所有测试代码
- 相关的 META-INF 服务文件和资源文件

所有文件内容保持不变，仅路径中的 `v2.0` 变为 `v2.1`。

## 总结

本提交是 Flink 2.1 版本支持迁移的第一步，将 `flink/v2.0/` 目录整体重命名为 `flink/v2.1/`，作为 Flink 2.1 代码的起点。这是一个纯目录移动操作，不涉及任何代码内容修改。后续提交将恢复 v2.0 目录、调整构建脚本并适配 Flink 2.1 的 API 差异。
