# 提交 0921：Build: don't include slf4j-api in bundled JARs (#10665)

## 提交信息

- **序号**：0921 / 4088
- **哈希**：048c92d43d6e1180e399ee9c68ad952310230b2b
- **短哈希**：048c92d43
- **日期**：2024-07-11 06:47:12 -0700
- **作者**：Devin Smith <devinsmith@deephaven.io>
- **提交说明**：Build: don't include slf4j-api in bundled JARs (#10665)
- **PR/Issue**：#10665（Fixes #10534）

## 总体目的

Iceberg 提供了多个 bundle JAR（`iceberg-aws-bundle`、`iceberg-azure-bundle`、`iceberg-gcp-bundle`、`iceberg-hive3-orc-bundle`），通过 Gradle Shadow 插件将第三方依赖打包（shade）进单个 JAR 中，方便用户在无需自行管理依赖冲突的情况下使用对应的云存储集成。

然而，`slf4j-api`（Simple Logging Facade for Java）作为一个日志门面框架，其设计意图是由应用程序在运行时提供具体的日志实现（如 `slf4j-simple`、`logback`、`log4j2` 等）。如果 `slf4j-api` 被打包（shade）进 bundle JAR，会导致以下问题（issue #10534）：

1. **类冲突**：bundle JAR 中被 relocate 的 `slf4j-api` 类与应用程序自己引入的 `slf4j-api` 类不兼容，导致 `ClassCastException` 或日志框架初始化失败。
2. **日志失控**：应用程序无法通过自己的 `slf4j-api` 实现来控制 Iceberg bundle 内部的日志行为，因为 bundle 内部的日志调用走的是被 shade 后的独立 `slf4j-api` 副本。

本提交的目的是将 `slf4j-api` 从四个 bundle JAR 的 shadow 打包中排除，使其不被打包进 bundle，而是由应用程序在运行时提供。这与 `iceberg-bundled-guava` 模块中已有的排除模式一致。

## 如何达成设计目的

在每个 bundle 模块的 `build.gradle` 的 `shadowJar` 配置块中，新增 `dependencies { exclude(dependency('org.slf4j:slf4j-api')) }`，告知 Shadow 插件在打包时排除 `org.slf4j:slf4j-api` 依赖的类。这样 bundle JAR 中不再包含 `slf4j-api` 的类，运行时由应用程序的类路径提供。

该模式与 `iceberg-bundled-guava/build.gradle` 中已有的排除写法完全一致，保持项目内的一致性。

## 修改详情

### `aws-bundle/build.gradle`

**修改目的**：从 `iceberg-aws-bundle` 的 shadow JAR 中排除 `slf4j-api`。

**工作逻辑**：在 `shadowJar` 任务的配置块中、`relocate` 指令之前，新增：
```groovy
dependencies {
  exclude(dependency('org.slf4j:slf4j-api'))
}
```
Shadow 插件在打包时会跳过 `org.slf4j:slf4j-api` 依赖的所有类文件，不将其包含在最终 JAR 中。

### `azure-bundle/build.gradle`

**修改目的**：从 `iceberg-azure-bundle` 的 shadow JAR 中排除 `slf4j-api`。

**工作逻辑**：与 `aws-bundle` 完全相同的 `exclude` 配置。

### `gcp-bundle/build.gradle`

**修改目的**：从 `iceberg-gcp-bundle` 的 shadow JAR 中排除 `slf4j-api`。

**工作逻辑**：与 `aws-bundle` 完全相同的 `exclude` 配置。

### `hive3-orc-bundle/build.gradle`

**修改目的**：从 `iceberg-hive3-orc-bundle` 的 shadow JAR 中排除 `slf4j-api`。

**工作逻辑**：与 `aws-bundle` 完全相同的 `exclude` 配置。

## 小结

- **成效**：成功将 `slf4j-api` 从四个 bundle JAR（aws、azure、gcp、hive3-orc）的 shadow 打包中排除，解决了 issue #10534 中报告的日志框架类冲突问题。bundle JAR 不再包含 `slf4j-api` 类，由应用程序运行时提供，保证应用程序对日志的统一控制。
- **影响范围**：涉及 4 个 bundle 模块的 `build.gradle` 文件，每个文件新增 4 行（共 16 行新增）。仅影响构建打包行为，不改变任何源代码逻辑。影响的产物是四个 bundle JAR 的内容（体积略减，不再含 `slf4j-api` 类）。
- **回迁到 1.4.x 的注意事项**：适合回迁，风险低。这是一个构建配置修复，解决了 bundle JAR 使用者的实际问题。需确认 1.4.x 分支中这四个 bundle 模块的 `build.gradle` 结构与 main 一致（都有 `shadowJar` 配置块），以及 `iceberg-bundled-guava` 中的参考模式是否已存在。回迁后需验证 bundle JAR 不再包含 `slf4j-api` 类。注意：使用这些 bundle JAR 的应用程序必须在自己的类路径上提供 `slf4j-api` 依赖，否则运行时会报 `ClassNotFoundException`，但这是标准做法。
