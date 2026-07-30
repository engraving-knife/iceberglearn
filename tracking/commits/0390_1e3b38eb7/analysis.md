# 提交 0390：Spark: backport #8656 and update docs (#9512)

## 提交信息

- **序号**：0390
- **哈希**：1e3b38eb73804e7901e2382c6fdb0dd3d3a77eda
- **短哈希**：1e3b38eb7
- **日期**：2024-01-19（Fri Jan 19 12:32:01 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Spark: backport #8656 and update docs (#9512)
- **PR/Issue**：#9512（backport 自 #8656）

## 总体目的

这个提交完成一次"弃用到期清理 + 跨版本对齐"工作。背景：在 1.4.0 里，`create_changelog_view` procedure 上的 `remove_carryovers` 参数被标记为 `@Deprecated`，弃用说明里写明"will be removed in 1.5.0"，理由是该 procedure 默认就应该移除 carry-over 行；如果用户真的想看 carry-over 行，应该改用 `SparkChangelogTable`（changelog table 不会做 carry-over 移除）。原始清理 PR #8656 只在 Spark 3.5 上做了真正的代码删除，而 3.3 与 3.4 两个维护分支上仍保留着 `@Deprecated` 的参数实现，文档里也仍把 `remove_carryovers` 列为可用参数。

本提交做的事情就是：
1. 把 #8656 的实际清理（删参数、删辅助方法、强制总是 removeCarryoverRows）backport 到 Spark 3.3 与 3.4 两个老分支，让三个 Spark 版本的 `CreateChangelogViewProcedure` 行为一致——都"总是移除 carry-over 行"。
2. 同步更新官方文档 `docs/spark-procedures.md`，删掉 `remove_carryovers` 这一行参数说明，避免用户继续看到一个已经不存在的参数。
3. 顺带把 3.5 上 `CreateChangelogViewProcedure` 的 Javadoc 与 3.3/3.4 对齐（3.5 此前代码已经清理过，但 Javadoc 措辞还是旧的"removes by default, set remove_carryovers false to keep"，本次统一改为"always removes, please query SparkChangelogTable"）。

设计意图是"言行一致 + 跨版本一致"：既然 1.5.0 已到，弃用窗口结束，所有维护分支都应进入"参数真正不存在"的状态，文档也必须同步，否则用户照着文档传 `remove_carryovers => false` 会被引擎拒绝（参数未定义），产生困惑。

第二层意图是引导用户到更合适的替代品 `SparkChangelogTable`：carry-over 行本身是 changelog 模型里的概念，要看原始未去重的 changelog 应该查 changelog table，而不是给一个"为下游 CDC 消费者净化视图"的 procedure 加开关——这种语义混淆正是当初弃用它的根本原因。

## 如何达成设计目的

实现路径分三类文件：
- **procedure 主代码（3.3 / 3.4）**：删除 `REMOVE_CARRYOVERS_PARAM` 字段、从 `PARAMETERS` 数组里摘掉它、删除 `shouldRemoveCarryoverRows` 辅助方法、把原先 `else if (shouldRemoveCarryoverRows(input))` 的条件分支改成无条件 `else`，让 `removeCarryoverRows` 总是被调用（除非走了 `computeUpdateImages` 那条分支）；同时更新类级 Javadoc。
- **procedure 主代码（3.5）**：只改类级 Javadoc 措辞，因为代码层面 3.5 早已清理。
- **测试（3.3 / 3.4）**：删除 `testWithCarryovers` 与 `testNotRemoveCarryOvers` 两个用例，因为它们都依赖 `remove_carryovers => false` 这一已不存在的参数。
- **文档**：从 `spark-procedures.md` 的参数表里删掉 `remove_carryovers` 那一行。

## 修改详情

### docs/spark-procedures.md

**修改目的**：从 `create_changelog_view` 的参数表中移除 `remove_carryovers` 行，让文档与代码一致。

**工作逻辑**：删除表格中 `| remove_carryovers | | boolean | Whether to remove carry-over rows ... Defaults to true. Deprecated since 1.4.0, will be removed in 1.5.0; Please query SparkChangelogTable to view carry-over rows. |` 这一行。文档现在只列 `net_changes`、`compute_updates`、`identifier_columns` 三个可选参数，与 procedure 实际暴露的 `ProcedureParameter` 数组完全对应。

### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/procedures/CreateChangelogViewProcedure.java
### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/CreateChangelogViewProcedure.java

**修改目的**：在 3.3 / 3.4 上完成 #8656 在 3.5 上做过的清理——彻底删除 `remove_carryovers` 参数及其实现路径，让 procedure 总是移除 carry-over 行。两个文件改动完全一致。

