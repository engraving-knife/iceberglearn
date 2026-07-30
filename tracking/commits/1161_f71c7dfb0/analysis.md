# 提交 1161：Core: Update metadata location without updating lastUpdatedMillis (#11151)

## 提交信息

- **序号**：1161 / 4088
- **哈希**：f71c7dfb0fe669cff3acc0646666cbc2bb05de4d
- **短哈希**：f71c7dfb0
- **日期**：2024-09-18（Wed Sep 18 07:42:23 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Update metadata location without updating lastUpdatedMillis (#11151)
  - 提交消息正文说明：使用 `TableMetadata.buildFrom(metadata).withMetadataLocation(metadataLocation).build()` 会做两件事：1) 更新 `lastUpdatedMillis`；2) 写一条新的 `metadata-log` 条目。但在 `LoadTableResponse` / `RestSessionCatalog` 的场景下，目的只是更新 metadata location，不应该触发上述两个副作用。
- **PR/Issue**：#11151

## 总体目的

`TableMetadata.Builder.withMetadataLocation(String)` 用于给一份 `TableMetadata` 标注它的元数据 JSON 文件所在路径（即 metadata location）。这个方法在两个典型场景被调用：

1. **真正的 commit 路径**：commit 时新写一份 metadata JSON 文件，然后调用 `withMetadataLocation(newLocation)` 把新文件路径挂到 metadata 上。这里 `lastUpdatedMillis` 应当更新（确实发生了改动），且 `previousFileLocation` 应当被记录到 `metadata-log` 中（旧 metadata 文件成为历史）。
2. **REST Catalog 加载路径**：`LoadTableResponse` / `RestSessionCatalog` 在加载表时，从 REST 服务器拿到 `TableMetadata` JSON 内容与它所在的 location，需要把 location "标注"到 metadata 对象上。这里**没有发生任何表改动**，只是把 metadata 与它的存储位置关联起来，`lastUpdatedMillis` 应保持不变，也不应该往 `metadata-log` 里塞新条目。

但原来的实现不区分这两种场景，`build()` 时总是会：
- 把 `lastUpdatedMillis` 更新为 `System.currentTimeMillis()`；
- 把原 metadata 的 `metadataFileLocation` 作为 `previousFileLocation`，从而在 `metadata-log` 中新增一条历史记录。

这导致 REST Catalog 每次加载表都会"污染" `TableMetadata`：`lastUpdatedMillis` 被错误地刷成当前时间（让表的"最后更新时间"失去意义），`metadata-log` 被塞入大量虚假历史条目（每次 load 都新增一条）。

本提交修复 `withMetadataLocation` 的语义：当基于已有 `TableMetadata`（即 `base != null`）调用时，主动把 `lastUpdatedMillis` 沿用 base 的值、把 `previousFileLocation` 置为 null，从而避免在 `build()` 时触发上述两个副作用。这样 REST Catalog 的加载路径就能"只更新 metadata location"而不引入虚假的更新时间与历史记录。

## 如何达成设计目的

在 `TableMetadata.Builder.withMetadataLocation(String newMetadataLocation)` 中追加逻辑：
- 设置 `this.metadataLocation = newMetadataLocation`（原有逻辑保留）。
- 若 `base != null`（即通过 `buildFrom(existingMetadata)` 起的 builder）：
  - `this.lastUpdatedMillis = base.lastUpdatedMillis();` —— 沿用原 metadata 的最后更新时间，不被 `build()` 中"未显式设置则取当前时间"的逻辑覆盖。
  - `this.previousFileLocation = null;` —— 显式置空，让 `build()` 不会把原 metadata location 写入 `metadata-log`，避免产生虚假历史条目。
- 注释说明这是安全的：因为 `withMetadataLocation` 仅标注位置不引起任何表改动，且 builder 的设计保证了 metadata location 设定后不能再叠加其他改动，所以"沿用 base 的时间戳 + 不写历史"不会掩盖真实的 commit 信息。

测试侧在 `TestTableMetadata` 中新增 `onlyMetadataLocationIsUpdatedWithoutTimestampAndMetadataLogEntry`，构造一份 metadata 并连续两次只改 metadata location，断言：
- `lastUpdatedMillis` 在多次 `withMetadataLocation` 后保持不变；
- `metadataFileLocation()` 反映最新的 location；
- `previousFiles()`（即 `metadata-log`）始终为空。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：让 `withMetadataLocation` 在基于已有 metadata 构建时不更新 `lastUpdatedMillis` 与 `previousFileLocation`。

**工作逻辑**：在 `Builder.withMetadataLocation(String newMetadataLocation)` 方法中追加：

