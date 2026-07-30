# 提交 0630：Build: Bump Spark from 3.5 to 3.5.1

## 提交信息

- **序号**：0630 / 4088
- **哈希**：b6cbb528ee38fa990ac9032dc44932060094e98d
- **短哈希**：b6cbb528e
- **日期**：2024-03-26 23:58:48 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Build: Bump Spark from 3.5 to 3.5.1 (#9832)
- **PR/Issue**：#9832

## 总体目的

本提交将 Iceberg 构建中 Spark 3.5 系列的依赖版本从 `3.5.0` 升级到 `3.5.1`。这是一个**补丁版本（patch version）升级**，目的是跟随 Spark 社区的维护版本更新，获取 3.5.1 中包含的 Bug 修复和稳定性改进。

**背景动机**：
- Spark 3.5.0 于 2023 年 9 月发布，3.5.1 作为其后的维护版本，包含了大量 Bug 修复（如 SQL 解析、查询执行、数据源交互等方面的修复）和性能优化。
- Iceberg 的 Spark 集成模块（`spark/v3.5/`）依赖 Spark 的 API，保持 Spark 依赖版本与社区最新维护版本同步，有助于：
  1. 让 Iceberg 用户在使用 Spark 3.5.1 时获得更好的兼容性（避免已知的 Spark Bug 影响 Iceberg 功能）。
  2. 在 CI 中使用更新的 Spark 版本进行测试，更早发现潜在的兼容性问题。
  3. 减少用户因 Iceberg 锁定旧版 Spark 而被迫降级的困扰。

## 如何达成设计目的

Iceberg 使用 Gradle 的 Version Catalog（`gradle/libs.versions.toml`）集中管理所有依赖版本。Spark 3.5 的版本通过 `spark-hive35` 键定义，在 `spark/v3.5/build.gradle` 中通过 `${libs.versions.spark.hive35.get()}` 引用。因此，只需修改 Version Catalog 中的一处版本声明，所有引用该键的构建配置都会自动使用新版本。

**影响范围分析**：
- `spark-hive35` 版本被 `spark/v3.5/build.gradle` 在 3 处引用（第 65、145、231 行），分别用于：
  - `compileOnly` 依赖：`org.apache.spark:spark-hive_${scalaVersion}`（主源码编译依赖，排除 avro、arrow、parquet、netty、roaringbitmap 等冲突模块）。
  - 另一处 `compileOnly` 依赖（可能用于扩展或不同源集）。
  - `integrationImplementation` 依赖：用于集成测试运行时。
- `settings.gradle` 中通过 `sparkVersions.contains("3.5")` 控制是否启用 Spark 3.5 子项目（`spark-3.5_${scalaVersion}`、`spark-extensions-3.5_${scalaVersion}`、`spark-runtime-3.5_${scalaVersion}`），这些子项目的构建都会使用升级后的版本。
- `jmh.gradle` 中也有 `sparkVersions.contains("3.5")` 的判断，JMH 基准测试同样受影响。

由于 3.5.0 → 3.5.1 是补丁版本升级，遵循语义化版本（SemVer）兼容性承诺，API 无破坏性变更，因此不需要修改任何 Iceberg 源码即可完成升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Spark 3.5 系列的依赖版本从 3.5.0 升级到 3.5.1。

**工作逻辑**：Version Catalog 是 Gradle 7.0+ 引入的依赖版本集中管理机制。本文件以 TOML 格式定义所有依赖的版本键值对。修改前：
```toml
spark-hive35 = "3.5.0"
```
修改后：
```toml
spark-hive35 = "3.5.1"
```

此键在构建脚本中通过 `libs.versions.spark.hive35.get()` 访问（Gradle 自动将 TOML 键 `spark-hive35` 映射为 `spark.hive35`）。所有引用该键的依赖声明（`spark/v3.5/build.gradle` 中的 `org.apache.spark:spark-hive_${scalaVersion}:${libs.versions.spark.hive35.get()}`）会自动解析为 `3.5.1`。

同文件中其他 Spark 版本不受影响：`spark-hive33 = "3.3.4"`、`spark-hive34 = "3.4.2"` 保持不变。

## 小结

**成效**：以单行改动完成 Spark 3.5 依赖从 3.5.0 到 3.5.1 的升级，使 Iceberg 的 Spark 3.5 集成模块与 Spark 社区最新维护版本保持同步。由于是补丁版本升级，API 完全兼容，无需修改任何产品代码或测试代码。

**影响范围**：
- 直接影响：`spark/v3.5/` 下的所有子项目（spark、spark-extensions、spark-runtime）的编译和测试依赖版本。
- 间接影响：使用 Iceberg Spark 3.5 runtime jar 的用户，其 Spark 环境应使用 3.5.1+（但 Iceberg 的 Spark 3.5 runtime 通常兼容整个 3.5.x 系列，因为 Spark 补丁版本保持二进制兼容）。
- CI 影响：CI 中 Spark 3.5 相关的构建和测试将使用 3.5.1 运行。

**回迁到 1.4.x 的注意事项**：
- 这是单行版本号修改，回迁极为简单，风险极低。
- 需确认 1.4.x 的 `gradle/libs.versions.toml` 中 `spark-hive35` 键存在且当前值为 `3.5.0`（1.4.x 可能已经是 3.5.0 或其他值）。
- 回迁后建议运行 Spark 3.5 模块的完整测试套件，确认 3.5.1 无引入兼容性问题（虽然补丁版本理论上兼容，但 Iceberg 对 Spark 内部 API 的使用偶尔可能受 Spark 修复影响）。
- 若 1.4.x 已有其他 Spark 版本升级（如 3.5.2+），则本提交可能已被后续提交覆盖，无需单独回迁。
