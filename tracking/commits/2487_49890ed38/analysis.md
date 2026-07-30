# 提交 2487：Parquet: Fix incorrect JavaDoc parameter descriptions in TripleWriter (#13784)

## 提交信息

- **序号**：2487 / 4088
- **哈希**：49890ed38d59a28b85e750d81ecf96403b2a6ca9
- **短哈希**：49890ed38
- **日期**：2025-08-12 10:25:51 -0700
- **作者**：JiHo Kim
- **提交说明**：Parquet: Fix incorrect JavaDoc parameter descriptions in TripleWriter (#13784)
- **PR/Issue**：#13784

## 总体目的

该提交修正了 `TripleWriter` 接口中类型特定的 write 方法 JavaDoc 参数描述的复制粘贴错误，将所有方法中错误描述的"the boolean value"改为对应类型的正确描述。

`TripleWriter` 接口定义了多种类型特定的写入方法（`writeInteger`、`writeLong`、`writeFloat`、`writeDouble`、`writeBinary`），每个方法都有 JavaDoc 文档。由于复制粘贴，所有这些方法的 `@param value` 描述都被错误地写成了"the boolean value"，而实际应该是各自类型的描述（如"the integer value"、"the long value"等）。这是典型的复制粘贴错误，虽然不影响运行时行为，但会误导开发者阅读文档。

## 如何达成设计目的

逐一修正 5 个方法的 JavaDoc `@param value` 描述：
- `writeInteger`：`the boolean value` → `the integer value`
- `writeLong`：`the boolean value` → `the long value`
- `writeFloat`：`the boolean value` → `the float value`
- `writeDouble`：`the boolean value` → `the double value`
- `writeBinary`：`the boolean value` → `the binary value`

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/TripleWriter.java` (+5/-5 lines)

**修改目的**：修正 5 个 write 方法的 JavaDoc 参数描述。

**工作逻辑**：

每个方法的 JavaDoc 修改如下（以 `writeInteger` 为例）：

修改前：
```java
/**
 * Write a triple.
 *
 * @param rl repetition level
 * @param value the boolean value
 */
default void writeInteger(int rl, int value) {
```

修改后：
```java
/**
 * Write a triple.
 *
 * @param rl repetition level
 * @param value the integer value
 */
default void writeInteger(int rl, int value) {
```

对 `writeLong`、`writeFloat`、`writeDouble`、`writeBinary` 做同样的修正，将"the boolean value"分别改为"the long value"、"the float value"、"the double value"、"the binary value"。

## 总结

这是一个纯文档修正提交，修复了 `TripleWriter` 接口中 5 个类型特定 write 方法的 JavaDoc `@param` 描述的复制粘贴错误。所有方法错误地使用了"the boolean value"描述，现已修正为各自对应的类型描述。该提交不涉及任何功能代码修改，仅提升 JavaDoc 文档的准确性。
