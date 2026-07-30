# 提交 0071：Build: Bump org.apache.pig:pig from 0.14.0 to 0.17.0 (#8774)

## 提交信息

- **序号**：0071 / 4088
- **哈希**：45da568f1c659783f25a357fda043c6c18102e15
- **短哈希**：45da568f1
- **日期**：2023-10-19
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.pig:pig from 0.14.0 to 0.17.0 (#8774)
- **PR/Issue**：#8774

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖版本升级提交，将 Iceberg 项目中使用的 `org.apache.pig:pig` 依赖从 0.14.0 升级到 0.17.0。Apache Pig 在 Iceberg 中主要用于 Pig 集成模块（`pig/` 子模块），用于支持通过 Pig 加载和写入 Iceberg 表。

此次升级跨越了三个次要版本（0.14 → 0.15 → 0.16 → 0.17），属于 semver-minor 类型的版本更新。0.14.0 是 2014 年发布的较老版本，而 0.17.0 是 Pig 项目 2017 年发布的版本，期间包含了多个 bug 修复、依赖更新和兼容性改进。通过这一升级，Iceberg 的 Pig 集成模块能够依赖更稳定、修复了已知缺陷的 Pig 版本。

从依赖管理角度看，这种由 Dependabot 触发的常规依赖升级有助于保持项目依赖树的现代化，减少与底层 Hadoop 生态（如 Hadoop 2.x/3.x）的兼容性风险，并为后续可能的 Pig 集成功能调整打下基础。

## 如何达成设计目的

Dependabot 通过 Gradle 的版本目录（version catalog）机制集中管理依赖版本。Iceberg 在 `gradle/libs.versions.toml` 中维护所有第三方依赖的版本号别名，各子模块通过别名引用而非直接写死版本号，因此只需修改这一处即可统一升级所有引用 `pig` 别名的地方。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Pig 依赖的版本别名从 0.14.0 升级到 0.17.0。

**工作逻辑**：该文件位于第 70-73 行附近，是 Iceberg 的 Gradle 版本目录。修改将 `pig = "0.14.0"` 改为 `pig = "0.17.0"`。由于版本目录采用别名机制，所有通过 `libs.pig` 引用该依赖的子模块（主要是 `pig/` 子模块的 `build.gradle`）会自动使用新版本，无需逐个修改构建脚本。

## 小结

Dependabot 通过一行版本目录修改，将 Iceberg 的 Pig 依赖从老旧的 0.14.0 升级到 0.17.0，保持了 Pig 集成模块依赖的现代化与稳定性。
