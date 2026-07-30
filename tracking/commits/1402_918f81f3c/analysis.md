# 提交 1402：Core: Fix CCE when retrieving TableOps (#11585)

## 提交信息

- **序号**：1402 / 4088
- **哈希**：918f81f3c3f498f46afcea17c1ac9cdc6913cb5c
- **短哈希**：918f81f3c
- **日期**：2024-11-20（Wed Nov 20 15:06:33 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Fix CCE when retrieving TableOps (#11585)
- **PR/Issue**：#11585

## 总体目的

`SerializableTable` 是 Iceberg 在 core 模块提供的可序列化表实现，用于在跨进程（例如 Spark executor）传递表对象时使用。它有一个静态内部类 `SerializableMetadataTable`，专门用于包装 `BaseMetadataTable`（如分支、快照、历史、文件清单等元数据表）的序列化副本。

父类 `SerializableTable#operations()` 的实现是：

```java
return (StaticTableOperations) ((BaseTable) lazyTable()).operations();
```

这里把 `lazyTable()` 强转为 `BaseTable`，再取其 `operations()`。但对于 `SerializableMetadataTable`，其 `lazyTable()` 通过 `newTable(...)` 重建的并不是 `BaseTable`，而是 `BaseMetadataTable` 的某个子类，于是这个 cast 会抛 `ClassCastException`（CCE）。

本提交的目的是：在 `SerializableMetadataTable` 上重写 `operations()`，直接抛出 `UnsupportedOperationException` 并给出清晰错误信息，避免触发 CCE，让调用方明确知道该操作在序列化后的元数据表上不被支持。

## 如何达成设计目的

在 `SerializableMetadataTable` 中显式重写 `operations()` 方法，抛出 `UnsupportedOperationException`，错误信息包含本类名并提示 `does not support operations()`。这样：

1. 调用方拿到的是语义清晰的 `UnsupportedOperationException` 而不是误导性的 `ClassCastException`；
2. 与父类其它不支持操作（如 `expireSnapshots`、`manageSnapshots`、`newTransaction` 等）的错误处理风格保持一致；
3. 调用栈上不会执行到会触发 CCE 的强转逻辑。

同时在测试 `TestTableSerialization` 中新增断言，验证序列化后的元数据表调用 `operations()` 确实抛 `UnsupportedOperationException`，且消息以 `does not support operations()` 结尾，以回归保护此行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SerializableTable.java`

**修改目的**：为 `SerializableMetadataTable` 显式禁用 `operations()` 调用，避免 CCE。

**工作逻辑**：在 `SerializableMetadataTable` 内部 `newTable(...)` 方法之后追加：

```java
@Override
public StaticTableOperations operations() {
  throw new UnsupportedOperationException(
      this.getClass().getName() + " does not support operations()");
}
```

注意：返回类型保留为 `StaticTableOperations`（与父类签名一致），但方法体直接抛异常，调用方一旦尝试获取 TableOps 就会得到明确异常而不是延迟到强转时才报错。错误信息采用 `this.getClass().getName()` 以便子类场景下也能显示真实类名。

### `core/src/test/java/org/apache/iceberg/hadoop/TestTableSerialization.java`

**修改目的**：为修复添加回归测试。

**工作逻辑**：

1. 新增静态导入 `assertThatThrownBy`；
2. 在 `SerializableMetadataTable` 序列化往返测试中追加断言：

```java
assertThatThrownBy(() -> ((HasTableOperations) serializableTable).operations())
    .isInstanceOf(UnsupportedOperationException.class)
    .hasMessageEndingWith("does not support operations()");
```

这里把 `serializableTable` 强转为 `HasTableOperations` 再调用 `operations()`，正是触发旧 CCE 的入口，现被验证为抛 `UnsupportedOperationException`，从而确保后续不会再回归为 CCE。

## 小结

- **成效**：序列化后的元数据表（`SerializableMetadataTable`）调用 `operations()` 不再抛 `ClassCastException`，而是抛出语义清晰的 `UnsupportedOperationException`，错误信息明确指出该类不支持 `operations()`。
- **影响范围**：仅 core 模块 `SerializableTable.java` 的内部静态类与对应测试，新增 10 行，无 API 变更或行为回归。
- **回迁到 1.4.x 的注意事项**：1.4.x 同样存在 `SerializableTable` 与 `SerializableMetadataTable`，如果用户在 1.4.x 中通过序列化方式传递元数据表并在远端调用 `operations()`，会撞上同样的 CCE。建议回迁此修复以保证错误语义一致；回迁时注意 `SerializableMetadataTable` 在 1.4.x 中的字段与构造逻辑是否一致，新方法直接复用即可，无依赖其他改动。同时测试需要 assertj 的 `assertThatThrownBy` 与 `hasMessageEndingWith`，确认 1.4.x 测试依赖版本支持。
