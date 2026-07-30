# 提交 1795：Docs: Remove Hive runtime jar link from latest release (#12422)

## 提交信息

- **序号**：1795 / 4088
- **哈希**：0f38b5bd12ad358d85e642d8d92dabb23d9e54cd
- **短哈希**：0f38b5bd1
- **日期**：2025-02-28 08:56:00 +0100
- **作者**：Manu Zhang
- **提交说明**：Docs: Remove Hive runtime jar link from latest release (#12422)
- **PR/Issue**：#12422

## 总体目的

此提交用于从 Iceberg 发布文档 `site/docs/releases.md` 中移除 “Hive runtime Jar” 的下载链接及对应的使用说明。

Iceberg 的 Hive 集成方式发生了变化：原先为 Hive 2/3 提供独立的 `iceberg-hive-runtime` jar 供用户通过 `ADD JAR` 加载，但随着 Hive 4.0.0 起内置 Iceberg 支持，且 Hive 2/3 的独立 runtime jar 维护成本与用户混淆较高，项目决定不再在最新发布页面提供该 jar 的下载入口。移除链接可避免用户误以为需要额外下载 Hive runtime jar，引导用户使用 Hive 4.0.0 自带的 Iceberg 集成或参考 hive-quickstart 文档。

这是纯文档类修改，不涉及任何构建产物或代码。

## 如何达成设计目的

通过删除 `site/docs/releases.md` 中两处与 Hive runtime jar 相关的内容达成目标：
1. 从最新版本的 runtime jar 下载列表中删除 “Hive runtime Jar” 这一条目；
2. 删除紧随其后的说明句 “To use Iceberg in Hive 2 or Hive 3, download the Hive runtime JAR and add it to Hive using `ADD JAR`.”。

其余 Spark/Flink runtime jar 链接与使用说明保留不变。

## 修改详情

### `site/docs/releases.md`（修改, +0/-3 lines）

**修改目的**：移除最新发布中 Hive runtime jar 的下载入口与使用说明。

**工作逻辑**：
- 在 “The latest version of Iceberg is ...” 段落下，原本列出的 runtime jar 下载链接包括 Spark（多个版本）、Flink（多个版本）、Hive runtime Jar、aws-bundle、gcp-bundle、azure-bundle、alibabacloud-bundle 等。本次删除其中的 “Hive runtime Jar” 行：
  ```
  * [{{ icebergVersion }} Hive runtime Jar](https://search.maven.org/remotecontent?filepath=org/apache/iceberg/iceberg-hive-runtime/{{ icebergVersion }}/iceberg-hive-runtime-{{ icebergVersion }}.jar)
  ```
- 同时删除其下方专门针对 Hive 2/3 的使用说明：
  ```
  To use Iceberg in Hive 2 or Hive 3, download the Hive runtime JAR and add it to Hive using `ADD JAR`.
  ```

删除后，页面不再引导用户为 Hive 2/3 下载独立 runtime jar，与项目当前的 Hive 集成策略一致。

## 小结

- **成效**：发布文档不再提供 Hive runtime jar 的下载链接与使用说明，避免用户在 Hive 2/3 场景下走老路径，与项目当前的 Hive 集成方向（Hive 4.0.0 内置）保持一致。
- **影响范围**：仅影响站点文档 `site/docs/releases.md`，不涉及代码、构建或测试。
- **回迁到 1.4.x 的注意事项**：纯文档修改，无前置依赖，回迁无风险。但需结合 1.4.x 分支的 Hive 集成策略判断：若 1.4.x 仍维护 `iceberg-hive-runtime` 产物并为 Hive 2/3 提供支持，则回迁此改动需同步确认 1.4.x 是否同样希望淡化 Hive runtime jar 的下载入口。建议回迁前与 1.4.x 的发布策略对齐。注意本提交与 1796（1.8.1 站点更新）有时间先后关系，1796 的 releases.md 基线已不含 Hive runtime jar 行。
