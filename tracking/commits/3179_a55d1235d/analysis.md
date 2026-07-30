# 提交 3179：Spark 4.1 | 4.0 | 3.5 | 3.4: Fail publish_changes procedure if there's more than one matching snapshot (#14955)

## 提交信息

- **序号**：3179 / 4088
- **哈希**：a55d1235d20a542b7de4cc60772dad172a414861
- **短哈希**：a55d1235d
- **日期**：2026-01-29
- **作者**：Sam Wheating
- **提交说明**：Spark 4.1 | 4.0 | 3.5 | 3.4: Fail publish_changes procedure if there's more than one matching snapshot (#14955)
- **PR/Issue**：#14955

## 总体目的

Iceberg 的 WAP（Write-Audit-Publish）模式允许用户通过 `spark.wap.id` 配置将写入暂存为带标记的快照，之后通过 `publish_changes` 存储过程将指定 `wap_id` 对应的快照 cherry-pick 到当前分支。此前的实现使用 `Iterables.find()` 在表的所有快照中查找匹配 `wap_id` 的快照——该方法在找到第一个匹配项后即返回，不会检测是否存在多个匹配项。

问题在于：当用户对同一表使用相同的 `spark.wap.id` 进行了多次 WAP 写入后，表中会存在多个具有相同 `wap_id` 的暂存快照。此时 `publish_changes` 仅会发布第一个匹配的快照而静默忽略其余快照，用户无法察觉存在歧义，可能导致部分变更丢失或发布非预期的快照版本。这是一种潜在的数据正确性风险。

本提交将 `publish_changes` 过程改为在检测到多个匹配快照时主动抛出 `ValidationException`，遵循"快速失败"原则，要求用户先消除歧义（例如使用不同的 wap_id 或清理重复快照）后再执行发布。文档也同步更新，明确说明该过程在存在多个匹配快照时会失败。

## 如何达成设计目的

改动覆盖 Spark v3.4、v3.5、v4.0、v4.1 四个版本。核心思路是将原先基于 `Iterables.find()` 的"取第一个匹配"逻辑替换为显式遍历所有快照的 for 循环，在发现第二个匹配项时立即抛出异常。同时移除了对 `Optional` 和 `Iterables` 的导入依赖。每个版本均补充了 `testApplyDuplicateWapId` 测试用例验证多匹配场景下的失败行为，并更新了 `spark-procedures.md` 文档。

## 修改详情

### `docs/docs/spark-procedures.md` (+2/-0 lines)

**修改目的**：文档说明 `publish_changes` 在存在多个匹配快照时会失败。

**工作逻辑**：
在已有的"Only append and dynamic overwrite snapshots can be successfully published."说明之后，新增一行："The `publish_changes` procedure will fail if there are multiple snapshots in the table with the provided `wap_id`."，使用户在使用前了解此约束。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/PublishChangesProcedure.java` (+16/-11 lines)

**修改目的**：重写快照查找逻辑以检测重复 `wap_id`。

**工作逻辑**：
原代码使用 `Iterables.find(table.snapshots(), snapshot -> wapId.equals(WapUtil.stagedWapId(snapshot)), null)` 包裹在 `Optional.ofNullable` 中，找到第一个匹配即返回；若未找到则抛出 "Cannot apply unknown WAP ID" 异常。

新代码改为显式 for 循环遍历 `table.snapshots()`：
```java
Snapshot matchingSnap = null;
for (Snapshot snap : table.snapshots()) {
  if (wapId.equals(WapUtil.stagedWapId(snap))) {
    if (matchingSnap != null) {
      throw new ValidationException(
          "Cannot apply non-unique WAP ID. Found multiple snapshots with WAP ID '%s'", wapId);
    } else {
      matchingSnap = snap;
    }
  }
}
```
当发现第二个匹配快照时立即抛出 `ValidationException`，消息明确指出 WAP ID 不唯一。若遍历结束 `matchingSnap` 仍为 null，则保留原有的 "Cannot apply unknown WAP ID" 错误。后续的 cherry-pick 与输出行逻辑不变，仅将 `wapSnapshot.get()` 替换为 `matchingSnap`。同时移除了不再需要的 `Optional` 和 `Iterables` 导入。

### `spark/v3.5/spark/src/main/java/.../PublishChangesProcedure.java` (+16/-11 lines)

**修改目的**：同 v3.4，重写快照查找逻辑。

**工作逻辑**：与 v3.4 改动完全一致。

### `spark/v4.0/spark/src/main/java/.../PublishChangesProcedure.java` (+16/-11 lines)

**修改目的**：同 v3.4，重写快照查找逻辑。

**工作逻辑**：与 v3.4 改动基本一致。v4.0 版本额外保留了 `java.util.Iterator` 导入（该版本 `asScanIterator` 返回迭代器），其余逻辑相同。

### `spark/v4.1/spark/src/main/java/.../PublishChangesProcedure.java` (+16/-11 lines)

**修改目的**：同 v3.4，重写快照查找逻辑。

**工作逻辑**：与 v4.0 改动一致，保留 `Iterator` 导入并使用 `asScanIterator` 返回输出。

### `spark/v3.4/spark-extensions/src/test/java/.../TestPublishChangesProcedure.java` (+20/-0 lines)

**修改目的**：添加重复 `wap_id` 场景的测试。

**工作逻辑**：
新增 `testApplyDuplicateWapId` 测试：创建表并启用 WAP（`WRITE_AUDIT_PUBLISH_ENABLED`），设置 `spark.wap.id` 为 `"wap_id_1"`，连续执行两次 `INSERT INTO`（每次写入会产生一个带相同 wap_id 的暂存快照）。随后调用 `CALL system.publish_changes` 并断言抛出 `ValidationException`，消息为 "Cannot apply non-unique WAP ID. Found multiple snapshots with WAP ID 'wap_id_1'"。

### `spark/v3.5/spark-extensions/src/test/java/.../TestPublishChangesProcedure.java` (+20/-0 lines)

**修改目的**：同 v3.4，添加重复 `wap_id` 测试。

**工作逻辑**：与 v3.4 测试完全一致。

### `spark/v4.0/spark-extensions/src/test/java/.../TestPublishChangesProcedure.java` (+20/-0 lines)

**修改目的**：同 v3.4，添加重复 `wap_id` 测试。

**工作逻辑**：与 v3.4 测试完全一致。

### `spark/v4.1/spark-extensions/src/test/java/.../TestPublishChangesProcedure.java` (+20/-0 lines)

**修改目的**：同 v3.4，添加重复 `wap_id` 测试。

**工作逻辑**：与 v3.4 测试完全一致。

## 总结

本提交修复了 `publish_changes` 存储过程在存在多个匹配 WAP ID 快照时静默选择第一个而忽略歧义的数据正确性风险，改为显式遍历所有快照并在发现重复时抛出 `ValidationException`。改动覆盖 Spark 3.4/3.5/4.0/4.1 四个版本并同步更新文档与测试，遵循快速失败原则，帮助用户及时发现并处理 WAP ID 冲突，避免非预期的快照发布。
