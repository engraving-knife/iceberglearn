# 提交 0694：更新文档中的 Flink 版本信息

## 提交信息
- **序号**：0694 / 4088
- **哈希**：0a4e6e6cfbea532745c8bc1523d221c72768506c
- **短哈希**：0a4e6e6cf
- **日期**：2024-04-16 22:11:54 -0700
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Docs: Updates flink versioning information in our docs (#10155)
- **PR/Issue**：#10155

## 总体目的

本提交是对前面 0691-0693 提交（Flink 1.19 引入、Flink 1.16 移除）的文档同步更新。当代码层面完成了版本支持矩阵的调整后，面向用户的文档也需要随之更新，确保用户能看到准确的 Flink 版本支持状态和正确的示例链接。

具体要解决两个问题：一是 `site/docs/multi-engine-support.md` 中的 Flink 版本支持矩阵表需要反映 1.16 已 End of Life、1.17 降级为 Deprecated、1.19 新增为 Maintained 的新状态；二是 `docs/docs/flink-writes.md` 中的单元测试示例链接还指向已删除的 `flink/v1.16/` 路径，需要更新为仍然存在的 `flink/v1.17/` 路径。

## 如何达成设计目的

通过对两个文档文件做精准的小幅修改来完成。修改量很小（4 行增、3 行删），但每处修改都有明确的对应关系：版本矩阵表与代码中的实际版本支持一一对应，示例链接与仓库中实际存在的文件路径一一对应。

## 修改详情

### `docs/docs/flink-writes.md`
**修改目的**：修复指向已删除的 v1.16 测试文件的链接。
**工作逻辑**：将"more example could be found in this unit test"链接中的路径从 `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSink.java` 改为 `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSink.java`。因为 0693 提交已删除 `flink/v1.16/` 目录，原链接会 404；改为 v1.17 是因为 v1.17 是当前仍维护的最接近的低版本，且该测试文件在 v1.17 中确实存在。

### `site/docs/multi-engine-support.md`
**修改目的**：更新 Flink 版本支持矩阵表，反映 1.16 EOL、1.17 降级、1.19 新增。
**工作逻辑**：在 Flink 版本支持表中做以下调整：
- **1.16**：状态从 `Deprecated` 改为 `End of Life`；最后支持版本从 `{{ icebergVersion }}` 改为固定的 `1.5.0`（即 1.16 的最后一个 Iceberg 发布版本），链接也改为指向 1.5.0 的固定 jar。
- **1.17**：状态从 `Maintained` 改为 `Deprecated`。
- **1.18**：保持 `Maintained` 不变。
- **1.19**：新增一行，状态为 `Maintained`，起始版本 `1.6.0`，最后版本 `{{ icebergVersion }}`，链接指向 `iceberg-flink-runtime-1.19`。

这样版本矩阵从 `1.16(Deprecated) / 1.17(Maintained) / 1.18(Maintained)` 变为 `1.16(EOL) / 1.17(Deprecated) / 1.18(Maintained) / 1.19(Maintained)`，与代码层面的版本支持完全一致。

## 小结
- **成效**：成功达成目的。文档与代码实现保持一致，用户可以看到正确的版本支持信息和无死链的示例。
- **影响范围**：仅影响文档，不涉及任何代码或构建配置变更。
- **回迁到 1.4.x 的注意事项**：回迁时需确认 1.4.x 分支的实际 Flink 版本支持情况。如果 1.4.x 仍支持 Flink 1.16，则不应回迁此文档更新，否则会误导用户。文档中的 `{{ icebergVersion }}` 是 Jekyll/Hugo 模板变量，回迁时需确保站点构建系统能正确解析。1.16 的最后支持版本 `1.5.0` 是固定值，回迁时无需调整。
