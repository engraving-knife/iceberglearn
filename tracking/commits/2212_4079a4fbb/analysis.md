# 提交 2212：Flink: Support compact in iceberg sink v2 (#12979)

## 提交信息

- **序号**：2212 / 4088
- **哈希**：4079a4fbb8c49a020d8f0237bec506c1bbc4bbdd
- **短哈希**：4079a4fbb
- **日期**：2025-06-05 17:57:48 +0800
- **作者**：GuoYu
- **提交说明**：Flink: Support compact in iceberg sink v2 (#12979)
- **PR/Issue**：#12979

## 总体目的

这个提交为 Flink 的 Iceberg Sink v2（基于 Flink 新版 Sink API 的实现）添加了小文件合并（compaction）能力。在流式写入 Iceberg 的场景中，由于 checkpoint 间隔较短，会产生大量小文件，影响查询性能和元数据管理。此前的 IcebergSink v2 实现中 `addPostCommitTopology` 方法仅留有 `// TODO Support small file compaction` 注释，并未实现压缩能力。本提交通过在提交后的拓扑中接入维护（maintenance）框架，自动触发 `RewriteDataFiles` 任务对小文件进行合并。同时引入了完整的锁配置（LockConfig）机制以避免多个 sink 实例并发触发压缩任务造成冲突。该改动显著提升了流式写入场景下 Iceberg 表的可维护性和查询性能。

## 如何达成设计目的

- 新增配置项 `compaction-enabled`（默认 false），通过 `FlinkWriteConf.compactMode()` 读取，控制是否启用压缩。
- 实现 `IcebergSink.addPostCommitTopology`：当 compactMode 为 true 时，将 committable 流通过 `CommittableToTableChangeConverter` 转换为 `TableChange` 流（描述新增数据文件和删除文件），然后接入 `TableMaintenance` 框架，添加 `RewriteDataFiles` 任务。
- 新增 `CommittableToTableChangeConverter`：单并行度算子，从 IcebergCommittable 中反序列化 DeltaManifests，读取 WriteResult，构造 TableChange，并清理已提交的临时 manifest（在 compactMode 下由 converter 负责清理，避免 committer 提前清理）。
- 新增 `FlinkMaintenanceConfig`：统一管理维护相关配置（rate limit、parallelism、lock check delay、slot sharing group）。
- 新增 `LockConfig`：支持 JDBC 和 Zookeeper 两种锁类型，用于在多 sink 并发场景下协调压缩任务的触发。
- 新增 `LockFactoryBuilder`：根据 LockConfig 的 lockType 创建对应的 `TriggerLockFactory`（JdbcLockFactory 或 ZkLockFactory）。
- 新增 `RewriteDataFilesConfig`：配置压缩任务的触发条件（commit 数、文件数、文件大小、间隔时间）和压缩参数（max bytes、partial progress）。
- 将 `FlinkConfParser` 及其内部类从 package-private 改为 public，并标注 `@Internal`，以便 maintenance 包复用配置解析能力。
- 修改 `IcebergCommitter`：新增 compactMode 字段，在 compactMode 下不在 commit 时删除 manifest（改由 converter 在 post-commit 阶段清理）。
- 修改 `FlinkManifestUtil.deleteCommittedManifests`：新增重载方法接收 tableName 和 FileIO 而非 Table 对象，便于 converter 调用。
- 提供完整测试覆盖：包括表 sink 压缩集成测试、配置类测试、锁配置和锁工厂构建器测试、CommittableToTableChangeConverter 单元测试。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +59/-8 lines)

**修改目的**：在 sink 构建和 post-commit 拓扑中集成压缩能力。

**工作逻辑**：
- 新增字段 `compactMode` 和 `flinkMaintenanceConfig`，在构造函数中从 `FlinkWriteConf` 读取 compactMode 并接收 maintenance 配置。
- 修改 `createCommitter`：将 compactMode 传递给 `IcebergCommitter`。
- 实现 `addPostCommitTopology`：若 compactMode 关闭则直接返回；否则将 committable 流 `.global()` 后通过 `CommittableToTableChangeConverter`（单并行度）转换为 TableChange 流，然后构建 `RewriteDataFilesConfig`、`LockConfig`、`TriggerLockFactory`，创建 `TableMaintenance.Builder` 添加 `RewriteDataFiles` 任务，配置 rateLimit、lockCheckDelay、slotSharingGroup、parallelism 后 append 到拓扑。
- 修改 `forLoader` 构建：在 builder 末尾创建 `FlinkMaintenanceConfig` 并传入 IcebergSink 构造函数。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/CommittableToTableChangeConverter.java` (新增, +107 lines)

**修改目的**：将 Flink sink 的 committable 消息转换为 maintenance 框架所需的 TableChange。

**工作逻辑**：
- 继承 `ProcessFunction<CommittableMessage<IcebergCommittable>, TableChange>`，强制单并行度运行。
- `processElement` 中：检查消息是否为 `CommittableWithLineage`，反序列化 `IcebergCommittable.manifest` 得到 `DeltaManifests`，通过 `FlinkManifestUtil.readCompletedFiles` 读取 WriteResult，构造 `TableChange`（包含 dataFiles 和 deleteFiles），输出后调用 `FlinkManifestUtil.deleteCommittedManifests` 清理临时 manifest。
- 异常处理：反序列化或读取失败时记录 warn 日志并跳过，不中断流处理。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockFactoryBuilder.java` (新增, +87 lines)

**修改目的**：根据 LockConfig 创建对应类型的锁工厂。

