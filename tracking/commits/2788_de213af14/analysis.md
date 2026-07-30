# 提交 2788：Docs: Add note about packaging uberjars (#14292)

## 提交信息

- **序号**：2788 / 4088
- **哈希**：de213af146d174ccad30183d2956aa4001756d80
- **短哈希**：de213af14
- **日期**：2025-10-23 17:12:29 +0200
- **作者**：wineandcheeze
- **提交说明**：Docs: Add note about packaging uberjars (#14292)
- **PR/Issue**：#14292

## 总体目的

本提交在 Iceberg 文档中添加关于打包 uberjar 时正确使用 runtime jar 的重要说明。

Iceberg 为 Spark 和 Flink 等引擎提供了 runtime jar（如 `iceberg-spark-runtime-3.5_2.12`、`iceberg-flink-runtime`），这些 jar 是 shaded 版本，包含了所有必要的依赖且避免了冲突。然而用户在打包应用 uberjar 时，常常错误地将 `iceberg-core`、`iceberg-parquet` 等非 runtime 模块也包含进去，这会导致依赖版本冲突——因为这些模块中包含的依赖（如 Guava、Parquet 等）可能与引擎运行时自带的版本冲突。

本提交在两个位置添加说明：
1. README.md 中为 Spark 和 Flink 模块描述添加指向 runtime jar 文档的链接。
2. multi-engine-support.md 中添加重要提示，说明只应包含 runtime jar（和存储特定的 bundle 如 `iceberg-aws-bundle`），排除其他模块。

## 如何达成设计目的

1. **README.md**：为 `iceberg-spark` 和 `iceberg-flink` 的描述中"runtime jar"文本添加超链接，指向 `https://iceberg.apache.org/multi-engine-support/#runtime-jar`，方便用户直接查看详细说明。

2. **multi-engine-support.md**：在 runtime jar 说明段落后新增一个 info 提示块（使用 `> ℹ️` 语法），明确说明：(1) 运行时 classpath 中只应包含 runtime jar 和存储 bundle；(2) 其他模块应排除以避免依赖冲突；(3) 以 Spark uberjar 为例，应只包含 `iceberg-spark-runtime` 而排除 `iceberg-core`、`iceberg-parquet` 等。

## 修改详情

### `README.md` (+2/-2 lines)

**修改目的**：为 Spark 和 Flink 模块描述添加 runtime jar 文档链接。

**工作逻辑**：将 `iceberg-spark` 描述中的 "use runtime jars for a shaded version" 改为 "use [runtime jars](https://iceberg.apache.org/multi-engine-support/#runtime-jar) for a shaded version to avoid dependency conflicts"；将 `iceberg-flink` 描述中的 "use iceberg-flink-runtime for a shaded version" 改为 "use [iceberg-flink-runtime](https://iceberg.apache.org/multi-engine-support/#runtime-jar) for a shaded version"。

### `site/docs/multi-engine-support.md` (+4/-0 lines)

**修改目的**：添加 uberjar 打包注意事项。

**工作逻辑**：在 runtime jar 说明段落后新增 info 提示块，强调：(1) 只包含 runtime jar 和存储 bundle；(2) 排除其他模块避免依赖冲突；(3) Spark uberjar 示例——只包含 `iceberg-spark-runtime`，排除 `iceberg-core`、`iceberg-parquet` 等。

## 总结

本提交是一个文档改进，通过在 README 和多引擎支持文档中添加明确的 uberjar 打包指导，帮助用户避免常见的依赖冲突问题。核心信息是：打包 uberjar 时只应包含 runtime jar（和存储 bundle），排除 `iceberg-core`、`iceberg-parquet` 等模块，以防依赖版本与引擎运行时冲突。
