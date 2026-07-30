# 提交 2528：Docs: Fix table of contents in Flink docs (#13864)

## 提交信息

- **序号**：2528 / 4088
- **哈希**：0478ff7dc8e4c048e0a1101df11656a00b9f81cd
- **短哈希**：0478ff7dc
- **日期**：2025-08-19 16:11:53 +0200
- **作者**：Maximilian Michels
- **提交说明**：Docs: Fix table of contents in Flink docs (#13864)
- **PR/Issue**：#13864

## 总体目的

此提交修复了 Flink 文档中 Markdown 标题层级不正确的问题，导致文档目录（Table of Contents）结构混乱。

在 `docs/docs/flink-writes.md` 文件中，存在以下标题层级问题：

1. **错误的顶级标题**：`# Flink Writes (SinkV2 based implementation)` 使用了一级标题（H1），但该内容实际上是 Flink Writes 文档的一个子部分，应该使用二级标题（H2）。在 MkDocs 中，每个页面通常只有一个 H1 标题（页面标题），使用额外的 H1 会导致目录结构错误。

2. **标题层级跳跃**：在 H1 之后直接使用了 H2（`## Writing with SQL`、`## Writing with DataStream`），使得这些子部分的层级不正确。修正后应该使用 H3（`### Writing with SQL`、`### Writing with DataStream`）。

3. **子标题层级不一致**：`#### Cache` 应该是 `### Caching`，修正层级并改用更自然的标题名称。

4. **标题措辞优化**：`## Flink Dynamic Iceberg Sink` 改为 `## Flink Dynamic Iceberg Sink`（保持 H2），以及 `Dynamic Flink Iceberg Sink allows:` 改为 `The Flink Dynamic Iceberg Sink (Dynamic Sink) allows:` 使描述更清晰。

## 如何达成设计目的

修复方案调整了 `flink-writes.md` 文件中的 Markdown 标题层级，使目录结构正确反映文档的层次关系：

1. `# Flink Writes (SinkV2 based implementation)` → `## Sink V2 based implementation`（H1 改 H2，简化标题）
2. `## Writing with SQL` → `### Writing with SQL`（H2 改 H3）
3. `## Writing with DataStream` → `### Writing with DataStream`（H2 改 H3）
4. `## Dynamic Iceberg Flink Sink` → `## Flink Dynamic Iceberg Sink`（调整措辞，保持 H2）
5. `#### Cache` → `### Caching`（H4 改 H3，调整措辞）

## 修改详情

### `docs/docs/flink-writes.md` (+6/-6 lines)

**修改目的**：修复标题层级，使文档目录结构正确。

**工作逻辑**：
- 将 SinkV2 实现部分从 H1 降为 H2
- 将 SQL 和 DataStream 写入子部分从 H2 降为 H3，使其成为 SinkV2 部分的子标题
- 将缓存部分从 H4 提升为 H3，与同级别内容保持一致
- 优化 Dynamic Sink 部分的标题措辞和描述文字

## 总结

此提交是一个纯文档修复，通过调整 Markdown 标题层级修复了 Flink 文档的目录结构问题。虽然变更仅涉及 6 行标题文本的修改，但对于文档的可读性和导航体验有实际改善。正确的标题层级确保了文档目录能够准确反映内容的层次结构。
