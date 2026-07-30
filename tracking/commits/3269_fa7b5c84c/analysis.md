# 提交 3269：Build: Bump lz4-java to 1.10.3 due to CVE-2025-12183 & CVE-2025-66566 (#14941)

## 提交信息

- **序号**：3269 / 4088
- **哈希**：fa7b5c84c46ca71c8f28d509891585d848d93d6e
- **短哈希**：fa7b5c84c
- **日期**：2026-02-17
- **作者**：slfan1989
- **提交说明**：Build: Bump lz4-java to 1.10.3 due to CVE-2025-12183 & CVE-2025-66566 (#14941)
- **PR/Issue**：#14941

## 总体目的

本提交通过 Gradle 依赖替换机制把传递依赖 `org.lz4:lz4-java` 强制升级到 `1.10.3`，以修复两个已披露的安全漏洞：`CVE-2025-12183` 与 `CVE-2025-66566`。

`lz4-java` 是 LZ4 无损压缩算法的 Java 实现，在 Iceberg 项目里属于传递依赖——它并非 Iceberg 直接声明的依赖，而是经由 Spark、Flink、Kafka、Hadoop 等下游引擎栈间接引入（这些栈普遍使用 LZ4 作为 shuffle 数据压缩、Kafka 消息压缩等）。Iceberg 自身对 LZ4 的直接使用在于 `core/src/main/java/org/apache/iceberg/puffin/PuffinCompressionCodec.java` 中定义的 `LZ4("lz4")` 编解码器（Puffin 文件的 LZ4 压缩），以及 Spark 适配器中 `SparkCompressionUtil` 默认压缩 codec 为 `"lz4"`。因此，即便 Iceberg 不直接 import lz4-java 的类，整个运行时 classpath 上的 lz4-java 版本仍会影响安全基线。

由于 lz4-java 是传递依赖，Iceberg 无法通过直接升级某个模块版本来控制它，必须用 Gradle 的 `dependencySubstitution` 在 `configurations.all` 级别做全局替换。值得注意的是本次替换指向的坐标是 `at.yawk.lz4:lz4-java`（一个由社区维护者 yawk 发布到 Maven Central 的 lz4-java 镜像/再发布坐标），而不是上游的 `org.lz4:lz4-java`。这通常是因为上游 `org.lz4:lz4-java` 在 Maven Central 的发布存在延迟或某些平台/Java 版本的兼容问题，社区会通过 `at.yawk.lz4:lz4-java` 这类镜像来获取包含修复的版本。`because(...)` 字符串显式记录了替换原因，便于后续审计与排障。

## 如何达成设计目的

整体思路是"在版本目录中声明目标版本与坐标 + 在构建脚本中加全局依赖替换"。涉及两个文件：在 `gradle/libs.versions.toml` 中新增 `lz4Java = "1.10.3"` 版本别名与 `lz4Java = { module = "at.yawk.lz4:lz4-java", version.ref = "lz4Java" }` 依赖别名；在 `build.gradle` 的 `subprojects` → `configurations.all` → `resolutionStrategy` → `dependencySubstitution` 中用 `substitute module("org.lz4:lz4-java") using module(libs.lz4Java.get().toString())` 把任何对 `org.lz4:lz4-java` 的请求替换为新坐标新版本。语义版本类型属于 patch 级升级（1.x → 1.10.3），按 lz4-java 的兼容性约定，1.x 内是 API 兼容的，因此预期对运行时行为无影响，仅带来漏洞修复。

## 修改详情

### `gradle/libs.versions.toml` (+2/-0 lines)

**修改目的**：在版本目录中声明 lz4-java 1.10.3 的版本号与坐标别名。

**工作逻辑**：在 `[versions]` 表中按字母序在 `kryo-shaded = "4.0.3"` 之后新增 `lz4Java = "1.10.3"`；在 `[libraries]` 表中按字母序在 `kafka-connect-transforms` 之后新增 `lz4Java = { module = "at.yawk.lz4:lz4-java", version.ref = "lz4Java" }`。注意坐标使用 `at.yawk.lz4` 作为 groupId，对应社区再发布镜像；`version.ref` 指向上面定义的版本号，遵循该文件统一的版本与坐标分离的声明风格。

### `build.gradle` (+5/-0 lines)

**修改目的**：在所有子项目的所有配置中强制把传递依赖 `org.lz4:lz4-java` 替换为 1.10.3。

**工作逻辑**：在 `subprojects` 块的 `configurations.all` → `exclude group/module` 列表之后新增一段 `resolutionStrategy { dependencySubstitution { substitute module("org.lz4:lz4-java") using module(libs.lz4Java.get().toString()) because("Enforce lz4-java that contains CVE-2025-12183 and CVE-2025-66566 fixes") } }`。`libs.lz4Java.get().toString()` 会展开为 `at.yawk.lz4:lz4-java:1.10.3`，Gradle 在解析依赖图时会把任何对 `org.lz4:lz4-java`（无论来自 Spark、Flink、Kafka 等传递依赖）的请求替换为该坐标，从而统一 classpath 上的 lz4-java 版本；`because(...)` 把替换原因写入解析日志，便于审计与排障。

## 总结

本提交通过在版本目录声明 `at.yawk.lz4:lz4-java:1.10.3` 并在 `build.gradle` 用全局 `dependencySubstitution` 把传递依赖 `org.lz4:lz4-java` 统一替换到含修复的版本，修补了 CVE-2025-12183 与 CVE-2025-66566 两个安全漏洞。由于 lz4-java 仅作为传递依赖经由 Spark/Flink/Kafka 等栈进入 classpath，且 Iceberg 自身对 LZ4 的使用仅限 Puffin 压缩 codec 与 Spark 默认压缩 codec，这次 patch 级升级不改变 API 行为，预期对功能无影响，仅提升运行时安全基线。
