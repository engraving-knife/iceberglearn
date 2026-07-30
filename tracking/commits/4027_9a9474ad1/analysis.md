# 提交 4027：Flink: Backport: handle simultaneous schema evolution and data conversion (#17024) (#17191)

## 提交信息

- **序号**：4027 / 4088
- **哈希**：9a9474ad1372f32543c1ba55b49f2758b2e6e13d
- **短哈希**：9a9474ad1
- **日期**：2026-07-13 17:03:37 -0700
- **作者**：Han You
- **提交说明**：Flink: Backport: handle simultaneous schema evolution and data conversion (#17024) (#17191)
- **PR/Issue**：#17191（backport of #17024）

## 总体目的

本提交是将提交 4021（#17024，"Flink: handle simultaneous schema evolution and data conversion"）backport 到 Flink 1.20 和 2.0 两个版本。原提交只在 Flink 2.1 中修复了 `EvolveSchemaVisitor` 在类型差异可由数据转换处理时仍错误触发 schema 更新的 bug，本次将相同改动应用到 `flink/v1.20` 和 `flink/v2.0`。

## 如何达成设计目的

将 4021 在 `flink/v2.1` 下的全部 5 个文件改动原样复制到 `flink/v1.20` 和 `flink/v2.0` 对应路径，共 10 个文件。代码逻辑与 4021 完全一致，仅包路径前缀不同。

## 修改详情

### `flink/v1.20/flink/...` (5 files)

**修改目的**：backport 到 Flink 1.20。

**工作逻辑**：与 4021 相同的改动：
- `CompareSchemasVisitor.java`：抽取 `isDataConversionPossible` 静态方法，简化 `primitive`。
- `EvolveSchemaVisitor.java`：`needsTypeUpdate` 增加 `!handledByDataConversion` 条件。
- `TestEvolveSchemaVisitor.java`、`TestRowDataConverter.java`、`TestTableUpdater.java`：测试覆盖。

### `flink/v2.0/flink/...` (5 files)

**修改目的**：backport 到 Flink 2.0。

**工作逻辑**：与 Flink 1.20 完全相同的改动。

## 总结

本提交是 4021 的跨版本 backport，将"同时 schema 演进和数据转换"的修复推广到 Flink 1.20 和 2.0，确保三个支持的 Flink 版本功能一致。代码逻辑无差异，仅是并行维护多个 Flink 版本分支的常规操作。
