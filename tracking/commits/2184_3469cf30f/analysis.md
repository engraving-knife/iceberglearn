# 提交 2184：API: Make PartitionSpec.Builder::identity public (#12975)

## 提交信息

- **序号**：2184 / 4088
- **哈希**：3469cf30f2d839763813596e3f0c66ed16189c9a
- **短哈希**：3469cf30f
- **日期**：2025-05-29 11:24:26 -0700
- **作者**：Ben Hannel
- **提交说明**：API: Make PartitionSpec.Builder::identity public (#12975)
- **PR/Issue**：#12975

## 总体目的

此提交将 `PartitionSpec.Builder` 类中的 `identity(String sourceName, String targetName)` 方法的可见性从包级私有（package-private）改为 public。该方法允许用户在构建分区规范时，使用指定的源列名和目标名创建一个 identity 分区字段。原来看不到该方法是包级私有的，外部模块无法调用此重载版本（只能使用其他 public 的 identity 重载）。此提交将其改为 public，使外部调用方能使用源列名+目标名的组合来创建 identity 分区字段。

## 如何达成设计目的

- 将 `PartitionSpec.Builder.identity(String sourceName, String targetName)` 方法的访问修饰符从包级私有改为 `public`

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java` (修改, +1/-1 lines)

**修改目的**：将 identity 方法改为 public。

**工作逻辑**：将 `Builder identity(String sourceName, String targetName)` 方法前添加 `public` 关键字，使其从包级私有变为公开方法。该方法内部调用 `identity(findSourceColumn(sourceName), targetName)`，通过源列名查找列并创建 identity 分区字段。

## 总结

此提交是一个简单的 API 可见性改进，将 `PartitionSpec.Builder.identity(String sourceName, String targetName)` 方法从包级私有改为 public，使外部模块能够使用源列名和目标名组合来创建 identity 分区字段。这是一个 API 易用性改进。
