# 提交 0130：Spark: Fix usage of staging location when optimizing metadata (#8959)

## 提交信息

- **序号**：0130 / 4088
- **哈希**：2c890109c17bfb971490cab007be23029b81bad8
- **短哈希**：2c890109c
- **日期**：2023-11-02 20:39:45 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark: Fix usage of staging location when optimizing metadata (#8959)
- **PR/Issue**：#8959

## 总体目的

这个提交修复了 `RewriteManifestsSparkAction` 在"直接提交"模式下错误使用用户提供的 staging location 的缺陷，避免 manifest 被写到表元数据目录之外的位置而破坏表的文件组织。

`RewriteManifestsSparkAction` 是 Iceberg Spark 模块中用于重写/合并 manifest 的动作（即"优化元数据"）。它将旧的若干 manifest 重写为新的 manifest，并以一次 commit 替换。该动作支持通过 `stagingLocation(String)` 指定一个临时存放新 manifest 的目录。manifest 的提交有两种模式，取决于表格式版本与 `snapshot-id-inheritance.enabled` 属性：

- **暂存模式（staging）**：当 format version 为 1 且未开启 snapshot ID 继承时启用。新 manifest 先写到 staging location，commit 时由 Iceberg 将其复制/重命名到元数据目录并按引用提交，commit 后需删除 staging location 中的临时副本。此时 staging location 真正起"暂存"作用。
- **直接提交模式（direct commit）**：当 format version >= 2 或开启了 snapshot ID 继承时采用。新 manifest 直接写到最终位置（元数据目录），commit 时直接按路径引用，无需事后删除。此时不存在"暂存"概念，manifest 必须落在元数据目录下。

本提交前的缺陷在于：`stagingLocation` 字段在 setter 中被无条件赋值，且该字段同时被用作 manifest 的实际写入目录（在 `toManifests` 调用链中传递）。这意味着当用户对一个 v2 表或开启了 snapshot ID 继承的表调用 `stagingLocation(customPath)` 时，新 manifest 会被写到 `customPath`，然后以直接提交模式按该路径引用并提交。结果是 manifest 永久地留在了用户提供的临时目录（表元数据目录之外），而非表的 metadata 目录下。这与用户对"staging location 是临时区"的预期相悖，更严重的是破坏了 Iceberg 表 manifest 应位于元数据目录的文件组织约定，可能导致后续表操作、清理、迁移时找不到或误删 manifest。

本提交通过引入 `shouldStageManifests` 标志（在构造时一次性计算），将 staging location 的语义与提交模式绑定：仅当确实处于暂存模式时才采纳用户提供的 staging location 作为输出目录；否则忽略它并输出警告，manifest 始终写到元数据目录。同时将字段重命名为 `outputLocation` 以准确反映其"实际输出目录"而非单纯"暂存目录"的语义。修复同步应用到 Spark 3.2/3.3/3.4/3.5 四个版本模块，并扩展测试验证 manifest 落位正确性。

## 如何达成设计目的

整体设计思路是"提前判定提交模式、绑定 staging 语义、统一输出目录"。具体做法：

1. 在构造方法中一次性计算 `shouldStageManifests = formatVersion == 1 && !snapshotIdInheritanceEnabled`，避免在 `replaceManifests` 中重复读取属性。
2. 将原 `stagingLocation` 字段重命名为 `outputLocation`，因为它承担的是"manifest 实际写入目录"职责，而非仅暂存。
3. 在 `stagingLocation(String)` setter 中加入条件：仅当 `shouldStageManifests` 为真时才将自定义路径赋给 `outputLocation`，否则记一条 warn 日志说明已忽略，`outputLocation` 保持为构造时默认的元数据目录。
4. `replaceManifests` 中提交后是否删除新 manifest 的判断，由原来的 `formatVersion == 1 && !snapshotIdInheritanceEnabled` 改为直接使用 `shouldStageManifests`，语义更清晰且与 setter 的判定一致。
5. 更新类 Javadoc 明确说明 staging location 在直接提交模式下会被忽略。
6. 测试中新增 `assertManifestsLocation` 辅助方法，按 `shouldStageManifests` 与是否传入 staging location 断言 manifest 路径落位。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：修复 staging location 在直接提交模式下被错误采纳的问题，绑定 staging 语义与提交模式。以下以 Spark 3.5 版本为例说明，Spark 3.2/3.3/3.4 版本的对应文件改动完全一致。

**工作逻辑**：

1. **新增 `shouldStageManifests` 字段并在构造时计算**：新增 `private final boolean shouldStageManifests;`，在构造方法中读取 `TableProperties.SNAPSHOT_ID_INHERITANCE_ENABLED`（默认 `SNAPSHOT_ID_INHERITANCE_ENABLED_DEFAULT`），赋值 `this.shouldStageManifests = formatVersion == 1 && !snapshotIdInheritanceEnabled;`。这一判定与原 `replaceManifests` 中的删除判断逻辑一致，但提前到构造阶段固化，供 setter 与提交逻辑共用。

2. **字段重命名 `stagingLocation` → `outputLocation`**：原 `private String stagingLocation = null;` 改为 `private String outputLocation = null;`。构造方法中默认值仍为元数据目录（`metadataFilePath.getParent().toString()`），相关注释从"default the staging location"改为"default the output location"。`toManifests(...)` 调用链中两处传参由 `stagingLocation` 改为 `outputLocation`。重命名使字段名准确反映其"实际输出目录"语义。

3. **`stagingLocation(String)` setter 加入条件判定**：原实现 `this.stagingLocation = newStagingLocation;` 无条件赋值。新实现：
   ```java
   if (shouldStageManifests) {
     this.outputLocation = newStagingLocation;
   } else {
     LOG.warn("Ignoring provided staging location as new manifests will be committed directly");
   }
   ```
   即仅当处于暂存模式时才采纳自定义路径；直接提交模式下忽略并告警，`outputLocation` 保持元数据目录默认值。这是本次修复的核心。

4. **`execute()` 描述简化**：原 `desc` 包含 staging location 信息（`"Rewriting manifests (staging location=%s) of %s"`），因 staging location 不再总是生效，简化为 `"Rewriting manifests in %s"`（仅含表名）。

5. **`replaceManifests` 提交后删除逻辑改用 `shouldStageManifests`**：原先在方法内部重新读取 `snapshotIdInheritanceEnabled` 属性并判断 `if (formatVersion == 1 && !snapshotIdInheritanceEnabled)`。现在移除这段属性读取，直接 `if (shouldStageManifests)` 删除已提交的新 manifest（因为暂存模式下 commit 会创建自己的副本，staging 副本需清理）。这消除了重复计算并保证与 setter 判定一致。

6. **类 Javadoc 更新**：将"configure a custom location for new manifests via `stagingLocation`"改为"configure a custom location for staged manifests via `stagingLocation(String)`"，并新增说明："The provided staging location will be ignored if snapshot ID inheritance is enabled. In such cases, the manifests are always written to the metadata folder and committed without staging." 明确告知调用方 staging location 的适用条件。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：验证 manifest 落位路径在不同提交模式下符合预期，覆盖 staging 被采纳与被忽略两种情形。Spark 3.2/3.3/3.4 版本测试改动一致。

**工作逻辑**：

1. **新增 `shouldStageManifests` 测试字段**：在构造方法中根据 `formatVersion == 1 && snapshotIdInheritanceEnabled.equals("false")` 计算，与生产代码判定逻辑一致。该字段驱动后续断言。

2. **新增 `assertManifestsLocation` 辅助方法**（两个重载）：
   ```java
   private void assertManifestsLocation(Iterable<ManifestFile> manifests, String stagingLocation) {
     if (shouldStageManifests && stagingLocation != null) {
       assertThat(manifests).allMatch(manifest -> manifest.path().startsWith(stagingLocation));
     } else {
       assertThat(manifests).allMatch(manifest -> manifest.path().startsWith(tableLocation));
     }
   }
   ```
   逻辑：仅当处于暂存模式且显式传入了 staging location 时，断言新 manifest 落在 staging location 下；否则断言落在 table location（即元数据目录）下。无参重载以 `null` 调用有参版本，验证未传 staging location 时一律落 table location。

3. **在各测试用例中插入落位断言**：多个已有测试在断言 `addedManifests` 数量后，调用 `assertManifestsLocation(result.addedManifests())` 或 `assertManifestsLocation(result.addedManifests(), stagingLocation)`。对于显式调用 `.stagingLocation(...)` 的用例，先将 staging location 存入局部变量再传入断言（原先直接 `temp.newFolder().toString()` 内联，无法复用路径）。这样无论表是 v1/v2、是否开启继承，都能验证 manifest 落位正确：暂存模式 + 自定义路径 → 落 staging；其余情形 → 落 table location。这直接覆盖了本提交修复的缺陷场景。

## 小结

本提交通过在构造时固化 `shouldStageManifests` 标志、在 setter 中按该标志决定是否采纳自定义 staging location、并统一输出目录语义，修复了 `RewriteManifestsSparkAction` 在直接提交模式下将 manifest 错误写到表元数据目录之外的问题，保证 manifest 始终落在正确位置，并在四个 Spark 版本模块同步修复与测试。
