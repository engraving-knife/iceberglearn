# 提交 1147：Build: Upgrade google-java-format to 1.22.0 (#11050)

## 提交信息

- **序号**：1147
- **哈希**：34cd01ba2e057866cdb13db8f9919bc98e11e638
- **短哈希**：34cd01ba2
- **日期**：2024-09-11（Wed Sep 11 18:08:49 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Build: Upgrade google-java-format to 1.22.0 (#11050)
- **PR/Issue**：#11050

## 总体目的

Iceberg 仓库通过 Spotless 插件 + google-java-format 强制 Java 代码风格统一。此前仓库固定使用 google-java-format **1.7**（2019 年版本），原因是注释中提到"我们使用与 JDK 8 兼容的旧版本"。这导致：
1. 1.7 不支持 JDK 21 运行——`baseline.gradle` 中专门有一段判断：当 `JavaVersion.current() == JavaVersion.VERSION_21` 时禁用 Spotless 并提示用户切换 JDK，否则 `spotlessApply` 会直接抛 `GradleException`。这让在 JDK 21 上构建的开发者无法运行代码格式化。
2. 1.7 的格式化规则陈旧，与社区主流项目（使用 1.17+）产出不一致，且无法享受新版规则改进（如 Javadoc 块标签格式化、链式调用更优换行等）。

本提交将 google-java-format 从 1.7 升级到 **1.22.0**（注释中说明跳过 1.23.0 因其有注释格式化 bug [#1155](https://github.com/google/google-java-format/issues/1155)），同步把 Spotless Gradle 插件从 6.13.0 升级到 6.25.0（6.25.0 才能正确加载 1.22.0），并移除 JDK 21 的禁用逻辑。同时**对全仓库 60+ 文件重新跑 `spotlessApply`**，让现有代码符合新版本的格式化规则，避免升级后 CI 上报满屏格式错误。

## 如何达成设计目的

1. **升级依赖版本**：
   - `build.gradle` 中 `spotless-plugin-gradle` 由 `6.13.0` 升级到 `6.25.0`；
   - `baseline.gradle` 中 `googleJavaFormat("1.7")` 改为 `googleJavaFormat("1.22.0")`，并更新注释说明"1.23.0 有注释格式化 bug，故用 1.22.0 在 JDK 11/17/21 上产出一致结果"。
2. **移除 JDK 21 禁用分支**：删除 `baseline.gradle` 中针对 `JavaVersion.VERSION_21` 抛 `GradleException` 的特殊分支，直接无条件 `apply plugin: 'com.diffplug.spotless'`。这样 JDK 21 也能正常跑 spotless。
3. **全仓库重新格式化**：对 64 个 Java 文件按 1.22.0 规则重新格式化，主要涉及以下几类机械改动：
   - 单行 Javadoc 块标签拆为多行：`/** @return ... */` → `/**\n * @return ...\n */`；
   - 在 Javadoc 与上方常量/字段之间插入空行，让 Javadoc 块与代码块视觉分离；
   - 链式调用换行风格调整（如 `spark.read().format(...).load(...).sort(...).collectAsList().stream()` 改为每行一个方法调用）；
   - 个别测试中的 import 顺序与空行调整。
4. **保持构建脚本其它配置不变**：`removeUnusedImports()`、`licenseHeaderFile` 等其它 spotless 配置保持不变。

## 修改详情

### `build.gradle`

**修改目的**：升级 Spotless Gradle 插件以兼容 google-java-format 1.22.0。

**工作逻辑**：在 `buildscript.dependencies` 中将 `com.diffplug.spotless:spotless-plugin-gradle` 由 `6.13.0` 改为 `6.25.0`。6.25.0 是 Spotless 6.x 系列较新版本，正确支持加载 google-java-format 1.22.0。

### `baseline.gradle`

**修改目的**：升级 google-java-format 版本并移除 JDK 21 禁用逻辑。

**工作逻辑**：

1. **删除 JDK 21 禁用分支**：原代码：
   ```groovy
   if (JavaVersion.current() == JavaVersion.VERSION_21) {
     task spotlessApply {
       doLast {
         throw new GradleException("Spotless plugin is currently disabled when running on JDK 21 (until we drop JDK 8). To run spotlessApply please use a different JDK version.")
       }
     }
   } else {
     apply plugin: 'com.diffplug.spotless'
   }
   ```
   替换为直接：
   ```groovy
   apply plugin: 'com.diffplug.spotless'
   ```
   即无论 JDK 版本都启用 Spotless。注释"我们需要升级到 Google Java Format 1.17.0+ 以在 JDK 8 上跑 spotless，但那需要放弃 JDK 8 支持"也被移除——本提交并未放弃 JDK 8 支持（Iceberg 1.4.x 仍支持 JDK 8），但 google-java-format 1.22.0 自身可在 JDK 8 上运行（仅作为格式化工具，运行时 JDK 与目标 JDK 解耦），所以这里只是不再用 JDK 21 禁用作权宜之计。

2. **升级 google-java-format 版本**：在 `spotless { java { ... } }` 配置中：
   ```groovy
   // we use an older version of google-java-format that is compatible with JDK 8
   googleJavaFormat("1.7")
   ```
   改为：
   ```groovy
   // 1.23.0 has an issue in formatting comments https://github.com/google/google-java-format/issues/1155
   // so we stick to 1.22.0 to produce consistent result for JDK 11/17/21
   googleJavaFormat("1.22.0")
   ```
   注释清楚记录了为什么不用更新的 1.23.0——避免后续维护者误升级到有 bug 的版本。

### Java 源文件批量重新格式化（64 个文件）

以下按改动类型归类说明，不逐一罗列每个文件：

#### 类型 1：单行 Javadoc 拆为多行

google-java-format 1.22.0 强制把含块标签（`@return`、`@deprecated`、`@param` 等）的单行 Javadoc 拆为多行格式。例如：

- `api/src/main/java/org/apache/iceberg/DataFile.java`：`/** @return the content stored in the file; one of DATA, POSITION_DELETES, or EQUALITY_DELETES */` → 三行 Javadoc 块。
- `api/src/main/java/org/apache/iceberg/UpdatePartitionSpec.java`、`api/src/main/java/org/apache/iceberg/encryption/KmsClient.java`、`api/src/main/java/org/apache/iceberg/transforms/Transforms.java`、`api/src/main/java/org/apache/iceberg/transforms/Truncate.java`：`/** @deprecated will be removed in 2.0.0 */` 类似拆分。
- `api/src/test/java/org/apache/iceberg/TestHelpers.java`：同类拆分。
- `core/src/main/java/org/apache/iceberg/GenericManifestEntry.java`：三个 `@return` Javadoc 拆分（status、snapshotId、file）。
- `core/src/main/java/org/apache/iceberg/ManifestEntry.java`、`core/src/main/java/org/apache/iceberg/ManifestFilterManager.java`、`core/src/main/java/org/apache/iceberg/ManifestReader.java`、`core/src/main/java/org/apache/iceberg/SystemConfigs.java`：同类拆分。
- `core/src/main/java/org/apache/iceberg/TableProperties.java`：4 个 `@deprecated` Javadoc 拆分（OBJECT_STORE_PATH、WRITE_FOLDER_STORAGE_LOCATION、MANIFEST_LISTS_ENABLED、MANIFEST_LISTS_ENABLED_DEFAULT）。
- `core/src/main/java/org/apache/iceberg/CatalogProperties.java`：同类拆分。
- `core/src/test/java/org/apache/iceberg/TestMetadataTableFilters.java`：同类拆分。
- `common/src/main/java/org/apache/iceberg/common/DynConstructors.java`、`common/src/main/java/org/apache/iceberg/common/DynMethods.java`：同类拆分。
- `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java`：两个 `@deprecated` Javadoc 拆分（readSupport、callInit）。
- `flink/v1.18|v1.19|v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkSchemaUtil.java`、`flink/v1.18|v1.19|v1.20/flink/src/main/java/org/apache/iceberg/flink/source/FlinkSource.java`、`flink/v1.18|v1.19|v1.20/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`、`flink/v1.18|v1.19|v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/AggregatedStatisticsTracker.java`、`flink/v1.18|v1.19|v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/RequestGlobalStatisticsEvent.java`：多版本同步拆分。
- `flink/v1.18|v1.19|v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestMapRangePartition.java`、`flink/v1.18|v1.19|v1.20/flink/src/test/java/org/apache/iceberg/flink/source/enumerator/TestContinuousSplitPlannerImpl.java`：测试类同步拆分。
- `orc/src/main/java/org/apache/iceberg/orc/ORC.java`、`orc/src/main/java/org/apache/iceberg/orc/ORCSchemaUtil.java`、`orc/src/main/java/org/apache/hadoop/hive/ql/io/orc/OrcSplit.java`：同类拆分。
- `snowflake/src/main/java/org/apache/iceberg/snowflake/SnowflakeCatalog.java`、`gcp/src/main/java/org/apache/iceberg/gcp/GCPProperties.java`：同类拆分。

#### 类型 2：Javadoc 与上方代码之间插入空行

1.22.0 要求 Javadoc 块前（若上方有字段/常量声明）插入空行，让视觉上更清晰。例如：

- `aws/src/main/java/org/apache/iceberg/aws/HttpClientProperties.java`：在约 12 处常量声明的 Javadoc 前插入空行（如 `CLIENT_PREFIX`、`PROXY_ENDPOINT`、`URLCONNECTION_CONNECTION_TIMEOUT_MS` 等）。
- `api/src/main/java/org/apache/iceberg/DataFile.java`：在 `// NEXT ID TO ASSIGN: 142` 注释前插入空行。
- `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOAwsClientFactory.java`、`aws/src/main/java/org/apache/iceberg/aws/HttpClientProperties.java`：类似空行调整。
- `core/src/main/java/org/apache/iceberg/GenericManifestEntry.java`、`core/src/main/java/org/apache/iceberg/ManifestEntry.java`：Javadoc 前空行。

#### 类型 3：链式调用换行风格调整

1.22.0 对超长链式调用的换行策略有调整，倾向于每行一个方法调用。例如：

- `spark/v3.3|v3.4|v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java`（3 个版本同步）：原代码：
  ```java
  spark.read().format("iceberg").load(loadLocation(tableIdentifier, "files"))
      .sort(DataFile.SPEC_ID.name()).collectAsList().stream()
  ```
  改为：
  ```java
  spark
      .read()
      .format("iceberg")
      .load(loadLocation(tableIdentifier, "files"))
      .sort(DataFile.SPEC_ID.name())
      .collectAsList()
      .stream()
  ```
- `flink/v1.18|v1.19|v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergCommitter.java`：测试中链式调用调整。

#### 类型 4：测试中导入与小幅调整

- `aws/src/integration/java/org/apache/iceberg/aws/glue/GlueTestBase.java`、`aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogCommitFailure.java`、`aws/src/integration/java/org/apache/iceberg/aws/s3/TestS3FileIOIntegration.java`、`aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java`、`core/src/test/java/org/apache/iceberg/hadoop/HadoopFileIOTest.java`：测试中 import 顺序或空行调整。
- `spark/v3.3|v3.4|v3.5/spark/src/test/java/org/apache/iceberg/spark/extensions/TestSnapshotTableProcedure.java`（3 个版本同步）：同类小调整。

#### 类型 5：单字段补 `final` 等

- `flink/v1.18|v1.19|v1.20/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousIcebergEnumerator.java`：单行小幅调整。
- `dell/src/main/java/org/apache/iceberg/dell/ecs/EcsSeekableInputStream.java`：单行调整。

## 小结

- **成效**：google-java-format 从 1.7（2019）跨大版本升级到 1.22.0（2024），让 Spotless 能在 JDK 21 上正常运行（移除了 JDK 21 禁用分支），并享受新版规则改进；同时把全仓库 64 个 Java 文件按新规则重新格式化，避免升级后 CI 报错。注释中明确记录了"跳过 1.23.0 因其有 bug"的决策，便于后续维护。
- **影响范围**：`build.gradle`（1 行）、`baseline.gradle`（+4/-12）+ 64 个 Java 文件（+188/-77 行）。所有 Java 文件改动均为格式化（无逻辑变更），由 `spotlessApply` 自动产生，可重复验证。
- **回迁到 1.4.x 的注意事项**：
  - 这是一次纯构建工具升级 + 全仓库重新格式化，**不修复任何 bug、不引入任何功能**。
  - 1.4.x 作为已发布维护分支，原则上**不建议回迁**此类大规模格式化提交。原因：
    1. 改动 64 个文件、+188/-77 行，回迁会让 1.4.x 与上游的 cherry-pick 路径出现大量无意义冲突，增加后续维护成本；
    2. 1.4.x 若已发布，其源码格式不影响产物；
    3. google-java-format 1.7 在 1.4.x 上若工作正常，没有升级的紧迫性。
  - 若 1.4.x 需要在 JDK 21 上构建（例如 CI 升级到 JDK 21），则可考虑**仅回迁 `build.gradle` 与 `baseline.gradle` 的版本升级部分**（不包括全仓库重新格式化），并接受 1.4.x 现有源码与新版本格式化规则不一致——这需要在 CI 上把 spotless 检查设为 warning 而非 error，或者只在新文件上启用新规则。
  - 若 1.4.x 决定整笔回迁（含全部格式化），需一次性完成，避免分批引入冲突；回迁后所有后续 cherry-pick 都需经过 `spotlessApply` 重新格式化以匹配新规则。
  - 注意 `flink/v1.18|v1.19|v1.20` 三个版本的同步修改——1.4.x 实际维护的 Flink 版本可能不同，回迁时需按 1.4.x 实际目录挑选对应版本。