**工作逻辑**：
- 类级 Javadoc 从 "removes the carry-over rows by default. If you want to keep them, you can set \"remove_carryovers\" to be false in the options." 改为 "always removes the carry-over rows. Please query {@link SparkChangelogTable} instead when carry-over rows are required."——措辞从"默认开、可关"变成"总是开、要原始数据请用 changelog table"。
- 删除 `REMOVE_CARRYOVERS_PARAM` 字段及其上方的 `@Deprecated` Javadoc 块（原 Javadoc 写明 "since 1.4.0, will be removed in 1.5.0"）。
- 从 `PARAMETERS` 数组里移除 `REMOVE_CARRYOVERS_PARAM` 一项，于是 procedure 不再接受这个参数；用户若仍传 `remove_carryovers => ...`，Spark 在解析调用时会因未知参数报错。
- 关键行为改动：原逻辑
  ```java
  if (shouldComputeUpdateImages(input)) {
      ...
  } else if (shouldRemoveCarryoverRows(input)) {
      df = removeCarryoverRows(df, netChanges);
  }
  ```
  改为
  ```java
  if (shouldComputeUpdateImages(input)) {
      ...
  } else {
      df = removeCarryoverRows(df, netChanges);
  }
  ```
  即 `else if` 变 `else`。原本当 `remove_carryovers=false` 时会跳过 `removeCarryoverRows`，留下 carry-over 行；现在无条件进入 `removeCarryoverRows`（仅在 `computeUpdateImages` 路径下不进，因为 update image 计算本身已隐含 carry-over 处理）。这正是"procedure 总是移除 carry-over"的语义落地。
- 删除 `shouldRemoveCarryoverRows(ProcedureInput)` 私有方法（原实现 `return input.asBoolean(REMOVE_CARRYOVERS_PARAM, true);`），因为没有调用方了。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/CreateChangelogViewProcedure.java

**修改目的**：把 3.5 上的类级 Javadoc 与 3.3/3.4 统一。

**工作逻辑**：只改 Javadoc 两行，措辞与 3.3/3.4 完全相同——从"默认移除、可设 false 保留"改为"总是移除、需 carry-over 请查 SparkChangelogTable"。3.5 的代码主体（参数定义、`else` 分支等）在 #8656 里早已清理，本提交不动这部分，仅同步文档措辞，避免三个版本 Javadoc 不一致给维护者造成困惑。

### spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCreateChangelogViewProcedure.java
### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCreateChangelogViewProcedure.java

**修改目的**：移除依赖 `remove_carryovers => false` 参数的测试用例，因为该参数已不存在。两个文件改动完全一致。

**工作逻辑**：删除两个 `@Test` 方法：
- `testWithCarryovers`：建两列表，三次写入（INSERT, INSERT, INSERT OVERWRITE 产生 carry-over），调 `create_changelog_view(remove_carryovers => false, table => ...)`，断言视图含 carry-over 行（DELETE + INSERT 同值）。整个用例的前提是"参数可用且设为 false"，参数一删，用例无意义。
- `testNotRemoveCarryOvers`：建三列表，构造一个 carry-over 行 `(2,'e',12)`，同样用 `remove_carryovers => false` 调用并断言 carry-over 行出现在结果里。删除原因同上。

这两个测试原本是验证"参数生效"的契约测试；参数移除后，等价的"想看 carry-over 行"语义已经被引导到 `SparkChangelogTable`，本提交没有为 changelog table 加新测试（因为那是另一条独立路径，已有自己的测试覆盖），只做减法。

## 小结

这是一个标准的"弃用窗口到期清理"提交，模式很清晰：
1. **到期删除**：1.4.0 标记弃用、承诺 1.5.0 移除的 `remove_carryovers` 参数，在 1.5.0 真正移除；
2. **跨版本对齐**：把已经在 3.5 做过的清理 backport 到 3.3 / 3.4，三个 Spark 版本行为统一；
3. **文档同步**：参数表里删掉对应行，避免文档误导；
4. **测试同步**：删掉验证旧参数的测试，加法留给新路径（SparkChangelogTable）已有的覆盖；
5. **Javadoc 统一**：3.5 顺手对齐措辞。

提交本身只做减法（净删 187 行、加 8 行），但意义在于把一个语义混乱的开关彻底从 procedure API 里拿掉，让 `create_changelog_view` 专注于"为下游 CDC 消费者产出净化后的 changelog 视图"这一职责，而把"原始 changelog（含 carry-over）"的查询需求明确路由到 `SparkChangelogTable`。这是一种通过减少 API 表面积来提升 API 清晰度的典型重构。
