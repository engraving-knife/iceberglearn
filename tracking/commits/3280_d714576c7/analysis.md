# 提交 3280：Revert "Build: Bump roaringbitmap from 1.3.0 to 1.6.0 (#14991)" (#15358)

## 提交信息

- **序号**：3280 / 4088
- **哈希**：d714576c7d53300e4b38d5ee27743dedbb153ace
- **短哈希**：d714576c7
- **日期**：2026-02-19
- **作者**：Amogh Jahagirdar
- **提交说明**：Revert "Build: Bump roaringbitmap from 1.3.0 to 1.6.0 (#14991)" (#15358)
- **PR/Issue**：#15358（回退 #14991）

## 总体目的

本提交回退了先前 #14991 将 RoaringBitmap 依赖从 1.3.0 升级到 1.6.0 的改动。RoaringBitmap 是 Iceberg 中用于位图删除追踪（如 position delete 索引、delete filter 中判断行是否被删除）的核心依赖。原 #14991 在升级到 1.6.0 时，将依赖坐标从 Maven Central 上的官方坐标 `org.roaringbitmap:RoaringBitmap` 改为 JitPack 上的 `com.github.RoaringBitmap.RoaringBitmap:roaringbitmap`，并在 `build.gradle` 中新增了 `https://jitpack.io/` 仓库；同时为 Spark 各版本的 `spark-hive` 集成测试依赖添加了 `exclude group: 'org.roaringbitmap'` 排除规则，以避免 Spark 自身引入的 Maven Central 版 RoaringBitmap 与 JitPack 版产生两份 jar 冲突。

这一升级方式引入了若干问题：JitPack 作为第三方构建仓库不如 Maven Central 稳定可靠，构建可重现性下降；JitPack 坐标与 Spark 传递依赖的 `org.roaringbitmap` group 不同，导致类路径上可能同时存在两个不同 group 的 RoaringBitmap artifact，排障复杂；额外添加的 exclude 规则也增加了构建脚本的维护负担。因此维护者决定回退这次升级，恢复到稳定的 1.3.0 + Maven Central 官方坐标方案，待后续以更稳妥的方式（直接使用 Maven Central 发布的更高版本）重新升级。

## 如何达成设计目的

通过 `git revert` 完整还原 #14991 的全部改动：将 `gradle/libs.versions.toml` 中的版本与坐标改回 1.3.0 和 `org.roaringbitmap:RoaringBitmap`，从根 `build.gradle` 移除 JitPack 仓库，从四个 Spark 版本的 `build.gradle` 移除 `spark-hive` 依赖上的 `exclude group: 'org.roaringbitmap'`，并将三处 LICENSE 文件中的版本号同步改回 1.3.0。

## 修改详情

### `gradle/libs.versions.toml` (+2/-2 lines)

**修改目的**：回退 RoaringBitmap 版本与 Maven 坐标。

**工作逻辑**：
版本号 `roaringbitmap = "1.6.0"` 改回 `"1.3.0"`；依赖坐标由 `com.github.RoaringBitmap.RoaringBitmap:roaringbitmap`（JitPack）改回 `org.roaringbitmap:RoaringBitmap`（Maven Central 官方坐标）。RoaringBitmap 在 Iceberg 中用于 `DeleteFilter`、位置删除位图等场景，回退后依赖解析重新走 Maven Central，保证构建稳定性。

### `build.gradle` (+0/-1 lines)

**修改目的**：移除 JitPack 仓库。

**工作逻辑**：
从 `allprojects { repositories { ... } }` 中删除 `maven { url 'https://jitpack.io/' }`。该仓库是 #14991 为解析 JitPack 坐标而新增的，回退后不再需要。

### `spark/v3.4/build.gradle`、`spark/v3.5/build.gradle`、`spark/v4.0/build.gradle`、`spark/v4.1/build.gradle` (各 +1/-3 lines)

**修改目的**：移除 spark-hive 集成测试依赖上的 RoaringBitmap 排除规则。

**工作逻辑**：
四个 Spark 版本的 `iceberg-spark-runtime` 模块中，`integrationImplementation("org.apache.spark:spark-hive_${scalaVersion}:...") { exclude group: 'org.roaringbitmap' }` 改回不带闭包的普通依赖声明。由于 RoaringBitmap 坐标恢复为 `org.roaringbitmap`，与 Spark 自身传递依赖的 group 一致，Gradle 会自动按版本协商解决，不再需要手动排除。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE`、`kafka-connect/kafka-connect-runtime/main/LICENSE`、`open-api/LICENSE` (各 +1/-1 lines)

**修改目的**：同步 LICENSE 文件中的 RoaringBitmap 版本号。

**工作逻辑**：
三处 LICENSE 文件中 `Group: org.roaringbitmap Name: RoaringBitmap Version: 1.6.0` 改回 `Version: 1.3.0`，保持许可声明与实际依赖版本一致。

## 总结

本提交回退了 #14991 对 RoaringBitmap 的升级，恢复到 Maven Central 官方坐标 `org.roaringbitmap:RoaringBitmap:1.3.0`，移除了 JitPack 仓库与 spark-hive 依赖上的排除规则，并同步更新 LICENSE。这消除了 JitPack 坐标带来的构建不稳定与潜在的双 artifact 冲突风险，是依赖治理的合理回退。
