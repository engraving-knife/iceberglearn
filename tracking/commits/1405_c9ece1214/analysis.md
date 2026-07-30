# 提交 1405：Spark 3.3: Deprecate support (#11596)

## 提交信息

- **序号**：1405 / 4088
- **哈希**：c9ece121446250643cb5e21133bef25522d69093
- **短哈希**：c9ece1214
- **日期**：2024-11-20（Wed Nov 20 10:40:48 2024 -0800）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Spark 3.3: Deprecate support (#11596)
- **PR/Issue**：#11596

## 总体目的

Iceberg 官方文档通过 `site/docs/multi-engine-support.md` 公开列出每个引擎版本当前所处的生命周期阶段：Beta、Maintained、Deprecated、End-of-life。其中：

- Spark 3.0、3.1、3.2 已 End of Life（最新支持版本分别停在 1.0.0、1.3.1、1.4.3）；
- Spark 3.3 在本提交之前标记为 `Maintained`。

社区决定把 Spark 3.3 的支持状态降级为 `Deprecated`，提示用户该版本不再被社区主动维护，建议迁移到更新的 Spark 版本。这是社区在 release 节奏上对老版本引擎的常规下线动作，便于集中维护资源到更新的 Spark / Flink 版本。

注意：本次只改文档表中 3.3 行的 `Lifecycle Stage` 单元格，把 `Maintained` 改为 `Deprecated`；3.3 的「Initial Iceberg Support（0.14.0）」「Latest Iceberg Support（{{ icebergVersion }}）」「Latest Runtime Jar」均保持不变，意味着当前 release 仍会发布 3.3 的 runtime jar，只是不再保证特性对齐与社区投入。

## 如何达成设计目的

直接编辑 `site/docs/multi-engine-support.md` 中 Spark 引擎版本生命周期表的 Spark 3.3 行，把第二列从 `Maintained` 改为 `Deprecated`。这与文档前文对生命周期阶段的定义保持一致：`Deprecated` 表示社区不再主动维护该版本，仍可由感兴趣的人自行从新版本回迁修复，但不再追求 feature parity，且贡献会逐步减少直至最终下线。

## 修改详情

### `site/docs/multi-engine-support.md`

**修改目的**：把 Spark 3.3 的生命周期阶段从「Maintained」变更为「Deprecated」。

**工作逻辑**：在 Apache Spark 生命周期表中，对 3.3 行：

```diff
-| 3.3        | Maintained         | 0.14.0                  | {{ icebergVersion }}   | [iceberg-spark-runtime-3.3_2.12](...) |
+| 3.3        | Deprecated         | 0.14.0                  | {{ icebergVersion }}   | [iceberg-spark-runtime-3.3_2.12](...) |
```

仅状态字段发生变化。表格其它行（2.4 / 3.0 / 3.1 / 3.2 / 3.4 / 3.5）保持原样。`{{ icebergVersion }}` 是 mkdocs 模板变量，在构建站点时替换为当前 release 版本号。

## 小结

- **成效**：文档明确告知用户 Spark 3.3 已进入 Deprecated 阶段，社区不再主动维护；用户应规划迁移到 Spark 3.4 / 3.5；同时本提交不删除任何代码，3.3 模块在仓库中仍然存在并参与构建与发布。
- **影响范围**：单文件单行文档变更，无代码与构建影响。
- **回迁到 1.4.x 的注意事项**：1.4.x 仍以 Spark 3.3 / 3.4 / 3.5 为支持的 Spark 版本之一，**不应**把 Spark 3.3 标记为 Deprecated 回迁到 1.4.x（1.4.x 是历史 release 分支，其支持的引擎版本在发布时已确定，文档状态变更属于 main 分支面向未来 release 的策略性声明）。即使要同步，也只影响展示层（站点文档），不影响 1.4.x 自身的代码或 jar 产物。
