# 提交 2633：Arrow: delete outdated comment (#14068)

## 提交信息

- **序号**：2633 / 4088
- **哈希**：dec44392c0afcf02e8d13a413d5fbbea9d1ae89a
- **短哈希**：dec44392c
- **日期**：2025-09-15 08:51:19 +0200
- **作者**：Nándor Kollár
- **提交说明**：Arrow: delete outdated comment (#14068)
- **PR/Issue**：#14068

## 总体目的

`ArrowReader.java` 的类级别 Javadoc 中列举了一些已知的限制（limitations），用于向使用者说明 ArrowReader 当前的已知问题。其中有一条注释描述了一个已不再存在的问题：当列具有常量值时，会以字典形式物理编码，Arrow 向量类型为 int32 而非 schema 中的类型，并引用了 issue #2484。

随着代码演进，该问题已在上游修复，注释描述的行为不再成立。保留过时注释会误导使用者，使其误以为当前版本仍存在该限制，因此需要删除该过时注释。

## 如何达成设计目的

直接从 `ArrowReader.java` 的类 Javadoc 中删除描述 issue #2484 的三行注释（一个 `<li>` 列表项）。其余列出的限制项（类型提升、复杂数据类型支持等）保持不变。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/ArrowReader.java` (+0/-3 lines)

**修改目的**：删除过时的限制说明注释。

**工作逻辑**：删除了类 Javadoc 中关于"常量值列以字典编码、Arrow 向量类型为 int32"的限制说明及其指向 issue #2484 的引用。该限制已不复存在，删除后文档与实际行为保持一致。

## 总结

这是一次纯文档清理提交，删除了 ArrowReader 中已经过时的限制说明注释。它不影响任何运行时行为，仅保证文档与代码现状一致，避免误导使用者和维护者。
