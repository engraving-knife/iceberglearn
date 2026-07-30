# 提交 3238：Build: Remove unused jackson versions in libs.versions.toml (#15295)

## 提交信息

- **序号**：3238 / 4088
- **哈希**：e5532d6bdd797aaedf8590e528e1f883e0c74598
- **短哈希**：e5532d6bd
- **日期**：2026-02-11
- **作者**：Sunwoo Jung
- **提交说明**：Build: Remove unused jackson versions in libs.versions.toml (#15295)
- **PR/Issue**：#15295

## 总体目的

本提交是一个构建配置清理。Iceberg 项目使用 Gradle 的版本目录（version catalog）`gradle/libs.versions.toml` 集中管理依赖版本。Jackson 是 Java 生态中最常用的 JSON 处理库，Iceberg 因为需要与不同版本的 Spark 和 Hive 集成（这些框架各自捆绑了不同版本的 Jackson），因此在版本目录中定义了多个 Jackson 版本别名，使用 `strictly` 约束来强制对齐特定框架所要求的 Jackson 版本，避免依赖冲突。

随着时间的推移，部分旧 Spark/Hive 版本被弃用或移除，对应的 Jackson 版本别名（`jackson211` = 2.11.4、`jackson212` = 2.12.3、`jackson213` = 2.13.4）变成了死代码——它们在版本目录中定义了版本号和 BOM 库坐标，但已没有任何 `build.gradle` 文件引用它们。当前仍被使用的是 `jackson214`（2.14.2）和 `jackson215`（2.15.2），它们在 `spark/v3.4`、`spark/v3.5`、`spark/v4.0`、`spark/v4.1` 的构建文件中被引用。保留这些未使用的版本别名不仅造成配置冗余，还会误导维护者以为这些版本仍在使用，增加维护负担。

## 如何达成设计目的

整体思路是直接从版本目录中删除已无引用的版本定义和库坐标定义。通过 `git grep` 确认 `jackson211`、`jackson212`、`jackson213` 在整个仓库的 Gradle 构建文件中已无任何引用，而 `jackson214` 和 `jackson215` 仍被 Spark 各版本模块使用，因此仅移除前三个。同时将 `jackson215` 行原有的注释说明迁移到了 `jackson215`（因为删除 `jackson211` 后，`jackson215` 成了最后一个带 `strictly` 约束且需要解释的版本行）。

## 修改详情

### `gradle/libs.versions.toml` (+1/-7 lines)

**修改目的**：移除未使用的 Jackson 版本别名及对应 BOM 库坐标定义。

**工作逻辑**：

- **版本定义区（versions 部分）**：删除了三行 `jackson211 = { strictly = "2.11.4"}`、`jackson212 = { strictly = "2.12.3"}`、`jackson213 = { strictly = "2.13.4"}`。这些是使用 Gradle 富版本（rich version）语法的版本约束，`strictly` 表示强制使用该精确版本、不接受其他版本传递。保留 `jackson214 = { strictly = "2.14.2"}` 和 `jackson215 = { strictly = "2.15.2"}`，并将原 `jackson211` 行后的注释 `# see rich version usage explanation above` 迁移到 `jackson215` 行末，保留了关于 `strictly` 用法的说明。

- **库坐标定义区（libraries 部分）**：删除了三行 BOM 依赖坐标 `jackson211-bom`、`jackson212-bom`、`jackson213-bom`，它们分别指向 `com.fasterxml.jackson:jackson-bom` 并引用上述已删除的版本别名。保留了 `jackson214-bom` 和 `jackson215-bom`，这两个仍被 Spark 模块构建文件引用。

## 总结

本提交是纯构建配置清理，移除了 Gradle 版本目录中三个已无引用的 Jackson 版本别名（2.11.4、2.12.3、2.13.4）及其 BOM 坐标定义。这些版本曾用于与旧版 Spark/Hive 的 Jackson 依赖对齐，但随着对应 Spark 版本的支持被移除而变为死代码。清理后版本目录更简洁，减少了维护者的认知负担，避免误以为这些旧版本仍在使用。保留的 `jackson214` 和 `jackson215` 仍被 Spark v3.4/v3.5/v4.0/v4.1 模块正常引用，不影响任何实际构建。
