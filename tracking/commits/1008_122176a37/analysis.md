# 提交 1008：Core: Upgrade Jetty and Servlet API (#10850)

## 提交信息

- **序号**：1008 / 4088
- **哈希**：122176a37fed02f6883a75fee440fb1e7ba161ec
- **短哈希**：122176a37
- **日期**：2024-08-02 14:08:43 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Upgrade Jetty and Servlet API (#10850)
- **PR/Issue**：#10850

## 总体目的

Iceberg core 模块的 REST 相关测试（`RESTCatalogServlet` 等）依赖 Jetty 作为内嵌 servlet 容器来模拟 REST Catalog 服务端。原本使用的是 Jetty 9.4.x（`9.4.55.v20240627`），它对应的是 `javax.servlet` 命名空间（Java EE 时代的 Servlet API 3.x/4.x）。Jetty 9.4.x 已经进入维护末期，且随着 Jakarta EE 的迁移，servlet API 的包名从 `javax.servlet` 迁移到 `jakarta.servlet`，社区主线已转向 Jetty 11+。

本提交的目的是把 Iceberg core 测试使用的 Jetty 从 9.4.x 升级到 11.0.22，并把 Servlet API 从 `javax.servlet` 迁移到 `jakarta.servlet`。提交说明中特别指出 "This is the latest Jetty version that runs with JDK11"——即选择 Jetty 11.0.22 是因为它是能在 JDK 11 上运行的最新 Jetty 版本（Jetty 12 要求 JDK 17）。这一升级为后续可能的依赖现代化与安全修复铺路，同时与同期"Drop support for Java 8"的提交保持一致（最低 JDK 已是 11）。

## 如何达成设计目的

实现方式分三步：

1. 在 `gradle/libs.versions.toml` 中把 `jetty` 版本由 `9.4.55.v20240627` 改为 `11.0.22`，并新增 `jakarta-servlet-api = "6.1.0"` 版本号与 `jakarta-servlet` 库别名（`jakarta.servlet:jakarta.servlet-api`）。
2. 在 `build.gradle` 的 `iceberg-core` 项目中，新增 `testImplementation libs.jakarta.servlet`，使测试代码能编译依赖 `jakarta.servlet` API。
3. 在 `core/src/test/java/org/apache/iceberg/rest/RESTCatalogServlet.java` 中，把三处 `javax.servlet.http.*` 的 import 改为 `jakarta.servlet.http.*`（`HttpServlet`、`HttpServletRequest`、`HttpServletResponse`），完成命名空间迁移。Jetty 11 自身的 server/servlet 模块已内置使用 `jakarta.servlet`，因此无需改动其它代码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Jetty 版本并引入 Jakarta Servlet API 依赖坐标。

**工作逻辑**：
- `[versions]` 段新增 `jakarta-servlet-api = "6.1.0"`，并把 `jetty = "9.4.55.v20240627"` 改为 `jetty = "11.0.22"`。
- `[libraries]` 段新增 `jakarta-servlet = {module = "jakarta.servlet:jakarta.servlet-api", version.ref = "jakarta-servlet-api"}`，供 build.gradle 引用。

### `build.gradle`

**修改目的**：让 `iceberg-core` 测试能使用 Jakarta Servlet API。

**工作逻辑**：在 `iceberg-core` 项目的依赖块中，紧跟 `testImplementation libs.jetty.servlet` 之后新增 `testImplementation libs.jakarta.servlet`。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogServlet.java`

**修改目的**：把 Servlet API 的 import 从 `javax.servlet` 命名空间迁移到 `jakarta.servlet`，以匹配 Jetty 11。

**工作逻辑**：
- 新增 `import jakarta.servlet.http.HttpServlet;`、`import jakarta.servlet.http.HttpServletRequest;`、`import jakarta.servlet.http.HttpServletResponse;`。
- 删除对应的三个 `import javax.servlet.http.*;`。
- 类体本身无需改动，因为 `jakarta.servlet.http.HttpServlet` 等的 API 与 `javax` 时代保持二进制兼容（仅包名变化）。

## 小结

- **成效**：将 Iceberg core 测试使用的 Jetty 从 9.4.55 升级到 11.0.22（JDK 11 上可用的最新 Jetty），并把 Servlet API 从 `javax.servlet` 迁移到 `jakarta.servlet`，完成 Jakarta EE 命名空间迁移。`RESTCatalogServlet` 测试辅助类保持功能不变。
- **影响范围**：仅 core 模块测试相关，3 个文件、+7/-4 行，无生产代码变更。
- **回迁到 1.4.x 的注意事项**：是否回迁移取决于 1.4.x 是否仍支持 Java 8。如果 1.4.x 仍支持 Java 8（如提交 1005 所述，1.4.x 不应回迁"Drop Java 8"），则**不建议回迁**本提交——因为 Jetty 11 虽然能在 JDK 11 上运行，但它要求 JDK 11+，与 1.4.x 可能仍需支持 Java 8 的目标冲突；且 `jakarta.servlet` 迁移一旦做了，会影响所有依赖该测试 servlet 的测试。如果 1.4.x 已决定放弃 Java 8，则可以回迁，但需同步检查 1.4.x 上是否有其它测试代码仍使用 `javax.servlet`。整体属于中等风险，主要受 1.4.x 的 JDK 支持策略约束。
