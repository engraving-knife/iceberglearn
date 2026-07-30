# 提交 0753：Core: Retry connections in JDBC catalog with user configured error code list (#10140)

## 提交信息

- **序号**：0753 / 4088
- **哈希**：2b21020aedb63c26295005d150c05f0a5a5f0eb2
- **短哈希**：2b21020ae
- **日期**：2024-05-10 11:18:46 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Retry connections in JDBC catalog with user configured error code list (#10140)
- **PR/Issue**：#10140（注：任务描述中标注为 #10165，实际提交信息为 #10140）

## 总体目的

本提交为 Iceberg 的 JDBC catalog 增加可配置的连接重试能力，解决 JDBC 后端数据库出现瞬时连接故障或特定可重试错误时操作直接失败的问题。在本次修改之前，`ClientPoolImpl`（客户端连接池基类）的重试逻辑非常有限：当执行操作时捕获到"连接异常"后，仅尝试一次 `reconnect`（重连）并重新执行操作；若重连本身或重执操作再次失败，则直接抛出原始异常，不做进一步重试。这意味着面对数据库重启、网络抖动、瞬时死锁等场景，JDBC catalog 操作会立即失败，需要上层应用自行处理重试。

本次修改从两个层面增强重试能力：

1. **通用的客户端连接池重试机制**：在 `ClientPoolImpl` 中引入 `maxRetries`（最大重试次数）配置，将原来"最多重试一次"的硬编码行为改为可配置的多次重试循环，并在每次重试之间加入等待间隔（`connectionRetryWaitPeriodMs`，默认 1000ms）。

2. **JDBC catalog 可配置的可重试错误码**：在 `JdbcClientPool` 中引入基于 SQLSTATE 错误码的可重试判定。除了原本基于异常类型的判定外，新增对一组跨厂商通用可重试 SQLSTATE（如 `08000` 连接异常、`08006` 连接失败、`40001` 死锁序列化失败等）的识别，并允许用户通过配置属性 `retryable_status_codes` 自定义额外的可重试 SQLSTATE 列表。同时，将原本重试的异常类型从 `SQLNonTransientConnectionException`（非瞬时连接异常）改为 `SQLTransientException`（瞬时异常），使重试判定更符合"瞬时故障才应重试"的语义。

## 如何达成设计目的

### 重试机制的分层设计

整体设计分为两层：底层是通用的 `ClientPoolImpl`，提供"最多重试 N 次、每次重试前等待固定时间"的循环重试框架；上层是 `JdbcClientPool`，通过覆盖 `isConnectionException` 方法来定义"什么样的异常算作可重试的连接异常"，并支持用户配置额外的可重试 SQLSTATE。

**底层 `ClientPoolImpl` 的重试循环**：原 `run` 方法在捕获异常后内联执行一次重连+重执。重构后，`run` 方法在判定异常为可重试连接异常后，委托给新提取的 `retryAction` 方法。`retryAction` 进入一个 `while (retryAttempts < maxRetries)` 循环：每次循环先调用 `reconnect` 获取新客户端，再用新客户端执行原操作；若执行成功则立即返回结果；若抛出异常，则判定该异常是否仍为可重试连接异常——若是，则递增重试计数并 `Thread.sleep(connectionRetryWaitPeriodMs)` 等待后进入下一轮；若不是可重试异常（说明是业务逻辑错误而非连接问题），则抛出原始失败异常。循环耗尽 `maxRetries` 次仍未成功，则抛出原始失败异常。这一设计保证了：重试只针对持续性的连接问题，一旦出现非连接类异常立即终止重试并上报原始错误。

**构造函数向后兼容**：新增带 `maxConnectionRetries` 参数的四参构造函数，原三参构造函数委托给新构造函数并传入默认值 `1`，保持与既有调用方的二进制兼容。

**上层 `JdbcClientPool` 的可重试判定**：覆盖 `isConnectionException(Exception e)` 方法，在调用父类判定的基础上，额外检查异常是否为 `SQLException` 且其 `getSQLState()` 返回值在可重试状态码集合中。可重试状态码集合由两部分组成：一组跨厂商通用的常量集合 `COMMON_RETRYABLE_CONNECTION_SQL_STATES`（`08000`、`08003`、`08006`、`08007`、`40001`），以及用户通过 `retryable_status_codes` 配置项自定义的状态码（逗号分隔，会去除空白字符）。同时，父类构造函数传入的异常类型从 `SQLNonTransientConnectionException.class` 改为 `SQLTransientException.class`，这意味着父类的 `isConnectionException`（基于 `Class.isInstance` 的判定）现在会识别所有 `SQLTransientException` 子类（包括 `SQLTransientConnectionException`、`SQLTimeoutException` 等）作为可重试异常。

### 测试验证

新增 `TestClientPoolImpl` 测试类，通过 `MockClientPoolImpl` 模拟客户端行为，验证四种场景：重试在最大次数内成功、重试次数耗尽后上报失败、非可重试异常不触发重试、关闭重试时不重试。在 `TestJdbcCatalog` 中新增两个测试：验证用户配置的 `retryable_status_codes`（含空格处理）能被正确识别为可重试，以及验证 `SQLNonTransientConnectionException`（如认证失败）不再被判定为可重试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ClientPoolImpl.java`

**修改目的**：将单次重试改为可配置多次重试循环，并提取重试逻辑为独立方法。

**工作逻辑**：

1. 新增字段 `maxRetries`（最大重试次数）与 `connectionRetryWaitPeriodMs`（重试间隔，默认 1000ms，硬编码未对外暴露配置）。

2. 新增四参构造函数 `ClientPoolImpl(int poolSize, Class<? extends E> reconnectExc, boolean retryByDefault, int maxConnectionRetries)`，将 `maxRetries` 初始化为传入的 `maxConnectionRetries`。原三参构造函数委托新构造函数并传 `1`（即保持原"最多重试一次"的默认行为）。

3. `run(Action, boolean)` 方法重构：异常处理逻辑简化为——若 `!retry || !isConnectionException(exc)` 则直接抛出原异常；否则委托 `retryAction(action, exc, client)` 执行重试循环。移除了原来内联的一次性重连+重执逻辑。

4. 新增私有方法 `retryAction(Action<R, C, E> action, Exception originalFailure, C client)`：
   - 进入 `while (retryAttempts < maxRetries)` 循环。
   - 每轮：调用 `reconnect(client)` 获取新客户端，调用 `action.run(reconnectedClient)` 执行操作，成功则返回。
   - 捕获异常：若 `isConnectionException(exc)` 为 true，则 `retryAttempts++` 并 `Thread.sleep(connectionRetryWaitPeriodMs)` 等待后继续；否则抛出 `reconnectExc.cast(originalFailure)`（即原始失败异常，而非当前重试中的异常）。
   - 循环耗尽仍失败，抛出 `reconnectExc.cast(originalFailure)`。
   - 注意：`reconnect` 与 `action.run` 的异常被同一个 catch 块捕获，意味着重连失败若为可重试异常也会继续重试循环。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java`

**修改目的**：暴露 `JdbcClientPool` 供测试访问。

**工作逻辑**：新增包级可见方法 `connectionPool()`（标注 `@VisibleForTesting`），返回 `connections` 字段（即 `JdbcClientPool` 实例）。这使得测试可以直接调用 `isConnectionException` 验证可重试判定逻辑，无需通过 catalog 操作间接触发。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcClientPool.java`

**修改目的**：实现基于 SQLSTATE 的可重试判定，并支持用户自定义可重试状态码。

**工作逻辑**：

1. 新增常量 `COMMON_RETRYABLE_CONNECTION_SQL_STATES`（`ImmutableSet`），包含 5 个跨厂商通用可重试 SQLSTATE：
   - `08000`：通用连接异常
   - `08003`：连接不存在
   - `08006`：连接失败
   - `08007`：事务解决状态未知
   - `40001`：死锁导致的序列化失败
   Javadoc 引用 https://en.wikipedia.org/wiki/wiki/SQLSTATE 说明。

2. 新增字段 `retryableStatusCodes`（`Set<String>`），存储当前实例的可重试状态码集合。

3. 构造函数 `JdbcClientPool(int poolSize, String dbUrl, Map<String, String> props)`：
   - 父类调用从 `super(poolSize, SQLNonTransientConnectionException.class, true)` 改为 `super(poolSize, SQLTransientException.class, true)`。这一改动使父类基于异常类型的 `isConnectionException` 判定范围从"非瞬时连接异常"扩大到"所有瞬时异常"（`SQLTransientException` 是 `SQLTransientConnectionException`、`SQLTimeoutException` 等的父类），符合"瞬时故障才应重试"的语义。
   - 初始化 `retryableStatusCodes`：先装入通用可重试状态码集合，再读取 `props.get(JdbcUtil.RETRYABLE_STATUS_CODES)`；若用户配置了该属性，则按逗号分割、去除空白后加入集合。

4. 覆盖 `isConnectionException(Exception e)`：返回 `super.isConnectionException(e)`（即父类基于 `SQLTransientException` 类型的判定）`||`（异常为 `SQLException` 且其 `getSQLState()` 在 `retryableStatusCodes` 集合中）。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java`

**修改目的**：定义 `retryable_status_codes` 配置属性常量。

**工作逻辑**：新增常量 `RETRYABLE_STATUS_CODES = "retryable_status_codes"`。该属性名无 `PROPERTY_PREFIX` 前缀（与同文件中 `INIT_CATALOG_TABLES_PROPERTY` 不同），用户在 catalog 属性中直接以 `retryable_status_codes` 为 key 配置。

### `core/src/test/java/org/apache/iceberg/TestClientPoolImpl.java`（新增文件）

**修改目的**：验证 `ClientPoolImpl` 的多次重试循环逻辑。

**工作逻辑**：定义 `MockClient`（模拟客户端，`succeedAfter(n)` 在前 n-1 次抛 `RetryableException`、第 n 次成功；`failWithNonRetryable` 抛 `NonRetryableException`）与 `MockClientPoolImpl`（继承 `ClientPoolImpl`，记录 `reconnectionAttempts`）。4 个测试用例：
- `testRetrySucceedsWithinMaxAttempts`：maxRetries=5，第 3 次成功，验证仅 1 次成功 action 调用、2 次重连。
- `testRetriesExhaustedAndSurfacesFailure`：maxRetries=3，第 5 次才成功，验证抛 `RetryableException`、重连 3 次。
- `testNoRetryingNonRetryableException`：抛非可重试异常，验证不重试、重连 0 次。
- `testNoRetryingWhenDisabled`：`retryByDefault=false`，验证不重试。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`

**修改目的**：验证 JDBC catalog 的可重试状态码配置与 `SQLNonTransientConnectionException` 不再可重试。

**工作逻辑**：
- `testRetryingErrorCodesProperty`：配置 `retryable_status_codes=57000,57P03,57P04`，验证这些状态码及通用状态码的 `SQLException` 都被判定为可重试；再测试带空格的配置 `"57000, 57P03, 57P04"` 同样生效。
- `testSqlNonTransientExceptionNotRetryable`：验证 `SQLNonTransientConnectionException`（如认证失败）被判定为不可重试，确认异常类型从 `SQLNonTransientConnectionException` 改为 `SQLTransientException` 后的行为变化是预期的。

## 小结

- **成效**：JDBC catalog 现在能在面对瞬时连接故障、死锁等可重试 SQLSTATE 时自动多次重试，显著提升了在数据库抖动场景下的操作健壮性。用户可通过 `retryable_status_codes` 配置针对特定数据库厂商的自定义可重试状态码，灵活适配不同后端。同时，将重试异常类型从非瞬时改为瞬时异常，避免了对认证失败等永久性错误进行无意义重试。
- **影响范围**：影响所有使用 JDBC catalog 的用户。`ClientPoolImpl` 是基类，其重试逻辑变更也会影响其他继承它的客户端池实现（如有）。默认行为变化：原基于 `SQLNonTransientConnectionException` 的判定改为基于 `SQLTransientException`，这可能改变某些边界场景下的重试行为。`maxRetries` 默认值为 1（通过三参构造函数路径），与原行为一致；但 JDBC catalog 走 `JdbcClientPool` 构造路径，其调用的是三参 `super`，因此默认仍是 1 次重试。
- **回迁注意事项**：
  1. 这是功能性增强，回迁到 1.4.x 分支时需确认 1.4.x 的 `ClientPoolImpl`、`JdbcClientPool`、`JdbcCatalog`、`JdbcUtil` 基础结构与此提交前的状态一致，否则可能产生合并冲突。
  2. `TestClientPoolImpl` 是新增测试文件，需确保 1.4.x 分支的测试依赖（AssertJ）版本支持所用断言 API。
  3. `JdbcCatalog.connectionPool()` 是新增的包级测试辅助方法，回迁时需连同 `@VisibleForTesting` 注解一起带入，并确认 `VisibleForTesting` 的 import 路径（`org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting`）在 1.4.x 中存在。
  4. 默认异常类型从 `SQLNonTransientConnectionException` 改为 `SQLTransientException` 是行为变更，回迁后需评估是否有用户依赖原行为（即期望对非瞬时连接异常重试）。如有，需在回迁说明中提示。
  5. `connectionRetryWaitPeriodMs` 硬编码为 1000ms 且未对外暴露配置，若 1.4.x 用户需要调整等待间隔，需额外开发配置项。
