# 提交 0780：Build: Bump org.springframework:spring-web from 5.3.34 to 5.3.35 (#10354)

## 提交信息

- **序号**：0780 / 4088
- **哈希**：f1a548f9724b25e2630d3a28d4051fbef9155f3a
- **短哈希**：f1a548f97
- **日期**：2024-05-23 09:18:11 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.springframework:spring-web from 5.3.34 to 5.3.35 (#10354)
- **PR/Issue**：#10354

## 总体目的

本提交由 Dependabot 自动生成，将 Java 依赖 `org.springframework:spring-web` 从 `5.3.34` 升级到 `5.3.35`，属于补丁版本（semver-patch）升级。`spring-web` 是 Spring Framework 的 Web 基础模块，Iceberg 在构建/测试中以 `direct:production` 依赖形式引入（依提交体标注）。升级目的是跟进 Spring 5.3.x 维护分支的缺陷修复与安全补丁，保持依赖为最新补丁版本。Dependabot 标注 `update-type: version-update:semver-patch`，表明仅补丁号递增，遵循 Spring 5.3.x 的语义版本兼容承诺，无破坏性 API 变更。

## 如何达成设计目的

整体思路是修改 Gradle 版本目录（version catalog）`gradle/libs.versions.toml` 中 `spring-web` 的版本引用值，从 `5.3.34` 改为 `5.3.35`。版本目录以单一 `version.ref` 集中管理，所有引用 `spring-web` 的模块会自动继承新版本，无需逐模块改动。不涉及任何代码逻辑改动，纯粹是依赖版本号递增。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `spring-web` 版本引用从 5.3.34 提升到 5.3.35。

**工作逻辑**：

```diff
-spring-web = "5.3.34"
+spring-web = "5.3.35"
```

该文件是 Gradle 版本目录，在 `[versions]` 段集中声明各依赖版本（如 `spring-boot = "2.7.18"`、`sqlite-jdbc = "3.45.3.0"`、`testcontainers = "1.19.8"` 等），并在 `[libraries]` 段通过 `version.ref` 引用（`spring-web = { module = "org.springframework:spring-web", version.ref = "spring-web" }`）。本次仅改动 `[versions]` 段的 `spring-web` 一行，所有引用处自动生效。Spring 5.3.35 是 5.3.x 维护分支的补丁版本，与 5.3.34 二进制兼容。

## 小结

- **成效**：将 `spring-web` 推进一个补丁版本，纳入 Spring 5.3.x 维护分支的修复（通常含安全补丁与缺陷修复），降低使用旧补丁版本的潜在风险。
- **影响范围**：仅 `gradle/libs.versions.toml` 单行，依赖以 `version.ref` 集中管理，影响所有引入 `spring-web` 的模块，但因属补丁版本升级、二进制兼容，运行时行为不变。
- **回迁注意事项**：回迁到 1.4.x 无风险，直接套用即可。需确认 1.4.x 的 `gradle/libs.versions.toml` 中 `spring-web` 仍以 `version.ref` 形式管理且当前版本不高于 5.3.35。若 1.4.x 上该版本已被其它提交升级，则无需回迁。Spring 5.3.x 与 `spring-boot = 2.7.18` 兼容，升级不触及 Spring Boot 主版本。
