# 提交 0044：Rename master branch to main (#8722)

## 提交信息

- **序号**：0044 / 4088
- **哈希**：6530a3e88ae47dc792b53bb50d2df8e94feb63e0
- **短哈希**：6530a3e88
- **日期**：2023-10-12
- **作者**：JB Onofré
- **提交说明**：Rename master branch to main (#8722)
- **PR/Issue**：#8722

## 总体目的

该提交配合 Apache Iceberg 仓库主分支由 `master` 重命名为 `main` 的基础设施变更，将仓库内仍然引用 `master` 分支名的所有位置统一更新为 `main`。这是项目治理层面的术语规范化工作——一方面跟随 ASF 与整个行业用 `main` 取代 `master` 的趋势；另一方面也确保在仓库分支实际重命名之后，CI 工作流仍然能在新的主分支上正常触发，文档中指向源码与 javadoc 的链接也能正确解析。

如果不做这次更新，重命名后会立即出现两类问题：(1) GitHub Actions 工作流不再在主分支 push 上触发（因为工作流里 `branches: ['master']` 的过滤条件匹配不到 `main` 分支），CI 将陷入沉默失败；(2) README 与 docs 中以 `master` 为路径段的链接会变成死链（因为 GitHub 的 URL 已切换为 `main`），对社区用户造成困扰。该提交一次性把仓库内 11 处引用全部更新，共 12 行改动、12 行删除，零代码逻辑变化。

## 如何达成设计目的

设计上做"全仓文字替换"——把所有以 `master` 为分支标识的位置（无论出现在 GitHub Actions 的 `branches` 过滤器、URL 路径段还是 javadoc 路径中）统一替换为 `main`。改动按文件类型分布：CI 工作流（7 个 yml 文件）、README（1 个 md 文件）、Gradle 构建脚本（1 个文件）、文档（2 个 md 文件）。每个文件改动量都是 1 行，除 README 是同一描述的"链接文本 + 链接 URL"两处共 2 行外，其余每文件 1 处。

## 修改详情

### `.github/workflows/*.yml`（7 个文件）

涉及的文件：
- [api-binary-compatibility.yml](file:///Users/fengxiaohang/trae/iceberglearn/.github/workflows/api-binary-compatibility.yml)
- [delta-conversion-ci.yml](file:///Users/fengxiaohang/trae/iceberglearn/.github/workflows/delta-conversion-ci.yml)
- [flink-ci.yml](file:///Users/fengxiaohang/trae/iceberglearn/.github/workflows/flink-ci.yml)
- [hive-ci.yml](file:///Users/fengxiaohang/trae/iceberglearn/.github/workflows/hive-ci.yml)
- [java-ci.yml](file:///Users/fengxiaohang/trae/iceberglearn/.github/workflows/java-ci.yml)
- [open-api.yml](file:///Users/fengxiaohang/trae/iceberglearn/.github/workflows/open-api.yml)
- [spark-ci.yml](file:///Users/fengxiaohang/trae/iceberglearn/.github/workflows/spark-ci.yml)

**修改目的**：让 GitHub Actions 工作流在新主分支 `main` 上继续触发。

**工作逻辑**：每个 yml 文件中 `on: push: branches:` 列表的第一项由 `- 'master'` 改为 `- 'main'`，保持原有 `- '0.**'`（维护分支）与 `tags: - 'apache-iceberg-**'`（发布标签）不变。flink-ci/hive-ci/java-ci/spark-ci 这四个文件原本用单破折号缩进风格（`-    - 'master'`），另三个用双空格缩进风格（`      - 'master'`），改动保留了各自原有的缩进风格，仅替换分支名字符串。

### [README.md](file:///Users/fengxiaohang/trae/iceberglearn/README.md)

**修改目的**：修正 README 中两处指向 javadoc 的文本与 URL。

**工作逻辑**：第一处把链接显示文本由"available for the master"改为"available for the main"；第二处把 `[iceberg-javadocs]` 链接定义的目标 URL 由 `https://iceberg.apache.org/javadoc/master` 改为 `https://iceberg.apache.org/javadoc/main`。两处必须同时改，否则前者链接文字与实际 URL 仍会不一致。

### [build.gradle](file:///Users/fengxiaohang/trae/iceberglearn/build.gradle)

**修改目的**：修正 RevAPI 配置中提示开发者查阅贡献指南的 URL。

**工作逻辑**：在 RevAPI 任务输出"API/ABI breaks detected"提示信息里，把指向 `CONTRIBUTING.md#semantic-versioning` 的链接由 `https://github.com/apache/iceberg/blob/master/CONTRIBUTING.md#semantic-versioning` 改为 `.../blob/main/CONTRIBUTING.md#semantic-versioning`。该 URL 仅作为编译提示信息出现，不影响构建逻辑，但若不改在分支重命名后会指向 404。

### [docs/flink-writes.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/flink-writes.md)

**修改目的**：修正文档中指向 Flink sink 测试用例源码的 GitHub 链接。

**工作逻辑**：将引用 `TestFlinkIcebergSink.java` 的链接由 `https://github.com/apache/iceberg/blob/master/flink/v1.16/...` 改为 `.../blob/main/flink/v1.16/...`。这是用户文档，分支重命名后旧链接会跳到 GitHub 的"branch not found"页，需要同步修复。

### [docs/metrics-reporting.md](file:///Users/fengxiaohang/trae/iceberglearn/docs/metrics-reporting.md)

**修改目的**：修正文档中指向 REST OpenAPI 规范文件的 GitHub 链接。

**工作逻辑**：把 `REST OpenAPI spec` 链接由 `https://github.com/apache/iceberg/blob/master/open-api/rest-catalog-open-api.yaml` 改为 `.../blob/main/open-api/rest-catalog-open-api.yaml`，保持锚文本不变。同 flink-writes.md，是文档侧死链修复。

## 小结

该提交是配合仓库主分支从 `master` 改名为 `main` 的一次性引用更新，覆盖 7 个 CI 工作流与 4 处文档/构建脚本中的链接，确保分支重命名后 CI 仍可触发、文档链接不失效，是项目治理与基础设施同步的必要收尾。
