# 提交 1475：docs: 1.7.1 Release notes (#11717)

## 提交信息

- **序号**：1475 / 4088
- **哈希**：2b2efd78f622df971e96ec1b72fe64fca9b4f7c2
- **短哈希**：2b2efd78f
- **日期**：2024-12-09（Mon Dec 9 10:35:56 2024 -0800）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：docs: 1.7.1 Release notes (#11717)
- **PR/Issue**：#11717

## 总体目的

Apache Iceberg 1.7.1 已于 2024 年 12 月 6 日正式发布（属于 1.7.x 维护系列的 bugfix release）。本提交为官方文档站点（基于 mkdocs）补全 1.7.1 的发布说明页面，并同步调整站点导航，让访问者能在文档网站看到 1.7.1 的版本入口、Release Highlights 与变更清单。

具体目标：
1. 在 `site/docs/releases.md` 中追加 1.7.1 章节并调整 "Past releases" 标题的位置，使 1.7.1 排在最前面，1.7.0 顺次下移；
2. 在 `site/mkdocs.yml` 把全局变量 `icebergVersion` 从 `1.7.0` 改为 `1.7.1`，让首页/横幅等位置自动显示最新版本号；
3. 在 `site/nav.yml` 顶部文档版本切换列表中插入 `1.7.1` 入口。

同时本提交给 1.7.0 章节补充了发布日期（"November 8, 2024"），与 1.7.1 的格式保持一致。

## 如何达成设计目的

通过修改三个 mkdocs 配置/内容文件实现：
- `site/docs/releases.md`：手工维护的发布说明 Markdown 文件，按版本倒序列出。新增 1.7.1 章节，把 `## Past releases` 标题下移到 1.7.0 之前，并补全 1.7.0 的发布日期。
- `site/mkdocs.yml`：mkdocs 主配置，定义了 `extra.icebergVersion` 变量供模板引用。
- `site/nav.yml`：导航配置，定义文档版本切换器的版本列表，按从新到旧顺序排列。

## 修改详情

### `site/docs/releases.md`

**修改目的**：补充 1.7.1 发布说明，调整历史版本排序。

**工作逻辑**：

1. 在 Maven 依赖示例之后、原 1.7.0 章节之前，新增 1.7.1 章节：

```markdown
### 1.7.1 release

Apache Iceberg 1.7.1 was released on December 6, 2024.

The 1.7.1 release contains bug fixes and new features. For full release notes visit [Github](https://github.com/apache/iceberg/releases/tag/apache-iceberg-1.7.1)

* Core
    - Revert "Use encoding/decoding methods for namespaces and deprecate Splitter/Joiner" ([\#11574](...))
    - Revert "Update TableMetadataParser to ensure all streams closed" ([\#11621](...))
* Azure
    - Fix ADLSLocation file parsing ([\#11395](...))
    - Support WASB scheme in ADLSFileIO ([\#11504](...))
* Spark
    - Fix NotSerializableException when migrating Spark tables ([\#11157](...))
    - Fix changelog table bug for start time older than current snapshot ([\#11564](...))
* Kafka Connect
    - Fix Hadoop dependency exclusion ([\#11516](...))
```

可以看到 1.7.1 包含 2 项 Core 的回退（Revert）、2 项 Azure 修复、2 项 Spark 修复、1 项 Kafka Connect 修复。其中两个 Revert 表明 1.7.0 引入的某些变更在 1.7.1 中被撤回——这是 bugfix release 的常见操作，目的是快速纠正上一版引入的问题。

2. 把 `## Past releases` 标题从原本位于 1.6.0 章节之后的位置，上移到 1.7.0 之前，即把 1.7.1 作为"当前版本"，而把 1.7.0 及更早版本归入"Past releases"组。

3. 同时给原 1.7.0 章节补充 "Apache Iceberg 1.7.0 was released on November 8, 2024." 一行，与 1.7.1 章节的格式对齐。

### `site/mkdocs.yml`

**修改目的**：把全局 `icebergVersion` 变量从 `1.7.0` 改为 `1.7.1`。

```yaml
extra:
-  icebergVersion: '1.7.0'
+  icebergVersion: '1.7.1'
```

mkdocs 模板中可以通过 `config.extra.icebergVersion` 引用此变量，例如在首页展示"最新版本"徽章、或在安装说明中给出推荐版本号。改一行即可让全站统一显示 1.7.1。

### `site/nav.yml`

**修改目的**：在文档版本切换器中插入 1.7.1 入口。

```yaml
   - Docs:
     - nightly: '!include docs/docs/nightly/mkdocs.yml'
     - latest: '!include docs/docs/latest/mkdocs.yml'
+    - 1.7.1: '!include docs/docs/1.7.1/mkdocs.yml'
     - 1.7.0: '!include docs/docs/1.7.0/mkdocs.yml'
```

`nav.yml` 通过 `!include` 引入各版本独立的 mkdocs 配置。新增 1.7.1 行后，文档站点的顶部导航会出现 "1.7.1" 选项，链接到对应的子站点。前置条件是 `docs/docs/1.7.1/mkdocs.yml` 文件已存在（通常由发布脚本从 latest 拷贝生成）。

## 小结

- **成效**：文档站点完整呈现 1.7.1 发布说明，包括 7 项主要变更点（2 Core Revert + 2 Azure + 2 Spark + 1 Kafka Connect 修复）；版本切换器、首页版本徽章也同步指向 1.7.1。
- **影响范围**：3 个文档/配置文件，共新增 24 行、删除 3 行，不涉及任何代码。
- **回迁到 1.4.x 的注意事项**：这是 1.7.1 发布的文档记录，对 1.4.x 没有直接关系——1.4.x 是更早的维护分支（早于 1.5/1.6/1.7），其发布说明归在自己的版本号下。**无需回迁**。1.4.x 自己的发布说明由 1.4.x 当时的发布流程单独维护。
