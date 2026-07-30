# 提交 3887：Spark: Cap RandomData collection size to speed up nested tests (#16696)

## 提交信息

- **序号**：3887 / 4088
- **哈希**：2f244c0bc597f64e283452b3378894e35370ffbd
- **短哈希**：2f244c0bc
- **日期**：2026-06-15 09:38:42 -0700
- **作者**：Sebastian Baunsgaard
- **提交说明**：Spark: Cap RandomData collection size to speed up nested tests (#16696)
- **PR/Issue**：#16696

## 总体目的

解决 Spark 测试中深层嵌套 schema 的性能问题。`RandomData` 测试工具类在生成随机测试数据时，使用 `random.nextInt(20)` 决定每个 list/map 的元素数量，这个上限会在每一层嵌套上重复应用。对于深层嵌套的 schema，元素数量会以指数级增长——例如 4 层嵌套的 list 可能产生 20^4 = 160,000 个叶子节点，导致测试被数据生成量主导而非真正测试读写代码路径。

通过将上限从 20 降低到 10，并将硬编码值替换为命名常量，在不改变测试覆盖的 schema、类型和嵌套结构的前提下，显著加速嵌套往返测试。

## 如何达成设计目的

在 Spark 3.5、4.0、4.1 三个版本的 `RandomData.java` 测试副本中：
1. 引入命名常量 `COLLECTION_SIZE_BOUND = 10` 替代硬编码的 `20`
2. 添加注释说明该上限是排他性上界（零长度集合合法）、每层嵌套都会应用、需保持较小以避免组合爆炸
3. 将所有 `random.nextInt(20)` 调用替换为 `random.nextInt(COLLECTION_SIZE_BOUND)`

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/RandomData.java` (+10/-4 lines)
### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/RandomData.java` (+10/-4 lines)
### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/RandomData.java` (+10/-4 lines)

**修改目的**：降低随机数据生成的集合大小上限以加速嵌套测试。

**工作逻辑**：
新增常量定义：
```java
// Exclusive upper bound on the number of elements generated for each list/map.
// Zero-length collections are in range, so this is a cap and not a minimum.
// Applied per nesting level, so deeply-nested schemas multiply quickly; keep
// this small enough to avoid combinatorial blow-up in heavily-nested tests.
private static final int COLLECTION_SIZE_BOUND = 10;
```

每个文件中有 4 处替换（两个内部类各有一对 list/map 生成方法）：
```java
-int numElements = random.nextInt(20);
+int numElements = random.nextInt(COLLECTION_SIZE_BOUND);
```
```java
-int numEntries = random.nextInt(20);
+int numEntries = random.nextInt(COLLECTION_SIZE_BOUND);
```

将上限从 20 降到 10，对于 4 层嵌套，最大叶子节点数从 160,000 降到 10,000（降低约 16 倍），显著减少数据生成开销，同时仍保留足够的随机性来测试边界情况。

## 总结

通过将 `RandomData` 测试工具中集合大小上限从 20 降低到 10 并提取为命名常量，在不改变测试覆盖范围的前提下显著加速了深层嵌套 schema 的往返测试。这是一个纯测试性能优化提交，体现了对测试效率的关注。三个 Spark 版本的测试副本同步修改保持一致性。
