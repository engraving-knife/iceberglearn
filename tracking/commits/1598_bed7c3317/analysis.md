# 提交 1598：API: add hashcode cache in StructType (#11764)

## 提交信息

- **序号**：1598 / 4088
- **哈希**：bed7c33174ca97809fc4a9657d39b1d09ae38b72
- **短哈希**：bed7c3317
- **日期**：2025-01-18（Sat Jan 18 05:58:15 2025 +0800）
- **作者**：Liurnly <masterwangzx@gmail.com>
- **提交说明**：API: add hashcode cache in StructType (#11764)
- **PR/Issue**：#11764

## 总体目的

`Types.StructType` 是 Iceberg 中最频繁被使用的类型对象之一：每条 schema、每个 struct 字段、每个 partition spec、每个 manifest 的分区类型都包含 `StructType` 实例。它在很多热路径上被作为 `HashMap` / `HashSet` 的 key 使用，例如 reader / writer 构建时的类型匹配缓存、字段查找表、表达式求值上下文等。原 `hashCode()` 实现是：

```java
return Objects.hash(NestedField.class, Arrays.hashCode(fields));
```

每次调用都会执行 `Arrays.hashCode(fields)` 遍历整个字段数组并对每个 `NestedField` 求哈希，再交给 `Objects.hash` 做混合。对于含数十甚至上百字段的表（在生产中并不少见），且 `StructType` 实例往往在生命周期内被反复作为 map key 查询，这一开销会累积成可观的 CPU 成本。由于 `StructType` 是不可变类型（fields 数组在构造后不再变更），其 hashCode 在整个生命周期内是稳定的，完全可以缓存。

本提交为 `StructType` 增加 hashCode 缓存：首次调用 `hashCode()` 时计算并写入实例字段 `hashCode`，后续调用直接返回缓存值。这是与 `StructType` 已有的"按名 / 按 ID 索引懒加载缓存"一致的优化策略——`StructType` 内部已经用 `transient` 字段缓存了 `fieldsByName` / `fieldsById` / `fieldsByLowerCaseName` / `fieldList` 等派生结构，本次只是把同样的懒加载思想扩展到 hashCode。

**注意：此提交随后在 #12007（提交 1601）被 Revert。** 原因后续分析中说明。本提交文档按其设计意图记录，并标注 revert 关联。

## 如何达成设计目的

实现策略：

1. **哨兵值区分"未计算"与"已计算"**：用 `private static final int NO_HASHCODE = Integer.MIN_VALUE;` 作为"尚未计算"的标记。选 `Integer.MIN_VALUE` 是因为正常的 `Objects.hash` 结果几乎不会恰好等于该值（虽然理论上有极小概率碰撞，但概率可忽略），避免引入额外 `boolean` 字段。
2. **实例字段 `transient int hashCode = NO_HASHCODE`**：`transient` 保证序列化时不写出该字段——`StructType` 实现了 `Serializable`（间接通过 `Type` 体系），跨 JVM 传输或落盘时不应携带本进程的缓存；反序列化后字段恢复为默认值 `0`，但因为 Java 反序列化不会跑字段初始化器，需要显式处理。**此处是该提交后来被 revert 的关键问题点之一**——普通 Java 反序列化会绕过字段初始化器，导致 `hashCode` 字段默认为 `0` 而非 `NO_HASHCODE`；如果 `0` 恰好是某次真实哈希计算的结果，缓存会"成功"返回 `0`；但如果真实哈希不是 `0`，第一次 `hashCode()` 调用看到 `0 != NO_HASHCODE` 会被误判为"已缓存"，返回错误的 `0`。这造成了序列化场景下的潜在正确性问题。
3. **`hashCode()` 改写**：

```java
@Override
public int hashCode() {
  if (hashCode == NO_HASHCODE) {
    hashCode = Objects.hash(NestedField.class, Arrays.hashCode(fields));
  }
  return hashCode;
}
```

   命中缓存直接返回，未命中则计算并写入。

### 修改详情

#### `api/src/main/java/org/apache/iceberg/types/Types.java`

**修改目的**：为 `StructType` 增加 hashCode 缓存以降低热路径 CPU 开销。

**工作逻辑**：

- 在 `StructType` 类内新增：
  - `private static final int NO_HASHCODE = Integer.MIN_VALUE;` 哨兵常量
  - `private transient int hashCode = NO_HASHCODE;` 实例缓存字段
- 重写 `hashCode()`：先检查 `hashCode == NO_HASHCODE`，未计算则调用原 `Objects.hash(...)` 并赋值，最终返回缓存值。

字段仍保持 `transient`，与 `StructType` 既有的 `fieldsByName` / `fieldsById` 等懒加载缓存字段一致，意图是序列化时丢弃缓存、反序列化后按需重建。

## 小结

- **成效（设计意图）**：在 `StructType` 不可变的前提下，把 `hashCode()` 从 O(n) 降为 O(1)（首次后），预期在大 schema / 频繁哈希场景下减少 CPU 开销。该优化方向本身合理，与 `StructType` 既有的懒加载缓存策略一致。
- **影响范围**：仅 `api/src/main/java/org/apache/iceberg/types/Types.java` 中 `StructType` 类约 +6 行变更，是 Iceberg 核心 API 模块。
- **回迁到 1.4.x 的注意事项**：**此提交随后被 #12007（提交 1601）完整 Revert，不应回迁到 1.4.x。** Revert 的原因（推测）涉及 Java 反序列化绕过字段初始化器导致 `hashCode` 字段在反序列化后默认为 `0` 而非 `NO_HASHCODE`，从而可能返回错误的缓存值（正确性风险）。如需在 1.4.x 上做类似优化，应采用不依赖字段初始化器的方案（如显式 `readObject` / `readResolve`，或使用 `Boolean` 标志位）。具体 revert 原因详见 1601 的分析文档。
