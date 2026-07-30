# 提交 0382：Core: Close the MetricsReporter when Catalog is closed

## 提交信息

- **序号**：0382
- **哈希**：2446cee5cf0ad93a2be9a68f0b2f7f6fa6edb865
- **短哈希**：2446cee5c
- **日期**：Thu Jan 18 15:31:40 2024 +0800
- **作者**：big face cat <731030576@qq.com>
- **提交说明**：Core: Close the MetricsReporter when Catalog is closed (#9353)
- **PR/Issue**：#9353

## 总体目的

这个提交解决的是 Iceberg Catalog 生命周期管理中的一个资源泄漏缺口。在 1.4.x 之前，`MetricsReporter` 是一个无状态的 `@FunctionalInterface`，只承担"上报指标"的职责（`report(MetricsReport)`），并不实现 `Closeable`，因此 Catalog 在关闭时不会显式释放它持有的资源。

问题在于：用户可以通过 `CatalogProperties.METRICS_REPORTER_IMPL` 配置自定义的 `MetricsReporter` 实现，这些实现完全可能持有连接池、HTTP 客户端、文件句柄、后台线程或缓冲区等需要显式释放的资源（例如把指标推送到 Prometheus、StatsD、自定义监控后端）。当 Catalog 通过 `close()` 被关闭时，原来只关闭了底层的 FileIO、连接池、客户端等"看得见"的 Closeable，却没人通知 `MetricsReporter` 释放自己持有的资源，从而造成泄漏——尤其在 Spark/引擎频繁创建短生命周期 Catalog 的场景下，泄漏会随任务数累积。

提交的设计意图是建立一条"自上而下、统一受控"的关闭链：让 `MetricsReporter` 接口本身实现 `Closeable`，并让 `BaseMetastoreCatalog` 在 `close()` 时主动关闭它；同时让各个具体 Catalog（DynamoDB、Glue、Hadoop、JDBC、InMemory、ECS、Nessie、Snowflake）将 reporter 注册到自己已有的 `CloseableGroup` 中，从而享受统一的失败抑制（`setSuppressCloseFailure(true)`）与批量关闭语义。这样无论用户实现多么复杂，只要实现 `close()`，资源就能被可靠释放。

从架构影响看，这是一个"接口扩展 + 默认实现 + 子类适配"的典型演进：通过给接口添加 `default void close() {}` 保持向后兼容（既有实现不受影响），同时为愿意显式释放资源的实现打开通道。这也意味着从 1.4.0 起，自定义 `MetricsReporter` 实现者多了一个推荐的生命周期钩子。

## 如何达成设计目的

核心思路分三步：1）把 `MetricsReporter` 接口改为继承 `java.io.Closeable`，并提供一个空 `default close()` 兼容旧实现；2）在 `BaseMetastoreCatalog` 中实现 `Closeable`，并在 `close()` 中显式关闭 `metricsReporter`，同时把 `metricsReporter()` 的访问权限从 `private` 提升到 `protected`，以便各子类在初始化时把 reporter 注册到自己的 `CloseableGroup`；3）逐个改造具体 Catalog——多数子类移除冗余的 `implements Closeable`（因为基类已经实现），并在 `initialize()` 时把 `metricsReporter()` 加入 `closeableGroup`，从而把"reporter 关闭"纳入到已有的"统一关闭"机制中。

对于没有 `CloseableGroup` 的 `JdbcCatalog` 和 `InMemoryCatalog`，则新建并维护一个 `CloseableGroup`，把 reporter（以及 JDBC 的 `connections`）统一纳入管理，避免分散的关闭逻辑。

## 修改详情

### api/src/main/java/org/apache/iceberg/metrics/MetricsReporter.java

**修改目的**：让 reporter 接口具备可关闭语义，并保持向后兼容。

**工作逻辑**：导入 `java.io.Closeable` 后将 `public interface MetricsReporter` 改为 `public interface MetricsReporter extends Closeable`。由于接口原本只声明了 `report(MetricsReport)`，扩展后会强制要求实现 `close()`——为了不破坏现有的 `@FunctionalInterface` 兼容性与已有实现，新增 `@Override default void close() {}`，作为"什么都不做"的默认实现。这样：旧实现照常工作；新实现可以覆盖 `close()` 来释放自己的资源；`@FunctionalInterface` 仍然成立，因为 `Closeable.close()` 已被默认实现兜底，抽象方法仍只有 `report` 一个。

### core/src/main/java/org/apache/iceberg/BaseMetastoreCatalog.java

**修改目的**：在所有元存储类 Catalog 的公共基类中统一承担关闭 reporter 的职责，并暴露 `metricsReporter()` 给子类使用。

**工作逻辑**：导入 `java.io.Closeable` 与 `java.io.IOException` 后，类签名从 `implements Catalog` 改为 `implements Catalog, Closeable`，统一为所有子类提供 `Closeable` 能力。`metricsReporter()` 由 `private` 提升为 `protected`，使子类在 `initialize()` 阶段可以拿到 reporter 实例并注册到自己的 `CloseableGroup`。新增 `close()` 方法：`if (metricsReporter != null) metricsReporter.close();`，保证即使子类自己没有 `CloseableGroup`，基类也会兜底关闭 reporter。这种"基类兜底 + 子类按需托管"的双层关闭设计，确保了不论子类是否记得加入 `CloseableGroup`，reporter 都会被关闭。

### aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbCatalog.java

**修改目的**：把 reporter 纳入 DynamoDbCatalog 的关闭链，并清理冗余的 `Closeable` 声明。

**工作逻辑**：移除 `import java.io.Closeable;`，类签名从 `implements Closeable, SupportsNamespaces, Configurable` 改为 `implements SupportsNamespaces, Configurable`（`Closeable` 现在由 `BaseMetastoreCatalog` 间接提供）。在 `initialize()` 中原有 `closeableGroup.addCloseable(dynamo); closeableGroup.addCloseable(fileIO);` 之后追加 `closeableGroup.addCloseable(metricsReporter());`，使 reporter 与 DynamoDB 客户端、FileIO 一起被 `CloseableGroup` 统一关闭，并受益于 `setSuppressCloseFailure(true)` 的失败抑制。

### aws/src/main/java/org/apache/iceberg/aws/glue/GlueCatalog.java

**修改目的**：与 DynamoDbCatalog 同理，把 reporter 纳入 GlueCatalog 的 `CloseableGroup`。

**工作逻辑**：移除 `import java.io.Closeable;`，类签名去掉 `Closeable`；在 `initialize()` 中追加 `closeableGroup.addCloseable(metricsReporter());`，与 `glue` 客户端、`lockManager` 一起被统一关闭。

### core/src/main/java/org/apache/iceberg/hadoop/HadoopCatalog.java

**修改目的**：把 reporter 纳入 HadoopCatalog 的关闭链，并清理冗余声明。

**工作逻辑**：移除 `import java.io.Closeable;`，类签名去掉 `Closeable`；在 `initialize()` 中追加 `closeableGroup.addCloseable(metricsReporter());`，与 `lockManager` 一起被统一关闭。

### core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java

**修改目的**：InMemoryCatalog 原本没有 `CloseableGroup`，需要新建一套统一的关闭机制来管理 reporter。

**工作逻辑**：导入 `org.apache.iceberg.io.CloseableGroup` 并新增私有字段 `private CloseableGroup closeableGroup;`。在 `initialize()` 末尾构造 `CloseableGroup`，调用 `closeableGroup.addCloseable(metricsReporter());` 和 `closeableGroup.setSuppressCloseFailure(true);`。在已有的 `close()` 方法中先调用 `closeableGroup.close();` 再清空 `namespaces/tables/views` 等内存结构。这样既复用了 reporter 统一关闭机制，又保持了对内存数据的清理顺序。

### core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java

**修改目的**：JdbcCatalog 也没有 `CloseableGroup`，且原 `close()` 直接调用 `connections.close()`，需要重构为统一关闭并纳入 reporter。

**工作逻辑**：把 `import java.io.Closeable;` 替换为 `import java.io.IOException;` 与 `import java.io.UncheckedIOException;`，类签名去掉 `Closeable`。新增 `private CloseableGroup closeableGroup;` 字段，并在 `initialize()` 末尾构造 `CloseableGroup`，依次 `addCloseable(metricsReporter())` 和 `addCloseable(connections)`，再 `setSuppressCloseFailure(true)`。`close()` 方法从直接 `connections.close()` 改为：`if (closeableGroup != null) { try { closeableGroup.close(); } catch (IOException e) { throw new UncheckedIOException(e); } }`。这里把检查异常包装为 `UncheckedIOException` 是因为原 `close()` 签名没有声明 `throws`，需要保持 API 兼容；同时通过 `closeableGroup != null` 防御 `initialize()` 未完成时调用 `close()` 的边界情况。

### dell/src/main/java/org/apache/iceberg/dell/ecs/EcsCatalog.java

**修改目的**：把 reporter 纳入 EcsCatalog 的关闭链。

**工作逻辑**：移除 `import java.io.Closeable;`，类签名去掉 `Closeable`；在 `initialize()` 中追加 `closeableGroup.addCloseable(metricsReporter());`，与 `client::destroy`、`fileIO` 一起被统一关闭。

### nessie/src/main/java/org/apache/iceberg/nessie/NessieCatalog.java

**修改目的**：把 reporter 纳入 NessieCatalog 的关闭链。

**工作逻辑**：类签名从 `implements AutoCloseable, SupportsNamespaces, Configurable<Object>` 改为 `implements SupportsNamespaces, Configurable<Object>`（`AutoCloseable` 由 `BaseMetastoreViewCatalog`→`BaseMetastoreCatalog` 间接提供的 `Closeable` 兜底，因为 `Closeable extends AutoCloseable`）。在 `initialize()` 中追加 `closeableGroup.addCloseable(metricsReporter());`，与 `client`、`fileIO` 一起被统一关闭。

### snowflake/src/main/java/org/apache/iceberg/snowflake/SnowflakeCatalog.java

**修改目的**：把 reporter 纳入 SnowflakeCatalog 的关闭链。

**工作逻辑**：移除 `import java.io.Closeable;`，类签名去掉 `Closeable`；在 `initialize()` 中追加 `closeableGroup.addCloseable(metricsReporter());`，与 `snowflakeClient` 一起被统一关闭。

## 小结

这个提交是 1.4.x 分支一个有架构意义的资源生命周期治理改进，模式可以概括为"接口扩展（带默认实现）+ 基类兜底关闭 + 子类按需托管到 `CloseableGroup`"。它把以前被忽视的 `MetricsReporter` 资源纳入统一关闭链，对自定义 reporter 实现尤其重要；同时通过 `default close() {}` 与基类 `Closeable` 实现，保持了向后二进制兼容。涉及面较广（10 个文件、覆盖 AWS/Dell/Nessie/Snowflake/Hadoop/JDBC/InMemory 等多个 Catalog 后端），但每个子类的改动手法高度一致、风险低，是一种典型的"在已有 `CloseableGroup` 机制上扩展新成员"的演进。
