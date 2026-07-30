# 提交 1037：Build: Bump Apache Avro to 1.12.0 (#10879)

## 提交信息

- **序号**：1037 / 4088
- **哈希**：86611d94dbc6b28f2f7b89addc5886d2d4ea96d8
- **短哈希**：86611d94d
- **日期**：2024-08-07 11:11:27 +0200
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Build: Bump Apache Avro to 1.12.0 (#10879)
- **PR/Issue**：#10879

## 总体目的

Apache Avro 是 Iceberg 的核心依赖之一，用于读写 Iceberg 的元数据文件（manifest、manifest list、snapshot metadata 等）以及 Avro 数据文件。Iceberg 此前依赖 Avro 1.11.3。Avro 1.12.0 是 Avro 项目于 2024 年发布的新的小版本（minor release），通常包含 bug 修复、性能改进以及少量向后兼容的新特性。

本提交的目的是将 Iceberg 依赖的 Avro 版本从 1.11.3 升级到 1.12.0，以获取上游的修复与改进，并保持与生态中其他依赖 Avro 的组件（如 Spark、Flink、Hive）的版本兼容性。这属于常规的依赖版本升级（dependabot 风格的维护工作），只改动版本目录中声明的版本号，不涉及任何代码逻辑改动。

## 如何达成设计目的

在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中将 `avro` 版本属性从 `"1.11.3"` 改为 `"1.12.0"`。所有通过 `version.ref = "avro"` 引用该版本的依赖（如 `avro-avro`、`avro-mapred` 等）会自动继承新版本，无需逐个修改依赖坐标。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Avro 依赖版本从 1.11.3 升级到 1.12.0。

**工作逻辑**：仅修改 `[versions]` 段中的一行：

```diff
-avro = "1.11.3"
+avro = "1.12.0"
```

该 `avro` 版本属性被 `[libraries]` 段中所有 Avro 相关依赖通过 `version.ref = "avro"` 引用，因此这一处改动会传播到所有 Avro 依赖坐标。无其他文件改动，无代码逻辑变更。

## 小结

- **成效**：将 Iceberg 的 Avro 依赖从 1.11.3 升级到 1.12.0，获取上游修复与改进。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、一行改动。影响所有依赖 Avro 的子模块（core、parquet、avro、flink、spark、hive 等）的构建产物。
- **回迁到 1.4.x 的注意事项**：可以回迁，但需谨慎验证。Avro 1.12.0 是 minor 版本升级，理论上向后兼容，但仍需确认：1) 1.4.x 分支的 Avro 相关代码不依赖 1.11.x 中已被移除/变更的 API；2) 1.4.x 支持的 Spark/Flink/Hive 版本与 Avro 1.12.0 兼容。建议回迁后跑一遍完整的 Avro 相关测试（元数据读写、Avro 数据文件读写）确认无回归。若 1.4.x 已有 1.11.x 的 patch 修复未合入 1.12.0，需评估是否等待 1.12.x 后续 patch。
