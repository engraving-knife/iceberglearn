# 提交 2658：Flink: Adjust build scripts for Flink 2.1

## 提交信息

- **序号**：2658 / 4088
- **哈希**：58da08695b8595cd93847a7f3133edcc82a9ca5f
- **短哈希**：58da08695
- **日期**：2025-09-19 07:36:48 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Adjust build scripts for Flink 2.1
- **PR/Issue**：无（此提交是 Flink 2.1 迁移系列的一部分）

## 总体目的

本提交是 Flink 2.1 版本支持迁移工作的第三步，调整 Gradle 构建脚本以正式支持 Flink 2.1 版本。在前两个提交（2655、2656）中已经创建了 `flink/v2.1/` 目录并恢复了 `flink/v2.0/` 目录，但构建系统尚未配置识别 v2.1。

本提交通过以下方式使构建系统支持 Flink 2.1：
1. 在版本目录（libs.versions.toml）中添加 Flink 2.1.0 的依赖定义
2. 在 settings.gradle 中注册 v2.1 的项目模块
3. 在 flink/build.gradle 中添加 v2.1 的构建入口
4. 将 v2.1/build.gradle 中的依赖引用从 flink20 改为 flink21
5. 更新 gradle.properties 中的默认和已知 Flink 版本列表

## 如何达成设计目的

通过 5 个构建配置文件的协同修改，完成 Flink 2.1 的构建集成：

1. **版本定义**：在 `libs.versions.toml` 中新增 `flink21 = "2.1.0"` 版本和对应的依赖库引用
2. **项目注册**：在 `settings.gradle` 中注册 `iceberg-flink-2.1` 和 `iceberg-flink-runtime-2.1` 两个子项目
3. **构建入口**：在 `flink/build.gradle` 中添加 v2.1 的条件应用逻辑
4. **依赖切换**：在 `flink/v2.1/build.gradle` 中将所有 `flink20` 依赖引用改为 `flink21`
5. **默认配置**：在 `gradle.properties` 中将默认 Flink 版本设为 2.1，并在已知版本列表中添加 2.1

## 修改详情

### `flink/build.gradle` (+4/-0 lines)

**修改目的**：添加 Flink 2.1 的构建入口。

**工作逻辑**：在文件末尾新增条件块，当 `flinkVersions` 包含 "2.1" 时，应用 `v2.1/build.gradle` 构建脚本。

### `flink/v2.1/build.gradle` (+18/-18 lines)

**修改目的**：将 v2.1 构建文件中的依赖从 Flink 2.0 切换到 Flink 2.1。

**工作逻辑**：
- 将 `flinkMajorVersion` 从 `'2.0'` 改为 `'2.1'`
- 将所有 `libs.flink20.*` 依赖引用改为 `libs.flink21.*`，包括：avro、metrics-dropwizard、streaming-java、table-api-java-bridge、connector-base、connector-files、connector-test-utils、core、runtime、test-utils、test-utilsjunit
- 将 `libs.versions.flink20` 引用改为 `libs.versions.flink21`

### `gradle.properties` (+2/-2 lines)

**修改目的**：更新默认 Flink 版本和已知版本列表。

**工作逻辑**：
- `defaultFlinkVersions` 从 `2.0` 改为 `2.1`
- `knownFlinkVersions` 从 `1.19,1.20,2.0` 改为 `1.19,1.20,2.0,2.1`

### `gradle/libs.versions.toml` (+12/-0 lines)

**修改目的**：添加 Flink 2.1.0 的版本号和依赖库定义。

**工作逻辑**：
- 新增版本定义 `flink21 = { strictly = "2.1.0"}`
- 新增 6 个主依赖库：`flink21-avro`、`flink21-connector-base`、`flink21-connector-files`、`flink21-metrics-dropwizard`、`flink21-streaming-java`、`flink21-table-api-java-bridge`
- 新增 5 个测试依赖库：`flink21-connector-test-utils`、`flink21-core`、`flink21-runtime`、`flink21-test-utils`、`flink21-test-utilsjunit`

### `settings.gradle` (+9/-0 lines)

**修改目的**：注册 Flink 2.1 的 Gradle 子项目。

**工作逻辑**：新增条件块，当 `flinkVersions` 包含 "2.1" 时：
- include `:iceberg-flink:flink-2.1` 和 `:iceberg-flink:flink-runtime-2.1`
- 设置项目目录为 `flink/v2.1/flink` 和 `flink/v2.1/flink-runtime`
- 设置项目名称为 `iceberg-flink-2.1` 和 `iceberg-flink-runtime-2.1`

## 总结

本提交完成了 Flink 2.1 的 Gradle 构建集成，包括依赖定义、项目注册和构建入口配置。通过将 v2.1 构建文件中的所有 Flink 2.0 依赖切换为 Flink 2.1，并将默认 Flink 版本更新为 2.1，使构建系统能够正确编译和打包 Flink 2.1 的 Iceberg 集成模块。这是 Flink 2.1 迁移系列中的关键构建配置步骤。
