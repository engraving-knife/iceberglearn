# 提交分析：Parquet: Deprecate readSupport and callInit in ReadBuilder

## 提交信息

- 哈希: 7dd01a367b220843773c730363d82091fb42d9e2
- 短哈希: 7dd01a367
- 日期: 2024-01-16 18:02:05 +0100
- 作者: Ryan Blue
- 说明: Parquet: Deprecate readSupport and callInit in ReadBuilder (#9325)

## 总体目的

本次提交为 Iceberg Parquet 模块 `Parquet.ReadBuilder` 中的两个旧式 API——`readSupport(ReadSupport<?>)` 与 `callInit()`——添加 `@Deprecated` 注解和 Javadoc 弃用说明，明确告知调用方这两个方法将在 Iceberg 2.0.0 中被移除，请改用 `createReaderFunc(Function)`。这是 Iceberg 长期 API 演进中的一次"软弃用"步骤：先通过注解和文档让上游模块（包括各引擎集成、Spark/Flink/Trino 等）感知到迁移信号，再在主版本升级时统一移除。

弃用的根因在于 `ReadSupport` 是 Parquet 旧版读取模型（基于 `RecordMaterializer`/`ReadSupport` 的初始化回调）的产物，要求调用方提供完整的 `ReadSupport` 实现并显式触发 `init()` 回调，这与 Iceberg 内部以 `Function<Schema, DatumReader<?>>` 形式表达读取逻辑的新模型（`createReaderFunc`）冗余。新模型更简洁、可组合，且能更好地与 Iceberg Schema 投影、类型转换层集成；保留旧 API 会让两条路径并存，增加维护成本并容易产生初始化顺序相关的 bug。通过本次标注，社区正式将 `createReaderFunc` 确立为唯一推荐的读取入口，旧路径进入"弃用倒计时"。

## 如何达成设计目的

实现非常克制：仅在 `Parquet.java` 中两个目标方法上方添加两行——`/** @deprecated will be removed in 2.0.0; use {@link #createReaderFunc(Function)} instead */` 与 `@Deprecated` 注解。方法体未改动，因此本次提交不会影响任何现有调用方的运行时行为，但 IDE 与编译器会在编译时输出 deprecation 警告，从而引导调用方主动迁移。这种"先弃用、后移除"的两步走策略是 Iceberg 维护公共 API 兼容性的标准做法，给外部集成方留出了完整的版本周期来完成迁移。

## 修改详情

### parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java

**修改目的**：将 `ReadBuilder` 中两个旧式读取 API 标记为弃用，引导调用方迁移到 `createReaderFunc`。

**工作逻辑**：
- 在 `public ReadBuilder readSupport(ReadSupport<?> newFilterSupport)` 方法前添加 Javadoc `@deprecated` 标签（指明 2.0.0 移除，建议使用 `createReaderFunc(Function)`）与 `@Deprecated` 注解。该方法原本用于将一个 Parquet `ReadSupport` 实例注入 builder，由 builder 在构建读取器时调用其 `init` 与 `recordMaterializer` 工厂。
- 在 `public ReadBuilder callInit()` 方法前同样添加 Javadoc 与 `@Deprecated` 注解。该方法原本用于显式开启对 `ReadSupport.init(Schema)` 回调的调用，是旧读取模型特有的初始化开关。
- 两个方法的内部实现（`this.readSupport = newFilterSupport;` 与 `this.callInit = true;`）保持不变，确保兼容性。
- 通过 `@link` 指向 `createReaderFunc(Function)`，让 IDE 跟踪跳转可以直接看到推荐替代方案的签名。

## 小结

本次提交对 `Parquet.ReadBuilder` 的 `readSupport(ReadSupport<?>)` 与 `callInit()` 方法添加 `@Deprecated` 注解和 Javadoc 弃用说明，明确这两个旧式 Parquet 读取 API 将在 Iceberg 2.0.0 移除、推荐改用 `createReaderFunc(Function)`。改动只涉及两处注解，运行时行为不变，是 Iceberg 公共 API 演进中的标准"软弃用"步骤，目的是引导上游引擎集成方在主版本到来前完成迁移。
