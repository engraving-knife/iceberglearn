# 提交 1730：Spark: Remove unused PruneColumnsWithReordering class. (#12258)

## 提交信息

- **序号**：1730 / 4088
- **哈希**：396dc440468da92130c70ed6b908a37678fca6cd
- **短哈希**：396dc4404
- **日期**：2025-02-14 09:53:05 +0530
- **作者**：Ryan Blue
- **提交说明**：Spark: Remove unused PruneColumnsWithReordering class. (#12258)
- **PR/Issue**：#12258

## 总体目的

`PruneColumnsWithReordering` 是一个 Spark 列裁剪优化相关的类，曾经用于在 Spark 读取 Iceberg 表时，结合列重排（reordering）逻辑来裁剪不需要读取的列，以减少 I/O 开销。然而，随着 Iceberg Spark 集成代码的演进和重构，该类已经不再被任何代码路径引用和使用，成为了一个死代码（dead code）。

保留未使用的代码会带来维护负担：它可能误导开发者以为该类仍在使用中，也可能在依赖升级或 API 变更时需要被不必要地同步修改。本提交的目标是清理这三个 Spark 版本（v3.3、v3.4、v3.5）中的 `PruneColumnsWithReordering` 类，减少代码库的冗余，提高可维护性。

## 如何达成设计目的

提交直接删除了三个 Spark 版本模块中各自的 `PruneColumnsWithReordering.java` 文件，共计删除 833 行代码。由于该类已无任何引用，删除操作不需要修改其他文件，也不会影响任何功能。三个版本中的文件内容基本一致（v3.3 为 275 行，v3.4 和 v3.5 各为 279 行），仅有少量版本兼容性差异。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/PruneColumnsWithReordering.java`（删除, -275 lines）

**修改目的**：移除 Spark 3.3 模块中未使用的 `PruneColumnsWithReordering` 类。

**工作逻辑**：该文件被整体删除。原类包含列裁剪与重排序的逻辑，使用 `Schema`、`Type`、`TypeUtil` 等 Iceberg 核心类型，以及 Spark 的 `StructType` 和 `Attribute` 类型。类中定义了构建投影 schema、处理嵌套类型裁剪等方法。由于该类不再被任何 Spark 优化规则或执行路径调用，整体删除是安全的。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/PruneColumnsWithReordering.java`（删除, -279 lines）

**修改目的**：移除 Spark 3.4 模块中未使用的 `PruneColumnsWithReordering` 类。

**工作逻辑**：与 v3.3 版本的文件类似，整体删除，无其他文件需要修改。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/PruneColumnsWithReordering.java`（删除, -279 lines）

**修改目的**：移除 Spark 3.5 模块中未使用的 `PruneColumnsWithReordering` 类。

**工作逻辑**：与上述两个版本一致，整体删除。

## 小结

- **成效**：成功清理了三个 Spark 版本模块中的死代码，共删除 833 行未使用的 `PruneColumnsWithReordering` 类，减少了代码库的维护负担。
- **影响范围**：仅涉及 Spark v3.3、v3.4、v3.5 三个模块的源码删除，不影响任何运行时功能，因为该类已无引用。
- **回迁到 1.4.x 的注意事项**：此提交为纯删除操作，无前置依赖，回迁风险极低。需确认 1.4.x 分支中该类确实未被引用（可能是不同的引用路径）。如果 1.4.x 分支中该类仍被使用，则不应回迁。建议先在 1.4.x 分支中搜索 `PruneColumnsWithReordering` 的引用情况再决定。
