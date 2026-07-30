# 提交 3495：Spec: Fix typos and stray formatting in gcm-stream-spec and puffin-spec (#15813)

## 提交信息

- **序号**：3495 / 4088
- **哈希**：d204e5e2dff8a5a7da6aa39c4be995633d223b22
- **短哈希**：d204e5e2df
- **日期**：2026-04-01 14:51:47 -0700
- **作者**：Eunbin Son
- **提交说明**：Spec: Fix typos and stray formatting in gcm-stream-spec and puffin-spec (#15813)
- **PR/Issue**：#15813

## 总体目的

修复 Iceberg 格式规范文档中的拼写错误和格式问题。具体修复 gcm-stream-spec.md 中的拼写错误（"AEG GCM" → "AES GCM"，"reflects" 时态问题）和 puffin-spec.md 中多余的水平分隔线。

## 如何达成设计目的

直接修正文档中的错误文本和格式。

## 修改详情

### `format/gcm-stream-spec.md` (+2/-2 lines)

**修改目的**：修复拼写错误。

**工作逻辑**：
1. "AEG GCM cipher" → "AES GCM cipher"（加密算法名称拼写错误）
2. "AADs are built to reflects the identity" → "AADs are built to reflect the identity"（语法错误，reflects → reflect）

### `format/puffin-spec.md` (+0/-1 line)

**修改目的**：移除多余的水平分隔线。

**工作逻辑**：删除压缩编解码器表格后多余的 `__` 分隔线（Markdown 水平线标记）。

## 总结

文档修复提交，修正 gcm-stream-spec.md 中的两处拼写/语法错误（AEG→AES，reflects→reflect）和 puffin-spec.md 中多余的水平分隔线。
