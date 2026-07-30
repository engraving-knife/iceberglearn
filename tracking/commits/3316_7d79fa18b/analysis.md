# 提交 3316：Build: Bump hadoop from 3.4.2 to 3.4.3. (#15431)

## 提交信息

- **序号**：3316 / 4088
- **哈希**：7d79fa18b6bd78ff854b144957abeeb4beea85bf
- **短哈希**：7d79fa18b
- **日期**：2026-02-26
- **作者**：slfan1989
- **提交说明**：Build: Bump hadoop from 3.4.2 to 3.4.3. (#15431)
- **PR/Issue**：#15431

## 总体目的

这是一次由 Dependabot 自动发起的依赖升级提交，将 Iceberg 构建中所使用的 Apache Hadoop 3.x 版本从 3.4.2 升级到 3.4.3。Hadoop 是 Iceberg 项目中至关重要的底层依赖：Iceberg 的多个模块（如 Hive 集成、Flink 集成、MapReduce 集成以及核心测试基础设施）都通过 `hadoop3` 版本变量引用 Hadoop 的 `hadoop-client`、`hadoop-common`、`hadoop-hdfs`、`hadoop-mapreduce-client-core` 和 `hadoop-minicluster` 等构件。

从版本号语义来看，3.4.2 到 3.4.3 属于补丁版本（PATCH）升级，按照语义化版本约定，此类升级仅包含缺陷修复和向后兼容的改进，不引入破坏性 API 变更。因此这次升级的动机是跟随上游 Hadoop 社区的维护版本，获取 3.4.3 中包含的缺陷修复与安全补丁，保持依赖的时效性，同时规避已知问题。

Dependabot 在 Iceberg 仓库中针对 `gradle/libs.versions.toml` 中声明的版本进行周期性扫描，发现上游已发布 3.4.3 后自动生成此 PR。这类升级虽改动极小，但对于维持整个多模块构建的健康度、避免在后续开发中踩到已修复的上游 Bug 具有常规的维护价值。

## 如何达成设计目的

改动集中在唯一的版本目录文件 `gradle/libs.versions.toml` 中，将 `hadoop3` 这一版本引用从 `"3.4.2"` 改为 `"3.4.3"`。由于该项目采用 Gradle 版本目录（Version Catalog）集中管理依赖版本，所有引用 `version.ref = "hadoop3"` 的库别名（`hadoop3-client`、`hadoop3-common`、`hadoop3-hdfs`、`hadoop3-mapreduce-client-core`、`hadoop3-minicluster`）都会随之统一升级，无需在各模块的 `build.gradle` 中逐个修改，体现了版本目录机制带来的维护便利。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Hadoop 3.x 系列的版本基线从 3.4.2 提升至 3.4.3。

**工作逻辑**：
该文件是 Gradle 版本目录，集中声明项目中所有依赖的版本号与库别名。改动仅涉及一行：将版本段中的 `hadoop3 = "3.4.2"` 修改为 `hadoop3 = "3.4.3"`。该 `hadoop3` 变量被同文件中的多个库别名以 `version.ref = "hadoop3"` 引用，包括 `hadoop3-client`、`hadoop3-common`、`hadoop3-hdfs`、`hadoop3-mapreduce-client-core`、`hadoop3-minicluster`。这些构件在仓库的 `build.gradle`（根项目及 `hive3`、`flink/v1.20`、`flink/v2.0`、`flink/v2.1`、`mr` 等子模块）中以 `libs.hadoop3.client`、`libs.hadoop3.common` 等形式被广泛用作 `compileOnly`、`testImplementation` 和 `integrationImplementation` 依赖。因此，这一处单行改动会级联地把上述全部模块所依赖的 Hadoop 3.x 构件统一升至 3.4.3。作为补丁版本升级，预期不破坏现有 API 契约，CI 会重新构建并运行集成测试以验证兼容性。

## 总结

本次提交通过版本目录的单行改动，将 Iceberg 全模块对 Hadoop 3.x 的依赖基线从 3.4.2 升级到 3.4.3，获取上游补丁版本中的缺陷修复与安全更新。改动风险低、影响面广但可控，是依赖维护的常规操作，体现了版本目录机制在集中化管理依赖方面的优势。
