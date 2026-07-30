# 提交 3016：Core: Change removal of deprecations to 1.12.0 (#14392)

## 提交信息

- **序号**：3016 / 4088
- **哈希**：831b4ea108b9def6ae92258a7382aab096d87ed2
- **短哈希**：831b4ea10
- **日期**：2025-12-15
- **作者**：gaborkaszab
- **提交说明**：Core: Change removal of deprecations to 1.12.0 (#14392)
- **PR/Issue**：#14392

## 总体目的

本提交的核心目的是将 Iceberg Core 模块中多处已废弃（deprecated）API 的"计划移除版本"从 2.0.0（或写作 2.0）提前到 1.12.0。在 Iceberg 的演进过程中，社区此前在废弃一批旧 API 时统一将移除时间标注为 2.0.0 大版本。然而 2.0 是一个较为遥远的里程碑，会导致废弃 API 长期滞留于代码库中，增加维护负担、混淆 API 表面（API surface），并使新用户难以判断应优先使用哪个 API。

通过将移除版本提前到 1.12.0，社区传达了更明确的清理信号：这些 API 即将在下一个 minor 版本被移除，下游项目（如 Flink、Spark、Trino 等集成）需尽快迁移到推荐替代方案。本提交属于纯文档/注释类改动，不改变任何运行时行为，仅修改 `@deprecated` Javadoc 文本和运行时告警信息中的版本号字符串，以及一处测试断言。

值得注意的是，改动并非"一刀切"。对于 `LocationProviders.java` 中的废弃属性提示信息，作者将 `2.0` 改为 `2.0.0`（统一格式而非提前移除），说明此处仅是版本号格式规范化。真正提前移除版本的是 `SystemConfigs`、`SystemProperties`、`AvroSchemaUtil`、`RawDecoder`、`StandardEncryptionManager`、`ContentCache`、`RESTSessionCatalog`、`SnapshotUtil`、`ThreadPools`、`MetricsConfig` 等类中的废弃 API。`TestJdbcTableConcurrency.java` 中的改动则属于另一类：将误标 `@Deprecated(since = "1.2")` 的 JDK 方法改为 `@SuppressWarnings("deprecation")`，因为 `setUnicodeStream` 是 JDBC 规范中的废弃方法，Iceberg 不应自行声明废弃，而应跟随 JDK 的废弃策略。

## 如何达成设计目的

整体思路是遍历 Core 模块中所有标注了 `will be removed in 2.0.0` 或类似措辞的废弃 API，将版本号统一改为 `1.12.0`。涉及的文件分布在 `core` 模块的主代码与测试代码中，改动方向高度一致：批量替换字符串字面量。同时作者区分了三类场景：真正提前移除版本的废弃 API、仅规范化版本号格式的提示信息、以及误用 `@Deprecated` 注解的测试代理类。

## 修改详情

### `core/src/main/java/org/apache/iceberg/LocationProviders.java` (+1/-1 lines)

**修改目的**：规范化废弃属性提示信息中的版本号格式。

**工作逻辑**：
将异常消息中的 `will be removed in 2.0` 改为 `will be removed in 2.0.0`。此处并非提前移除版本，而是将非标准写法 `2.0` 规范为语义化版本 `2.0.0`，保持与其他提示信息格式一致。该异常在用户使用 `DEPRECATED_PROPERTIES`（如 `write.folder-storage.path`、`write.object-storage.path`）时抛出，引导用户改用 `write.data.path`。

### `core/src/main/java/org/apache/iceberg/MetricsConfig.java` (+1/-1 lines)

**修改目的**：为 `fromProperties` 方法的废弃注释补充移除版本。

**工作逻辑**：
在 `@deprecated use {@link MetricsConfig#forTable(Table)}` 后追加 `Will be removed in 2.0.0`。此处是补充信息而非提前版本，但仍属于本提交统一废弃说明的工作。

### `core/src/main/java/org/apache/iceberg/SystemConfigs.java` (+2/-2 lines)

**修改目的**：将 `NETFLIX_UNSAFE_PARQUET_ID_FALLBACK_ENABLED` 配置项的移除版本从 2.0.0 提前到 1.12.0。

**工作逻辑**：
该配置项控制 Parquet 中不安全的回退 ID 分配机制，社区推荐使用 name mapping 替代。修改同时更新了 Javadoc `@deprecated` 注释和运行时 `LOG.warn` 告警消息中的版本号，确保用户在启用该配置时能立即看到新的移除时间点，从而及时迁移。

### `core/src/main/java/org/apache/iceberg/SystemProperties.java` (+1/-1 lines)

**修改目的**：将整个 `SystemProperties` 类的移除版本从 2.0.0 提前到 1.12.0。

**工作逻辑**：
该类已被 `SystemConfigs` 取代，类级别 Javadoc 的 `@deprecated` 注释版本号从 2.0.0 改为 1.12.0，表明整个类将在 1.12.0 被移除。

### `core/src/main/java/org/apache/iceberg/avro/AvroSchemaUtil.java` (+1/-1 lines)

**修改目的**：将废弃方法的移除版本从 2.0.0 提前到 1.12.0。

**工作逻辑**：
该方法被废弃并推荐使用 `applyNameMapping` 和 `pruneColumns(Schema, Set)` 替代，注释中的移除版本统一改为 1.12.0。

### `core/src/main/java/org/apache/iceberg/data/avro/RawDecoder.java` (+1/-1 lines)

**修改目的**：将废弃构造方法的移除版本从 2.0.0 提前到 1.12.0。

**工作逻辑**：
废弃的构造方法推荐使用 `create(org.apache.iceberg.Schema, Function, Schema)` 替代，注释版本号改为 1.12.0。

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java` (+3/-3 lines)

**修改目的**：将三个废弃方法的移除版本从 2.0 提前到 1.12.0。

**工作逻辑**：
涉及废弃的构造方法和 `wrapKey`、`unwrapKey` 两个方法。这三处原本标注 `will be removed in 2.0`（非标准写法），统一改为 `will be removed in 1.12.0`，既提前了移除版本，又规范化了版本号格式。

### `core/src/main/java/org/apache/iceberg/io/ContentCache.java` (+1/-1 lines)

**修改目的**：将废弃缓存方法的移除版本从 2.0.0 提前到 1.12.0。

**工作逻辑**：
该方法自 1.7.0 起废弃，因存在竞态条件仅做 best-effort 失效。注释中的移除版本从 2.0.0 改为 1.12.0。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+1/-1 lines)

**修改目的**：将废弃常量 `REST_PAGE_SIZE` 的移除版本从 2.0.0 提前到 1.12.0。

**工作逻辑**：
该常量推荐使用 `RESTCatalogProperties.PAGE_SIZE` 替代，注释版本号改为 1.12.0。

### `core/src/main/java/org/apache/iceberg/util/SnapshotUtil.java` (+1/-1 lines)

**修改目的**：将废弃方法 `newFilesBetween` 的移除版本从 2.0.0 提前到 1.12.0。

**工作逻辑**：
该旧方法推荐使用带 `FileIO` 参数的新版本 `newFilesBetween(Long, long, Function, FileIO)` 替代，注释版本号改为 1.12.0。

### `core/src/main/java/org/apache/iceberg/util/ThreadPools.java` (+4/-4 lines)

**修改目的**：将多个废弃线程池相关 API 的移除版本从 2.0.0 提前到 1.12.0。

**工作逻辑**：
涉及 `WORKER_THREAD_POOL_SIZE_PROP` 常量和两个 `getWorkerPool` 方法。这些 API 推荐使用 `SystemConfigs.WORKER_THREAD_POOL_SIZE` 配置项，以及 `newExitingWorkerPool` 或 `newFixedThreadPool` 方法替代。注释中版本号统一改为 1.12.0，同时调整了 Javadoc 格式。

### `core/src/test/java/org/apache/iceberg/TestLocationProvider.java` (+3/-3 lines)

**修改目的**：同步更新测试断言中的版本号字符串。

**工作逻辑**：
三处 `hasMessage` 断言中的版本号从 `2.0` 改为 `2.0.0`，与 `LocationProviders.java` 主代码中的格式变更保持一致，确保测试通过。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcTableConcurrency.java` (+2/-1 lines)

**修改目的**：修正测试代理类中对 JDK 方法的误标注废弃。

**工作逻辑**：
`setUnicodeStream` 是 `PreparedStatement` 接口中 JDK 自身已废弃的方法。原代码在测试代理类上额外标注了 `@Deprecated(since = "1.2")`，这是不正确的——Iceberg 不应自行声明 JDK 方法的废弃。改为 `@SuppressWarnings("deprecation")` 并添加注释 `// This is deprecated in JDK, we have to remove it once removed there.`，明确表明跟随 JDK 的废弃策略。

## 总结

本提交是 Iceberg 废弃 API 治理的一次集中推进，将一批原计划在 2.0.0 移除的废弃 API 提前到 1.12.0 移除，向下游用户发出更紧迫的迁移信号。虽然改动仅为字符串替换，但影响范围覆盖 Core 模块十余个核心类，且修正了测试代理类中对 JDK 废弃方法的误标注，体现了社区对 API 卫生（API hygiene）的重视。
