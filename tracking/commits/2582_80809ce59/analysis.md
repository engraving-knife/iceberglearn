# 提交 2582：Build: add gradle options --no-parallel and --no-configuration-cache to stage-binaries.sh (#13958)

## 提交信息

- **序号**：2582 / 4088
- **哈希**：80809ce59a7f8ab95f82146ba7955628b580d271
- **短哈希**：80809ce59
- **日期**：2025-08-31 10:30:46 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Build: add gradle options --no-parallel and --no-configuration-cache to stage-binaries.sh (#13958)
- **PR/Issue**：#13958

## 总体目的

此次提交为发布构建脚本 `stage-binaries.sh` 中的所有 Gradle 调用添加 `--no-parallel` 和 `--no-configuration-cache` 选项，以解决发布构建过程中的稳定性问题。

背景是发布构建（`-Prelease` publish）在并行执行或启用配置缓存时可能出现问题：Gradle 的并行构建会同时运行多个子项目的任务，而发布任务涉及签名、上传到 Maven 暂存仓库等有状态操作，并行可能导致竞争或顺序问题；配置缓存（configuration cache）在发布场景下也可能因任务使用了不被缓存兼容的 API 而导致失败或产物不一致。

此前发布文档建议通过在 `gradle.properties` 中设置 `org.gradle.parallel=false` 来禁用并行，但这种方式依赖手动配置且不够显式。此次改为直接在脚本的每个 `./gradlew` 命令后追加 `--no-parallel --no-configuration-cache` 标志，使禁用成为脚本的固有行为，无需发布者额外配置。同时从发布文档中移除手动设置 `org.gradle.parallel=false` 的说明，因为脚本已自动处理。

## 如何达成设计目的

- 在 `dev/stage-binaries.sh` 的三个 `./gradlew` 调用命令末尾各追加 `--no-parallel --no-configuration-cache`。
- 从 `site/docs/how-to-release.md` 中删除"Disable gradle parallelism by setting `org.gradle.parallel=false` in `gradle.properties`"这一手动配置说明行。

## 修改详情

### `dev/stage-binaries.sh` (+3/-3)

**修改目的**：显式禁用发布构建的并行执行和配置缓存。

**工作逻辑**：三个 publish 命令（Scala 2.12 主发布、Spark 3.4 Scala 2.13 发布、Spark 3.5 Scala 2.13 发布）末尾均追加 `--no-parallel --no-configuration-cache`，确保发布构建以串行、无配置缓存方式执行，避免竞争和缓存兼容性问题。

### `site/docs/how-to-release.md` (+0/-1)

**修改目的**：移除过时的手动禁用并行说明。

**工作逻辑**：删除"Disable gradle parallelism by setting `org.gradle.parallel=false` in `gradle.properties`"行，因为 `stage-binaries.sh` 已通过命令行标志自动禁用，无需发布者手动配置。

## 总结

一次发布构建稳定性改进提交，在 `stage-binaries.sh` 的所有 Gradle publish 命令中追加 `--no-parallel --no-configuration-cache` 标志，将禁用并行和配置缓存固化为脚本行为，并从发布文档中移除对应的手动配置说明，避免发布构建因并行/配置缓存导致的稳定性问题。
