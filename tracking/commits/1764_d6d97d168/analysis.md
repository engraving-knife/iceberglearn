# 提交 1764：Spark: Remove Spark 3.3 support (#12279)

## 提交信息

- **序号**：1764 / 4088
- **哈希**：d6d97d1683465c08d3903673ca6ccbca2004f735
- **短哈希**：d6d97d168
- **日期**：2025-02-20 10:42:05 +0100
- **作者**：Manu Zhang
- **提交说明**：Spark: Remove Spark 3.3 support (#12279)
- **PR/Issue**：#12279

## 总体目的

本提交旨在从 Iceberg 项目中移除对 Spark 3.3 的支持。Spark 3.3 是 2022 年发布的版本，随着 Spark 3.4 和 3.5 的广泛采用，Iceberg 项目决定不再维护对 Spark 3.3 的支持，以减少代码维护负担和构建复杂度。

Iceberg 项目为每个支持的 Spark 版本维护一套完整的代码模块（包括 Spark 核心代码、扩展、运行时），以及对应的测试套件。移除 Spark 3.3 支持意味着删除整个 `spark/v3.3/` 目录下的所有源代码、测试代码和构建配置，同时更新所有引用 Spark 3.3 的构建脚本、CI 配置和文档。

这是一个大规模代码删除提交，共删除 511 个文件，约 110,385 行代码。

## 如何达成设计目的

提交通过以下策略完成移除：

1. **删除 Spark 3.3 模块代码**：删除 `spark/v3.3/` 目录下的所有源代码和测试代码，包括 `spark/`、`spark-extensions/`、`spark-runtime/` 三个子模块。

2. **更新构建配置**：
   - `settings.gradle`：移除 Spark 3.3 相关的项目包含配置
   - `build.gradle`：从 BOM 的 Spark-Scala 版本映射中移除 "3.3" 条目
   - `gradle.properties`：将 `knownSparkVersions` 从 `3.3,3.4,3.5` 改为 `3.4,3.5`
   - `spark/build.gradle`：移除 Spark 3.3 相关配置
   - `gradle/libs.versions.toml`：移除 Spark 3.3 相关依赖声明
   - `jmh.gradle`：移除 Spark 3.3 基准测试配置

3. **更新 CI 配置**：修改 `.github/workflows/spark-ci.yml` 和 `.github/workflows/publish-snapshot.yml`，移除 Spark 3.3 的构建和发布任务。

4. **更新文档**：将文档中引用 "Spark 3.3" 的示例改为 "Spark 3.5"（如 `aws.md`、`nessie.md`、`multi-engine-support.md`）。

5. **更新其他引用**：修改 `.gitignore`、`dev/stage-binaries.sh` 等文件中对 Spark 3.3 的引用。

## 修改详情

### 构建配置文件（修改）

**修改目的**：从构建系统中移除 Spark 3.3 的配置。

**工作逻辑**：
- `settings.gradle`（-12 lines）：移除 `if (sparkVersions.contains("3.3"))` 块及其中的项目包含和命名配置。
- `gradle.properties`（+1/-1 lines）：`knownSparkVersions` 从 `3.3,3.4,3.5` 改为 `3.4,3.5`。
- `build.gradle`（-1 line）：从 BOM 的 `sparkScalaVersions` 映射中移除 `"3.3": ["2.12", "2.13"]` 条目。
- `spark/build.gradle`（-4 lines）：移除 Spark 3.3 相关构建逻辑。
- `gradle/libs.versions.toml`（-1 line）：移除 Spark 3.3 依赖声明。
- `jmh.gradle`（-5 lines）：移除 Spark 3.3 基准测试配置。
- `.gitignore`（-2 lines）：移除 Spark 3.3 benchmark 输出目录的忽略规则。

### CI 配置文件（修改）

**修改目的**：从 CI 流水线中移除 Spark 3.3 的构建和发布任务。

**工作逻辑**：
- `.github/workflows/spark-ci.yml`（+2/-2 lines）：移除 Spark 3.3 的 CI 构建矩阵条目。
- `.github/workflows/publish-snapshot.yml`（+1/-1 lines）：移除 Spark 3.3 的快照发布配置。

### 文档文件（修改）

**修改目的**：更新文档中的 Spark 版本引用，将 "Spark 3.3" 改为 "Spark 3.5"。

**工作逻辑**：
- `docs/docs/aws.md`（+9/-9 lines）：将所有 S3 配置示例中的 "Spark 3.3" 改为 "Spark 3.5"。
- `docs/docs/nessie.md`（+4/-4 lines）：更新 Nessie 集成示例中的 Spark 版本引用。
- `site/docs/multi-engine-support.md`（+1/-1 lines）：从支持引擎列表中移除 Spark 3.3。

### `dev/stage-binaries.sh`（修改, +1/-2 lines）

**修改目的**：移除二进制暂存脚本中的 Spark 3.3 引用。

### Spark 3.3 源代码和测试（删除, ~110,000 lines）

**修改目的**：移除 Spark 3.3 模块的全部代码。

**工作逻辑**：删除 `spark/v3.3/` 目录下的所有文件，包括：
- `spark/v3.3/build.gradle`（构建配置）
- `spark/v3.3/spark/`（Spark 3.3 核心实现：Java/Scala 源代码和测试）
- `spark/v3.3/spark-extensions/`（Spark 3.3 SQL 扩展：解析器、分析器、优化器等）
- `spark/v3.3/spark-runtime/`（Spark 3.3 运行时打包）
- 基准测试代码

这些代码与 Spark 3.4/3.5 的对应代码在结构上类似，但针对 Spark 3.3 的 API 进行了适配。

## 小结

- **成效**：移除了对 Spark 3.3 的完整支持，删除了约 11 万行代码，简化了项目构建和维护。Iceberg 现在仅支持 Spark 3.4 和 3.5。
- **影响范围**：大规模变更，涉及构建系统、CI 配置、文档和大量源代码/测试代码的删除。影响所有使用 Spark 3.3 与 Iceberg 集成的用户——他们需要升级到 Spark 3.4 或 3.5。
- **回迁到 1.4.x 的注意事项**：不建议回迁到 1.4.x 分支。1.4.x 分支可能仍然需要支持 Spark 3.3（取决于其发布时间线和用户需求）。如果 1.4.x 分支决定也移除 Spark 3.3 支持，此提交可作为参考，但需要谨慎处理 1.4.x 分支特有的代码差异。此提交本身规模庞大且为破坏性变更（移除功能支持），回迁需充分考虑用户影响。
