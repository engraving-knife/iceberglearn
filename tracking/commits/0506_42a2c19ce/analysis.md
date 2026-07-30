# 提交 0506：Core: Only write view history when currentVersionId changes (#9725)

## 提交信息

| 字段 | 内容 |
|------|------|
| 序号 | 0506 |
| 完整哈希 | 42a2c19cec31c626cbff6cc2dfafb86cdf223bd0 |
| 短哈希 | 42a2c19ce |
| 日期 | 2024-02-15 |
| 作者 | Eduard Tudenhoefner <etudenhoefner@gmail.com> |
| 提交说明 | Core: Only write view history when currentVersionId changes (#9725) |
| PR | #9725 |

## 总体目的

本提交修复了 Iceberg 视图（View）元数据中版本历史（version-log / history）记录逻辑的一个设计缺陷。在修改之前，`ViewMetadata.Builder` 在调用 `addVersion()` 添加视图版本时就会立即向 `history` 列表追加一条 `ViewHistoryEntry` 记录。这导致一个问题：当多个版本被添加但并非所有版本都被设为当前版本时，那些从未被设为 currentVersionId 的版本也会出现在历史日志中，造成历史记录与实际的"版本切换"语义不一致。

视图的历史日志（version-log）在语义上应当记录的是"哪个版本在何时成为当前版本"，而非"哪个版本被添加到了元数据中"。一个版本可以被添加到 `versions` 列表中进行保留（例如为了版本历史保留策略），但如果它从未被设为当前版本，它就不应该出现在 `history` 中。此前实现将"添加版本"与"切换当前版本"两个动作混为一谈，导致 history 中可能包含从未被激活的版本条目。

本提交将历史条目的创建时机从 `addVersion()` 延迟到 `setCurrentVersionId()`，确保只有真正成为当前版本的版本才会被记录到历史日志中。同时，历史条目不再立即加入 `history` 列表，而是暂存在一个 `historyEntry` 字段中，在 `build()` 时才最终追加，这与其它变更跟踪字段（如 `lastAddedVersionId`、`lastAddedSchemaId`）的处理方式保持一致。

## 如何达成设计目的

实现路径分为三步：首先在 `Builder` 中新增一个 `historyEntry` 字段用于暂存待写入的历史条目；然后在 `setCurrentVersionId()` 方法中根据目标版本的时间戳和版本号构建该条目；最后在 `build()` 方法末尾将暂存的条目追加到 `history` 列表。同时移除了原先在 `addVersionInternal()` 中直接向 `history` 列表追加条目的代码。此外，为 `expireVersions` 和 `updateHistory` 两个静态方法添加了 `@VisibleForTesting` 注解，使测试可以直接调用它们进行验证。

## 修改详情

### core/src/main/java/org/apache/iceberg/view/ViewMetadata.java

**修改目的**：将视图历史条目的写入时机从"添加版本"改为"设置当前版本"，确保历史日志只记录真正成为当前版本的版本。

**工作逻辑**：

1. 新增 `historyEntry` 字段（类型 `ViewHistoryEntry`，初始为 `null`），用于在 Builder 构建过程中暂存待写入的历史条目。

2. 在 `setCurrentVersionId(int newVersionId)` 方法中，当确定要切换当前版本时，构建一个 `ImmutableViewHistoryEntry`（包含目标版本的 `timestampMillis` 和 `versionId`）并赋值给 `this.historyEntry`：
```java
this.historyEntry =
    ImmutableViewHistoryEntry.builder()
        .timestampMillis(version.timestampMillis())
        .versionId(version.versionId())
        .build();
```

3. 在 `addVersionInternal()` 方法中，移除了原先直接向 `history` 列表追加条目的代码块：
```java
// 已移除：
// history.add(
//     ImmutableViewHistoryEntry.builder()
//         .timestampMillis(version.timestampMillis())
//         .versionId(version.versionId())
//         .build());
```

4. 在 `build()` 方法中，在处理历史大小限制之前，将暂存的 `historyEntry` 追加到 `history` 列表：
```java
if (null != historyEntry) {
  history.add(historyEntry);
}
```

5. 为 `expireVersions` 和 `updateHistory` 两个静态方法添加 `@VisibleForTesting` 注解，表明它们仅供测试直接调用。

6. 引入了 `org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting`。

### core/src/test/java/org/apache/iceberg/rest/responses/TestLoadViewResponseParser.java

**修改目的**：更新测试中期望的 JSON 输出，移除 version-log 中不再应出现的旧版本条目。

**工作逻辑**：在两处期望的 JSON 字符串中，移除了 version-id 为 1（timestamp-ms 23）和 version-id 为 2（timestamp-ms 24）的历史条目，只保留 version-id 为 3（timestamp-ms 25）的条目。这反映了修复后的行为：只有被设为当前版本的版本 3 才会出现在 history 中。

### core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java

**修改目的**：更新和新增测试以验证修复后的历史记录行为。

**工作逻辑**：

1. `expireVersionsTest`：扩展为测试保留 3、2、1 个版本的三种情况，使用 `containsExactlyInAnyOrder` 和 `containsExactly` 进行更精确的断言。

2. `updateHistoryTest`：重写为使用命名变量（`one`、`two`、`three`）并测试三种场景：全部保留、中间出现无效版本导致前面被移除、另一种无效版本场景。验证 `updateHistory` 在遇到不在保留集合中的版本时，会移除该条目及其之前所有条目。

3. `viewHistoryNormalization` 重命名为 `viewVersionHistoryNormalization`，并将历史大小断言从 3 改为 1（因为只有最后一个被设为 current 的版本才在历史中）。

4. 新增 `viewVersionHistoryIsCorrectlyRetained` 测试：这是核心验证测试。它创建三个版本并设当前版本为 3，验证 history 只有 1 条记录（versionId=3）。然后 rebuild 后 history 仍为 1 条。接着切换到版本 2，验证 history 变为 2 条（先是 3，后是 2）。再切回版本 3，history 变为 3 条。最后验证切换到已被过期删除的版本 1 会抛出 `IllegalArgumentException`。

5. `viewMetadataAndMetadataChanges` 测试中，将 history 大小断言从 3 改为 1。

### core/src/test/java/org/apache/iceberg/view/TestViewMetadataParser.java

**修改目的**：更新解析器测试以适配新的历史记录行为。

**工作逻辑**：两处测试中，将期望的 `ViewMetadata` 构建方式从直接 `builder().addVersion(...).setCurrentVersionId(2).build()` 改为先用 `builder()` 构建一个以版本 1 为当前的元数据，再用 `buildFrom()` 切换到版本 2。这样版本 1 和版本 2 的切换都会产生历史条目，与解析 JSON 文件中的 version-log 保持一致。这种嵌套的 `buildFrom(buildFrom(builder()...).setCurrentVersionId(1)...)` 结构确保了历史条目的正确产生。

## 小结

本提交修复了视图历史记录的语义正确性问题，将历史条目的创建从"添加版本"时延迟到"设置当前版本"时，确保 version-log 只记录真正被激活为当前版本的版本。修改涉及核心 `ViewMetadata.Builder` 的逻辑调整（新增暂存字段、移动写入时机），以及多个测试文件的适配。核心变更集中而精准，通过暂存机制保持了与现有变更跟踪模式的一致性。
