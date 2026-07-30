# 提交 1792：Core: Interface changes for separating rewrite planner and runner (#12306)

## 提交信息

- **序号**：1792 / 4088
- **哈希**：a50ec923f3d928f67e2a4a361c0d1162341aa084
- **短哈希**：a50ec923f
- **日期**：2025-02-26 17:22:56 -0800
- **作者**：pvary
- **提交说明**：Core: Interface changes for separating rewrite planner and runner (#12306)
- **PR/Issue**：#12306

## 总体目的

此提交是 Iceberg Core 模块在文件重写（rewrite）能力上的一次重要接口重构，目的是将 “重写计划（planning）” 与 “重写执行（running）” 这两个职责在接口层面分离开来。

在重构之前，`RewriteDataFiles` / `RewritePositionDeleteFiles` 这类 action 把 “扫描表、决定哪些文件需要重写、如何分组” 与 “实际读取并写出新文件” 这两个阶段耦合在同一个 action 流程内，引擎（Spark、Flink、Trino 等）难以独立复用某一阶段。例如，用户可能希望先在控制面生成计划，再在独立的执行面落地，或者把计划和执行放在不同的引擎/集群上执行。

本次提交通过引入三个新的核心抽象来达成解耦：
1. `FileRewritePlanner` —— 负责扫描并生成 `FileRewritePlan`；
2. `FileRewritePlan` —— 不可变的计划输出，包含若干 `RewriteGroupBase`；
3. `FileRewriteRunner` —— 引擎侧实现，接收 group 并执行真正的重写。

同时引入 `RewriteGroupBase` 抽象基类，将 `RewriteFileGroup` 与 `RewritePositionDeletesGroup` 的公共结构（info、scan tasks、目标输出大小、split 大小、预期输出文件数）上提，使两类 group 共享同一套计划/执行契约。这是后续把 rewrite 流程拆分为 “规划器 + 运行器” 架构的地基性接口变更。

## 如何达成设计目的

整体设计采用 “接口先行、保留兼容” 的策略：

1. **新增抽象接口与基类**：在 `org.apache.iceberg.actions` 包下新增 `FileRewritePlanner`、`FileRewritePlan`、`FileRewriteRunner` 三个类型，并新增 `RewriteGroupBase` 抽象基类。这些类型用泛型参数 `<I, T, F, G>` 表达 plan info、scan task、content file、group 四类对象，覆盖 data files 与 position deletes 两类重写场景。

2. **改造既有 group 类**：让 `RewriteFileGroup` 与 `RewritePositionDeletesGroup` 继承 `RewriteGroupBase`，把原本各自维护的 `info`、`fileScanTasks`/`tasks` 字段及对应方法上提到基类，统一命名为 `info()`、`fileScanTasks()`、`inputFilesSizeInBytes()`、`inputFileNum()` 等通用名称。

3. **扩展 group 构造参数**：新增构造器接收 `maxOutputFileSize`、`inputSplitSize`、`expectedOutputFiles`（以及 data files 场景的 `outputSpecId`），让 planner 能把目标输出大小、split 大小、预期输出文件数等执行 hint 透传给 runner。

4. **保留旧 API 以保证兼容**：旧的构造器与方法（如 `fileScans()`、`tasks()`、`sizeInBytes()`、`numFiles()`、`rewrittenBytes()`、`numRewrittenDeleteFiles()`）保留并标注 `@Deprecated`，内部委托到新方法，确保既有引擎实现和测试不会因本次接口变更而中断。

通过这种 “抽象上提 + 新接口引入 + 旧 API 标记弃用” 的方式，本次提交在不破坏现有实现的前提下，为后续把 planner 和 runner 完全拆开打下接口基础。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/FileRewritePlan.java`（新增, +71 lines）

**修改目的**：定义 planner 的不可变输出容器，封装一组待重写的 group 及分区级统计。

**工作逻辑**：`FileRewritePlan<I, T, F, G>` 持有三个字段：
- `CloseableIterable<G> groups`：生成的 group 流（如 `RewriteFileGroup` 或 `RewritePositionDeletesGroup`）；
- `int totalGroupCount`：本计划生成的 group 总数；
- `Map<StructLike, Integer> groupsInPartition`：每个分区内的 group 数量。

构造器为包级私有，仅由 planner 实现创建。对外暴露 `groups()`、`groupsInPartition(partition)`、`totalGroupCount()` 三个只读方法。泛型约束保证 `G extends RewriteGroupBase<I, T, F>`，使 plan 与 group 的类型参数严格对齐。

### `core/src/main/java/org/apache/iceberg/actions/FileRewritePlanner.java`（新增, +88 lines）

**修改目的**：定义重写计划阶段的接口契约，描述 planner 的生命周期。

**工作逻辑**：`FileRewritePlanner<I, T, F, G>` 是接口，Javadoc 详细说明了三类协作类（Planner、Plan、Runner）的分工与生命周期：
- `default String description()`：默认返回类名；
- `Set<String> validOptions()`：返回 planner 支持的选项集合，运行时只接受这些选项；
- `void init(Map<String, String> options)`：用配置初始化 planner；
- `FileRewritePlan<I, T, F, G> plan()`：生成重写计划。

接口将 “扫描文件 + 分组” 的职责显式独立出来，并要求实现者声明合法选项，便于后续做参数校验。

### `core/src/main/java/org/apache/iceberg/actions/FileRewriteRunner.java`（新增, +76 lines）

**修改目的**：定义重写执行阶段的接口契约，描述 runner 的生命周期。

**工作逻辑**：`FileRewriteRunner<I, T, F, G>` 是接口，与 planner 对称：
- `default String description()`：默认返回类名；
- `Set<String> validOptions()`：返回 runner 支持的选项集合；
- `void init(Map<String, String> options)`：用配置初始化 runner；
- `Set<F> rewrite(G group)`：对一个 group 执行真正的重写，返回新生成的文件集合。

Javadoc 明确 `rewrite` 的实现是引擎特定的（Spark、Flink、Trino），把 “如何写文件” 这一引擎相关逻辑收口到 runner。

### `core/src/main/java/org/apache/iceberg/actions/RewriteGroupBase.java`（新增, +96 lines）

**修改目的**：为 data files 与 position deletes 两类 group 提供公共抽象基类，统一字段与方法命名。

**工作逻辑**：`RewriteGroupBase<I, T, F>` 是抽象类，构造器接收 `info`、`fileScanTasks`、`maxOutputFileSize`、`inputSplitSize`、`expectedOutputFiles` 五个字段。对外提供：
- `info()`：group 的标识与分区信息；
- `fileScanTasks()`：输入文件的 scan task 列表；
- `inputFilesSizeInBytes()`：输入文件总大小；
- `inputFileNum()`：输入文件数；
- `maxOutputFileSize()`：runner 应使用的目标输出文件大小（planner 可覆盖表属性）；
- `inputSplitSize()`：单次读取任务的字节数（控制并行度与碎片化）；
- `expectedOutputFiles()`：整个 group 预期产出的文件数。

通过把 `maxOutputFileSize`/`inputSplitSize`/`expectedOutputFiles` 上提到基类，planner 能把执行 hint 透传给 runner，这是解耦的关键之一。

### `core/src/main/java/org/apache/iceberg/actions/RewriteFileGroup.java`（修改, +70/-45 lines）

**修改目的**：让 data files 的 group 继承 `RewriteGroupBase`，统一接口并保留兼容。

**工作逻辑**：
- 改为 `extends RewriteGroupBase<FileGroupInfo, FileScanTask, DataFile>`，删除自有的 `info`、`fileScanTasks` 字段；
- 旧构造器 `RewriteFileGroup(info, fileScanTasks)` 标注 `@Deprecated`，委托新构造器并传入默认值 `0, 0L, 0L, 0`；
- 新增构造器接收 `outputSpecId`、`writeMaxFileSize`、`splitSize`、`expectedOutputFiles`，调用 `super(...)` 并保存 `outputSpecId`；
- `fileScans()` 标注 `@Deprecated`，委托 `fileScanTasks()`；
- `sizeInBytes()`、`numFiles()` 标注 `@Deprecated`，分别委托 `inputFilesSizeInBytes()`、`inputFileNum()`；
- `rewrittenFiles()`、`asResult()`、`toString()`、`comparator()` 全部改用基类新方法名；
- 新增 `outputSpecId()` 方法，因为 `outputSpecId` 是 data files 场景特有字段，留在子类；
- `toString()` 增加对 `maxOutputFileSize`、`inputSplitSize`、`expectedOutputFiles`、`outputSpecId` 的输出，便于调试。

### `core/src/main/java/org/apache/iceberg/actions/RewritePositionDeletesGroup.java`（修改, +65/-45 lines）

**修改目的**：让 position deletes 的 group 同样继承 `RewriteGroupBase`，保持与 data files group 一致的接口。

**工作逻辑**：
- 改为 `extends RewriteGroupBase<FileGroupInfo, PositionDeletesScanTask, DeleteFile>`，删除自有的 `info`、`tasks` 字段；
- 旧构造器 `RewritePositionDeletesGroup(info, tasks)` 标注 `@Deprecated`，委托新构造器；
- 新增构造器接收 `writeMaxFileSize`、`splitSize`、`expectedOutputFiles`，调用 `super(...)`，并保留 `maxRewrittenDataSequenceNumber` 的计算（这是 position deletes 特有逻辑，留在子类）；
- `tasks()` 标注 `@Deprecated`，委托 `fileScanTasks()`；
- `rewrittenBytes()`、`numRewrittenDeleteFiles()` 标注 `@Deprecated`，委托基类方法；
- `rewrittenDeleteFiles()`、`asResult()`、`toString()`、`comparator()` 改用基类新方法名；
- `toString()` 增加 `maxOutputFileSize`、`inputSplitSize`、`expectedOutputFiles` 输出。

## 小结

- **成效**：在 Core 层引入 Planner/Plan/Runner 三件套与 `RewriteGroupBase` 抽象基类，把 “规划” 与 “执行” 在接口层面解耦，同时通过 `@Deprecated` 旧 API 保证向后兼容，为后续引擎侧实现独立 runner、跨引擎/跨集群执行计划奠定基础。
- **影响范围**：仅涉及 `core/src/main/java/org/apache/iceberg/actions` 包下 6 个文件（3 个新增、3 个修改），属于 API 层重构。data files 与 position deletes 两类重写场景均受影响。本次只改接口与数据结构，未触及任何 action 实现类（如 `BaseRewriteDataFilesSparkAction`）的具体行为，因此运行时行为不变。
- **回迁到 1.4.x 的注意事项**：这是一次纯接口扩展性重构，不改变运行时行为，回迁风险较低，但需注意以下几点：
  1. **API 兼容性**：1.4.x 是已发布分支，引入新接口（`FileRewritePlanner`/`FileRewriteRunner`/`FileRewritePlan`/`RewriteGroupBase`）与对既有 group 类的 `@Deprecated` 标注属于 API 表面变更，需评估是否符合 1.4.x 的兼容性策略。若 1.4.x 严格遵循语义化版本，新增 public API 一般可接受，但 `@Deprecated` 旧构造器/方法对下游引擎实现是温和提示，不会破坏编译。
  2. **无前置依赖**：本提交不依赖其他未回迁的提交，可独立回迁。
  3. **后续依赖**：本提交是后续将 planner/runner 完全拆分系列工作的地基，若仅回迁本提交而不同步后续实现类改造，则新接口暂时没有内置实现，仅是 “接口先行”。这点需在回迁说明中明确，避免使用者误以为新接口已可直接使用。
  4. **测试**：本提交未含测试，回迁后建议确认 1.4.x 现有的 rewrite 相关测试不受 group 类构造器/方法变更影响（因旧 API 保留，理论上应通过）。
