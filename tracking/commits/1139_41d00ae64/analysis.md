# 提交 1139：Core: Prevent incremental file cleanup when expiring specified snapshots (#10983)

## 提交信息

- **序号**：1139 / 4088
- **哈希**：41d00ae64df218f8f955b5d2fa076125bb43a4e5
- **短哈希**：41d00ae64
- **日期**：2024-09-10（Tue Sep 10 00:03:05 2024 +0800）
- **作者**：dongwang <mingwbd@gmail.com>
- **提交说明**：Core: Prevent incremental file cleanup when expiring specified snapshots (#10983)
- **PR/Issue**：#10983

## 总体目的

`RemoveSnapshots` 是 Iceberg core 中实现 `ExpireSnapshots` 接口的核心类，负责"过期快照"——删除不再被保留的旧 snapshot 及其关联的元数据与数据/删除文件。它支持两种文件清理策略：

- **增量清理（incremental cleanup）**：通过比较"过期前"与"过期后"的快照状态，只清理本次过期操作新暴露出来的、不再被任何存活 snapshot 引用的文件。这种方式较快，但前提是"过期的是一整段连续的旧快照"。
- **全量清理（non-incremental）**：遍历所有存活 snapshot，重新计算哪些文件不再被引用。这种方式更彻底，适用于复杂的过期场景。

`incrementalCleanup` 默认为 `null`，会在 `cleanExpiredSnapshots()` 中根据 `current.refs().size() == 1`（只有一个 main 分支引用）自动决定是否启用增量清理。

问题在于：当调用方通过 `expireSnapshotId(long)` **显式指定要过期某个具体的 snapshot id** 时，被过期的不是"一段连续的旧快照"，而是中间某个特定快照。此时增量清理的"前/后状态差"逻辑不再成立——它假设过期的是连续尾部，会把一些本应仍被其它存活 snapshot 引用的文件误判为可删除，造成**数据文件被错误删除**的严重后果。

本提交在 `RemoveSnapshots` 中新增 `specifiedSnapshotId` 标志，在 `expireSnapshotId(...)` 被调用时置为 true；并在 `cleanExpiredSnapshots()` 开头检查：若显式指定了 snapshot id，则

- 若调用方还显式要求 `incrementalCleanup=true`，直接抛 `UnsupportedOperationException`，明确告知"指定 snapshot id 时不能增量清理"；
- 若调用方未显式指定（`incrementalCleanup == null`），则强制设为 `false`，走全量清理，避免静默地误删文件。

同时配套修改两个测试用例，把原本通过私有 `removeSnapshots(table)` 工具方法构造 `RemoveSnapshots`（绕过公开 API）改为使用 `table.expireSnapshots()` 公开 API，并新增一个测试 `testIncrementalCleanupFailsWhenExpiringSnapshotId` 验证显式同时指定 `withIncrementalCleanup(true)` 与 `expireSnapshotId(...)` 会抛出预期异常。

## 如何达成设计目的

1. 在 `RemoveSnapshots` 类中新增一个布尔成员 `specifiedSnapshotId`，初始为 `false`。
2. 在 `expireSnapshotId(long)` 方法中，把 snapshot id 加入待删除集合的同时，把 `specifiedSnapshotId` 置为 `true`。
3. 在 `cleanExpiredSnapshots()` 方法开头（在原有的"若 `incrementalCleanup == null` 则按 refs 数量自动决定"逻辑之前）插入检查：若 `specifiedSnapshotId` 为 true，则当 `incrementalCleanup` 被显式设为 true 时抛 `UnsupportedOperationException`，否则强制设为 `false`。
4. 修改两个测试用例：把 `removeSnapshots(table)...` 改为 `table.expireSnapshots()...`，使测试通过公开 API 触发新增的标志逻辑。
5. 新增测试用例验证"同时显式指定增量清理与 snapshot id"时抛异常。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RemoveSnapshots.java`

**修改目的**：在显式指定 snapshot id 过期时，禁止增量清理以防止误删仍被引用的数据文件。

**工作逻辑**：

1. 新增成员变量（第 87 行附近）：

```java
   private Boolean incrementalCleanup;
+  private boolean specifiedSnapshotId = false;
```

2. 在 `expireSnapshotId` 中置标志（第 117 行附近）：

```java
   public ExpireSnapshots expireSnapshotId(long expireSnapshotId) {
     LOG.info("Expiring snapshot with id: {}", expireSnapshotId);
     idsToRemove.add(expireSnapshotId);
+    specifiedSnapshotId = true;
     return this;
   }
```

3. 在 `cleanExpiredSnapshots()` 开头插入守卫逻辑（第 323 行附近）：

```java
   private void cleanExpiredSnapshots() {
     TableMetadata current = ops.refresh();

+    if (specifiedSnapshotId) {
+      if (incrementalCleanup != null && incrementalCleanup) {
+        throw new UnsupportedOperationException(
+            "Cannot clean files incrementally when snapshot IDs are specified");
+      }
+
+      incrementalCleanup = false;
+    }
+
     if (incrementalCleanup == null) {
       incrementalCleanup = current.refs().size() == 1;
     }
```

逻辑解读：

- 若 `specifiedSnapshotId` 为 true（即调用方点了具体某个 snapshot id）：
  - 若 `incrementalCleanup` 被显式设为 `true`（非 null 且为 true），说明调用方既要点名过期又要求增量清理，二者不兼容，直接抛异常，fail-fast。
  - 否则（`incrementalCleanup` 为 null，即调用方未指定）强制设为 `false`，走全量清理，安全地处理"点名过期"场景。
- 若 `specifiedSnapshotId` 为 false，行为不变，沿用原有"按 refs 数量自动决定增量清理"的逻辑。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java`

**修改目的**：让相关测试通过公开 API 触发新增的标志逻辑，并新增覆盖异常路径的测试。

**工作逻辑**：

1. 修改 `testExpireOldSnapshotCount` 中的调用方式（第 370 行附近）：

```java
-    removeSnapshots(table).expireSnapshotId(firstSnapshotId).retainLast(3).commit();
+    table.expireSnapshots().expireSnapshotId(firstSnapshotId).retainLast(3).commit();
```

把原先通过测试工具方法 `removeSnapshots(table)`（返回 `RemoveSnapshots` 实例）改为通过公开的 `table.expireSnapshots()`。`table.expireSnapshots()` 返回 `ExpireSnapshots` 接口，内部同样构造 `RemoveSnapshots`，但走公开入口能正确触发新增的 `specifiedSnapshotId` 逻辑。

2. 修改 `testExpireSnapshotId` 中的调用方式（第 956 行附近）：

```java
-    removeSnapshots(table)
+    table
+        .expireSnapshots()
         .deleteWith(deletedFiles::add)
         .expireSnapshotId(snapshotB.snapshotId())
         .commit();
```

同样改为公开 API。

3. 新增测试 `testIncrementalCleanupFailsWhenExpiringSnapshotId`（第 1172 行附近）：

```java
+  @TestTemplate
+  public void testIncrementalCleanupFailsWhenExpiringSnapshotId() {
+    table.newAppend().appendFile(FILE_A).commit();
+    table.newDelete().deleteFile(FILE_A).commit();
+    long snapshotId = table.currentSnapshot().snapshotId();
+    table.newAppend().appendFile(FILE_B).commit();
+    waitUntilAfter(table.currentSnapshot().timestampMillis());
+    RemoveSnapshots removeSnapshots = (RemoveSnapshots) table.expireSnapshots();
+
+    assertThatThrownBy(
+            () ->
+                removeSnapshots
+                    .withIncrementalCleanup(true)
+                    .expireSnapshotId(snapshotId)
+                    .cleanExpiredFiles(true)
+                    .commit())
+        .isInstanceOf(UnsupportedOperationException.class)
+        .hasMessage("Cannot clean files incrementally when snapshot IDs are specified");
+  }
```

该测试构造一个包含 append(FILE_A) → delete(FILE_A) → append(FILE_B) 的表，取中间那个 delete 快照的 id，然后强制 `withIncrementalCleanup(true)` 并 `expireSnapshotId(snapshotId)`，断言 `commit()` 抛出 `UnsupportedOperationException` 且消息匹配。注意此处把 `table.expireSnapshots()` 的返回值强转为 `RemoveSnapshots`，是为了调用 `withIncrementalCleanup(true)` 这个仅存在于实现类上的方法（用于测试守卫逻辑）。

## 小结

- **成效**：当用户显式指定要过期某个具体 snapshot id 时，Iceberg 不再走增量清理（增量清理假设过期的是连续尾部快照，点名过期会破坏该假设并可能误删仍被引用的数据文件）。若用户同时显式要求增量清理，则 fail-fast 抛 `UnsupportedOperationException`；若用户未指定清理策略，则自动降级为全量清理。这是一项防止数据丢失的重要正确性修复。
- **影响范围**：`RemoveSnapshots.java` 新增 11 行（1 个成员 + 1 行设标志 + 9 行守卫块）；`TestRemoveSnapshots.java` 改 2 处调用、新增 1 个测试方法（共 +25/-2 行）。仅影响 core 的快照过期路径与对应测试。
- **回迁到 1.4.x 的注意事项**：这是一个涉及数据安全的核心正确性修复，**强烈建议回迁**到 1.4.x，以避免用户在 1.4.x 上通过 `expireSnapshotId` 点名过期时发生数据文件误删。回迁时需注意：
  - 确认 1.4.x 的 `RemoveSnapshots` 是否已有 `incrementalCleanup` 字段与 `withIncrementalCleanup` 方法（应已存在，本提交未引入这些）。
  - 测试中用到的 `assertThatThrownBy` 来自 AssertJ，1.4.x 测试基类应已引入；若未引入需配套调整。
  - 回迁后建议在 1.4.x 上跑一遍 `TestRemoveSnapshots` 全套，确认无回归。
  - 该改动是纯防御性逻辑，不改变快照格式或元数据，对存量表无影响，向后兼容。
