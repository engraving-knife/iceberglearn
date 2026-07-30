# 提交 1062：AWS, Core: Slim down Jetty config for tests (#10945)

## 提交信息

- **序号**：1062 / 4088
- **哈希**：9f12cf91dce532b68ce542379647bdc24cd94d07
- **短哈希**：9f12cf91d
- **日期**：2024-08-15 14:40:43 -0600
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：AWS, Core: Slim down Jetty config for tests (#10945)
- **PR/Issue**：#10945

## 总体目的

Iceberg 在多个 REST 相关测试用例中（`TestS3RestSigner`、`TestRESTCatalog`、`TestRESTViewCatalog`）使用嵌入式 Jetty 服务器来托管 REST Catalog / S3 Signer 的 Servlet，用以模拟真实 REST 服务端进行集成测试。这些测试在构造 `ServletContextHandler` 时使用了一系列"样板"配置，包括 `setContextPath("/")`、设置 `javax.ws.rs.Application` 初始化参数为 `ServiceListPublic`、`setVirtualHosts(null)`、`setGzipHandler(new GzipHandler())` 等。

本提交的目的是把这些冗余且语义上不正确的配置项瘦身掉，使测试代码更精简、更贴近 Jetty 的真实使用方式。具体来说：`setContextPath("/")` 是默认值无需显式设置；`javax.ws.rs.Application` 初始化参数对自定义的裸 Servlet 没有意义；`setVirtualHosts(null)` 也是默认行为；而 `setGzipHandler(...)` 在原代码中并未真正把 Servlet 挂到 GzipHandler 之下，导致 GzipHandler 实际上没有起作用。这次重写不仅简化了代码，还顺手修正了 GzipHandler 的挂载方式，使其按 Jetty 的 handler 链方式正确生效。

## 如何达成设计目的

实现思路是把三个测试类里几乎完全相同的"Servlet 挂载 + Gzip 配置"代码段统一替换为更简洁的写法：

- 不再调用 `setContextPath("/")`，依赖 Jetty 默认行为（在 `TestRESTViewCatalog` 中保留了 `setContextPath("/")`，因为该处略去也无碍）；
- 不再为 `ServletHolder` 设置 `javax.ws.rs.Application = ServiceListPublic` 初始化参数，因为被注册的是自定义 Servlet，并不是 JAX-RS 的 Application 容器；
- 不再调用 `setVirtualHosts(null)`，这是默认行为；
- 把原来的 `servletContext.setGzipHandler(new GzipHandler())` 改写为 `servletContext.setHandler(new GzipHandler())`：在 Jetty 中，`setGzipHandler` 会把 GzipHandler 注入到 handler 链中作为外层包装，但原代码同时设置 ServletContextHandler 又调用 `setGzipHandler`，并不是把 ServletContextHandler 作为子 handler 挂到 GzipHandler 之下，gzip 逻辑其实未生效。改用 `setHandler` 显式把 GzipHandler 设为子 handler 的方式，意图更清晰。

这样的精简在不影响测试覆盖行为的前提下，提升了代码可读性。

## 修改详情

### `aws/src/test/java/org/apache/iceberg/aws/s3/signer/TestS3RestSigner.java`

**修改目的**：精简 S3 REST Signer 测试中 Jetty 服务器的 Servlet 上下文配置，去掉冗余设置并修正 GzipHandler 的挂载方式。

**工作逻辑**：原代码先构造 `ServletHolder`，设置 `javax.ws.rs.Application` 初始化参数，再 `addServlet`，然后 `setVirtualHosts(null)`、`setGzipHandler(new GzipHandler())`。改写后直接 `servletContext.addServlet(new ServletHolder(servlet), "/*")` 一行完成 Servlet 注册，并使用 `servletContext.setHandler(new GzipHandler())` 显式把 GzipHandler 设为上下文 handler。等价的功能保留，代码由 6 行收缩为 2 行。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：精简 TestRESTCatalog 中 Jetty 测试服务器的配置逻辑，与 AWS 模块保持一致。

**工作逻辑**：原本把 `RESTCatalogServlet` 单独赋值给局部变量 `servlet` 再构造 `ServletHolder` 并设置 `javax.ws.rs.Application` 初始化参数，调用 `setContextPath("/")`、`setVirtualHosts(null)`、`setGzipHandler(...)`。改写后直接 `servletContext.addServlet(new ServletHolder(new RESTCatalogServlet(adaptor)), "/*")`，再 `servletContext.setHandler(new GzipHandler())`，由 6 行收缩为 2 行。同时删除了不再使用的局部变量 `servlet`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java`

**修改目的**：精简 TestRESTViewCatalog 中 Jetty 测试服务器的配置逻辑，保持与其他两个测试一致。

**工作逻辑**：改动模式与上面相同，把原 6 行样板替换为 2 行；与 `TestRESTCatalog` 唯一不同的是这里保留了 `servletContext.setContextPath("/")` 一行，可能是历史遗留或该测试对 context path 有显式依赖，保留并不影响整体瘦身目标。

## 小结

- **成效**：删除 19 行冗余的 Jetty 配置代码，新增 6 行更简洁的写法；同时修正了 `setGzipHandler` 实际未生效的隐患，统一了三个 REST 相关测试的 Jetty 配置风格。
- **影响范围**：仅涉及 3 个测试文件（`aws` 模块 1 个、`core` 模块 2 个），全部为 `src/test/java` 目录，不影响生产代码、API 兼容性或运行时行为。
- **回迁到 1.4.x 的注意事项**：这是一次纯测试代码重构，对功能无任何影响，回迁到 1.4.x 风险极低；但需要确认 1.4.x 分支上这三个测试文件的存在和上下文是否一致，如果 1.4.x 上已存在相同的样板代码，cherry-pick 可直接采纳；若 1.4.x 测试框架或 Jetty 版本不同，需要复核 `setGzipHandler` 与 `setHandler` 的语义差异在该版本上仍然成立。
