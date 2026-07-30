# 提交 0397：Docs: Correct spelling of gauge

## 提交信息

- **序号**：0397
- **哈希**：77ee577a84e49f6e1b103b3f60f585c7b4e46338
- **短哈希**：77ee577a8
- **日期**：Mon Jan 22 10:25:43 2024 +0800
- **作者**：Wang Tao <watters.tao@gmail.com>
- **提交说明**：Docs: Correct spelling of gauge (#9543)
- **PR/Issue**：#9543

## 总体目的

这个提交修复了 Iceberg Flink 集成文档中指标类型名称的拼写错误。在度量（Metrics）领域，"Gauge" 是一种标准的指标类型（源自 Dropwizard Metrics 库及 Micrometer 等度量框架），用于表示一个瞬时值——即在某个时间点观察到的单个数值，例如当前温度、队列长度或上一次操作耗时。文档中错误地将 "Gauge" 拼写为 "Gague"，这是一个字母顺序颠倒的常见笔误。

Iceberg 的 Flink 写入文档中列出了 writer 和 committer 两类算子暴露的监控指标。其中 `lastFlushDurationMs`、`lastCheckpointDurationMs`、`lastCommitDurationMs`、`elapsedSecondsSinceLastSuccessfulCommit` 这四个指标都属于 Gauge 类型（表示耗时或经过时间的瞬时值），而非 Counter 类型（累计计数）。文档中将它们的类型错误地标注为 "Gague"，这会误导用户在配置监控系统或编写告警规则时使用错误的指标类型名称，可能导致监控面板或查询语句失效。

由于 Iceberg 文档存在两份副本——主文档 `docs/flink-writes.md` 和 nightly 站点文档 `site/docs/docs/nightly/docs/flink-writes.md`，本提交同步修复了两份文件中的拼写错误，共 8 处（每份文件 4 处），保证两份文档内容一致。

## 如何达成设计目的

实现方式是纯文本替换：在两个文档文件中将所有出现的 "Gague" 替换为 "Gauge"。修改严格限定在指标类型列的表格单元格内，不影响表格结构、指标名称、描述或其它任何内容。这是一次精准的拼写订正。

## 修改详情

### docs/flink-writes.md

**修改目的**：修复主文档中 Flink writer 和 committer 指标表中 "Gauge" 类型的拼写错误。

**工作逻辑**：在 writer 子任务指标表（约 213 行）中，将 `lastFlushDurationMs` 的 Metric type 从 "Gague" 改为 "Gauge"；在 committer 算子指标表（约 227-241 行）中，将 `lastCheckpointDurationMs`、`lastCommitDurationMs`、`elapsedSecondsSinceLastSuccessfulCommit` 三个指标的 Metric type 从 "Gague" 改为 "Gauge"。共修改 4 处。

### site/docs/docs/nightly/docs/flink-writes.md

**修改目的**：同步修复 nightly 站点文档副本中相同的拼写错误，保持两份文档一致。

**工作逻辑**：与主文档对应，在 nightly 版本的同一张 writer 指标表和 committer 指标表中，将 4 处 "Gague" 改为 "Gauge"。此文件内容与 `docs/flink-writes.md` 高度对应，是站点构建的 nightly 镜像版本。

## 小结

这是一个低风险、纯文档性质的拼写修正提交。虽然改动简单，但它修正了用户面向的指标类型名称，避免了用户在配置 Flink + Iceberg 监控时因错误的指标类型名而产生困惑或告警配置失败。同步修复两份文档副本也体现了文档维护的严谨性。
