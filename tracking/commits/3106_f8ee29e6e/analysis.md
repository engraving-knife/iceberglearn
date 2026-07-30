# 提交 3106：Core: Drop support for Java 11 (#14400)

## 提交信息

- **序号**：3106 / 4088
- **哈希**：f8ee29e6eb8b5f33ea0e91fa4406a76643cb4ef6
- **短哈希**：f8ee29e6e
- **日期**：2026-01-12
- **作者**：Manu Zhang
- **提交说明**：Core: Drop support for Java 11 (#14400)
- **PR/Issue**：#14400

## 总体目的

本提交的核心目的是正式停止对 Java 11 运行时/构建环境的支持，将 Iceberg 的最低 JDK 要求提升到 Java 17。这并不是一次临时性的清理，而是伴随整个生态演进的必然决策：上游依赖（例如 Nessie 自 0.104.2 起测试依赖已经要求 JDK 17+）、Spark 4.x 系列以及现代大数据栈都在向 Java 17/21 收敛，继续维护 Java 11 一方面要不断写 `onlyIf { JavaVersion.current() != JavaVersion.VERSION_11 }` 之类的条件分支规避依赖冲突，另一方面也让 CI 矩阵变得臃肿（每个工作流都要跑三套 JVM），增加了维护成本却没有实际收益。

从 diff 来看，作者并没有半途而废：不仅删掉了所有 CI 工作流里 `jvm: [11, 17, 21]` 矩阵中的 `11`，还把 `build.gradle` 中专门为 Java 11 准备的分支（`if (JavaVersion.current() == JavaVersion.VERSION_11)`）整体移除，把 `options.release = 11`、Scala 的 `sourceCompatibility = "11"`、`-release:11` 全部提升到 `17`，并删除了 `iceberg-nessie` 模块里因为 Nessie 不支持 Java 11 而存在的 `test.onlyIf` 兜底逻辑。这意味着从源码编译产物到字节码版本都统一为 Java 17，不再有向后兼容的余地。

此外，作者还顺手清理了 `spark-ci.yml` 里因 Java 11 而存在的 exclude 规则（原先要排除 `jvm: 11 + spark: 4.0/4.1` 的组合），因为 Java 11 已不再参与矩阵，这些排除项自然失去意义。README、contribute.md、baseline.gradle 中的注释和说明也一并更新，保证对外文档与实际构建要求一致，避免贡献者按旧文档准备环境后构建失败。

## 如何达成设计目的

整体思路是"全链路一刀切"：从 CI 矩阵、Gradle 构建脚本、Scala/Java 编译目标版本、模块级条件兜底，到面向用户和贡献者的文档，所有提及 Java 11 的位置统一替换为仅支持 17/21。涉及的文件主要是 `.github/workflows/*.yml`（CI 矩阵）、`build.gradle`（构建逻辑与编译目标）、`baseline.gradle`/`jmh.gradle`（格式化与基准测试的版本校验），以及 `README.md`、`site/docs/contribute.md`（文档说明）。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：从 delta-conversion CI 矩阵中移除 Java 11。

**工作逻辑**：两个 job 的 `matrix.jvm` 由 `[11, 17, 21]` 改为 `[17, 21]`，使该工作流不再在 Java 11 上构建和测试 delta-conversion 模块，减少无效的 CI 运行并和新的最低 JDK 要求保持一致。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：从 Flink CI 矩阵中移除 Java 11。

**工作逻辑**：`matrix.jvm` 由 `[11, 17, 21]` 改为 `[17, 21]`，Flink 各版本（1.20/2.0/2.1）只会在 Java 17 与 21 上验证。

### `.github/workflows/hive-ci.yml` (+1/-1 lines)

**修改目的**：从 Hive CI 矩阵中移除 Java 11。

**工作逻辑**：`matrix.jvm` 由 `[11, 17, 21]` 改为 `[17, 21]`。

### `.github/workflows/java-ci.yml` (+3/-3 lines)

**修改目的**：从 Java CI 的三个 job 中移除 Java 11。

**工作逻辑**：三处 `matrix.jvm` 全部由 `[11, 17, 21]` 改为 `[17, 21]`，确保核心 Java 模块的 CI 只覆盖受支持的 JDK 版本。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 lines)

