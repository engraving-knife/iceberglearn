# 提交 3147：Build: Bump comet version from 0.10.1 to 0.12.0 (#15105)

## 提交信息

- **序号**：3147 / 4088
- **哈希**：51ae3466525b5b30e3b8bd6728bae3e1e8f61e68
- **短哈希**：51ae34665
- **日期**：2026-01-23 09:13:29 -0800
- **作者**：Manu Zhang
- **提交说明**：Build: Bump comet version from 0.10.1 to 0.12.0 (#15105)
- **PR/Issue**：#15105

## 总体目的

本提交将项目所依赖的 Apache DataFusion Comet 版本从 `0.10.1` 升级到 `0.12.0`。Comet（`org.apache.datafusion:comet-spark-spark*`）是 Apache DataFusion 提供的 Spark 原生向量化执行加速器，Iceberg 在 Spark 各版本模块（`spark/v3.4`、`spark/v3.5`、`spark/v4.0`、`spark/v4.1`）中以 `compileOnly` 与 `testImplementation` 的方式引入它，用于在测试和编译期与 Comet 的向量化执行/列式读取接口对接，保证 Iceberg 的 Spark 集成能在 Comet 加速环境下正常工作。版本号集中维护在 Gradle 版本目录 `gradle/libs.versions.toml` 的 `comet` 属性中，各 Spark 模块的 `build.gradle` 通过 `${libs.versions.comet.get()}` 引用。

值得注意的背景是：在 Spark 4.1 模块中，由于 Comet 尚未正式发布对应 Spark 4.1 的构件，`spark/v4.1/build.gradle` 中存在 `// TODO: datafusion-comet Spark 4.1 support` 注释，并暂时以 `comet-spark-spark4.0_2.13` 构件作为过渡。升级到 0.12.0 可获取 Comet 上游两个小版本（0.11、0.12）积累的修复与改进，保持与上游 Comet 的同步，降低因依赖过旧而出现的兼容性风险。这是一次跨两个次版本（minor）的升级（0.10 → 0.12），预期影响是测试期 Comet 加速行为的更新，并不影响 Iceberg 自身发布的构件产物（因为 Comet 仅作为 `compileOnly`/`testImplementation`，不进入发布包）。

## 如何达成设计目的

整体思路非常简单：在集中化的版本目录中修改 `comet` 版本字符串，所有 Spark 模块的 `build.gradle` 通过属性引用自动跟随升级，无需逐个改动各模块构建脚本，体现了集中版本管理的优势。改动仅涉及 `gradle/libs.versions.toml` 一个文件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Comet 依赖版本从 0.10.1 提升到 0.12.0。

**工作逻辑**：
将 `comet = "0.10.1"` 修改为 `comet = "0.12.0"`。该属性被 `spark/v3.4`、`spark/v3.5`、`spark/v4.0`、`spark/v4.1` 各自 `build.gradle` 中的 `compileOnly` 与 `testImplementation` 配置项以 `org.apache.datafusion:comet-spark-spark${sparkMajorVersion}_${scalaVersion}:${libs.versions.comet.get()}` 形式引用。修改后，所有相关模块在编译期与测试期都会自动拉取 0.12.0 版本的 Comet 构件。本次为跨越两个次版本（0.10→0.11→0.12）的升级，属于 `version-update:semver-minor` 级别，按语义化版本约定可包含新功能与兼容性改进，但应保持向后兼容。

## 总结

本提交通过在版本目录中将 Apache DataFusion Comet 从 0.10.1 升级到 0.12.0，使 Iceberg 的 Spark 集成模块与上游 Comet 加速器保持同步，获取两个次版本积累的改进与修复；由于 Comet 仅作为编译期/测试期依赖引入，升级不会影响 Iceberg 发布产物，仅影响基于 Comet 的测试执行行为。
