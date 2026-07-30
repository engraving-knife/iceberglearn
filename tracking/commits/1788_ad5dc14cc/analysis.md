# 提交 1788：Build: Remove Hadoop 2 (#12348)

## 提交信息

- **序号**：1788 / 4088
- **哈希**：ad5dc14cc3e28718461b3f2b31e6b3633599cc4b
- **短哈希**：ad5dc14cc
- **日期**：2025-02-26 12:53:33 +0100
- **作者**：Kristin Cowalcijk
- **提交说明**：Build: Remove Hadoop 2 (#12348)
- **PR/Issue**：#12348

## 总体目的

这个提交将 Iceberg 项目从同时支持 Hadoop 2 和 Hadoop 3 改为仅支持 Hadoop 3。提交说明中给出了明确的理由：由于 Iceberg 的最低 Java 版本已经提升到 11，而 Hadoop 2.x 不支持 Java 11（Hadoop 2.7.x 最高支持 Java 8），因此 Iceberg 实际上已经无法在 Hadoop 2 环境下运行。Hadoop 3.3.0+ 才是支持 Java 11 的最低 Hadoop 版本。

此前，Iceberg 的构建配置中同时维护了 Hadoop 2 和 Hadoop 3 两套依赖，在 `gradle/libs.versions.toml` 中定义了 `hadoop2`（2.7.3）和 `hadoop3`（3.4.1）两个版本。各模块在 `build.gradle` 中根据需要引用 `hadoop2-*` 或 `hadoop3-*` 依赖。这种双版本维护增加了构建配置的复杂性，且 Hadoop 2 的依赖实际上已经无法使用。

移除 Hadoop 2 的版本和库定义，强制所有构建和测试使用 Hadoop 3 的兼容版本，简化了构建配置，并为后续使用 Hadoop 3 的现代 API 铺平了道路。

## 如何达成设计目的

提交通过以下策略实现：

1. **移除 Hadoop 2 依赖定义**：从 `gradle/libs.versions.toml` 中移除 `hadoop2` 版本号和所有 `hadoop2-*` 库引用（hadoop2-client、hadoop2-common、hadoop2-hdfs、hadoop2-mapreduce-client-core、hadoop2-minicluster）。

2. **补全 Hadoop 3 依赖定义**：在 `gradle/libs.versions.toml` 中新增此前缺失的 `hadoop3-hdfs`、`hadoop3-mapreduce-client-core`、`hadoop3-minicluster` 库引用，与原有的 `hadoop3-client` 和 `hadoop3-common` 一起形成完整的 Hadoop 3 依赖集。

3. **全面替换依赖引用**：在所有模块的 `build.gradle` 文件中，将 `hadoop2-*` 依赖引用替换为对应的 `hadoop3-*` 依赖引用。

4. **修复测试兼容性问题**：Hadoop 3 与 Hadoop 2 在某些行为上有差异，需要修复因此产生的测试失败：
   - Hadoop 3 的 CRC 校验文件（`.crc`）需要在测试中一并删除。
   - Hadoop 3 的配置传播行为不同，需要在测试中重置配置。

5. **修复阿里云测试中的依赖问题**：Hadoop 3 不再传递 `org.apache.directory.api:api-util` 依赖，需要改用 `commons-codec` 的 Hex 工具。

## 修改详情

### `gradle/libs.versions.toml`（修改, +3/-6 lines）

**修改目的**：移除 Hadoop 2 依赖定义，补全 Hadoop 3 依赖定义。

**工作逻辑**：
- 移除 `hadoop2 = "2.7.3"` 版本定义。
- 移除 5 个 hadoop2-* 库引用（hadoop2-client、hadoop2-common、hadoop2-hdfs、hadoop2-mapreduce-client-core、hadoop2-minicluster）。
- 新增 3 个 hadoop3-* 库引用（hadoop3-hdfs、hadoop3-mapreduce-client-core、hadoop3-minicluster），此前这些只有 hadoop2 版本。

### `build.gradle`（修改, +15/-15 lines）

**修改目的**：将所有模块的 Hadoop 2 依赖替换为 Hadoop 3。

**工作逻辑**：在以下模块中将 `hadoop2-*` 替换为 `hadoop3-*`：
- `iceberg-core`：`hadoop2.client` -> `hadoop3.client`
- `iceberg-data`：`hadoop2.common` -> `hadoop3.common`，`hadoop2.client` -> `hadoop3.client`
- `iceberg-aliyun`：`hadoop2.common` -> `hadoop3.common`
- `iceberg-aws`：`hadoop2.common` -> `hadoop3.common`
- `iceberg-delta-lake`：`hadoop2.common` -> `hadoop3.common`，`hadoop2.minicluster` -> `hadoop3.minicluster`
- `iceberg-gcp`：`hadoop2.common` -> `hadoop3.common`
- `iceberg-hive-metastore`：`hadoop2.client` -> `hadoop3.client`
- `iceberg-orc`：`hadoop2.common` -> `hadoop3.common`，`hadoop2.client` -> `hadoop3.client`
- `iceberg-parquet`：`hadoop2.client` -> `hadoop3.client`
- `iceberg-arrow`：`hadoop2.common` -> `hadoop3.common`，`hadoop2.mapreduce.client.core` -> `hadoop3.mapreduce.client.core`
- `iceberg-nessie`：`hadoop2.common` -> `hadoop3.common`

### `flink/v1.18/build.gradle`、`flink/v1.19/build.gradle`、`flink/v1.20/build.gradle`（修改, +6/-6 lines each）

**修改目的**：将 Flink 模块的 Hadoop 2 依赖替换为 Hadoop 3。

**工作逻辑**：将 `hadoop2.hdfs`、`hadoop2.common`、`hadoop2.minicluster` 分别替换为 `hadoop3.hdfs`、`hadoop3.common`、`hadoop3.minicluster`。

### `mr/build.gradle`（修改, +1/-1 lines）

**修改目的**：将 MR 模块的 Hadoop 2 依赖替换为 Hadoop 3。

**工作逻辑**：将 `hadoop2.client` 替换为 `hadoop3.client`。

### `spark/v3.4/build.gradle`、`spark/v3.5/build.gradle`（修改, +1/-1 lines each）

**修改目的**：将 Spark 模块的 Hadoop 2 依赖替换为 Hadoop 3。

**工作逻辑**：将 `hadoop2.minicluster` 替换为 `hadoop3.minicluster`。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockLocalStore.java`（修改, +2/-3 lines）

**修改目的**：修复 Hadoop 3 不再传递 directory API 依赖的问题。

**工作逻辑**：
- 移除 `import java.util.Locale;` 和 `import org.apache.directory.api.util.Hex;`。
- 改为 `import org.apache.commons.codec.binary.Hex;`（commons-codec 是 Hadoop 3 传递的依赖）。
- `Hex.encodeHex()` + `toUpperCase()` 替换为 `Hex.encodeHexString(md.digest(), false)`（参数 `false` 表示输出大写）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestCompressionSettings.java`（修改, +8/-0 lines）

**修改目的**：修复 Hadoop 3 配置传播行为差异导致的测试失败。

**工作逻辑**：新增 `@Before` 方法 `resetSpecificConfigurations()`，在每个测试前清除压缩编解码器、压缩级别和压缩策略的 Spark 配置。这是因为 Hadoop 3 的配置传播行为与 Hadoop 2 不同，前一个测试设置的配置可能影响后续测试。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreaming.java`（修改, +5/-0 lines）和 `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreaming.java`（修改, +5/-0 lines）

**修改目的**：修复 Hadoop 3 CRC 校验文件导致的测试失败。

**工作逻辑**：在删除 checkpoint 提交文件（`/commits/1`）的同时，还需要删除对应的 CRC 校验文件（`/commits/.1.crc`）。Hadoop 3 默认启用 CRC 校验，如果不删除 `.crc` 文件，Hadoop 会因校验和不匹配而报错。

## 小结

- **成效**：成功移除了 Iceberg 对 Hadoop 2 的支持，将所有模块统一使用 Hadoop 3 依赖。简化了构建配置，消除了无法实际使用的 Hadoop 2 依赖维护负担，为后续使用 Hadoop 3 现代 API 铺平了道路。同时修复了 Hadoop 3 带来的测试兼容性问题（CRC 校验文件、配置传播行为、依赖传递变更）。
- **影响范围**：涉及所有使用 Hadoop 依赖的模块（core、data、aliyun、aws、delta-lake、gcp、hive-metastore、orc、parquet、arrow、nessie、flink、mr、spark）的构建配置。影响测试代码的兼容性修复。
- **回迁到 1.4.x 的注意事项**：需要谨慎评估。这是一个重要的构建基础设施变更。回迁需要：(1) 确认 1.4.x 分支的最低 Java 版本为 11（否则 Hadoop 2 仍可能需要）；(2) 同步修改所有模块的 build.gradle 和 libs.versions.toml；(3) 回迁测试兼容性修复。如果 1.4.x 分支仍需支持 Hadoop 2 环境（如用户部署在 Hadoop 2 集群上），则不应回迁此提交。如果 1.4.x 已要求 Java 11+，则建议回迁以简化构建。此提交无代码级前置依赖，但需整体评估构建兼容性。
