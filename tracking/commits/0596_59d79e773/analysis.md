# 提交 0596：Docs: document view properties (#9961)

## 提交信息

- **序号**：0596 / 4088
- **哈希**：59d79e773d0bc2061d8ced4cde6917eb4428dec6
- **短哈希**：59d79e773
- **日期**：Fri Mar 15 09:50:25 2024 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Docs: document view properties (#9961)
- **PR/Issue**：#9961

## 总体目的

本提交为 Apache Iceberg 文档站点新增"视图配置（View Configuration）"页面，正式对外文档化 Iceberg 视图（View）所支持的全部属性。

背景动机：

1. Iceberg 在 main 分支上已经引入了对 SQL 视图（Views）的支持（视图元数据、版本化、替换/重写等），但官网文档一直没有专门页面集中介绍视图可配置属性，用户只能通过翻阅源码或 Javadoc 自行摸索。
2. 与表（Table）已有的 `configuration.md` 类似，视图同样存在元数据压缩、版本保留、提交重试等可调参数，缺乏统一文档会影响用户正确使用视图特性。
3. 该 PR 补齐了文档缺口，使视图功能在用户侧具备完整可发现性，配合导航菜单（mkdocs.yml）的接入，让"视图配置"成为文档站的一等公民。

## 如何达成设计目的

设计思路非常直接：

1. 新增一个 Markdown 文档 `docs/docs/view-configuration.md`，使用与表配置页一致的 front matter（`title: "Configuration"`）和表格化结构，按"视图属性"和"视图行为属性"两类列出全部属性、默认值与描述。
2. 在 `docs/mkdocs.yml` 的 `nav` 导航树中，在 `schemas.md` 之后、`Spark` 之前新增 `Views` 分组并挂入 `view-configuration.md`，使新页面在文档站点侧边栏可见。
3. 属性条目直接对应当时代码中 `ViewProperties` / `ViewBehaviorProperties` 类中定义的常量，确保文档与实现一致。

这种"新建一个内容文件 + 在 mkdocs.yml 注册导航条目"的方式是 Iceberg 文档站的通用增页模式，无需改动构建脚本或主题。

## 修改详情

### `docs/docs/view-configuration.md`

**修改目的**：新建视图配置文档页，集中说明视图相关属性。

**工作逻辑**：文件以 Apache License 头和 `# Configuration` 标题开头，包含两节：

1. **View properties（视图属性）** —— 影响视图元数据与版本管理的静态属性，表格列出三项：
   - `write.metadata.compression-codec`（默认 `gzip`）：元数据压缩编解码器，可选 `none` / `gzip`。
   - `version.history.num-entries`（默认 `10`）：控制保留的 `versions` 数量。
   - `replace.drop-dialect.allowed`（默认 `false`）：控制 `replace` 操作中是否允许丢弃某种 SQL 方言。

2. **View behavior properties（视图行为属性）** —— 影响提交重试行为的运行时属性，表格列出四项：
   - `commit.retry.num-retries`（默认 `4`）：提交失败前重试次数。
   - `commit.retry.min-wait-ms`（默认 `100`）：重试前最小等待毫秒数。
   - `commit.retry.max-wait-ms`（默认 `60000` / 1 分钟）：重试前最大等待毫秒数。
   - `commit.retry.total-timeout-ms`（默认 `1800000` / 30 分钟）：提交重试总超时毫秒数。

这些条目与 `ViewProperties`、`ViewBehaviorProperties` 中的 key、默认值一一对应，是文档作为"对外契约"的权威来源。

### `docs/mkdocs.yml`

**修改目的**：将新建的视图配置页注册到文档站导航树，使其在站点侧边栏可见。

**工作逻辑**：在 `nav:` 列表中，紧跟 `schemas.md` 之后插入：

```yaml
  - Views:
    - view-configuration.md
```

这新增了一个顶层 `Views` 分组（仅含一个子页面），位于 `Configuration` 大类下表/Schema 配置之后、Spark 文档之前，位置符合"先讲存储模型配置、再讲引擎集成"的文档组织惯例。

## 小结

- **成效**：补齐了 Iceberg 视图功能的文档缺口，用户现在可在文档站直接查阅视图的全部可配置属性及默认值，无需翻阅源码。
- **影响范围**：纯文档变更，无任何代码或行为改动；不影响构建产物（除站点 HTML 外）和运行时逻辑，零回退风险。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支通常不包含完整的 Views 实现（视图特性在更高版本才 GA）。若 1.4.x 已回迁了部分视图相关代码，则可一并回迁此文档；若 1.4.x 完全没有视图支持，回迁此文档反而会暴露尚未实现的属性，建议先确认 1.4.x 中 `ViewProperties` / `ViewBehaviorProperties` 是否存在再决定。回迁时需同步确认 `docs/mkdocs.yml` 中 `nav` 上下文与目标分支一致，避免导航错位。
