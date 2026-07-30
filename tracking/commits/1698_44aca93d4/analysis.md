# 提交 1698 44aca93d4 分析

## 提交信息
- 哈希：44aca93d42726cb528fbd14cf5af9c0724577549
- 日期：2025-02-06 21:46:15 -0800
- 作者：Manu Zhang
- 消息：Spark 3.4: Remove use of File.Separator in RewriteTablePath (#12173)

## 总体目的

本提交移除 Spark 3.4 模块中 `RewriteTablePathSparkAction` 对 `java.io.File.separator` 的使用，统一改用 `RewriteTablePathUtil.FILE_SEPARATOR`（即固定字符串 "/"）。

背景：`java.io.File.separator` 是平台相关的（在 Linux/macOS 是 "/"，在 Windows 是 "\"）。Iceberg 表的元数据路径、manifest 中的 file location 都是 URI 风格的路径，统一使用 "/" 作为分隔符。如果 `RewriteTablePathSparkAction` 在 Windows 上运行，使用 `File.separator` 会产生带 "\" 的路径，导致：
- stagingDir 拼接出 `...copy-table-staging-<uuid>\` 这种非法 URI 路径；
- `getMetadataLocation` 用 `lastIndexOf(File.separator)` 在含 "/" 的 URI 路径上能凑巧工作，但语义不正确。

同时，本提交把 `RewriteTablePathSparkAction` 中重复实现的 `newPath` 私有方法删除，改用 `RewriteTablePathUtil.newPath` 公共静态方法，消除代码重复。

## 如何达成设计目的

把所有对 `File.separator` 的引用替换为 `RewriteTablePathUtil.FILE_SEPARATOR`（"/"），把路径拼接的尾部斜杠处理交给 `RewriteTablePathUtil.maybeAppendFileSeparator`，把 `newPath` 的实现委托给 `RewriteTablePathUtil.newPath`。这些都是 `RewriteTablePathUtil` 中已经存在的公共静态方法，本提交只是让 Spark 3.4 的 action 调用它们而非自己重新实现。

### 修改详情

#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java
共 25 行变更（13 增 12 删）。

1. 移除 `import java.io.File;`（不再使用）。

2. `stagingDir` 初始化逻辑（约 182 行，`validateAndSetStartVersion` 或类似方法）：
   - 原代码：
     ```java
     if (stagingDir == null) {
       stagingDir = getMetadataLocation(table) + "copy-table-staging-" + UUID.randomUUID() + "/";
     } else if (!stagingDir.endsWith("/")) {
       stagingDir = stagingDir + "/";
     }
     ```
   - 新代码：
     ```java
     if (stagingDir == null) {
       stagingDir = getMetadataLocation(table) + "copy-table-staging-" + UUID.randomUUID() + RewriteTablePathUtil.FILE_SEPARATOR;
     } else {
       stagingDir = RewriteTablePathUtil.maybeAppendFileSeparator(stagingDir);
     }
     ```
   - 改动点：硬编码 "/" 改为 `FILE_SEPARATOR` 常量；`else if (!stagingDir.endsWith("/"))` 分支合并为 `else { maybeAppendFileSeparator(stagingDir); }`，因为 `maybeAppendFileSeparator` 内部已经做了 `endsWith` 判断，逻辑等价但更简洁。

3. `writeMetadataFile`（约 368 行，方法名待确认）：
   - 原代码：`return Pair.of(stagingPath, newPath(versionFilePath, sourcePrefix, targetPrefix));`
   - 新代码：`return Pair.of(stagingPath, RewriteTablePathUtil.newPath(versionFilePath, sourcePrefix, targetPrefix));`
   - 改动点：调用从本地 `newPath` 改为 `RewriteTablePathUtil.newPath`。

4. manifest list copy plan 构造（约 400 行）：
   - 原代码：`result.copyPlan().add(Pair.of(outputPath, newPath(path, sourcePrefix, targetPrefix)));`
   - 新代码：`result.copyPlan().add(Pair.of(outputPath, RewriteTablePathUtil.newPath(path, sourcePrefix, targetPrefix)));`

5. 删除本地私有静态方法 `newPath`（约 684 行）：
   ```java
   private static String newPath(String path, String sourcePrefix, String targetPrefix) {
     return RewriteTablePathUtil.combinePaths(
         targetPrefix, RewriteTablePathUtil.relativize(path, sourcePrefix));
   }
   ```
   该方法只是对 `RewriteTablePathUtil.combinePaths` 与 `relativize` 的简单组合，与 `RewriteTablePathUtil.newPath` 实现等价，删除后改用公共方法。

6. `getMetadataLocation`（约 690 行）：
   - 原代码：`int lastIndex = currentMetadataPath.lastIndexOf(File.separator);`
   - 新代码：`int lastIndex = currentMetadataPath.lastIndexOf(RewriteTablePathUtil.FILE_SEPARATOR);`

注：`RewriteTablePathUtil` 中相关定义（已存在，本提交未修改）：
- `public static final String FILE_SEPARATOR = "/";`
- `public static String newPath(String path, String sourcePrefix, String targetPrefix)`：返回 `maybeAppendFileSeparator(absolutePath) + relativePath`。
- `public static String maybeAppendFileSeparator(String path)`：若 path 不以 FILE_SEPARATOR 结尾则补一个。

## 小结

本次修改消除了 `RewriteTablePathSparkAction` 对平台相关 `File.separator` 的依赖，统一使用 `RewriteTablePathUtil.FILE_SEPARATOR`，使路径处理在 Windows 上也正确。同时删除了与 `RewriteTablePathUtil.newPath` 重复的本地实现，减少维护成本。

回迁到 1.4.x 的注意事项：
1. 依赖 `RewriteTablePathUtil.FILE_SEPARATOR`、`maybeAppendFileSeparator`、`newPath` 三个公共静态方法，回迁前需确认 1.4.x 的 `RewriteTablePathUtil` 已有这些方法。若 1.4.x 较旧可能需要先回迁引入这些方法的提交。
2. 该修改只针对 Spark 3.4 模块。Spark 3.3/3.5 的 `RewriteTablePathSparkAction` 可能已经做过类似清理（或后续提交会做），回迁时注意各 Spark 版本的一致性。
3. 若 1.4.x 不计划支持 Windows 运行，此修复优先级较低；但仍建议回迁以保持代码与 main 一致。
4. 该提交与 1697、1700、1701 同属 RewriteTablePath 系列修复，回迁时注意合并冲突，建议按序号顺序回迁。
