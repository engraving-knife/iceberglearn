# 提交 2030：Spark 3.4: Add Parallelism Parameter Validation to AddFilesProcedure

## 提交信息

- **序号**：2030 / 4088
- **哈希**：321e66ffc97349f7221096e15129a7eddb5ab81d
- **短哈希**：321e66ffc
- **日期**：2025-04-23 09:59:40 +0200
- **作者**：slfan1989
- **提交说明**：Spark 3.4: Add Parallelism Parameter Validation to AddFilesProcedure (#12872)
- **PR/Issue**：#12872

## 总体目的

Iceberg 的 Spark 3.4 模块中，`AddFilesProcedure`（用于将外部数据文件导入到 Iceberg 表的过程）接受一个 `parallelism` 参数来控制导入时的并行度。然而此前该参数缺少输入校验：当用户传入 0 或负数时，过程不会提前报错，而是会在后续执行阶段才出现难以理解的异常或导致行为异常。

本提交为该参数添加前置校验，要求 `parallelism` 必须大于 0，否则抛出 `IllegalArgumentException`，从而实现"快速失败"，给用户以明确的错误提示，避免无效参数流入后续复杂逻辑造成难以诊断的问题。

## 如何达成设计目的

设计思路简洁明确：在 `AddFilesProcedure` 解析参数的位置，紧接获取 `parallelism` 值之后，使用 Iceberg 的 `Preconditions.checkArgument` 进行校验。这样在进入实际导入逻辑之前即可拦截非法输入。

同时配套新增一个测试用例 `testAddFilesWithInvalidParallelism`，验证当传入 `parallelism => -1` 时会抛出 `IllegalArgumentException` 且错误消息为 "Parallelism should be larger than 0"。

关键组件协作：
- `AddFilesProcedure`：负责参数解析与校验，调用 `importToIceberg` 执行导入。
- `TestAddFilesProcedure`：扩展测试基类，验证校验逻辑。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/AddFilesProcedure.java` (修改, +1/-0 lines)

**修改目的**：在解析 `parallelism` 参数后立即校验其合法性。

**工作逻辑**：
在原有代码 `int parallelism = input.asInt(PARALLELISM, 1);` 之后新增一行：
```java
Preconditions.checkArgument(parallelism > 0, "Parallelism should be larger than 0");
```
当 `parallelism <= 0` 时，`checkArgument` 会抛出 `IllegalArgumentException`，消息为 "Parallelism should be larger than 0"，阻止继续执行 `importToIceberg`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java` (修改, +16/-0 lines)

**修改目的**：为新增的参数校验添加测试覆盖。

**工作逻辑**：
新增测试方法 `testAddFilesWithInvalidParallelism`，先创建一个未分区的 Hive 源表和一个分区的 Iceberg 目标表，然后通过 `assertThatThrownBy` 断言调用 `system.add_files` 过程并传入 `parallelism => -1` 时会抛出 `IllegalArgumentException`，且消息为 "Parallelism should be larger than 0"。使用 AssertJ 的链式断言同时验证异常类型和消息内容。

## 总结

本提交为 Spark 3.4 的 `AddFilesProcedure` 的 `parallelism` 参数添加了大于 0 的前置校验，避免无效的并行度参数进入后续导入逻辑，并通过新测试用例保障了校验行为的正确性。改动量小（共 17 行），属于健壮性增强。
