# 提交 0915：Flink: Pre-create fieldGetters to avoid constructing them for each row (#10565)

## 提交信息

- **序号**：0915 / 4088
- **哈希**：05923d6ab821a37108409236e03ef7dd5df62670
- **短哈希**：05923d6ab
- **日期**：2024-07-10（Wed Jul 10 14:38:23 2024 +0800）
- **作者**：fengjiajie <laputafancy@gmail.com>
- **提交说明**：Flink: Pre-create fieldGetters to avoid constructing them for each row (#10565)
- **PR/Issue**：#10565

## 总体目的

这是一个针对 Flink 1.17 集成模块的性能优化提交。问题出在 `RowDataUtil.clone(...)` 方法中：原实现在每次克隆一行 `RowData` 时，都会通过 `RowData.createFieldGetter(rowType.getTypeAt(i), i)` 为每个字段现构造一个 `RowData.FieldGetter`。`FieldGetter` 是一个针对特定 `LogicalType` 与字段位置编译出的 lambda/匿名类实例，构造本身有一定开销（涉及 switch 分发、装箱、可能的内部状态）。在 Flink source reader 逐行处理数据的场景下（每行都调用 `clone(...)`），这种"每行每字段重新构造 getter"的开销会被放大，成为不必要的 CPU 与对象分配负担。

正确的做法是：`FieldGetter` 只依赖于 `RowType` 与字段下标，对于同一个 schema 来说是无状态且可复用的。因此应当在 `RowDataRecordFactory` 构造时（schema 已知）就把整个 `RowData.FieldGetter[]` 数组预创建好，之后每次 `clone(...)` 直接复用该数组，避免在热路径上重复构造。这能把"每行 N 次构造"降为"工厂初始化时 N 次构造"。

## 如何达成设计目的

通过两步达成：
1. 在 `RowDataUtil.clone(...)` 增加一个新重载，多接收一个 `RowData.FieldGetter[] fieldGetters` 参数，方法体内不再调用 `RowData.createFieldGetter(...)`，而是直接使用传入的 `fieldGetters[i]`。原 4 参数的旧 `clone(...)` 方法保留但标记 `@Deprecated`（计划在 1.7.0 移除），内部通过临时构造一次 `fieldGetters` 数组再委托给新方法，以保持向后兼容。
2. 在 `RowDataRecordFactory`（Flink source reader 用于创建/复用 `RowData` 批次的工厂）的构造函数中预创建 `fieldGetters` 数组并保存为字段，`clone(...)` 调用处改为传入该数组。同时在测试辅助类 `TestHelpers` 中也做对应适配，保证测试路径使用相同的新签名。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/data/RowDataUtil.java`

**修改目的**：让 `clone(...)` 方法支持外部传入预创建的 `fieldGetters` 数组，避免在每行处理时重复构造 getter。

**工作逻辑**：

新签名：
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
```

关键变化：循环里原来的 `RowData.FieldGetter getter = RowData.createFieldGetter(rowType.getTypeAt(i), i);` 被移除，改为直接使用 `fieldGetters[i].getFieldOrNull(from)`。`fieldSerializers[i].copy(...)` 这层深拷贝逻辑保持不变。

旧的 4 参数方法被保留并标记 `@Deprecated`，文档说明将在 1.7.0 移除，内部实现是临时构造一次 `fieldGetters` 数组再委托新方法：

```java
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

注意旧兼容方法中只在 `!from.isNullAt(i)` 时才构造 getter，与新方法在循环内做 null 判断的行为保持一致（getter 对 null 字段不会被使用）。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/RowDataRecordFactory.java`

**修改目的**：在工厂构造时预创建 `fieldGetters` 数组并保存为字段，使后续每行 `clone(...)` 复用。

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

`createFieldGetters` 与已有的 `createFieldSerializers` 结构对称，遍历 rowType 的每个字段，调用 `RowData.createFieldGetter(...)` 一次性构造好所有 getter。

`growBatch(...)`（实际是 `set`/`appendToBatch` 类方法的实现）的调用从 4 参数版本改为 5 参数版本：

```java
batch[position] =
    RowDataUtil.clone(from, batch[position], rowType, fieldSerializers, fieldGetters);
```

这样每次写入一行时，直接复用工厂字段 `fieldGetters`，不再构造新的 getter。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java`

**修改目的**：测试辅助方法 `cloneRowData(...)` 适配新签名。

**工作逻辑**：

```java
RowData.FieldGetter[] fieldGetters = new RowData.FieldGetter[rowType.getFieldCount()];
for (int i = 0; i < rowType.getFieldCount(); ++i) {
  fieldGetters[i] = RowData.createFieldGetter(rowType.getTypeAt(i), i);
}
return RowDataUtil.clone(from, null, rowType, fieldSerializers, fieldGetters);
```

注意测试辅助方法没有把 `fieldGetters` 缓存为字段（因为 `TestHelpers` 是静态工具方法，每次调用 schema 可能不同），只是在调用 `clone(...)` 前临时构造一次。这虽然没享受到跨行复用的优化，但至少保证使用新签名、避免调用 `@Deprecated` 方法。

## 小结

- **成效**：消除了 Flink 1.17 source reader 在逐行 `clone` `RowData` 时对每个字段重复构造 `RowData.FieldGetter` 的开销，把构造时机从"每行 N 次"前移到"工厂初始化时 N 次"。在大量行处理场景下可降低 CPU 与对象分配压力，属于热点路径优化。
- **影响范围**：仅 `flink/v1.17` 模块，共 3 个文件，45 行新增 / 5 行删除。涉及生产代码 `RowDataUtil`、`RowDataRecordFactory` 与测试代码 `TestHelpers`。注意本提交只覆盖 v1.17，v1.18/v1.19 的对应改动由后续提交 #10676（序号 0918）回迁完成。
- **回迁到 1.4.x 的注意事项**：适合回迁，且建议回迁，因为这是一个低风险的纯性能优化：
  - 改动是向后兼容的：旧 4 参数 `clone(...)` 方法保留并标记 `@Deprecated`，1.4.x 中任何外部调用方不会因 API 变化而编译失败。
  - 回迁时需同时回迁 `RowDataUtil`、`RowDataRecordFactory`、`TestHelpers` 三个文件，缺一不可（`RowDataRecordFactory` 调用新签名，`RowDataUtil` 提供新签名，`TestHelpers` 适配新签名）。
  - 若 1.4.x 同时维护 v1.18/v1.19 模块，应一并回迁对应模块的同一改动（即把 #10676 也一起回迁），保持各 Flink 版本模块一致性。
  - `@Deprecated` 注释中提到"will be removed in 1.7.0"，回迁时该版本号表述可保留不动，因为 1.4.x 不会单独修改它。
