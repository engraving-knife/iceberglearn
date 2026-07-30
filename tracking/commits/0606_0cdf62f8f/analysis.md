# 提交 0606：Build: Bump spring-boot from 2.5.4 to 2.7.18

## 提交信息

- **序号**：0606 / 4088
- **哈希**：0cdf62f8fd90f0480d46b2b74787e1db87042ea6
- **短哈希**：0cdf62f8f
- **日期**：2024-03-18 11:02:52 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump spring-boot from 2.5.4 to 2.7.18 (#9985)
- **PR/Issue**：#9985

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 构建中声明的 Spring Boot 版本从 `2.5.4`（2021 年 8 月发布）升级到 `2.7.18`（2023 年 11 月发布，是 Spring Boot 2.7.x 分支的最后一个版本，也是 Spring Boot 2.x 开源支持的终版）。这是一次跨越两个 minor 版本（2.5 → 2.6 → 2.7）的安全与可维护性升级。

升级的核心动机包括：

1. **消除安全漏洞**：Spring Boot 2.5.4 距离 2.7.18 发布间隔超过两年，期间 2.5.x 与 2.7.x 分支累积修复了大量 CVE。其中最关键的是其传递依赖的 Spring Framework 上的 **CVE-2022-22965（Spring4Shell）**——一个允许远程代码执行的高危漏洞，影响使用 JDK 9+ 且运行在 Servlet 容器上的 Spring 应用。虽然 Iceberg 中 Spring Boot 仅用于测试，但供应链安全要求测试栈也不能携带已知高危漏洞。
2. **对齐受支持版本**：Spring Boot 2.5.x 开源支持已于 2022 年 11 月终止，2.6.x 于 2023 年 5 月终止，2.7.x 是 2.x 系列最后一个仍能获得社区安全补丁的分支（2.7.18 为终版）。停留在 2.5.4 意味着测试栈运行在无人维护的基线上。
3. **配套其他依赖升级**：本提交与紧邻的 0607（spring-web 5.3.30 → 5.3.33）、0608（jetty 9.4.53 → 9.4.54）同属一批 Dependabot 升级，共同把 Iceberg 阿里云 OSS 集成测试栈中的 Spring Boot + Spring Web + Jetty 组合拉齐到无已知漏洞的最新受支持状态。

在 Iceberg 中，Spring Boot 是**纯测试域依赖**：`iceberg-aliyun` 模块通过 `spring-boot-starter-jetty` 和 `spring-boot-starter-web` 启动一个本地模拟 OSS 服务（`AliyunOSSMockApp`），用于阿里云 OSS 集成测试。它不进入 Iceberg 表引擎的生产运行时路径，因此本升级不影响 Iceberg 库的生产行为，但作为供应链安全加固仍有必要。

## 如何达成设计目的

由于 Iceberg 使用 Gradle Version Catalog（`gradle/libs.versions.toml`）集中管理依赖版本，整个升级只需修改一处版本声明。Version Catalog 中 `spring-boot = "2.5.4"` 改为 `spring-boot = "2.7.18"`，所有通过 `version.ref = "spring-boot"` 引用该版本的库声明会自动跟随，包括：

- `spring-boot-starter-jetty = { module = "org.springframework.boot:spring-boot-starter-jetty", version.ref = "spring-boot" }`
- `spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web", version.ref = "spring-boot" }`

这种集中式版本管理让跨多个 minor 版本的升级变得单点化、可控，无需在多个 `build.gradle` 中分散修改。

升级刻意停留在 Spring Boot 2.7.x 而非跃迁到 3.x，原因在于：Spring Boot 3.x 要求 Jakarta EE 9+（`jakarta.*` 命名空间），而当前 `AliyunOSSMockApp` 使用的 `org.springframework.web.servlet.config.annotation.WebMvcConfigurer` 等 API 仍基于 `javax.*` 命名空间，且与之配套的 Jetty 9.4.x 也只支持 Servlet 3.1/4.0。跳到 Spring Boot 3.x 需要同步迁移到 Jetty 11/12 并改造所有 `javax.servlet` 引用，工程量大且超出 Dependabot 自动升级范围。2.7.18 作为 2.x 终版，是在不破坏现有测试代码前提下能升级到的最高版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Spring Boot 版本从 2.5.4 升级到 2.7.18，跨两个 minor 版本，修复累积安全漏洞（含 Spring4Shell 相关风险）并对齐到 2.x 分支最后的受支持版本。

**工作逻辑**：在版本目录的 `[versions]` 段，`spring-boot = "2.5.4"` 改为 `spring-boot = "2.7.18"`（diff 上下文位于 `snowflake-jdbc`、`spark-hive33/34/35` 与 `spring-web`、`sqlite-jdbc` 之间）。该版本号被同文件 `[libraries]` 段的两条声明通过 `version.ref = "spring-boot"` 引用：
- `spring-boot-starter-jetty`：提供嵌入式 Jetty Servlet 容器
- `spring-boot-starter-web`：提供 Spring MVC 与 Web 自动装配

`build.gradle` 中 `iceberg-aliyun` 模块（约第 435–443 行）以 `testImplementation` 消费这两个 starter，并显式排除 `logback-classic`、`spring-boot-starter-logging`（避免与 Iceberg 自有日志栈冲突）以及部分 Jetty websocket 模块。`AliyunOSSMockApp`（`aliyun/src/test/java/org/apache/iceberg/aliyun/oss/mock/AliyunOSSMockApp.java`）通过 `SpringApplicationBuilder` 启动该 mock 服务，使用 `@EnableAutoConfiguration`（排除 `SecurityAutoConfiguration`）+ `@Configuration` + `@ComponentScan` 装配一个基于 Spring MVC 的 OSS REST 端点模拟器，供 `AliyunOSSTestRule` / `TestLocalAliyunOSS` 等测试类使用。修改后，所有相关测试类路径会解析到 Spring Boot 2.7.18 及其传递依赖（如 Spring Framework 5.3.x、Tomcat/Jetty 适配器等）。

此外，`iceberg-aws` 模块通过 `libs.s3mock.junit5` 间接引入 Spring Boot（S3Mock 内部依赖 spring-boot-starter），本次升级也会影响其测试类路径中 Spring Boot 的解析版本（catalog 声明优先级高于 S3Mock 传递版本）。

## 小结

本提交通过一行 Version Catalog 版本号修改，将测试域依赖 Spring Boot 从 2.5.4（2021-08）升级到 2.7.18（2023-11，2.x 终版），覆盖 2.5 → 2.6 → 2.7 两个 minor 版本跨度内累积的安全修复，消除测试栈中 Spring Boot 2.5.x 停留在开源支持终止状态带来的供应链风险。升级停留在 2.7.x 而非 3.x，保持与现有 `javax.*` 命名空间、Jetty 9.4.x 及 `AliyunOSSMockApp` 测试代码的兼容。回迁到 1.4.x 时需注意：1.4.x 当前 `spring-boot` 仍为 2.5.4，回迁后需同步验证 `iceberg-aliyun` 模块的 OSS mock 测试在 2.7.18 下能正常启动（Spring Boot 2.6 起默认禁用循环引用、2.7 引入 `@AutoConfiguration` 注解但仍兼容旧 `@EnableAutoConfiguration`），并确保与 0607/0608 的 spring-web、jetty 升级配套合入以保持版本组合一致性。
