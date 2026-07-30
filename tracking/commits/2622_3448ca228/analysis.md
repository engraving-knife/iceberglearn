# 提交 2622：Site, Build: finalize 1.10.0 release with notes and update revapi and links (#13996)

## 提交信息

- **序号**：2622 / 4088
- **哈希**：3448ca228b889822b4afa97fb38bd8f1b6376627
- **短哈希**：3448ca228
- **日期**：2025-09-11 11:27:10 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Site, Build: finalize 1.10.0 release with notes and update revapi and links
- **PR/Issue**：#13996

## 总体目的

这是 Apache Iceberg 1.10.0 版本发布的"收尾"提交，将仓库中的版本相关配置、文档站点与发布说明统一更新到 1.10.0。1.10.0 是一个包含大量新功能与修复的重要版本（支持 Spark 4.0、Flink 2.0、加密密钥、行血缘、variant 类型等），发布日期为 2025-09-11。

具体目的包括：
1. 将发布说明（release notes）写入文档站点，列出 1.10.0 的全部变更（弃用、行为变更、Spec、API、Core、Arrow、Parquet、Spark、Flink、Hive、Kafka Connect、Vendor 集成、依赖升级）。
2. 将文档站点的"最新版本"从 1.9.2 切换为 1.10.0，1.9.2 降级为历史版本。
3. 更新 revapi 的 API 兼容性基线版本（从 1.9.0 升到 1.10.0），使后续开发以 1.10.0 为基准检测 API 破坏性变更。
4. 更新 bug 报告模板的版本选项，将 1.10.0 置为最新。
5. 更新 DOAP（Apache 项目描述）元数据。
6. 更新文档站点 mkdocs 配置中的版本变量（icebergVersion、flinkVersion），反映 Flink 2.0 成为默认支持版本。

## 如何达成设计目的

通过修改多个版本相关的配置与文档文件，协同完成版本切换：
- 发布说明文件 `site/docs/releases.md` 新增 1.10.0 章节，并将"## Past releases"分隔符上移，使 1.9.2 及更早版本归入历史发布。
- 站点配置 `mkdocs.yml` 更新 `icebergVersion` 与 `flinkVersion` 变量（Flink 从 1.20 升到 2.0，因 1.10.0 支持 Flink 2.0）。
- 导航配置 `nav.yml` 将"Latest"标签改为 1.10.0，1.9.2 移入"Previous"列表。
- 构建配置 `build.gradle` 将 revapi 的 `oldVersion` 从 1.9.0 改为 1.10.0。
- GitHub issue 模板将 1.10.0 加入版本选项顶部。
- `doap.rdf` 更新 release 条目。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` (+2/-1 lines)

**修改目的**：更新 bug 报告模板的版本选项。

**工作逻辑**：将版本下拉选项中 `"1.9.2 (latest release)"` 改为 `"1.10.0 (latest release)"` 并新增 `"1.9.2"` 作为非最新选项，使用户能正确报告所使用的 1.10.0 版本。

### `build.gradle` (+1/-1 line)

**修改目的**：更新 revapi API 兼容性基线版本。

**工作逻辑**：将 `revapi { oldVersion = "1.9.0" }` 改为 `oldVersion = "1.10.0"`。revapi 用于检测 API 破坏性变更，`oldVersion` 是比较基线。将其设为 1.10.0 意味着后续 main 分支开发将以 1.10.0 发布的 API 为基准，任何破坏性变更会被标记。

### `doap.rdf` (+3/-3 lines)

**修改目的**：更新 Apache 项目描述文件中的 release 条目。

**工作逻辑**：将 `<release>` 下的 `<Version>` 从 name/created/revision = 1.9.2 / 2025-07-16 / 1.9.2 改为 1.10.0 / 2025-09-11 / 1.10.0。DOAP 是 Apache 项目用于项目目录的 RDF 元数据。

### `site/docs/releases.md` (+135/-5 lines)

**修改目的**：新增 1.10.0 发布说明，调整历史版本结构。

**工作逻辑**：
- 新增完整的 `### 1.10.0 release` 章节，列出该版本所有变更，按类别组织：Deprecation/End of Support（移除 Flink 1.18 支持等）、Behavior change（Hive namespace 行为、partition stats field ids）、Spec（加密密钥、行血缘、default value 冲突等）、API、Core（大量 REST/JDBC/分区统计/DV 相关改进）、Arrow、Parquet、Spark（支持 Spark 4.0、行血缘、variant、存储分区 join 等）、Flink（支持 Flink 2.0、v2 sink 增强）、Hive、Kafka connect（CVE 修复）、Vendor integrations（AWS/GCP/Azure 多项修复与增强）、Dependencies（Parquet 1.15.1→1.16.0、Jackson 2.19.0→2.19.1、AWS SDK、Netty、Comet、httpclient 等）。每项附 PR 链接。
- 在 1.10.0 章节后新增 `## Past releases` 标题，将 1.9.2 及更早版本归入历史发布区。
- 将 1.9.2 条目的列表缩进风格统一（4 空格改为 2 空格）。

### `site/mkdocs.yml` (+3/-3 lines)

**修改目的**：更新文档站点版本变量。

**工作逻辑**：在 `extra` 配置中，将 `icebergVersion` 从 `1.9.2` 改为 `1.10.0`；`flinkVersion` 从 `1.20.0` 改为 `2.0.0`；`flinkVersionMajor` 从 `1.20` 改为 `2.0`。这些变量用于文档中展示当前推荐版本与快速开始示例。

### `site/nav.yml` (+2/-1 lines)

**修改目的**：更新导航菜单的版本标签与历史版本列表。

**工作逻辑**：将 `Latest (1.9.2)` 改为 `Latest (1.10.0)`；在 `Previous` 列表顶部新增 `1.9.2` 条目（指向 `docs/docs/1.9.2/mkdocs.yml`），使 1.9.2 作为历史版本可访问。

## 总结

本提交是 Iceberg 1.10.0 版本发布的收尾工作，全面更新了发布说明、文档站点版本、revapi 基线、bug 模板与项目元数据。1.10.0 是一个重要版本，支持 Spark 4.0 与 Flink 2.0，引入加密密钥、行血缘、variant 类型等特性。此提交使仓库与文档站点正式切换到 1.10.0 作为最新版本，为后续以 1.10.0 为基线的开发奠定基础。后续提交 2625 会进一步修复 1.10.0 发布说明中的问题。
