# 提交 0608：Build: Bump jetty from 9.4.53.v20231009 to 9.4.54.v20240208

## 提交信息

- **序号**：0608 / 4088
- **哈希**：a3f8879815138a6e815115578386a1c5f106d807
- **短哈希**：a3f887981
- **日期**：2024-03-18 11:53:56 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump jetty from 9.4.53.v20231009 to 9.4.54.v20240208 (#9982)
- **PR/Issue**：#9982

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 构建中声明的 Jetty 版本从 `9.4.53.v20231009`（2023 年 10 月 9 日发布，由 0049 引入）升级到 `9.4.54.v20240208`（2024 年 2 月 8 日发布），是一次 patch 级别（semver-patch）的安全补丁升级。提交与 0606（spring-boot）、0607（spring-web）在同一天由同一机器人紧邻合入，属于同一批供应链维护工作，共同把 Iceberg 测试栈中的 Spring Boot + Spring Web + Jetty 组合拉齐到最新状态。

升级的核心动机是消除 9.4.53 之后 Jetty 9.4.x 分支披露的安全漏洞。9.4.53 → 9.4.54 期间（约 4 个月）Jetty 修复的关键 CVE 是：

- **CVE-2024-22201**（在 9.4.54 中修复，2024 年 2 月披露）：HTTP/2 协议层可通过构造大量 SETTINGS 帧或异常流量导致服务端内存耗尽（DoS）。该漏洞影响所有启用 HTTP/2 的 Jetty 版本（含 9.4.x、10.x、11.x、12.x），在 9.4.54 / 10.0.20 / 11.0.20 / 12.0.6 中修复。9.4.54 于 2024-02-08 发布，本提交于 2024-03-18 合入，时间点吻合，表明作者有意追平该补丁。

在 Iceberg 中，Jetty 是**纯测试域依赖**，有两个消费路径：

1. **iceberg-core 模块**（`build.gradle` 第 357–358 行）以 `testImplementation libs.jetty.servlet` 和 `libs.jetty.server` 直接引入。`TestRESTCatalog`（`core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`）使用 Jetty 的 `Server` + `ServletContextHandler` + `ServletHolder` 启动一个真实的嵌入式 HTTP 服务（`new Server(0)` 绑定随机端口），用于测试 REST Catalog 客户端与服务端的端到端交互（如 catalog 初始化、表操作、server-side retry 等）。这是 Jetty 在 Iceberg 中最主要、最直接的消费点。
2. **iceberg-aliyun 模块**通过 `spring-boot-starter-jetty`（0606 引入的 spring-boot 2.7.18）间接引入 Jetty，用于 `AliyunOSSMockApp` 测试 fixture。该路径下 Jetty 版本由 Spring Boot BOM 管理，但 Gradle 依赖解析会在同一类路径上选取更高版本，因此 catalog 中显式声明的 `jetty` 版本也会影响该模块测试类路径中 Jetty 的最终解析版本。

因此本升级主要影响测试 fixture 的安全性，不进入 Iceberg 表引擎的生产运行时路径，但作为供应链安全加固仍有必要，尤其 `TestRESTCatalog` 启动了真实 HTTP 服务，理论上可被构造的 HTTP/2 流量利用（尽管测试中不启用 HTTP/2）。

## 如何达成设计目的

由于 Iceberg 使用 Gradle Version Catalog（`gradle/libs.versions.toml`）集中管理依赖版本，整个升级只需修改一处版本声明。Version Catalog 中 `jetty = "9.4.53.v20231009"` 改为 `jetty = "9.4.54.v20240208"`，所有通过 `version.ref = "jetty"` 引用该版本的库声明会自动跟随，包括：

- `jetty-server = { module = "org.eclipse.jetty:jetty-server", version.ref = "jetty" }`
- `jetty-servlet = { module = "org.eclipse.jetty:jetty-servlet", version.ref = "jetty" }`

Jetty 的版本号本身就是其发布标识（`v20240208` 表示 2024-02-08 构建），保留这一格式以匹配上游。由于是 patch 版本升级，API 完全兼容，无需改动任何业务或测试代码。

升级停留在 Jetty 9.4.x 而非跃迁到 11/12，原因与 0606/0607 一致：Jetty 11+ 要求 Servlet 5.x 与 Jakarta EE 9（`jakarta.*` 命名空间），而当前 `TestRESTCatalog` 与 `AliyunOSSMockApp` 基于 `javax.servlet` API，且 Spring Boot 2.7.x 只兼容 Jetty 9.4.x。9.4.54 是 9.4.x 分支当时的最新 patch。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Jetty 版本从 9.4.53.v20231009 升级到 9.4.54.v20240208，修复 CVE-2024-22201（HTTP/2 DoS）。

**工作逻辑**：在版本目录的 `[versions]` 段，`jetty = "9.4.53.v20231009"` 改为 `jetty = "9.4.54.v20240208"`（diff 上下文位于 `jaxb-runtime` 之后、`junit` 之前）。该版本号被同文件 `[libraries]` 段的两条声明通过 `version.ref = "jetty"` 引用：
- `jetty-server`：提供嵌入式 HTTP 服务核心（`org.eclipse.jetty.server.Server`）
- `jetty-servlet`：提供 Servlet 容器支持（`ServletContextHandler`、`ServletHolder`）

`build.gradle` 中 `iceberg-core` 模块（第 357–358 行）以 `testImplementation` 消费这两个库，供 `TestRESTCatalog`（第 86–89 行 import `org.eclipse.jetty.server.Server`、`GzipHandler`、`ServletContextHandler`、`ServletHolder`；第 175–186 行在 `init()` 中构造并启动 `Server(0)`）使用。修改后，iceberg-core 测试类路径会解析到 Jetty 9.4.54。对 iceberg-aliyun，`spring-boot-starter-jetty` 传递的 Jetty 版本会被 catalog 显式声明（在解析冲突时取更高版本）拉齐到 9.4.54，确保整个测试栈 Jetty 版本统一。

## 小结

本提交通过一行 Version Catalog 版本号修改，将测试域依赖 Jetty 从 9.4.53 升级到 9.4.54，修复 CVE-2024-22201（HTTP/2 DoS），与同日合入的 0606（spring-boot 2.7.18）、0607（spring-web 5.3.33）配套，共同加固 Iceberg REST Catalog 与阿里云 OSS 集成测试栈的供应链安全。回迁到 1.4.x 时需注意：本提交与 0606/0607 应配套回迁以保持版本组合一致性；1.4.x 当前 jetty 版本可能仍为 9.4.43（未回迁 0049），需先回迁 0049 再回迁本提交；patch 版本升级 API 兼容，`TestRESTCatalog` 无需改动，回迁风险低。
