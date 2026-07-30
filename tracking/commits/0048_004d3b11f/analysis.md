# 提交 0048：Build: Upgrade to spring-web 5.3.30 (#8828)

## 提交信息

- **序号**：0048 / 4088
- **哈希**：004d3b11fb405301e27d47eec12c051b5f321fb4
- **短哈希**：004d3b11f
- **日期**：2023-10-13 19:22:32 +0200
- **作者**：JB Onofré
- **提交说明**：Build: Upgrade to spring-web 5.3.30 (#8828)
- **PR/Issue**：#8828

## 总体目的

本提交将 Iceberg 构建中声明的 `spring-web` 依赖版本从 `5.3.9`（2021 年 7 月发布）升级到 `5.3.30`（2023 年下半年发布），跨度超过两年。这是一次典型的依赖安全升级，主要动机是修复 5.3.9 之后 Spring Framework 累积披露的多个安全漏洞。

最关键的是 CVE-2022-22965（即业界俗称的"Spring4Shell"），该漏洞影响 Spring Framework 5.3.0–5.3.17 版本，允许在满足特定条件（JDK 9+、运行于 Tomcat 等 servlet 容器、参数绑定到非基本类型字段）时通过恶意请求注入 `class.module.classLoader.*` 路径实现远程代码执行，于 2022 年 3 月公开，CVSS 评分 9.8（Critical）。5.3.9 正处于受影响范围，升级到 5.3.30 直接消除了该 Critical 级漏洞。此外，5.3.9 → 5.3.30 之间还修复了若干其他 CVE 与缺陷（如对 URI 路径处理、WebFlux 相关问题等），并对 Java 17+ 兼容性做了改进。

在 Iceberg 中，`spring-web` 并非生产运行时依赖，而是测试域依赖。从 [`build.gradle`](../../../../build.gradle) 第 434 行可见，`libs.spring.web` 被 `iceberg-aliyun` 模块以 `testImplementation` 引入，配合 `spring-boot-starter-jetty`、`spring-boot-starter-web` 以及 `s3mock-junit5` 一起，用于在阿里云 OSS 集成测试中通过 S3Mock 启动一个本地模拟的 S3 服务（S3Mock 基于 Spring Boot + Jetty 实现）。因此本升级主要影响阿里云 OSS 测试 fixture 的安全性，而非 Iceberg 表引擎本身。但出于供应链安全考虑（避免构建产物中捆绑已知漏洞版本的传递依赖），即使仅用于测试，升级也是必要的。

本次升级是紧随其后提交 0049（Jetty 升级）的前置配套——S3Mock 同时依赖 Spring Boot/Jetty 作为内嵌 servlet 容器，两者需协同升级以保持版本兼容。

## 如何达成设计目的

由于 Iceberg 使用 Gradle Version Catalog（`gradle/libs.versions.toml`）集中管理依赖版本，整个升级只需修改一处版本声明。Version Catalog 的 `spring-web = "5.3.9"` 改为 `spring-web = "5.3.30"`，所有通过 `version.ref = "spring-web"` 引用该版本的库（即 `spring-web = { module = "org.springframework:spring-web", version.ref = "spring-web" }`）会自动跟随。这种集中式版本管理让依赖升级变得单点化、可控，避免了在多个 `build.gradle` 中分散修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `spring-web` 版本从 5.3.9 升级到 5.3.30，修复累积安全漏洞（最关键为 CVE-2022-22965 Spring4Shell）。

**工作逻辑**：在版本目录的 `[versions]` 段，第 67 行 `spring-web = "5.3.9"` 改为 `spring-web = "5.3.30"`。该版本号被同文件 `[libraries]` 段的 `spring-web = { module = "org.springframework:spring-web", version.ref = "spring-web" }` 通过 `version.ref` 引用；`build.gradle` 中 `iceberg-aliyun` 模块以 `testImplementation libs.spring.web` 消费该库（用于 S3Mock 测试 fixture）。修改后，所有传递依赖 `spring-web` 的测试类路径都会解析到 5.3.30。其余引用 Spring 体系（如 `spring-boot = "2.5.4"`、`spring-boot-starter-jetty`、`spring-boot-starter-web`）的版本号未在本提交中改动，依赖 Spring Boot 2.5.4 自带的 spring-web 版本管理会被本 catalog 显式声明的 5.3.30 覆盖（catalog 声明优先级高于 BOM 传递版本）。

## 小结

本提交通过一行 Version Catalog 版本号修改，将测试域依赖 `spring-web` 从存在 Spring4Shell（CVE-2022-22965）等高危漏洞的 5.3.9 升级到 5.3.30，提升 Iceberg 阿里云 OSS 集成测试 fixture 的供应链安全性，并与紧随其后的 Jetty 升级（0049）配套使用。
