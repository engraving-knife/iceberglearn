# 提交 0209：Core: REST HttpClient connections config (#9195)

## 提交信息

- **序号**：0209 / 4088
- **哈希**：09a8ad5c391d4e1b1027a02b7e2d366d0ff56fc2
- **短哈希**：09a8ad5c3
- **日期**：2023-12-02
- **作者**：Daniel Weeks
- **提交说明**：Core: REST HttpClient connections config (#9195)
- **PR/Issue**：#9195

## 总体目的

Apache Iceberg 的 REST Catalog 客户端 `org.apache.iceberg.rest.HTTPClient` 内部基于 Apache HttpClient 5（`org.apache.hc.client5`）实现 HTTP 调用。在此提交之前，`HTTPClient` 通过 `HttpClients.custom()` 构建客户端，并未显式配置连接池——这意味着它落到了 HC5 的默认 `PoolingHttpClientConnectionManager` 上，默认每路由（per route，即每个目标 host）只允许 2 个并发连接、总连接数也受限。对于高并发的 REST Catalog 工作负载（多个引擎/查询同时拉取元数据、提交事务），这种默认值会立刻成为瓶颈：请求在连接池前排队、吞吐被压低，且无法通过 Iceberg 配置项调整。

本提交在 `HTTPClient` 构造时显式注入一个 `PoolingHttpClientConnectionManager`，并暴露两个新的可配置项：

- `rest.client.max-connections`：连接池总连接数上限，默认 100。
- `rest.client.connections-per-route`：每路由并发连接数上限，默认 100。

默认值都设为 100，相比 HC5 自带的默认（每路由 2）有数量级提升，对绝大多数 REST Catalog 场景够用；同时把上限暴露为可配置项，让运维侧能针对具体部署进一步调优。对 Iceberg 演进的意义在于：使 REST Catalog 客户端从"能跑"升级为"在并发场景下能撑住"，让连接池这一关键运行时参数对用户可见、可调。

## 如何达成设计目的

整体设计是"显式构建连接池 + 暴露两个配置项"。改动集中在 `HTTPClient` 私有构造函数：通过 HC5 的 `PoolingHttpClientConnectionManagerBuilder` 创建一个 `HttpClientConnectionManager`，调用 `useSystemProperties()` 让其兼容 HC5 标准系统属性，再用 `setMaxConnTotal(...)` / `setMaxConnPerRoute(...)` 把两个上限设为从配置读取的值，最后通过 `clientBuilder.setConnectionManager(connectionManager)` 注入到 `HttpClients.custom()` 构建的客户端中。新增了两个常量字符串作为配置键，以及两个对应的默认值常量。整体改动是纯新增 18 行，没有任何既有代码被修改或删除。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java`

**修改目的**：为 REST Catalog 客户端显式配置连接池，并提供 `rest.client.max-connections`、`rest.client.connections-per-route` 两个可调参数，解决默认连接池在高并发下成为瓶颈的问题。

**工作逻辑**：

改动分两部分。

1. 新增配置键与默认值常量（紧挨既有的 `REST_MAX_RETRIES` 之后）：

```java
private static final String REST_MAX_RETRIES = "rest.client.max-retries";
private static final String REST_MAX_CONNECTIONS = "rest.client.max-connections";
private static final int REST_MAX_CONNECTIONS_DEFAULT = 100;
private static final String REST_MAX_CONNECTIONS_PER_ROUTE = "rest.client.connections-per-route";
private static final int REST_MAX_CONNECTIONS_PER_ROUTE_DEFAULT = 100;
```

两个默认值都设为 100，远高于 HC5 默认的每路由 2，能覆盖绝大多数 REST Catalog 部署。

2. 新增 import 并在私有构造函数中显式构建并注入连接池：

```java
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
...
HttpClientBuilder clientBuilder = HttpClients.custom();

HttpClientConnectionManager connectionManager =
    PoolingHttpClientConnectionManagerBuilder.create()
        .useSystemProperties()
        .setMaxConnTotal(Integer.getInteger(REST_MAX_CONNECTIONS, REST_MAX_CONNECTIONS_DEFAULT))
        .setMaxConnPerRoute(
            PropertyUtil.propertyAsInt(
                properties,
                REST_MAX_CONNECTIONS_PER_ROUTE,
                REST_MAX_CONNECTIONS_PER_ROUTE_DEFAULT))
        .build();
clientBuilder.setConnectionManager(connectionManager);
```

这里有几个值得注意的设计点：

- **`useSystemProperties()`**：让连接管理器遵循 HC5 的标准系统属性（如 `http.maxConnections` 等），保留与 HC5 既有调优手段的兼容，避免本提交引入的配置项与 HC5 自带机制打架。
- **两个参数读取来源不同**：`max-connections` 用 `Integer.getInteger(REST_MAX_CONNECTIONS, default)`，即从 **JVM 系统属性**（`-Drest.client.max-connections=...`）读取；而 `connections-per-route` 用 `PropertyUtil.propertyAsInt(properties, ...)`，从传入构造函数的 **Iceberg properties Map**（即 catalog 配置 `rest.client.connections-per-route=...`）读取。这种不对称是本提交的一个特点：总连接数走 JVM 启动参数、每路由数走 catalog 配置。默认值都是 100，未配置时两者表现一致。
- **显式 `setConnectionManager`**：之前 `HttpClients.custom()` 不带 `setConnectionManager` 调用，HC5 会内部创建一个默认池（per-route=2）。本提交通过显式 `setConnectionManager` 取代该默认池，把上限纳入用户控制。

行为变化总结：之前 REST Catalog 客户端在高并发下因每路由 2 连接而排队；本提交后默认每路由 100、总数 100，并可由用户进一步调大或调小。已有调用方（`RESTCatalog`、`RESTSessionCatalog` 等通过 `HTTPClient.builder()` 创建客户端的代码）无需任何修改即可受益——配置项通过构造时传入的 `properties` Map 传递，未设置则取默认值。

## 小结

本提交为 REST Catalog 客户端显式注入 `PoolingHttpClientConnectionManager` 并新增 `rest.client.max-connections`、`rest.client.connections-per-route` 两个可配置项，把默认每路由 2 连接提升到 100，解决 REST Catalog 在并发场景下的连接池瓶颈，使客户端吞吐可由用户根据部署规模调优。
