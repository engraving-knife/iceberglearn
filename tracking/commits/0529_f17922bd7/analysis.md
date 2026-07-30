# 提交 0529：docs: Fix listings on Release page / Update Multi-engine support

## 提交信息

- **序号**：0529 / 4088
- **哈希**：f17922bd785743ae86f4c6a947d14eed05adbf3f
- **短哈希**：f17922bd7
- **日期**：2024-02-22 16:41:28 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：docs: Fix listings on Release page / Update Multi-engine support
- **PR/Issue**：#9775

## 总体目的

本提交是一个**纯文档修复**，做两件事：

1. **修复 Release 页面（`site/docs/releases.md`）的列表渲染问题**：发版说明里大量历史版本的变更条目使用了错误的 markdown 列表缩进（2 空格而非 4 空格），导致这些条目在 Docusaurus 站点上没有被正确渲染为“父分类下的子条目”，而是被解析为同级段落或丢失嵌套关系。这造成 Release 页面“Past releases”区域条目结构混乱、可读性差。本提交把 668 行列表项的缩进从 2 空格统一改为 4 空格，恢复正确的嵌套层级，同时去掉若干条目中冗余的 `Core:` 前缀（这些条目本身已在 `* Core` 父节点下，前缀重复）。
2. **更新多引擎支持页（`site/docs/multi-engine-support.md`）的 Spark 表格**：在 Apache Spark 引擎支持表中新增 Spark 3.5 一行，标注其生命周期阶段为 `Maintained`，初始 Iceberg 支持版本为 `1.4.0`，并提供 `iceberg-spark-runtime-3.5_2.12` 的 Maven Central 下载链接。这让用户能在多引擎支持页直接看到 Spark 3.5 的官方支持状态。

### 背景

- 1.4.0 版本已经新增了对 Spark 3.5 的支持（`Added support for Spark 3.5` 出现在 1.4.0 release notes），但多引擎支持页的表格此前只列到 Spark 3.4，缺失 Spark 3.5 一行——文档与代码支持状态不一致。
- Release 页面的列表缩进问题是历史遗留：markdown 严格模式下，2 空格缩进不足以让 `-` 子项被识别为父级 `*` 项的子项，需要 4 空格。Docusaurus 默认的 markdown 解析器对这一点比较严格。

## 如何达成设计目的

### releases.md 的列表缩进修复

策略是**机械地把所有 `* <section>` 下的 `  - `（2 空格 + 短横线）改为 `    - `（4 空格 + 短横线），把 `  * ` 改为 `    * `**，使子条目达到 markdown 规范要求的 4 空格缩进，从而被正确解析为嵌套列表项。具体改动模式：

- `* Core` / `* API` / `* Spark` / `* Flink` / `* AWS` / `* Hive` 等父分类（顶级列表项）保持不变（仍是 `* ` 顶格）。
- 这些父分类下的变更条目（次级列表项）从 `  - ` 改为 `    - `。
- 次级列表项下的更深层条目（如 `Added support for Spark 3.5` 下的 4 条说明）从原本错误的 2 空格同级缩进改为正确的 8 空格 `        - `，使其真正成为 `Added support for Spark 3.5` 的子项。

同时，对 7 条原本以 `Core: <内容>` 开头的条目去掉 `Core:` 前缀（它们已经在 `* Core` 父节点下，前缀是冗余的）。这 7 条集中在 1.4.2、1.4.1、1.3.x、1.2.x 等历史版本段落。

### multi-engine-support.md 的 Spark 3.5 新增

在 Apache Spark 表格的 Spark 3.4 行之后新增一行：
```
| 3.5 | Maintained | 1.4.0 | {{ icebergVersion }} | [iceberg-spark-runtime-3.5_2.12](https://search.maven.org/remotecontent?filepath=org/apache/iceberg/iceberg-spark-runtime-3.5_2.12/{{ icebergVersion }}/iceberg-spark-runtime-3.5_2.12-{{ icebergVersion }}.jar) |
```
- `Lifecycle Stage = Maintained`：Spark 3.5 处于活跃维护状态。
- `Initial Iceberg Support = 1.4.0`：Iceberg 1.4.0 首次支持 Spark 3.5。
- `Latest Iceberg Support = {{ icebergVersion }}`：使用 Docusaurus 模板变量，自动指向当前站点配置的最新版本。
- `Latest Runtime Jar`：指向 Maven Central 上 `iceberg-spark-runtime-3.5_2.12` 的下载链接，链接中也用 `{{ icebergVersion }}` 模板变量。

