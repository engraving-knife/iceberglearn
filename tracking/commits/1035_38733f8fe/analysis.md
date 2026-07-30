# 提交 1035：Flink: adjust code for the new 1.20 module.

## 提交信息

- **序号**：1035 / 4088
- **哈希**：38733f8fe63751826b3d99e5ab79f7e795e5c166
- **短哈希**：38733f8fe
- **日期**：2024-08-06 08:45:56 -0700
- **作者**：Steven Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: adjust code for the new 1.20 module. also fixed the bug of missing jmh in the 1.19 module.
- **PR/Issue**：无（提交说明中无 #编号；属于 Flink 1.20 支持系列重构的收尾提交，对应后续 PR #10888 中提到的 #10881）

## 总体目的

本提交是 Flink 1.20 模块引入四步序列（1032-1035）的最后一步，也是唯一涉及代码与构建配置实际内容修改的一步。前三步（1032 重命名 v1.19→v1.20、1033 拷贝恢复 v1.19、1034 删除 v1.17）都是纯目录级操作，没有改动任何文件内容。本提交承担两项任务：

1. **让 v1.20 模块真正成为 1.20 模块**：经过 1032 的重命名，`flink/v1.20/` 目录下的 `build.gradle` 内部 `flinkMajorVersion` 仍写作 `'1.19'`、依赖坐标仍引用 `libs.flink119.*`，整个仓库的构建系统（`settings.gradle`、`flink/build.gradle`、`gradle.properties`、`gradle/libs.versions.toml`、`jmh.gradle`、CI 矩阵）也没有 1.20 的入口、仍残留 1.17 的引用。本提交把这些配置全部切换到 1.20，并移除 1.17 的构建引用，使仓库的 Flink 版本矩阵正式变为 `1.18/1.19/1.20`。

2. **修复 v1.19 模块缺失 JMH 适配的 bug**：提交说明的第二句指出"also fixed the bug of missing jmh in the 1.19 module"。具体来说，`MapRangePartitionerBenchmark` 此前调用的是旧的 `MapRangePartitioner(schema, sortOrder, dataStatistics, partitions)` 构造签名（接收 `MapDataStatistics`），而主线代码已重构为 `MapRangePartitioner(schema, sortOrder, mapAssignment)`（接收 `MapAssignment`）。v1.19 的 JMH 基准测试还停留在旧 API，无法编译。本提交同时修复 v1.19 与 v1.20 两个模块里的这个基准测试，使其使用新的 `MapAssignment.fromKeyFrequency(...)` 构造方式。

此外，针对 Flink 1.20 相对 1.19 的少量 API 差异也做了最小化适配：`JobManagerOptions.SLOT_REQUEST_TIMEOUT` 在 1.20 中类型由 `long` 变为 `Duration`，需要改用 `Duration.ofSeconds(5)`；`FlinkPackage.version()` 的期望值从 `"1.19.0"` 改为 `"1.20.0"`。

## 如何达成设计目的

整体思路是"配置层全面切换 + 代码层最小适配"：

- **配置层**：在 `gradle/libs.versions.toml` 中新增 `flink120` 版本号与一整套 flink120-* 依赖坐标、删除 flink117 相关条目；在 `settings.gradle` 中新增 1.20 子项目包含声明、删除 1.17 子项目声明；在 `flink/build.gradle` 中新增 1.20 的 `apply from`、删除 1.17 的；在 `gradle.properties` 中把 `defaultFlinkVersions` 从 `1.19` 改为 `1.20`、`knownFlinkVersions` 从 `1.17,1.18,1.19` 改为 `1.18,1.19,1.20`；在 `jmh.gradle` 中把 JMH 项目列表从 1.16/1.17/1.18 更新为 1.18/1.19/1.20；在 CI 矩阵 `.github/workflows/flink-ci.yml` 中把 flink 矩阵从 `1.17/1.18/1.19` 改为 `1.18/1.19/1.20`，并删除原先因为 1.17 不支持 Java 17/21 而设的 exclude 规则。

- **代码层**：把 `flink/v1.20/build.gradle` 内部的 `flinkMajorVersion` 从 `'1.19'` 改为 `'1.20'`，所有 `libs.flink119.*` 改为 `libs.flink120.*`；修复 v1.19 与 v1.20 的 `MapRangePartitionerBenchmark`；针对 1.20 API 差异调整 `TestIcebergSpeculativeExecutionSupport` 与 `TestFlinkPackage`。

