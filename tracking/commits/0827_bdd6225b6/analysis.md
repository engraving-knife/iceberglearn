# 提交 0827：Build: Bump io.delta:delta-standalone_2.12 from 3.1.0 to 3.2.0 (#10321)

## 提交信息
- **序号**：0827 / 4088
- **哈希**：bdd6225b6296c7fb10e396494f6735b72faf4feb
- **短哈希**：bdd6225b6
- **日期**：2024-06-12
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.delta:delta-standalone_2.12 from 3.1.0 to 3.2.0 (#10321)
- **PR/Issue**：#10321

## 总体目的

这是一次由 Dependabot 自动发起的依赖版本升级提交，将 `io.delta:delta-standalone_2.12` 从 3.1.0 升级到 3.2.0。delta-standalone 是 Delta Lake 项目提供的独立 Java API（不依赖 Spark 运行时），Iceberg 在 `iceberg-delta-lake` 模块中通过它来读取 Delta Lake 表的元数据、类型信息以及执行快照（snapshot）转换等操作。

本次升级属于 semver-minor（次要版本）升级，目标是保持 Iceberg 对 Delta Lake 生态的兼容性，跟进上游修复与改进。

提交说明中包含 Dependabot 元数据：
- dependency-name: io.delta:delta-standalone_2.12
- dependency-type: direct:production
- update-type: version-update:semver-minor

## 如何达成设计目的

Iceberg 使用 Gradle 的 version catalog（`gradle/libs.versions.toml`）集中管理依赖版本，这种集中式版本目录的好处是 Dependabot 等工具可以自动识别版本声明并提交 PR。

本次提交仅修改 version catalog 中的一行版本号声明，所有引用 `delta-standalone` 的模块（如 `iceberg-delta-lake` 的 `build.gradle` 中通过 `${libs.versions.delta.standalone.get()}` 引用）会自动获取新版本，无需逐个模块修改，实现单点升级。

Delta 3.1.0 → 3.2.0 是 Delta Lake 项目的次版本更新，通常包含 bug 修复、新 API 和性能改进，但保持了 API 兼容性（minor 升级）。由于 Iceberg 仅使用 delta-standalone 的类型系统（`io.delta.standalone.types.*`）和 DeltaLog/OptimisticTransaction 等 API，这些 API 在 minor 升级中保持稳定，因此升级风险较低。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 delta-standalone 版本号从 3.1.0 升级到 3.2.0。

**工作逻辑**：在 `[versions]` 段中修改一行：

```toml
# 改动前
delta-standalone = "3.1.0"
# 改动后
delta-standalone = "3.2.0"
```

该值通过 `[libraries]` 段中的别名定义被引用：

```toml
delta-standalone = { module = "io.delta:delta-standalone_2.12", version.ref = "delta-standalone" }
```

随后在根 `build.gradle` 的 `iceberg-delta-lake` 子项目配置中通过 `${libs.versions.delta.standalone.get()}` 取出该版本号，作为 `compileOnly` 依赖注入到 delta-lake 模块的编译类路径中。改完 version catalog 一行后，所有依赖会自动指向 3.2.0。

## 小结
- **成效**：将 delta-standalone 依赖从 3.1.0 升级到 3.2.0，跟进 Delta Lake 上游的次要版本更新，保持依赖最新，获取上游的 bug 修复与改进。
- **影响范围**：仅影响 `iceberg-delta-lake` 模块的编译/测试类路径（通过 version catalog 间接影响），不影响 Iceberg 核心或其他模块。属于纯依赖升级，源代码无任何改动。
- **回迁注意事项**：1.4.x 分支当前的 `gradle/libs.versions.toml` 中 `delta-standalone` 版本与 main 分支历史版本差异较大（1.4.x 分支早期可能仍为 0.6.0 这条独立维护线，且 Scala 版本 `delta-standalone_2.12` 与 1.4.x 实际使用的 Scala 跨版本变量需匹配）。回迁前需先确认 1.4.x 分支的 delta-standalone 当前版本以及它与 delta-core、Spark 版本之间的兼容矩阵。如果 1.4.x 还停留在 0.x 系列，直接跳到 3.2.0 可能涉及 API 不兼容（如 `io.delta.standalone` 包结构在 0.x 与 3.x 之间有过变化），不应直接套用本次提交。若 1.4.x 已经在 3.x 系列，则可直接套用本次 3.1.0 → 3.2.0 的升级。
