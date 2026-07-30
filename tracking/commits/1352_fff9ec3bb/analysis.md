# 提交 1352：Docs: Fix format of verifying release candidate with Flink (#11487)

## 提交信息

- **序号**：1352 / 4088
- **哈希**：fff9ec3bbc322080da6363b657415b039c0e92a0
- **短哈希**：fff9ec3bb
- **日期**：2024-11-08（Fri Nov 8 14:36:27 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Fix format of verifying release candidate with Flink (#11487)
- **PR/Issue**：#11487

## 总体目的

`site/docs/how-to-release.md` 是 Iceberg 项目维护者发布新版本时使用的指南文档，其中包含用 Flink SQL Client 验证 release candidate 的命令示例。该示例使用了一段多行 shell 命令（`sql-client.sh embedded ... shell`），但代码块在 `shell` 之后缺少闭合的 ``` ``` ``` 标记，导致后续的 "## Voting" 标题被 Markdown 渲染器误判为仍处于该 shell 代码块内，发布指南页面渲染异常。

本提交在 `shell` 行之后补上缺失的代码块结束标记 ``` ``` ```，使 Markdown 重新正确渲染，Voting 章节恢复为正常标题。

## 如何达成设计目的

直接编辑 `site/docs/how-to-release.md`，在 Flink SQL Client 命令示例的 `shell` 行之后追加一行 ``` ``` ```（三个反引号），闭合此前未关闭的代码块。这是一处单行的 Markdown 格式修复，不涉及任何代码或构建逻辑。

## 修改详情

### `site/docs/how-to-release.md`

**修改目的**：闭合 Flink release candidate 验证示例的代码块。

**工作逻辑**：在第 441 行附近的 `shell` 之后新增一行 ``` ``` ```：

```markdown
    -j iceberg-flink-runtime-1.20-{{ icebergVersion }}.jar \
    -j flink-connector-hive_2.12-1.20.jar \
    shell
```

此前文档中该 ``` ``` ```shell ``` 起始标记对应的结束标记缺失，新增后整个 Flink 命令示例成为完整的代码块，后续的 "## Voting" 章节恢复正常显示。

## 小结

- **成效**：发布指南页面中 Flink release candidate 验证示例的代码块正确闭合，Markdown 渲染正常，后续 Voting 章节不再被误并入代码块。
- **影响范围**：仅 `site/docs/how-to-release.md` 一个文件，新增 1 行，无代码、构建或运行时变更。
- **回迁到 1.4.x 的注意事项**：这是 main 分支网站文档的一处格式修复，与 1.4.x 维护分支的发布产物无关。1.4.x 自身的发布文档已随其发布时定型，无需回迁此修复；即便 1.4.x 的 `how-to-release.md` 中存在类似格式问题，也不影响其 jar/发布物。**无需回迁**。
