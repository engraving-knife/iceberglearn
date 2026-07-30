# 提交 1806：Docs: Fix link of ndv in spark-procedures.md (#12425)

## 提交信息

- **序号**：1806 / 4088
- **哈希**：adef1ad4759eb74fa3ea2b51592d7dd1b4942810
- **短哈希**：adef1ad47
- **日期**：2025-03-01 13:42:19 -0800
- **作者**：wangyinsheng
- **提交说明**：Docs: Fix link of ndv in spark-procedures.md (#12425)
- **PR/Issue**：#12425

## 总体目的

Iceberg 的 Spark 存储过程文档 `docs/docs/spark-procedures.md` 中 `compute_table_stats` 小节描述了该过程会计算 NDV（Number of Distinct Values，不同值数量）统计，并提供了一个指向 puffin-spec 的链接。原链接为 `../../format/puffin-spec.md`，该相对路径指向了错误的目录层级（`docs/docs/` 下的文件用 `../../format/` 会跳到仓库根的 `format/` 目录，而文档站点实际应以文档目录为基准）。

此外，原链接仅指向 puffin-spec 文档本身，未定位到具体的 NDV blob 类型章节。本提交修正链接路径并添加锚点，使其直接指向 `Apache DataSketches Theta v1 blob type` 小节，方便用户直达 NDV 统计的相关说明。

## 如何达成设计目的

通过修改 `docs/docs/spark-procedures.md` 中 `compute_table_stats` 描述里的 Markdown 链接，将路径从 `../../format/puffin-spec.md` 改为 `../../puffin-spec.md#apache-datasketches-theta-v1-blob-type`。调整相对路径层级并追加锚点。

## 修改详情

### `docs/docs/spark-procedures.md`（修改, ±1 lines）

**修改目的**：修正 NDV 统计的文档链接。

**工作逻辑**：将 `compute_table_stats` 小节描述中的链接 `[Number of Distinct Values (NDV) statistics](../../format/puffin-spec.md)` 改为 `[Number of Distinct Values (NDV) statistics](../../puffin-spec.md#apache-datasketches-theta-v1-blob-type)`。修正点：
- 路径从 `../../format/puffin-spec.md` 改为 `../../puffin-spec.md`（去掉多余的 `format/` 层级，使路径正确指向 `docs/puffin-spec.md`）。
- 追加锚点 `#apache-datasketches-theta-v1-blob-type`，直接定位到 Apache DataSketches Theta v1 blob 类型小节，该小节描述了 NDV 统计使用的 blob 格式。

## 小结

- **成效**：修正了 Spark 存储过程文档中 NDV 统计链接的路径错误，并添加锚点直达相关章节，提升文档可用性。
- **影响范围**：仅影响文档 `docs/docs/spark-procedures.md` 的一行链接，不涉及代码改动。
- **回迁到 1.4.x 的注意事项**：纯文档改动，无风险，无前置依赖。但需确认 1.4.x 分支的文档目录结构与 main 一致，且 `puffin-spec.md` 中存在对应的锚点标题；若 1.4.x 文档结构不同，需调整相对路径。建议回迁以保持文档链接正确。
