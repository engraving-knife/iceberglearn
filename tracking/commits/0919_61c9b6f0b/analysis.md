# 提交 0919：Flink: Backport #10565 to v1.18 and v1.19 (#10676)

## 提交信息

- **序号**：0919 / 4088
- **哈希**：61c9b6f0bac595be2fe7d291bffe72ab8b585936
- **短哈希**：61c9b6f0b
- **日期**：2024-07-11（Thu Jul 11 03:46:38 2024 +0800）
- **作者**：fengjiajie <fengjiajie@sensorsdata.cn>
- **提交说明**：Flink: Backport #10565 to v1.18 and v1.19 (#10676)
- **PR/Issue**：#10676（回迁 #10565）

## 总体目的

本提交是序号 0914（#10565）的回迁移（backport）。#10565 此前只针对 `flink/v1.17` 模块做了 `RowDataUtil.clone(...)` 的性能优化——把 `RowData.FieldGetter` 的构造从"每行每字段一次"前移到"`RowDataRecordFactory` 初始化时一次性构造并复用"。但 Iceberg 同时维护 `flink/v1.17`、`flink/v1.18`、`flink/v1.19` 三个 Flink 版本模块，它们的 `RowDataUtil`、`RowDataRecordFactory`、`TestHelpers` 代码几乎完全相同（每个版本一份独立拷贝）。

如果只优化 v1.17 而 v1.18/v1.19 仍保留旧的"每行构造 getter"实现，会导致：1) v1.18/v1.19 用户无法享受到性能优化；2) 三个版本模块的代码不一致，增加后续维护与同步成本。因此本提交把 #10565 的改动原样应用到 `flink/v1.18` 与 `flink/v1.19`，使三个 Flink 版本模块的优化保持一致。

## 如何达成设计目的

直接把 #10565 在 v1.17 中的三处改动原样复制到 v1.18 与 v1.19 的对应文件：
1. `RowDataUtil.clone(...)` 增加 5 参数重载（多接收 `RowData.FieldGetter[] fieldGetters`），原 4 参数方法标记 `@Deprecated` 并委托新方法。
2. `RowDataRecordFactory` 构造时预创建 `fieldGetters` 数组并保存为字段，`clone(...)` 调用处改为传入该数组。
3. `TestHelpers` 测试辅助方法适配新签名。

三个 Flink 版本模块的这三个文件改动完全一致（diff 内容相同），仅文件路径不同。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/data/RowDataUtil.java` 与 `flink/v1.19/.../RowDataUtil.java`

**修改目的**：与 v1.17 相同——让 `clone(...)` 支持外部传入预创建的 `fieldGetters` 数组，避免每行重复构造 getter。

**工作逻辑**：与 #10565（序号 0914）在 v1.17 中的改动完全一致：

```java
public static RowData clone(
    RowData from,
    RowData reuse,
    RowType rowType,
    TypeSerializer[] fieldSerializers,
    RowData.FieldGetter[] fieldGetters) {
  ...
  for (int i = 0; i < rowType.getFieldCount(); i++) {
    if (!from.isNullAt(i)) {
      ret.setField(i, fieldSerializers[i].copy(fieldGetters[i].getFieldOrNull(from)));
    } else {
      ret.setField(i, null);
    }
  }
  return ret;
}

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

旧 4 参数方法保留并标记 `@Deprecated`（计划在 1.7.0 移除），内部临时构造一次 `fieldGetters` 数组再委托新方法，保证向后兼容。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowDataRecordFactory.java` 与 `flink/v1.19/.../RowDataRecordFactory.java`

**修改目的**：在工厂构造时预创建 `fieldGetters` 数组并复用。

**工作逻辑**：

```java
class RowDataRecordFactory implements RecordFactory<RowData> {
  private final RowType rowType;
  private final TypeSerializer[] fieldSerializers;
  private final RowData.FieldGetter[] fieldGetters;

  RowDataRecordFactory(RowType rowType) {
    this.rowType = rowType;
    this.fieldSerializers = createFieldSerializers(rowType);
    this.fieldGetters = createFieldGetters(rowType);
  }

  static RowData.FieldGetter[] createFieldGetters(RowType rowType) {
    RowData.FieldGetter[] fieldGetters = new RowData.FieldGetter[rowType.getFieldCount()];
    for (int i = 0; i < rowType.getFieldCount(); ++i) {
      fieldGetters[i] = RowData.createFieldGetter(rowType.getTypeAt(i), i);
    }
    return fieldGetters;
  }
```

`growBatch`/`set` 调用处改为 5 参数版本：`RowDataUtil.clone(from, batch[position], rowType, fieldSerializers, fieldGetters)`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java` 与 `flink/v1.19/.../TestHelpers.java`

**修改目的**：测试辅助方法 `cloneRowData(...)` 适配新签名。

**工作逻辑**：

```java
RowData.FieldGetter[] fieldGetters = new RowData.FieldGetter[rowType.getFieldCount()];
for (int i = 0; i < rowType.getFieldCount(); ++i) {
  fieldGetters[i] = RowData.createFieldGetter(rowType.getTypeAt(i), i);
}
return RowDataUtil.clone(from, null, rowType, fieldSerializers, fieldGetters);
```

与 v1.17 相同，测试辅助方法在调用 `clone(...)` 前临时构造一次 `fieldGetters`（未跨调用复用，因为 `TestHelpers` 是静态工具方法，每次调用 schema 可能不同），主要目的是使用新签名、避免调用 `@Deprecated` 方法。

## 小结

- **成效**：把 #10565（序号 0914）的 `RowDataUtil.clone` 性能优化从 `flink/v1.17` 回迁移到 `flink/v1.18` 与 `flink/v1.19`，使三个 Flink 版本模块的优化保持一致。共修改 6 个文件（每版本 3 个），90 行新增 / 10 行删除。
- **影响范围**：仅 `flink/v1.18` 与 `flink/v1.19` 模块，各 3 个文件（`RowDataUtil`、`RowDataRecordFactory` 为生产代码，`TestHelpers` 为测试代码）。改动内容与 v1.17 完全一致。
- **回迁到 1.4.x 的注意事项**：与 #10565（序号 0914）的注意事项一致：
  - 适合回迁，且建议与 #10565 一起回迁，确保 1.4.x 的三个 Flink 版本模块（v1.17/v1.18/v1.19）同步获得优化、保持代码一致。
  - 改动向后兼容：旧 4 参数 `clone(...)` 方法保留并标记 `@Deprecated`，外部调用方不会因 API 变化编译失败。
  - 回迁时需同时回迁每个版本的 `RowDataUtil`、`RowDataRecordFactory`、`TestHelpers` 三个文件。如果 1.4.x 维护的 Flink 版本与 main 不同（例如 1.4.x 可能还支持 v1.16 或不支持 v1.19），需根据 1.4.x 实际维护的版本列表选择性回迁。
  - `@Deprecated` 注释中"will be removed in 1.7.0"的版本号表述可保留不动。
