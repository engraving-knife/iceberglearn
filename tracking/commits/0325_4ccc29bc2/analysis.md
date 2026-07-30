# 提交 0325：Build: Bump actions/labeler from 4 to 5 (#9331)

## 提交信息

- **序号**：0325 / 4088
- **哈希**：4ccc29bc2f7115f372baa5c6ea04fafb41db8888
- **短哈希**：4ccc29bc2
- **日期**：2024-01-04 13:46:44 +0100
- **作者**：panbingkun
- **提交说明**：Build: Bump actions/labeler from 4 to 5 (#9331)
- **PR/Issue**：#9331

## 总体目的

这个提交将 Iceberg 仓库使用的 GitHub Actions `labeler` 自动标签 Action 从 v4 升级到 v5，并相应迁移了其配置文件格式。`labeler` 是一个在 Pull Request 上自动打标签的 GitHub Action：当 PR 修改了特定路径下的文件时，它会根据配置自动给 PR 打上对应的标签（如 `CORE`、`API`、`SPARK`、`DOCS`、`INFRA` 等），帮助维护者快速识别 PR 涉及的模块，便于分流与评审。Iceberg 作为一个模块众多的项目（core、api、spark、flink、parquet、orc、aws、gcp 等），这种基于文件路径的自动标签机制对 PR 分类很有价值。

`actions/labeler` 从 v4 升级到 v5 是一个包含破坏性变更的大版本升级。v5 改变了配置文件（`labeler.yml`）的 schema：在 v4 中，每个标签下直接列出一组 glob 路径模式（如 `CORE: - core/**/*`），Action 会判断 PR 修改的文件是否匹配任意 glob 来决定是否打标签；而在 v5 中，必须显式使用 `changed-files` 键，并在其下用 `any-glob-to-any-file`、`all-glob-to-all-files`、`any-glob-to-all-files`、`head-to-base-changed-files` 等更精确的匹配策略来描述规则。这意味着单纯升级 workflow 中的 Action 版本而不迁移配置文件，会导致 labeler 无法正确解析配置，自动标签功能将失效。

因此本提交同时修改了两个文件：一是 `.github/workflows/labeler.yml`（工作流定义），将 `actions/labeler@v4` 改为 `actions/labeler@v5`；二是 `.github/labeler.yml`（标签配置），将全部 25 个标签的规则从 v4 的扁平 glob 列表格式迁移为 v5 的 `changed-files` + `any-glob-to-any-file` 嵌套格式。迁移过程中保持了原有的标签语义不变——每个标签对应的文件路径模式集合与 v4 完全一致，只是换了表达形式，确保升级后自动标签行为与升级前一致。

## 如何达成设计目的

设计思路是"版本升级 + 配置迁移"同步进行，避免中间状态出现功能失效。工作流文件中通过修改一行 `uses: actions/labeler@v4` 为 `actions/labeler@v5` 完成版本切换；配置文件则对全部 25 个标签逐一改写。改写规则是：将原本直接列在标签下的 glob 字符串，整体包进 `- changed-files:` 下的 `- any-glob-to-any-file:` 数组中。`any-glob-to-any-file` 语义为"PR 修改的任意文件匹配任意一个 glob 即触发该标签"，这与 v4 默认行为（任意匹配即打标签）等价，从而保证迁移后行为不变。原配置中单个 glob 的标签（如 `API: - api/**/*`）也统一改写为数组形式，保持一致性。

## 修改详情

### `.github/workflows/labeler.yml`

**修改目的**：将 Pull Request 自动标签工作流使用的 Action 版本从 v4 升级到 v5。

**工作逻辑**：在 `triage` job 的 steps 中，将 `- uses: actions/labeler@v4` 改为 `- uses: actions/labeler@v5`。其余配置不变：工作流触发条件仍为 `on: pull_request_target`，权限仍为 `contents: read` 与 `pull-requests: write`，运行环境仍为 `ubuntu-22.04`，`with` 块仍传入 `repo-token` 与 `sync-labels: true`（`sync-labels` 表示当文件不再匹配时会移除标签）。这一行改动是版本切换的核心，但需要配合配置文件迁移才能生效。

### `.github/labeler.yml`

**修改目的**：将标签配置文件从 v4 的扁平 glob 列表格式迁移到 v5 的 `changed-files` 嵌套格式，使升级后的 labeler v5 能正确解析规则。

**工作逻辑**：

1. 文件顶部新增一行空行（原 `# Pull Request Labeler Github Action Configuration:` 注释行后），属格式整理。

2. 对全部 25 个标签（INFRA、BUILD、DOCS、SPECIFICATION、EXAMPLES、COMMON、API、CORE、PARQUET、ARROW、ORC、HIVE、DATA、SPARK、FLINK、MR、PIG、AWS、NESSIE、ALIYUN、GCP、DELL、SNOWFLAKE、OPENAPI、AZURE）统一执行格式迁移。以 `CORE` 为例：

   v4 格式（原）：
   ```yaml
   CORE:
     - core/**/*
   ```

   v5 格式（新）：
   ```yaml
   CORE:
     - changed-files:
       - any-glob-to-any-file: [
         'core/**/*'
       ]
   ```

   原本直接作为列表项的 glob 字符串，被包进 `changed-files` 键下的 `any-glob-to-any-file` 数组中。`any-glob-to-any-file` 的语义是"PR 修改的文件中，只要有一个匹配数组中任意一个 glob，就触发该标签"，这与 v4 中"匹配任意 glob 即打标签"的默认行为等价。

3. 对于原本包含多个 glob 的标签（如 `INFRA` 含 `.asf.yaml`、`.gitattributes` 等 10 项；`HIVE` 含 `hive3/**/*`、`hive-metastore/**/*`、`hive-runtime/**/*`、`hive3-orc-bundle/**/*` 共 4 项；`AWS` 含 `aws/**/*`、`aws-bundle/**/*`；`GCP` 含 `gcp/**/*`、`gcp-bundle/**/*`；`AZURE` 含 `azure/**/*`、`azure-bundle/**/*`），迁移后将所有 glob 统一放入同一个 `any-glob-to-any-file` 数组，保持"匹配任意一个即打标签"的语义不变。

4. 迁移过程中 glob 模式本身未做任何改动（路径与通配符与 v4 完全一致），仅改变外层结构，确保升级前后标签触发逻辑等价。

## 小结

本提交将 Iceberg 仓库的 GitHub Actions `labeler` 从 v4 升级到 v5，并通过将 `labeler.yml` 配置中全部 25 个标签的规则从扁平 glob 列表迁移为 `changed-files` + `any-glob-to-any-file` 的 v5 嵌套格式，完成了大版本升级所需的破坏性配置迁移。迁移严格保持原有标签语义不变，使 PR 自动标签功能在升级后继续按原有规则正确工作。
