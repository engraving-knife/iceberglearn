# 提交 2171：Core, AWS, Spark: use loopback address explicitly in tests to work in more restrictive firewall env on dev machines (#13101)

## 提交信息

- **序号**：2171 / 4088
- **哈希**：52a72dea81c2cbc9be2215ea25d83f59c25dfa76
- **短哈希**：52a72dea8
- **日期**：2025-05-27 11:33:01 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Core, AWS, Spark: use loopback address explicitly in tests to work in more restrictive firewall env on dev machines (#13101)
- **PR/Issue**：#13101

## 总体目的

开发者在限制性防火墙环境下运行测试时会遇到问题。当测试中启动 Jetty HTTP 服务器或 Spark 本地会话时，默认绑定到 `0.0.0.0`（所有网络接口）或使用机器的主机名，这会被严格的防火墙规则拦截。此提交通过显式使用回环地址（loopback address，即 127.0.0.1）来绑定测试服务器和配置 Spark driver host，使测试能在更严格的网络环境下正常运行。这是一个提升开发者体验的改进，确保测试在不同开发环境中的可移植性。

## 如何达成设计目的

- 对于 Jetty 服务器：将 `new Server(0)`（绑定所有接口）改为 `new Server(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))`（仅绑定回环地址）
- 对于 Spark 会话：添加 `spark.driver.host` 配置项，设置为回环地址的主机名
- 修改覆盖了 Core、AWS 和 Spark 三个模块的测试代码（v3.4、v3.5、v4.0 三个 Spark 版本）

## 修改详情

### `aws/src/integration/java/org/apache/iceberg/aws/s3/signer/TestS3RestSigner.java` (修改, +3/-1 lines)

**修改目的**：使 S3 REST 签名器的集成测试服务器绑定到回环地址。

**工作逻辑**：在创建 Jetty Server 时，从 `new Server(0)` 改为使用 `InetSocketAddress(InetAddress.getLoopbackAddress(), 0)`，端口号仍为 0（系统自动分配），但只监听回环接口。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (修改, +3/-1 lines)

**修改目的**：使 REST Catalog 测试的 HTTP 服务器绑定到回环地址。

**工作逻辑**：同上，将 `new Server(0)` 改为绑定回环地址的 `InetSocketAddress`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (修改, +3/-1 lines)

**修改目的**：使 REST View Catalog 测试的 HTTP 服务器绑定到回环地址。

**工作逻辑**：同上模式，修改 Jetty Server 的绑定地址。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalogWithAssumedViewSupport.java` (修改, +3/-1 lines)

**修改目的**：使带 Assumed View 支持的 REST View Catalog 测试服务器绑定到回环地址。

**工作逻辑**：同上模式。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestBase.java` (修改, +2/-0 lines)

**修改目的**：为 Spark 3.4 测试配置 driver host 为回环地址。

**工作逻辑**：在 SparkSession.builder() 中添加 `.config("spark.driver.host", InetAddress.getLoopbackAddress().getHostAddress())`，避免 Spark 使用机器主机名导致的防火墙问题。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/ScanTestBase.java` (修改, +6/-1 lines)

**修改目的**：为 Spark 3.4 Scan 测试配置 driver host 为回环地址。

**工作逻辑**：重构 SparkSession 创建代码，添加 `spark.driver.host` 配置。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBase.java` (修改, +2/-0 lines)

**修改目的**：为 Spark 3.5 测试配置 driver host 为回环地址。同 v3.4 的修改。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/ScanTestBase.java` (修改, +6/-1 lines)

**修改目的**：为 Spark 3.5 Scan 测试配置 driver host 为回环地址。同 v3.4 的修改。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/TestBase.java` (修改, +2/-0 lines)

**修改目的**：为 Spark 4.0 测试配置 driver host 为回环地址。同 v3.4 的修改。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/ScanTestBase.java` (修改, +6/-1 lines)

**修改目的**：为 Spark 4.0 Scan 测试配置 driver host 为回环地址。同 v3.4 的修改。

## 总结

此提交通过在所有测试中显式使用回环地址（127.0.0.1）来绑定 HTTP 服务器和配置 Spark driver host，解决了在限制性防火墙环境下测试失败的问题。修改覆盖了 Core、AWS 和 Spark（v3.4/v3.5/v4.0）三个模块共 10 个测试文件，是一个提升开发者体验的实用改进。
