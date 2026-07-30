# 提交 0480：Build: Bump io.delta:delta-spark_2.12 from 3.0.0 to 3.1.0 (#9631)

## 提交信息

- **序号**：0480
- **哈希**：3348d88d96e41df58259304d6362f19a50f65f53
- **短哈希**：3348d88d9
- **日期**：2024-02-06 17:27:12 -0800
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.delta:delta-spark_2.12 from 3.0.0 to 3.1.0
- **PR/Issue**：#9631

## 总体目的

本提交由 Dependabot 自动生成，将 `io.delta:delta-spark_2.12` 从 `3.0.0` 升级到 `3.1.0`，属于一次语义化版本中的 minor 版本更新（`version-update:semver-minor`，依据 Dependabot 元数据推断）。`delta-spark` 是 Delta Lake 项目提供的、面向 Spark 的连接器库，它允许 Spark 读写 Delta Lake 格式的表，并提供 Delta 表的元数据访问 API。与 `delta-standalone`（不依赖 Spark 的独立读取库）不同，`delta-spark` 依赖 Spark 运行时，主要用于集成测试场景下的 Delta 表读写验证。

在 Iceberg 中，该依赖服务于 `:iceberg-delta-lake` 模块（`build.gradle` 第 557 行起的项目定义）。该模块实现了 Iceberg 与 Delta Lake 之间的互操作能力，支持将 Delta 表的数据文件迁移或转换为 Iceberg 表。具体而言，`delta-spark` 在 `build.gradle` 第 591 行以 `integrationImplementation` 作用域引入：`integrationImplementation "io.delta:delta-spark_${scalaVersion}:${libs.versions.delta.spark.get()}"`，即它仅在集成测试（integration test）编译与运行时被加入 classpath。集成测试借助 Spark 3.5 的能力来读取 Delta Lake 表的数据文件，从而验证 Iceberg 对 Delta 表的读取与转换逻辑的正确性。同一模块还以 `compileOnly` 引入 `delta-standalone`（第 579 行），后者提供不依赖 Spark 的 Delta 元数据访问，是生产代码的编译期依赖。

将版本从 3.0.0 提升到 3.1.0，目的在于跟进 Delta Lake 3.1.x 系列的改进。Delta 3.1.0 相对于 3.0.0 包含了若干新特性与缺陷修复，可能涉及 Delta 表元数据解析、CDC（Change Data Feed）、`delta-spark` 与 Spark 3.5 的兼容性优化等方面。由于 Iceberg 的 `:iceberg-delta-lake` 集成测试依赖 delta-spark 来构造与读取 Delta 测试表，及时升级可确保集成测试在较新的 Delta 实现下验证互操作正确性，并避免在测试中命中旧版本的已知缺陷。`build.gradle` 第 588 行的注释也明确说明"delta-core 的最新版本使用 Spark 3.5.*"，本次升级与 Iceberg 测试栈的 Spark 版本保持协同。

## 如何达成设计目的

Dependabot 扫描 `gradle/libs.versions.toml` 中的版本声明，识别出 `delta-spark = "3.0.0"` 这一可升级项，并自动生成单行版本号替换，将引用值改为 `3.1.0`。该版本号通过 `version.ref` 机制集中声明，`build.gradle` 第 591 行通过 `libs.versions.delta.spark.get()` 引用该常量，因此单点修改即可让 `:iceberg-delta-lake` 模块的集成测试依赖同步升级，无需改动 build 脚本本身。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 delta-spark 版本引用从 `3.0.0` 提升到 `3.1.0`。

**工作逻辑**：

文件第 34 行附近（在 `caffeine`、`calcite`、`delta-standalone` 等版本声明相邻处）将：

```
delta-spark = "3.0.0"
```

改为：

```
delta-spark = "3.1.0"
```

该声明对应的库坐标定义在 `libs.versions.toml` 第 178 行 `delta-spark = { module = "io.delta:delta-spark_2.12", version.ref = "delta-spark" }`，`version.ref` 指向被修改的常量。`build.gradle` 第 591 行在 `:iceberg-delta-lake` 模块的 `integrationImplementation` 作用域下通过 `"io.delta:delta-spark_${scalaVersion}:${libs.versions.delta.spark.get()}"` 引用该版本——其中 `scalaVersion` 取决于构建时选择的 Spark/Scala 组合（如 2.12），运行时解析为具体坐标如 `io.delta:delta-spark_2.12:3.1.0`。由于该依赖仅在集成测试 classpath 中生效，本次升级不影响 Iceberg 的生产运行时，仅影响 Delta 互操作能力的集成测试验证。需要注意的是，同模块的 `delta-standalone`（`0.6.0`）与 `delta-core`（`2.2.0`）版本未随之变动，它们各自维持独立版本，分别服务于编译期与不同测试作用域。

## 小结

本提交是一次由 Dependabot 驱动的集成测试依赖 minor 升级，仅修改 `gradle/libs.versions.toml` 中 `delta-spark` 的版本号（3.0.0 → 3.1.0），文件改动量为 1 行增、1 行删。delta-spark 在 Iceberg 中作为 `:iceberg-delta-lake` 模块集成测试的 Delta Lake 读写后端（`integrationImplementation` 作用域），用于验证 Iceberg 对 Delta 表的互操作能力。本次 minor 升级旨在跟进 Delta 3.1.x 的改进与缺陷修复，与测试栈中的 Spark 3.5 保持协同。由于版本号采用集中式 `version.ref` 声明，单点修改即可让集成测试依赖生效，且不影响生产运行时。
