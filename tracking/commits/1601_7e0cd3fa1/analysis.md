# 提交 1601 7e0cd3fa1 分析

## 提交信息
- 哈希：7e0cd3fa1e51d3c80f6c8cff23a03dca86f942fa
- 日期：2025-01-19 18:44:15 +0100（提交时间 2025-01-19 10:44:15 -0700）
- 作者：Fokko Driesprong <fokko@apache.org>
- 消息：Revert "API: add hashcode cache in StructType (#11764)" (#12007)

## 总体目的

本提交回退（revert）提交 1598（`bed7c3317`，"API: add hashcode cache in StructType"），将 `Types.StructType` 的 `hashCode()` 方法恢复为每次调用都重新计算的方式。

回退原因是提交 1598 引入的 `transient int hashCode` 缓存字段破坏了 Flink 的序列化测试。具体来说，Flink 的 `TypeInformationTestBase.testSerialize` 方法验证对象序列化和反序列化后的 `hashCode` 是否一致。由于缓存字段标记为 `transient`，在 Java 序列化时被跳过，反序列化后该字段被重置为 `int` 默认值 `0`（而非哨兵值 `NO_HASHCODE = Integer.MIN_VALUE`）。这导致反序列化后的 `hashCode()` 方法误认为缓存已初始化（`0 != Integer.MIN_VALUE`），直接返回 `0` 而非正确的哈希值，使 Flink 测试失败。

根据 PR #12007 的讨论，原作者（Liurnly）有意使用 `transient` 是因为担心将 `hashCode` 序列化后在不同节点上复用缓存值可能不安全——同一个对象在不同 JVM 实例上的 `hashCode` 可能不同（取决于 `Arrays.hashCode` 的实现和 JVM 版本）。但 Flink 测试期望 `hashCode` 在序列化/反序列化后保持一致，两者存在矛盾。维护者同意先回退，待找到更好的解决方案后再重新引入。

## 如何达成设计目的

通过 `git revert` 完全撤销提交 1598 的所有修改，将 `Types.java` 恢复到缓存引入前的状态。

### 修改详情

#### `api/src/main/java/org/apache/iceberg/types/Types.java`

**修改目的**：移除 hashCode 缓存机制，恢复每次调用重新计算的行为。

**工作逻辑**：

1. 删除常量和字段：
   ```java
   private static final int NO_HASHCODE = Integer.MIN_VALUE;
   private transient int hashCode = NO_HASHCODE;
   ```

2. 恢复 `hashCode()` 方法为原始实现：
   ```java
   @Override
   public int hashCode() {
     return Objects.hash(NestedField.class, Arrays.hashCode(fields));
   }
   ```

这与提交 1598 的修改完全相反——删除了缓存字段和哨兵常量，`hashCode()` 方法恢复为每次调用执行 `Objects.hash(NestedField.class, Arrays.hashCode(fields))` 计算。

## 小结

- 成效：修复了提交 1598 引入的 Flink 序列化测试失败问题，恢复 `StructType.hashCode()` 的正确行为。
- 影响范围：仅 `api/src/main/java/org/apache/iceberg/types/Types.java`，1 个文件，删除 7 行，恢复 1 行。
- 与提交 1598 的关联：本提交是提交 1598（`bed7c33174ca97809fc4a9657d39b1d09ae38b72`，"API: add hashcode cache in StructType"）的完全回退。提交 1598 添加了 `transient int hashCode` 缓存字段，但由于 `transient` 字段在反序列化后重置为 `0`（而非哨兵值 `Integer.MIN_VALUE`），导致反序列化后的 `hashCode()` 返回错误的 `0` 值。Flink 的 `TypeInformationTestBase.testSerialize` 检测到这个不一致并失败。回退后，`hashCode()` 恢复为每次调用重新计算，虽然性能略差但行为正确。
- 后续展望：PR 讨论中提到，未来重新引入缓存需要找到一种方案，既能避免重复计算，又能兼容序列化场景。可能的方案包括：使用 `Integer`（装箱类型，反序列化后为 `null`）代替 `int`，或在 `readObject` 方法中重新初始化缓存字段，或使用 Flink 的 `TypeSerializer` 而非 Java 原生序列化。
- 回迁到 1.4.x 的注意事项：由于提交 1598 已被回退，1.4.x 分支不应包含缓存改动。本回退提交确保 main 分支恢复到正确状态，1.4.x 分支无需额外操作。
