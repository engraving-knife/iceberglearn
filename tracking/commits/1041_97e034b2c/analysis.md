# 提交 1041：Flink: Remove deprecated RowDataUtil.clone method (#10902)

## 提交信息

- **序号**：1041 / 4088
- **哈希**：97e034b2cec9408a6f792c410a8eb8dddb452e14
- **短哈希**：97e034b2c
- **日期**：2024-08-07 16:08:16 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Flink: Remove deprecated RowDataUtil.clone method (#10902)
- **PR/Issue**：#10902

## 总体目的

Iceberg 的 Flink 集成模块中，`RowDataUtil` 类提供 `clone` 方法用于复制 Flink 的 `RowData`。此前该类存在两个 `clone` 重载：

1. `clone(RowData from, RowData reuse, RowType rowType, TypeSerializer[] fieldSerializers)` —— 旧版本，**不接收** `RowData.FieldGetter[]` 参数。该方法在内部每次调用都会动态构造一个 `FieldGetter` 数组（对每个非 null 字段调用 `RowData.createFieldGetter`），无法复用 `FieldGetter`，存在性能损耗。
2. `clone(RowData from, RowData reuse, RowType rowType, TypeSerializer[] fieldSerializers, RowData.FieldGetter[] fieldGetters)` —— 新版本，**接收**调用方预构造的 `FieldGetter[]`，可在多次 clone 之间复用，性能更优。

旧方法（重载 1）早已被标注 `@Deprecated`，其 Javadoc 明确写道"will be removed in 1.7.0; Not reusing FieldGetter in this method could lead to performance degradation, use {@link #clone(..., RowData.FieldGetter[])} instead."。即计划在 Iceberg 1.7.0 移除。

本提交的目的是按计划在 1.7.0（main 分支开发中的版本）移除这个已废弃的旧 `clone` 重载，推动调用方迁移到接收 `FieldGetter[]` 的新版本，消除性能陷阱。提交说明中也明确指出"Scheduled for removal in 1.7.0."。

## 如何达成设计目的

在 Flink 各版本子模块（v1.18、v1.19、v1.20）的 `RowDataUtil.java` 中删除旧的 `clone` 重载方法及其 `@Deprecated` Javadoc。由于该方法已废弃且社区调用方应已迁移到新重载，删除后不影响编译（前提是没有残留调用）。三个 Flink 版本模块的修改完全一致，因为 `RowDataUtil` 在三个版本间是同步维护的。

注意：v1.17 模块已在提交 1034 中删除，所以本提交不涉及 v1.17。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/data/RowDataUtil.java`

**修改目的**：移除已废弃的旧 `clone(RowData, RowData, RowType, TypeSerializer[])` 重载。

**工作逻辑**：删除文件末尾的 `@Deprecated` 方法及其 Javadoc，共 19 行：

```java
  /**
   * @deprecated will be removed in 1.7.0; Not reusing FieldGetter in this method could lead to
   *     performance degradation, use {@link #clone(RowData, RowData, RowType, TypeSerializer[],
   *     RowData.FieldGetter[])} instead.
   */
  @Deprecated
  public static RowData clone(
      RowData from, RowData reuse, RowType rowType, TypeSerializer[] fieldSerializers) {
    RowData.FieldGetter[] fieldGetters = new RowData.FieldGetter[rowType.getFieldCount()];
    for (int i = 0; i < rowType.getFieldCount(); ++i) {
      if (!from.isNullAt(i)) {
        fieldGetters[i] = RowData.createFieldGetter(rowType.getTypeAt(i), i);
      }
    }

    return clone(from, reuse, rowType, fieldSerializers, fieldGetters);
  }
```

删除后，类中仅保留接收 `FieldGetter[]` 的新 `clone` 重载。类的其余部分不变。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/data/RowDataUtil.java`

**修改目的**：与 v1.18 同步移除废弃的旧 `clone` 重载。

**工作逻辑**：删除内容与 v1.18 完全一致（同样的 19 行方法及 Javadoc）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/RowDataUtil.java`

**修改目的**：与 v1.18、v1.19 同步移除废弃的旧 `clone` 重载。

**工作逻辑**：删除内容与 v1.18、v1.19 完全一致（同样的 19 行方法及 Javadoc）。

## 小结

- **成效**：按计划移除了 `RowDataUtil` 中标注 `@Deprecated` 且计划在 1.7.0 删除的旧 `clone` 重载，推动调用方使用可复用 `FieldGetter[]` 的新重载，消除每次调用都重建 `FieldGetter` 数组的性能损耗。三个 Flink 版本模块（1.18/1.19/1.20）同步修改。
- **影响范围**：3 个文件（`flink/v1.18`、`flink/v1.19`、`flink/v1.20` 下的 `RowDataUtil.java`），每个文件删除 19 行（含 Javadoc），共约 57 行删除。仅涉及 Flink 集成模块，不影响其他引擎集成。
- **回迁到 1.4.x 的注意事项**：**不应回迁**。该旧 `clone` 重载计划在 1.7.0 移除，而 1.4.x 是更早的维护分支（对应 1.4.x 版本），其 API 兼容性承诺仍包含此废弃方法。如果在 1.4.x 上提前删除，会破坏下游依赖该重载的代码（即使已废弃，仍可能有外部调用方），违反 1.4.x 的 API 兼容性。此删除应只在 main（1.7.0+）进行。此外，1.4.x 分支可能没有 v1.20 模块，cherry-pick 时该文件的改动需要按 1.4.x 实际拥有的 Flink 版本模块调整。
