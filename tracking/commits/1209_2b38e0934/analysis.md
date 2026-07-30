# 提交 1209：Build: Bump org.eclipse.microprofile.openapi:microprofile-openapi-api (#11182)

## 提交信息

- **序号**：1209 / 4088
- **哈希**：2b38e093464f9d5151e52347f7c5f9639b62aa46
- **短哈希**：2b38e0934
- **日期**：2024-10-03（Thu Oct 3 04:08:28 2024 -0700）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.eclipse.microprofile.openapi:microprofile-openapi-api from 3.1.1 to 3.1.2
- **PR/Issue**：#11182

## 总体目的

Dependabot 自动生成的依赖升级提交，把 MicroProfile OpenAPI API 从 `3.1.1` 升级到 `3.1.2`（semver-patch 补丁版本升级）。

`org.eclipse.microprofile.openapi:microprofile-openapi-api` 是 MicroProfile OpenAPI 规范的 API 接口包，Iceberg 在 REST Catalog（`rest` 模块）中使用它来生成/描述 OpenAPI 文档接口。升级动机是跟进上游 3.1.2 补丁版本的 bug 修复，保持依赖最新。

## 如何达成设计目的

Iceberg 的依赖版本统一集中在 `gradle/libs.versions.toml` 中维护（Gradle Version Catalog），`microprofile-openapi-api` 共享一个版本变量，只需把版本号从 `3.1.1` 改为 `3.1.2` 即可。属于 patch 版本升级，理论上不包含破坏性 API 变更。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：升级 microprofile-openapi-api 版本。

**工作逻辑**：

```diff
-microprofile-openapi-api = "3.1.1"
+microprofile-openapi-api = "3.1.2"
```

该版本变量被 `microprofile-openapi-api = { module = "org.eclipse.microprofile.openapi:microprofile-openapi-api", version.ref = "microprofile-openapi-api" }` 引用，所有使用该 library 的模块（主要是 `rest` 模块）会同步升级。

## 小结

- **成效**：把 MicroProfile OpenAPI API 从 3.1.1 升级到 3.1.2，跟进上游 patch 修复。仅修改 1 行 1 个文件，无源代码改动。
- **影响范围**：仅影响 `rest` 模块（REST Catalog）的 OpenAPI 文档生成相关依赖，不影响其他模块。
- **回迁到 1.4.x 的注意事项**：纯依赖版本升级，回迁零风险。1.4.x 分支的 `gradle/libs.versions.toml` 可直接 cherry-pick。需确认 1.4.x 上 `rest` 模块的 OpenAPI 用法与 3.1.2 兼容（patch 版本通常兼容）。
