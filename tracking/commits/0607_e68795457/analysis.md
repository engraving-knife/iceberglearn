# 提交 0607：Build: Bump org.springframework:spring-web from 5.3.30 to 5.3.33

## 提交信息

- **序号**：0607 / 4088
- **哈希**：e687954579a487217cbc069eea3ffd0a8a75c580
- **短哈希**：e68795457
- **日期**：2024-03-18 11:53:19 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.springframework:spring-web from 5.3.30 to 5.3.33 (#9989)
- **PR/Issue**：#9989

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 构建中独立声明的 `org.springframework:spring-web` 版本从 `5.3.30`（2023 年 7 月发布）升级到 `5.3.33`（2024 年 3 月发布），是一次 patch 级别（semver-patch）的安全补丁升级。提交与 0606（spring-boot 升级）在同一天由同一机器人紧邻合入，属于同一批供应链维护工作。

升级的核心动机是消除 Spring Framework 5.3.30 之后披露的 URL 解析与开放重定向类安全漏洞。5.3.30 → 5.3.33 期间（约 8 个月）Spring Framework 5.3.x 分支修复的关键 CVE 包括：

- **CVE-2024-22243**（在 5.3.33 中修复）：`UriComponentsBuilder` 解析用户可控 URL 时可被构造为开放重定向或 SSRF，影响使用 Spring MVC / WebFlux 处理 URL 的应用；
- **CVE-2024-22259**（在 5.3.32 中修复）：`UrlPathHelper` 解析 URL 路径时的异常行为；
- **CVE-2024-22234**（在 5.3.31 中修复）：`HandlerMethodArgumentResolver` 相关问题。

虽然 Iceberg 中 spring-web 是**纯测试域依赖**（`iceberg-aliyun` 模块的 `AliyunOSSMockApp` 使用 Spring MVC 构建 mock OSS REST 端点），不进入生产运行时路径，但供应链安全要求测试栈也不携带已知漏洞。

值得注意的是，spring-boot 2.7.18（0606 引入）自身已传递依赖 spring-web 5.3.x，但 Iceberg 在 Version Catalog 中**单独显式声明**了 `spring-web` 版本，目的是用 catalog 声明覆盖 Spring Boot BOM 传递的版本，确保 spring-web 始终跟随最新 patch。本提交正是对这一显式声明的例行 patch 追平。

## 如何达成设计目的

由于 Iceberg 使用 Gradle Version Catalog（`gradle/libs.versions.toml`）集中管理依赖版本，整个升级只需修改一处版本声明。Version Catalog 中 `spring-web = "5.3.30"` 改为 `spring-web = "5.3.33"`，所有通过 `version.ref = "spring-web"` 引用该版本的库声明会自动跟随，即：

- `spring-web = { module = "org.springframework:spring-web", version.ref = "spring-web" }`

这种集中式版本管理让 patch 级安全升级变得单点化、可控。由于是 patch 版本升级，API 完全兼容，无需改动任何业务或测试代码。

升级停留在 Spring Framework 5.3.x 而非跃迁到 6.x，原因与 0606 一致：6.x 要求 Jakarta EE 9+（`jakarta.*` 命名空间），而 `AliyunOSSMockApp` 使用的 `org.springframework.web.servlet.config.annotation.WebMvcConfigurer` 等 API 仍基于 `javax.*`，且 Spring Boot 2.7.x 只兼容 Spring Framework 5.3.x / 6.0.x 中的前者。5.3.33 是 5.3.x 分支当时的最新 patch。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `spring-web` 版本从 5.3.30 升级到 5.3.33，修复 CVE-2024-22243、CVE-2024-22259、CVE-2024-22234 等 URL 解析类安全漏洞。

**工作逻辑**：在版本目录的 `[versions]` 段，`spring-web = "5.3.30"` 改为 `spring-web = "5.3.33"`（diff 上下文位于 `spring-boot = "2.7.18"` 之后、`sqlite-jdbc` 之前，紧跟 0606 的改动）。该版本号被同文件 `[libraries]` 段的 `spring-web = { module = "org.springframework:spring-web", version.ref = "spring-web" }` 通过 `version.ref` 引用。`build.gradle` 中 `iceberg-aliyun` 模块（第 434 行）以 `testImplementation libs.spring.web` 直接消费该库，用于 `AliyunOSSMockApp`（`aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockApp.java`）——一个基于 `@EnableAutoConfiguration` + `@Configuration` + `@ComponentScan` 的 Spring MVC 应用，启动后提供 OSS REST 端点模拟，供 `AliyunOSSTestRule` / `TestLocalAliyunOSS` 等测试类使用。修改后，所有相关测试类路径会解析到 spring-web 5.3.33。

由于 catalog 中显式声明的 `spring-web` 版本优先级高于 Spring Boot 2.7.18 BOM 传递的版本，本次升级同时也覆盖了 `spring-boot-starter-web`（0606 引入）传递依赖的 spring-web 版本，确保整个测试栈中 spring-web 统一为 5.3.33，避免版本分裂。

## 小结

本提交通过一行 Version Catalog 版本号修改，将测试域依赖 `spring-web` 从 5.3.30 升级到 5.3.33，修复 2023 年下半年至 2024 年初披露的多个 Spring Framework URL 解析类安全漏洞（CVE-2024-22243/22259/22234），与同日合入的 0606（spring-boot 2.7.18）配套，确保 Iceberg 阿里云 OSS 集成测试栈中 Spring Boot + Spring Web 版本组合处于无已知漏洞状态。回迁到 1.4.x 时需注意：本提交依赖 0606 先行回迁（diff 上下文显示 `spring-boot` 已为 2.7.18），且 1.4.x 当前 spring-web 版本可能更旧（如 5.3.9），需确认是否先回迁早期 spring-web 升级；patch 版本升级 API 兼容，回迁风险极低。
