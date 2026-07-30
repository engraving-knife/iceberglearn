# 提交 2816：Build: Bump calcite from 1.40.0 to 1.41.0 (#14470)

## 提交信息

- **序号**：2816 / 4088
- **哈希**：9bbe4cfdfcf47554713e7d3b42232e37384b0926
- **短哈希**：9bbe4cfdf
- **日期**：2025-11-01 23:10:09 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump calcite from 1.40.0 to 1.41.0 (#14470)
- **PR/Issue**：#14470

## 总体目的

这是 Dependabot 自动生成的依赖版本升级提交。Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、查询优化和查询执行能力。Iceberg 项目使用 Calcite 的 `calcite-core` 和 `calcite-druid` 制品，主要用于 SQL 相关的功能（如 Iceberg 的 SQL 扩展、查询计划等）。

本次从 1.40.0 升至 1.41.0，属于次版本（minor）升级，可能引入新的 SQL 函数、优化器改进或 API 变更。Calcite 作为 SQL 引擎核心组件，版本升级需关注潜在的 API 兼容性问题，但 Dependabot 通常会配合 CI 验证。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中的 `calcite` 版本属性，从 `1.40.0` 更新为 `1.41.0`。该属性统一管理 `calcite-core` 和 `calcite-druid` 两个制品的版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Apache Calcite 版本。

**工作逻辑**：将 `calcite = "1.40.0"` 改为 `calcite = "1.41.0"`。此属性被版本目录中 `calcite-core` 和 `calcite-druid` 依赖引用，一次修改即可同步升级两个制品。

## 总结

将 Apache Calcite（calcite-core 和 calcite-druid）从 1.40.0 升级到 1.41.0，属于次版本升级。Calcite 是 SQL 解析与优化的核心依赖，升级可能带来新的 SQL 功能和优化器改进。这是 Dependabot 批量依赖升级（2811-2819）的一部分。
