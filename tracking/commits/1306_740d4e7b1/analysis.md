# 提交 1306：open-api: Fix `testFixtures` dependencies (#11422)

## 提交信息

- **序号**：1306 / 4088
- **哈希**：740d4e7b1ced2c1b4549edf9f6189003f0e06c2c
- **短哈希**：740d4e7b1
- **日期**：2024-10-29（Tue Oct 29 19:19:10 2024 +0100）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：open-api: Fix `testFixtures` dependencies
- **PR/Issue**：#11422

## 总体目的

`iceberg-open-api` 模块在 `testFixtures`（Gradle 的测试夹具源集，供本模块与依赖方共享测试代码）中此前依赖了一行：

```groovy
testFixturesImplementation project(':iceberg-core').sourceSets.test.runtimeClasspath
```

这种写法把 `iceberg-core` 的整个测试 `runtimeClasspath`（包括所有测试类、测试依赖的传递闭包）直接拉进 `iceberg-open-api` 的 testFixtures 实现依赖。这有两个明显问题：

1. **过度耦合**：`iceberg-core` 测试源集里的任意改动都会传染到 `iceberg-open-api` 的 testFixtures，构建缓存命中率低、增量构建失效；
2. **传递依赖不可控**：`iceberg-core` 测试用到的所有第三方库（Hadoop、Jetty、Avro 等）会以不可见的方式进入 testFixtures 的 classpath，难以独立维护或裁剪，也容易在版本升级时引入冲突。

本提交移除该粗放依赖，改为按需显式声明 testFixtures 真正需要的依赖项，使得依赖图清晰、可维护。

## 如何达成设计目的

把 `project(':iceberg-core').sourceSets.test.runtimeClasspath` 整行删除，替换为以下显式依赖：

1. **`libs.hadoop3.common`**：testFixtures 需要 Hadoop `FileSystem`/`Path` 等基础类型来构造测试用例（如读取 manifest 文件、构造 InputFile/OutputFile）。出于避免与 Iceberg 自带版本冲突的考虑，排除了 Hadoop 传递进来的 log4j、slf4j、reload4j、avro、woodstox、guava、protobuf、curator、zookeeper、kerby、hadoop-auth、commons-configuration2、hadoop-shaded-protobuf_3_7、jetty 等一系列子依赖，只保留 Hadoop common 本身的核心类。
2. **`project(path: ':iceberg-bundled-guava', configuration: 'shadow')`**：用 Iceberg 自打包的 guava（shadow configuration）替代 Hadoop 传递进来的 guava，避免版本冲突。
3. **`libs.junit.jupiter`**：显式引入 JUnit 5，因为 testFixtures 此前是通过 `iceberg-core` 的测试 classpath 间接获得 JUnit，现在需要单独声明。

原有的 `project(':iceberg-api')`、`project(':iceberg-core')`、`project(path: ':iceberg-core', configuration: 'testArtifacts')`、`project(':iceberg-aws')`、`project(':iceberg-gcp')`、`project(':iceberg-azure')` 与 `libs.jetty.servlet`、`libs.jetty.server` 等保留不变。

## 修改详情

### `build.gradle`（修改，+19/-1 行）

**修改目的**：清理 `iceberg-open-api` 模块 testFixtures 的依赖声明。

**工作逻辑**：

在 `project(':iceberg-open-api')` 块的 `testFixturesImplementation` 区域：

- 删除：`testFixturesImplementation project(':iceberg-core').sourceSets.test.runtimeClasspath`
- 新增：
  ```groovy
  testFixturesImplementation(libs.hadoop3.common) {
    exclude group: 'log4j'
    exclude group: 'org.slf4j'
    exclude group: 'ch.qos.reload4j'
    exclude group: 'org.apache.avro', module: 'avro'
    exclude group: 'com.fasterxml.woodstox'
    exclude group: 'com.google.guava'
    exclude group: 'com.google.protobuf'
    exclude group: 'org.apache.curator'
    exclude group: 'org.apache.zookeeper'
    exclude group: 'org.apache.kerby'
    exclude group: 'org.apache.hadoop', module: 'hadoop-auth'
    exclude group: 'org.apache.commons', module: 'commons-configuration2'
    exclude group: 'org.apache.hadoop.thirdparty', module: 'hadoop-shaded-protobuf_3_7'
    exclude group: 'org.codehaus.woodstox'
    exclude group: 'org.eclipse.jetty'
  }
  testFixturesImplementation project(path: ':iceberg-bundled-guava', configuration: 'shadow')
  testFixturesImplementation libs.junit.jupiter
  ```

排除项选择遵循以下原则：

- `log4j`、`org.slf4j`、`ch.qos.reload4j`：日志实现交给 Iceberg 项目级统一配置，避免 Hadoop 强行引入 reload4j/log4j 1.x；
- `org.apache.avro:avro`：Iceberg 自己有 `iceberg-avro` 模块与版本管理；
- `com.fasterxml.woodstox`、`org.codehaus.woodstox`：XML stax 实现，Hadoop 配置解析需要但 Iceberg 测试不需要；
- `com.google.guava`：改由 `iceberg-bundled-guava` shadow 提供，避免版本冲突；
- `com.google.protobuf`：Hadoop 内部序列化用，Iceberg open-api 测试用不到；
- `org.apache.curator`、`org.apache.zookeeper`、`org.apache.kerby`：Hadoop HA / 安全相关，本测试不需要；
- `hadoop-auth`、`commons-configuration2`、`hadoop-shaded-protobuf_3_7`：Hadoop 自身内部依赖；
- `org.eclipse.jetty`：Hadoop 测试可能用到，但 Iceberg open-api testFixtures 已单独声明 `libs.jetty.*`，避免重复。

## 小结

- **成效**：`iceberg-open-api` 模块的 testFixtures 依赖图从"全量继承 iceberg-core 测试 classpath"改为"显式声明 3 项 + 大量 exclude 的 hadoop3.common"，依赖更精简可控，构建缓存命中率提升，避免不相关的传递依赖污染。
- **影响范围**：仅根 `build.gradle` 一个文件，纯依赖配置变更，无源代码改动。对 `iceberg-open-api` 模块 testFixtures 的实际classpath 做了收紧，理论上不应改变其能访问的类集合（因为之前能通过 `iceberg-core` 测试 classpath 间接拿到的 Hadoop common、guava、JUnit 现在都显式提供了）。
- **回迁到 1.4.x 的注意事项**：
  1. 1.4.x 分支的 `build.gradle` 可能尚未引入 `libs.hadoop3.common` 这一项依赖别名（取决于 1.4.x 的 `gradle/libs.versions.toml`），回迁前需确认该别名存在；
  2. `iceberg-bundled-guava` 的 `shadow` configuration 在 1.4.x 上应已存在（该项目长期维护），可直接复用；
  3. 若 1.4.x 上 `iceberg-open-api` 的 testFixtures 此前并未使用 `project(':iceberg-core').sourceSets.test.runtimeClasspath`（即 1.4.x 上原本就是显式声明），本提交可能不适用，需对比 1.4.x 现状；
  4. 排除项清单是按 main 分支当时的 Hadoop 3.4.x 传递依赖整理的，1.4.x 若 Hadoop 版本不同（如仍用 3.3.x），传递依赖集合可能略有差异，回迁后建议跑一次 `./gradlew :iceberg-open-api:dependencies --configuration testFixturesRuntimeClasspath` 验证。
