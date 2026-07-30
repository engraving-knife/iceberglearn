# 提交 1484：Spark: Remove deprecated SparkAppenderFactory (#11727)

## 提交信息

- **序号**：1484 / 4088
- **哈希**：5c00b29ae29b2c5f763559c70638e8e76431ecfd
- **短哈希**：5c00b29ae
- **日期**：2024-12-12（Thu Dec 12 14:31:21 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Spark: Remove deprecated SparkAppenderFactory (#11727)
- **PR/Issue**：#11727

## 总体目的

Iceberg 的 Spark 模块中存在两套并行的 writer 工厂实现：
- 旧实现 `SparkAppenderFactory`：基于 `FileAppenderFactory<InternalRow>` 接口，构造参数多且易错，曾通过 builder 模式缓解（PR #2499），但整体设计已落后。
- 新实现 `SparkFileWriterFactory`：基于 `FileWriterFactory` 接口，与 Iceberg 新版 writer API 一致，已在 `SparkWrite`、`SparkPositionDeltaWrite`、`SparkPositionDeletesRewrite` 等核心写入路径全面采用。

`SparkAppenderFactory` 已于 1.7.0 版本（PR #11076，commit 09370ddbc，2024-09-27）标注 `@Deprecated`，并在 Javadoc 中明确"since 1.7.0, will be removed in 1.8.0; use SparkFileWriterFactory instead"。本提交按计划在 1.8.0 开发周期将其彻底删除，连同仅服务于该类的单元测试与基准测试一并清理，使代码库保持单一、清晰的 writer 工厂实现，避免新旧并存带来的维护负担与用户混淆。

## 如何达成设计目的

直接删除 `SparkAppenderFactory` 类、对应的 `TestSparkAppenderFactory` 单元测试、`TestSparkMergingMetrics` 测试（该测试以 `SparkAppenderFactory` 为载体测试合并指标，新实现已通过 `SparkFileWriterFactory` 路径覆盖）、以及 `WritersBenchmark` 基准测试（专门 benchmark 旧工厂）。由于新实现 `SparkFileWriterFactory` 已在所有生产写入路径取代旧工厂，且全仓库搜索确认无残留引用，删除是干净、无副作用的纯清理性变更。

该修改同时应用到 Spark 3.3、3.4、3.5 三个并行分支目录，三处文件结构一致。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkAppenderFactory.java`（删除，约 322 行）

**修改目的**：移除已废弃的旧 writer 工厂实现。

**工作逻辑**：该类实现 `FileAppenderFactory<InternalRow>`，承担根据表 schema、分区规范、写上下文等构造 `FileAppender` 的职责。删除后，所有写入路径统一使用 `SparkFileWriterFactory`（基于 `FileWriterFactory` 接口），后者与 Iceberg 核心 V2 写入 API 对接更直接，支持 FanoutWriter、写入指标、分布式分发等新特性。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkAppenderFactory.java`（删除，约 328 行）

**修改目的**：同上，对 Spark 3.4 分支做相同清理。

**工作逻辑**：3.4 版本的 `SparkAppenderFactory` 与 3.3 基本一致，存在少量因 Spark API 差异造成的行数差别。删除理由与 3.3 相同。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkAppenderFactory.java`（删除，约 328 行）

**修改目的**：同上，对 Spark 3.5 分支做相同清理。

**工作逻辑**：与 3.4 几乎完全一致。删除后 3.5 模块仅保留 `SparkFileWriterFactory` 作为 Spark 写入工厂的唯一实现。

### `spark/v3.{3,4,5}/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkAppenderFactory.java`（各删除 64 行）

**修改目的**：移除针对旧工厂的单元测试。

**工作逻辑**：`TestSparkAppenderFactory` 继承自 Iceberg Core 的 `TestAppenderFactory` 抽象测试基类，专门为 `SparkAppenderFactory` 提供引擎侧测试用例。被测对象已删除，测试自然随之删除；新工厂 `SparkFileWriterFactory` 的对应测试在其它测试类中维护。

### `spark/v3.{3,4,5}/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMergingMetrics.java`（各删除约 70 行）

**修改目的**：移除依赖旧工厂的合并指标测试。

**工作逻辑**：`TestSparkMergingMetrics` 通过 `SparkAppenderFactory` 构造 appender 来测试写入时的指标合并行为（如记录数、文件大小等统计）。旧工厂删除后该测试失去载体；新写入路径下相同指标行为由 `SparkFileWriterFactory` 相关测试覆盖。

### `spark/v3.{3,4,5}/spark/src/jmh/java/org/apache/iceberg/spark/source/WritersBenchmark.java`（各删除 97 行）

**修改目的**：移除针对旧工厂的 JMH 基准测试。

**工作逻辑**：`WritersBenchmark` 是 JMH 基准测试类，专门度量 `SparkAppenderFactory` 写入路径的性能。被测对象已删除，基准测试一并清理，避免编译失败。新工厂的性能基准如有需要会另行添加。

## 小结

- **成效**：Spark 3.3 / 3.4 / 3.5 三个分支目录共删除 12 个文件、1672 行废弃代码，Spark 模块 writer 工厂实现归一为 `SparkFileWriterFactory`，代码库更精简、维护成本降低，与 1.7.0 发布时声明的"1.8.0 移除"承诺保持一致。
- **影响范围**：纯删除，无新增。生产写入路径（`SparkWrite`、`SparkPositionDeltaWrite`、`SparkPositionDeletesRewrite`）此前已切换至 `SparkFileWriterFactory`，删除旧工厂不影响任何运行时行为；仅影响测试套件与 JMH 基准的覆盖范围。
- **回迁到 1.4.x 的注意事项**：
  - **不建议回迁**。1.4.x 是维护分支，对应 Iceberg 1.4.x 版本，而 `SparkAppenderFactory` 是在 **1.7.0 才被标记废弃**，1.4.x 中该类仍是合法、非废弃的实现，可能仍被部分写入路径使用。
  - 强行 cherry-pick 本提交到 1.4.x 会直接删除 1.4.x 中仍在使用的工厂类，**会导致编译失败或运行时缺失**。
  - 该清理属于"按版本路线图推进的废弃类移除"，1.4.x 用户若需要新工厂能力应升级到 1.7.0+；1.4.x 维护分支应保留 `SparkAppenderFactory` 直到自身生命周期结束。
  - 若 1.4.x 上确实需要修复与旧工厂相关的 bug，应单独打补丁，而非引入本删除提交。
