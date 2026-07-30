# 提交 3751：Spark: Remove Spark 3.4 support (#14122)

## 提交信息

- **序号**：3751 / 4088
- **哈希**：463249a44bb443bc9df09ebe11b238796a8d5dcb
- **短哈希**：463249a44
- **日期**：2026-05-20 10:55:18 +0200
- **作者**：Kevin Liu
- **提交说明**：Spark: Remove Spark 3.4 support (#14122)
- **PR/Issue**：#14122

## 总体目的

本提交从 Iceberg 项目中完全移除 Spark 3.4 的支持，包括所有 Spark 3.4 相关的源代码、测试、构建配置、CI 工作流、文档和发布脚本。这是一次大规模的代码清理，删除了约 14 万行代码（629 个文件变更）。

Spark 3.4 已进入生命周期的末端（End of Life），维护成本高且用户基数逐渐减少。此前 Spark 3.4 已被标记为 "Deprecated"（在 #14122 之前），本次提交将其状态更新为 "End of Life"，最后支持的 Iceberg 版本为 1.11.0。移除 Spark 3.4 支持可以：
1. 减少 Iceberg 仓库的代码体积和维护负担（Spark 3.4 的集成代码、测试、扩展等约 14 万行）。
2. 简化 CI 矩阵，减少需要测试的 Spark 版本组合。
3. 消除因 Spark 3.4 钉死旧版依赖（如 jackson 2.14）而引入的临时豁免（如 CVE-2025-52999 的 trivyignore）。
4. 让开发团队聚焦于维护 Spark 3.5、4.0、4.1 等活跃版本。

## 如何达成设计目的

通过删除整个 `spark/v3.4/` 目录下的所有源代码、测试和构建文件，并同步更新所有引用 Spark 3.4 的构建配置、CI 工作流、文档和发布脚本。具体包括：
1. 删除 `spark/v3.4/` 目录下的所有内容（spark、spark-extensions、spark-runtime 三个子模块的源码、测试、构建脚本）。
2. 从 `settings.gradle`、`spark/build.gradle`、`jmh.gradle`、`build.gradle` 中移除 Spark 3.4 的项目包含和构建逻辑。
3. 从 `gradle/libs.versions.toml` 中移除 `spark34` 版本变量。
4. 从 `gradle.properties` 的 `knownSparkVersions` 中移除 `3.4`。
5. 从 CI 工作流（`spark-ci.yml`、`cve-scan.yml`、`publish-snapshot.yml`）中移除 Spark 3.4 相关的矩阵条目和构建任务。
6. 删除为 Spark 3.4 创建的 CVE 豁免文件 `.github/trivyignores/spark-runtime-3.4_2.12.trivyignore`。
7. 更新文档（`aws.md`、`multi-engine-support.md`、`releases.md`）中的 Spark 3.4 引用。
8. 更新 `dev/stage-binaries.sh` 发布脚本移除 Spark 3.4 的 Scala 2.13 发布步骤。
9. 更新 `.gitignore` 移除 Spark 3.4 的 benchmark 输出目录。

## 修改详情

### 构建配置文件

**修改目的**：从构建系统中移除 Spark 3.4 的项目定义和版本声明。

**工作逻辑**：
- `settings.gradle` (-12 lines)：移除 `sparkVersions.contains("3.4")` 块，该块包含 `spark-3.4`、`spark-extensions-3.4`、`spark-runtime-3.4` 三个项目的 include 和目录映射。
- `spark/build.gradle` (-4 lines)：移除 `if (sparkVersions.contains("3.4")) { apply from: file("$projectDir/v3.4/build.gradle") }` 块。
- `gradle/libs.versions.toml` (-1 line)：移除 `spark34 = "3.4.4"` 版本变量。
- `gradle.properties` (-1/+1 line)：将 `knownSparkVersions=3.4,3.5,4.0,4.1` 改为 `knownSparkVersions=3.5,4.0,4.1`。
- `build.gradle` (-1 line)：从 BOM 的 `sparkScalaVersions` map 中移除 `"3.4": ["2.12", "2.13"]` 条目。
- `jmh.gradle` (-5 lines)：移除 Spark 3.4 的 JMH 基准测试项目添加块。

### CI 工作流

**修改目的**：从 CI 中移除 Spark 3.4 的测试、扫描和发布。

**工作逻辑**：
- `.github/workflows/spark-ci.yml` (-4/+2 lines)：从矩阵的 `spark` 维度中移除 `'3.4'`，并移除 `jvm: 21 / spark: '3.4'` 的 exclude 规则（因 Spark 3.4 不支持 Java 21）。
- `.github/workflows/cve-scan.yml` (-7 lines)：移除 `spark-runtime-3.4_2.12` 矩阵条目（含 build-task、scan-path、trivyignores）。
- `.github/workflows/publish-snapshot.yml` (-1/+1 line)：将快照发布的 sparkVersions 从 `3.4,3.5,4.0` 改为 `3.5,4.0`。

### CVE 豁免文件

**修改目的**：删除为 Spark 3.4 创建的临时 CVE 豁免文件。

**工作逻辑**：
- `.github/trivyignores/spark-runtime-3.4_2.12.trivyignore` (-29 lines, 删除文件)：该文件在 #16287 中为豁免 CVE-2025-52999（jackson-core 2.14.2，被 Spark 3.4 钉死）而创建。随着 Spark 3.4 支持移除，该豁免不再需要。

### 发布脚本

**修改目的**：从发布流程中移除 Spark 3.4 的 artifact 发布。

**工作逻辑**：
- `dev/stage-binaries.sh` (-3/+2 lines)：将 `SPARK_VERSIONS=3.4,3.5,4.0,4.1` 改为 `SPARK_VERSIONS=3.5,4.0,4.1`，并移除 Spark 3.4 的 Scala 2.13 单独发布步骤。

### 文档

**修改目的**：更新文档中的 Spark 3.4 引用。

**工作逻辑**：
- `docs/docs/aws.md` (-3/+3 lines)：将 Spark 示例从 `iceberg-spark-runtime-3.4_2.12` 更新为 `iceberg-spark-runtime-3.5_2.12`。
- `site/docs/multi-engine-support.md` (-3/+3 lines)：将 Spark 版本支持表中 3.4 的状态从 "Deprecated" 改为 "End of Life"，最后支持版本更新为 1.11.0；将示例从 Spark 3.4/3.5 改为 Spark 3.5/4.0。
- `site/docs/releases.md` (-2 lines)：移除 Spark 3.4 的 Scala 2.12 和 2.13 runtime Jar 下载链接。
- `.gitignore` (-2 lines)：移除 `spark/v3.4/spark/benchmark/*` 和 `spark/v3.4/spark-extensions/benchmark/*` 条目。

### Spark 3.4 源代码（删除约 14 万行，629 个文件中的绝大部分）

**修改目的**：移除 Spark 3.4 集成的全部源代码和测试。

**工作逻辑**：
删除 `spark/v3.4/` 目录下的所有内容，包括：
- `spark/v3.4/build.gradle`：Spark 3.4 构建配置。
- `spark/v3.4/spark/`：Spark 3.4 集成主源码（Java/Scala），包括 `SparkSessionCatalog`、`SparkReadConf`、`SparkScan` 等类。
- `spark/v3.4/spark-extensions/`：Spark 3.4 SQL 扩展（语法解析器 `IcebergSqlExtensions.g4`、`IcebergSparkSessionExtensions.scala` 及相关分析器/优化器）。
- `spark/v3.4/spark-runtime/`：Spark 3.4 runtime 打包模块。
- 各模块的测试代码（`TestSparkReadConf`、`TestTimestampWithoutZone`、`TestUnpartitionedWrites` 等大量测试类）。
- JMH 基准测试代码（`PlanningBenchmark`、`DeleteFileIndexBenchmark` 等）。

## 总结

本提交从 Iceberg 项目中完全移除了 Spark 3.4 的支持，删除了约 14 万行代码（629 个文件），涵盖源码、测试、构建配置、CI 工作流、文档和发布脚本。Spark 3.4 的状态从 "Deprecated" 更新为 "End of Life"，最后支持的 Iceberg 版本为 1.11.0。这是 Iceberg 项目维护生命周期管理的重要里程碑，显著减少了代码体积和维护负担，简化了 CI 矩阵，消除了因 Spark 3.4 钉死旧依赖而需要的临时 CVE 豁免，使团队聚焦于 Spark 3.5/4.0/4.1 等活跃版本。