## 修改详情

### `.github/workflows/flink-ci.yml`

**修改目的**：更新 CI 构建矩阵，使其覆盖新的 Flink 版本组合。

**工作逻辑**：将 `matrix.flink` 从 `['1.17', '1.18', '1.19']` 改为 `['1.18', '1.19', '1.20']`，并移除原先针对 Flink 1.17 不支持 Java 17/21 的两条 `exclude` 规则（因为 1.17 已不在矩阵中，这些排除规则不再需要）。Java 矩阵 `[11, 17, 21]` 保持不变。

### `flink/build.gradle`

**修改目的**：更新 Flink 顶层构建脚本，移除 1.17 子模块引用、新增 1.20 子模块引用。

**工作逻辑**：删除 `if (flinkVersions.contains("1.17")) { apply from: file("$projectDir/v1.17/build.gradle") }` 块；新增 `if (flinkVersions.contains("1.20")) { apply from: file("$projectDir/v1.20/build.gradle") }` 块。1.18、1.19 的 apply 块保持不变。

### `flink/v1.19/flink/src/jmh/java/.../MapRangePartitionerBenchmark.java`

**修改目的**：修复 v1.19 模块 JMH 基准测试无法编译的 bug（提交说明中"missing jmh in the 1.19 module"即指此）。

**工作逻辑**：基准测试原先使用旧的 `MapRangePartitioner` 构造签名，接收 `MapDataStatistics`。但主线 `MapRangePartitioner` 已重构为接收 `MapAssignment`。修改点：
- 新增 `import java.util.Comparator`、`SortOrderComparators`、`StructLike`。
- 新增静态字段 `SORT_ORDER_COMPARTOR = SortOrderComparators.forSchema(SCHEMA, SORT_ORDER)`。
- 在 `setup` 方法中将 `new MapDataStatistics(mapStatistics)` 替换为 `MapAssignment.fromKeyFrequency(2, mapStatistics, 0.0, SORT_ORDER_COMPARTOR)`。
- 将 `new MapRangePartitioner(SCHEMA, ..., dataStatistics, 2)` 改为 `new MapRangePartitioner(SCHEMA, ..., mapAssignment)`（去掉多余的 partitions 参数，因为分区数已内含于 MapAssignment）。

### `flink/v1.20/build.gradle`

**修改目的**：把 v1.20 子模块的构建配置从 1.19 真正切换到 1.20。

**工作逻辑**：
- `String flinkMajorVersion = '1.19'` 改为 `'1.20'`。
- 所有 `compileOnly`/`testImplementation`/`integrationImplementation` 中的 `libs.flink119.*`（avro、metrics.dropwizard、streaming.java、table.api.java.bridge、connector.base、connector.files、connector.test.utils、core、runtime、test.utilsjunit、test.utils）统一替换为 `libs.flink120.*`。
- `flink-table-planner_${scalaVersion}` 的版本引用从 `libs.versions.flink119.get()` 改为 `libs.versions.flink120.get()`。
- 这些改动使 `:iceberg-flink:iceberg-flink-1.20` 子项目实际依赖 Flink 1.20.0 的 jar。

### `flink/v1.20/flink/src/jmh/java/.../MapRangePartitionerBenchmark.java`

**修改目的**：与 v1.19 同步修复 JMH 基准测试，使其匹配新的 `MapRangePartitioner` API。

**工作逻辑**：改动内容与上述 v1.19 的修复完全一致（新增 import、新增 `SORT_ORDER_COMPARTOR` 静态字段、用 `MapAssignment.fromKeyFrequency` 替换 `MapDataStatistics`、调整构造调用）。

### `flink/v1.20/flink/src/test/java/.../TestIcebergSpeculativeExecutionSupport.java`

**修改目的**：适配 Flink 1.20 中 `JobManagerOptions.SLOT_REQUEST_TIMEOUT` 的 API 类型变更。

**工作逻辑**：Flink 1.20 将 `JobManagerOptions.SLOT_REQUEST_TIMEOUT` 的配置类型从 `Long` 改为 `Duration`，因此 `configuration.set(JobManagerOptions.SLOT_REQUEST_TIMEOUT, 5000L)` 不再编译。改为 `configuration.set(JobManagerOptions.SLOT_REQUEST_TIMEOUT, Duration.ofSeconds(5))`，语义等价（5 秒超时）。

