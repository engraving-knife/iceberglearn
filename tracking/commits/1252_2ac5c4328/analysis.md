# 提交 1252：Flink: make FLIP-27 default in SQL and mark the old FlinkSource as deprecated (#11345)

## 提交信息

- **序号**：1252 / 4088
- **哈希**：2ac5c43284a65a9fb53438c310f15a4a362f925e
- **短哈希**：2ac5c4328
- **日期**：2024-10-18（Fri Oct 18 09:06:57 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: make FLIP-27 default in SQL and mark the old FlinkSource as deprecated (#11345)
- **PR/Issue**：#11345

## 总体目的

Flink 社区在 1.20 版本中正式把基于 FLIP-27（Refactor Source Interface）的新版 Source 接口确立为推荐方向，旧的 `SourceFunction` 接口自 2023 年 8 月起已在 Flink 内部被标记为废弃。Iceberg 的 Flink 集成同时维护两套 Source 实现：基于旧 `SourceFunction` 的 `FlinkSource`，以及基于新版 FLIP-27 接口的 `IcebergSource`。此前 Iceberg 在 SQL 中默认使用旧的 `FlinkSource`，需用户显式开启 `table.exec.iceberg.use-flip27-source=true` 才能切换到新实现。

本提交完成两件事：一是把 Flink 1.20 集成中 `table.exec.iceberg.use-flip27-source` 的默认值由 `false` 改为 `true`，使新版 `IcebergSource`（FLIP-27）成为 SQL 的默认 Source；二是为旧的 `FlinkSource` 类（覆盖 Flink 1.18/1.19/1.20 三个版本）添加 `@Deprecated` 注解与 Javadoc 说明，明确告知用户该类自 Iceberg 1.7.0 起废弃、将在 2.0.0 移除，应改用 `IcebergSource`。同时移除了 `IcebergSource` 类上的 `@Experimental` 注解，表明新 Source 已从实验阶段转为稳定状态。文档同步更新，说明默认值随 Flink 版本不同而不同（1.19 及以下为 false，1.20 及以上为 true），并指导用户如何 opt-out。

## 如何达成设计目的

通过三组改动协同完成：

1. **配置默认值翻转**：在 `flink/v1.20` 的 `FlinkConfigOptions.java` 中将 `TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE` 的 `defaultValue(false)` 改为 `defaultValue(true)`。注意仅改动 1.20 模块，1.18/1.19 模块保持 false，体现「按 Flink 版本区分默认值」的策略。

2. **旧实现标记废弃**：在三个版本（1.18/1.19/1.20）的 `FlinkSource.java` 顶部新增 `@Deprecated` 注解及 Javadoc，说明废弃版本、移除版本与替代方案，并显式 import `SourceFunction` 以便 Javadoc 链接生效。

3. **新实现去掉实验标记**：在三个版本的 `IcebergSource.java` 中移除 `@Experimental` 注解及其 import，宣告新 Source 接口已稳定可用。

4. **文档更新**：`flink-queries.md` 把原「opt in」示例改为「opt out」示例，并说明默认值随 Flink 版本而异。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkConfigOptions.java`

**修改目的**：将 FLIP-27 Source 设为 Flink 1.20 的默认实现。

**工作逻辑**：将配置项 `table.exec.iceberg.use-flip27-source` 的默认值从 `false` 改为 `true`。该配置由 Iceberg 的 Flink SQL 集成读取，决定加载表时使用 `FlinkSource`（旧）还是 `IcebergSource`（新 FLIP-27）。改动后，Flink 1.20 用户在 SQL 中查询 Iceberg 表时默认走新版 Source，无需额外配置。

### `flink/v11{8,9,20}/flink/src/main/java/org/apache/iceberg/flink/source/FlinkSource.java`（三个版本相同改动）

**修改目的**：标记旧 `FlinkSource` 为废弃，引导用户迁移。

**工作逻辑**：新增 import `org.apache.flink.streaming.api.functions.source.SourceFunction`；在类声明上方添加 Javadoc 与 `@Deprecated` 注解：

```java
/**
 * Flink source builder for old {@link SourceFunction} implementation.
 *
 * @deprecated since 1.7.0, will be removed in 2.0.0. Use {@link IcebergSource} instead, which
 *     implement the newer FLIP-27 source interface. This class implements the old {@link
 *     SourceFunction} that has been marked as deprecated in Flink since Aug 2023.
 */
@Deprecated
public class FlinkSource {
```

（注：1.20 版本的 Javadoc 起头多出一行 `/**`，是合并时的轻微笔误，不影响注解功能。）

### `flink/v1{1.18,1.19,1.20}/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`（三个版本相同改动）

**修改目的**：移除新 Source 的实验性标记，表明其已稳定。

**工作逻辑**：删除 `import org.apache.flink.annotation.Experimental;` 以及类上的 `@Experimental` 注解。这意味着 `IcebergSource` 不再被标注为 Flink 实验性 API，对外释放「可放心使用」的信号。

### `docs/docs/flink-queries.md`

**修改目的**：更新 SQL 使用文档以反映默认值变化。

**工作逻辑**：将原「Opt in the FLIP-27 source. Default is false. SET ... = true;」改为「Opt out the FLIP-27 source. Default is false for Flink 1.19 and below, and true for Flink 1.20 and above. SET ... = false;」，并调整段落顺序，先给出配置项说明，再补充「其余 SQL 设置对新旧 Source 均适用」。

## 小结

- **成效**：Flink 1.20 的 Iceberg SQL 默认使用基于 FLIP-27 的新版 `IcebergSource`，旧 `FlinkSource` 被正式标记废弃（1.7.0 起废弃、2.0.0 移除），新 Source 移除实验性标记转为稳定，文档同步说明默认值随 Flink 版本而异及如何 opt-out。
- **影响范围**：仅 Flink 集成模块（1.18/1.19/1.20）的 6 个 Java 文件与 1 个文档文件，共约 35 行增改，无核心或 API 模块改动。属于行为默认值与 API 标注层面的变更，不改变任何持久化格式或协议。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支早于此次默认值翻转，其 Flink 集成默认仍使用旧 `FlinkSource`。此改动涉及 Iceberg 1.7.0 的废弃声明与 Flink 1.20 的默认值，**不建议回迁**到 1.4.x：一方面 1.4.x 维护周期内引入默认行为变更会破坏存量用户兼容性；另一方面 `@Deprecated` 声明的版本号（since 1.7.0）与 1.4.x 不符，回迁会造成版本语义混乱。若 1.4.x 确需修复 FLIP-27 相关 bug，应单独评估具体补丁，而非整体回迁本提交。
