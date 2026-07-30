# 提交 0049：Build: Upgrade to Jetty 9.4.53.v20231009 (#8830)

## 提交信息

- **序号**：0049 / 4088
- **哈希**：a339f409178e2f3148bad8cd162ee92cd3c76985
- **短哈希**：a339f4091
- **日期**：2023-10-13 19:22:59 +0200
- **作者**：JB Onofré
- **提交说明**：Build: Upgrade to Jetty 9.4.53.v20231009 (#8830)
- **PR/Issue**：#8830

## 总体目的

本提交将 Iceberg 构建中声明的 Jetty 版本从 `9.4.43.v20210629`（2021 年 6 月）升级到 `9.4.53.v20231009`（2023 年 10 月，与本提交同月发布），跨度约两年三个月。这是一次依赖安全升级，主要动机是消除 9.4.43 之后 Jetty 9.4.x 分支累积披露的多个安全漏洞。

这段时间内 Jetty 9.4.x 分支修复的重要 CVE 包括：
- **CVE-2022-2047**：URI 合规性解析问题，可在 9.4.47 之前版本上触发异常行为；
- **CVE-2022-2048**：WebSocket 帧处理可导致 DoS，影响 9.4.47 之前版本；
- **CVE-2023-26048**：multipart 请求处理可被构造为耗尽内存（OOM），影响 9.4.51 之前版本；
- **CVE-2023-26049**：Cookie 解析可被注入伪造 cookie，影响 9.4.51 之前版本；
- **CVE-2023-36479**：`CookieCRLF` 验证绕过，影响 9.4.51–9.4.52，**在 9.4.53 中修复**。

升级到 9.4.53 恰好覆盖到最新披露的 CVE-2023-36479，时间点也吻合（9.4.53 于 2023-10-09 发布，本提交于 2023-10-13 合入）。这表明作者有意追平 Jetty 当时最新的安全补丁。

在 Iceberg 中，Jetty 与提交 0048 升级的 spring-web 同属测试域依赖：从 [`build.gradle`](../../../../build.gradle) 第 357–358 行可见，`libs.jetty.servlet` 和 `libs.jetty.server` 被 `iceberg-core` 模块以 `testImplementation` 引入；第 435 行 `iceberg-aliyun` 模块通过 `spring-boot-starter-jetty` 间接引入 Jetty，配合 S3Mock 启动本地模拟 S3 服务用于阿里云 OSS 集成测试。因此本升级主要影响测试 fixture 的安全性，不进入 Iceberg 表引擎的生产运行时路径，但作为供应链安全加固仍有必要。本提交与 0048（spring-web 升级）是同一作者在同一天紧邻合入的配套升级，共同保证 S3Mock 测试栈中 Spring Boot + Jetty 的版本组合处于受支持且无已知漏洞的状态。

## 如何达成设计目的

与 0048 一样，由于 Iceberg 使用 Gradle Version Catalog 集中管理依赖版本，整个升级只需修改一处版本声明。Version Catalog 中 `jetty = "9.4.43.v20210629"` 改为 `jetty = "9.4.53.v20231009"`，所有通过 `version.ref = "jetty"` 引用该版本的库（即 `jetty-server` 和 `jetty-servlet` 两个库声明）会自动跟随。Jetty 的版本号本身就是其发布标识（`v20231009` 表示 2023-10-09 构建），保留这一格式以匹配上游。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Jetty 版本从 9.4.43.v20210629 升级到 9.4.53.v20231009，修复 CVE-2022-2047、CVE-2022-2048、CVE-2023-26048、CVE-2023-26049、CVE-2023-36479 等多个安全漏洞。

**工作逻辑**：在版本目录的 `[versions]` 段，第 44 行 `jetty = "9.4.43.v20210629"` 改为 `jetty = "9.4.53.v20231009"`。该版本号被同文件 `[libraries]` 段的两条声明通过 `version.ref = "jetty"` 引用：
- `jetty-server = { module = "org.eclipse.jetty:jetty-server", version.ref = "jetty" }`
- `jetty-servlet = { module = "org.eclipse.jetty:jetty-servlet", version.ref = "jetty" }`

`build.gradle` 中 `iceberg-core` 模块以 `testImplementation libs.jetty.servlet` / `libs.jetty.server` 消费这两个库；`iceberg-aliyun` 模块则通过 `spring-boot-starter-jetty` 间接引入 Jetty（其版本由 Spring Boot BOM 管理，但 catalog 中 `jetty` 版本号会被显式覆盖）。修改后所有相关测试类路径都会解析到 9.4.53。注意 Iceberg 仍保持在 Jetty 9.4.x 分支（而非跃迁到 Jetty 11/12），因为 Spring Boot 2.5.4 与 S3Mock 当时的版本依赖 Servlet 3.1/4.0 API，与 Jetty 9.4.x 兼容；跳到 Jetty 11+ 需要 Servlet 5.x 与 Jakarta EE 9 命名空间迁移，不在本提交范围。

## 小结

本提交通过一行 Version Catalog 版本号修改，将测试域依赖 Jetty 从 9.4.43 升级到 9.4.53，修复了 2021 年至 2023 年间披露的多个 Jetty 安全漏洞（含最新发布的 CVE-2023-36479），与紧邻合入的 spring-web 5.3.30 升级（0048）配套，共同加固 Iceberg 阿里云 OSS 集成测试栈的供应链安全。
