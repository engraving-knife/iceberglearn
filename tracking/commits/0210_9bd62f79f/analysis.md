# 提交 0210：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9202)

## 提交信息

- **序号**：0210 / 4088
- **哈希**：9bd62f79f8cd973c39d14e89163cb1c707470ed2
- **短哈希**：9bd62f79f
- **日期**：2023-12-03
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9202)
- **PR/Issue**：#9202

## 总体目的

Apache HttpClient 5（`org.apache.httpcomponents.client5:httpclient5`）是 Iceberg REST Catalog 客户端 `org.apache.iceberg.rest.HTTPClient` 的底层 HTTP 传输依赖，被 `HTTPClient` 直接用来构建 `CloseableHttpClient`、连接池管理器、请求/响应处理等。该依赖也出现在 `build.gradle` 多个子项目的 `implementation`、`compileOnly`、`testImplementation` 依赖声明中。本提交由 GitHub Dependabot 自动生成，把 `httpcomponents-httpclient5` 版本从 `5.2.1` 升级到 `5.2.3`，跨越两个 patch 版本（5.2.2、5.2.3）。

这是一次纯依赖维护升级，目的是跟随上游 HttpClient 5 的 patch 修复版本。根据上游 `RELEASE_NOTES.txt`，5.2.x 系列的 patch 版本通常包含 HTTP/1.1 与连接管理、连接池复用、SSL/TLS、重试逻辑等方面的 bug 修复与小幅改进。Dependabot 把依赖标记为 `direct:production`、更新类型 `version-update:semver-patch`，按 SemVer 应为向后兼容升级。对 Iceberg 演进的意义在于把 REST Catalog 客户端依赖的 HTTP 传输层持续保持在上游 patch 窗口内，及时拿到 bug 修复、避免因 SDK 偏旧暴露于已知缺陷之下——尤其考虑到 REST Catalog 是 Iceberg 主推的对接方式之一，HTTP 传输层的稳定性直接影响生产可用性。

值得注意的是本提交紧接在 0209（REST HttpClient connections config）之后一天合入：0209 引入了显式 `PoolingHttpClientConnectionManagerBuilder` 调用，对 HttpClient 5 的内部 API 有一定依赖；0210 把 HttpClient 5 自身推到最新 patch，确保新引入的连接池配置运行在最新修复过的 HttpClient 之上，二者形成连续的依赖治理动作。

## 如何达成设计目的

整体设计是一次单行版本号替换：在 `gradle/libs.versions.toml` 中把 `httpcomponents-httpclient5 = "5.2.1"` 改为 `5.2.3`。版本目录中同时定义了版本字符串和坐标：

```toml
httpcomponents-httpclient5 = "5.2.3"
...
httpcomponents-httpclient5 = { module = "org.apache.httpcomponents.client5:httpclient5", version.ref = "httpcomponents-httpclient5" }
```

由于该坐标通过 `version.ref` 引用同一字符串，所有引用方（`build.gradle` 中的 `libs.httpcomponents.httpclient5`）会自动同步升级，不需要任何调用代码改动。从 5.2.1 → 5.2.3 是 patch 升级，HttpClient 5 的对外 API（`HttpClients.custom()`、`PoolingHttpClientConnectionManagerBuilder`、`HttpClientConnectionManager` 等）保持稳定，Iceberg 现有调用代码（包括 0209 刚引入的连接池配置）均无需调整。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Apache HttpClient 5 客户端依赖从 5.2.1 升到 5.2.3，纳入上游 patch 修复。

**工作逻辑**：

`gradle/libs.versions.toml` 是 Iceberg 的 Gradle 版本目录，集中声明第三方依赖版本与坐标。本次 diff 只改一行：

```diff
-httpcomponents-httpclient5 = "5.2.1"
+httpcomponents-httpclient5 = "5.2.3"
```

`httpcomponents-httpclient5` 在版本目录里被声明为坐标 `org.apache.httpcomponents.client5:httpclient5`（通过 `version.ref` 引用同一字符串）。该坐标在 `build.gradle` 中以 `libs.httpcomponents.httpclient5` 的形式被多处引用：包括 `implementation`（运行时依赖）、`compileOnly`（编译期依赖）、`testImplementation`（测试依赖）等不同作用域。改这一行后，所有这些引用方的 HttpClient 5 版本同步升到 5.2.3。

Iceberg 的核心 REST 客户端 `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` 直接使用该坐标下的类（`CloseableHttpClient`、`HttpClientBuilder`、`HttpClients`、`PoolingHttpClientConnectionManagerBuilder`、`HttpClientConnectionManager`、`HttpUriRequestBase` 等）。由于 5.2.1 → 5.2.3 是 patch 升级，这些类的对外签名不变，因此无需改动任何业务代码即可完成升级。这种依赖跟随是 Iceberg 在 REST Catalog 这一主推对接路径上保持 HTTP 传输层稳定性的常规手段。

## 小结

本提交是 Apache HttpClient 5 依赖的例行维护：在 `gradle/libs.versions.toml` 中把 `httpcomponents-httpclient5` 从 5.2.1 升到 5.2.3，通过版本目录统一同步所有引用方，纳入上游 patch 修复；同时与前一提交（0209 REST 连接池配置）形成连续的依赖治理动作，确保新引入的连接池配置运行在最新 HttpClient 5 之上。
