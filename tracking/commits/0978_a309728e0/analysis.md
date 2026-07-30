# 提交 0978：Infra, Docs: Publish Apache Iceberg 1.6.0 release (#10752)

## 提交信息

- **序号**：0978 / 4088
- **哈希**：a309728e08ae1dce2534b5553a89d77b6f476c01
- **短哈希**：a309728e0
- **日期**：2024-07-25 08:00:17 -0600
- **作者**：JB Onofré
- **提交说明**：Infra, Docs: Publish Apache Iceberg 1.6.0 release (#10752)
- **PR/Issue**：#10752

## 总体目的

Apache Iceberg 1.6.0 于 2024 年 7 月 23 日正式发布（社区投票通过）。发布后需要在仓库内多处对外可见的位置同步登记此次发布信息，使 ASF 项目目录、GitHub Issue 模板、官方文档站点的发布说明均能反映 1.6.0 已发布这一事实。本提交即承担这一"发布后公示"职责，由发布经理 JB Onofré 主导（与 Eduard Tudenhoefner 共同署名）。

具体需要更新的位置包括：

1. **DOAP 文件**（`doap.rdf`）：ASF 项目目录通过该文件读取项目最新发布版本，需将登记版本从 1.5.2 更新为 1.6.0。
2. **GitHub Bug 报告模板**（`.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`）：用户提交 bug 时选择 Iceberg 版本的下拉选项需将 "1.6.0" 标记为最新发布，并将原 1.5.2 降级为普通选项。
3. **官方发布说明文档**（`site/docs/releases.md`）：新增 1.6.0 的完整发布说明（含变更清单），并将 1.5.2 等历史发布归入 "Past releases" 段。

## 如何达成设计目的

实现方式是直接编辑上述三个文件，做覆盖式或追加式更新：

- `doap.rdf`：替换 `<release>` 块中的版本名、发布日期、版本号三字段。
- Bug 报告模板：在版本下拉选项顶部新增 "1.6.0 (latest release)"，并将原 "1.5.2 (latest release)" 改为 "1.5.2"。
- `releases.md`：在文档顶部 Maven 依赖示例之后插入完整的 1.6.0 发布说明章节（包含 Build/Core/Kafka Connect/Parquet/Spark/Flink/Hive/specs/Vendor Integrations/Dependencies 等分类的 PR 清单），并新增 "## Past releases" 二级标题将历史发布说明归组。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`

**修改目的**：更新 bug 报告模板中的 Iceberg 版本下拉选项，反映 1.6.0 为最新发布。

**工作逻辑**：将原选项 `- "1.5.2 (latest release)"` 替换为两行：`- "1.6.0 (latest release)"` 和 `- "1.5.2"`。即 1.6.0 成为最新发布选项，1.5.2 保留为普通选项（去掉 "latest release" 标记）。

### `doap.rdf`

**修改目的**：将 DOAP 文件登记的最新发布版本从 1.5.2 更新为 1.6.0。

**工作逻辑**：替换 `<release>/<Version>` 块中的三个字段：

```diff
-        <name>1.5.2</name>
-        <created>2024-05-09</created>
-        <revision>1.5.2</revision>
+        <name>1.6.0</name>
+        <created>2024-07-23</created>
+        <revision>1.6.0</revision>
```

发布日期由 2024-05-09（1.5.2）更新为 2024-07-23（1.6.0 实际发布日期）。DOAP 文件采用覆盖式更新，只保留最新一个 release 条目。

### `site/docs/releases.md`

**修改目的**：新增 1.6.0 完整发布说明，并将历史发布归入 "Past releases" 段。

**工作逻辑**：在文档顶部 Maven 依赖示例代码块之后，新增 `### 1.6.0 release` 章节，包含：

- 一句发布声明："Apache Iceberg 1.6.0 was released on July 23, 2024."
- 一句概要说明：1.6.0 包含修复、依赖更新和新功能（如 Kafka Connect commit coordinator 和 record converters）。
- 按分类列出的 PR 清单（每项带指向 GitHub PR 的链接），分类包括：Build、Core、Kafka Connect、Parquet、Spark、Flink、Hive、specs（OpenAPI）、Vendor Integrations（AWS、Azure）、Dependencies（Nessie、Spark、Arrow、Azure SDK、Kryo、Netty、Jetty、Kafka、ORC、AWS SDK、Google Cloud Libraries 等版本升级）。
- 末尾指向 GitHub release tag 的链接。

同时，在 1.5.0 发布说明之后新增 `## Past releases` 二级标题，并将原位于文档末尾的 `## Past releases` 标题删除（即把 "Past releases" 分隔上移，使 1.6.0 之后的所有历史发布统一归入该段）。该结构调整使最新发布位于 "Past releases" 之上。

## 小结

- **成效**：完成 1.6.0 发布后的对外信息公示——DOAP 文件、GitHub bug 模板、官方发布说明文档三处均已同步 1.6.0，使 ASF 项目目录、Issue 提交者、文档访问者均能感知 1.6.0 已发布。
- **影响范围**：`doap.rdf`、`.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`、`site/docs/releases.md` 三个文件，共 99 行新增、6 行删除。无代码逻辑改动。
- **回迁到 1.4.x 的注意事项**：该提交属于 main 分支 1.6.0 发布流程的一部分，**不应回迁到 1.4.x 分支**。1.4.x 维护分支的 DOAP、bug 模板、发布说明应登记 1.4.x 系列版本（如 1.4.3 或后续 patch），若将 1.6.0 信息回迁会造成版本登记混乱。1.4.x 分支应按自身发布节奏独立维护这些文件。
