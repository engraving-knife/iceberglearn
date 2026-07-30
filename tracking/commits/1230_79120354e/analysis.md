# 提交 1230：Build: Bump org.apache.hadoop.thirdparty:hadoop-shaded-guava (#11061)

## 提交信息

- **序号**：1230 / 4088
- **哈希**：79120354e8e05a75e9610a7b08a4333bb4491e68
- **短哈希**：79120354e
- **日期**：2024-10-12（Sat Oct 12 21:15:41 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.apache.hadoop.thirdparty:hadoop-shaded-guava (#11061)
- **PR/Issue**：#11061

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级提交。`hadoop-shaded-guava` 是 Apache Hadoop 第三方 shaded（重定位）库，它将 Google Guava 库的包名重定位为 `org.apache.hadoop.thirdparty.com.google.common.*`，以避免不同组件依赖不同版本 Guava 时产生的类路径冲突。Iceberg 的 Kafka Connect 模块通过 `force` 强制锁定此依赖的版本，确保运行时使用统一版本。

本次将 `hadoop-shaded-guava` 从 1.2.0 升级到 1.3.0，属于次版本（semver-minor）升级。

与其他同批次提交不同，此升级修改的不是全局版本目录 `libs.versions.toml`，而是 `kafka-connect/build.gradle` 中的 `force` 声明。

## 如何达成设计目的

直接修改 `kafka-connect/build.gradle` 中 `force` 强制依赖声明的版本号，从 `1.2.0` 改为 `1.3.0`。`force` 是 Gradle 的依赖强制解析机制，用于在依赖解析阶段将指定模块的版本强制覆盖为指定版本，无论传递依赖图中的其他组件要求什么版本。

## 修改详情

### `kafka-connect/build.gradle`

**修改目的**：升级 kafka-connect 模块中 hadoop-shaded-guava 的强制版本号。

**工作逻辑**：将第 75 行的 force 声明从：

```groovy
force 'org.apache.hadoop.thirdparty:hadoop-shaded-guava:1.2.0'
```

改为：

```groovy
force 'org.apache.hadoop.thirdparty:hadoop-shaded-guava:1.3.0'
```

此 `force` 声明位于 `iceberg-kafka-connect-runtime` 子项目的依赖配置中，与 `jettison`、`snappy-java`、`commons-compress` 等其他强制锁定的依赖并列。这些 `force` 声明的目的是统一 Kafka Connect 运行时打包中所有传递依赖的版本，避免版本冲突。

`hadoop-shaded-guava` 1.2.0 → 1.3.0 升级意味着底层 shaded 的 Guava 版本可能更新，Hadoop 第三方 shaded 库的 1.3.0 版本通常对应更新的 Guava 版本和 shaded 协议改进。由于是 shaded 库（包名已重定位），升级不会与 Iceberg 其他模块使用的原生 Guava 产生冲突。

## 小结

- **成效**：hadoop-shaded-guava 从 1.2.0 升级到 1.3.0，获取次版本改进。此升级仅作用于 Kafka Connect 运行时模块。
- **影响范围**：仅 `kafka-connect/build.gradle` 一个文件，1 行改动，无代码逻辑变更。影响范围限于 Kafka Connect 运行时打包。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支的 kafka-connect 模块同样使用 `force` 锁定 hadoop-shaded-guava 版本。此升级属于次版本更新，且使用 shaded 重定位机制，冲突风险低。回迁时需注意：1.3.0 版本的 hadoop-shaded-guava 可能依赖更高版本的 JDK 或引入新的 shaded Guava API，建议回迁后构建 Kafka Connect 运行时包并验证基本功能。若 1.4.x 的 Kafka Connect 模块运行稳定，可暂不回迁。