**修改目的**：从 Kafka Connect CI 矩阵中移除 Java 11。

**工作逻辑**：`matrix.jvm` 由 `[11, 17, 21]` 改为 `[17, 21]`。

### `.github/workflows/spark-ci.yml` (+1/-5 lines)

**修改目的**：从 Spark CI 矩阵中移除 Java 11 并清理对应的 exclude 规则。

**工作逻辑**：`matrix.jvm` 由 `[11, 17, 21]` 改为 `[17, 21]`；同时删除了原本用于排除 `jvm: 11 + spark: 4.0` 与 `jvm: 11 + spark: 4.1` 两个组合的 exclude 项——因为 Spark 4.x 本就不计划支持 Java 11，而 Java 11 已经不在矩阵中，这些排除规则变成冗余，删除后矩阵更简洁，同时保留了因 Java 21 与 Spark 3.4 不兼容而存在的排除项。

### `README.md` (+1/-1 lines)

**修改目的**：更新对外构建说明，去掉 Java 11。

**工作逻辑**：将 "Iceberg is built using Gradle with Java 11, 17, or 21." 改为 "Java 17 or 21"，使新用户第一时间获知正确的 JDK 版本。

### `baseline.gradle` (+1/-1 lines)

**修改目的**：更新 google-java-format 版本说明中的注释。

**工作逻辑**：注释从 "produce consistent result for JDK 11/17/21" 改为 "JDK 17/21"，仅注释维护，无功能影响，保持文档与实际支持版本一致。

### `build.gradle` (+5/-12 lines)

**修改目的**：移除 Java 11 构建分支，将编译目标统一提升到 Java 17。

**工作逻辑**：
- 删除 `if (JavaVersion.current() == JavaVersion.VERSION_11)` 分支，只保留 `VERSION_17 || VERSION_21` 分支，错误提示从 "must be run with JDK 11 or 17 or 21" 改为 "JDK 17 or 21"。
- `tasks.withType(JavaCompile)` 的 `options.release = 11` 提升为 `options.release = 17`，意味着编译产物字节码版本为 Java 17。
- Scala 编译的 `sourceCompatibility`、`targetCompatibility` 与 `-release:11` 全部改为 `17`，保证 Scala 子模块字节码同样以 17 为目标。
- 删除 `iceberg-nessie` 模块中 `test.onlyIf { JavaVersion.current() != JavaVersion.VERSION_11 }` 以及相关注释——Nessie 0.104.2+ 已要求 JDK 17，而项目最低版本现在也是 17，该条件判断失去存在必要。

### `jmh.gradle` (+2/-2 lines)

**修改目的**：更新 JMH 基准测试的 JDK 校验逻辑。

**工作逻辑**：版本校验从 `jdkVersion != '11' && != '17' && != '21'` 简化为 `!= '17' && != '21'`，错误信息同步更新，确保基准测试也只能在 JDK 17/21 上运行。

### `site/docs/contribute.md` (+1/-1 lines)

**修改目的**：更新贡献者文档中的构建说明。

**工作逻辑**：与 README 同步，将 "Java 11, 17, or 21" 改为 "Java 17 or 21"，避免贡献者按旧文档准备环境后构建失败。

## 总结

本提交是 Iceberg 对 Java 11 支持的正式收尾：通过统一调整 CI 矩阵、Gradle 构建脚本（编译目标 release=17）、Scala 编译参数、JMH 校验以及用户/贡献者文档，彻底移除了 Java 11 这一构建与运行目标，并顺手清理了因 Java 11 而存在的 Spark CI exclude 规则和 Nessie 模块条件兜底。其核心价值在于简化构建与 CI 维护、与上游依赖（Nessie、Spark 4.x）的 JDK 要求对齐，并让项目字节码基线提升到 Java 17，为使用新语言特性扫清障碍；对使用者的影响是需要将运行/构建环境升级到 JDK 17 及以上。
