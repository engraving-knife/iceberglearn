# 提交 2727：Build: Upgrade datafusion-comet to 0.10.1

## 提交信息

- **序号**：2727 / 4088
- **哈希**：9ee4d023f85d70e0a0c14431f2561af479508640
- **短哈希**：9ee4d023f
- **日期**：2025-10-09 07:16:23 -0700
- **作者**：Manu Zhang
- **提交说明**：Build: Upgrade datafusion-comet to 0.10.1
- **PR/Issue**：#14273

## 总体目的

DataFusion Comet 是 Apache DataFusion 的 Spark 原生加速器，它使用 Rust 实现的向量化执行引擎来加速 Spark 查询。在 Iceberg 项目中，Comet 作为 Spark 4.0 模块的编译和测试依赖，用于验证 Iceberg 与 Comet 的兼容性。

此前 Iceberg 使用 Comet 0.8.1 版本，此次升级到 0.10.1。值得注意的是，此次升级不仅更新了版本号，还修改了依赖工件名称的生成方式：从硬编码的 `comet-spark-spark3.5_2.13` 改为使用 `sparkMajorVersion` 变量动态生成 `comet-spark-spark${sparkMajorVersion}_2.13`。

这个修改很重要，因为之前的硬编码 `spark3.5` 对于 Spark 4.0 模块来说是错误的——Comet 0.10.1 开始发布针对 Spark 4.0 的专用工件 `comet-spark-spark4.0_2.13`，因此需要使用变量来动态匹配正确的 Spark 版本。

## 如何达成设计目的

1. 在版本目录中将 `comet` 版本从 `0.8.1` 更新为 `0.10.1`
2. 在 Spark 4.0 的 `build.gradle` 中，将两处硬编码的 `spark3.5` 改为使用 `sparkMajorVersion` 变量，使依赖工件名称能正确匹配 Spark 4.0

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 DataFusion Comet 版本号。

**工作逻辑**：将 `comet` 变量从 `"0.8.1"` 更改为 `"0.10.1"`。这是一个跨越两个 minor 版本的升级（0.8.1 -> 0.9.x -> 0.10.1），可能包含较多新功能和改进。

### `spark/v4.0/build.gradle` (+2/-2 lines)

**修改目的**：修复 Comet 依赖工件名称，使用动态 Spark 版本变量替代硬编码。

**修改逻辑**：
1. **compileOnly 依赖**（iceberg-spark 模块）：将 `"org.apache.datafusion:comet-spark-spark3.5_2.13:..."` 改为 `"org.apache.datafusion:comet-spark-spark${sparkMajorVersion}_2.13:..."`。这样在 Spark 4.0 模块中会解析为 `comet-spark-spark4.0_2.13`，正确匹配 Spark 4.0 版本。
2. **testImplementation 依赖**（iceberg-spark-extensions 模块）：同样将硬编码的 `spark3.5` 改为 `${sparkMajorVersion}` 变量。

之前的硬编码 `spark3.5` 在 Spark 4.0 模块中是不正确的，因为 Comet 从 0.10.1 开始发布了针对不同 Spark 版本的专用工件。使用变量后，依赖名称会自动匹配当前模块的 Spark 版本。

## 总结

此提交将 DataFusion Comet 从 0.8.1 升级到 0.10.1，并修复了依赖工件名称中硬编码 Spark 版本的问题。使用 `sparkMajorVersion` 变量动态生成工件名称，确保 Comet 依赖正确匹配 Spark 4.0 模块。这确保了 Iceberg 与最新版本 Comet 的兼容性验证。
