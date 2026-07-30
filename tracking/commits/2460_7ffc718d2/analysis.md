# 提交 2460：Core, Spark: Preserve the relative path in RewriteTablePathUtil on staging

## 提交信息

- **序号**：2460 / 4088
- **哈希**：7ffc718d2857c1f4e4e7e1d70eebc8662020d6bd
- **短哈希**：7ffc718d2
- **日期**：2025-08-05 11:55:32 -0700
- **作者**：Mustafa Elbehery
- **提交说明**：Core, Spark: Preserve the relative path in RewriteTablePathUtil on staging
- **PR/Issue**：无明确 PR 编号

## 总体目的

该提交修复了 `RewriteTablePathUtil` 在表路径重写（table path rewrite）过程中，将文件复制到 staging 目录时可能产生的文件名冲突问题。

原有的 `stagingPath(originalPath, stagingDir)` 方法仅使用原始路径的文件名（`fileName(originalPath)`）来构建 staging 路径。这意味着如果两个不同目录下的文件具有相同的文件名（例如 `/source/table/hash1/delete_0_0_0.parquet` 和 `/source/table/hash2/delete_0_0_0.parquet`），它们在 staging 目录中会被映射到同一个路径，导致文件冲突和数据损坏。

该提交引入了新的 `stagingPath(originalPath, sourcePrefix, stagingDir)` 方法，通过保留原始路径相对于 sourcePrefix 的相对目录结构来避免冲突。这样，不同目录下同名的文件在 staging 目录中会保持各自的目录层级，不会发生冲突。

## 如何达成设计目的

设计思路如下：

1. **新增带 sourcePrefix 的 stagingPath 方法**：新方法首先使用 `relativize(originalPath, sourcePrefix)` 计算原始路径相对于源前缀的相对路径，然后将该相对路径与 stagingDir 组合，生成保留目录结构的 staging 路径。

2. **标记旧方法为弃用**：原有的两参数 `stagingPath` 方法被标记为 `@Deprecated`（since 1.10.0, will be removed in 1.11.0），引导用户使用新方法。

3. **更新所有调用点**：在 Core 模块和 Spark 模块的所有 `stagingPath` 调用处，都改为传入 `sourcePrefix` 参数，使用新的三参数方法。

4. **新增测试**：创建 `TestRewriteTablePathUtil` 测试类，验证新方法正确保留目录结构、向后兼容性以及复杂路径场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+20/-3 lines)

**修改目的**：新增保留相对路径的 stagingPath 方法，并更新内部调用。

**工作逻辑**：

1. 新增三参数方法：
```java
public static String stagingPath(String originalPath, String sourcePrefix, String stagingDir) {
    String relativePath = relativize(originalPath, sourcePrefix);
    return combinePaths(stagingDir, relativePath);
}
```

2. 旧方法标记为 `@Deprecated`

3. 两处内部调用（manifest copy plan 和 metadata file copy plan）改为使用新方法

### `core/src/test/java/org/apache/iceberg/TestRewriteTablePathUtil.java` (+90 lines, 新文件)

**修改目的**：为新方法添加单元测试。

**工作逻辑**：包含四个测试用例：
- `testStagingPathPreservesDirectoryStructure`：验证同名不同路径的文件生成不同的 staging 路径
- `testStagingPathBackwardCompatibility`：验证弃用方法的向后兼容性
- `testStagingPathWithComplexPaths`：验证复杂分区路径的正确处理
- `testStagingPathWithNoMiddlePart`：验证文件直接在 sourcePrefix 下时的行为

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+7/-4 lines)

**修改目的**：更新 Spark 3.4 中所有 stagingPath 调用为三参数版本。

**工作逻辑**：在 `rewriteVersionFile`、`rewriteStatisticsFile`、`rewriteManifestList`、`rewriteManifests` 等方法中，将 `stagingPath(path, stagingDir)` 改为 `stagingPath(path, sourcePrefix, stagingDir)`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+7/-4 lines)

**修改目的**：对 Spark 3.5 应用相同修改。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+7/-4 lines)

**修改目的**：对 Spark 4.0 应用相同修改。

## 总结

该提交修复了表路径重写过程中 staging 目录的文件名冲突问题。通过引入保留相对路径结构的新 `stagingPath` 方法，确保不同目录下同名的文件在 staging 目录中不会冲突。旧方法被标记为弃用，所有调用点已更新为使用新方法，并添加了完整的单元测试。该修改涉及 Core 和 Spark 两个模块（3.4/3.5/4.0 三个版本），是一个重要的 bug 修复。
