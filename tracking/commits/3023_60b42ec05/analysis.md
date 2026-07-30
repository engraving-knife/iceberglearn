# 提交 3023：API: Remove redundant } from Transforms javadoc (#14866)

## 提交信息

- **序号**：3023 / 4088
- **哈希**：60b42ec0550bcb31c1a05000f34b5ca24016221a
- **短哈希**：60b42ec05
- **日期**：2025-12-17
- **作者**：Yuya Ebihara
- **提交说明**：API: Remove redundant } from Transforms javadoc (#14866)
- **PR/Issue**：#14866

## 总体目的

本提交修复了 `Transforms.java` 类级别 Javadoc 中的一个冗余 `}` 字符。在原始 Javadoc 中，文本为 `...using a {@link PartitionSpec#builderFor(Schema)} partition spec builder}.`——注意末尾 `builder` 后有一个多余的 `}`，位于句号之前。这个多余的 `}` 可能是早期编辑中 Javadoc 链接语法 `{@link ...}` 被修改时遗留的闭合括号。

虽然这个多余的 `}` 在 Javadoc 渲染后可能不会被肉眼直接察觉（取决于 Javadoc 工具如何处理），但它是一个语法上的瑕疵，在源码阅读时可能造成困惑——读者可能误以为此处有一个未配对的 `{@link` 或 `{@code` 标签。此外，某些 Javadoc 检查工具（如 checkstyle 的 Javadoc 规则）可能会对此类不规范的括号使用发出警告。本提交将其修正为 `...partition spec builder.`，保持文本清晰。

## 如何达成设计目的

改动仅一行：将 `spec builder}.` 中的 `}` 移除，变为 `spec builder.`。Javadoc 文本语义不变，仅消除冗余字符。

## 修改详情

### `api/src/main/java/org/apache/iceberg/transforms/Transforms.java` (+1/-1 lines)

**修改目的**：移除 Transforms 类 Javadoc 中多余的 `}` 字符。

**工作逻辑**：
该 Javadoc 位于 `Transforms` 类的类注释中，描述如下：
```java
/**
 * Factory methods for transforms.
 *
 * <p>Most users should create transforms using a {@link PartitionSpec#builderFor(Schema)} partition
 * spec builder.
 *
 * @see PartitionSpec#builderFor(Schema) The partition spec builder.
 */
```
修改前为 `partition spec builder}.`（多了一个 `}`），修改后为 `partition spec builder.`（正常）。前一句中的 `{@link PartitionSpec#builderFor(Schema)}` 已经正确闭合，末尾的 `}` 是多余的。同时，`@see` 标签已经提供了到 `PartitionSpec#builderFor(Schema)` 的链接引用，因此文本中的 `}` 确实是冗余的。

## 总结

本提交是一个极小的 Javadoc 文本修正，移除了 `Transforms` 类注释中多余的 `}` 字符。虽然改动仅涉及一个字符，但保持了 API 文档源码的整洁性，避免了开发者在阅读源码时的潜在困惑。
