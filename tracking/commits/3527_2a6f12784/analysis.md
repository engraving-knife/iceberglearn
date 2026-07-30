# 提交 3527：Core: Expose MetricsConfig.from method with 3-parameter version (#15819)

## 提交信息

- **序号**：3527 / 4088
- **哈希**：2a6f127842a8f21e8e16efed45fdd5d538af29ae
- **短哈希**：2a6f12784
- **日期**：2026-04-13 15:11:36 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Core: Expose MetricsConfig.from method with 3-parameter version (#15819)
- **PR/Issue**：#15819

## 总体目的

`MetricsConfig` 类中有一个三参数的 `from(Map<String, String> props, Schema schema, SortOrder order)` 方法，此前是 `private` 的，仅限类内部使用。这个方法用于根据属性配置、表 schema 和排序顺序生成 `MetricsConfig`，包含了完整的列级 metrics 模式推断逻辑（包括对 sort order 列的 truncate(16) 处理）。

将该方法的可见性从 `private` 提升为 `public`，目的是让外部模块（例如第三方集成、其他 catalog 实现或工具）能够复用这套完整的 metrics 配置推断逻辑，而不必重新实现或依赖受限的入口。这是为下游消费者开放 API 的小型改进。

## 如何达成设计目的

仅修改方法签名的访问修饰符，从 `private` 改为 `public`。方法签名、参数、实现逻辑、Javadoc 都不变。Javadoc 此前已存在（描述了 props、schema、order 参数和返回值），说明这个方法本来就是为对外使用准备的文档化方法，只是可见性没跟上。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetricsConfig.java` (+1/-1 lines)

**修改目的**：将三参数 `from` 方法的可见性从 `private` 改为 `public`。

**工作逻辑**：
```java
-  private static MetricsConfig from(Map<String, String> props, Schema schema, SortOrder order) {
+  public static MetricsConfig from(Map<String, String> props, Schema schema, SortOrder order) {
```
方法体完全不变。该方法会调用 `maxInferredDefaultColumns(props)` 推断最大默认列数，再为每列计算 `MetricsMode`，对 sort order 列默认应用 `truncate(16)`，最终返回不可变的 `MetricsConfig` 实例。

## 总结

本提交将 `MetricsConfig.from(props, schema, order)` 三参数重载的可见性从 `private` 改为 `public`，开放完整的 metrics 配置推断逻辑供外部模块复用。改动极小（一行），属于 API 可见性调整，不影响现有行为，但扩展了可被外部调用的入口。
