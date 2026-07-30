# 提交 0687：Spark 3.5: Add threshold for failed commits in data rewrites

## 提交信息
- **序号**：0687 / 4088
- **哈希**：78e8204c53286748ca96dd2cb1d5f7ca899f82f1
- **短哈希**：78e8204c5
- **日期**：2024-04-16 05:06:23 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.5: Add threshold for failed commits in data rewrites (#9611)
- **PR/Issue**：#9611

## 总体目的

本提交为 Spark 3.5 的 `RewriteDataFiles` 动作新增"失败提交阈值"配置项，让用户可以更好地控制当启用部分进度（partial progress）时，重写操作在出现多少次提交失败时应当整体失败。

**背景与问题**：在启用 `partial-progress.enabled=true` 时，原有的"部分进度"机制会将整个重写工作拆分为多个较小的提交，单个提交失败不会中止整个动作，只会在结果中通过 `rewriteFailures` 字段返回失败信息。然而：

1. 当几乎所有提交都失败时，原实现仅在 `commitResults.size() == 0`（即零成功提交）时打印一条 `LOG.error` 日志，但不抛出异常。这导致上层调用者难以察觉"实质上重写没有完成任何有效工作"的情况，仍以为动作成功。
2. 用户没有细粒度的控制：要么完全允许失败（默认行为），要么必须禁用 partial-progress 才能让任何失败都直接抛错。缺少"允许少量失败、但失败超过一定数量即抛错"的中间策略。

**目标**：引入 `partial-progress.max-failed-commits` 配置项，允许用户设定一个阈值。当实际失败的提交数量超过该阈值时，动作主动抛出 `RuntimeException`，提示用户去查看日志或调大 `max-commits` 把重写切得更细。默认值与 `max-commits` 相等，从而保持向后兼容（即默认仍允许所有提交失败）。

## 如何达成设计目的

设计思路分为三步：

1. **在 API 层暴露新的配置键**：在 `RewriteDataFiles` 接口中新增 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS` 常量，作为公共契约，所有实现（Spark、Flink 等）均可识别。
2. **在核心层统计成功提交数**：`BaseCommitService` 是 Spark/Flink 通用基类，在每次成功调用 `commitOrClean(batch)` 后递增 `succeededCommits`，并暴露 `succeededCommits()` 查询方法。这样上层无需关心 `results()` 的具体内容，只需要一个简单的差值 `maxCommits - succeededCommits` 即可得到失败次数。
3. **在 Spark 3.5 动作层加入阈值判断**：`RewriteDataFilesSparkAction.doExecuteWithPartialProgress` 在 `commitService.close()` 之后：
   - 计算 `failedCommits = maxCommits - commitService.succeededCommits()`；
   - 当 `0 < failedCommits <= maxFailedCommits` 时，仅打印警告日志，仍然返回正常结果（保持"部分成功"语义）；
   - 当 `failedCommits > maxFailedCommits` 时，抛出带详细说明的 `RuntimeException`，建议用户调大 `PARTIAL_PROGRESS_MAX_COMMITS`。

这一策略将"统计"放在最底层的 `BaseCommitService`，将"判断与决策"放在各引擎的具体动作中，让其它实现（如 Flink 后续若启用 partial-progress）也能复用 `succeededCommits()` 计数，而无需重复实现。

默认值 `maxFailedCommits = maxCommits` 是关键设计点：它保证现有用户在未显式配置新参数时，行为与原来完全一致（最多失败 maxCommits 次，全部失败也仅日志告警），从而实现向后兼容的平滑引入。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/RewriteDataFiles.java`

**修改目的**：在公共 API 契约中新增 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS` 配置键及文档说明。

**工作逻辑**：在已有的 `PARTIAL_PROGRESS_MAX_COMMITS_DEFAULT` 常量下方新增：

```java
/**
 * The maximum amount of failed commits that this rewrite is allowed if partial progress is
 * enabled. By default, all commits are allowed to fail. This setting has no effect if partial
 * progress is disabled.
 */
String PARTIAL_PROGRESS_MAX_FAILED_COMMITS = "partial-progress.max-failed-commits";
```

文档明确说明：默认允许所有提交失败；当 partial-progress 未启用时该参数无效。

### `core/src/main/java/org/apache/iceberg/actions/BaseCommitService.java`

**修改目的**：为提交服务增加"成功提交计数"能力，供上层判断失败次数。

**工作逻辑**：
- 新增字段 `private int succeededCommits = 0;`
- 在 `commitOrClean(batch)` 成功返回后（即 `committedRewrites.addAll(batch)` 之后）立即 `succeededCommits++;`，确保只在真正提交成功时才计数。
- 新增公共方法 `public int succeededCommits()` 返回该值。

注意：原 catch 块只是 `LOG.error("Failure during rewrite commit process, partial progress enabled. Ignoring", e);`，仍然吞掉异常继续循环——即单次失败不会中止服务，但也不会增加计数。这正是差值算法 `maxCommits - succeededCommits` 能够正确反映失败次数的关键。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`

**修改目的**：在 Spark 3.5 的 partial-progress 执行路径中实现基于阈值的失败决策，并读取新配置项。

**工作逻辑**：

1. 在 `VALID_OPTIONS` 集合中新增 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS`，使其成为合法可识别的选项，避免被 `validateAndInitOptions` 拒绝。
2. 新增字段 `private int maxFailedCommits;`。
3. 在 `validateAndInitOptions()` 中读取配置，默认值为 `maxCommits`：
   ```java
   maxFailedCommits =
       PropertyUtil.propertyAsInt(options(), PARTIAL_PROGRESS_MAX_FAILED_COMMITS, maxCommits);
   ```
   这一步保证了向后兼容——用户不配置时，`maxFailedCommits` 等于 `maxCommits`，即"允许所有提交失败"。
4. 在 `doExecuteWithPartialProgress` 中替换原本只判断 `commitResults.size() == 0` 的简单日志逻辑：
   - **修改前**：当所有提交都失败时仅打印 `LOG.error`，不抛异常，调用方拿到一个空 `rewriteResults` 的成功结果。
   - **修改后**：
     ```java
     int failedCommits = maxCommits - commitService.succeededCommits();
     if (failedCommits > 0 && failedCommits <= maxFailedCommits) {
       LOG.warn(...);  // 警告，但继续返回结果
     } else if (failedCommits > maxFailedCommits) {
       throw new RuntimeException(errorMessage);  // 抛异常，提示调大 max-commits
     }
     ```
5. 顺手把"从 List 转 Iterable<FileGroupRewriteResult>"的逻辑抽取为 `toRewriteResults` 私有方法，使结果构建链路更清晰。

阈值消息中明确包含 `PARTIAL_PROGRESS_ENABLED`、`failedCommits`、`maxFailedCommits`、`PARTIAL_PROGRESS_MAX_COMMITS`，便于用户在日志中快速定位和给出修复建议。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`

**修改目的**：新增针对新阈值的端到端测试，并澄清既有测试的注释。

**工作逻辑**：
- 修正了既有 `testParallelPartialProgress` 测试中关于"10 original groups and Max Commits of 3"的注释表达，使其更清晰：4 个一组分成 3 次提交，去掉 3 个组后剩 4+3 两组在两次提交里完成。
- 新增 `testParallelPartialProgressWithMaxFailedCommits` 测试：
  - 表 20 条记录，`MAX_CONCURRENT_FILE_GROUP_REWRITES=3`，`PARTIAL_PROGRESS_MAX_COMMITS=3`，`PARTIAL_PROGRESS_MAX_FAILED_COMMITS=0`；
  - 通过 Mockito spy 让 `rewriteFiles` 在 group 1、3、7 时抛 `RuntimeException`，从而制造至少一个失败的提交；
  - 断言：执行时抛出 `RuntimeException`，消息包含 `"1 rewrite commits failed. This is more than the maximum allowed failures of 0"`；
  - 同时断言：数据未发生变化（`assertEquals(originalData, postRewriteData)`），仍然有 3 个快照、无孤儿文件、缓存干净。

该测试覆盖了"maxFailedCommits=0 时只要有失败即抛错"的边界场景，验证了新参数的最严格行为。

## 小结
- **成效**：成功引入可配置的失败提交阈值，向后兼容默认行为，并提供细粒度的失败控制策略。设计层次清晰：API 暴露常量 → 核心层统计 → 引擎层决策。
- **影响范围**：
  - 公共 API `RewriteDataFiles` 接口（影响所有引擎实现）。
  - `core` 模块的 `BaseCommitService`（影响所有继承它的提交服务实现，包括 Flink/MR 后续若使用）。
  - Spark 3.5 的 `RewriteDataFilesSparkAction`（仅 v3.5 受影响，其它 Spark 版本和 Flink 暂未同步改动）。
- **回迁到 1.4.x 的注意事项**：
  1. 该改动同时触及 `api`、`core`、`spark/v3.5` 三个模块，回迁时需要整体回迁以保证一致性；不能只回迁 spark 模块，否则 `PARTIAL_PROGRESS_MAX_FAILED_COMMITS` 常量和 `succeededCommits()` 方法缺失会导致编译失败。
  2. 需确认 1.4.x 中的 `BaseCommitService` 结构与 main 一致（特别是 `commitOrClean` 调用位置）；若 1.4.x 在该路径上有差异（例如重写过程不再使用 `BaseCommitService`），需要相应调整统计逻辑。
  3. 默认值策略必须保持 `maxFailedCommits = maxCommits`，否则会改变现有用户的行为，可能造成已上线的重写作业突然开始抛异常。
  4. 若 1.4.x 同时维护其它 Spark 版本（如 v3.3、v3.4），可考虑同步回迁以保持各 Spark 版本特性一致；但若仅作为 hotfix 回迁，至少要保证 v3.5 内一致性。
  5. 测试 `testParallelPartialProgressWithMaxFailedCommits` 依赖 Mockito spy 与 GroupInfoMatcher，回迁时确认这些测试基础设施在 1.4.x 中可用。
