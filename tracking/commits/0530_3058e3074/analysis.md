# 提交 0530：Infra: Don't run Delta Conversion CI on changes to site folder

## 提交信息

- **序号**：0530 / 4088
- **哈希**：3058e307481f134fade38581b322b9387337ce8b
- **短哈希**：3058e3074
- **日期**：2024-02-23 01:03:03 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Infra: Don't run Delta Conversion CI on changes to site folder
- **PR/Issue**：#9780

## 总体目的

本提交是一个**单行 CI 配置优化**：在 `delta-conversion-ci.yml` GitHub Actions 工作流的 `pull_request.paths-ignore` 列表中新增 `'site/**'`，使得仅修改 `site/` 目录（Iceberg 官方文档网站源码）的 PR 不再触发 Delta Conversion CI 工作流。

### 背景

- `Delta Conversion CI` 工作流负责测试 `iceberg-delta-lake` 模块（位于 `delta-lake/` 目录），它通过 `./gradlew :iceberg-delta-lake:check` 跑 Spark 3.5 / Scala 2.12 与 2.13 两组矩阵测试（JVM 8/11/17），是较重的 CI 任务。
- `site/` 目录是 Docusaurus 文档网站源码（包含 `site/docs/`、`site/blog/` 等），与 delta-lake 模块的代码毫无关系。
- 在此之前，`paths-ignore` 已忽略了 `docs/**`（AsciiDoc 文档源）、`flink/**`、`pig/**`、`open-api/**`、`format/**` 等与 delta-conversion 无关的目录，但**遗漏了 `site/**`**。结果：只改 `site/` 下文档页的 PR（如 commit 0529 修复 releases.md / multi-engine-support.md 这类纯文档改动）会无谓地触发 Delta Conversion CI，浪费 CI 资源、增加 PR 反馈延迟。
- 本提交补上这个遗漏，让 CI 触发条件更精确。

## 如何达成设计目的

设计非常直接：在 `on.pull_request.paths-ignore` 数组中已有的 `'docs/**'` 行之后插入一行 `'site/**'`。GitHub Actions 的 `paths-ignore` 语义是：当 PR 的所有改动文件都匹配 `paths-ignore` 中的任一模式时，该工作流不会被触发。新增 `'site/**'` 后，纯 `site/` 改动的 PR 会被本工作流跳过。

### paths-ignore 工作机制

- `paths-ignore` 是 GitHub Actions 的工作流触发过滤机制：PR 中只要有一个文件不匹配任何 ignore 模式，工作流仍会触发；只有当所有改动文件都被 ignore 模式覆盖时，工作流才跳过。
- 本工作流的 ignore 列表已覆盖文档（`docs/**`、`site/**`）、其他引擎模块（`flink/**`、`pig/**`、`mr/**`、`hive3/**`、`hive-runtime/**`）、其他工作流定义文件、配置文件（`.gitignore`、`.asf.yml`、`README.md`、`CONTRIBUTING.md`、`LICENSE`、`NOTICE`、`.gitattributes`）等。
- `push` 事件（main / 0.\*\* 分支与 apache-iceberg-\*\* tag）不受 `paths-ignore` 影响，仍会无条件触发——这保证主干合并与发版 tag 仍有完整的 delta-conversion 测试覆盖。

### Delta Conversion CI 的消费链路

- 工作流定义文件：`.github/workflows/delta-conversion-ci.yml`。
- 触发条件：`push`（main、0.\*\* 分支、apache-iceberg-\*\* tag）+ `pull_request`（带 paths-ignore 过滤）。
- 测试目标：`iceberg-delta-lake` 模块（源码在 `delta-lake/` 目录），通过 `./gradlew -DsparkVersions=3.5 -DscalaVersion=2.12 -DhiveVersions= -DflinkVersions= :iceberg-delta-lake:check -Pquick=true -x javadoc` 与对应的 Scala 2.13 版本运行测试。
- 该模块提供 Delta Lake 表向 Iceberg 表的转换能力（`RewriteDeltaTableProcedure` 等），与 `site/` 文档完全无关，故 ignore 是合理的。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml`

**修改目的**：把 `site/**` 加入 `pull_request.paths-ignore`，避免纯文档网站改动的 PR 触发 delta-lake 模块测试。

**工作逻辑**：

- 在 `on.pull_request.paths-ignore` 列表中，于 `'docs/**'` 行之后新增一行 `'site/**'`。修改后该列表包含（节选）：
  ```yaml
  paths-ignore:
    - '.github/ISSUE_TEMPLATE/**'
    - '.github/workflows/api-binary-compatibility.yml'
    # ... 其他工作流定义文件 ...
    - 'flink/**'
    - 'pig/**'
    - 'docs/**'
    - 'site/**'        # ← 本次新增
    - 'open-api/**'
    - 'format/**'
    - '.gitattributes'
    - 'README.md'
    - 'CONTRIBUTING.md'
    - 'LICENSE'
    - 'NOTICE'
  ```
- 修改后效果：当 PR 仅改动 `site/` 目录下文件（如 `site/docs/releases.md`、`site/docs/multi-engine-support.md`、`site/blog/...` 等）时，Delta Conversion CI 不会被触发；混合改动（既改 `site/` 又改 `delta-lake/`）仍会触发，保证测试覆盖不被误减。
- `push` 事件不受影响，main 分支与发版 tag 仍会无条件跑 delta-conversion 测试。

## 小结

- **成效**：补齐 `paths-ignore` 中遗漏的 `site/**`，让纯文档网站改动的 PR 不再触发 delta-lake 模块的 CI 测试，节省 CI 资源、缩短 PR 反馈时间。这是一个与 0529（修 `site/docs/releases.md` 与 `site/docs/multi-engine-support.md`）这类纯文档 PR 直接相关的优化——0529 本身就会触发 delta-conversion CI，本提交之后类似 PR 不再触发。
- **影响范围**：仅影响 `.github/workflows/delta-conversion-ci.yml` 的 PR 触发条件，不改变 push 触发条件，不影响任何代码、测试逻辑或运行时行为。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯 CI 配置改动，无依赖、无风险，可直接 cherry-pick。
  2. 1.4.x 分支的 `delta-conversion-ci.yml` 若与 main 有分歧（例如 1.4.x 的 Spark 版本矩阵不同），cherry-pick 时只需关注 `paths-ignore` 列表是否已包含 `site/**`，不涉及其他字段。
  3. 这是 CI 资源优化，回迁优先级低；若 1.4.x 维护期已无频繁的 `site/` 改动 PR，可不回迁。
  4. 注意与 0529 的协同：0529 改的就是 `site/docs/` 下的文件，本提交正是为了让这类 PR 不再触发 delta-conversion CI。两者主题相关但无强依赖，可独立回迁。
