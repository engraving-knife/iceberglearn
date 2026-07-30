# 提交序号 1504 短哈希 ce7a4b42f 分析

## 提交信息
- 哈希：ce7a4b42f466ffc272f552f1a63b755f20a8dcf7
- 日期：2024-12-17
- 作者：Fokko Driesprong <fokko@apache.org>
- 消息：API: Add missing deprecations (#11734)

## 总体目的

本提交为 Iceberg API 模块中 `transforms` 包下多个变换（Transform）类补充了此前缺失的 `@Deprecated` 注解和相应的 Javadoc 说明。这些类中的 `apply()` 方法（以及少数其它方法）属于旧的转换 API，Iceberg 计划在 2.0.0 版本中将其移除，推荐用户改用 `bind(Type)` 返回的 `BoundTransform` 来执行变换。

在修改之前，这些方法虽然在设计上已经过时，但由于缺少 `@Deprecated` 注解，IDE 和编译器不会向调用方发出弃用警告，外部用户也无从知晓这些方法即将被移除。这违背了 API 演进的可见性原则：当一个 API 即将被废弃时，应当通过明确的注解和文档告知调用方迁移路径。本提交正是补齐这一缺失，使弃用意图在 API 层面正式可见。

本次涉及的变换类包括 `Bucket`、`Dates`、`Identity`、`Timestamps`、`UnknownTransform` 和 `VoidTransform`，它们都实现了 `Transform` 接口。统一为这些类的过时方法添加注解和文档，可以让整个 transforms 包的弃用策略保持一致。

## 如何达成设计目的

本提交通过在 6 个 Transform 实现类中为指定的过时方法添加 `@Deprecated` 注解和 Javadoc 来达成目的。每个方法的 Javadoc 都说明了方法作用、参数、返回值，并标注 `@deprecated will be removed in 2.0.0; use {@link #bind(Type)} instead`，明确指出移除版本和推荐替代方案。

### 修改详情

#### api/src/main/java/org/apache/iceberg/transforms/Bucket.java

`Bucket` 是分桶变换。本次修改为两处添加弃用标注：

1. 静态方法 `get(Type type, int numBuckets)`：这是旧的基于类型创建分桶变换的工厂方法，标注 `@Deprecated`，Javadoc 指出"will be removed in 2.0.0; use {@link #get(int)} instead"，推荐改用只接受桶数的 `get(int)` 重载。这反映了 Iceberg 正在弱化"按类型推断"的旧路径，转而使用更简洁的、不依赖类型的工厂方法。

2. `apply(T value)` 方法：这是直接对值进行分桶变换的旧方法，标注 `@Deprecated`，推荐改用 `bind(Type)` 返回的 `BoundTransform`。这是因为新 API 希望先绑定类型再执行变换，以获得类型安全和更好的实现路径。

#### api/src/main/java/org/apache/iceberg/transforms/Dates.java

`Dates` 是日期变换（如按天/月/年分区）。为 `apply(Integer days)` 方法添加 `@Deprecated` 注解和 Javadoc，推荐改用 `bind(Type)`。同样遵循"先绑定类型再变换"的新模型。

#### api/src/main/java/org/apache/iceberg/transforms/Identity.java

`Identity` 是恒等变换（直接使用原值作为分区值）。为 `apply(T value)` 方法添加 `@Deprecated` 注解和 Javadoc，推荐改用 `bind(Type)`。

#### api/src/main/java/org/apache/iceberg/transforms/Timestamps.java

`Timestamps` 是时间戳变换（如按小时/天/月分区）。为 `apply(Long timestamp)` 方法添加 `@Deprecated` 注解和 Javadoc，推荐改用 `bind(Type)`。

#### api/src/main/java/org/apache/iceberg/transforms/UnknownTransform.java

`UnknownTransform` 表示无法识别的变换（其 `apply` 会抛出 `UnsupportedOperationException`）。为 `apply(S value)` 方法添加 `@Deprecated` 注解和 Javadoc，Javadoc 中额外通过 `@throws UnsupportedOperationException Implementation is unknown` 说明该方法会抛异常，并推荐改用 `bind(Type)`。

#### api/src/main/java/org/apache/iceberg/transforms/VoidTransform.java

`VoidTransform` 是空变换（分区值为 void）。本次为两处添加弃用标注：

1. `apply(Object value)` 方法：标注 `@Deprecated`，Javadoc 说明返回 null，推荐改用 `bind(Type)`。

2. `toHumanString(Void value)` 方法：这是旧的可读字符串表示方法，标注 `@Deprecated`，推荐改用 `toHumanString(Type, Object)`，即带类型信息的版本。Javadoc 还补充说明"null values will return 'null'"。

## 小结

本提交是一个 API 可维护性改进：通过为 transforms 包中 6 个类的过时方法补齐 `@Deprecated` 注解和迁移说明，正式向调用方宣告这些方法将在 2.0.0 移除，并指明推荐替代方案（`bind(Type)` 或对应的新重载）。这统一了 transforms 包的弃用策略，使 IDE 能在编译期向用户发出警告，帮助外部用户提前迁移，降低未来 2.0.0 升级时的破坏性影响。本次仅添加注解与文档，未改变任何运行时行为，是一个安全的、向前兼容的 API 演进准备。
