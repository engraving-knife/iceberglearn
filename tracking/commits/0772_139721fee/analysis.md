# 提交 0772：Remove unused manifest predicate (#10339)

## 提交信息

- **序号**：0772 / 4088
- **哈希**：139721fee680fa22c69d28c296563f9acabab8e2
- **短哈希**：139721fee
- **日期**：2024-05-16 10:21:49 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Remove unused manifest predicate (#10339)
- **PR/Issue**：#10339

## 总体目的

这个提交是一次代码清理（dead code removal），移除 `ManifestGroup` 类中未被任何调用方使用的 `manifestPredicate` 字段、对应的 `filterManifests` 方法，以及扫描流程中对该谓词的实际过滤调用。这些代码虽然存在且会被执行（过滤逻辑在 `plan` 流程中确实被调用），但其过滤条件始终为 `m -> true`（永远返回 true），因为没有任何外部调用方会调用 `filterManifests` 来注册额外的 manifest 级别过滤条件，导致该过滤形同虚设。移除它们可以减少扫描路径上的一次无效迭代过滤，同时消除维护上的混淆。

## 如何达成设计目的

`ManifestGroup` 是 Iceberg 核心扫描引擎中负责将一组 manifest 文件规划为扫描任务（`ScanTask`）的关键类。它内部维护了多个可选的过滤条件，包括 `manifestPredicate`（manifest 文件级谓词）、`manifestEntryPredicate`（manifest 条目级谓词）、`dataFilter`（数据级表达式过滤）等。这些谓词通过对应的 `filterXxx` 方法由调用方按需设置。

提交者通过代码审查发现，`ManifestGroup.filterManifests(Predicate<ManifestFile>)` 方法在整个代码库中没有任何调用方。对该方法的全仓库搜索确认：仓库中虽然存在其他类（如 `BaseDistributedDataScan`、`ManifestFilterManager`）的同名方法 `filterManifests`，但那些是不同类上的独立方法，与 `ManifestGroup.filterManifests` 无关。因此 `manifestPredicate` 字段始终保持其初始值 `m -> true`。

由于该谓词恒为 true，在 `plan` 方法中对 `matchingManifests` 执行的 `CloseableIterable.filter(scanMetrics.skippedDataManifests(), matchingManifests, manifestPredicate)` 调用不会过滤掉任何 manifest，却仍然会遍历整个 manifest 集合执行一次无意义的谓词判断，并可能影响 `skippedDataManifests` 计数器的语义（一个 manifest 也不会被计入"跳过"）。因此本次清理一并移除了字段声明、构造函数中的初始化、`filterManifests` 方法、以及 `plan` 中的过滤调用，保留紧随其后的 `CloseableIterable.count(scanMetrics.scannedDataManifests(), matchingManifests)` 计数调用不变。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestGroup.java`

**修改目的**：移除未使用的 `manifestPredicate` 字段及其相关方法与过滤调用。

**工作逻辑**：

具体改动分为四处：

1. **移除字段声明**（约第 70 行附近）：删除 `private Predicate<ManifestFile> manifestPredicate;` 字段。该字段此前用于存储 manifest 文件级的过滤谓词。

2. **移除构造函数初始化**（约第 104 行附近的构造函数）：删除 `this.manifestPredicate = m -> true;`。此前构造函数将谓词初始化为恒真 lambda，作为默认值。

3. **移除 `filterManifests` 方法**（约第 132 行附近）：删除整个方法：
   ```java
   ManifestGroup filterManifests(Predicate<ManifestFile> newManifestPredicate) {
     this.manifestPredicate = manifestPredicate.and(newManifestPredicate);
     return this;
   }
   ```
   该方法是外部设置 `manifestPredicate` 的唯一入口，由于无调用方，整段删除。

4. **移除 `plan` 方法中的过滤调用**（约第 298-300 行附近）：删除以下过滤代码：
   ```java
   matchingManifests =
       CloseableIterable.filter(
           scanMetrics.skippedDataManifests(), matchingManifests, manifestPredicate);
   ```
   删除后，`matchingManifests` 在经过 `hasAddedFiles() || hasDeletedFiles()` 过滤后，直接进入 `CloseableIterable.count(scanMetrics.scannedDataManifests(), matchingManifests)` 计数阶段，不再经过一次恒真的谓词过滤。

注意：`manifestEntryPredicate` 字段、`filterManifestEntries` 方法及对应的条目级过滤逻辑均保留不动，因为 `filterManifestEntries` 仍有实际调用方，属于在用代码。

**diff 摘要**：1 file changed, 10 deletions(-)。

## 小结

### 成效

移除了 `ManifestGroup` 中无人调用的 `filterManifests` 方法及配套的 `manifestPredicate` 字段，消除了扫描路径上一处无效的 manifest 级过滤迭代。虽然性能影响微小（仅省去一次恒真谓词的全集遍历），但更重要的是减少了代码的认知负担：后续维护者不会再误以为该谓词有实际作用，也不会误用 `filterManifests` 注册不会被任何逻辑触发的过滤条件。

### 影响范围

改动局限于 `ManifestGroup` 内部实现，不改变任何公共 API 或扫描行为（因为被移除的过滤恒为 true，删与不删结果一致）。所有使用 `ManifestGroup` 的扫描入口（如各种 `TableScan` 实现）行为保持不变。`manifestEntryPredicate` 等其他过滤机制不受影响。

### 回迁注意事项

- 回迁到 1.4.x 分支前，先确认 1.4.x 分支的 `ManifestGroup` 中 `filterManifests` 方法是否同样无调用方。若 1.4.x 分支有任何自定义代码（如厂商扩展）调用了 `ManifestGroup.filterManifests`，则不可直接回迁，需保留该方法或调整调用方。
- 由于 `filterManifests` 是包级可见（package-private）方法，同包内的其他 Iceberg 核心类理论上可调用。回迁时应在 1.4.x 分支上执行 `grep -rn "filterManifests" core/src/` 确认 `ManifestGroup` 上的该方法确实无同包调用方。
- 移除 `CloseableIterable.filter(scanMetrics.skippedDataManifests(), ...)` 调用后，`skippedDataManifests` 指标在该处不再被触碰。若 1.4.x 分支有依赖该指标在此处被消费的逻辑（极不可能），需特别留意。该指标在其他位置（如 manifest 分区过滤处）仍会被正常更新。
