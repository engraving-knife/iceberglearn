# 提交 3236：Core: Fix relativize() to handle path equal to prefix (#15173)

## 提交信息

- **序号**：3236 / 4088
- **哈希**：f49b2fd97b48682d4e4ca6f1a552cb48f53c4ea5
- **短哈希**：f49b2fd97
- **日期**：2026-02-10
- **作者**：Sebastian Baunsgaard
- **提交说明**：Core: Fix relativize() to handle path equal to prefix (#15173)
- **PR/Issue**：#15173

## 总体目的

本提交修复了 `RewriteTablePathUtil` 中路径重写工具方法在"路径等于前缀"这一边界场景下的缺陷。在表路径重写（table path rewrite）场景中，例如将一个表从 `s3://bucket/warehouse` 迁移到 `s3://bucket-dr/warehouse` 时，需要把元数据与数据文件路径中的源前缀替换为目标前缀。当某个配置路径（如 `write.data.path`）恰好等于表的位置（即等于源前缀本身）时，原有的 `relativize()` 方法会出错。

原 `relativize()` 的实现是：先对 `prefix` 调用 `maybeAppendFileSeparator` 得到 `toRemove`（如 `/a` 变为 `/a/`），然后检查 `path.startsWith(toRemove)`。当 `path` 恰好等于 `/a`（不带尾部分隔符）时，`"/a".startsWith("/a/")` 为 `false`，于是抛出 `IllegalArgumentException`，导致路径重写失败。这意味着在实际使用中，凡是表的 `location` 与 `write.data.path`、`write.metadata.path` 等配置相同时，整个重写流程会崩溃。

此外，即使 `relativize` 返回空字符串，旧的 `combinePaths()` 也会无条件地在目标前缀后追加分隔符（`maybeAppendFileSeparator(absolutePath) + relativePath`），导致结果如 `/tgt/` 而非 `/tgt`，引入了多余的分隔符。本提交同时修复了这两个相互关联的问题。

## 如何达成设计目的

整体思路是让 `relativize()` 对"路径等于前缀"和"路径在前缀之下"两种情况都做正确处理，并让 `combinePaths()` 在相对路径为空时原样返回目标前缀。具体做法是：在 `relativize()` 中先对 `path` 也做尾部分隔符归一化（`maybeAppendFileSeparator(path)`），用以做 `startsWith` 判断；当归一化后的 path 与归一化后的 prefix 完全相等时返回空字符串，表示这是"根目录本身"。`combinePaths()` 则在 `relativePath.isEmpty()` 时直接返回 `absolutePath`，不再追加分隔符。同时补充了详尽的单元测试覆盖正常、边界与非法场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+33/-9 lines)

**修改目的**：修复 `relativize()` 与 `combinePaths()` 在路径等于前缀时的错误行为，并完善文档。

**工作逻辑**：

- `combinePaths(String absolutePath, String relativePath)`：新增了对空相对路径的判断，`return relativePath.isEmpty() ? absolutePath : maybeAppendFileSeparator(absolutePath) + relativePath;`。当 `relativePath` 为空字符串时，直接返回 `absolutePath` 不做任何修改，避免在目标前缀后无端追加分隔符。这保证了当源路径等于前缀、relativize 返回空字符串后，最终结果就是目标前缀本身。

- `relativize(String path, String prefix)`：核心修复。原实现只对 `prefix` 加尾部分隔符得到 `toRemove`，然后 `path.startsWith(toRemove)` 判断。新实现先对 `path` 也调用 `maybeAppendFileSeparator` 得到 `normalizedPath`，再用 `normalizedPath.startsWith(toRemove)` 做判断。这样 `/a` 会被归一化为 `/a/`，与 `toRemove`（`/a/`）的 `startsWith` 判断自然为真。当 `normalizedPath.equals(toRemove)` 时返回空字符串 `""`，表示路径就是前缀本身（根目录）；否则按原逻辑 `path.substring(toRemove.length())` 返回相对部分。错误信息也改用 `normalizedPath` 以保持一致。

- 文档（Javadoc）：对 `newPath`、`combinePaths`、`relativize` 三个方法都补充了详细的 Javadoc，明确说明了尾部分隔符归一化行为（`"/a"` 与 `"/a/"` 等价）、路径等于前缀时返回空字符串的语义，以及参数约束和抛出异常的条件。

### `core/src/test/java/org/apache/iceberg/TestRewriteTablePathUtil.java` (+165/-0 lines)

**修改目的**：为新修复的逻辑补充全面的单元测试。

**工作逻辑**：新增了大量测试用例，覆盖以下场景：

- `testRelativize()`：验证正常路径（`/a/b/c` 对 `/a` 得 `b/c`）、路径等于前缀（`/a` 对 `/a` 得 `""`，以及 S3 路径场景）、各种尾部分隔符组合。
- `testRelativizeInvalid()`：验证路径不以前缀开头时抛出 `IllegalArgumentException`，以及重叠名称场景（`/table-old` 不应匹配前缀 `/table`），确保不会误匹配。
- `testNewPath()` / `testNewPathEqualsPrefix()` / `testNewPathTrailingSeparatorCombinations()`：验证 `newPath` 在正常、路径等于前缀（对应 issue #15172 的 `write.data.path = table location` 场景）、各种尾部分隔符组合下的正确行为，包含 S3 存储迁移场景。
- `testNewPathBackupRestore()`：模拟备份（重写到原位置的子目录）与恢复（从子目录重写到父目录）场景。
- `testNewPathTableRename()`：模拟表重命名（如 `/tableX` 到 `/table`，源名是目标名子串；以及反向），确保不产生误替换。
- `testCombinePaths()`：验证正常拼接、已有分隔符不重复、空相对路径原样返回。
- `testFileName()`：验证从各种路径（含 S3、根目录文件、纯文件名）提取文件名。

## 总结

本提交修复了表路径重写工具在"路径恰好等于前缀"时的崩溃问题（issue #15172），该问题在实际存储迁移、备份恢复等场景中会频繁触发。通过在 `relativize()` 中对 path 做尾部分隔符归一化并处理"等于前缀"的特殊返回值，以及在 `combinePaths()` 中跳过空相对路径的拼接，使得路径重写逻辑对所有路径与前缀的组合都能正确工作。配套的全面测试不仅覆盖了修复点，还系统性地覆盖了备份/恢复、表重命名等真实使用场景，显著提升了该工具方法的健壮性。
