# 提交 2400：Flink: backport PR #11485 from 1.20 to 1.19 for maintenance actions with DVs

## 提交信息

- **序号**：2400 / 4088
- **哈希**：98b7df377fcf84f8b82dde5ee687d63435ee23a8
- **短哈希**：98b7df377
- **日期**：2025-07-24 09:28:46 +0200
- **作者**：Steven Wu
- **提交说明**：Flink: backport PR #11485 from 1.20 to 1.19 for maintenance actions with DVs
- **PR/Issue**：backport #11485

## 总体目的

此提交将 PR #11485 从 Flink 1.20 backport 到 Flink 1.19，核心目标是**为 Flink 1.19 的维护操作（maintenance actions）测试添加 DV（Deletion Vector）支持**。

Iceberg V3 表引入了 Deletion Vectors（DV）作为位置删除（position delete）的替代方案。原先 Flink 1.19 的 `TestRewriteDataFilesAction` 测试仅针对 V2 表（使用 position deletes），此 backport 扩展了测试参数化配置，使其同时覆盖 V2 和 V3 两种格式版本，从而验证维护操作在 DV 场景下的正确性。

## 如何达成设计目的

关键设计点：

1. **新增 formatVersion 参数**：在测试参数化配置中新增第四个参数 `formatVersion`，取值为 2 和 3，使每个测试用例在 V2 和 V3 两种格式下运行。
2. **表创建时指定格式版本**：在创建测试表时通过 `TableProperties.FORMAT_VERSION` 属性传入 `formatVersion` 参数，替代原先硬编码的 `format-version='2'`。
3. **参数化命名更新**：测试参数命名从 `catalogName={0}, baseNamespace={1}, format={2}` 更新为包含 `formatVersion={3}`。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/actions/TestRewriteDataFilesAction.java` (+17/-10 lines)

**修改目的**：扩展测试参数化以覆盖 V2 和 V3 格式版本。

**工作逻辑**：
- 新增 `@Parameter(index = 3) private int formatVersion` 字段。
- `parameters()` 方法中新增内层循环 `for (int version : Arrays.asList(2, 3))`，为每个 catalog 和 format 组合生成 V2 和 V3 两个测试用例。
- 参数命名更新为 `catalogName={0}, baseNamespace={1}, format={2}, formatVersion={3}`。
- 三个建表 SQL 语句（unpartitioned、partitioned、with PK）中，将硬编码的 `format-version` 改为通过 `TableProperties.FORMAT_VERSION` 和 `formatVersion` 变量动态指定。其中 `TABLE_NAME_WITH_PK` 原先硬编码 `'format-version'='2'`，现在统一使用参数化的 `formatVersion`。

## 总结

此提交为 Flink 1.19 的数据文件重写测试添加了 V3 格式版本（DV）的覆盖，通过参数化配置使测试同时运行在 V2 和 V3 表上。修改仅涉及测试文件，不影响生产代码逻辑。
