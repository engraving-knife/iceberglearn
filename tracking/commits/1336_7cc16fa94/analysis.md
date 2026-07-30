# 提交 1336：Revert "Build: Bump parquet from 1.13.1 to 1.14.3 (#11264)" (#11462)

## 提交信息

- **序号**：1336 / 4088
- **哈希**：7cc16fa94d7cd4e19397e9b4fba62185e0fa5eac
- **短哈希**：7cc16fa94
- **日期**：2024-11-04（Mon Nov 4 13:27:40 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Revert "Build: Bump parquet from 1.13.1 to 1.14.3 (#11264)" (#11462)
- **PR/Issue**：#11462（回退 #11264）
- **回退对象**：b8c2b20237bc9309d34dc96c473e9941d1b2ad58

## 总体目的

先前 PR #11264（提交 b8c2b20）将 `parquet` 依赖从 1.13.1 升级到 1.14.3。但社区随后在 Parquet 1.14.0/1/2/3 系列中发现了一个回归缺陷（参见 apache/parquet-java#3040，由 @pan3793 报告）。为了避免该缺陷影响 Iceberg 用户，本提交**直接 revert** 该升级，把 parquet 版本回退到 1.13.1，同时把此前因升级而调整的 3 个 `TestMetadataTableReadableMetrics` 测试中"列文件大小"期望值回退到 1.13.1 时序的数值。

需要回退测试期望值是因为 Parquet 1.14 在内部布局/统计计算上与 1.13.1 略有差异，导致同一份测试数据生成的 Parquet 文件 `fileSizeInBytes`、`recordCount`、`lowerBound` 等指标发生变化。回退 parquet 后必须把这些期望值同步回 1.13.1 时的数值，测试才能通过。

## 如何达成设计目的

使用 `git revert` 还原提交 b8c2b20 的所有改动：

1. `gradle/libs.versions.toml` 中 `parquet = "1.14.3"` 还原为 `parquet = "1.13.1"`。
2. 三处 `flink/v1.18|v1.19|v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java` 中此前被升级版本修改的"文件大小"等期望值，逐行还原。

这是纯版本回滚 + 测试期望值回滚，无新逻辑引入。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 parquet 依赖版本回退到 1.13.1。

**工作逻辑**：

```toml
parquet = "1.14.3"   # 旧
parquet = "1.13.1"   # 新（即回退后）
```

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`
### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`
### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`

**修改目的**：把此前因 Parquet 1.14 升级而调整的列文件大小等期望值还原回 1.13.1 时序数值。

**工作逻辑**：三个文件改动完全一致，均为对 `testReadableMetrics` / `testNestedValues` 中各类型列（binary、boolean、decimal、double、fixed、float、int、long、string，以及嵌套表的 leafDoubleCol、leafLongCol）的 `Row.of(...)` 中第一项（文件大小，单位字节）做数值回退。例如：

```java
// 升级到 1.14 时
Row booleanCol = Row.of(36L, 4L, 0L, null, false, true);
// 回退到 1.13.1 后
Row booleanCol = Row.of(32L, 4L, 0L, null, false, true);
```

其余类型列同样各自回到 1.13.1 的字节数。这是 Parquet 1.13 与 1.14 文件布局差异带来的字节数微调，回退后必须同步。

## 小结

- **成效**：撤销了引发回归缺陷的 Parquet 1.14.3 升级，把仓库 Parquet 依赖恢复到 1.13.1；Flink 三个版本的 `TestMetadataTableReadableMetrics` 期望值也同步回退，CI 可继续通过。
- **影响范围**：`gradle/libs.versions.toml` 一行版本回退 + 3 个 Flink 测试文件各 22 行（11 处文件大小数值 + 反向），共 4 个文件、+34/-34 行。无产品代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：1.4.x 作为维护分支，**很可能本就没有引入 Parquet 1.14 升级**（1.4.x 发布于 Parquet 1.13.x 时代）。回迁与否取决于 1.4.x 当前 parquet 版本：
  - 若 1.4.x 已是 1.13.1 或更早，则无需回迁本 revert（本就是 1.13 状态）。
  - 若 1.4.x 后续被 Dependabot 升级到 1.14.x，则应回迁本 revert 以规避上游缺陷。
  - 即便 1.4.x 已是 1.14.x，回迁本 revert 仍需注意是否同步回退 Flink 测试期望值（1.4.x 可能没有 v1.20 模块）。建议先确认 1.4.x 上的 parquet 实际版本与受影响测试再决定是否回迁。
