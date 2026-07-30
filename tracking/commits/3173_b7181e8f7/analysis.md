# 提交 3173：Fix incorrect partition bounds calculation in manifest on deletion (#15127)

## 提交信息

- **序号**：3173 / 4088
- **哈希**：b7181e8f765fc3ff188c325ba422b347f59d271e
- **短哈希**：b7181e8f7
- **日期**：2026-01-28
- **作者**：Dong Wang
- **提交说明**：Fix incorrect partition bounds calculation in manifest on deletion (#15127)
- **PR/Issue**：#15127

## 总体目的

这是一个数据正确性 bug 修复。当通过 `DeleteFiles.deleteFromRowFilter` 删除数据文件时，Iceberg 会重写受影响的 manifest 文件，并在重写过程中更新每个分区的统计信息（`PartitionFieldStats` 的 min/max 边界值）。提交说明指出：**若目标表以 `binary` 类型列作为分区列，删除文件后 manifest 中写入的分区边界值会出错**。

根因在于 `PartitionData`（分区数据的内部表示）的 `get` 方法在返回 `byte[]` 类型字段时，直接将该数组包装为 `ByteBuffer` 返回，未做拷贝。而 `ManifestReader` 在顺序读取多个文件的分区数据时，出于性能会**复用同一个底层 byte 数组缓冲区**（解码下一条记录时覆盖前一条的内容）。`PartitionSummary.updateFields` 在累积分区统计时，会从 `PartitionData` 取出 min/max 的 byte[] 引用并保存到 `PartitionFieldStats`。由于取到的是缓冲区引用而非拷贝，当 `ManifestReader` 继续读取下一个文件时，之前保存的统计值会被新数据覆盖，导致最终 manifest 中记录的分区 min/max 指向最后一个被读取文件的值，而非真实聚合结果。

这会带来严重后果：manifest 中的分区边界错误会使后续查询的分区裁剪（partition pruning）失效——可能漏掉应扫描的文件（数据丢失）或扫描不必要的文件（性能下降），破坏 Iceberg 的查询正确性保证。该 bug 仅在分区列为 binary 类型时触发，因为其他类型（如 long、string）在 `PartitionData.get` 中返回的是不可变或值拷贝对象，不受缓冲区复用影响。

修复方式是在 `PartitionData.get` 返回 `byte[]` 时，用 `Arrays.copyOf` 复制一份再包装为 `ByteBuffer`，使返回值与底层缓冲区解耦。同时为 Spark v3.5/v4.0/v4.1 各新增回归测试 `testDeleteFromTablePartitionedByVarbinary`，覆盖 binary 分区表的删除场景。

## 如何达成设计目的

核心修复仅一行：在 `PartitionData.java` 的 `get` 方法中，对 `byte[]` 分支从直接 `ByteBuffer.wrap((byte[]) data[pos])` 改为先 `Arrays.copyOf` 再 wrap。这样每次取值都返回独立副本，后续缓冲区复用不会污染已保存的统计值。测试侧在三个 Spark 版本的 `TestDeleteFrom` 中新增针对 `binary` 分区列的删除用例，验证删除后查询结果与按分区值过滤查询结果均正确。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionData.java` (+2/-1 lines)

**修改目的**：修复 byte[] 字段返回值被缓冲区复用污染的缺陷。

**工作逻辑**：
原代码：
```java
if (data[pos] instanceof byte[]) {
  return ByteBuffer.wrap((byte[]) data[pos]);
}
```
改为：
```java
if (data[pos] instanceof byte[]) {
  byte[] copied = Arrays.copyOf((byte[]) data[pos], ((byte[]) data[pos]).length);
  return ByteBuffer.wrap(copied);
}
```
`Arrays.copyOf` 创建一个与原数组内容相同的新数组，`ByteBuffer.wrap` 再将其包装。返回的 ByteBuffer 底层数组与 `ManifestReader` 的复用缓冲区完全独立，因此 `PartitionSummary.updateFields` 保存的 min/max 引用不会因后续读取被篡改。注意该拷贝仅在 binary 分区列场景发生，对其他类型无性能影响，且 binary 分区列本身较少见，拷贝开销可接受。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestDeleteFrom.java` (+23/-0 lines)

**修改目的**：新增 binary 分区表删除回归测试（Spark 3.5）。

**工作逻辑**：
新增 `testDeleteFromTablePartitionedByVarbinary` 测试方法：
1. 建表 `CREATE TABLE %s (id bigint NOT NULL, data binary) USING iceberg PARTITIONED BY (data)`，以 binary 列 `data` 作为分区列。
2. 插入两行 `(1, X'e3bcd1')` 和 `(2, X'bcd1')`，断言查询结果正确（验证插入与读取路径）。
3. 执行 `DELETE FROM %s WHERE data = X'bcd1'` 删除一行。
4. 断言 `SELECT * FROM %s` 仅剩一行 `(1, [-29,-68,-47])`。
5. 断言 `SELECT * FROM %s where data = X'e3bcd1'` 能查到剩余行——**这一步是关键**，若 manifest 分区边界因 bug 被错误计算为被删除文件的值，则按 `data = X'e3bcd1'` 做分区裁剪时可能错误地排除剩余文件，导致查询返回空，从而暴露 bug。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestDeleteFrom.java` (+23/-0 lines)

**修改目的**：同 v3.5，为 Spark 4.0 新增相同回归测试。内容与 v3.5 完全一致。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestDeleteFrom.java` (+23/-0 lines)

**修改目的**：同 v3.5，为 Spark 4.1 新增相同回归测试。内容与 v3.5 完全一致。

## 总结

本次提交修复了一个影响数据正确性的严重缺陷：当表以 `binary` 列分区时，删除文件会导致 manifest 中的分区 min/max 边界被 `ManifestReader` 的缓冲区复用污染而记录错误值，进而破坏分区裁剪的正确性。修复手段是在 `PartitionData.get` 返回 byte[] 时做防御性拷贝，并在三个 Spark 版本中新增针对性的回归测试。该修复对保障 Iceberg 在 binary 分区场景下的查询正确性具有重要意义。