**工作逻辑**：
- `build(LockConfig, tableName)`：根据 `lockType` 分发，"jdbc" 创建 JdbcLockFactory（设置 jdbcUri、lockId、properties 含 init-lock-table），"zookeeper" 创建 ZkLockFactory（设置 zkUri、lockId、超时和重试参数），其他抛出 IllegalArgumentException。
- 对必要参数进行非空校验。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/FlinkMaintenanceConfig.java` (新增, +112 lines)

**修改目的**：统一管理维护任务的配置。

**工作逻辑**：
- 定义前缀 `flink-maintenance.`，配置项包括 lock-check-delay-seconds、parallelism、rate-limit-seconds、slot-sharing-group。
- 通过 `FlinkConfParser` 解析各配置项，支持从 writeOptions、Flink 配置、默认值三级查找。
- 提供 `createRewriteDataFilesConfig()` 和 `createLockConfig()` 工厂方法。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/LockConfig.java` (新增, +183 lines)

**修改目的**：配置并发压缩任务的分布式锁。

**工作逻辑**：
- 支持两种锁类型：JDBC（通过 `JdbcLockConfig` 配置 uri、init-lock-table）和 Zookeeper（通过 `ZkLockConfig` 配置 uri、session/connection timeout、base sleep、max retries）。
- `lockId(tableName)`：若未配置则默认使用表名作为锁 ID。
- `properties()`：合并 writeProperties 和 setProperties，过滤出以 PREFIX 开头的键并去除前缀后返回，用于传递给锁工厂。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFilesConfig.java` (新增, +150 lines)

**修改目的**：配置数据文件压缩（RewriteDataFiles）任务的触发条件和参数。

**工作逻辑**：
- 触发条件配置：schedule.commit-count（默认10）、schedule.data-file-count（默认1000）、schedule.data-file-size（默认100GB）、schedule.interval-second（默认10分钟）。
- 压缩参数：max-bytes、partial-progress-enabled、partial-progress-max-commits。
- `properties()`：提取以 PREFIX 开头的配置，去除前缀后返回，传递给 RewriteDataFiles action。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkConfParser.java` (修改, +13/-12 lines)

**修改目的**：将配置解析器公开给 maintenance 包复用。

**工作逻辑**：将 `FlinkConfParser` 类及内部所有 ConfParser 子类（Boolean/Int/Long/Double/String/Enum/Duration/ConfParser）从 package-private 改为 `public`，并给类添加 `@Internal` 注解，表明虽然公开但属于内部 API。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteConf.java` (修改, +9 lines)

**修改目的**：暴露 compactMode 配置读取。

**工作逻辑**：新增 `compactMode()` 方法，通过 confParser 读取 `compaction-enabled` 选项，支持从 FlinkWriteOptions.COMPACTION_ENABLE 和 Flink 配置中读取，默认 false。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteOptions.java` (修改, +3 lines)

**修改目的**：定义压缩开关配置项。

**工作逻辑**：新增 `COMPACTION_ENABLE = ConfigOptions.key("compaction-enabled").booleanType().defaultValue(false)`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergCommitter.java` (修改, +7/-2 lines)

**修改目的**：在压缩模式下延迟清理 manifest。

**工作逻辑**：
- 新增 `compactMode` 字段并在构造函数中接收。
- 修改 commit 方法：仅在 `!compactMode` 时调用 `FlinkManifestUtil.deleteCommittedManifests`。在压缩模式下，manifest 清理由 `CommittableToTableChangeConverter` 负责，因为它需要先读取 manifest 内容构造 TableChange。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkManifestUtil.java` (修改, +13/-4 lines)

**修改目的**：支持以 tableName 和 FileIO 而非 Table 对象清理 manifest。

**工作逻辑**：新增 `deleteCommittedManifests(String tableName, FileIO io, List<ManifestFile>, String flinkJobId, long checkpointId)` 重载方法，原方法委托给新方法。这样 CommittableToTableChangeConverter 无需持有完整 Table 对象即可清理 manifest。

### 测试文件（新增多个, 共约 +920 lines）

**修改目的**：验证压缩功能的端到端流程和各组件的正确性。

**工作逻辑**：
- `TestFlinkTableSinkCompaction.java`（+178）：端到端集成测试，验证 sink 写入后自动触发压缩。
- `TestIcebergSinkCompact.java`（+149）：测试 sink 压缩拓扑构建。
- `TestCommittableToTableChangeConverter.java`（+319）：详细单元测试 converter 的各种场景，包括正常转换、空 committable、反序列化失败、manifest 清理等。
- `TestRewriteDataFilesConfig.java`（+142）：测试压缩配置解析。
- `TestLockConfig.java`（+84）：测试锁配置解析。
- `TestLockFactoryBuilder.java`（+109）：测试锁工厂构建器，包括 JDBC/ZK 创建和异常场景。

### 其他维护 API 文件的小幅修改

- `RewriteDataFiles.java`、`TableMaintenance.java`、`JdbcLockFactory.java`、`TableChange.java`：小幅修改以支持新功能集成（如调整方法签名、字段可见性等）。

## 总结

该提交是 Flink Iceberg Sink v2 的重要功能增强，实现了流式写入场景下的小文件自动压缩能力。通过将 sink 的 post-commit 阶段与 maintenance 框架（RewriteDataFiles）集成，并引入分布式锁机制协调并发压缩任务，有效解决了流式写入产生大量小文件的问题。设计上充分复用了已有的 maintenance API，配置体系完整且可灵活定制触发条件。测试覆盖全面，包括端到端集成测试和各组件单元测试。
