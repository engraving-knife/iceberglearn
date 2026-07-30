# 提交 0666：Spec: Document support for binary in truncate transform

## 提交信息

- **序号**：0666 / 4088
- **哈希**：cd707394ddc8cd41ba12bde83ead059716cf5623
- **短哈希**：cd707394d
- **日期**：2024-04-08 14:12:57 -0700
- **作者**：Brian Hulette <hulettbh@gmail.com>
- **提交说明**：Spec: Document support for binary in truncate transform (#10079)
- **PR/Issue**：#10079

## 总体目的

本提交是一个**规范文档（Spec）类改动**：在 Iceberg 表格式的核心规范文件 `format/spec.md` 中，正式记录 `truncate[W]`（截断）变换对 `binary`（二进制）类型的支持。

### 背景

Iceberg 的分区规范定义了多种变换函数（transform），用于将源列的值映射为分区值。其中 `truncate[W]` 变换将值截断到指定宽度 `W`，常用于实现范围分区或减少分区基数。在此提交之前，规范中 `truncate[W]` 的支持类型仅列为 `int`、`long`、`decimal`、`string`，并未明确包含 `binary` 类型。而另一个变换 `bucket[N]` 则早已明确支持 `binary`。

这意味着虽然实际实现中可能已经支持了对 `binary` 类型的截断操作，但规范文档未能同步更新，导致规范与实现之间出现不一致。本提交通过更新规范文档，明确 `truncate[W]` 支持 `binary` 类型，消除文档与实现之间的偏差。

## 如何达成设计目的

设计思路非常直接：在 `format/spec.md` 的两个位置做最小化修改：

1. 在变换概览表中，将 `binary` 添加到 `truncate[W]` 的"Source type"列。
2. 在截断变换的详细说明表中，新增 `binary` 类型的行，说明其截断语义，并添加一条注释说明 binary 与 string 截断的关键区别。

## 修改详情

### `format/spec.md`

**修改目的**：在 Iceberg 规范中正式文档化 `truncate[W]` 变换对 `binary` 类型的支持。

**工作逻辑**：

1. **变换概览表（第 317 行附近）**：在 `truncate[W]` 行的"Source type"列中，将 `int`, `long`, `decimal`, `string` 修改为 `int`, `long`, `decimal`, `string`, `binary`。这使得 `truncate[W]` 支持的类型列表与 `bucket[N]` 保持一致（两者现在都支持 `binary`）。

2. **截断详细说明表（第 354 行附近）**：新增一行描述 `binary` 类型的截断行为：
   - 宽度参数为 `L`（长度）
   - 截断逻辑为 `v.subarray(0, L)`，即取前 `L` 个字节
   - 示例：`L=3` 时，`\x01\x02\x03\x04\x05` → `\x01\x02\x03`

3. **注释（第 358 行附近）**：新增第 4 条注释，明确指出："In contrast to strings, binary values do not have an assumed encoding and are truncated to `L` bytes."（与字符串不同，二进制值没有假定的编码，截断到 `L` 个字节。）这条注释非常关键，因为 string 的截断（注释 3）需要保证结果是有效的 UTF-8 字符串且不超过 `L` 个码点（code points），而 binary 没有编码概念，直接按字节截断即可。

## 小结

- **成效**：成功在规范文档中明确了 `truncate[W]` 对 `binary` 类型的支持，消除了规范与实现之间的不一致。新增的注释也清晰区分了 binary 与 string 截断语义的差异。
- **影响范围**：仅影响 Iceberg 规范文档 `format/spec.md`，不涉及任何代码修改。对实现层无直接影响，主要是文档对齐。
- **回迁到 1.4.x 的注意事项**：这是一个纯文档改动，回迁无风险。只需确保 1.4.x 分支的 `format/spec.md` 同步更新即可。
