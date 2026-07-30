# 提交 0394：Build: Bump actions/upload-artifact from 3 to 4

## 提交信息

- **序号**：0394
- **哈希**：e9f26b13f417cb48da83791b700433149f608e5d
- **短哈希**：e9f26b13f
- **日期**：2024-01-19（Fri Jan 19 12:09:27 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump actions/upload-artifact from 3 to 4
- **PR/Issue**：#9319

**完整提交说明**：

```
Bumps [actions/upload-artifact](https://github.com/actions/upload-artifact) from 3 to 4.
- [Release notes](https://github.com/actions/upload-artifact/releases)
- [Commits](https://github.com/actions/upload-artifact/compare/v3...v4)

---
updated-dependencies:
- dependency-name: actions/upload-artifact
  dependency-type: direct:production
  update-type: version-update:semver-major
...

Signed-off-by: dependabot[bot] <support@github.com>
Co-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
```

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 仓库所有 GitHub Actions 工作流中使用的 `actions/upload-artifact` 从 v3 升级到 v4。这是一个重要的强制性升级：GitHub 官方宣布 `actions/upload-artifact@v3` 将于 2024 年 1 月开始弃用，并在 2024 年初逐步停用，所有使用 v3 的工作流将无法正常上传构建产物（如测试日志、基准测试结果等），必须迁移到 v4 才能保证 CI 流水线正常运行。

v4 相比 v3 有若干重要变化：
- v4 上传速度显著提升（通过分块上传）；
- 单个 artifact 默认大小限制从 10GB 调整，且 v4 在节点间合并 artifact 的方式不同；
- v4 不再支持 v3 中某些边缘用法，但本仓库使用的 `name` + `if: failure()` 模式在 v4 中完全兼容。

Iceberg 的 CI 工作流大量使用 `actions/upload-artifact` 在测试失败时上传日志（`if: failure()`），或在基准测试中始终上传结果（`if: ${{ always() }}`）。这些 artifact 对调试 CI 失败、追踪性能基准至关重要。本次升级覆盖 8 个工作流文件共 12 处引用，确保所有 CI 流水线在 v3 弃用截止日期前完成迁移。

从分支维护角度看，Dependabot 提交虽然自动化生成，但属于必要的依赖维护工作。1.4.x 维护分支同样需要保持 CI 可用性，因此这类升级也需要回溯到维护分支。

## 如何达成设计目的

Dependabot 扫描仓库中所有 `.github/workflows/*.yml` 文件，将 `uses: actions/upload-artifact@v3` 全部替换为 `uses: actions/upload-artifact@v4`，其余参数（`if` 条件、`with.name` 等）保持不变。这是一个纯粹的版本号替换，不涉及工作流逻辑变更。由于 v4 与 v3 在本仓库使用场景下行为兼容，因此无需额外适配。

## 修改详情

### .github/workflows/api-binary-compatibility.yml

**修改目的**：将 API 二进制兼容性检查工作流中的 upload-artifact 从 v3 升级到 v4。

**工作逻辑**：该工作流运行 `./gradlew revapi` 进行 API 兼容性检查，当检查失败时（`if: failure()`）上传名为 "test logs" 的 artifact 供开发者排查。本次仅将 `actions/upload-artifact@v3` 改为 `@v4`，其余配置不变。

### .github/workflows/delta-conversion-ci.yml

**修改目的**：将 Delta Lake 转换模块 CI 工作流中的两处 upload-artifact 升级到 v4。

**工作逻辑**：该工作流包含两个 job，分别针对 Spark 3.5 + Scala 2.12 和 Spark 3.5 + Scala 2.13 运行 `:iceberg-delta-lake:check`。每个 job 在失败时上传 "test logs" artifact。本次将两处 `@v3` 都改为 `@v4`。

### .github/workflows/flink-ci.yml

**修改目的**：将 Flink CI 工作流中的 upload-artifact 升级到 v4。

**工作逻辑**：该工作流针对矩阵中的各 Flink 版本运行 `:iceberg-flink:iceberg-flink-${{ matrix.flink }}:check` 等检查，失败时上传 "test logs"。本次将一处 `@v3` 改为 `@v4`。

### .github/workflows/hive-ci.yml

**修改目的**：将 Hive CI 工作流中的两处 upload-artifact 升级到 v4。

**工作逻辑**：该工作流包含两个 job，分别针对 Hive 2 和 Hive 3 运行 `:iceberg-mr:check`、`:iceberg-hive-runtime:check` 等检查。每个 job 失败时上传 "test logs"。本次将两处 `@v3` 都改为 `@v4`。

### .github/workflows/java-ci.yml

**修改目的**：将核心 Java CI 工作流中的 upload-artifact 升级到 v4。

**工作逻辑**：该工作流运行 `./gradlew check`（关闭 spark/hive/flink 子模块）进行核心 Java 代码检查，失败时上传 "test logs"。本次将一处 `@v3` 改为 `@v4`。

### .github/workflows/jmh-benchmarks.yml

**修改目的**：将 JMH 基准测试工作流中的 upload-artifact 升级到 v4。

**工作逻辑**：该工作流是手动触发的（`github.event.inputs.spark_version`），运行 `./gradlew :iceberg-spark:...:jmh` 执行基准测试，并将结果上传为 "benchmark-results" artifact。注意此处使用 `if: ${{ always() }}`，即无论基准测试成功或失败都上传结果。本次将一处 `@v3` 改为 `@v4`。

### .github/workflows/recurring-jmh-benchmarks.yml

**修改目的**：将周期性 JMH 基准测试工作流中的 upload-artifact 升级到 v4。

**工作逻辑**：该工作流是定期调度的（recurring），针对矩阵中的 Spark 版本运行基准测试，同时输出 `.txt` 和 `.json` 两种格式的结果，并上传为 "benchmark-results" artifact（`if: ${{ always() }}`）。本次将一处 `@v3` 改为 `@v4`。

### .github/workflows/spark-ci.yml

**修改目的**：将 Spark CI 工作流中的三处 upload-artifact 升级到 v4。

**工作逻辑**：该工作流包含三个 job，分别针对 Scala 2.12、Scala 2.13、以及矩阵中其他 Scala 版本组合运行 Spark 模块检查（`iceberg-spark-${{ matrix.spark }}_*:check`、`iceberg-spark-extensions-*:check`、`iceberg-spark-runtime-*:check`）。每个 job 失败时上传 "test logs"。本次将三处 `@v3` 都改为 `@v4`。

## 小结

本提交是 Dependabot 自动生成的依赖升级，将所有 GitHub Actions 工作流中的 `actions/upload-artifact` 从 v3 升级到 v4，以应对 GitHub 官方对 v3 的弃用。修改覆盖 8 个工作流文件共 12 处引用，全部为版本号替换，无逻辑变更。这类自动化依赖升级虽然简单，但对保障 CI 流水线持续可用至关重要——若不及时升级，v3 停用后将导致所有测试日志和基准结果无法上传，严重影响 CI 调试能力。Dependabot 提交是开源项目依赖治理的标准化实践，体现了 Iceberg 项目对 CI 基础设施维护的重视。
