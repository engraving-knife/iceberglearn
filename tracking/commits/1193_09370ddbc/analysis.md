# 提交 1193：Spark: Deprecate SparkAppenderFactory (#11076)

## 提交信息

- **序号**：1193 / 4088
- **哈希**：09370ddbc39fc3920fb8cbd3dff11b377dd37e40
- **短哈希**：09370ddbc
- **日期**：2024-09-27（Fri Sep 27 10:06:58 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Spark: Deprecate SparkAppenderFactory (#11076)
- **PR/Issue**：#11076

## 总体目的

Iceberg 的 Spark 模块（同时维护 v3.3、v3.4、v3.5 三个 Spark 版本分支）历史上使用 `SparkAppenderFactory` 作为 `FileAppenderFactory<InternalRow>` 的 Spark 实现，用于在 Spark 写入流程中创建文件 appender。社区在更新版本中已经引入了更通用的 `SparkFileWriterFactory`，作为后续写入流程的标准入口，并计划在 1.8.0 中移除旧的 `SparkAppenderFactory`。

本提交在三个 Spark 版本（v3.3/v3.4/v3.5）的 `SparkAppenderFactory` 类上同时添加 `@Deprecated` 注解及 Javadoc，明确告知调用方该类自 1.7.0 起弃用、将在 1.8.0 移除，并指向替代实现 `SparkFileWriterFactory`。这是一次纯 API 治理性改动，目的是给下游使用者一个明确的迁移窗口，避免在 1.8.0 突然删除导致编译失败。

## 如何达成设计目的

在 `spark/v3.3/spark`、`spark/v3.4/spark`、`spark/v3.5/spark` 三个目录下的 `SparkAppenderFactory.java` 文件中，类声明上方插入同样的 4 行：

```java
/**
 * @deprecated since 1.7.0, will be removed in 1.8.0; use {@link SparkFileWriterFactory} instead.
 */
@Deprecated
```

不改动类体内部任何字段或方法，只是给类打上弃用标记。这样在编译期，任何仍在引用 `SparkAppenderFactory` 的代码都会触发 `deprecation` 警告，IDE 与构建工具（如 `--deprecation`）也会显式提示。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkAppenderFactory.java`

**修改目的**：在 Spark 3.3 模块中将 `SparkAppenderFactory` 标记为弃用。

**工作逻辑**：在 `class SparkAppenderFactory implements FileAppenderFactory<InternalRow>` 这一行之前，新增 `@Deprecated` 注解及 Javadoc 注释，注释中明确：

- 弃用起始版本：1.7.0
- 计划移除版本：1.8.0
- 替代实现：`SparkFileWriterFactory`

类内 `properties`、`writeSchema` 等字段及所有方法均未改动。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkAppenderFactory.java`

**修改目的**：在 Spark 3.4 模块中标记弃用。

**工作逻辑**：与 v3.3 完全相同的 4 行注释 + 注解，作用一致。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkAppenderFactory.java`

**修改目的**：在 Spark 3.5 模块中标记弃用。

**工作逻辑**：与 v3.3、v3.4 完全相同的 4 行注释 + 注解，作用一致。

## 小结

- **成效**：三个 Spark 版本（3.3/3.4/3.5）下的 `SparkAppenderFactory` 现均带有 `@Deprecated` 标记与迁移指引，调用方在编译时即可收到弃用警告，并有明确路径迁移到 `SparkFileWriterFactory`；为 1.8.0 移除该类铺平道路。
- **影响范围**：仅三个 `SparkAppenderFactory.java` 文件，每个文件新增 4 行注释/注解，共 12 行新增；无运行时行为变更，无 API 签名变更（除 `@Deprecated` 外）。
- **回迁到 1.4.x 的注意事项**：1.4.x 是更早的维护分支，其对应历史时期的 `SparkAppenderFactory` 是否已被替换为 `SparkFileWriterFactory` 取决于 1.4.x 的代码基线。一般而言，**弃用注解属于"向前提示"，回迁意义不大**：1.4.x 不会跃迁到 1.8.0，标记 1.7.0 弃用反而会让 1.4.x 用户产生版本顺序错乱的疑惑。建议 1.4.x 不回迁此改动；若 1.4.x 已经存在 `SparkFileWriterFactory` 且希望统一弃用策略，可调整为符合 1.4.x 节奏的弃用版本号后再回迁。
