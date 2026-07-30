# 提交 1513 88a25967e 分析

## 提交信息
- 哈希：88a25967e72d8137147b50c6cb0a221487050369
- 日期：2024-12-19（Thu Dec 19 07:53:41 2024 -0700）
- 作者：Amogh Jahagirdar <amoghj@apache.org>
- 消息：Core, Spark, Flink, Hive: Remove unused failsafe dependency from core and add Failsafe to runtime LICENSE(s) (#11816)

## 总体目的

本提交处理 Iceberg 代码库中 `failsafe`（一个 Java 弹性编程库，用于重试、熔断、回退等）依赖的两个相关问题。

第一个问题是 `iceberg-core` 模块在 `build.gradle` 中声明了对 `failsafe` 的 `implementation` 依赖，但实际 core 源码中已不再使用它（属于历史遗留的废弃依赖）。把无用依赖留在 core 中会带来一系列副作用：它会被传递到所有依赖 core 的下游模块（Spark、Flink、Hive 等），增加 classpath 体积、可能引发依赖冲突，并且让发布产物（runtime jar）的依赖树不必要地膨胀。

第二个问题是：虽然 core 不再用 failsafe，但 failsafe 实际上仍被其他模块（例如 `iceberg-aws` 等模块通过 `ResolvingFileIO`/retry 机制）引入到 runtime shaded jar 中。Apache 项目发布规范要求每个发布的二进制 jar 必须在对应的 LICENSE 文件中声明所包含的第三方代码及其许可证。本次提交前的几个 runtime LICENSE 文件未声明 failsafe，存在合规风险。

因此本提交做两件事：（1）从 `iceberg-core` 的 `build.gradle` 中移除 `implementation libs.failsafe` 这一行；（2）为所有受影响的 runtime 模块（flink v1.18/v1.19/v1.20、hive-runtime、spark v3.3/v3.4/v3.5、kafka-connect-runtime）的 LICENSE 文件追加 failsafe 的声明条目。

## 如何达成设计目的

### 修改详情

#### `build.gradle`
- 在 `project(':iceberg-core')` 的 `dependencies` 块中删除 `implementation libs.failsafe` 一行。
- **目的**：core 模块不再直接依赖 failsafe。由于这是 `implementation` 配置（不传递给编译期下游），删除后 core 的编译 classpath 和 runtime classpath 都会变小。其他确实需要 failsafe 的模块（如 aws 模块的 retry）应在各自 `build.gradle` 中显式声明，避免通过 core 间接传递。
- **工作逻辑**：Gradle 的 `implementation` 配置使得该依赖不出现在下游 `compileClasspath`，但仍会出现在下游 `runtimeClasspath`（通过传递依赖）。删除后，core 不再向下游传递 failsafe，下游若使用需自行声明。

#### `flink/v1.18/flink-runtime/LICENSE`、`flink/v1.19/flink-runtime/LICENSE`、`flink/v1.20/flink-runtime/LICENSE`
- 三个 Flink runtime LICENSE 文件末尾各追加一段 failsafe 声明：
  ```
  --------------------------------------------------------------------------------

  This binary artifact contains failsafe.

  Copyright: Jonathan Halterman and friends
  Home page: https://failsafe.dev/
  License: http://www.apache.org/licenses/LICENSE-2.0.html
  ```
- **目的**：声明 flink-runtime shaded jar 中包含 failsafe 二进制代码及其 Apache 2.0 许可证，满足 Apache 发布合规要求。
- **注意**：v1.18/v1.19 文件末尾保留了 `No newline at end of file`（与原文件风格一致），v1.20 文件末尾则有换行。

#### `hive-runtime/LICENSE`
- 追加与上述相同格式的 failsafe 声明。
- **目的**：声明 hive-runtime jar 中包含 failsafe。

#### `kafka-connect/kafka-connect-runtime/LICENSE`
- 追加 failsafe 声明，但采用与该文件原有风格一致的 "Group/Name/Version + Project URL + License (from POM)" 格式：
  ```
  Group: dev.failsafe  Name: failsafe  Version: 3.3.2
  Project URL (from POM): https://github.com/failsafe-lib/failsafe
  License (from POM): Apache License, Version 2.0 - https://www.apache.org/licenses/LICENSE-2.0.txt
  ```
- **目的**：声明 kafka-connect-runtime jar 中包含 failsafe 3.3.2。该文件原本就采用 POM 元数据格式（与 license-maven-plugin 生成的风格一致），故沿用。

#### `spark/v3.3/spark-runtime/LICENSE`、`spark/v3.4/spark-runtime/LICENSE`、`spark/v3.5/spark-runtime/LICENSE`
- 三个 Spark runtime LICENSE 文件末尾各追加与 Flink 相同格式的 failsafe 声明。
- **目的**：声明 spark-runtime shaded jar 中包含 failsafe。

## 小结

- **成效**：清理了 core 模块中无用的 failsafe 依赖，减小依赖传递面积；同时补齐了所有 runtime 模块 LICENSE 文件对 failsafe 的声明，使发布产物符合 Apache 许可证合规要求。这属于典型的"既瘦身又补合规"的双赢改动。
- **影响范围**：构建脚本 1 处 + 8 个 LICENSE 文件，共 9 个文件，+62/-1 行。无任何代码逻辑变更，无 API 变更，无运行时行为变更。
- **回迁到 1.4.x 的注意事项**：1.4.x 作为维护分支，若其 `iceberg-core` 的 `build.gradle` 仍含 `implementation libs.failsafe` 且 core 确实不使用，则**建议回迁** build.gradle 的删除部分以保持依赖清洁；LICENSE 合规部分若 1.4.x 也发布对应 runtime jar 且未声明 failsafe，则同样**应回迁**以满足发布合规。这是低风险改动，回迁主要受益。

__tr_native_ec=$?; pwd -P >| '/var/folders/j4/8_ygb9zx7ll_gb4jlr9jd_vw0000gn/T/trae-agent-toolhost-501/jobs/job-a2bdeb22a0734f71a43804f2581f866e/cwd.txt'; exit "$__tr_native_ec"