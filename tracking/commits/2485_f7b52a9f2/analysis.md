# 提交 2485：Spark: Remove unused code (#13790)

## 提交信息

- **序号**：2485 / 4088
- **哈希**：f7b52a9f2cfd02963b0a9a8b543311851eaff1ab
- **短哈希**：f7b52a9f2c
- **日期**：2025-08-12 16:44:01 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Remove unused code (#13790)
- **PR/Issue**：#13790

## 总体目的

该提交移除了 Spark 4.0 模块测试代码中未使用的导入和字段，清理冗余代码。

在 `AvroDataTestBase` 测试基类中，存在若干未被使用的导入（`ImmutableList`、`VariantMetadata`、`VariantTestUtil`、`Variants`）和静态字段（`TEST_METADATA_BUFFER`、`TEST_METADATA`、`SCHEMA`）。这些代码可能是在开发 variant 类型支持时遗留的，最终未在测试中使用。保留未使用的代码会增加维护负担并造成代码混乱，因此将其移除。

## 如何达成设计目的

直接删除未使用的 import 语句和未使用的静态字段。删除操作不影响任何测试逻辑，因为这些字段和导入未被任何测试方法引用。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/AvroDataTestBase.java` (+0/-12 lines)

**修改目的**：移除未使用的导入和静态字段。

**工作逻辑**：

1. **移除导入**（4 个）：
   - `org.apache.iceberg.relocated.com.google.common.collect.ImmutableList`
   - `org.apache.iceberg.variants.VariantMetadata`
   - `org.apache.iceberg.variants.VariantTestUtil`
   - `org.apache.iceberg.variants.Variants`

2. **移除静态字段**（3 个）：
   - `TEST_METADATA_BUFFER`：基于 `VariantTestUtil.createMetadata` 创建的 ByteBuffer。
   - `TEST_METADATA`：基于 `Variants.metadata` 创建的 VariantMetadata。
   - `SCHEMA`：包含 id 和 variant 字段的 Schema 定义。

这些字段与 variant 类型测试相关，但在当前测试基类中未被任何方法使用，属于遗留代码。

## 总结

这是一个简单的测试代码清理提交，移除了 Spark 4.0 模块 `AvroDataTestBase` 中未使用的导入和静态字段（主要与 variant 类型相关）。该提交不影响任何测试行为，仅减少冗余代码，保持测试代码的整洁。
