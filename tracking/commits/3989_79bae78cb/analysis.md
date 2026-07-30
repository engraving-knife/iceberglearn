# 提交 3989：Docs: Fix reversed Flink binary/varbinary to Iceberg type mapping (#17090)

## 提交信息

- **序号**：3989 / 4088
- **哈希**：79bae78cba314ecf29cb2c2bf7b7016503e6c919
- **短哈希**：79bae78cb
- **日期**：2026-07-06 20:20:47 +0200
- **作者**：Eunbin Son
- **提交说明**：Docs: Fix reversed Flink binary/varbinary to Iceberg type mapping (#17090)
- **PR/Issue**：#17090

## 总体目的

本提交修复了 Flink 类型映射文档中 binary/varbinary 映射颠倒的错误。文档错误地将 Flink 的 `binary` 类型映射到 Iceberg 的 `binary`，将 `varbinary` 映射到 `fixed`，但实际映射应该是相反的：Flink `binary` → Iceberg `fixed`，Flink `varbinary` → Iceberg `binary`。

这个文档错误可能误导用户理解 Flink 与 Iceberg 之间的二进制类型映射关系。

## 如何达成设计目的

交换文档表格中 binary 和 varbinary 两行的 Iceberg 类型映射。

## 修改详情

### `docs/docs/flink.md` (+2/-2 lines)

**修改目的**：修复颠倒的类型映射。

**工作逻辑**：
```markdown
# 旧（错误）：
| binary              | binary                     |               |
| varbinary           | fixed                      |               |

# 新（正确）：
| binary              | fixed                      |               |
| varbinary           | binary                     |               |
```

## 总结

简单的文档修复，交换了 Flink binary/varbinary 到 Iceberg 类型的映射，使其与实际代码行为一致。
