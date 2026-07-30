# 提交 0853：Build: Run revapi workflow on workflow/build system changes (#10485)

## 提交信息
- **序号**：0853 / 4088
- **哈希**：08cc776bfef8c576378b9fc2d8f74f6c6e87370d
- **短哈希**：08cc776bf
- **日期**：2024-06-18
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Build: Run revapi workflow on workflow/build system changes (#10485)
- **PR/Issue**：#10485

## 总体目的
本提交是一次 CI 工作流配置微调，目的是让 `API Binary Compatibility Checks`（revapi）工作流在 PR 修改了「工作流自身或构建系统文件」时也被触发运行。

Iceberg 的 revapi 工作流负责检查 API 的二进制兼容性，由 Palantir revapi 工具配合 `.palantir/revapi.yml` 配置实现。该工作流在 `pull_request` 事件上使用 `paths` 过滤器，只有当 PR 修改了指定路径下的文件时才会触发。原来的路径列表只包含 `api/**` 和 `.palantir/revapi.yml`，即只关心 API 模块代码与 revapi 配置本身的变更。

问题在于：如果 PR 修改了构建系统文件（如根目录的 `*.gradle`、`gradle*` 相关文件）或 revapi 工作流文件本身（`.github/workflows/api-binary-compatibility.yml`），这些变更可能影响 revapi 的执行（例如 Gradle 插件版本变化、revapi 任务配置变化、工作流步骤变化），但原配置不会触发 revapi 检查，存在漏检风险。本次提交把这三类路径加入触发条件，确保构建系统与工作流本身的变更也会经过二进制兼容性检查。

## 如何达成设计目的
提交的实现极其简单：在 `.github/workflows/api-binary-compatibility.yml` 的 `pull_request.paths` 列表中新增三条路径：
- `.github/workflows/api-binary-compatibility.yml` —— 工作流文件自身变更时触发，便于验证工作流改动不会破坏 revapi 执行。
- `*.gradle` —— 根目录下的 Gradle 构建脚本变更时触发，覆盖构建配置、插件版本、依赖版本等可能影响 revapi 任务行为的变更。
- `gradle*` —— 匹配 `gradle.properties`、`gradlew`、`gradlew.bat`、`gradle/` 等与 Gradle wrapper 和属性相关的文件。

GitHub Actions 的 `paths` 过滤器支持 glob 语法，`*` 在仓库根目录下匹配任意文件名，因此 `*.gradle` 与 `gradle*` 能覆盖绝大多数构建系统入口文件。加入这三条后，PR 只要触及这些文件，revapi 工作流就会被触发，从而把构建系统变更纳入二进制兼容性保障范围。

## 修改详情
### `.github/workflows/api-binary-compatibility.yml`
**修改目的**：扩展 revapi 工作流在 PR 上的触发路径，纳入工作流自身与构建系统文件。
**工作逻辑**：在 `on.pull_request.paths` 数组中、原有的 `api/**` 与 `.palantir/revapi.yml` 之前，新增三条路径项：`.github/workflows/api-binary-compatibility.yml`、`*.gradle`、`gradle*`。新增项放在数组最前仅是排版，触发语义上与原有项是「或」关系——PR 只要修改任一匹配路径即触发工作流。`push` 与 `tags` 触发条件未改动，仍按分支名 / tag 名模式触发。

## 小结
- **成效**：补齐了 revapi 工作流在构建系统 / 工作流文件变更时的触发条件，避免因 Gradle 配置或工作流自身改动导致的二进制兼容性漏检，提升了 CI 保障的完整性。
- **影响范围**：仅修改 GitHub Actions 工作流配置，不影响任何源代码、API 或运行时行为；对 CI 触发频率略有提升（多一类 PR 会跑 revapi），但不改变 revapi 检查本身。
- **回迁注意事项**：回迁到 1.4.x 风险极低，可直接套用。需注意 1.4.x 分支上该工作流文件的路径过滤器可能与 main 略有差异（例如 1.4.x 上是否已有 `.palantir/revapi.yml` 路径项），回迁时应以 1.4.x 现有内容为基础追加三条新路径，而非整体覆盖。另外，若 1.4.x 的 Gradle 文件布局与 main 不同（例如存在子目录下的 gradle 脚本），`*.gradle` 只匹配根目录，可能需要额外考虑是否要扩展匹配模式。本提交与上下游无功能耦合，可独立回迁。
