# 提交 1233：Build: Use the active shadow plugin (#11315)

## 提交信息

- **序号**：1233 / 4088
- **哈希**：5832a7adb7f7121cd4bc67acb8be2ecf8c1fa5c3
- **短哈希**：5832a7adb
- **日期**：2024-10-14（Mon Oct 14 16:16:25 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Build: Use the active shadow plugin (#11315)
- **PR/Issue**：#11315

## 总体目的

Iceberg 通过 Gradle Shadow 插件构建各类 fat-jar / runtime-jar / bundle 产物（如 `iceberg-spark-runtime`、`iceberg-flink-runtime`、`iceberg-aws-bundle`、`iceberg-azure-bundle`、`iceberg-gcp-bundle`、`iceberg-hive-runtime`、`iceberg-hive3-orc-bundle`、`iceberg-bundled-guava` 等）。原仓库使用 `io.github.goooler.shadow:shadow-gradle-plugin:8.1.8` 这个 fork 版本（插件 ID 为 `io.github.goooler.shadow`）。

由于上游原版 Shadow 插件已迁移到 `com.gradleup.shadow` 命名空间并由 GradleUp 组织维护（活跃版本），而 `goooler` fork 后续活跃度下降。本提交把整个仓库的 Shadow 插件统一从 `io.github.goooler.shadow` 切换到 `com.gradleup.shadow`（活跃维护版本），同时把 `build.gradle` buildscript classpath 中的版本号从 `8.1.8` 升级到 `8.3.3`。这样可避免使用已停止维护的 fork，并获得后续修复与新版 Gradle 的兼容性。

## 如何达成设计目的

1. 在 `build.gradle` 的 `buildscript.dependencies` 中将 `classpath 'io.github.goooler.shadow:shadow-gradle-plugin:8.1.8'` 改为 `classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.3'`，把插件坐标和版本号一并切换。
2. 在所有 11 个使用 `apply plugin: 'io.github.goooler.shadow'` 的子工程 `build.gradle` 中，把插件 ID 替换为 `'com.gradleup.shadow'`。
3. 没有改动任何业务逻辑或 API，仅构建脚本调整。由于新插件与旧插件 API 兼容（同名 task `shadowJar` 等），各工程 `tasks.jar.dependsOn tasks.shadowJar` 的既有约定可继续工作。

## 修改详情

### `build.gradle`

**修改目的**：切换 Shadow 插件 classpath 坐标到 GradleUp 维护的活跃版本。

**工作逻辑**：在 `buildscript { dependencies { ... } }` 中：

```diff
-    classpath 'io.github.goooler.shadow:shadow-gradle-plugin:8.1.8'
+    classpath 'com.gradleup.shadow:shadow-gradle-plugin:8.3.3'
```

同时在 `project(':iceberg-bundled-guava')` 中将 `apply plugin: 'io.github.goooler.shadow'` 改为 `apply plugin: 'com.gradleup.shadow'`。

### 各 bundle / runtime 模块 `build.gradle`（11 处）

涉及文件：

- `aws-bundle/build.gradle`
- `azure-bundle/build.gradle`
- `gcp-bundle/build.gradle`
- `hive-runtime/build.gradle`
- `hive3-orc-bundle/build.gradle`
- `flink/v1.18/build.gradle`
- `flink/v1.19/build.gradle`
- `flink/v1.20/build.gradle`
- `spark/v3.3/build.gradle`
- `spark/v3.4/build.gradle`
- `spark/v3.5/build.gradle`

**修改目的**：把各模块应用的 Shadow 插件 ID 统一切换。

**工作逻辑**：每个文件中：

```diff
-  apply plugin: 'io.github.goooler.shadow'
+  apply plugin: 'com.gradleup.shadow'
```

这些模块均依赖 Shadow 插件提供的 `shadowJar` task 来产出包含全部依赖的 runtime/bundle jar；切换插件 ID 后，task 名称与行为保持一致，原有的 `tasks.jar.dependsOn tasks.shadowJar` 链路不变。

## 小结

- **成效**：仓库构建工具链从维护停滞的 `goooler` fork 切换到 GradleUp 官方活跃维护的 Shadow 插件（版本 8.1.8 → 8.3.3），降低未来与新版 Gradle 不兼容的风险，并持续获得上游修复。
- **影响范围**：仅构建脚本（12 个 `build.gradle` 文件），共 13 行改动；无任何产品代码、API 或测试逻辑变更，不影响运行时行为。
- **回迁到 1.4.x 的注意事项**：1.4.x 作为维护分支，发布产物已经依赖特定 Shadow 版本。是否回迁需权衡：
  - 若 1.4.x 当前构建在更新版 Gradle 上失败，回迁此修复可解决；
  - 若 1.4.x 构建正常，可暂不回迁，避免无谓改动发布产物元信息。
  - 若回迁，必须同时把 `build.gradle` 的 classpath 坐标和所有 11 个子模块 `apply plugin:` ID 一并改，缺一会导致 Shadow 任务找不到。同时注意 1.4.x 中可能存在的 Spark 3.2 / Flink 1.17 / Flink 1.16 / Hive 2 等更老模块的 `build.gradle`（不在本提交范围内）也应同步切换，否则该模块会沿用旧插件。
