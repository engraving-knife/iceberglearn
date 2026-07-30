# 提交 1401：Core: Filter on live entries when reading the manifest (#9996)

## 提交信息

- **序号**：1401 / 4088
- **哈希**：3c4a710d2f087ff2d1220607424e9164a6f09281
- **短哈希**：3c4a710d2
- **日期**：2024-11-20（Wed Nov 20 09:48:27 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Core: Filter on live entries when reading the manifest (#9996)
- **PR/Issue**：#9996

## 总体目的

`ManifestFilterManager` 负责"按需重写 manifest 文件"，把不符合过滤条件（删除路径、分区删除、表达式过滤、过期删除文件等）的 entry 从 manifest 中剔除，输出一份新的、精简的 manifest。其核心方法 `filterManifestWithDeletedFiles` 遍历 manifest 的所有 entry，对每个 entry 判断是 `writer.delete(entry)`（标记删除）还是 `writer.existing(entry)`（保留为既有）。

此前的实现使用 `reader.entries()` 遍历，它会返回 manifest 中**所有** entry，包括状态为 `ManifestEntry.Status.DELETED` 的 entry（即历史上已被标记删除的文件）。然后在循环体内用 `if (entry.status() != ManifestEntry.Status.DELETED)` 把 DELETED entry 跳过。这意味着：

1. **读取浪费**：DELETED entry 虽然最终被跳过，但仍被从 manifest 文件中读出、反序列化、遍历，对大 manifest（百万级 entry）是明显的 I/O 与 CPU 浪费。
2. **代码冗余**：外层 `if (entry.status() != DELETED)` 包裹整段处理逻辑，多一层缩进，可读性差。

本提交改用 `reader.liveEntries()` 替代 `reader.entries()`：`liveEntries()` 在读取阶段就过滤掉 DELETED entry，只返回 ADDED 与 EXISTING 状态的 entry（统称"live"）。这样：

1. 避免读取与处理 DELETED entry，提升 manifest 重写性能；
2. 移除循环体内的 `if (entry.status() != DELETED)` 外层判断，代码更扁平、更清晰；
3. 由于 `liveEntries()` 已保证 entry 是 live 的，`markedForDelete` 计算中的 `entry.isLive()` 检查变得冗余但保留也无害（实际上 `liveEntries()` 返回的 entry `isLive()` 恒为 true，该条件永远成立，不影响逻辑）。

## 如何达成设计目的

1. 把 `reader.entries()` 改为 `reader.liveEntries()`，让读取端只产出 live entry。
2. 删除循环体内 `if (entry.status() != ManifestEntry.Status.DELETED) { ... } else { writer.existing(entry); }` 的外层 if/else 结构，把内层逻辑直接提到 `forEach` 顶层。
3. 内层逻辑保持不变：先计算 `markedForDelete`（综合路径删除、文件删除、分区删除、过期删除判断），再判断 `markedForDelete || evaluator.rowsMightMatch(file)` 决定是否处理；处理时根据 `allRowsMatch` 决定 `writer.delete(entry)` 或 `writer.existing(entry)`。
4. 不匹配的 entry 走 `writer.existing(entry)`（保留为既有）。

由于 `liveEntries()` 已经把 DELETED entry 过滤掉，原来"DELETED entry 走 else 分支什么也不做"的路径不再需要——DELETED entry 根本不会进入循环。而原 else 分支对非 DELETED entry 是 `writer.existing(entry)`，这与新代码中"不匹配则 existing"一致，所以语义完全等价。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java`

**修改目的**：用 `liveEntries()` 替代 `entries()`，避免读取 DELETED entry；扁平化循环结构。

**工作逻辑**（`filterManifestWithDeletedFiles` 方法，第 431 行附近）：

修改前：
```java
reader
    .entries()
    .forEach(
        entry -> {
          F file = entry.file();
          boolean markedForDelete =
              deletePaths.contains(file.location())
                  || deleteFiles.contains(file)
                  || dropPartitions.contains(file.specId(), file.partition())
                  || (isDelete
                      && entry.isLive()
                      && entry.dataSequenceNumber() > 0
                      && entry.dataSequenceNumber() < minSequenceNumber);
          if (entry.status() != ManifestEntry.Status.DELETED) {
            if (markedForDelete || evaluator.rowsMightMatch(file)) {
              boolean allRowsMatch = markedForDelete || evaluator.rowsMustMatch(file);
              ValidationException.check(
                  allRowsMatch || isDelete,
                  "Cannot delete file where some, but not all, rows match filter %s: %s",
                  this.deleteExpression,
                  file.location());

              if (allRowsMatch) {
                writer.delete(entry);
                if (deletedFiles.contains(file)) {
                  LOG.warn("Deleting a duplicate path from manifest {}: {}",
                      manifest.path(), file.location());
                  duplicateDeleteCount += 1;
                } else {
                  deletedFiles.add(file.copyWithoutStats());
                }
              } else {
                writer.existing(entry);
              }
            } else {
              writer.existing(entry);
            }
          }
        });
```

修改后：
```java
reader
    .liveEntries()
    .forEach(
        entry -> {
          F file = entry.file();
          boolean markedForDelete =
              deletePaths.contains(file.location())
                  || deleteFiles.contains(file)
                  || dropPartitions.contains(file.specId(), file.partition())
                  || (isDelete
                      && entry.isLive()
                      && entry.dataSequenceNumber() > 0
                      && entry.dataSequenceNumber() < minSequenceNumber);
          if (markedForDelete || evaluator.rowsMightMatch(file)) {
            boolean allRowsMatch = markedForDelete || evaluator.rowsMustMatch(file);
            ValidationException.check(
                allRowsMatch || isDelete,
                "Cannot delete file where some, but not all, rows match filter %s: %s",
                this.deleteExpression,
                file.location());

            if (allRowsMatch) {
              writer.delete(entry);
              if (deletedFiles.contains(file)) {
                LOG.warn("Deleting a duplicate path from manifest {}: {}",
                    manifest.path(), file.location());
                duplicateDeleteCount += 1;
              } else {
                deletedFiles.add(file.copyWithoutStats());
              }
            } else {
              writer.existing(entry);
            }
          } else {
            writer.existing(entry);
          }
        });
```

关键变化：
- `entries()` → `liveEntries()`：读取端只返回 live entry（ADDED/EXISTING），DELETED entry 在读取阶段被过滤。
- 删除外层 `if (entry.status() != ManifestEntry.Status.DELETED) { ... }`，内层逻辑上移一级，缩进减少。
- `markedForDelete` 中的 `entry.isLive()` 条件保留（在 `liveEntries()` 下恒为 true，但不影响正确性，保留作为防御性编码）。
- 整体语义不变：live entry 中匹配删除条件的走 `writer.delete`，不匹配或部分匹配（对 delete 文件）的走 `writer.existing`。

## 小结

- **成效**：`ManifestFilterManager.filterManifestWithDeletedFiles` 改用 `liveEntries()` 读取，避免反序列化与遍历 DELETED entry，提升 manifest 重写性能（尤其对含大量历史删除的大 manifest）；同时简化循环结构，移除冗余的外层状态判断，代码更扁平。
- **影响范围**：1 个文件、26 处新增、28 处删除；纯性能与可读性优化，无语义变化。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个性能优化与代码清理，建议回迁到 1.4.x，特别是当 1.4.x 部署在有大 manifest（频繁删除导致 DELETED entry 累积）的场景时，可减少 manifest 重写的 I/O 与 CPU 开销。
  - 回迁风险低：`liveEntries()` 与 `entries()` 的区别仅在于是否过滤 DELETED entry，而原代码本就跳过 DELETED entry，所以行为完全等价。
  - 需确认 1.4.x 的 `ManifestReader.liveEntries()` 实现与 main 一致（该方法历史悠久，1.4.x 应已提供）。
  - 注意 `markedForDelete` 中保留的 `entry.isLive()` 条件：在 `liveEntries()` 下该条件恒为 true，不影响正确性，但如果 1.4.x 上有人后续优化去掉该条件，需确保不破坏 `isDelete && ... && dataSequenceNumber < minSequenceNumber` 的整体语义。
  - 该改动对 `expire_snapshots`、`rewrite_data_files`、`rewrite_manifests` 等涉及 manifest 重写的操作有正面性能影响，建议优先回迁。
