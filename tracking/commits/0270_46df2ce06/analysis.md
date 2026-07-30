# 提交 0270：API: Restore RuntimeIOException for use (#5640)

## 提交信息

- **序号**：0270 / 4088
- **哈希**：46df2ce069dd03bfb81004d663c9000416896426
- **短哈希**：46df2ce06
- **日期**：2023-12-14 09:17:36 +0100
- **作者**：Daniel Weeks <dweeks@apache.org>
- **提交说明**：API: Restore RuntimeIOException for use (#5640)
- **PR/Issue**：#5640

## 总体目的

本提交的目的是恢复 `RuntimeIOException` 这一公开 API 类的可用状态，撤销此前对其施加的 `@Deprecated` 弃用标记。`RuntimeIOException` 是 Iceberg API 模块（`org.apache.iceberg.exceptions`）中的一个异常类，继承自 `java.io.UncheckedIOException`，用于将受检的 `IOException` 包装为不受检的 `RuntimeException` 并附加上下文信息，使调用方免于强制处理受检 IO 异常。

此前该类被标记为 `@Deprecated`，并在 Javadoc 中建议"直接使用 `java.io.UncheckedIOException` 作为替代"。弃用的本意是引导使用者逐步迁移到 JDK 标准的 `UncheckedIOException`，减少 Iceberg 自定义异常类的维护负担，向"纯 JDK 标准"靠拢。然而，这一弃用决定在实际执行中遇到了阻力。`RuntimeIOException` 相比原生 `UncheckedIOException` 提供了额外的能力：它有带 `@FormatMethod` 注解的格式化构造函数（支持 printf 风格的消息模板），以及一个仅接收消息字符串的构造函数（内部自动创建 `IOException` 作为 cause）。这些能力在 `AvroIterable`、`SnapshotProducer`、`ParallelIterable`、`ManifestWriter`、`SparkExceptionUtil` 等核心模块中被广泛使用——仅 `core` 和 `api` 模块就有二十余处引用。强制弃用意味着这些代码都需要迁移，且下游集成项目（Spark、Flink 等）中使用该异常的代码也会受到弃用告警的困扰。

值得注意的是，PR #5640 的编号远小于同期合并的其他 PR（如 #9253、#9260、#9288、#8340），表明这是一个长期开放、经过充分讨论后才最终合并的 PR。本提交决定撤销弃用决定，将 `RuntimeIOException` 恢复为正式支持的 API，使其可以继续被安全使用而不再产生弃用告警。这体现了项目在 API 演进策略上的务实调整：保留有实际价值且被广泛依赖的 API，而非为追求"纯 JDK 标准"而强行弃用，避免给自身和下游生态造成不必要的迁移负担。

## 如何达成设计目的

提交通过移除 `RuntimeIOException` 类上的 `@Deprecated` 注解和对应的弃用 Javadoc 说明来达成目的。类本身的代码逻辑（构造函数、继承关系等）未做任何改动，仅恢复了其作为非弃用 API 的状态。修改极其精简——1 行新增、5 行删除——但意义在于明确表达了项目维护者对该 API 的长期支持承诺。

## 修改详情

### `api/src/main/java/org/apache/iceberg/exceptions/RuntimeIOException.java`

**修改目的**：移除 `RuntimeIOException` 类的 `@Deprecated` 注解和弃用说明，恢复其为正式支持的 API。

**工作逻辑**：

修改前的类声明部分如下：
```java
/**
 * @deprecated Use java.io.UncheckedIOException directly instead.
 *     <p>Exception used to wrap {@link IOException} as a {@link RuntimeException} and add context.
 */
@Deprecated
public class RuntimeIOException extends UncheckedIOException {
```

修改后变为：
```java
/** Exception used to wrap {@link IOException} as a {@link RuntimeException} and add context. */
public class RuntimeIOException extends UncheckedIOException {
```

具体变化：
1. 移除了 `@Deprecated` 注解，使编译器不再对使用该类的代码产生弃用告警。
2. 移除了 Javadoc 中的 `@deprecated Use java.io.UncheckedIOException directly instead.` 弃用提示。
3. 保留了原有的功能描述 `Exception used to wrap {@link IOException} as a {@link RuntimeException} and add context.`，并合并为单行 Javadoc，更加简洁。

类的继承关系（`extends UncheckedIOException`）、三个构造函数（`RuntimeIOException(IOException cause)`、`@FormatMethod RuntimeIOException(IOException cause, String message, Object... args)`、`@FormatMethod RuntimeIOException(String message, Object... args)`）和其他成员均未改动。这些构造函数正是 `RuntimeIOException` 相比原生 `UncheckedIOException` 的额外价值所在——`@FormatMethod` 注解支持 printf 风格的格式化消息且能被 Error Prone 静态分析工具校验格式字符串安全性，而原生 `UncheckedIOException` 不提供此类构造函数。这一修改使得使用 `RuntimeIOException` 的二十余处核心代码（如 `AvroIterable` 中打开 Avro 文件失败时抛出 `new RuntimeIOException(e, "Failed to open file: %s", file)`、`SparkExceptionUtil` 中将 `IOException` 转为 `RuntimeIOException` 等）不再触发编译器弃用警告。

## 小结

本提交撤销了 `RuntimeIOException` 类的 `@Deprecated` 弃用标记，恢复其为正式支持的公开 API。修改极其精简，仅涉及移除注解和弃用 Javadoc 文本，不改变任何代码逻辑。这一调整体现了项目在 API 演进上的务实决策：`RuntimeIOException` 凭借其格式化构造函数等增强能力在代码库中被广泛使用（二十余处引用），强行弃用只会带来迁移负担而无实质收益。保留该类作为长期支持的 API，既尊重了已有的使用习惯，也避免了给下游生态项目造成不必要的破坏性影响。
