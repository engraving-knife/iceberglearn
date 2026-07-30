# 提交 2105：Revert "Spark: Spark 4.0 initial support (#12494)"

## 提交信息

- **序号**：2105 / 4088
- **哈希**：a5bcacd979dc9ac70be3d7e5b93bb967ff04f71a
- **短哈希**：a5bcacd9
- **日期**：2025-05-09 09:51:33 -0600
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Revert "Spark: Spark 4.0 initial support (#12494)"\n\nThis reverts commit ad7f5c439b8392bd13d6376050e21330c2dfeced.
- **PR/Issue**：回退 #12494（即提交 2099）

## 总体目的

本提交整体回退两天前合入的 #12494（提交 2099 `ad7f5c439`，Spark 4.0 初始支持）。#12494 一次性引入约 580 个文件、13.2 万行代码（新建 `spark/v4.0/` 模块、构建配置、CI、大量从 v3.5 复制并适配的源码与测试），规模过大且尚未充分稳定。回退的目的是在 1.10.0 发布前移除这一不够成熟的大改动，避免引入回归风险，待后续以更小粒度、更稳妥的方式重新引入 Spark 4.0 支持。

回退操作把 #12494 修改/新增的所有内容恢复到合入前的状态：删除整个 `spark/v4.0/` 目录、还原 `settings.gradle`/`build.gradle`/`spark/build.gradle`/`gradle.properties`/`gradle/libs.versions.toml`/`jmh.gradle`/`.gitignore` 中与 Spark 4.0 相关的条目、还原 CI 工作流矩阵（移除 `spark: '4.0'`）、还原 `core/MetadataColumns.java` 的 `_spec_id` 字段（从 `optional` 改回 `required`）、还原 `hive/TestHiveMetastore.java` 的小改动。

## 如何达成设计目的

通过 `git revert` 生成反向 diff：对 #12494 中每个被修改的文件，恢复到该提交之前的状态；对每个新增文件，整体删除。由于 #12494 是一次性大提交且其后没有依赖它的后续提交（在 main 上），revert 干净彻底，不留残余。

## 修改详情

由于本提交是 #12494 的精确反向操作，文件级改动与 #12494 一一对应但方向相反（新增→删除、修改→还原）。下面按类别概述。

### CI 工作流（`.github/workflows/*.yml`）

**修改目的**：从 CI 矩阵与快照发布中移除 Spark 4.0。

**工作逻辑**：
- `spark-ci.yml`：矩阵 `spark` 还原为 `['3.4', '3.5']`，移除 `spark 4.0 + scala 2.12` 与 `spark 4.0 + jvm 11` 的排除项。
- `java-ci.yml`：矩阵 `jvm` 还原为 `[11, 17, 21]`（#12494 因 Spark 4.0 不支持 Java 11 而移除 11，revert 后恢复）。
- `jmh-benchmarks.yml` / `recurring-jmh-benchmarks.yml`：默认/循环基准的 `spark_version` 从 `iceberg-spark-4.0` 还原为 `iceberg-spark-3.5`。
- `publish-snapshot.yml`：发布命令的 `sparkVersions` 从 `3.4,3.5,4.0` 还原为 `3.4,3.5`。

### 构建配置

**修改目的**：移除 Spark 4.0 模块注册与版本登记。

**工作逻辑**：
- `gradle.properties`：`defaultSparkVersions` 还原为 `3.5`，`knownSparkVersions` 还原为 `3.4,3.5`。
- `gradle/libs.versions.toml`：删除 `antlr413`、`spark40` 版本别名与 `antlr-antlr413`、`antlr-runtime413` 依赖别名。
- `settings.gradle`：删除 `sparkVersions.contains("4.0")` 块（三个 v4.0 子项目 include）。
- `spark/build.gradle`：删除 `v4.0/build.gradle` apply 块。
- `build.gradle`：删除 Apache Spark 临时 Maven 仓库。
- `jmh.gradle`：删除 Spark 4.0 JMH 项目块。
- `.gitignore`：删除 `spark/v4.0/*` benchmark 条目。

### `spark/v4.0/**` (删除, 约 580 文件 / -132219 lines)

**修改目的**：移除整个 Spark 4.0 模块源码、扩展、actions、source、data、测试与基准。

**工作逻辑**：`spark/v4.0/build.gradle`、`spark/`、`spark-extensions/`、`spark-runtime/`、`benchmark/` 下全部文件删除，包括 SQL 扩展语法、catalyst 分析/逻辑/执行 Scala 类、`SparkCatalog`/`SparkTable`/`actions`/`source`/`data` Java 源码、数百个测试类、JMH 基准、资源文件（如 `decimal_dict_and_plain_encoding.parquet`）。

### `core/src/main/java/org/apache/iceberg/MetadataColumns.java` (修改, +1/-1 lines)

**修改目的**：还原 `_spec_id` 字段为 required。

**工作逻辑**：`NestedField.optional(...)` 改回 `NestedField.required(...)`，与 #12494 之前一致。

### `hive/src/test/java/org/apache/iceberg/hive/TestHiveMetastore.java` (修改, +0/-1 lines)

**修改目的**：还原 #12494 引入的小改动。

## 总结

本次提交是 #12494（提交 2099，Spark 4.0 初始支持）的精确 revert：删除整个 `spark/v4.0/` 模块（约 580 文件、13.2 万行），还原构建配置（`settings.gradle`/`build.gradle`/`libs.versions.toml`/`gradle.properties`/`jmh.gradle`/`.gitignore`）、CI 工作流矩阵、`MetadataColumns._spec_id`（改回 required）与 hive 测试小改动。回退原因是该提交规模过大、稳定性不足，1.10.0 发布前需移除风险，后续将重新以更稳妥的方式引入 Spark 4.0 支持。二者（2099 添加、2105 回退）构成一对完整的功能引入与撤回。
