# 提交 3289：Spark 4.1: Introduce modes in SparkWriteBuilder (#15374)

## 提交信息

- **序号**：3289 / 4088
- **哈希**：4ed7658bdcbab03c696472f56f3cfc810d1fbec8
- **短哈希**：4ed7658bd
- **日期**：2026-02-19
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Introduce modes in SparkWriteBuilder (#15374)
- **PR/Issue**：#15374

## 总体目的

`SparkWriteBuilder` 是 Spark 4.1 写入路径的构建器，负责根据用户调用的 API（追加、动态分区覆盖、按过滤条件覆盖、copy-on-write 的 UPDATE/DELETE/MERGE）组装出正确的 `SparkWrite`。重构前，它用一组可变的布尔标志位与可空字段来表达当前写入模式：`overwriteDynamic`、`overwriteByFilter`、`overwriteFiles`、`overwriteExpr`、`copyOnWriteScan`、`copyOnWriteCommand`、`copyOnWriteIsolationLevel`。这种"标志位组合"写法存在几个问题：①各 setter 之间必须靠分散的 `Preconditions.checkState` 维护互斥不变量（如"不能既按过滤器覆盖又动态覆盖"），容易遗漏；②`build()`、`toBatch()`、`toStreaming()`、`writeRequirements()` 里大量 if-else 分支都基于这些布尔位组合判断，可读性差；③无法在类型层面保证"同时只有一个模式生效"。

本提交将这些散乱状态收敛为一个 `sealed interface Mode`，并以 record 形式定义四个具体模式：`Append`（追加）、`DynamicOverwrite`（动态分区覆盖）、`OverwriteByFilter(Expression)`（按过滤条件覆盖，即静态覆盖）、`CopyOnWriteOperation(scan, command, isolationLevel)`（COW 操作）。每个 setter 现在只检查 `mode == null` 后赋一个具体的 Mode 实例，互斥性由"单一字段单次赋值"天然保证。`build()` 及其内部匿名类的 `toBatch()`/`toStreaming()`/`writeRequirements()` 改用 `instanceof ... var` 模式匹配分发，逻辑更清晰。

与此同时，作者把原本揉在 `build()` 与 `validateOrMergeWriteSchema` 静态方法中的"行血缘（row lineage）处理"与"schema 校验/合并"逻辑拆分成一组职责单一的小方法（`writeNeedsRowLineage()`、`writeIncludesRowLineage()`、`sparkWriteSchema()`、`validateRowLineage()`、`validateWriteSchema()`、`mergeAndValidateWriteSchema()`、`addRowLineageIfNeeded()`），并对行血缘缺失的场景改为主动抛错而非静默填充 null 列。这属于 Spark 4.1 适配链中提升写入构建器可维护性与正确性的结构性重构。

## 如何达成设计目的

整体思路是用"代数数据类型（sealed interface + records）"替代"布尔标志位组合"来表达写入模式状态机：字段收敛为单一 `private Mode mode = null`，setter 中 `Preconditions.checkState(mode == null, ...)` 后赋值对应 Mode 实例，分发处用模式匹配。同时把构造期就确定的配置（`caseSensitive`、`checkNullability`、`checkOrdering`、`mergeSchema`）从 `writeConf` 提前缓存为 final 字段，避免反复读取。行血缘与 schema 校验逻辑被拆分为独立的私有方法，由 `build()` 编排调用。所有改动集中在 `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` 一个文件。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+111/-108 lines)

**修改目的**：用 sealed `Mode` 类型重构写入模式状态机，并拆分行血缘与 schema 校验逻辑。

**工作逻辑**：

**字段与构造器**：移除旧的 `writeInfo`/`dsSchema`/`overwriteMode`/`overwriteDynamic`/`overwriteByFilter`/`overwriteExpr`/`overwriteFiles`/`copyOnWriteScan`/`copyOnWriteCommand`/`copyOnWriteIsolationLevel` 等字段，改为 `info`、`caseSensitive`、`checkNullability`、`checkOrdering`、`mergeSchema`（构造时一次性从 `writeConf` 读取缓存）以及核心的 `private Mode mode = null`。

**Mode 类型定义**：在类内新增 `sealed interface Mode`，含四个 record：`Append()`、`DynamicOverwrite()`、`OverwriteByFilter(Expression expr)`、`CopyOnWriteOperation(SparkCopyOnWriteScan scan, Command command, IsolationLevel isolationLevel)`，分别对应追加、动态覆盖、按过滤条件覆盖、COW 操作。sealed 保证穷尽匹配。

**setter 改造**：
- `overwriteFiles(scan, command, isolationLevel)`：校验 `mode == null` 后赋 `mode = new CopyOnWriteOperation(...)`。
- `overwriteDynamicPartitions()`：校验 `mode == null` 后赋 `mode = new DynamicOverwrite()`。
- `overwrite(filters)`：把 `SparkFilters.convert(filters)` 得到 expr，再通过新增的 `useDynamicOverwrite(expr)` 判断（`expr == alwaysTrue() && "dynamic".equals(writeConf.overwriteMode())`）决定赋 `DynamicOverwrite` 还是 `OverwriteByFilter(expr)`。这把原先散在 setter 里的分支判断抽成独立方法。

