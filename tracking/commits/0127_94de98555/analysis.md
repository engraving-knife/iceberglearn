# 提交 0127：Parquet: Remove duplicate test code (#8098)

## 提交信息

- **序号**：0127 / 4088
- **哈希**：94de98555d25c8f49c7bda70278598ecb04dc717
- **短哈希**：94de98555
- **日期**：2023-11-02 18:52:17 +0530
- **作者**：Ajantha Bhat
- **提交说明**：Parquet: Remove duplicate test code (#8098)
- **PR/Issue**：#8098

## 总体目的

这个提交清理了 Parquet 模块中一份与 core 模块完全重复的测试基类代码，消除维护负担与潜在的行为分叉风险。

Iceberg 的读投影（read projection）测试体系由一个抽象基类 `TestReadProjection`（位于 `org.apache.iceberg.avro` 包）和若干子类实现组成。该抽象基类定义了大量通用的投影测试用例（如全投影、基本投影、嵌套 struct 投影、map/list 投影等），并通过抽象方法 `writeAndRead` 由各格式子类提供具体的读写实现。这个抽象基类的"权威"副本位于 [core/src/test/java/org/apache/iceberg/avro/TestReadProjection.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/test/java/org/apache/iceberg/avro/TestReadProjection.java)。

然而在 parquet 模块的测试目录下，存在一份重复的同名同包副本：[parquet/src/test/java/org/apache/iceberg/avro/TestReadProjection.java](file:///Users/fengxiaohang/trae/iceberglearn/parquet/src/test/java/org/apache/iceberg/avro/TestReadProjection.java)（549 行）。这份副本是早期为支持 parquet 模块独立测试而复制过去的，但 parquet 模块本身就通过测试类路径依赖 core 模块的测试类，因此这份本地副本完全冗余。更糟的是，core 模块的权威副本此后又新增了若干测试用例（如空 struct 投影相关的 5 个测试：`testEmptyStructProjection`、`testEmptyStructRequiredProjection`、`testRequiredEmptyStructInRequiredStruct`、`testEmptyNestedStructProjection`、`testEmptyNestedStructRequiredProjection`），而 parquet 模块的陈旧副本并未同步这些新增用例。这导致两个问题：一是重复代码带来双倍维护成本；二是 parquet 子类实际继承的是本地陈旧副本，遗漏了基类新增的测试覆盖。

本提交删除 parquet 模块中的重复副本，使 `TestParquetReadProjection` 直接继承 core 模块的权威 `TestReadProjection`。由于 Parquet 格式不支持空 struct 的读取（empty struct read is not supported for Parquet），而新继承下来的基类恰好包含 5 个空 struct 相关测试，因此在 parquet 子类中通过 `@Disabled` 注解显式禁用这 5 个用例，并附带清晰的禁用原因说明。这样既消除了重复代码，又复用了基类全部通用测试覆盖，同时正确处理了 Parquet 的格式限制。

## 如何达成设计目的

整体设计思路是"删除重复、复用基类、显式禁用不适用项"。具体做法分两步：第一步，删除 parquet 模块下 549 行的重复 `TestReadProjection.java`，使 parquet 测试类路径解析到 core 模块的权威基类；第二步，在 `TestParquetReadProjection` 子类中，对从基类继承下来但 Parquet 不支持的 5 个空 struct 测试方法进行 `@Override` 并标注 `@Disabled`，方法体置空。改动后，parquet 模块自动获得基类所有其他通用投影测试的覆盖，无需维护任何重复代码。

## 修改详情

### `parquet/src/test/java/org/apache/iceberg/avro/TestParquetReadProjection.java`

**修改目的**：在删除重复基类后，显式禁用 Parquet 不支持的空 struct 投影测试，并补充必要的 import。

**工作逻辑**：

1. 新增 import `org.junit.jupiter.api.Disabled` 和 `org.junit.jupiter.api.Test`，用于标注被禁用的测试方法。

2. 新增 5 个被 `@Override` + `@Test` + `@Disabled("Empty struct read is not supported for Parquet")` 标注的空方法，分别对应基类中 5 个空 struct 相关测试：
   - `testEmptyStructProjection()`
   - `testEmptyStructRequiredProjection()`
   - `testRequiredEmptyStructInRequiredStruct()`
   - `testEmptyNestedStructProjection()`
   - `testEmptyNestedStructRequiredProjection()`

   这些方法体为空（`{}`），因为 `@Disabled` 会使 JUnit 跳过执行，无需任何实现。`@Disabled` 注解的描述信息明确指出"Empty struct read is not supported for Parquet"，便于开发者理解跳过原因。通过 `@Override` 确保方法签名与基类一致，避免因签名漂移而 silently 失去覆盖。

### `parquet/src/test/java/org/apache/iceberg/avro/TestReadProjection.java`

**修改目的**：删除与 core 模块完全重复的陈旧测试基类副本（549 行）。

**工作逻辑**：该文件被整体删除。删除前，它是一份与 core 模块权威基类内容高度重复的抽象类，但缺少后者后来新增的空 struct 测试用例。删除后，parquet 模块的 `TestParquetReadProjection` 通过测试类路径继承 core 模块的 `TestReadProjection`，自动获得基类全部（含空 struct 在内的）测试定义，再由子类的 `@Disabled` 标注排除 Parquet 不支持的项。这一改动将 parquet 模块的投影测试维护成本降为零，同时保证测试覆盖与 core 基类同步演进。

## 小结

本提交通过删除 parquet 模块中与 core 模块重复的 549 行测试基类副本，并显式禁用 Parquet 不支持的空 struct 测试，消除了重复代码维护负担，使 parquet 投影测试自动复用并跟随 core 基类演进。
