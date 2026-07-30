# 提交 1772：Flink: Fix the comment error in SketchDataStatistics (#12375)

## 提交信息

- **序号**：1772 / 4088
- **哈希**：ebc9fbc0995ad4cd324cf0d6e23984bd87c26aaf
- **短哈希**：ebc9fbc09
- **日期**：2025-02-23 19:08:47 -0800
- **作者**：GuoYu
- **提交说明**：Flink: Fix the comment error in SketchDataStatistics (#12375)
- **PR/Issue**：#12375

## 总体目的

这个提交修复了 Flink 模块中 `SketchDataStatistics` 类的 Javadoc 注释错误。原先的类注释写的是 "MapDataStatistics uses map to count key frequency"（MapDataStatistics 使用 map 来统计键频率），但这个类实际上是 `SketchDataStatistics`，使用的是蓄水池抽样算法（reservoir sampling）来统计键频率，而不是使用 map。

这个注释错误很可能是在代码重构或复制粘贴时遗留的，注释内容描述的是另一个类（`MapDataStatistics`）的实现方式，而非当前类（`SketchDataStatistics`）的实际行为。该提交将注释更正为正确描述当前类所使用的算法。

## 如何达成设计目的

提交通过修改三个 Flink 版本（v1.18、v1.19、v1.20）中对应的 `SketchDataStatistics.java` 文件的类级 Javadoc 注释来实现。由于 Iceberg 项目为不同的 Flink 版本维护了独立的代码副本，同一处修改需要在三个版本目录中分别应用。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchDataStatistics.java`（修改, +1/-1 lines）

**修改目的**：修正类注释，使其正确描述 SketchDataStatistics 的实现方式。

**工作逻辑**：
- 将类注释从 `/** MapDataStatistics uses map to count key frequency */` 修改为 `/** SketchDataStatistics uses reservoir sampling algorithm to count key frequency */`。
- 新注释正确指出了类名（SketchDataStatistics）和所使用的算法（reservoir sampling algorithm，蓄水池抽样算法），与类中实际使用的 `ReservoirItemsSketch<SortKey>` 字段一致。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchDataStatistics.java`（修改, +1/-1 lines）

**修改目的**：同上，修正 Flink 1.19 版本中的同一处注释错误。

**工作逻辑**：与 v1.18 完全相同的修改。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SketchDataStatistics.java`（修改, +1/-1 lines）

**修改目的**：同上，修正 Flink 1.20 版本中的同一处注释错误。

**工作逻辑**：与 v1.18 完全相同的修改。

## 小结

- **成效**：成功修正了三个 Flink 版本中 SketchDataStatistics 类的注释错误，使注释正确反映了类名和所使用的蓄水池抽样算法。
- **影响范围**：仅影响 Flink sink shuffle 模块的文档注释，不影响任何运行时逻辑和功能。
- **回迁到 1.4.x 的注意事项**：可选回迁。纯注释修复，无功能影响，回迁优先级低。如需回迁，需确认 1.4.x 分支中对应 Flink 版本的 SketchDataStatistics.java 文件存在，并逐版本修改。
