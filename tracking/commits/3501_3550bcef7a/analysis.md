# 提交 3501：Core: Upgrade Jetty to 12.1.5 (#10837)

## 提交信息

- **序号**：3501 / 4088
- **哈希**：3550bcef7a6979e425f7eec7964e7fd0c07c9f7c
- **短哈希**：3550bcef7a
- **日期**：2026-04-03 16:14:19 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Upgrade Jetty to 12.1.5 (#10837)
- **PR/Issue**：#10837

## 总体目的

将 Jetty 从 11.0.26 升级到 12.1.5。这是一个跨大版本升级（11.x → 12.x），Jetty 12 引入了重大架构变化，特别是 EE10 servlet API 的分离。该升级影响 REST Catalog 测试服务器和所有 Spark 版本的测试基础设施。

## 如何达成设计目的

1. 在 `libs.versions.toml` 中将 jetty 版本从 `11.0.26` 升级到 `12.1.5`。
2. 将 `jetty-servlet` 依赖从 `org.eclipse.jetty:jetty-servlet` 改为 `org.eclipse.jetty.ee10:jetty-ee10-servlet`（Jetty 12 的 EE10 模块化分离）。
3. 更新所有 Spark 版本（3.4, 3.5, 4.0, 4.1）的 build.gradle，简化 REST Catalog 测试依赖配置（移除手动 `transitive = false` 和单独的 jetty-servlet/sqlite 声明）。
4. 新增 `DummyMetricsServlet` 类到各 Spark 版本的测试基础设施中（Jetty 12 需要显式注册 servlet）。
5. 更新 `TestBase` 初始化逻辑以适配 Jetty 12 的 API 变化。
6. 更新多个测试和 Benchmark 文件的 import 以适配 API 变化。

## 修改详情

### `gradle/libs.versions.toml` (+2/-2 lines)

**修改目的**：升级 Jetty 版本和更新 servlet 依赖坐标。

**工作逻辑**：
- `jetty = "11.0.26"` → `jetty = "12.1.5"`
- `jetty-servlet` 模块从 `org.eclipse.jetty:jetty-servlet` 改为 `org.eclipse.jetty.ee10:jetty-ee10-servlet`

### `spark/v3.4/build.gradle` (+3/-12 lines，其他版本类似)

**修改目的**：简化 REST Catalog 测试依赖配置。

**工作逻辑**：移除 `transitive = false` 限制和单独的 `testRuntimeOnly libs.jetty.servlet`、`testRuntimeOnly libs.sqlite.jdbc` 声明，改为直接使用 `testImplementation (project(path: ':iceberg-open-api', configuration: 'testFixturesRuntimeElements'))`。

### 各 Spark 版本新增 `DummyMetricsServlet.java` (+62 lines each)

**修改目的**：Jetty 12 需要 servlet 类用于测试。

### 各 Spark 版本 `TestBase.java` (+8-9 lines each)

**修改目的**：适配 Jetty 12 API 初始化。

### 测试文件（Benchmark、Extensions 等）(各 +1-3 lines)

**修改目的**：适配 Jetty 12 API 变化的 import 更新。

## 总结

Jetty 跨大版本升级提交（11.0.26 → 12.1.5）。Jetty 12 引入了 EE10 模块化分离，需要将 servlet 依赖迁移到 `org.eclipse.jetty.ee10` 坐标。修改涉及版本目录、4 个 Spark 版本的 build.gradle 依赖简化、新增 DummyMetricsServlet、更新 TestBase 初始化逻辑，以及大量测试文件的 import 适配。共修改 145 个文件。