```java
public Builder withMetadataLocation(String newMetadataLocation) {
  this.metadataLocation = newMetadataLocation;
  if (null != base) {
    // carry over lastUpdatedMillis from base and set previousFileLocation to null to avoid
    // writing a new metadata log entry
    // this is safe since setting metadata location doesn't cause any changes and no other
    // changes can be added when metadata location is configured
    this.lastUpdatedMillis = base.lastUpdatedMillis();
    this.previousFileLocation = null;
  }

  return this;
}
```

要点：
- `base` 字段是 `buildFrom(existingMetadata)` 时保存的原 metadata 引用；若 builder 是 `buildFromEmpty()` 起的，`base` 为 null，逻辑不变（保持原有"新表"行为）。
- 显式赋值 `this.lastUpdatedMillis` 覆盖了 `build()` 中"若未设置则取 `System.currentTimeMillis()`"的默认行为。
- 显式置 `this.previousFileLocation = null` 让 `build()` 不会把原 metadata location 加入 `metadata-log`（`build()` 中会检查 `previousFileLocation` 非空才追加历史条目）。
- 注释明确说明安全性：`withMetadataLocation` 不引起表改动，且 builder 设计上不允许在设定 metadata location 后再叠加其他改动，所以该处理不会掩盖真实 commit。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：覆盖"只更新 metadata location"时的不变量。

**工作逻辑**：新增 `onlyMetadataLocationIsUpdatedWithoutTimestampAndMetadataLogEntry` 测试：
1. 用 `TableMetadata.buildFromEmpty()` 构造一份初始 metadata，`withMetadataLocation("original-metadata-location").build()`。断言 `previousFiles()` 为空（新建场景，无历史）、`metadataFileLocation()` 等于 `"original-metadata-location"`。
2. 用 `TableMetadata.buildFrom(metadata).withMetadataLocation("new-metadata-location").build()` 基于上一步的 metadata 只改 location。断言：
   - `newMetadata.lastUpdatedMillis()` 等于 `metadata.lastUpdatedMillis()`（时间戳未变）。
   - `newMetadata.metadataFileLocation()` 等于 `"new-metadata-location"`。
   - `newMetadata.previousFiles()` 为空（未产生新历史条目）。
3. 再用 `TableMetadata.buildFrom(newMetadata).withMetadataLocation("updated-metadata-location").build()` 连续第三次只改 location。同样断言时间戳不变、location 已更新、历史仍为空。

该测试覆盖了连续多次"只更新 location"的场景，确保每次都不会累积历史条目或刷新时间戳。

## 小结

- **成效**：修复 REST Catalog 加载表时错误更新 `lastUpdatedMillis` 与污染 `metadata-log` 的问题。`lastUpdatedMillis` 现在能真实反映表的最后一次 commit 时间（而非最近一次 REST 加载时间），`metadata-log` 不再被虚假条目污染，便于审计与元数据管理。修复通过让 `withMetadataLocation` 在基于已有 metadata 构建时显式沿用 base 的时间戳与置空 `previousFileLocation` 实现。
- **影响范围**：仅 `core` 模块的 `TableMetadata.Builder.withMetadataLocation` 一个方法，约 9 行新增逻辑；外加一个测试用例。无 API 签名变更，无元数据格式变更。
- **回迁到 1.4.x 的注意事项**：
  1. 这是一个 bug 修复，回迁到 1.4.x 是有价值的，尤其当 1.4.x 的 REST Catalog 频繁加载表时该问题会累积放大（`metadata-log` 越来越长，`lastUpdatedMillis` 失真）。
  2. 改动很小且自包含，只需回迁 `TableMetadata.java` 中 `withMetadataLocation` 方法的 9 行新增逻辑与对应的测试用例，不依赖其他提交。
  3. 需要确认 1.4.x 的 `TableMetadata.Builder` 已有 `base` 字段（这是 `buildFrom(existingMetadata)` 模式引入时就存在的）。1.4.0 应已具备。
  4. 行为变化：回迁后，1.4.x 的 REST Catalog 加载表时 `lastUpdatedMillis` 与 `metadata-log` 不再被错误更新。这与之前的行为不完全一致——若有下游应用依赖"加载后 `lastUpdatedMillis` 变成当前时间"这一副作用（虽然不太可能），需要注意。但通常该修复是纯粹的改进。
  5. 不改变 metadata JSON 的序列化格式，与 1.4.x 已有表完全兼容，无升级路径。
  6. 真正的 commit 路径不受影响：commit 时 `base` 通常为 null（`buildFromEmpty`）或场景不同，且 commit 流程会显式设置 `lastUpdatedMillis` 与 `previousFileLocation`，不走本修复的"沿用 base"分支。
