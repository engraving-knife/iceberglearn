# 提交 3309：Build: Exclude roaringbitmap dependency from Spark and update LICENSE files (#15405)

## 提交信息

- **序号**：3309 / 4088
- **哈希**：501824f0c0032b3225b0fe52b904756f0fe5c589
- **短哈希**：501824f0c
- **日期**：2026-02-24
- **作者**：Manu Zhang
- **提交说明**：Build: Exclude roaringbitmap dependency from Spark and update LICENSE files (#15405)
- **PR/Issue**：#15405

## 总体目的

Iceberg 自身使用 RoaringBitmap（`org.roaringbitmap`）库来实现高效的位图索引（如删除文件 deletion vector 相关功能），版本为 1.6.10。而 Apache Spark 的 `spark-hive` 依赖也传递引入了 RoaringBitmap，但其版本可能与 Iceberg 使用的不一致（此前 LICENSE 文件记录为 1.3.0）。当两个版本的 RoaringBitmap 同时出现在 classpath 上时，可能因 API 不兼容或类加载顺序不确定导致运行时错误。

本提交有两个目的：一是在 Spark 各版本（v3.4、v3.5、v4.0、v4.1）的 `build.gradle` 中排除 `spark-hive` 传递引入的 `org.roaringbitmap` 依赖，确保 Spark 集成测试运行时 classpath 只保留 Iceberg 自身管理的 RoaringBitmap 版本；二是同步更新 kafka-connect 和 open-api 模块的 LICENSE 文件中 RoaringBitmap 的版本号从 1.3.0 到 1.6.10，使其与实际使用的版本一致，满足 Apache 许可证合规要求。

## 如何达成设计目的

在四个 Spark 版本的 `build.gradle` 中，将 `spark-hive` 的 `integrationImplementation` 依赖声明从字符串形式改为闭包形式，添加 `exclude group: 'org.roaringbitmap'` 排除规则。同时将三个 LICENSE 文件中的 RoaringBitmap 版本号从 `1.3.0` 更新为 `1.6.10`。

## 修改详情

### `spark/v3.4/build.gradle` (+3/-1 lines)

**修改目的**：从 Spark 3.4 的 spark-hive 依赖中排除 RoaringBitmap。

**工作逻辑**：
将 `integrationImplementation "org.apache.spark:spark-hive_${scalaVersion}:${libs.versions.spark34.get()}"` 改为闭包形式：
```gradle
integrationImplementation("org.apache.spark:spark-hive_${scalaVersion}:${libs.versions.spark34.get()}") {
  exclude group: 'org.roaringbitmap'
}
```
这样 Gradle 在解析 spark-hive 的传递依赖时不会引入 `org.roaringbitmap` 组的所有 artifact，Iceberg 自身声明的 RoaringBitmap 1.6.10 成为 classpath 上的唯一版本。

### `spark/v3.5/build.gradle` (+3/-1 lines)

**修改目的**：从 Spark 3.5 的 spark-hive 依赖中排除 RoaringBitmap。

**工作逻辑**：与 v3.4 相同的排除规则，针对 `spark35` 版本。

### `spark/v4.0/build.gradle` (+3/-1 lines)

**修改目的**：从 Spark 4.0 的 spark-hive 依赖中排除 RoaringBitmap。

**工作逻辑**：与 v3.4 相同的排除规则，针对 `spark40` 版本。

### `spark/v4.1/build.gradle` (+3/-1 lines)

**修改目的**：从 Spark 4.1 的 spark-hive 依赖中排除 RoaringBitmap。

**工作逻辑**：与 v3.4 相同的排除规则，针对 `spark41` 版本。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` (+1/-1 lines)

**修改目的**：更新 RoaringBitmap 版本号以保持许可证合规。

**工作逻辑**：将 `Group: org.roaringbitmap  Name: RoaringBitmap  Version: 1.3.0` 更新为 `Version: 1.6.10`，反映实际打包的依赖版本。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` (+1/-1 lines)

**修改目的**：同上，更新 main 模块 LICENSE 中的版本号。

**工作逻辑**：同上，1.3.0 改为 1.6.10。

### `open-api/LICENSE` (+1/-1 lines)

**修改目的**：同上，更新 open-api 模块 LICENSE 中的版本号。

**工作逻辑**：同上，1.3.0 改为 1.6.10。

## 总结

本提交通过排除 Spark 的 spark-hive 传递引入的 RoaringBitmap 依赖，避免了 Spark 集成测试中因多版本 RoaringBitmap 共存导致的潜在类冲突，同时将三个 LICENSE 文件中的版本号从 1.3.0 更新到 1.6.10 以保持许可证合规。这是一个构建健壮性和合规性维护提交。
