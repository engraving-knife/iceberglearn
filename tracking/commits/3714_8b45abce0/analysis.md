# 提交 3714：Close metrics reporter in RESTSessionCatalog and add test in CatalogTests (#16310)

## 提交信息

- **序号**：3714 / 4088
- **哈希**：8b45abce036b480fbecd2427d4ae70fae0536640
- **短哈希**：8b45abce0
- **日期**：2026-05-14 16:57:07 -0700
- **作者**：Talat UYARER
- **提交说明**：Close metrics reporter in RESTSessionCatalog and add test in CatalogTests (#16310)
- **PR/Issue**：#16310

## 总体目的

这个提交修复了 `RESTSessionCatalog` 中 metrics reporter 资源泄漏的问题。`RESTSessionCatalog` 在初始化时会通过 `CatalogUtil.loadMetricsReporter` 加载配置的 metrics reporter，用于报告扫描和提交操作的指标。但此前加载的 reporter 没有被注册到 catalog 的 `closeables` 列表中。

`RESTSessionCatalog` 实现了 `Closeable` 接口，其 `close()` 方法会关闭 `closeables` 列表中注册的所有资源。由于 metrics reporter 未被注册，当 catalog 关闭时 reporter 不会被正确关闭，导致资源泄漏。某些 reporter 实现可能持有后台线程、网络连接或其他需要显式释放的资源。

## 如何达成设计目的

通过在 `RESTSessionCatalog` 初始化 metrics reporter 后，将其添加到 `closeables` 列表中，确保 catalog 关闭时 reporter 也会被关闭。同时在 `CatalogTests` 中添加测试验证此行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+1 line)

**修改目的**：将 metrics reporter 注册到 closeables。

**工作逻辑**：

```java
this.reporter = CatalogUtil.loadMetricsReporter(mergedProps);
+this.closeables.addCloseable(reporter);
```

在加载 metrics reporter 后，立即将其添加到 `closeables` 列表。这样当 `RESTSessionCatalog.close()` 被调用时，会自动关闭 reporter。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (+13 lines)

**修改目的**：添加测试验证 reporter 被正确关闭。

**工作逻辑**：

在 `CustomMetricsReporter` 中新增 `CLOSE_COUNTER` 计数器和 `close()` 方法：

```java
static final AtomicInteger CLOSE_COUNTER = new AtomicInteger(0);

@Override
public void close() {
  CLOSE_COUNTER.incrementAndGet();
}
```

在测试中验证 catalog 关闭后 reporter 的 close 方法被调用：

```java
CustomMetricsReporter.CLOSE_COUNTER.set(0);
((Closeable) catalogWithCustomReporter).close();
assertThat(CustomMetricsReporter.CLOSE_COUNTER.get())
    .as("Catalog.close() must propagate to the configured MetricsReporter")
    .isEqualTo(1);
```

测试断言信息明确说明："Catalog.close() must propagate to the configured MetricsReporter"，即 catalog 的 close 方法必须传播到配置的 MetricsReporter。

## 总结

这是一个资源管理修复提交，确保 `RESTSessionCatalog` 关闭时正确关闭其加载的 metrics reporter，避免资源泄漏。修改简洁——仅需一行代码将 reporter 注册到 closeables 列表，但配套添加了完善的测试验证此行为。这种资源生命周期管理对于长时间运行的应用程序尤为重要。
