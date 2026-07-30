# 提交 4025：Core: Use mocks in TrackedFile tests (#17133)

## 提交信息

- **序号**：4025 / 4088
- **哈希**：9484b5769186af84bcd833760d7e3e5810130c41
- **短哈希**：9484b5769
- **日期**：2026-07-13 15:16:35 -0700
- **作者**：Ryan Blue
- **提交说明**：Core: Use mocks in TrackedFile tests (#17133)
- **PR/Issue**：#17133

## 总体目的

本提交重构 `TestTrackedFileStruct` 测试，将原本用真实对象（`TrackingStruct`、`PartitionData`、`DeletionVectorStruct`、`ManifestInfoStruct`、`ContentStats`）构造测试夹具的方式改为使用 Mockito mock 对象。

目的是让 `TrackedFileStruct` 的单元测试更聚焦于 `TrackedFileStruct` 自身的行为（如字段存取、copy 语义、序列化），而不依赖被嵌入的子对象（Tracking、PartitionData、DeletionVector、ManifestInfo、ContentStats）的具体实现。这些子对象各自有独立的序列化测试，不应在 `TrackedFileStruct` 测试中重复验证。

使用 mock 后：
1. copy 测试可以精确验证 `TrackedFileStruct.copy()` 是否调用了各子对象的 `copy()` 方法（通过 `isSameAs(TRACKING_COPY)` 等），而非用比较器间接验证值相等。
2. 序列化测试将子对象设为 null，避免测试 `TrackedFileStruct` 序列化时连带测试子对象序列化（注释明确"TrackingStruct has its own serialization tests"等）。
3. 移除了不再需要的 `Comparator`、`Comparators`、`newPartition` 辅助方法。

## 如何达成设计目的

1. 将 `TRACKING`、`PARTITION`、`DELETION_VECTOR`、`MANIFEST_INFO`、`CONTENT_STATS` 等静态夹具改为 `Mockito.mock(...)`。
2. 在 static 块中配置每个 mock 的 `copy()` 返回对应的 `*_COPY` mock，使 copy 测试能断言 `copy.tracking() isSameAs(TRACKING_COPY)`。
3. copy 测试断言从 `Comparator.compare(...) == 0` 改为 `isSameAs(*_COPY)`，验证调用链而非值相等。
4. 序列化测试将子对象参数设为 null，断言反序列化后为 null，聚焦于 `TrackedFileStruct` 自身字段的序列化。
5. 删除 `PARTITION_COMPARATOR`、`TRACKING_COMPARATOR`、`newPartition` 等不再需要的辅助。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestTrackedFileStruct.java` (+28/-67 lines)

**修改目的**：用 mock 替代真实子对象夹具，聚焦 TrackedFileStruct 自身测试。

**工作逻辑**：
- 夹具改为 mock：
  ```java
  private static final Tracking TRACKING = Mockito.mock(Tracking.class);
  private static final Tracking TRACKING_COPY = Mockito.mock(Tracking.class);
  private static final PartitionData PARTITION = Mockito.mock(PartitionData.class);
  private static final PartitionData PARTITION_COPY = Mockito.mock(PartitionData.class);
  // ... DELETION_VECTOR, MANIFEST_INFO, CONTENT_STATS 同理
  static {
    Mockito.when(TRACKING.copy()).thenReturn(TRACKING_COPY);
    Mockito.when(PARTITION.copy()).thenReturn(PARTITION_COPY);
    // ...
  }
  ```
- copy 测试：
  ```java
  assertThat(copy.tracking()).isSameAs(TRACKING_COPY);
  assertThat(copy.partition()).isSameAs(PARTITION_COPY);
  assertThat(copy.deletionVector()).isSameAs(DELETION_VECTOR_COPY);
  assertThat(copy.manifestInfo()).isSameAs(MANIFEST_INFO_COPY);
  ```
  替代原来的 `Comparator.compare(...) == 0`。
- 序列化测试：子对象参数传 null，断言反序列化后 `isNull()`，注释说明子对象有独立序列化测试。
- 删除 `import Comparator/Comparators` 和 `newPartition` 方法。

## 总结

本提交将 `TestTrackedFileStruct` 的测试夹具从真实子对象改为 Mockito mock，使测试聚焦于 `TrackedFileStruct` 自身的字段存取、copy 调用链和序列化行为，不再耦合子对象的实现。copy 测试现在能精确验证 `copy()` 是否正确委托给各子对象的 `copy()`，序列化测试不再连带验证子对象序列化。这是一次测试质量提升，符合"每个类测试自身职责"的原则。
