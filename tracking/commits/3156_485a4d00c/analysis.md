# 提交 3156：Build: Bump com.fasterxml.jackson.core:jackson-annotations (#15136)

## 提交信息

- **序号**：3156 / 4088
- **哈希**：485a4d00cd492b38027a1d9dc6d6e8e540b383aa
- **短哈希**：485a4d00c
- **日期**：2026-01-25 12:02:03 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.fasterxml.jackson.core:jackson-annotations from 2.20 to 2.21
- **PR/Issue**：#15136

## 总体目的

本提交是 Dependabot 自动发起的依赖升级，将 Jackson Annotations（`com.fasterxml.jackson.core:jackson-annotations`）从 `2.20` 升级到 `2.21`。Jackson 是 Iceberg 全栈核心的 JSON 序列化/反序列化库，广泛用于 REST Catalog 协议、元数据 JSON、配置解析等场景；`jackson-annotations` 提供了 `@JsonProperty`、`@JsonInclude`、`@JsonIgnore` 等注解，是 Jackson 数据绑定栈的基础构件之一。

本次升级为次版本（minor）升级（`version-update:semver-minor`，2.20 → 2.21），按语义化版本约定可包含新功能与向后兼容的改进。值得注意的是仓库同时维护了多个 Jackson 版本线：`jackson-bom` 已是 `2.21.0`，而 `jackson-annotations` 此前停留在 `2.20`，二者版本不一致。本次升级使 `jackson-annotations` 与 `jackson-bom` 的次版本号对齐到 2.21，避免注解包与 BOM 管理的核心库（databind、core 等）出现次版本错配。Dependabot 在提交信息中附带了上游 release notes、changelog 与 commits 对比链接。

## 如何达成设计目的

直接在 `gradle/libs.versions.toml` 中将 `jackson-annotations` 属性从 `2.20` 改为 `2.21`，所有通过 `version.ref = "jackson-annotations"` 引用该属性的模块自动跟随升级，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 jackson-annotations 版本从 2.20 提升到 2.21，与 jackson-bom 的 2.21 次版本对齐。

**工作逻辑**：将 `jackson-annotations = "2.20"` 修改为 `jackson-annotations = "2.21"`。该属性通过 `jackson-annotations = { module = "com.fasterxml.jackson.core:jackson-annotations", version.ref = "jackson-annotations" }` 在依赖目录中声明，供需要 Jackson 注解的模块引用。升级到 2.21 为 semver-minor 级别，预期与 Jackson 2.21.x 核心/数据绑定库保持 API 兼容，并使注解包版本与项目 `jackson-bom = "2.21.0"` 在次版本上保持一致。

## 总结

本提交通过将 Jackson Annotations 从 2.20 升级到 2.21，使该注解构件与项目所用的 jackson-bom 2.21.0 在次版本上保持对齐，避免注解包与 BOM 管理的核心库出现次版本错配；作为 semver-minor 升级，预期对 Iceberg 的 JSON 序列化栈保持向后兼容。
