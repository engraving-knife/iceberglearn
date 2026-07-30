# 提交分析：Spark: Bump minor version for Spark-3.4 (#10243)

## 提交信息

| 项目 | 内容 |
| --- | --- |
| 哈希 | `9310bd4828736e26caecd1f6e51274fd840440a9` |
| 短哈希 | `9310bd482` |
| 作者 | Ajantha Bhat |
| 提交时间 | 2024-04-29 12:15:27 +0530 |
| 提交标题 | Spark: Bump minor version for Spark-3.4 (#10243) |
| 提交正文 | release notes: https://spark.apache.org/releases/spark-release-3-4-3.html |
| 变更范围 | 1 个文件，1 行新增，1 行删除 |

## 总体目的

将 Iceberg 所依赖的 Spark 3.4 系列版本从 `3.4.2` 升级到 `3.4.3`，使 Spark 3.4 模块（`spark-hive34`）跟随上游 Spark 的最新维护版本，获取 bug 修复与稳定性改进。提交标题中的 “minor version” 用词并不严谨——从语义化版本看，`3.4.2 → 3.4.3` 实际上是一个 patch（补丁）版本升级，对应 Spark 3.4.3 的维护发行版。

## 如何达成设计目的

通过修改 Iceberg 项目的 Gradle 版本目录（version catalog）`gradle/libs.versions.toml`，将 `spark-hive34` 这一依赖坐标的版本号字面量从 `3.4.2` 改为 `3.4.3`。由于该版本在 toml 中以常量形式集中声明，并被各 Spark 3.4 子模块通过引用方式使用，因此单点修改即可让整个 `spark-hive34` 工具链切换到新版本，无需改动任何业务代码或构建脚本逻辑。

## 修改详情

### `gradle/libs.versions.toml`

版本目录中 Spark 3.4 相关条目的版本号更新：

```toml
-spark-hive34 = "3.4.2"
+spark-hive34 = "3.4.3"
```

- 仅此一行变更，把 `spark-hive34` 的版本从 `3.4.2` 提升到 `3.4.3`。
- 同文件中其它 Spark 版本（如 `spark-hive33 = "3.3.4"`、`spark-hive35 = "3.5.1"`）保持不变，说明本次只针对 Spark 3.4 这一支。
- Spark 3.4.3 属于 3.4.x 维护线上的补丁发行版（参见提交正文给出的发行说明链接），按 Spark 项目的兼容性承诺，同一 minor 内的 patch 升级向后兼容。

## 小结

### 成效
- 让 Iceberg 的 Spark 3.4 集成模块对齐上游最新补丁版本，纳入 3.4.3 中累积的 bug 修复。
- 改动面极小（单行），风险低，回归面主要落在 Spark 3.4 相关的集成测试上。

### 影响范围
- 仅影响构建期拉取的 `spark-hive34` 依赖版本，以及依赖该版本的编译/测试产物；不涉及 Iceberg 自身 API 或运行时行为。
- 对使用 `spark-runtime` 3.4 分发的下游用户而言，打包进来的 Spark 版本随之上调。

### 回迁注意事项（1.4.x ← main）
- 该改动为纯依赖版本号提升，回迁到 1.4.x 时只需保证 `gradle/libs.versions.toml` 中 `spark-hive34` 同步为 `3.4.3` 即可，无代码冲突风险。
- 需确认 1.4.x 分支上 Spark 3.4 模块仍受支持；若 1.4.x 已调整 Spark 支持矩阵，需结合该分支当前版本号决定是否直接套用。
- 回迁后建议跑一遍 Spark 3.4 模块的集成测试，验证 3.4.3 不引入意外行为差异。
