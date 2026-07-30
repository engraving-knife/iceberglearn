# 提交 0622：为文档加入链接检查器并修复失效链接

## 提交信息

- **序号**：0622 / 4088
- **哈希**：33838d5a4870e654829e551a1a2770ff82ac94ab
- **短哈希**：33838d5a4
- **日期**：2024-03-23（Sat Mar 23 11:37:10 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：docs: Add links checker (#9965)
- **PR/Issue**：#9965

## 总体目的

Iceberg 项目的文档量非常大，分布在 `docs/`（mkdocs 源文档）、`site/`（站点素材与多引擎支持/发布说明等）、`format/`（规范文档）、`README.md` 等多个目录，且大量使用相对路径互相引用。随着文档不断演进，链接失效问题积累：

- 相对路径写错（如 `../spark-configuration.md` 在 mkdocs 构建后无法解析，因为构建后所有文档处于同一层级）；
- 外链协议缺失或过期（`http://` 该升 `https://`、`www.getdaft.io` 缺协议、`http://snowflake.com/` 应为 `https://www.snowflake.com/`）；
- 图片引用相对路径在 README 渲染时失效；
- 已删除/不可达的下载链接（如 0.7.0-incubating 的旧 jar、已下线的 Flink CDC 教程博客）仍在文档里；
- 分支名从 `master` 改为 `main` 后，旧文档引用未同步。

本提交的目的有两层：

1. **引入自动化链接检查 CI**，让后续 PR 在改动文档时自动验证所有 Markdown 链接是否可达，从源头杜绝新增失效链接。
2. **一次性清理存量失效链接**，让 CI 在引入时是绿的，并把无法通过检查的"合法但动态"链接（如带 `{{ icebergVersion }}` 模板变量的 Maven Central 下载链接）用注释显式豁免。

## 如何达成设计目的

设计者选择"工具 + 配置 + 一次性修复"的组合：

### 1. 引入 `gaurav-nelson/github-action-markdown-link-check` 这个第三方 GitHub Action

这是一个广泛使用的链接检查 Action，底层基于 `markdown-link-check` npm 包，能扫描 Markdown 中所有 `[text](url)` 与图片语法，对每个链接发 HTTP 请求或检查本地文件是否存在。

### 2. 触发条件精心收窄

- `push` 只在 `main` 分支且 `docs/**` 或 `site/**` 路径变更时触发——避免无关改动（如纯 Java 代码）浪费 CI 资源；
- `pull_request` 无路径过滤，所有 PR 都跑——保证任何改动文档的 PR 都被检查；
- `workflow_dispatch` 支持手动触发——便于排查或定期全量验证。

### 3. 用配置文件处理 Iceberg 文档的特殊性

`site/link-checker-config.json` 是 markdown-link-check 的标准配置文件，定义了两类规则：

- **`ignorePatterns`**：完全跳过某些链接的检查。Iceberg 排除了三类：
  - `https://www.linkedin.com/`——LinkedIn 反爬严格，HTTP 探测必失败，但链接对人是有效的；
  - `https://mvnrepository.com/`——同样反爬，且 Iceberg 官方用 `search.maven.org` 作为权威下载入口；
  - `^../../javadoc`——相对 javadoc 路径，构建时才生成，CI 环境下不存在。
- **`replacementPatterns`**：在检查前对链接做替换。Iceberg 把两类"渲染时才正确"的相对路径重写为 CI 可达的形式：
  - `^docs/latest/` → `{{BASEURL}}/docs/docs/`——站点上 `docs/latest/` 是版本化别名，CI 中需要映射到实际源目录 `docs/docs/`；
  - `^../../` → `{{BASEURL}}/site/docs/`——`site/` 下文档引用 `../../` 跳到 `docs/docs/` 的相对路径在 CI 直接检查时需要重写。

`{{BASEURL}}` 是 markdown-link-check 的占位符，运行时被替换为仓库根目录。

### 4. 对"合法但不可达"的链接用 HTML 注释局部豁免

对于带模板变量（如 `{{ icebergVersion }}`）的 Maven Central 下载链接、含动态路径的博客链接，CI 无法解析模板变量必然失败。作者没有改写这些链接，而是用 `markdown-link-check` 支持的 HTML 注释指令局部豁免：

- `<!-- markdown-link-check-disable-next-line -->`：跳过下一行的链接检查（用于单条链接）；
- `<!-- markdown-link-check-disable -->` ... `<!-- markdown-link-check-enable -->`：跳过区间内所有链接检查（用于 `releases.md` 的下载列表、`multi-engine-support.md` 的运行时 jar 表格这类密集动态链接）。

### 5. 一次性修复存量链接

作者按"先开 CI 发现问题、再逐文件修复"的节奏推进（提交说明里的多个子弹点 `Fix broken paths` / `Fix moar links` / `Last few` 印证了多轮迭代），修复策略包括：

- **相对路径纠正**：mkdocs 构建后所有 `docs/docs/*.md` 处于同一 URL 层级，因此 `../spark-configuration.md` 应改为 `spark-configuration.md`，`../branching.md` 改为 `branching.md`。这是本次修改体量最大的一类。
- **跨目录相对路径纠正**：`docs/docs/configuration.md` 引用规范用 `../../../spec/#format-versioning` 是错的，应为 `../../spec.md#format-versioning`（从 `docs/docs/` 到 `format/spec.md`）。
- **外链协议升级**：`http://snowflake.com/` → `https://snowflake.com/`、`http://starburst.io` → `https://starburst.io`、`http://linkedin.com/in/andreiionescu` → `https://www.linkedin.com/in/andreiionescu`、`www.getdaft.io` → `https://www.getdaft.io/`。
- **图片资源升级**：README 的 Iceberg logo 从 `https://iceberg.apache.org/docs/latest/img/Iceberg-logo.png` 换为 `https://iceberg.apache.org/assets/images/Iceberg-logo.svg`（站点资源迁移）；`format/spec.md` 的架构图从相对路径 `assets/images/iceberg-metadata.png` 换为绝对 URL `https://iceberg.apache.org/assets/images/iceberg-metadata.png`（让 spec 在 GitHub 上直接渲染时也能加载图）。
- **删除已失效内容**：删除 `blogs.md` 中已不可达的 Ververica Flink CDC 教程条目；删除 `releases.md` 末尾 0.7.0-incubating 的旧下载链接（已被 Apache 仓库下线）；删除 `README.md` 中已不存在的 roadmap 链接；删除 `site/README.md` 中已不存在的 `release` recipe 说明。
- **分支名同步**：`site/docs/how-to-release.md` 中 `https://github.com/apache/iceberg/blob/master/versions.props` 改为 `https://github.com/apache/iceberg/blob/main/site/mkdocs.yml`——既同步了 `master` → `main`，又指向了版本配置的真实新位置 `site/mkdocs.yml`。

## 修改详情

### `.github/workflows/docs-check-links.yml`（新增）

**修改目的**：建立文档链接检查的 CI 流水线。

**工作逻辑**：

- `name: Check Markdown docs links`——在 GitHub Actions UI 显示的作业名。
- 触发条件：`push`（仅 `main` 分支且 `docs/**` 或 `site/**` 变更）、`pull_request`（所有 PR）、`workflow_dispatch`（手动）。
- 单 job `markdown-link-check`，`runs-on: ubuntu-latest`，两步：
  1. `actions/checkout@v4` 检出代码；
  2. `gaurav-nelson/github-action-markdown-link-check@v1` 跑链接检查，传入 `config-file: 'site/link-checker-config.json'` 与 `use-verbose-mode: yes`（便于排查失败链接）。

### `site/link-checker-config.json`（新增）

**修改目的**：为链接检查器提供 Iceberg 特定的忽略与重写规则。

**工作逻辑**：

- `ignorePatterns`：3 条正则，跳过 LinkedIn、mvnrepository、相对 javadoc 路径。
- `replacementPatterns`：2 条重写规则，把 `docs/latest/` 与 `../../` 映射到 CI 可达的 `{{BASEURL}}/docs/docs/` 与 `{{BASEURL}}/site/docs/`。

### `README.md`

**修改目的**：修复首页 logo 与失效链接。

**工作逻辑**：

- logo URL 从 `.png` 换为 `.svg`（站点资源迁移）；
- 删除已不存在的 `[roadmap]` 链接及其引用；
- `[iceberg-spec]` URL 加末尾斜杠 `https://iceberg.apache.org/spec/`（规范化，避免 301 重定向）。

### `docs/docs/configuration.md`

**修改目的**：修复跨目录相对路径。

**工作逻辑**：

- `[Spec](../../../spec/#format-versioning)` → `[Spec](../../spec.md#format-versioning)`——从 `docs/docs/configuration.md` 到 `format/spec.md` 的正确相对路径，并补上 `.md` 扩展名与锚点。
- `[Metrics reporting](../metrics-reporting.md)` → `[Metrics reporting](metrics-reporting.md)`——同目录引用去掉 `../`。

### `docs/docs/daft.md`

**修改目的**：修复缺失协议的外链。

**工作逻辑**：`[Daft](www.getdaft.io)` → `[Daft](https://www.getdaft.io/)`——补 `https://` 协议与末尾斜杠，否则 markdown-link-check 会把它当本地文件检查。

### `docs/docs/flink-actions.md`、`flink-connector.md`、`flink-ddl.md`、`flink-queries.md`、`flink-writes.md`、`flink.md`

**修改目的**：批量修复 Flink 文档集的相对路径。

**工作逻辑**：统一把 `../xxx.md` 改为 `xxx.md`（同目录引用），如 `../maintenance.md#compact-data-files` → `maintenance.md#compact-data-files`、`../flink.md` → `flink.md`、`../branching.md` → `branching.md`、`../flink-configuration.md#write-options` → `flink-configuration.md#write-options`、`../../spec.md#identifier-field-ids` 保留（这是跨目录到 `format/spec.md` 的正确路径）。`flink.md` 还在 Flink 集群启动说明前加 `<!-- markdown-link-check-disable-next-line -->`，豁免含 `{{ icebergVersion }}` 的 Maven Central jar 链接。

### `docs/docs/spark-configuration.md`、`spark-ddl.md`、`spark-getting-started.md`、`spark-procedures.md`、`spark-queries.md`、`spark-structured-streaming.md`、`spark-writes.md`

**修改目的**：批量修复 Spark 文档集的相对路径。

**工作逻辑**：同上，统一把 `../xxx.md` 改为 `xxx.md`，覆盖 `spark-configuration.md`、`spark-ddl.md`、`spark-queries.md`、`spark-writes.md`、`spark-procedures.md`、`configuration.md`、`partitioning.md`、`api.md`、`maintenance.md` 等引用。`spark-getting-started.md` 在 Maven Central jar 链接前加 `<!-- markdown-link-check-disable-next-line -->` 豁免。

### `format/spec.md`

**修改目的**：修复架构图在 GitHub 渲染时的加载。

**工作逻辑**：`![Iceberg snapshot structure](assets/images/iceberg-metadata.png)` → `![Iceberg snapshot structure](https://iceberg.apache.org/assets/images/iceberg-metadata.png)`——相对路径在 GitHub 上查看 spec.md 时无法解析（图片在 `format/assets/`，但 GitHub 渲染时基准 URL 不同），改为绝对 URL 保证随处可加载。

### `site/README.md`

**修改目的**：移除已删除的 `release` recipe 说明。

**工作逻辑**：删除 `> [release](dev/release.sh): Release the current /docs as ICEBERG_VERSION ...` 一行，并在 `serve` recipe 说明前加 `<!-- markdown-link-check-disable-next-line -->` 豁免 `http://localhost:8000`（本地地址 CI 不可达）。

### `site/docs/blogs.md`

**修改目的**：清理失效博客链接并为 LinkedIn 链接加豁免。

**工作逻辑**：

- 删除 Ververica Flink CDC 教程条目（链接已 404）；
- 在多个 Snowflake/Substack 博客条目前加 `<!-- markdown-link-check-disable-next-line -->`——这些是 substack/snowflake 博客链接，CI 探测不稳定，作者选择豁免；
- `http://linkedin.com/in/andreiionescu` → `https://www.linkedin.com/in/andreiionescu`（协议升级 + 补 www，仍被 `ignorePatterns` 跳过检查但 URL 本身更规范）。

### `site/docs/multi-engine-support.md`

**修改目的**：为含 `{{ icebergVersion }}` 的运行时 jar 表格加区间豁免。

**工作逻辑**：在 Spark / Flink / Hive 三张运行时 jar 表格前后分别加 `<!-- markdown-link-check-disable -->` 与 `<!-- markdown-link-check-enable -->`，因为表格里的 `search.maven.org/remotecontent?filepath=.../{{ icebergVersion }}/...` 链接含模板变量，CI 无法解析。

### `site/docs/releases.md`

**修改目的**：为下载链接表格加区间豁免并删除失效旧版本下载链接。

**工作逻辑**：

- 下载列表前后加 `<!-- markdown-link-check-disable -->` / `<!-- markdown-link-check-enable -->`（含 `{{ icebergVersion }}`）；
- 删除 0.7.0-incubating 的 source tar.gz 与 Spark 2.4 runtime jar 下载链接（Apache 孵化器历史发布已从主下载站下线，链接 404）。

### `site/docs/spark-quickstart.md`

**修改目的**：为含模板变量的 runtime jar 链接加豁免。

**工作逻辑**：在 `[spark-runtime-jar]: https://search.maven.org/...{{ icebergVersion }}...` 引用定义前加 `<!-- markdown-link-check-disable-next-line -->`。

### `site/docs/vendors.md`

**修改目的**：修复厂商外链协议并为 Snowflake 加豁免。

**工作逻辑**：

- `http://snowflake.com/` → `https://snowflake.com/`（标题链接）；
- Snowflake 段落拆分：把原本"标题 + 段落"粘在一起的结构拆为"标题（豁免）+ 段落（豁免）"两块，分别在前面加 `<!-- markdown-link-check-disable-next-line -->`——因为 Snowflake 文档链接 CI 探测不稳定；
- `http://starburst.io` → `https://starburst.io`（协议升级）。

### `site/docs/how-to-release.md`

**修改目的**：同步分支名与版本配置文件位置。

**工作逻辑**：`https://github.com/apache/iceberg/blob/master/versions.props` → `https://github.com/apache/iceberg/blob/main/site/mkdocs.yml`——既把 `master` 改为 `main`，又把版本配置指向新位置 `site/mkdocs.yml`（`versions.props` 已不再是 nessie 版本的来源）。

## 小结

本提交为 Iceberg 文档引入了基于 `markdown-link-check` 的 CI 链接检查流水线，并一次性修复了约 26 个文件中的存量链接问题，是文档质量基础设施建设的重要一步。

**影响范围**：

- 新增 CI 工作流会在所有 PR 与 main 推送上运行，未来任何引入失效链接的 PR 都会被 CI 拦截。
- 配置文件 `site/link-checker-config.json` 后续可继续扩充 `ignorePatterns` / `replacementPatterns` 以应对更多特殊情况。
- 文档相对路径统一为"同级引用不带 `../`"，符合 mkdocs 构建后的实际 URL 层级，提升文档可维护性。
- 删除了若干已下线的旧版本下载链接与失效博客条目，避免用户踩坑。

**回迁到 1.4.x 的注意事项**：

- 回迁非常安全，纯文档与 CI 配置改动，不动任何运行时代码。
- 建议把整个 PR 作为一个整体回迁——只回迁 CI 工作流不回修复，会让 CI 在 1.4.x 上一启动就全红；只回修复不回 CI，则失去回迁的意义。
- 注意 1.4.x 分支的文档目录结构应与 main 一致（`docs/docs/`、`site/docs/`、`format/`），否则 `replacementPatterns` 中的 `{{BASEURL}}/docs/docs/` 等映射会失效，需要根据 1.4.x 实际结构调整。
- 若 1.4.x 已有自己的版本变量占位符（不一定叫 `{{ icebergVersion }}`），需检查豁免注释是否覆盖到位。
- `master` → `main` 的引用同步要确认 1.4.x 仓库默认分支名（若 1.4.x 仍以 `master` 为主分支则需保留 `master`）。
- `format/spec.md` 的图片改绝对 URL 依赖 `iceberg.apache.org` 站点资源可用，离线构建时图片不会加载，但 spec 文本不受影响。