**行血缘处理**（重构为多个方法）：
- `writeNeedsRowLineage()`：`TableUtil.supportsRowLineage(table) && mode instanceof CopyOnWriteOperation`——即只有表支持行血缘且当前是 COW 操作时才需要写出行血缘列。语义与旧代码的 `writeRequiresRowLineage = supportsRowLineage && overwriteFiles` 一致。
- `writeIncludesRowLineage()`：检查 `info.metadataSchema()` 中是否存在名为 `MetadataColumns.ROW_ID` 的字段，判断输入数据是否已携带行血缘信息。
- `sparkWriteSchema()`：若 `writeIncludesRowLineage()`，则从 `info.metadataSchema()` 取出 `ROW_ID` 与 `LAST_UPDATED_SEQUENCE_NUMBER` 两个 `StructField` 追加到 `info.schema()`；否则直接返回 `info.schema()`。相比旧代码用硬编码 `LongType$.MODULE$` 添加列，现在直接复用元数据 schema 中的字段定义，类型更准确。
- `validateRowLineage()`：新增的校验——若 `writeNeedsRowLineage()` 为真但 `writeIncludesRowLineage()` 为假，则抛 `IllegalArgumentException("Row lineage information is missing for write in mode: %s", mode)`。这是相对旧代码的行为收紧：旧代码在此情形下会静默追加 null 的 LongType 列，新代码要求 COW 写入必须显式提供行血缘信息，避免写出无意义的 null 行 ID。

**schema 校验/合并**（拆分原 `validateOrMergeWriteSchema` 静态方法）：
- `validateWriteSchema()`：非 merge 路径，`SparkSchemaUtil.convert(table.schema(), info.schema(), caseSensitive)` 后 `TypeUtil.validateWriteSchema(...)`，再 `addRowLineageIfNeeded`。
- `mergeAndValidateWriteSchema()`：merge schema 路径，注释列出四步流程：用 `convertWithFreshIds` 给新字段分配 ID → `updateSchema().unionByNameWith(newSchema).apply()` 得到合并 schema → 用合并 schema 重新转换 dsSchema 以采用 UpdateSchema 分配的 ID → 校验通过后 `update.commit()` 提交 schema 变更，最后 `addRowLineageIfNeeded`。
- `addRowLineageIfNeeded(schema)`：`writeNeedsRowLineage()` 时 `MetadataColumns.schemaWithRowLineage(schema)`，否则原样返回。

**build() 编排**：改为先 `validateRowLineage()`，再按 `mergeSchema` 选择 `mergeAndValidateWriteSchema()` 或 `validateWriteSchema()` 得到 `writeSchema`，然后 `validatePartitionTransforms`，构造 `SparkWrite` 时用 `sparkWriteSchema()` 提供 Spark 侧写入 schema。

**匿名 SparkWrite 分发**：
- `toBatch()`：用 `mode instanceof OverwriteByFilter overwrite`/`DynamicOverwrite`/`CopyOnWriteOperation cow` 模式匹配分发到 `asOverwriteByFilter(overwrite.expr())`/`asDynamicOverwrite()`/`asCopyOnWriteOperation(cow.scan(), cow.isolationLevel())`，否则 `asBatchAppend()`。
- `toStreaming()`：`OverwriteByFilter` 时校验 expr 必须为 `alwaysTrue()`（流式不支持带过滤覆盖）后 `asStreamingOverwrite()`；`mode == null || mode instanceof Append` 时 `asStreamingAppend()`；其余抛 `IllegalStateException("Unsupported streaming write mode: " + mode)`。相比旧代码多个分散 checkState，收敛为单一 switch-like 分发。
- `writeRequirements()`：`CopyOnWriteOperation cow` 时用 `writeConf.copyOnWriteRequirements(cow.command())`，否则 `writeConf.writeRequirements()`。

**导入调整**：移除 `LongType$` 导入（不再硬编码 LongType），新增 `StructField` 导入（用于 sparkWriteSchema 取字段），以及四个 Mode 子类型的静态导入。

## 总结

本提交将 SparkWriteBuilder 从布尔标志位状态机重构为基于 sealed interface/records 的代数数据类型模式，使写入模式的互斥性与穷尽匹配在类型层面得到保障，并把行血缘处理与 schema 校验拆分为职责单一的方法，同时收紧了 COW 写入缺失行血缘时的校验（由静默填充 null 改为抛错）。整体显著提升了 Spark 4.1 写入构建器的可读性、可维护性与正确性保证，为后续 Spark 4.1 写入相关演进奠定更清晰的代码结构。
