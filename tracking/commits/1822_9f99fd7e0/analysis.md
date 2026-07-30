# 提交 1822：Core: Wrap variant in PrimitiveLikeHoder so serialization can result same instance (#12317)

## 提交信息

- **序号**：1822 / 4088
- **哈希**：9f99fd7e07c075082899fe2d2635070d8c2da7df
- **短哈希**：9f99fd7e0
- **日期**：2025-03-04 22:47:49 -0800
- **作者**：Aihua Xu
- **提交说明**：Core: Wrap variant in PrimitiveLikeHoder so serialization can result same instance (#12317)
- **PR/Issue**：#12317

## 总体目的

该提交修复了 Variant 类型在 Java 序列化后无法返回相同单例实例的问题。

Iceberg 的类型系统使用单例模式管理不可变类型（如 `Types.StringType.get()`、`Types.IntegerType.get()` 等），通过 `writeReplace()` 机制在 Java 序列化时将类型对象替换为 `PrimitiveHolder`，反序列化时再通过 `Types.fromName()` 还原为对应的单例实例。这保证了序列化往返后类型对象仍是同一个单例，`==` 比较成立。

然而，新增的 `Types.VariantType`（同样是单例）没有实现 `writeReplace()`，导致 Variant 类型经过 Java 序列化（如 Spark RPC 传输、分布式缓存等场景）后得到的是新创建的实例，而非原始单例。这破坏了类型相等性假设，可能引发难以排查的 bug。

本提交通过将 `PrimitiveHolder` 重命名为 `PrimitiveLikeHolder`（因为它现在也包装非原始的 Variant 类型），并为 `VariantType` 添加 `writeReplace()` 方法，使其序列化后能还原为单例。

## 如何达成设计目的

整体思路是复用已有的 `PrimitiveHolder` 序列化替换机制。由于 Variant 类型在概念上类似原始类型（无内部状态的单例），但名称上 "Primitive" 不够准确，因此将 `PrimitiveHolder` 重命名为 `PrimitiveLikeHolder`。然后在 `Types.VariantType` 中添加 `writeReplace()` 方法返回 `PrimitiveLikeHolder`，反序列化时 `PrimitiveLikeHolder.readResolve()` 会调用 `Types.fromName("variant")` 还原为 `VariantType.get()` 单例。

## 修改详情

### api/src/main/java/org/apache/iceberg/types/PrimitiveLikeHolder.java (重命名, 原 PrimitiveHolder.java)

将 `PrimitiveHolder` 类重命名为 `PrimitiveLikeHolder`，类注释更新为"Replacement for primitive types and Variant type in Java Serialization"。构造方法和字段名同步更新。该类通过 `readResolve()` 调用 `Types.fromName(typeAsString)` 将类型名字符串还原为对应的单例类型实例。

### api/src/main/java/org/apache/iceberg/types/Type.java (修改, 1 line)

将 `PrimitiveType.writeReplace()` 中的 `new PrimitiveHolder(toString())` 改为 `new PrimitiveLikeHolder(toString())`，适配重命名。

### api/src/main/java/org/apache/iceberg/types/Types.java (修改, 多行)

- 新增 `import java.io.ObjectStreamException`。
- 在 `VariantType` 内部类中新增 `writeReplace()` 方法，返回 `new PrimitiveLikeHolder(toString())`，使 Variant 类型序列化时被替换为 PrimitiveLikeHolder，反序列化时还原为单例。

### api/src/test/java/org/apache/iceberg/types/TestSerializableTypes.java (修改, 多行)

- 将 `Types.VariantType.get()` 加入 `identityPrimitives` 数组，统一测试其序列化往返后返回同一实例。
- 移除原先单独的 `testVariant()` 测试方法（已被统一测试覆盖）。

## 小结

该提交修复了 Variant 类型的 Java 序列化单例问题，确保 Variant 类型在分布式场景（如 Spark 任务序列化）中行为正确。影响范围集中在 api 模块的类型系统，改动小而精确。回迁到 1.4.x 分支时需注意：1.4.x 分支需已包含 `Types.VariantType` 类型定义（即 Variant 类型的基础设施已存在）；重命名 `PrimitiveHolder` 为 `PrimitiveLikeHolder` 涉及类名变更，需确认 1.4.x 分支无其他引用旧类名的地方。该提交是 Variant 支持的配套修复，建议与 1819（Avro Variant）协同回迁。注意完整哈希对应的短哈希为 `9f99fd7e0`（取前9位）。
