# 提交 1132：Core: Fix setting hasNewDataFile flag in MergingSnapshotProducer (#11088)

## 提交信息

- **序号**：1132 / 4088
- **哈希**：6e05ae022c71a51778cbfcf1585a92b28d796255
- **短哈希**：6e05ae022
- **日期**：2024-09-06（Fri Sep 6 21:29:47 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Fix setting hasNewDataFile flag in MergingSnapshotProducer (#11088)
- **PR/Issue**：#11088

## 总体目的

`MergingSnapshotProducer` 是 Iceberg core 中处理"合并型快照"（如 append、overwrite、upsert 等涉及数据文件增删的操作）的抽象基类。它内部用一个布尔标志 `hasNewDataFiles` 来标记"是否存在尚未写入 manifest 的新数据文件"，并在 `newDataFilesAsManifests()` 方法中据此决定是否需要把 `newDataFilesBySpec`（按分区 spec 分组的新数据文件）滚动写入新的 manifest，以及是否需要丢弃旧的缓存 manifest 重新生成。

原有的实现把 `this.hasNewDataFiles = false;` 这条重置语句放在了 `newDataFilesBySpec.forEach(...)` 的 lambda 闭包内部，也就是说：每当处理完一个 spec 对应的新数据文件、写完该 spec 的 manifest 后，就立即把标志清零。如果 `newDataFilesBySpec` 有多个分区 spec，标志会在第一个 spec 处理完就被置为 false，而后续 spec 的写入尚未完成，这在语义上是不正确的（标志本意是"全部新数据文件都已落盘到 manifest"）。同时，把对外部对象状态的修改埋在遍历回调中也属于不良写法，容易让人误读为"每个 spec 各自维护一份标志"。

本提交把这条重置语句从 lambda 内部移出到 `forEach` 之后、但仍保留在 `if (cachedNewDataManifests.isEmpty())` 块内，确保只有在所有 spec 的新 manifest 都写完之后才一次性把 `hasNewDataFiles` 置为 false，使标志语义与实际行为一致。

## 如何达成设计目的

仅修改 `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` 中 `newDataFilesAsManifests()` 方法内的两行位置：删除 lambda 内 `this.cachedNewDataManifests.addAll(writer.toManifestFiles());` 之后那行 `this.hasNewDataFiles = false;`，并在 `forEach` 调用结束、`if` 块结束之前补回同样的一行。这样既保留了"缓存为空时才写入新 manifest"的惰性逻辑，又把标志重置时机修正为"全部 spec 写完后"。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`

**修改目的**：修正 `hasNewDataFiles` 标志的重置时机。

**工作逻辑**：`newDataFilesAsManifests()` 方法的核心流程如下：

1. 若 `hasNewDataFiles` 为 true 且 `cachedNewDataManifests` 非空，说明此前缓存的 manifest 已过期（又有新文件加入），需要先删除旧 manifest 文件并清空缓存。
2. 若缓存为空，则遍历 `newDataFilesBySpec`，为每个 spec 用 `RollingManifestWriter` 把新数据文件写入 manifest，并把结果加入 `cachedNewDataManifests`。
3. 返回缓存中的 manifest 列表。

修改前，第 2 步的遍历 lambda 在每次写完一个 spec 的 manifest 后都执行 `this.hasNewDataFiles = false;`，导致：

- 当存在多个 spec 时，第一个 spec 写完就把标志清零。若此期间该 producer 被并发访问（虽然实际场景下不太可能，但代码契约上不应假设），后续 spec 的写入会与"标志已为 false"的状态不一致。
- 更关键的是，把对 producer 实例状态的副作用藏在遍历回调中，违反"标志应在全部新文件落盘后才清零"的语义。

修改后，`this.hasNewDataFiles = false;` 被移到 `forEach` 之外，仍在 `if (cachedNewDataManifests.isEmpty())` 块内，表示"缓存为空、需要重新生成 manifest"这一整个分支执行完毕（所有 spec 都写完）后才清零标志。diff 形式如下：

```java
              this.cachedNewDataManifests.addAll(writer.toManifestFiles());
-              this.hasNewDataFiles = false;
            } catch (IOException e) {
              throw new RuntimeIOException(e, "Failed to close manifest writer");
            }
          });
+      this.hasNewDataFiles = false;
    }
```

## 小结

- **成效**：`hasNewDataFiles` 标志现在只在所有分区 spec 的新数据文件都写入 manifest 之后才被重置，语义与实际行为一致，消除了多 spec 场景下标志提前清零的潜在隐患，并让代码意图更清晰。
- **影响范围**：仅 `MergingSnapshotProducer.java` 一个文件、两行位置调整（删一行加一行），无新增逻辑、无 API 变更。
- **回迁到 1.4.x 的注意事项**：这是一个针对 core 写路径的语义修正，属于低风险但正确的 bug 修复。1.4.x 作为维护分支同样会执行 `MergingSnapshotProducer` 的代码路径，若 1.4.x 中该行仍在 lambda 内（视 1.4.x 分支与 main 的差异而定），**建议回迁**以保持一致并避免多 spec 场景下的潜在问题。回迁时只需复制这一处位置调整，无需配套改动。注意该改动本身不引入兼容性问题，且不影响已写入的 manifest 或快照格式。
