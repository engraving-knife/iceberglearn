# 提交 3549：Data: Clean engineProjection in BaseFormatModelTests (#15995)

## 提交信息

- **序号**：3549 / 4088
- **哈希**：dde712ec9ed6c9d28183ee4615d50f97b246af5d
- **短哈希**：dde712ec9
- **日期**：2026-04-16 13:28:57 +0200
- **作者**：GuoYu
- **提交说明**：Data: Clean engineProjection in BaseFormatModelTests (#15995)
- **PR/Issue**：#15995

## 总体目的

`BaseFormatModelTests` 是 Iceberg data 模块中格式模型测试的基类，用于跨格式（Parquet、ORC、Avro）验证读写行为。此前测试中大量使用了 `engineSchema(engineSchema(schema))` 和 `engineProjection(engineSchema(...))` 调用——这些是 `FormatModelRegistry` 的 builder 方法，用于向引擎提供「引擎视角的 schema 投影」。

随着 `FormatModelRegistry` API 的演进（`engineSchema`/`engineProjection` 方法被移除或不再需要），测试中这些调用变成了编译错误或冗余代码。本提交清理这些过时的调用，使测试与当前 API 保持一致。

同时清理了一处死代码：一个 `new Schema(...)` 表达式语句没有赋值给任何变量，是无意义的悬空构造，一并删除。

## 如何达成设计目的

遍历 `BaseFormatModelTests` 中所有 `FormatModelRegistry.dataWriteBuilder/readBuilder` 的 builder 链，移除 `.engineSchema(engineSchema(schema))` 和 `.engineProjection(engineSchema(...))` 调用，保留其余有意义的 builder 配置（如 `.schema`、`.spec`、`.project`、`.filter`、`.split`、`.reuseContainers` 等）。同时删除悬空的 `new Schema(...)` 语句。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+1/-20 lines)

**修改目的**：移除过时的 `engineSchema`/`engineProjection` 调用和死代码。

**工作逻辑**：
1. 数据写入 builder 中移除 `.engineSchema(engineSchema(schema))`：
```java
-    DataWriter<T> writer =
-        writerBuilder
-            .schema(schema)
-            .engineSchema(engineSchema(schema))
-            .spec(PartitionSpec.unpartitioned())
-            .build();
+    DataWriter<T> writer = writerBuilder.schema(schema).spec(PartitionSpec.unpartitioned()).build();
```
2. equality delete 写入 builder 移除 `.engineSchema(engineSchema(schema))`
3. 多处读取 builder 移除 `.engineProjection(engineSchema(projectedSchema))` / `.engineProjection(engineSchema(schema))`，涉及过滤读取、split 读取、reuse 读取等场景
4. 删除悬空死代码：
```java
     Schema schema = SCHEMA;
-    new Schema(
-        Types.NestedField.required(1, "id", Types.IntegerType.get()),
-        Types.NestedField.required(2, "data", Types.StringType.get()));
```
这个 `new Schema(...)` 构造后未赋值给任何变量，是无效语句。

总计移除约 20 行，仅保留必要的 builder 链调用。

## 总结

本提交清理了 `BaseFormatModelTests` 中过时的 `engineSchema`/`engineProjection` builder 调用（这些方法在 `FormatModelRegistry` API 演进中已移除或不再需要），并删除一处悬空的 `new Schema(...)` 死代码。属于测试代码与 API 同步的清理维护，使测试编译通过且更简洁。
