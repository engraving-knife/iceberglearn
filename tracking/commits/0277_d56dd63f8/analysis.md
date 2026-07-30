# 提交 0277：Doc: Adding documentation for flink iceberg connector for version 1.18 (#9304)

## 提交信息

- **序号**：0277 / 4088
- **哈希**：d56dd63f8e26fe08025933f33176599703013844
- **短哈希**：d56dd63f8
- **日期**：2023-12-14 15:32:21 -0800
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Doc: Adding documentation for flink iceberg connector for version 1.18 (#9304)
- **PR/Issue**：#9304

## 总体目的

本提交是纯粹的文档维护类改动，目的是把 Iceberg 官方文档站点的"Flink 多引擎支持矩阵"更新到包含 Flink 1.18 版本，并同步调整旧版本的生命周期状态标签，使文档反映 Iceberg 1.5.x 发布周期内 Flink 引擎支持的最新事实。

Iceberg 通过 `iceberg-flink-runtime-<flinkVersion>` 系列打包模块为各 Flink 大版本提供独立的运行时 jar。文档 `site/docs/multi-engine-support.md` 以一张表的形式向用户展示每个 Flink 大版本的状态（End of Life / Deprecated / Maintained）、对应的 Iceberg 起始版本与最高支持版本，以及指向 Maven Central 上对应 jar 包的下载链接。随着 Iceberg 1.5.0 发版，社区正式新增了对 Flink 1.18 的支持，但文档并未及时跟进——本提交正是补上这一缺口。

同时，新版本上线意味着旧版本应被降级：Flink 1.15 此前已标记为 Deprecated（弃用），随着时间推移本提交将其降为 End of Life（生命周期终结）；Flink 1.16 从 Maintained 降为 Deprecated，为新版本让出"维护中"的位置。这种"新版本加入、旧版本降级"的滚动更新是文档维护的常规动作，确保用户看到的支持状态与社区实际维护策略一致，帮助用户做出升级决策。

从更宏观的背景看，文档页头部还有一句"Users should continuously upgrade their Flink version to stay up-to-date"（用户应持续升级 Flink 版本以保持最新），这张表正是该建议的事实依据：用户据此判断当前使用的 Flink 版本是否仍处于 Maintained 状态，从而决定是否需要升级。

## 如何达成设计目的

提交仅修改一个 Markdown 文件 `site/docs/multi-engine-support.md`，对 Flink 版本支持表做了三处行级调整：

1. 新增一行 Flink 1.18 的条目，状态为 Maintained，Iceberg 起始版本为 1.5.0，下载链接指向 `iceberg-flink-runtime-1.18` 在 Maven Central 上的对应路径。
2. 把 Flink 1.15 的状态从 Deprecated 改为 End of Life。
3. 把 Flink 1.16 的状态从 Maintained 改为 Deprecated。

下载链接使用了站点模板变量 `{{ icebergVersion }}`，这是 Iceberg 文档站点（基于 Jekyll/Hugo 类静态生成器）在构建时替换的占位符，会被替换为当前文档对应的 Iceberg 版本号，从而保证链接始终指向与当前文档版本匹配的 jar 包。

## 修改详情

### `site/docs/multi-engine-support.md`

**修改目的**：更新 Flink 引擎支持矩阵，新增 1.18 条目，并调整 1.15/1.16 的生命周期状态。

**工作逻辑**：

文件只改动了表格的四行（1.15、1.16、1.17、新增 1.18），具体如下：

- **1.15 行**：状态由 `Deprecated` 改为 `End of Life`。表示 Iceberg 社区不再为该 Flink 版本提供任何维护（即便是安全修复），用户应尽快升级。该行的起始版本 `0.14.0`、最高版本占位符 `{{ icebergVersion }}` 与下载链接保持不变——虽然状态降级，但历史 jar 仍可在 Maven Central 上获取，链接依然有效。
- **1.16 行**：状态由 `Maintained` 改为 `Deprecated`。意味着该版本仍可使用但不再积极开发新功能，处于"弃用过渡期"，用户应规划迁移到更新的版本。
- **1.17 行**：保持 `Maintained` 不变，作为另一仍在维护的版本。本提交未改动此行，但它在 diff 上下文中出现是因为新增行紧随其后。
- **1.18 行（新增）**：状态 `Maintained`，起始 Iceberg 版本 `1.5.0`，最高版本 `{{ icebergVersion }}`，下载链接 `https://search.maven.org/remotecontent?filepath=org/apache/iceberg/iceberg-flink-runtime-1.18/{{ icebergVersion }}/iceberg-flink-runtime-1.18-{{ icebergVersion }}.jar`。这表明从 Iceberg 1.5.0 起官方支持 Flink 1.18，用户可在 Maven Central 下载对应 jar。

表格末尾的脚注 `[3] Flink 1.12 shares the same runtime jar iceberg-flink-runtime with Flink 1.11 before Iceberg 0.13.0` 未受影响，仅作为历史说明保留。

整张表呈现"自下而上"的版本递进：越靠下的 Flink 版本越新、状态越活跃（Maintained），越靠上的越旧、状态越趋于 EOL。本提交通过新增 1.18 并将 1.15/1.16 各降一级，让这张表重新对齐"两个 Maintained + 一个 Deprecated + 历史 EOL"的健康分布。

## 小结

本提交是一次低风险、纯文档的滚动更新：为 Iceberg 1.5.0 引入的 Flink 1.18 支持补上官方文档，并同步把 Flink 1.15 降为 EOL、1.16 降为 Deprecated。改动虽小，但直接面向用户决策——用户依赖这张表判断所用的 Flink 版本是否仍受支持、应从哪里下载匹配的运行时 jar。及时维护该矩阵是社区支持多引擎承诺的体现，避免文档与实际发版脱节。这类文档更新通常随每个 Iceberg 小版本发布一同进行，是项目运维的常规组成部分。
