# 提交 0875：Build: Move to `goooler` shadow plugin (#10568)

## 提交信息

- **序号**：0875 / 4088
- **哈希**：10fc04b639136af7e5bf6720e89b00637109374c
- **短哈希**：10fc04b63
- **日期**：2024-06-25 15:42:43 -0600
- **作者**：Fokko Driesprong
- **提交说明**：Build: Move to `goooler` shadow plugin (#10568)
- **PR/Issue**：#10568

## 总体目的

Iceberg 项目使用 Gradle Shadow 插件（`shadow`）来构建 fat-jar / uber-jar，用于产出 `iceberg-*-runtime`、`iceberg-*-bundle` 等运行时 shaded 包。原本使用的插件坐标是 `com.github.johnrengelman:shadow:8.1.1`，对应插件 id `com.github.johnrengelman.shadow`。该原始仓库由 johnrengelman 维护，但已长期不活跃，无法及时跟进新版 Gradle（如 8.8）的兼容性问题。

社区开发者 goooler fork 了一份该插件并持续维护，发布了新坐标 `io.github.goooler.shadow:shadow-gradle-plugin:8.1.7`，插件 id 改为 `io.github.goooler.shadow`。本提交将 Iceberg 构建中所有引用 shadow 插件的位置统一切换到 goooler 维护的版本，以保证 shadow 插件能与最新 Gradle 协同工作，避免构建链路因插件停更而受阻。

## 如何达成设计目的

实现方式是机械替换：在根 `build.gradle` 的 `buildscript.dependencies.classpath` 中把 shadow 插件的 Maven 坐标从 `com.github.johnrengelman:shadow:8.1.1` 改为 `io.github.goooler.shadow:shadow-gradle-plugin:8.1.7`；然后在所有应用该插件的子项目 `build.gradle` 中，把 `apply plugin: 'com.github.johnrengelman.shadow'` 替换为 `apply plugin: 'io.github.goooler.shadow'`。两个插件 id 走相同的任务模型（`shadowJar` 任务等），因此切换后无需改动任何 task 配置或构建逻辑。

## 修改详情

### `build.gradle`

**修改目的**：切换 shadow 插件的 Maven 坐标到 goooler 维护的版本。
**工作逻辑**：在 `buildscript.dependencies` 块中，将 `classpath 'com.github.johnrengelman:shadow:8.1.1'` 替换为 `classpath 'io.github.goooler.shadow:shadow-gradle-plugin:8.1.7'`，使构建脚本能够解析到新坐标。同时将 `:iceberg-bundled-guava` 子项目中的 `apply plugin: 'com.github.johnrengelman.shadow'` 改为 `apply plugin: 'io.github.goooler.shadow'`。

### `aws-bundle/build.gradle`、`azure-bundle/build.gradle`、`gcp-bundle/build.gradle`

**修改目的**：将三个云厂商 bundle 模块的 shadow 插件 id 切换为 `io.github.goooler.shadow`。
**工作逻辑**：每个文件仅替换 `apply plugin:` 一行，后续 `tasks.jar.dependsOn tasks.shadowJar` 等配置保持不变。

### `flink/v1.17/build.gradle`、`flink/v1.18/build.gradle`、`flink/v1.19/build.gradle`

**修改目的**：将三个 Flink 版本的 runtime 模块的 shadow 插件 id 切换为 `io.github.goooler.shadow`。
**工作逻辑**：每个文件仅替换 `iceberg-flink-runtime-*` 子项目中的 `apply plugin:` 一行，其余配置不变。

### `hive-runtime/build.gradle`、`hive3-orc-bundle/build.gradle`

**修改目的**：将 Hive runtime 与 Hive3 ORC bundle 模块的 shadow 插件 id 切换为 `io.github.goooler.shadow`。
**工作逻辑**：每个文件仅替换 `apply plugin:` 一行。

### `spark/v3.3/build.gradle`、`spark/v3.4/build.gradle`、`spark/v3.5/build.gradle`

**修改目的**：将三个 Spark 版本的 runtime 模块的 shadow 插件 id 切换为 `io.github.goooler.shadow`。
**工作逻辑**：每个文件仅替换 `iceberg-spark-runtime-*` 子项目中的 `apply plugin:` 一行，其余配置不变。

## 小结

- **成效**：将 Gradle Shadow 插件从已停更的 `com.github.johnrengelman:shadow:8.1.1` 切换到社区维护活跃的 `io.github.goooler.shadow:shadow-gradle-plugin:8.1.7`，确保 shaded jar 构建能与新版 Gradle（如 8.8）协同工作。
- **影响范围**：涉及 12 个 `build.gradle` 文件（1 个根脚本 + 11 个子项目脚本），每个文件改动 1-2 行，无生产代码或测试代码变更。
- **回迁到 1.4.x 的注意事项**：纯构建插件切换，可酌情回迁。回迁前需确认 1.4.x 分支当前所用的 shadow 插件版本与 Gradle 版本是否兼容；若 1.4.x 已使用 goooler 版本则无需回迁。该切换不改变任何产物内容（仅更换插件来源），风险极低。建议与 Gradle 8.8 升级（提交 0872）一同评估回迁。
