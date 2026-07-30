# 提交 1278：Deprecate iceberg-pig (#11379)

## 提交信息

- **序号**：1278 / 4088
- **哈希**：fdc2c223efa3367f8a10d9c49985326352b687b4
- **短哈希**：fdc2c223e
- **日期**：2024-10-24（Thu Oct 24 20:55:18 2024 +0200）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Deprecate iceberg-pig (#11379)
- **PR/Issue**：#11379

## 总体目的

Apache Pig 已经多年停止活跃演进，社区采用度持续下降，Hadoop 生态中几乎无人再用 Pig 处理 Iceberg 数据——主流的表处理引擎早已迁移到 Spark、Flink、Trino、Hive 等计算框架。Iceberg 长期维护一个使用率极低、却要随 Iceberg 主线升级同步适配的 `iceberg-pig` 模块，维护成本高、收益低，且 Pig 的 API 与依赖（Pig 0.17、老版 Hadoop2）也成为 Iceberg 升级依赖（如 Hadoop3、Java 17）的拖累。

本提交正式启动 `iceberg-pig` 模块的弃用流程：为该模块全部 4 个公开类（`IcebergPigInputFormat`、`IcebergStorage`、`PigParquetReader`、`SchemaUtil`）打上 `@Deprecated` 注解并在 Javadoc 标注"will be removed in 1.8.0"，同时在关键入口（构造函数）打印 `LOG.warn("Iceberg Pig is deprecated and will be removed in Iceberg 1.8.0")`，给还在使用该模块的用户一个明确的弃用信号与迁移缓冲期（1.8.0 前仍可用，但会持续告警），便于社区后续在 1.8.0 中彻底移除该模块。

## 如何达成设计目的

通过"Javadoc 注释 + Java 注解 + 运行时告警"三层信号实现弃用告知：

1. **Javadoc 注释**：每个类上加 `/** @deprecated will be removed in 1.8.0 */`，在 IDE 鼠标悬停与生成的 Javadoc 站点上直观显示弃用信息与移除版本；
2. **`@Deprecated` 注解**：触发编译器 `deprecation` 警告，强制依赖方在编译时就能感知到该模块已弃用，便于及早迁移；
3. **运行时 `LOG.warn`**：在 `IcebergPigInputFormat` 构造、`IcebergStorage` 构造、`PigParquetReader` 构造时打印弃用告警，确保即使依赖方未留意编译警告，在作业实际运行时也会在日志中看到明确的移除时间表。

## 修改详情

### `pig/src/main/java/org/apache/iceberg/pig/IcebergPigInputFormat.java`（修改）

**修改目的**：标记 Pig 输入格式类弃用。

- 类上加 Javadoc `/** @deprecated will be removed in 1.8.0 */` 与 `@Deprecated` 注解；
- 在 `IcebergPigInputFormat(Table table, String signature)` 构造函数开头加 `LOG.warn("Iceberg Pig is deprecated and will be removed in Iceberg 1.8.0")`，对象实例化即告警。

### `pig/src/main/java/org/apache/iceberg/pig/IcebergStorage.java`（修改）

**修改目的**：标记 Pig `LoadFunc` 主入口弃用。

- 类上加 Javadoc 与 `@Deprecated`；
- 新增显式无参构造 `public IcebergStorage()`，其中打印弃用 warn——`IcebergStorage` 是 Pig 反射加载 `LoadFunc` 的入口，Pig 通过反射调用无参构造实例化该类，因此在该构造里告警能在 Pig 作业启动时第一时间提示用户。

### `pig/src/main/java/org/apache/iceberg/pig/PigParquetReader.java`（修改）

**修改目的**：标记 Parquet 读取辅助类弃用，并补齐日志器。

- 新增 `org.slf4j.Logger`/`LoggerFactory` 导入；
- 类上加 Javadoc 与 `@Deprecated`；
- 新增静态 `LOG` 字段；
- 把原私有无参构造 `private PigParquetReader() {}` 改为带告警的实现 `private PigParquetReader() { LOG.warn(...); }`。

### `pig/src/main/java/org/apache/iceberg/pig/SchemaUtil.java`（修改）

**修改目的**：标记 Schema 转换工具类弃用。

- 类上加 Javadoc 与 `@Deprecated`。该类只有静态方法、无私有构造告警需要，因此只加注解与注释。

## 小结

- **成效**：正式宣布 `iceberg-pig` 进入弃用周期，给社区用户一个 1.8.0 移除的明确预期，同时降低主线维护 Pig 模块的负担；编译期 + 运行期双通道告知，覆盖面广。
- **影响范围**：仅 `pig` 模块 4 个公开类的注解/告警，无功能变更，运行时行为除新增 warn 日志外完全不变；其它模块不受影响。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯注解与日志告警，无破坏性变更。需注意 1.4.x 是否计划在 1.8.0 之前发布——若是，则 1.4.x 用户会持续看到弃用告警，属预期行为。若 1.4.x 计划长期维护，可考虑在该告警中同步提示"建议迁移到 Spark/Flink"等替代方案（本提交未加此类提示）。如果 1.4.x 上的 pig 模块结构略有不同（如某些类未引入），cherry-pick 时按文件粒度处理即可。
