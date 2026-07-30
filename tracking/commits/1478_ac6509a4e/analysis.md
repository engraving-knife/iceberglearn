# 提交 1478：Flink: Fix range distribution npe when value is null (#11662)

## 提交信息

- **序号**：1478 / 4088
- **哈希**：ac6509a4e469f808bebb8b713a5c4213f98ff4a5
- **短哈希**：ac6509a4e
- **日期**：2024-12-11（Wed Dec 11 05:33:55 2024 +0800）
- **作者**：GuoYu <511955993@qq.com>
- **提交说明**：Flink: Fix range distribution npe when value is null (#11662)
- **PR/Issue**：#11662

## 总体目的

Iceberg Flink sink 支持 `range` 分布模式（`write.distribution-mode=range`），通过 `DataStatisticsCoordinator` 收集各 subtask 上报的本地数据统计，再据此计算全局 `MapAssignment` 或 `RangeBounds`，把数据按 sort key 范围重新分配到下游 subtask，从而避免写入时数据倾斜。统计信息中的核心载体是 `SortKey`，每个 `SortKey` 包含 sort order 中各字段经过 transform 后的值。

**Bug**：当 sort key 中的某个字段值为 `null`（这在实际数据中很常见，例如分区列允许 null、或业务数据中存在 null）时，`SortKeySerializer` 在序列化和反序列化 `SortKey` 时会抛 NullPointerException，导致整个 Flink job 失败。原因是旧版 `SortKeySerializer`（snapshot version 1）在 `serialize` / `deserialize` 时直接调用 `record.get(i, XXX.class)`，对于 null 值的 primitive 类型读取会抛 NPE，且没有任何 null 标记机制。

**额外问题**：旧版（v1）的 `SortKeySerializer` 一旦升级到新版（v2），从历史 checkpoint 恢复时无法正确解析 v1 序列化的字节——因为 v2 引入了 null 标记字节，但 v1 数据中并没有这个字节，反序列化会错位。这破坏了 Flink 的 state 兼容性，对一个正在运行的 production job 来说是不能接受的。

本提交要同时解决这两件事：
1. 让 `SortKeySerializer` 支持 null 字段值的序列化/反序列化；
2. 保持对历史 v1 checkpoint 数据的兼容，能从 v1 数据正确恢复，恢复后切换到 v2 继续工作。

## 如何达成设计目的

通过引入 `SortKeySerializer` 的 v2 协议——在序列化每个字段之前先写一个 boolean `isNull` 标记；反序列化时先读这个标记，若为 true 则把该字段设为 null 并跳过类型特定的读取。同时引入 v1 → v2 的迁移路径：

1. `SortKeySerializerSnapshot.CURRENT_VERSION` 从 1 升到 2；
2. `readSnapshot` 同时支持 v1 和 v2 的快照读取，v1 读取后把内部 `version` 字段置为 1；
3. `resolveSchemaCompatibility` 在"旧 v1 ↔ 新 v2"时返回 `compatibleAfterMigration()`，告知 Flink 框架需要执行迁移；
4. `restoreSerializer()` 返回的 serializer 携带旧版本号，从而能按旧协议读取历史 state；
5. 在 `StatisticsUtil.deserializeCompletedStatistics` 中加入二级回退逻辑：先用当前版本反序列化，若失败（说明是 v1 数据但 snapshot 路径未触发迁移）则手动切换 serializer 到 v1 重试，成功后再切回 latest，让后续 TM 上报的数据按 v2 解析。

同时给 `CompletedStatistics` 增加 `isValid()` 方法做有效性校验，作为反序列化失败的兜底检测。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java`

**修改目的**：核心修复——让序列化器支持 null 字段，并实现 v1/v2 兼容。

**工作逻辑**：

1. **新增 `version` 字段**：
   ```java
   private int version;
   ```
   记录当前 serializer 实例使用的协议版本（1 或 2）。这允许同一个类实例按 v1 或 v2 协议工作。

2. **构造函数扩展**：保留 `SortKeySerializer(Schema, SortOrder)` 旧构造（默认 version = `CURRENT_VERSION`，即 2），新增 `SortKeySerializer(Schema, SortOrder, int version)` 显式指定版本。

3. **新增版本控制 API**：
   ```java
   public int getLatestVersion() { return snapshotConfiguration().getCurrentVersion(); }
   public void restoreToLatestVersion() { this.version = snapshotConfiguration().getCurrentVersion(); }
   public void setVersion(int version) { this.version = version; }
   ```
   `restoreToLatestVersion` 和 `setVersion` 是给 `StatisticsUtil` 二级回退用的——读取 v1 数据后切换回 v2。

4. **`serialize` 增加 null 标记**：在每个字段序列化之前插入：
   ```java
   if (version > 1) {
     Object value = record.get(i, Object.class);
     if (value == null) {
       target.writeBoolean(true);
       continue;
     } else {
       target.writeBoolean(false);
     }
   }
   ```
   `version > 1` 守卫保证 v1 协议下不会写出这个字节，从而保持对 v1 数据的兼容输出。注意只对 v2 才写 null 标记。

5. **`deserialize` 对应处理**：
   ```java
   if (version > 1) {
     boolean isNull = source.readBoolean();
     if (isNull) {
       reuse.set(i, null);
       continue;
     }
   }
   ```
   v1 协议下不会读这个标记，按原逻辑读字段。

6. **`SortKeySerializerSnapshot` 升级**：
   - `CURRENT_VERSION` 从 1 改为 2；
   - 新增 `private int version = CURRENT_VERSION` 字段；
   - `readSnapshot` 用 switch 同时处理 v1 和 v2：v1 读取后 `this.version = 1`，v2 直接读；
   - 把原 `readV1` 方法重命名为 `read`（v1/v2 共用同一份 schema/sortOrder 读取逻辑，差异只在 version 字段）；
   - `resolveSchemaCompatibility` 在"旧 v1 ↔ 新 v2"时返回 `TypeSerializerSchemaCompatibility.compatibleAfterMigration()`，告诉 Flink 框架"两者兼容但需要迁移"；
   - `restoreSerializer()` 用 `new SortKeySerializer(schema, sortOrder, version)`——注意传入的是 snapshot 中读到的 version（可能是 1 或 2），从而让恢复后的 serializer 按正确协议解析历史 state。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatisticsSerializer.java`

**修改目的**：暴露切换 SortKeySerializer 版本的能力。

**工作逻辑**：新增两个 public 方法：
```java
public void changeSortKeySerializerVersion(int version) {
  if (sortKeySerializer instanceof SortKeySerializer) {
    ((SortKeySerializer) sortKeySerializer).setVersion(version);
  }
}

public void changeSortKeySerializerVersionLatest() {
  if (sortKeySerializer instanceof SortKeySerializer) {
    ((SortKeySerializer) sortKeySerializer).restoreToLatestVersion();
  }
}
```
委托给内部的 `sortKeySerializer` 实例（持有 `ListSerializer<SortKey>` 中的元素 serializer）。`instanceof` 守卫是为了避免在不使用 `SortKeySerializer` 的自定义实现时出错。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsUtil.java`

**修改目的**：实现 v1 数据反序列化失败的二级回退。

**工作逻辑**：把 `deserializeCompletedStatistics` 的参数类型从 `TypeSerializer<CompletedStatistics>` 收窄为 `CompletedStatisticsSerializer`（这样才能调用 `changeSortKeySerializerVersion`），并重写为：

```java
static CompletedStatistics deserializeCompletedStatistics(
    byte[] bytes, CompletedStatisticsSerializer statisticsSerializer) {
  try {
    DataInputDeserializer input = new DataInputDeserializer(bytes);
    CompletedStatistics completedStatistics = statisticsSerializer.deserialize(input);
    if (!completedStatistics.isValid()) {
      throw new RuntimeException("Fail to deserialize aggregated statistics,change to v1");
    }
    return completedStatistics;
  } catch (Exception e) {
    try {
      // 二级回退：切换到 v1 协议重试
      statisticsSerializer.changeSortKeySerializerVersion(1);
      DataInputDeserializer input = new DataInputDeserializer(bytes);
      CompletedStatistics deserialize = statisticsSerializer.deserialize(input);
      statisticsSerializer.changeSortKeySerializerVersionLatest();
      return deserialize;
    } catch (IOException ioException) {
      throw new UncheckedIOException("Fail to deserialize aggregated statistics", ioException);
    }
  }
}
```

注意点：
- **第一次尝试**：用 serializer 当前 version（可能是 v2）反序列化，并用 `isValid()` 校验结果。
- **二级回退**：如果第一次抛异常或返回无效结果，切换到 v1 协议重试；成功后立即切换回 latest，让后续从 TM 收到的新数据按 v2 解析。
- 注释明确说明："If we restore from a lower version, the new version of SortKeySerializer cannot correctly parse the checkpointData, so we need to first switch the version to v1. Once the state data is successfully parsed, we need to switch the serialization version to the latest version to parse the subsequent data passed from the TM."

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/CompletedStatistics.java`

**修改目的**：为反序列化结果提供有效性校验。

**工作逻辑**：新增方法：
```java
boolean isValid() {
  if (type == StatisticsType.Sketch) {
    if (null == keySamples) {
      return false;
    }
  } else {
    if (null == keyFrequency()) {
      return false;
    }
    if (keyFrequency().values().contains(null)) {
      return false;
    }
  }
  return true;
}
```
对 Sketch 类型校验 `keySamples` 非空；对 Map 类型校验 `keyFrequency` 非空且值不含 null。这是 v1 数据被 v2 serializer 错误解析时的兜底——错误解析往往产生 null value，被 `isValid` 拦截后触发二级回退。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/DataStatisticsCoordinator.java`

**修改目的**：把传给 `deserializeCompletedStatistics` 的 serializer 强转为 `CompletedStatisticsSerializer`。

**工作逻辑**：把调用处改为 `(CompletedStatisticsSerializer) completedStatisticsSerializer`。因为 `StatisticsUtil.deserializeCompletedStatistics` 的参数类型已收窄，需要显式 cast。Coordinator 持有的 `completedStatisticsSerializer` 字段实际类型就是 `CompletedStatisticsSerializer`，cast 安全。

### 测试文件

1. **`TestCompletedStatisticsSerializer.java`**：新增三个测试：
   - `testSerializer` 基础往返测试；
   - `testRestoreOldVersionSerializer` 模拟 v1 数据：先用 `changeSortKeySerializerVersion(1)` 序列化得到 v1 字节，再切回 latest 用 `StatisticsUtil.deserializeCompletedStatistics` 反序列化，验证能正确恢复；
   - `testRestoreNewSerializer` 验证 v2 数据正常往返。

2. **`TestSortKeySerializerSnapshot.java`**：新增 `testRestoredOldSerializer` 测试——构造 v1 serializer，序列化一个含 string+int 的 SortKey，再通过 `restoreSerializer()` 恢复并 `setVersion(1)`，验证能正确反序列化。

3. **`TestSortKeySerializerPrimitives.java`**：更新一个断言——序列化字节数从 38 增加到 39（多了 1 字节 null 标记）。注释更新为："34 UUID text + 4 byte integer of string length + 1 byte of isnull flag"。

4. **`TestDataStatisticsCoordinator.java`**：新增 `testDataStatisticsEventHandlingWithNullValue` 参数化测试——构造一个 `SortKey` 第一字段为 null，验证 Coordinator 能正确处理含 null 的统计事件、正确计算 `MapAssignment` 与 `RangeBounds`（Sketch 模式下 `rangeBounds` 仍为 `CHAR_KEYS.get("b")`）。

5. **`TestDataStatisticsOperator.java`**：新增 `testProcessElementWithNull` 测试——往 operator 发送 `GenericRowData.of(null, 5)`，验证本地统计能正确序列化/反序列化。

6. **`TestFlinkIcebergSinkDistributionMode.java`**：新增端到端测试 `testRangeDistributionWithNullValue`——在 partitioned 表上启用 range distribution，发送 6 个 checkpoint 的 char rows + 一条 `(1, null)` 记录，验证 job 能成功完成且生成对应 snapshots。

## 小结

- **成效**：修复了 Flink range distribution 模式下 sort key 含 null 字段时的 NPE，并实现了 `SortKeySerializer` v1 → v2 的兼容迁移——既能正确序列化 null，又能从历史 v1 checkpoint 平滑恢复，恢复后自动切换到 v2 协议。新增端到端测试覆盖 null 值场景。
- **影响范围**：仅 `flink/v1.20` 模块，11 个文件，新增 325 行、删除 15 行。注意 v1.18/v1.19 没有同步修改——这意味着该修复只回迁到了 v1.20，可能与各 Flink 版本的维护节奏有关。
- **回迁到 1.4.x 的注意事项**：**不要回迁**。1.4.x 分支没有 range distribution 这套 `SortKeySerializer` / `DataStatisticsCoordinator` 代码（range distribution 是 1.6.0 引入的特性，1.4.x 早于此），不存在对应的 bug。若强行 cherry-pick 会因找不到这些类而失败。1.4.x 用户若需要 range distribution，应升级到 1.6.0+ 并应用此修复。
