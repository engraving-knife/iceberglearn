# 提交 0757：docs: Update Quickstart to Hive 4.0.0 (#10325)

## 提交信息

- **序号**：0757 / 4088
- **哈希**：b752b742e4d114df07e285dba018cda3cd7c5261
- **短哈希**：b752b742e
- **日期**：2024-05-13 15:54:32 +0900
- **作者**：911432
- **提交说明**：docs: Update Quickstart to Hive 4.0.0 (#10325)
- **PR/Issue**：#10325

## 总体目的

本提交更新 Iceberg 官方文档中的 Hive 快速入门（Quickstart）页面，将其引用的 Hive 版本从旧的 beta 版本（`4.0.0-beta-1`）更新为正式发布的 Hive 4.0.0，并同步更新文档中关于 Hive 4.0.0 内置 Iceberg 版本的说明（从 `Iceberg 0.13.1` 更新为 `Iceberg 1.4.3`），以及修正 Docker Hub 标签页的 URL 排序参数。Apache Hive 4.0.0 已于 2024 年正式发布（GA），Iceberg 在 Hive 中的集成版本也随之更新，文档需要反映这一事实，避免用户按过时的 beta 版本号操作。

## 如何达成设计目的

文档修改集中在单一文件 `site/docs/hive-quickstart.md`，通过三处定点修改完成版本号与链接的更新：
1. 将环境变量 `HIVE_VERSION` 的默认值从 `4.0.0-beta-1` 改为 `4.0.0`，使用户按文档操作时拉取的是正式版镜像。
2. 将"Hive 4.0.0 已内置 Iceberg"段落中的版本号从 `4.0.0-alpha-1` 更新为 `4.0.0`，并将内置的 Iceberg 版本从 `0.13.1` 更新为 `1.4.3`，同时把指向 Hive 集成文档的锚点从 `#enabling-iceberg-support-in-hive` 调整为 `#hive-23x-hive-31x`（与新文档结构对齐）。
3. 修正 Docker Hub 标签页 URL 的查询参数，将 `?page=1&ordering=-last_updated` 改为 `?ordering=last_updated`，去掉显式的 `page=1` 并把排序从降序改为升序（`last_updated`），使链接更简洁并指向最新的标签排序。

这些修改纯属文档内容更新，不涉及任何代码逻辑，目的是让快速入门指引与当前 Hive/Iceberg 的实际发布版本保持一致。

## 修改详情

### `site/docs/hive-quickstart.md`

**修改目的**：将 Hive 快速入门文档更新到 Hive 4.0.0 正式版。

**工作逻辑**：共 3 处改动（3 行增、3 行删）：

1. **Docker Hub 标签页链接**（约第 38 行）：
   - 旧：`https://hub.docker.com/r/apache/hive/tags?page=1&ordering=-last_updated`
   - 新：`https://hub.docker.com/r/apache/hive/tags?ordering=last_updated`
   去掉 `page=1` 显式页码，并把排序参数从 `-last_updated`（按更新时间降序）改为 `last_updated`（升序）。

2. **环境变量默认值**（约第 41 行）：
   - 旧：`export HIVE_VERSION=4.0.0-beta-1`
   - 新：`export HIVE_VERSION=4.0.0`
   用户复制粘贴命令时直接使用 Hive 4.0.0 正式版镜像。

3. **"Adding Iceberg to Hive" 段落**（约第 111 行）：
   - 旧：`If you already have a Hive 4.0.0-alpha-1, or later, environment, it comes with the Iceberg 0.13.1 included. ... see [Enabling Iceberg support in Hive](docs/latest/hive.md#enabling-iceberg-support-in-hive).`
   - 新：`If you already have a Hive 4.0.0, or later, environment, it comes with the Iceberg 1.4.3 included. ... see [Enabling Iceberg support in Hive](docs/latest/hive.md#hive-23x-hive-31x).`
   将最低 Hive 版本要求从 `4.0.0-alpha-1` 提升到 `4.0.0`，内置 Iceberg 版本从 `0.13.1` 更新为 `1.4.3`，并把文档内链锚点更新为新结构下的 `#hive-23x-hive-31x`。

## 小结

- **成效**：使 Hive 快速入门文档与 Hive 4.0.0 正式版及 Iceberg 1.4.3 的实际发布状态对齐，用户按文档操作可拉取到正确的正式版镜像，并获知正确的内置 Iceberg 版本信息。链接锚点的更新保证文档内跳转有效。
- **影响范围**：仅影响网站文档页面 `site/docs/hive-quickstart.md`，不涉及任何代码、构建配置或运行时行为。对 Iceberg 库本身的功能无任何影响。
- **回迁注意事项**：此为纯文档修改，回迁到 1.4.x 分支无技术风险。但需注意：
  1. 1.4.x 分支的 `hive-quickstart.md` 内容可能与此提交前状态不同（例如 1.4.x 分支可能仍在用更旧的 Hive 版本号），cherry-pick 时需核对上下文是否匹配，可能需手动调整目标版本号。
  2. 文档中提到的"Iceberg 1.4.3"是 Hive 4.0.0 内置的 Iceberg 版本，与 Iceberg 项目自身的 1.4.x 分支版本号无直接关系，回迁时不要混淆。
  3. 文档内链锚点 `#hive-23x-hive-31x` 依赖 `hive.md` 文档的对应标题存在，回迁时需确认 1.4.x 分支的 `hive.md` 已有该锚点，否则链接会失效。
