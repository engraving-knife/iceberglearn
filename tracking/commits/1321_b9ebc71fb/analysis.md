# 提交 1321：Puffin: Add deletion-vector-v1 blob type (#11238)

## 提交信息

- **序号**：1321 / 4088
- **哈希**：b9ebc71fbc9803b6a8a4b9ed63b9ad4adeb66edf
- **短哈希**：b9ebc71fb
- **日期**：2024-11-02（Sat Nov 2 03:04:03 2024 -0700）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Puffin: Add deletion-vector-v1 blob type (#11238)
- **PR/Issue**：#11238

## 总体目的

Puffin 是 Iceberg 用来在 Parquet/ORC/Avro 数据文件之外存放"辅助文件"（如统计 sketches、删除向量等）的容器格式，spec 位于 `format/puffin-spec.md`。原 spec 中只定义了一种 blob 类型 `apache-datasketches-theta-v1`（用于存放 NDV 等近似去重 sketch）。

随着 Iceberg 引入 deletion vector（删除向量）特性——把"位置删除"以 puffin 文件中的紧凑位图形式持久化，从而支持行级 upsert/delete 而无需重写数据文件或散落的 equality delete 文件——社区需要在 spec 中正式定义一个新的 blob 类型 `deletion-vector-v1`，描述其序列化格式与元数据约束。

本提交即在 `format/puffin-spec.md` 的 "Blob types" 章节下，紧跟 `apache-datasketches-theta-v1` 之后新增 `#### deletion-vector-v1 blob type` 子节，规范其：

1. 用途：表示一个数据文件中被删除行的位置集合。
2. 位图结构：支持 64-bit 行号（最高位必须为 0），内部用 32-bit Roaring 位图数组实现——把 64-bit 拆为"高 32-bit key + 低 32-bit 子位置"，按 key 维护 32-bit Roaring 位图。
3. 物理布局：4 字节大端 length+magic、4 字节 magic `D1 D3 39 64`、向量本身、4 字节大端 CRC-32 校验和。
4. 向量本身的 portable Roaring 序列化格式：8 字节小端 bitmap 数量，逐个 32-bit Roaring bitmap 按 key 无符号升序排列，每项为 4 字节小端 key + 标准 32-bit Roaring 位图。
5. 元数据约束：必须包含 `referenced-data-file` 和 `cardinality` 两个 properties，必须省略 `compression-codec`；`snapshot-id` 与 `sequence-number` 在 Puffin v1 中必须设为 -1。

这一格式规范正是提交 1e3ee1e4e（#11372）中 `RoaringPositionBitmap.serialize/deserialize` 实现所对应的 on-disk 格式——spec 与实现双向对齐。

## 如何达成设计目的

通过纯文档改动（不改代码、不改测试）在 spec 中正式注册新 blob 类型。具体步骤：

1. 在 `format/puffin-spec.md` 的 `#### apache-datasketches-theta-v1 blob type` 子节之后新增 `#### deletion-vector-v1 blob type` 子节；
2. 文字描述用途、支持的位域范围、内部 32-bit Roaring 位图数组结构、key/子位置拆分策略、查询位置是否被置位的算法；
3. 描述序列化字节布局（length+magic / magic / vector / CRC）；
4. 描述向量本身的 portable Roaring 序列化结构；
5. 列出 blob properties 的强约束（必填 `referenced-data-file` 和 `cardinality`、必省 `compression-codec`）与 `snapshot-id`/`sequence-number` 取值约束；
6. 在末尾追加两条 Roaring 官方 spec 的引用链接。

格式设计上的几个关键选择在文本中显式说明：

- **大小端混用**：外层 length/CRC 用大端，内层 Roaring 用小端。大端选择是为了与 Delta 表中已有的 deletion vector 兼容（迁移/互操作考虑）。
- **magic `D1 D3 39 64`**：与 Delta 表使用的 magic 一致，便于复用既有工具链。
- **不压缩**：`deletion-vector-v1` 明确不允许 compression-codec，保持简单与互操作。
- **`snapshot-id` / `sequence-number` = -1**：因为 puffin 文件生成时还不知道最终归属快照与序列号，spec 显式约束为 -1，由后续 commit 阶段补充关联。

## 修改详情

### `format/puffin-spec.md`

**修改目的**：在 spec 中正式注册 `deletion-vector-v1` blob 类型。

**工作逻辑**：在 `### Blob types` 节下、`#### apache-datasketches-theta-v1` 之后新增 `#### deletion-vector-v1 blob type` 子节（约 55 行）。内容结构如下：

1. **用途说明**：
   > A serialized delete vector (bitmap) that represents the positions of rows in a file that are deleted. A set bit at position P indicates that the row at position P is deleted.

2. **位图结构**：支持正数 64-bit 位置（最高位必须为 0），内部用一组 32-bit Roaring 位图，64-bit 位置拆分为"高 32-bit key + 低 32-bit 子位置"。对每个出现的 key 维护一个 32-bit Roaring 位图存放该 key 下的子位置集合。

3. **查询算法**：取位置的最高 4 字节作为 key 找对应 32-bit 位图，再用最低 4 字节作为子位置在该位图中查询；找不到对应 key 的位图则位置未置位。

4. **blob 字节布局**（外层）：
   - 4 字节大端：向量长度 + magic 字节数之和（即除自身外的剩余 blob 长度）
   - 4 字节 magic：`D1 D3 39 64`
   - 向量本身：见下条
   - 4 字节大端：CRC-32 校验和（覆盖 magic 与序列化向量）

5. **向量本身的 portable Roaring 序列化格式**：
   - 8 字节小端：32-bit Roaring 位图的数量
   - 对每个 32-bit Roaring 位图（按 key 的无符号比较升序排列）：
     - 4 字节小端 key
     - 一个标准 32-bit Roaring 位图

6. **大小端约定**：length 与 CRC 用大端（兼容 Delta）；Roaring 内部用小端。

7. **blob properties 约束**：
   - 必含 `referenced-data-file`：本删除向量所适用的数据文件 location，必须等于表元数据中数据文件的 `location`；
   - 必含 `cardinality`：被删除行数（置位数量）；
   - 必须省略 `compression-codec`（不压缩）。

8. **快照/序列号约束**：因为 puffin 文件生成时还不知道最终快照 ID 与序列号，`snapshot-id` 与 `sequence-number` 在 Puffin v1 中必须设为 -1。

9. **参考链接**：
   - [Roaring portable serialization（64-bit 扩展）](https://github.com/RoaringBitmap/RoaringFormatSpec?tab=readme-ov-file#extension-for-64-bit-implementations)
   - [Roaring general layout](https://github.com/RoaringBitmap/RoaringFormatSpec?tab=readme-ov-file#general-layout)

## 小结

- **成效**：Puffin spec 现正式定义 `deletion-vector-v1` blob 类型，规范了删除向量的 on-disk 格式、magic、CRC 校验、大小端约定、必填/禁填 properties、快照与序列号取值。这一规范与 `RoaringPositionBitmap` 的实现（#11372）以及 `BitmapPositionDeleteIndex` 切换（1318 #11441）、`cardinality()` 暴露（1320 #11442）一起构成 deletion vector 特性的 spec + 实现闭环。
- **影响范围**：1 个文件、55 行新增。纯规范文档改动，无代码、无测试。
- **回迁到 1.4.x 的注意事项**：
  - 纯 spec 文档变更，回迁零运行时风险。
  - 但需结合 1.4.x 是否计划支持 deletion vector 特性整体决策：
    - 若 1.4.x 不打算引入 deletion vector，单独回迁此 spec 段落没有意义（也不会被代码使用），可暂不回迁；
    - 若 1.4.x 计划支持 deletion vector，则本 spec 段落必须与 1318（`RoaringPositionBitmap` 接入索引）、1320（`cardinality()` 暴露）、#11372（`RoaringPositionBitmap` 类与序列化实现）一起回迁，否则会出现 spec 与代码不匹配。
  - 规范中明确"为兼容 Delta 而采用大端 length/CRC + Delta 的 magic `D1 D3 39 64`"，这一互操作设计即便 1.4.x 暂不实现 deletion vector 也无副作用——保留在 spec 中不会有兼容性问题。
  - 若 1.4.x 决定只回迁 spec 文档以提前对齐未来版本规划，也是安全的，因为 spec 不约束运行时行为，仅约束"将来如果实现该 blob 类型时应如何读写"。
