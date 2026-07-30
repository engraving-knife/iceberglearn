# 提交 1346：open-api: Build runtime jar for test fixture (#11279)

## 提交信息

- **序号**：1346 / 4088
- **哈希**：2ffd3b02a2aa8c02ea0a322c0410b1bffcbc62fa
- **短哈希**：2ffd3b02a
- **日期**：2024-11-06（Wed Nov 6 13:47:35 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：open-api: Build runtime jar for test fixture (#11279)
- **PR/Issue**：#11279

## 总体目的

Iceberg 的 `iceberg-open-api` 模块维护 REST Catalog 的 OpenAPI 规范（`rest-catalog-open-api.yaml`）以及对应的兼容性测试套件（RCK，REST Compatibility Kit）。RCK 通过 `src/testFixtures/java/org/apache/iceberg/rest/` 下的 `RESTCatalogServer`、`RESTServerCatalogAdapter`、`RESTServerExtension` 等类，提供了一个内嵌的 REST Catalog Server 实现，供 OpenAPI 规范的 conformance 测试（基于 schemathesis/python）和 Java 端 RCK 测试使用。

此前这些 test fixtures 只能在本仓库内被引用（其他子项目的测试可通过 `testFixturesImplementation` 拉取 `iceberg-open-api-test-fixtures`）。但对于需要在独立环境（如 Docker 容器、CI 中的 python 进程）中启动一个真实可运行的 Iceberg REST Catalog Server 来验证 OpenAPI 规范的场景，缺少一个"开箱即用"的可执行 jar。

本提交为 `iceberg-open-api` 模块新增 `shadowJar` 任务，把 test fixtures 及其完整运行时依赖打包成一个 fat jar（`iceberg-open-api-test-fixtures-runtime`），并在 manifest 中指定 `Main-Class: org.apache.iceberg.rest.RESTCatalogServer`，使其可通过 `java -jar` 直接启动一个 Iceberg REST Catalog Server。同时把该 jar 纳入 deploy 流程作为发布的 artifact，并补齐 `LICENSE`、`NOTICE` 文件以符合 Apache 发布合规要求（fat jar 内含第三方依赖，必须随 jar 提供 LICENSE/NOTICE）。

## 如何达成设计目的

- **应用 shadow 插件**：在 `project(':iceberg-open-api')` 中 `apply plugin: 'com.gradleup.shadow'`，并设置 `build.dependsOn shadowJar`，让构建时自动产出 fat jar。
- **配置 shadowJar 任务**：
  - `archiveBaseName = "iceberg-open-api-test-fixtures-runtime"`、`archiveClassifier = null`：产物名为 `iceberg-open-api-test-fixtures-runtime-<version>.jar`（无 classifier，便于作为主 artifact 发布）。
  - `configurations = [project.configurations.testFixturesRuntimeClasspath]`：把 test fixtures 的运行时类路径全部打包，包括 iceberg-core/spark/flink/各 catalog 实现等。
  - `from sourceSets.testFixtures.output`：把 testFixtures 编译产物（即 `RESTCatalogServer` 等 class）打入 jar。
  - `zip64 true`：因为依赖众多，普通 zip 索引会超过 65535 条目，启用 zip64。
  - `from(projectDir) { include 'LICENSE'; include 'NOTICE' }`：把模块根目录的 LICENSE/NOTICE 文件复制到 jar 根路径，满足 Apache 发布要求。
  - `manifest { attributes 'Main-Class': 'org.apache.iceberg.rest.RESTCatalogServer' }`：指定入口类。
- **禁用普通 jar**：`jar { enabled = false }`：因为 testFixtures 默认会产出 `iceberg-open-api-test-fixtures-<version>.jar`，但本模块本身没有 main src，普通 jar 没有意义，禁用避免重复产物干扰发布。
- **修正 hadoop3 依赖排除**：从 `testFixturesImplementation(hadoop3.common)` 中移除 `exclude group: 'log4j'`、`exclude group: 'com.fasterxml.woodstox'`、`exclude group: 'org.codehaus.woodstox'` 三项排除。原因：fat jar 需要在运行时能够加载完整的 Hadoop 栈，原本为规避测试时与 slf4j/log4j 冲突的排除项在打包成可运行 jar 后会导致缺失类，因此放开。
- **加入 deploy**：`deploy.gradle` 中 `isOpenApi` 分支额外 `artifact shadowJar`，把 fat jar 作为发布物上传到 Maven 仓库。
- **补 LICENSE/NOTICE**：新增 `open-api/LICENSE`（Apache 2.0 完整文本，555 行）与 `open-api/NOTICE`（226 行，列出 Apache Iceberg 自身以及 fat jar 内包含的所有第三方组件来源声明，包括 Jackson、Guava、Hadoop、Avro、Parquet、Spark 等）。这是 Apache 发布包含第三方依赖的 binary artifact 时的强制要求。

## 修改详情

### `build.gradle`

**修改目的**：为 `:iceberg-open-api` 启用 shadow 插件并配置 fat jar 任务。

**工作逻辑**：
- `project(':iceberg-open-api')` 块顶部新增：
  ```gradle
  apply plugin: 'java-test-fixtures'
  apply plugin: 'com.gradleup.shadow'
  build.dependsOn shadowJar
  ```
- `testFixturesImplementation(libs.hadoop3.common) { ... }` 中删除 `exclude group: 'log4j'`、`exclude group: 'com.fasterxml.woodstox'`、`exclude group: 'org.codehaus.woodstox'` 三项。
- 在 `check.dependsOn('validateRESTCatalogSpec')` 之后新增 `shadowJar { ... }` 与 `jar { enabled = false }` 配置块（具体配置如"如何达成设计目的"小节所述）。

### `deploy.gradle`

**修改目的**：把 shadowJar 产物纳入发布。

**工作逻辑**：
```gradle
} else if (isOpenApi) {
  artifact testJar
  artifact testFixturesJar
  artifact shadowJar   // 新增
}
```
即 open-api 模块除原有的 testJar、testFixturesJar 外，再额外发布 shadowJar 产物，使用户可从 Maven 中央仓库直接拉取 `iceberg-open-api-test-fixtures-runtime` 并通过 `java -jar` 启动 REST Catalog Server。

### `open-api/LICENSE`（新增）

**修改目的**：随 fat jar 一同发布 Apache License 2.0 完整文本，满足合规要求。

**工作逻辑**：标准 Apache 2.0 许可证全文（555 行），从根目录 LICENSE 复制而来。

### `open-api/NOTICE`（新增）

**修改目的**：声明 fat jar 中包含的第三方组件来源与版权。

**工作逻辑**：226 行 NOTICE 文件，包含：
- Apache Iceberg 自身版权声明（Copyright 2017-2024 The Apache Software Foundation）。
- Kite（Cloudera）代码来源声明。
- 详细列出 binary artifact 中包含的第三方项目（Jackson、Guava、Hadoop、Avro、Parquet、Spark、Netty、Jetty、各类 AWS/Azure/GCP SDK 等）及其版权声明。

## 小结

- **成效**：`iceberg-open-api` 模块现在会构建 `iceberg-open-api-test-fixtures-runtime-<version>.jar` 这一可执行 fat jar，可通过 `java -jar iceberg-open-api-test-fixtures-runtime-<version>.jar` 直接启动内嵌的 Iceberg REST Catalog Server，便于 OpenAPI 规范的 conformance 测试与第三方集成验证；该 jar 同步发布到 Maven 仓库，补齐 LICENSE/NOTICE 满足 Apache 发布合规。
- **影响范围**：仅构建脚本与发布配置；无 Java 源码变更（test fixtures 类未改动）；新增 LICENSE/NOTICE 是纯文档。
- **回迁到 1.4.x 的注意事项**：
  - 这是构建/发布基础设施改进，不引入运行时行为变化，**可考虑回迁**到 1.4.x 以便该分支也能产出可运行的 REST Catalog Server 测试 jar。
  - 回迁前需确认 1.4.x 的 `build.gradle`/`deploy.gradle` 与本提交基线兼容（如 `com.gradleup.shadow` 插件版本、`testFixturesRuntimeClasspath` 配置是否可用），以及 `RESTCatalogServer` 类在 1.4.x 中的实现与本提交假设一致。
  - 若 1.4.x 不需要发布 open-api runtime jar（如该分支不发布 open-api 模块），可跳过本提交。
  - LICENSE/NOTICE 文件随源码复制即可，无需特殊处理。