### `flink/v1.20/flink/src/test/java/.../TestFlinkPackage.java`

**修改目的**：更新版本断言，使 v1.20 模块的 `FlinkPackage.version()` 测试期望值与实际运行时版本一致。

**工作逻辑**：`assertThat(FlinkPackage.version()).isEqualTo("1.19.0")` 改为 `isEqualTo("1.20.0")`。因为 v1.20 模块运行时所在的 Flink 版本是 1.20.0。

### `gradle.properties`

**修改目的**：更新默认与已知 Flink 版本列表。

**工作逻辑**：`systemProp.defaultFlinkVersions` 从 `1.19` 改为 `1.20`；`systemProp.knownFlinkVersions` 从 `1.17,1.18,1.19` 改为 `1.18,1.19,1.20`。这影响 Gradle 构建时不指定 `flinkVersions` 时的默认值。

### `gradle/libs.versions.toml`

**修改目的**：在版本目录中移除 flink117 依赖、新增 flink120 依赖。

**工作逻辑**：
- 在 `[versions]` 段删除 `flink117 = { strictly = "1.17.2"}`、新增 `flink120 = { strictly = "1.20.0"}`。
- 在主依赖 `[libraries]` 段删除 6 个 `flink117-*`（avro、connector-base、connector-files、metrics-dropwizard、streaming-java、table-api-java-bridge），新增 6 个对应的 `flink120-*`。
- 在测试依赖 `[libraries]` 段删除 5 个 `flink117-*`（connector-test-utils、core、runtime、test-utils、test-utilsjunit），新增 5 个对应的 `flink120-*`。
- flink118、flink119 条目保持不变。

### `jmh.gradle`

**修改目的**：更新 JMH 基准测试所覆盖的 Flink 子项目列表。

**工作逻辑**：原先 `jmhProjects` 根据 `flinkVersions` 包含 1.16/1.17/1.18 三个子项目，现改为 1.18/1.19/1.20。注意这里同时修正了一个历史遗留——原先列表里还引用了已不存在的 1.16，本次一并清理。

### `settings.gradle`

**修改目的**：更新 Gradle 设置，移除 1.17 子项目、新增 1.20 子项目。

**工作逻辑**：删除 `if (flinkVersions.contains("1.17")) { ... }` 块（包含 `:iceberg-flink:flink-1.17` 与 `:iceberg-flink:flink-runtime-1.17` 两个子项目及其 projectDir/name 设置）；新增对应的 `if (flinkVersions.contains("1.20")) { ... }` 块，注册 `:iceberg-flink:flink-1.20` 与 `:iceberg-flink:flink-runtime-1.20`，projectDir 指向 `flink/v1.20/flink` 与 `flink/v1.20/flink-runtime`。

## 小结

- **成效**：完成 Flink 1.20 支持的最后落地——v1.20 模块的 `build.gradle` 真正依赖 Flink 1.20.0；整个仓库的构建系统（settings/build/gradle.properties/libs.versions.toml/jmh/CI）的 Flink 版本矩阵统一切换为 1.18/1.19/1.20；修复了 v1.19 与 v1.20 的 `MapRangePartitionerBenchmark` JMH 编译 bug；适配了 1.20 的 `SLOT_REQUEST_TIMEOUT` 类型变更与版本号断言。
- **影响范围**：共 11 个文件、70 行增、66 行删。涉及构建配置（7 个文件：CI、flink/build.gradle、gradle.properties、libs.versions.toml、jmh.gradle、settings.gradle、v1.20/build.gradle）、JMH 基准测试（2 个文件：v1.19 与 v1.20 的 MapRangePartitionerBenchmark）、测试代码（2 个文件：v1.20 的 TestIcebergSpeculativeExecutionSupport、TestFlinkPackage）。
- **回迁到 1.4.x 的注意事项**：**不应回迁**。本提交是 Flink 1.20 支持的收尾，依赖 1032-1034 的目录重构前置。1.4.x 分支的 Flink 版本矩阵（1.15/1.16/1.17）与 main 完全不同，且 1.4.x 的 `libs.versions.toml` 没有 flink120 条目、`flink/v1.20/` 目录也不存在，cherry-pick 会直接失败。1.4.x 若要支持 1.20 需整体重新设计，不能照搬这组提交。
