# 提交 3134：Build: Bump roaringbitmap from 1.3.0 to 1.6.0 (#14991)

## 提交信息

- **序号**：3134 / 4088
- **哈希**：8967729beac20f1fbcbe6b1f43ae010525a2c6f5
- **短哈希**：8967729be
- **日期**：2026-01-19
- **作者**：Manu Zhang
- **提交说明**：Build: Bump roaringbitmap from 1.3.0 to 1.6.0 (#14991)
- **PR/Issue**：#14991

## 总体目的

本提交将 RoaringBitmap 依赖从 `1.3.0` 升级到 `1.6.0`，但由于新版坐标发布渠道的变化，本次升级不只是改版本号——它同时切换了 Maven 坐标来源、新增了 JitPack 仓库，并在 Spark 集成构建中排除了 Spark 自带的 RoaringBitmap 以避免冲突。

RoaringBitmap 是 Iceberg 中用于高效位图索引的关键依赖，主要服务于删除位置追踪：`core` 模块的 `BitmapPositionDeleteIndex`（基于位图记录被删除的行位置）和 `SortingPositionOnlyDeleteWriter`（写位置删除时用 RoaringBitmap 收集删除位置）都依赖它；Spark 扩展中的 `MergeRowsExec` 也用到。RoaringBitmap 提供压缩有序整数集合的高效运算（并、交、补），对 position delete 的等值过滤性能至关重要。升级到 1.6.0 可获取上游累积的性能优化与缺陷修复。

值得注意的是，本次升级把坐标从 Maven Central 的 `org.roaringbitmap:RoaringBitmap` 切换为 JitPack 的 `com.github.RoaringBitmap.RoaringBitmap:roaringbitmap`。这通常是因为目标版本尚未（或不会）发布到 Maven Central，转而通过 JitPack 直接从 GitHub 仓库构建发布包。因此需要在构建中新增 JitPack 仓库。由于 Spark 自身也传递依赖了 `org.roaringbitmap`（旧 Maven Central 坐标），若不排除会与 Iceberg 新引入的 JitPack 版本形成两套不同坐标的 RoaringBitmap 共存，引发类路径冲突，故在 Spark 各版本的集成构建中对 `spark-hive` 排除 `org.roaringbitmap` 组。本次升级类型为次版本升级（1.3.0 → 1.6.0，跨越 3 个次版本），按语义化版本约定引入向后兼容的新功能与改进。

## 如何达成设计目的

改动分四部分：在 `libs.versions.toml` 更新版本号并切换库坐标到 JitPack；在根 `build.gradle` 新增 `jitpack.io` 仓库以解析新坐标；在 Spark v3.4/v3.5/v4.0/v4.1 四个版本的 `build.gradle` 中对 `spark-hive` 集成依赖排除 `org.roaringbitmap` 组；同步更新 kafka-connect 与 open-api 的 LICENSE 文件中的版本声明。作者为 Manu Zhang（非 dependabot 自动提交），属于人工处理的依赖升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级版本号并切换 RoaringBitmap 的 Maven 坐标。

**工作逻辑**：
`[versions]` 段 `roaringbitmap` 由 `1.3.0` 改为 `1.6.0`；`[libraries]` 段模块坐标由 `org.roaringbitmap:RoaringBitmap`（Maven Central）改为 `com.github.RoaringBitmap.RoaringBitmap:roaringbitmap`（JitPack 坐标格式 `com.github.<owner>.<repo>:<module>`）。该坐标由各模块通过 `libs.roaringbitmap` 消费（如 `core`、`spark` 扩展等）。

### `build.gradle` (+1 line)

**修改目的**：新增 JitPack 仓库以解析新的 RoaringBitmap 坐标。

**工作逻辑**：
在 `allprojects { repositories { ... } }` 中新增 `maven { url 'https://jitpack.io/' }`，使所有子项目都能从 JitPack 拉取 `com.github.RoaringBitmap.RoaringBitmap:roaringbitmap` 制品。与已有的 `mavenCentral()`、`mavenLocal()` 并列。

### `spark/v3.4/build.gradle`、`spark/v3.5/build.gradle`、`spark/v4.0/build.gradle`、`spark/v4.1/build.gradle` (各 +2/-1 lines)

**修改目的**：在 Spark 集成测试中排除 Spark 自带的 RoaringBitmap，避免与新坐标冲突。

**工作逻辑**：
四个 Spark 版本模块的 `iceberg-spark-runtime` 集成构建中，对 `integrationImplementation "org.apache.spark:spark-hive_..."` 依赖改为闭包形式并添加 `exclude group: 'org.roaringbitmap'`。这样 Spark 传递引入的旧坐标 RoaringBitmap 不会出现在集成测试类路径上，确保测试使用 Iceberg 声明的 JitPack 版本，避免同一库两套坐标共存导致的 `NoSuchMethodError`/`ClassNotFoundException` 等冲突。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE`、`kafka-connect/kafka-connect-runtime/main/LICENSE`、`open-api/LICENSE` (各 +1/-1 lines)

**修改目的**：同步更新许可证清单中的 RoaringBitmap 版本声明。

**工作逻辑**：
将分发许可证文件中 `Group: org.roaringbitmap Name: RoaringBitmap Version: 1.3.0` 更新为 `Version: 1.6.0`，保持 LICENSE 文件与实际打包依赖一致，满足分发合规要求。

## 总结

本提交将 RoaringBitmap 从 1.3.0 升级到 1.6.0 以获取上游性能与修复，但由于新版改走 JitPack 发布，相应切换了 Maven 坐标、新增了 JitPack 仓库，并在 Spark 集成构建中排除 Spark 自带的旧坐标 RoaringBitmap 以规避类路径冲突，同时同步更新了许可证清单，是一次兼顾依赖升级与构建兼容性的完整维护。
