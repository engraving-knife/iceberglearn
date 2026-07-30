# 提交 1571：Core,Rest: Read the max connection from properties (#11522)

## 提交信息

- **序号**：1571 / 4088
- **哈希**：f7d40f07131894719c7ad9f99fda7c6cb7aec932
- **短哈希**：f7d40f071
- **日期**：2025-01-13（Mon Jan 13 12:06:09 2025 +0530）
- **作者**：S N Munendra <9696252+munendrasn@users.noreply.github.com>
- **提交说明**：Core,Rest: Read the max connection from properties (#11522)
- **PR/Issue**：#11522
- **共同作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>

## 总体目的

Iceberg 的 REST catalog 客户端 `HTTPClient` 在构建底层 HTTP 连接池时，允许配置两个关键参数：最大连接总数（`max-connections`）和每路由最大连接数（`connections-per-route`）。这两个参数直接影响 REST 客户端与 REST 服务端之间的并发吞吐能力。

在本次修复之前，这两个参数的配置来源不一致：
- `setMaxConnPerRoute`（每路由最大连接数）通过 `PropertyUtil.propertyAsInt(properties, ...)` 从 Iceberg properties map 中读取——这是用户配置 Iceberg 的标准方式（通过 catalog 属性、表属性等传入）。
- `setMaxConnTotal`（最大连接总数）却通过 `Integer.getInteger(REST_MAX_CONNECTIONS, ...)` 从 **JVM 系统属性**（`-Drest.client.max-connections=...`）读取，而不是从 Iceberg properties 读取。

这种不一致导致一个问题：用户按照 Iceberg 的常规配置方式，在 catalog 属性中设置 `rest.client.max-connections=200` 会被静默忽略（因为代码只读 JVM 系统属性，不读 properties map）。用户必须改用 `-Drest.client.max-connections=200` 这种 JVM 启动参数才能生效，这与 Iceberg 其他所有 REST 客户端配置（如 `rest.client.max-retries`、`rest.client.connection-timeout-ms`、`rest.client.socket-timeout-ms`、`rest.client.connections-per-route` 等）都从 properties 读取的惯例不符，容易造成配置困惑。

本提交修复这个不一致：让 `setMaxConnTotal` 也优先从 Iceberg properties 读取，同时保留 JVM 系统属性作为向后兼容的 fallback（已使用 `-D` 参数的用户不会被打断）。优先级为：JVM 系统属性 > Iceberg properties > 默认值（100）。

## 如何达成设计目的

### 设计思路

采用"嵌套 fallback"策略，利用 `Integer.getInteger(key, default)` 的语义：如果 JVM 系统属性存在则用它，否则用传入的 default 值。把 default 值设为 `PropertyUtil.propertyAsInt(properties, key, defaultConstant)` 的结果——即先从 Iceberg properties 查，查不到再用常量默认值。这样实现了三级优先级：
1. JVM 系统属性 `-Drest.client.max-connections=N`（最高优先级，向后兼容）
2. Iceberg properties `rest.client.max-connections=N`（标准配置方式，本次新增支持）
3. 默认值 100

同时把相关常量从 `private` 改为包级可见（`static`，无 `private`），让测试可以直接引用常量名断言，避免硬编码魔法数字。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java`
**修改目的**：让 `max-connections` 支持 from Iceberg properties 读取，同时保留 JVM 系统属性向后兼容。

**工作逻辑**：
1. 常量可见性调整：把以下 4 个常量从 `private static final` 改为 `static final`（包级可见），以便测试引用：
   ```java
   static final String REST_MAX_CONNECTIONS = "rest.client.max-connections";
   static final int REST_MAX_CONNECTIONS_DEFAULT = 100;
   static final String REST_MAX_CONNECTIONS_PER_ROUTE = "rest.client.connections-per-route";
   static final int REST_MAX_CONNECTIONS_PER_ROUTE_DEFAULT = 100;
   ```
2. `configureConnectionManager` 方法中 `setMaxConnTotal` 调用修改：
   ```java
   // 修改前：
   .setMaxConnTotal(Integer.getInteger(REST_MAX_CONNECTIONS, REST_MAX_CONNECTIONS_DEFAULT))
   
   // 修改后：
   .setMaxConnTotal(
       Integer.getInteger(
           REST_MAX_CONNECTIONS,
           PropertyUtil.propertyAsInt(
               properties, REST_MAX_CONNECTIONS, REST_MAX_CONNECTIONS_DEFAULT)))
   ```
   `Integer.getInteger(key, default)` 的语义：若名为 `key` 的 JVM 系统属性存在，返回其整数值；否则返回 `default`。这里把 `default` 设为 `PropertyUtil.propertyAsInt(properties, REST_MAX_CONNECTIONS, REST_MAX_CONNECTIONS_DEFAULT)` 的结果，即：
   - 若 JVM 系统属性 `rest.client.max-connections` 存在 → 用它（向后兼容）
   - 否则若 Iceberg properties 中 `rest.client.max-connections` 存在 → 用它（本次新增）
   - 否则 → 用默认值 100
3. `setMaxConnPerRoute` 保持不变（已经从 properties 读取）。

#### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java`
**修改目的**：新增测试验证从 properties 和默认值读取 max connection 配置的正确性。

**工作逻辑**：
1. 新增 import：`PoolingHttpClientConnectionManager` 和 `HttpClientConnectionManager`，用于断言连接池类型和读取配置后的实际值。
2. 新增 `testMaxConnectionSettingsFromProperties` 测试：
   - 构造 properties，设置 `rest.client.max-connections=10`、`rest.client.connections-per-route=5`。
   - 调用 `HTTPClient.configureConnectionManager(properties)` 获取连接管理器。
   - 断言它是 `PoolingHttpClientConnectionManager` 实例。
   - 断言 `getMaxTotal() == 10`、`getDefaultMaxPerRoute() == 5`。
   - 这验证了从 Iceberg properties 读取配置能正确作用到底层连接池。
3. 新增 `testMaxConnectionSettingsFromDefaults` 测试：
   - 构造空 properties。
   - 调用 `HTTPClient.configureConnectionManager(properties)`。
   - 断言 `getMaxTotal() == REST_MAX_CONNECTIONS_DEFAULT`（100）、`getDefaultMaxPerRoute() == REST_MAX_CONNECTIONS_PER_ROUTE_DEFAULT`（100）。
   - 这验证了不配置时使用默认值。
4. 两个测试都直接调用 `configureConnectionManager`（包级静态方法）而非构建完整 `HTTPClient`，测试轻量且聚焦于连接池配置逻辑。

## 小结

- **成效**：修复了 REST 客户端 `max-connections` 配置只能通过 JVM 系统属性设置、无法通过 Iceberg properties 设置的不一致问题。修复后用户可以通过标准的 catalog/table properties 方式配置 `rest.client.max-connections`，与 `connections-per-route`、`max-retries`、`connection-timeout-ms` 等其他 REST 客户端配置保持一致的配置方式。同时保留 JVM 系统属性作为向后兼容的更高优先级入口，已有用户配置不受影响。
- **影响范围**：仅 `core` 模块的 `HTTPClient.java`（1 处方法调用修改 + 4 个常量可见性调整）和 `TestHTTPClient.java`（2 个新测试）。`HTTPClient` 是 REST catalog 客户端的核心，但本次改动只影响连接池配置的读取来源，不改变 HTTP 请求逻辑。
- **回迁到 1.4.x 的注意事项**：这是一个配置一致性 bug 修复，1.4.x 用户若尝试通过 properties 配置 `rest.client.max-connections` 会发现不生效（被静默忽略）。**建议回迁**。改动极小（1 行核心逻辑 + 常量可见性），与 1.4.x 完全兼容，无 API 破坏风险。测试用例建议一并回迁以验证修复。回迁时注意确认 1.4.x 的 `HTTPClient.configureConnectionManager` 方法签名与本次修改一致（方法已存在，只是修改内部一行）。
