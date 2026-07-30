# 提交 3057：Spec: fix impl note about snapshot ID generation (#14720)

## 提交信息

- **序号**：3057 / 4088
- **哈希**：00046005889c66ffc860bae012d7fc560e8f040a
- **短哈希**：000460058
- **日期**：2026-01-01
- **作者**：Dan LaRocque
- **提交说明**：Spec: fix impl note about snapshot ID generation (#14720)
- **PR/Issue**：#14720

## 总体目的

本提交修复了 Iceberg 规范文档 `format/spec.md` 中关于快照 ID（snapshot id）生成机制的一处事实性错误描述。该段实现说明（impl note）描述的是参考 Java 实现如何基于 type 4 UUID 生成伪随机的快照 ID，原文写成"将 UUID 的 4 个最高有效字节（4 most significant bytes）与 4 个最低有效字节（4 least significant bytes）做 XOR"，然后再与 `Long.MAX_VALUE` 做 AND 运算得到一个正的 long 型快照 ID。

问题在于：UUID 是 128 位（16 字节），而 Java 的 `long` 是 64 位（8 字节）。要将 128 位的 UUID 压缩成 64 位的 long，必须把高 64 位与低 64 位做 XOR，也就是 8 个字节对 8 个字节，而不是 4 字节对 4 字节。原文的"4 most/least significant bytes"在数值上是错误的——如果只 XOR 4 字节，就会丢弃 UUID 一半的熵，既与参考实现的真实做法不符，也会误导其他实现者照此实现，从而错误地缩小快照 ID 的随机空间、增加碰撞概率。本提交把两处"4"改为"8"，使文档与实际实现及位运算语义保持一致。

这是一处纯文档勘误，不涉及任何代码或行为变更，但对规范作为权威参考的准确性至关重要，尤其因为快照 ID 的唯一性是 Iceberg 元数据正确性的基础。

## 如何达成设计目的

仅修改 `format/spec.md` 中快照 ID 生成说明段落里的两个数量词，把 `4 most significant bytes` 与 `4 least significant bytes` 分别改为 `8 most significant bytes` 与 `8 least significant bytes`，使描述与 128 位 UUID 折叠为 64 位 long 的真实位运算对齐。

## 修改详情

### `format/spec.md` (+1/-1 lines)

**修改目的**：修正快照 ID 生成说明中 XOR 操作的字节数描述。

**工作逻辑**：
将原文 `The reference Java implementation uses a type 4 uuid and XORs the 4 most significant bytes with the 4 least significant bytes then ANDs with the maximum long value to arrive at a pseudo-random snapshot id with a low probability of collision.` 中的 `4 most significant bytes` 改为 `8 most significant bytes`，`4 least significant bytes` 改为 `8 least significant bytes`。type 4 UUID 共 128 位，参考实现取其高 64 位（8 字节）与低 64 位（8 字节）做 XOR 得到 64 位结果，再与 `Long.MAX_VALUE`（即 `0x7FFFFFFFFFFFFFFF`）做 AND 以清除符号位、确保结果为正 long。修正后的描述准确反映了这一位运算过程。

## 总结

本提交是一处规范文档勘误，将快照 ID 生成说明中错误的"4 字节 XOR 4 字节"更正为"8 字节 XOR 8 字节"，使规范与 128 位 UUID 折叠为 64 位 long 的真实实现及位运算语义一致，避免误导其他 Iceberg 实现。
