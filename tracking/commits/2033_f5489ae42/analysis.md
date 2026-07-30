# 提交 2033：Flink: Copy back v1.20 directory

## 提交信息

- **序号**：2033 / 4088
- **哈希**：f5489ae429aa679f80e113b7dfbd128d82f2adda
- **短哈希**：f5489ae42
- **日期**：2025-04-23 13:21:24 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Copy back v1.20 directory
- **PR/Issue**：无（属于 Flink 2.0 支持系列的一部分）

## 总体目的

本提交是 Flink 2.0 支持重构系列（提交 2032-2035）的第二步。在提交 2032 中，`flink/v1.20/` 目录被整体移动到了 `flink/v2.0/`，导致 Flink 1.20 的支持暂时丢失。

本提交的目的是恢复对 Flink 1.20 的支持：将 `flink/v2.0/` 目录中的全部内容复制一份回 `flink/v1.20/` 目录。此时 `v1.20` 和 `v2.0` 两个目录的内容完全相同（都是原 Flink 1.20 的代码），后续提交 2034 将只修改 `v2.0` 目录以适配 Flink 2.0 API，而 `v1.20` 目录保持原样继续支持 Flink 1.20。

这种"先移动再复制回来"的策略相比直接"复制 v1.20 到 v2.0"的好处是：git 能更好地跟踪文件历史，且让审查者清楚地看到 v2.0 是从 v1.20 派生出来的。

## 如何达成设计目的

将 `flink/v2.0/` 下的所有文件（378 个文件，约 69782 行）原样复制到 `flink/v1.20/` 目录。复制的内容包括：
- `build.gradle`：构建配置
- `flink-runtime/` 下的 LICENSE 和 NOTICE
- `flink/src/main/java/` 下的全部主源码
- `flink/src/test/java/` 下的全部测试源码
- `flink/src/main/resources/` 下的 SPI 服务注册文件

复制后，`v1.20` 和 `v2.0` 两个目录的内容完全一致，均包含原始的 Flink 1.20 适配代码。

## 修改详情

### `flink/v1.20/` (新增, +69782 lines)

**修改目的**：恢复 Flink 1.20 支持目录，使 v1.20 和 v2.0 可以独立维护。

**工作逻辑**：
从 `flink/v2.0/` 完整复制 378 个文件到 `flink/v1.20/`，内容涵盖 Flink 集成的全部模块：
- **核心 Catalog 与 Table 集成**：`FlinkCatalog`、`FlinkCatalogFactory`、`FlinkDynamicTableFactory`、`FlinkSchemaUtil` 等
- **Sink（写入）模块**：`FlinkSink`、`IcebergSink`、`IcebergStreamWriter`、`IcebergFilesCommitter`、shuffle 相关的数据统计与分区器等
- **Source（读取）模块**：`IcebergSource`、`FlinkSource`、`FlinkInputFormat`、enumerator、assigner、reader 等
- **Data 模块**：Parquet/Avro/ORC 的读写器
- **Maintenance 模块**：快照过期、文件删除等维护任务
- **测试代码**：完整的单元测试和集成测试

所有文件内容与原 v1.20 完全相同，不涉及任何代码修改。

## 总结

本提交是 Flink 2.0 支持系列的第二步，通过将 `flink/v2.0/` 目录内容完整复制回 `flink/v1.20/`，恢复了对 Flink 1.20 的支持。此后 v1.20 和 v2.0 两个目录内容相同，后续提交将仅修改 v2.0 以适配 Flink 2.0。属于纯文件复制操作，新增 378 个文件共约 69782 行。
