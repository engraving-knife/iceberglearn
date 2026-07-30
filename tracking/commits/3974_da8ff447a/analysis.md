# 提交 3974：Docs: Document nightly snapshots (#16544)

## 提交信息

- **序号**：3974 / 4088
- **哈希**：da8ff447a23447733ea6231625cc4d468245b090
- **短哈希**：da8ff447a
- **日期**：2026-07-01 23:51:42 -0700
- **作者**：Vova Kolmakov
- **提交说明**：Docs: Document nightly snapshots (#16544)
- **PR/Issue**：#16544

## 总体目的

本提交为 Iceberg 网站新增了"开发者快照测试"（Developer Snapshot Testing）文档页面，正式文档化 Iceberg 每晚发布的开发快照（nightly snapshots）的使用方式。此前，多引擎支持页面仅简单提及快照仓库的存在，缺乏详细的使用指南和警告。

文档明确指出 nightly snapshots 是未发布的开发制品，仅供活跃开发者和引擎维护者测试使用，**不应用于生产环境**。文档提供了 Gradle、Maven、sbt 和 Spark 的依赖配置示例，并说明了快照版本号的计算规则。

## 如何达成设计目的

1. 新增 `site/docs/developer-snapshot-testing.md` 页面，包含快照版本说明、使用警告和多构建工具的依赖配置示例。
2. 更新 `site/docs/multi-engine-support.md`，将指向快照仓库的简单链接替换为指向新文档页面的链接。
3. 在导航配置（`site/mkdocs-dev.yml` 和 `site/nav.yml`）中注册新页面。

## 修改详情

### `site/docs/developer-snapshot-testing.md` (+112/-0 lines, 新文件)

**修改目的**：新增开发者快照测试文档。

**工作逻辑**：
- 使用警告块明确标注：仅供 Iceberg 开发者使用，不是正式发布，可能随时变更或损坏，不得用于生产。
- 说明快照每天 00:00 UTC 发布，版本号通过递增最新发布的 minor 版本并将 patch 重置为 0 来计算（如当前 1.x.y → 下一个快照为 1.(x+1).0-SNAPSHOT）。使用 Jinja2 模板动态计算版本号。
- 提供四种构建工具的依赖配置示例：Gradle、Maven、sbt、Spark shell，均标注 "development only"。
- 引用 ASF 发布政策，说明未发布制品是开发者资源。

### `site/docs/multi-engine-support.md` (+1/-1 lines)

**修改目的**：更新快照引用链接。

**工作逻辑**：将 `a daily snapshot is published in the [Apache snapshot repository](...)` 改为 `[developer snapshot testing](developer-snapshot-testing.md) is available for active developers and engine maintainers testing ongoing development`。

### `site/mkdocs-dev.yml` 和 `site/nav.yml` (+1/-0 lines each)

**修改目的**：在导航中注册新文档页面。

## 总结

本提交正式文档化了 Iceberg nightly snapshots 的使用方式，为引擎维护者和活跃开发者提供了清晰的依赖配置指南，同时通过明确的警告防止生产环境误用。文档遵循 ASF 发布政策，正确区分了开发制品和正式发布。