## 修改详情

### `site/docs/releases.md`

**修改目的**：修复历史发版说明的列表嵌套渲染，让“Past releases”区域的变更条目按 `* <section>` → `    - <change>` 的层级正确显示；同时去除冗余 `Core:` 前缀。

**工作逻辑**：

- 共修改 668 行（334 行删除 + 334 行新增），实际是逐行重写缩进。改动覆盖从 1.4.2 一直到 0.7.x 等多个历史版本段落。
- **缩进规则**：
  - 顶级分类（如 `* Core`、`* API`、`* Spark`、`* Flink`、`* AWS`、`* Hive`、`* Parquet`、`* ORC`、`* Build` 等）保持顶格 `* `。
  - 这些分类下的变更条目从 `  - ` 改为 `    - `（4 空格）。
  - `  * ` 形式的次级条目（部分老版本段落使用 `*` 而非 `-`）从 `  * ` 改为 `    * `（4 空格）。
- **Spark 3.5 子项重嵌套**：1.4.0 段落中 `Added support for Spark 3.5` 下的 4 条说明（Code for DELETE/UPDATE/MERGE…、Support for WHEN NOT MATCHED BY SOURCE…、Column pruning…、Ability to request a bigger advisory partition size…）原本与 `Added support for Spark 3.5` 同级（都是 `  - `），改为 8 空格 `        - `，使其成为 `Added support for Spark 3.5` 的真正子项，渲染为缩进的子列表。
- **去除冗余 `Core:` 前缀**：以下 7 条变更条目去掉 `Core:` 前缀（因父节点已是 `* Core`）：
  - 1.4.2: `Core: Ignore split offsets array when split offset is past file length` → `Ignore split offsets array when split offset is past file length`
  - 1.4.1: `Core: Do not use a lazy split offset list in manifests`、`Core: Ignore split offsets when the last split offset is past the file length`
  - 1.3.1: `Core: Fix snapshot log with intermediate transaction snapshots`、`Core: Fix exception handling in BaseTaskWriter`、`Core: Support deleting tables without metadata files`、`Core: Add CommitStateUnknownException handling to REST`
- 内容本身（链接、PR 号、说明文字）除上述前缀去除外无其他变化。

### `site/docs/multi-engine-support.md`

**修改目的**：在 Apache Spark 引擎支持表中新增 Spark 3.5 一行，使多引擎支持页与 1.4.0 起对 Spark 3.5 的实际支持状态一致。

**工作逻辑**：

- 在 Spark 表格的 `3.4` 行之后插入 `3.5` 行，字段为：`Maintained` / `1.4.0` / `{{ icebergVersion }}` / 指向 Maven Central `iceberg-spark-runtime-3.5_2.12` 的链接。
- 该表格被 Docusaurus 渲染为 HTML 表格，用户在 https://iceberg.apache.org/multi-engine-support/ 页面能看到 Spark 3.5 的支持状态与下载入口。
- `{{ icebergVersion }}` 是站点构建时替换的模板变量，保证链接始终指向最新发布版本的 jar。

## 小结

- **成效**：本提交修复了 Release 页面长期存在的列表渲染问题（668 行缩进修正 + 7 条冗余前缀去除 + Spark 3.5 子项重嵌套），让历史发版说明以正确的层级结构展示；同时在多引擎支持页补全 Spark 3.5 行，消除文档与代码支持状态的偏差。两者都是面向最终用户的文档质量改进。
- **影响范围**：仅影响 `site/docs/` 下的两个 markdown 文件，不涉及任何代码、构建或运行时行为。`releases.md` 的改动虽大（668 行）但本质是机械化的缩进重排，无语义变化（除前缀去除与 Spark 3.5 子项重嵌套外）。`multi-engine-support.md` 仅新增 1 行表格行。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯文档改动，无依赖、无风险，可直接 cherry-pick。
  2. 注意 `releases.md` 中 `{{ icebergVersion }}` 模板变量由站点构建系统注入，1.4.x 的站点配置若不同，渲染结果会按 1.4.x 的最新版本展示——这是预期行为，无需特殊处理。
  3. `multi-engine-support.md` 中 Spark 3.5 标注的 `Initial Iceberg Support = 1.4.0` 与 1.4.x 分支一致（1.4.0 是首个支持 Spark 3.5 的版本），回迁后语义正确。
  4. 由于 `releases.md` 改动行数多但语义机械，回迁时若遇冲突（例如 1.4.x 已有其他 releases.md 改动），优先以 main 分支的缩进规范为准重新格式化即可。
