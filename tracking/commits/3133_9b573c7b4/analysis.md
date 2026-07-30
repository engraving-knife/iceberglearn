# 提交 3133：API: Use Transform#isIdentity in PartitionSpec#identitySourceIds (#15066)

## 提交信息

- **序号**：3133 / 4088
- **哈希**：9b573c7b46950a41d614c4240752f255282d8c1f
- **短哈希**：9b573c7b4
- **日期**：2026-01-19
- **作者**：Raunaq Morarka
- **提交说明**：API: Use Transform#isIdentity in PartitionSpec#identitySourceIds (#15066)
- **PR/Issue**：#15066

## 总体目的

本提交将 `PartitionSpec.identitySourceIds()` 中判断分区字段是否为 identity 变换的方式，由字符串比较改为调用 `Transform#isIdentity()` 布尔方法，消除不必要的字符串转换与比较，提升代码的效率与健壮性。

原实现为 `if ("identity".equals(field.transform().toString()))`：每次判断都要调用 `transform().toString()`（产生字符串）再与字面量 `"identity"` 做字符串相等比较。这不仅带来了字符串分配与比较开销，更关键的是它依赖 `toString()` 的输出恰好等于 `"identity"` 这一隐式约定——这是一种"字符串魔术比较"（string magic comparison），脆弱且不符合面向对象的设计直觉。

Iceberg 的 `Transform` 接口早已提供了语义化的 `default boolean isIdentity()`（默认返回 `false`），而 `Identity` 变换类重写该方法返回 `true`。使用这一专用方法既避免了字符串分配与比较，又使判断基于类型语义而非字符串表示，更加准确、可读且易于维护。本提交正是把 `identitySourceIds` 改为使用该 API。

## 如何达成设计目的

改动极其聚焦：仅修改 `PartitionSpec.java` 中 `identitySourceIds()` 方法内的一行判断条件，把 `"identity".equals(field.transform().toString())` 替换为 `field.transform().isIdentity()`，无其它文件改动。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java` (+1/-1 lines)

**修改目的**：用 `Transform#isIdentity()` 替代字符串比较判断 identity 变换。

**工作逻辑**：
`identitySourceIds()` 遍历分区字段，收集所有 identity 变换的源列 ID。判断条件由 `"identity".equals(field.transform().toString())` 改为 `field.transform().isIdentity()`。`Transform` 接口的 `isIdentity()` 默认返回 `false`，`Identity` 变换重写为返回 `true`，因此语义等价但避免了 `toString()` 的字符串构造与字面量比较。该方法在 Iceberg 内部多处被调用（如确定哪些列是分区列以优化扫描/过滤），改用布尔方法后在热点路径上更高效。

## 总结

本提交是一处小而精的代码质量改进，将 `PartitionSpec.identitySourceIds()` 中基于 `toString()` 字符串比较的 identity 判断替换为语义化的 `Transform#isIdentity()` 调用，消除了字符串分配与比较开销并降低了对外部 `toString()` 表示的隐式依赖，使判断更高效、更健壮、更符合面向对象设计。
