# 提交 0876：Spec: Fix Typo (#10564)

## 提交信息

- **序号**：0876 / 4088
- **哈希**：29ff08a08633ca8df194a26f533b5f9af50fca9e
- **短哈希**：29ff08a08
- **日期**：2024-06-25 15:40:42 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Spec: Fix Typo (#10564)
- **PR/Issue**：#10564

## 总体目的

Iceberg 的格式规范文档 `format/spec.md` 中，描述 primitive types 的表格表头有一处拼写错误：将 "version" 误写为 "verison"（字母 `i` 与 `o` 顺序颠倒）。本提交修正这处拼写错误，使规范文档保持专业性和可读性。

## 如何达成设计目的

直接修改 `format/spec.md` 第 171 行所在表格的表头，将 `Added by verison` 改为 `Added by version`。改动仅 1 个字符顺序调整，不涉及任何规范语义变化。

## 修改详情

### `format/spec.md`

**修改目的**：修正 primitive types 表格表头中的拼写错误。
**工作逻辑**：将表头单元格 `Added by verison` 改为 `Added by version`。

```diff
-| Added by verison | Primitive type     | Description ... |
+| Added by version | Primitive type     | Description ... |
```

## 小结

- **成效**：修正了 Iceberg 格式规范文档中一处拼写错误（`verison` → `version`）。
- **影响范围**：仅 `format/spec.md` 一个文件，1 行改动。
- **回迁到 1.4.x 的注意事项**：纯文档拼写修正，可安全回迁，无任何风险。若 1.4.x 分支的 `spec.md` 存在相同拼写错误则可 cherry-pick；若已修正或不存在则无需回迁。
