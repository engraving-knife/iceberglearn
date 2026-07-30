# 提交 2569：Flink: Improve ConfigOption Descriptions for Flink Table Maintenance Configs. (#13832)

## 提交信息

- **序号**：2569 / 4088
- **哈希**：55159939b0a98dd43fe3db9b9f875db836a55c5c
- **短哈希**：55159939b
- **日期**：2025-08-28 14:37:46 -0700
- **作者**：slfan1989
- **提交说明**：Flink: Improve ConfigOption Descriptions for Flink Table Maintenance Configs. (#13832)
- **PR/Issue**：#13832

## 总体目的

此次提交针对 Flink Table Maintenance（表维护）相关的配置项（ConfigOption）补充描述信息。Iceberg 的 Flink 维护模块通过 `ConfigOption` 定义了一系列可配置参数，如锁检查延迟、并行度、速率限制、槽位共享组、锁类型、JDBC/Zookeeper 锁配置、数据文件重写参数等。原先这些 `ConfigOption` 只定义了 key、类型和默认值，缺少 `withDescription(...)` 描述，导致用户在 Flink SQL 客户端或文档中无法直观看到每个参数的含义与单位。

补全描述后，用户可以通过 Flink 的配置展示机制（如 `SET` 命令、Web UI、配置文档生成）直接读到每个参数的作用、单位（秒/毫秒/字节等）及取值含义，显著降低配置出错概率，尤其是涉及超时、重试、限流等容易误配的参数。

此外，提交还为各 getter 方法补充了 Javadoc 注释，提升代码可读性和 IDE 悬浮提示体验，方便后续维护者理解每个方法的用途。

## 如何达成设计目的

- 对三个配置类（`FlinkMaintenanceConfig`、`LockConfig`、`RewriteDataFilesConfig`）中所有 `ConfigOption` 静态常量追加 `.withDescription(...)` 调用，描述内容明确单位与作用场景。
- 将原本单行链式调用拆分为多行格式，以保持可读性并容纳新增的描述方法。
- 为配置类中的 getter 方法（如 `rateLimit()`、`parallelism()`、`lockCheckDelay()` 等）添加 Javadoc 注释。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/FlinkMaintenanceConfig.java` (+24/-4)

**修改目的**：为顶层维护配置项补全描述。

**工作逻辑**：
- `LOCK_CHECK_DELAY_OPTION`：描述为维护操作（重写数据文件、manifest 文件、过期快照、删除孤儿文件）期间每次锁检查之间的延迟（秒）。
- `PARALLELISM_OPTION`：描述为维护动作的并行任务数。
- `RATE_LIMIT_OPTION`：描述为维护操作的速率限制（秒），控制每秒可执行的操作数。
- `SLOT_SHARING_GROUP_OPTION`：描述为维护任务的槽位共享组，决定 Flink 执行环境中哪些算子可共享槽位。
- 为 `rateLimit()`、`parallelism()`、`lockCheckDelay()`、`slotSharingGroup()` 方法添加 Javadoc。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/LockConfig.java` (+53/-13)

**修改目的**：为锁配置项（含 JDBC 与 Zookeeper 两类）补全描述。

**工作逻辑**：
- `LOCK_TYPE_OPTION`：锁类型，如 jdbc 或 zookeeper。
- `LOCK_ID_OPTION`：锁的唯一标识。
- JDBC 子配置：`JDBC_URI_OPTION`（JDBC 连接 URI）、`JDBC_INIT_LOCK_TABLE_OPTION`（是否初始化锁表）。
- Zookeeper 子配置：`ZK_URI_OPTION`（ZK 服务 URI）、`ZK_SESSION_TIMEOUT_MS_OPTION`（会话超时，毫秒）、`ZK_CONNECTION_TIMEOUT_MS_OPTION`（连接超时，毫秒）、`ZK_BASE_SLEEP_MS_OPTION`（重试间基础休眠，毫秒）、`ZK_MAX_RETRIES_OPTION`（最大重试次数）。
- 为 `lockType()`、`lockId()`、`jdbcUri()`、`jdbcInitTable()`、`zkUri()`、`zkSessionTimeoutMs()`、`zkConnectionTimeoutMs()`、`zkBaseSleepMs()`、`zkMaxRetries()` 等方法添加 Javadoc。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFilesConfig.java` (+48/-8)

**修改目的**：为数据文件重写相关配置项补全描述。

**工作逻辑**：
- `MAX_BYTES_OPTION`：单次重写操作允许的最大字节数，超出则限制本次调度的压缩范围。
- `TARGET_SIZE_BYTES_OPTION`：重写后目标数据文件大小（字节）。
- `MIN_INPUT_FILES_OPTION`：触发重写所需的最小输入文件数。
- `MAX_FILE_GROUP_SIZE_BYTES_OPTION`：单个文件组的最大字节数。
- `SMALL_FILE_THRESHOLD_BYTES_OPTION`：判定为小文件的阈值（字节），小于该值的文件将被合并。
- `DELETE_RATIO_THRESHOLD_OPTION`：删除文件占比阈值，用于决定是否触发重写以清理删除文件。
- 为对应 getter 方法添加 Javadoc。

## 总结

一次纯文档/可读性增强提交，通过为 Flink 表维护模块的 `ConfigOption` 追加 `withDescription` 描述和为 getter 方法补充 Javadoc，使用户和开发者能够更清晰地理解每个配置参数的含义、单位与作用，降低误配风险并提升代码可维护性。无功能逻辑变更。
