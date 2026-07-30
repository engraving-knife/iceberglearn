# 提交 0237：Make Flink 1.18 to work

## 提交信息

- **序号**：0237 / 4088
- **哈希**：22b95dc70eb46e82a0d51fd4cfb71ad4cbdf946a
- **短哈希**：22b95dc70
- **日期**：2023-12-07 11:10:22 -0800
- **作者**：Rodrigo Meneses
- **提交说明**：Make Flink 1.18 to work
- **PR/Issue**：无（提交说明未带 PR 号）

## 总体目的

本提交是 Flink 版本迁移系列"加法"半步，与 0236（删除 1.15）配对完成一次版本轮换：把 Iceberg 的 Flink 支持矩阵从 `1.15/1.16/1.17` 升级为 `1.16/1.17/1.18`。具体而言，它做了三件事：(1) 在构建系统中移除对 Flink 1.15 的全部引用（与 0236 删除的源码对应）；(2) 在构建系统中新增 Flink 1.18 的全部依赖坐标与子项目注册，并把 `flink/v1.18/build.gradle` 中原先"借用" 1.17 坐标的占位实现替换为真正的 1.18 坐标；(3) 修复 1.18 下暴露出的两处测试兼容性问题，使新版本真正"work"。

背景是 Iceberg 维护着多版本 Flink 子模块，每个版本一份独立的 `build.gradle` 与 `libs.versions.toml` 坐标条目。`flink/v1.18/` 目录此前可能作为预占位存在但直接复用了 1.17 的依赖（`flinkMajorVersion = '1.17'`、`libs.flink117.*`），导致 `iceberg-flink-1.18` 实际是 1.17 的副本，无法构建出真正的 1.18 产物。本提交把它"激活"为名副其实的 1.18 模块，并对 CI 矩阵、默认版本、staging 脚本同步更新。

## 如何达成设计目的

整体设计是"删除一组 1.15 坐标 + 新增一组 1.18 坐标 + 把 1.18 子模块的依赖引用从 117 改到 118 + 修补测试"。改动集中在 9 个构建与测试文件，呈对称的"减 115 / 加 118"模式，再叠加两处针对 1.18 行为差异的测试代码修复。所有版本切换都通过 `gradle/libs.versions.toml`（rich version 与依赖别名）和 `settings.gradle`（子项目注册）这两个集中化的版本清单完成，`flink/build.gradle` 只做按版本号条件 `apply` 的轻量路由。

## 修改详情

### `gradle/libs.versions.toml`、`gradle.properties`、`settings.gradle`、`flink/build.gradle`、`.github/workflows/flink-ci.yml`、`dev/stage-binaries.sh`

**修改目的**：在版本清单与构建入口处把支持矩阵从 1.15/1.16/1.17 切换为 1.16/1.17/1.18。

**工作逻辑**：
- `libs.versions.toml`：删除 `flink115` rich version（`strictly = "[1.15, 1.16["`）及其 6 条主依赖别名（`flink115-avro/-connector-base/-connector-files/-metrics-dropwizard/-streaming-java/-table-api-java-bridge`）和 5 条测试依赖别名（`flink115-connector-test-utils/-core/-runtime/-test-utils/-test-utilsjunit`）；对称新增 `flink118 = { strictly = "[1.18, 1.19[", prefer = "1.18.0"}` 及对应的 6+5 条 `flink118-*` 别名。注意 rich version 的严格区间使用半开区间避免版本漂移，命名风格保持一致。
- `gradle.properties`：`systemProp.defaultFlinkVersions` 由 `1.17` 改为 `1.18`，`systemProp.knownFlinkVersions` 由 `1.15,1.16,1.17` 改为 `1.16,1.17,1.18`，使默认构建直接产出 1.18 产物。
- `settings.gradle`：删除 `flinkVersions.contains("1.15")` 分支（注册 `iceberg-flink:flink-1.15` 与 `flink-runtime-1.15` 并映射到 `flink/v1.15/` 目录），对称新增 `1.18` 分支注册 `flink-1.18` / `flink-runtime-1.18` 并映射到 `flink/v1.18/`。
- [`flink/build.gradle`](flink/build.gradle)：删除 `if (flinkVersions.contains("1.15")) apply ... v1.15/build.gradle`，新增 `1.18` 的同名 apply 块，保持按版本号条件加载子项目构建脚本的路由模式。
- `.github/workflows/flink-ci.yml`：CI 矩阵 `flink: ['1.15', '1.16', '1.17']` 改为 `['1.16', '1.17', '1.18']`，CI 同步覆盖新支持矩阵。
- `dev/stage-binaries.sh`：`FLINK_VERSIONS=1.15,1.16,1.17` 改为 `1.16,1.17,1.18`，发布 staging 同步。

### `flink/v1.18/build.gradle`

**修改目的**：把原本"复用 1.17 坐标"的 1.18 子模块改为真正依赖 Flink 1.18。

**工作逻辑**：将 `String flinkMajorVersion = '1.17'` 改为 `'1.18'`，使子项目名变为 `iceberg-flink-1.18` / `iceberg-flink-runtime-1.18`。然后把所有 `libs.flink117.*` 引用替换为 `libs.flink118.*`（avro、metrics-dropwizard、streaming-java 及其 tests、table-api-java-bridge、connector-base、connector-files、connector-test-utils、core、runtime、test-utilsjunit、test-utils），以及把 `flink-table-planner_${scalaVersion}` 的版本号引用从 `libs.versions.flink117.get()` 改为 `libs.versions.flink118.get()`。无源码改动，纯依赖坐标迁移。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java`、`flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java`

**修改目的**：修复 Flink 1.18 下两处测试兼容性问题，使 1.18 模块真正可测。

**工作逻辑**：
- [`TestHelpers.java`](flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java)：原 `assertArrayValues` 之前的类型转换 `actualArrayData = (ArrayData) actual` 在 1.18 下会因 `actual` 实际是 Java 原生数组（`int[]`/`long[]`/`byte[]` 等）而抛 `ClassCastException`，原 catch 块只能兜底 `Object[]`。本次改为调用新引入的 `convertToArray(actual)`，该方法按 `actual.getClass()` 分派到 8 种原生数组类型（`Object[]`/`int[]`/`long[]`/`float[]`/`double[]`/`short[]`/`byte[]`/`boolean[]`），分别 `new GenericArrayData(...)` 包装，无法识别则抛 `IllegalArgumentException`。这反映 1.18 的某些算子/UDT 输出在测试断言路径上返回原生数组而非 `ArrayData`。
- [`TestFlinkPackage.java`](flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java)：断言 `FlinkPackage.version()` 由 `"1.17.1"` 改为 `"1.18.0"`，与 `flink118` 的 `prefer` 版本对齐，避免版本探测测试在 1.18 模块下失败。

## 小结

本提交完成 Flink 版本迁移的"加法"半步：在构建系统中用 1.18 坐标替换 1.15 坐标、激活 `flink/v1.18` 子模块并修复两处 1.18 测试兼容性，与 0236 共同把 Iceberg 的 Flink 支持矩阵轮换到 1.16/1.17/1.18。
