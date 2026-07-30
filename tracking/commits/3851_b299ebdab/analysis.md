# 提交 3851：Mumbling: Add draft Mumbling Bitmap spec (#16518)

## 提交信息

- **序号**：3851 / 4088
- **哈希**：b299ebdab02a1e14c4f7798d2a3542588d47b798
- **短哈希**：b299ebdab
- **日期**：2026-06-09 14:43:50 -0700
- **作者**：Ryan Blue
- **提交说明**：Mumbling: Add draft Mumbling Bitmap spec (#16518)
- **PR/Issue**：#16518

## 总体目的

本提交新增了 Mumbling Bitmap 格式的草案规范（draft spec）。Mumbling Bitmap 是一种压缩位图格式，专门为有界总大小的用例（如删除向量 Deletion Vector）设计，基于 Roaring Bitmap 的思想但进行了简化和优化。

Iceberg 的删除向量需要一种紧凑的格式来存储被删除行的位置集合。现有的 Roaring Bitmap 虽然功能强大，但其设计针对通用大规模场景，对于 Iceberg 中较小的嵌入式删除向量（通常少于 100,000 条目）来说存在开销。Mumbling Bitmap 通过使用更小的容器（32 字节而非 Roaring 的 8KB）和描述符数组（descriptor array）替代 Roaring 的键+基数+偏移三元组，减少了每容器开销。

本规范定义了版本 1 的完整格式，包括头部、描述符数组、容器（稀疏和密集）的结构，以及用于压缩描述符数组的 PFOR（Patched Frame of Reference）编码算法。

## 如何达成设计目的

规范文档分为以下部分：

1. **Overview（概述）**：介绍 Mumbling Bitmap 的基本概念——将位图分为 256 位的容器，每个容器稀疏（存储偏移）或密集（存储位集），使用描述符字节数组跟踪容器。

2. **Design choices（设计选择）**：解释为何选择描述符数组而非 Roaring 的键+偏移方案。对于 Iceberg 的小型删除向量场景，描述符数组避免了每容器 4 字节的开销（2 字节键 + 2 字节偏移），只需 1 字节描述符。

3. **Format（格式）**：定义三段式结构（头部 + 描述符数组 + 容器），所有整数为无符号小端序。

4. **Appendix A（附录 A）**：详细定义 PFOR 编码算法用于压缩描述符数组。

## 修改详情

### `format/mumbling-spec.md` (+316/-0 lines, new file)

**修改目的**：新增 Mumbling Bitmap 格式规范文档。

**工作逻辑**：

规范定义了以下关键结构：

1. **Header（头部，6 字节）**：
   - Format version（1 字节）：`0x01`
   - Cardinality（3 字节）：设置的位数
   - Container count（2 字节）：容器数量（最多 8,192）

2. **Descriptor array（描述符数组）**：每个容器一个描述符字节。最高 3 位决定容器类型：
   - `000`：稀疏容器（0-31 个值），低 5 位为值数量
   - `001`：密集容器（32 字节），低 5 位必须为 0
   - 使用 PFOR 编码压缩

3. **Containers（容器）**：
   - 稀疏容器：最多 31 个设置位置，每个为无符号字节（0-255），升序排列
   - 密集容器：32 字节数组，每位对应一个位置，最高有效位在前

4. **PFOR 编码**（附录 A）：将值数组分为 256 值的块，每块用头部（3 字节：b1/b2/e/m）+ 主值数组 + 异常偏移 + 异常值数组编码。选择最优位宽 `b1` 使大部分值能用 `b1` 位存储，少数异常值额外存储剩余位。

规范还包含多个示例说明编码和解码过程。

## 总结

本提交引入了 Mumbling Bitmap 格式的草案规范，为 Iceberg 删除向量提供了一种比 Roaring Bitmap 更紧凑的专用格式。这是一个设计文档提交，不包含任何代码实现，为后续实现工作奠定基础。规范设计针对 Iceberg 的具体需求（小型嵌入式删除向量）进行了优化，通过描述符数组和 PFOR 编码减少开销。这是 Iceberg 格式演进的重要一步。
