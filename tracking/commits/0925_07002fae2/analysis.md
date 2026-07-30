# 提交 0925：Core: Expose incremental/changelog scan in SerializableTable (#10682)

## 提交信息

- **序号**：0925 / 4088
- **哈希**：07002fae27445408a4f8aeb512b14e1a555aa94f
- **短哈希**：07002fae2
- **日期**：2024-07-12 10:06:00 +0200
- **作者**：Denys Kuzmenko <dkuzmenko@cloudera.com>
- **提交说明**：Core: Expose incremental/changelog scan in SerializableTable (#10682)
- **PR/Issue**：#10682

## 总体目的

`SerializableTable` 是 Iceberg 提供的一个可序列化的 `Table` 代理实现，常用于需要把表对象序列化后分发到执行端（如 Spark/MapReduce 任务）的场景：它只携带必要的标识信息，在被反序列化后再通过 `lazyTable()` 重新加载真实的 `Table` 实例。`Table` 接口上定义了 `newIncrementalAppendScan()` 和 `newIncrementalChangelogScan()` 两个增量扫描方法，分别用于增量追加扫描和变更日志扫描。

但 `SerializableTable` 此前并未覆盖这两个方法（`Table` 接口已声明，`SerializableTable` 作为实现类会继承接口默认实现或编译期缺省）。由于 `Table` 接口对这些方法提供了默认实现（默认抛出 `UnsupportedOperationException`），导致通过 `SerializableTable` 反序列化得到的表对象无法正确发起增量/变更日志扫描——调用时会走到接口默认实现而非真正委托给底层表。本提交的目的是补全这两个方法的覆盖，使 `SerializableTable` 也能正确支持增量扫描与变更日志扫描，与真实 `Table` 行为保持一致。

## 如何达成设计目的

在 `SerializableTable` 中新增两个 `@Override` 方法，分别覆盖 `newIncrementalAppendScan()` 和 `newIncrementalChangelogScan()`，方法体与其他 scan 方法（如 `newScan()`、`newBatchScan()`）保持同一模式：直接委托给 `lazyTable()` 返回的真实表对象，从而保证反序列化后调用会落到底层真实表的实现上。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SerializableTable.java`

**修改目的**：让 `SerializableTable` 把增量扫描与变更日志扫描调用委托给底层真实表。

**工作逻辑**：在 `newScan()` 之后新增两个方法：

```java
@Override
public IncrementalAppendScan newIncrementalAppendScan() {
  return lazyTable().newIncrementalAppendScan();
}

@Override
public IncrementalChangelogScan newIncrementalChangelogScan() {
  return lazyTable().newIncrementalChangelogScan();
}
```

二者均通过 `lazyTable()` 获取反序列化后加载的真实表，再调用其对应方法，确保 `SerializableTable` 在序列化往返后仍能正确发起增量扫描。

## 小结

- **成效**：补全了 `SerializableTable` 对 `IncrementalAppendScan` 和 `IncrementalChangelogScan` 的支持，使依赖可序列化表代理的执行引擎能够正常发起增量扫描与变更日志扫描。
- **影响范围**：仅 `core` 模块的 `SerializableTable.java` 一个文件，+10 行，无逻辑改动到其他模块。
- **回迁到 1.4.x 的注意事项**：回迁风险低。前提是 1.4.x 的 `Table` 接口已声明这两个扫描方法且 `IncrementalAppendScan`/`IncrementalChangelogScan` 类型已存在（1.4.x 应已具备）。需确认 1.4.x 上这两个方法在 `Table` 接口中有默认实现（否则 `SerializableTable` 早就编译失败了）；本提交只是把默认实现替换为委托给 `lazyTable()`，属于行为修正，适合回迁。
