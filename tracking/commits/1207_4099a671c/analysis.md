# 提交 1207：Puffin: Document stats `ndv` value representation (#10793)

## 提交信息

- **序号**：1207 / 4088
- **哈希**：4099a671c73d0017845a3d821e5c171190fc8c99
- **短哈希**：4099a671c
- **日期**：2024-10-03（Thu Oct 3 12:49:41 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Puffin: Document stats `ndv` value representation (#10793)
- **PR/Issue**：#10793

## 总体目的

Puffin 是 Iceberg 用于存放 deletion vector（DV）和统计 sketch（如 Theta Sketch 用于 NDV 估算）的辅助文件格式。Puffin 规范（`format/puffin-spec.md`）中，`apache-datasketches-theta-v1` 类型的 blob 在其 `BlobMetadata.properties` 中可携带一个 `ndv` 属性，表示从 sketch 推导出的"不同值数量（number of distinct values）"的估计值。

但规范此前只写了：

> `ndv`: estimate of number of distinct values, derived from the sketch.

没有说明这个值在 properties 中是如何序列化为字符串的。`properties` 是一个 `JSON object with string property values`（见 `BlobMetadata` 字段定义），即所有属性值都是 JSON 字符串。那么 `ndv` 这个数值应该以什么格式存进字符串？是十进制数字串？还是带符号？是否允许前导/尾随空格？规范未明确，导致：

1. **实现不一致风险**：不同写入端（Iceberg core、各引擎）可能用不同的 `toString()` / `String.valueOf(long)` 方式序列化，读出端若用严格解析（如 `Long.parseLong`）可能因意外的空格、符号、前导零等导致解析失败。
2. **互操作性问题**：第三方工具读取 Puffin 文件时无法确定 `ndv` 的确切字符串格式。

本提交通过在规范中明确 `ndv` 的字符串表示规则来消除这一歧义：**非负整数，用十进制数字表示，无前导或尾随空格**。

## 如何达成设计目的

直接在 `puffin-spec.md` 中给 `ndv` 属性的描述补充一句话，明确其字符串序列化格式。这是一处纯文档修订，不涉及任何代码改动，目的是让规范的表述足够精确，使所有实现方按统一规则序列化/解析 `ndv`。

## 修改详情

### `format/puffin-spec.md`（修改，+2/-1 行）

**修改目的**：明确 `ndv` 属性值的字符串表示格式。

**工作逻辑**：

把：

```markdown
- `ndv`: estimate of number of distinct values, derived from the sketch.
```

改为：

```markdown
- `ndv`: estimate of number of distinct values, derived from the sketch,
  stored as non-negative integer value represented using decimal digits
  with no leading or trailing spaces.
```

关键约束：

- **non-negative integer**：非负整数（NDV 估算值天然非负，明确禁止负数）；
- **decimal digits**：十进制数字表示（即 `0-9`，不允许可选的 `+` 符号、不允许十六进制等）；
- **no leading or trailing spaces**：无前导或尾随空格（禁止 `" 123"` 或 `"123 "` 这类带空格的序列化）。

这与 Java `Long.toString(long)` / `Long.parseLong(String)` 的默认行为一致（`Long.toString` 产出纯十进制数字串无空格，`Long.parseLong` 默认允许前导/尾随空格但本规范禁止），为写入端用 `String.valueOf(long)` / `Long.toString()` 序列化、读出端用 `Long.parseLong` 解析提供了规范依据。

## 小结

- **成效**：消除了 Puffin 规范中 `ndv` 属性字符串表示的歧义，明确了"非负整数、十进制数字、无前后空格"的格式契约，提升了不同实现之间的互操作性。
- **影响范围**：仅修改规范文档 `format/puffin-spec.md` 一个文件、一句话。不改变任何代码行为，但为后续实现（写入端序列化、读出端解析）提供了规范依据。现有 Iceberg 实现中 `ndv` 通常用 `String.valueOf(long)` 序列化，已符合此规范，故无兼容性问题。
- **回迁到 1.4.x 的注意事项**：纯文档修订，回迁零风险。1.4.x 分支上的 `format/puffin-spec.md` 可直接 cherry-pick。若 1.4.x 的 Puffin 写入/读取代码对 `ndv` 的序列化/解析有特殊处理（如允许空格、允许符号），应趁机对齐到规范要求。
