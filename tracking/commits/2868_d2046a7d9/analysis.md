# 提交 2868：Core, Spark: Preserve DV-specific fields for deletion vectors (#14351)

## 提交信息

- **序号**：2868 / 4088
- **哈希**：d2046a7d9e7a94745e7cad610817df6b761dd085
- **短哈希**：d2046a7d9
- **日期**：2025-11-12 08:46:26 +0100
- **作者**：adawrapub
- **提交说明**：Core, Spark: Preserve DV-specific fields for deletion vectors (#14351)
- **PR/Issue**：#14351

## 总体目的

这个提交修复了在表路径重写（RewriteTablePath）操作中，删除向量（Deletion Vectors, DV）特定字段丢失的问题。

DV 文件使用 Puffin 格式存储，其中引用的数据文件路径存储在两个位置：
1. **清单元数据（Manifest Metadata）**：`DeleteFile.referencedDataFile()` 字段
2. **Puffin Blob 元数据**：blob 内部的 `referenced-data-file` 属性

原始的 RewriteTablePath 实现只更新了清单元数据中的路径引用，而 Puffin blob 元数据中仍包含旧路径。这导致 DV 读取器在新位置应用删除操作时失败，因为它根据 blob 元数据中的旧路径找不到对应的数据文件。

该提交实现了双重更新策略：
1. 更新清单元数据中的 `referencedDataFile` 字段
2. 重写 Puffin 文件内容，更新 blob 元数据中的 `referenced-data-file` 属性，同时保留位图数据不变

## 如何达成设计目的

修改分为三个层面：

1. **FileMetadata.Builder**：在 `copy()` 方法中补充保留 DV 特定字段（referencedDataFile、contentOffset、contentSizeInBytes、splitOffsets），确保在复制文件元数据时不丢失这些字段。

2. **RewriteTablePathUtil**：
   - 新增 `newPositionDeleteEntry()` 方法：创建位置删除条目时更新 referencedDataFile 路径。
   - 新增 `rewriteReferencedDataFilePathForDV()` 方法：为 DV 文件重写引用数据文件路径。
   - 新增 `rewriteDVFile()` 方法：读取 Puffin 文件，更新 blob 元数据中的路径引用，写入新文件。
   - 修改 `rewriteDeleteFile()` 方法：对 DV 文件调用 `rewriteDVFile()` 而非普通的位置删除文件重写逻辑。

3. **测试**：将测试改为参数化测试，支持 v2 和 v3 格式版本，验证 DV 文件的路径重写正确性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/FileMetadata.java` (+5/-0 lines)

**修改目的**：在 Builder 的 `copy()` 方法中保留 DV 特定字段。

**工作逻辑**：在 `FileMetadata.Builder.copy(FileMetadata toCopy)` 方法中，除了原有的 `keyMetadata` 和 `sortOrderId` 外，新增保留以下字段：
- `splitOffsets`：分片偏移量
- `referencedDataFile`：DV 引用的数据文件路径
- `contentOffset`：内容偏移量
- `contentSizeInBytes`：内容大小

这些字段在 DV 文件（v3+ 格式）中特别重要，之前 copy 操作不保留它们会导致路径重写后信息丢失。

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+100/-16 lines)

**修改目的**：实现 DV 文件的路径重写逻辑，包括清单元数据和 Puffin 内容的双重更新。

**工作逻辑**：

1. **`newPositionDeleteEntry()` 方法**：替代原来内联的位置删除文件处理逻辑。创建新的 DeleteFile 元数据时，使用 `FileMetadata.deleteFileBuilder(spec).copy(file)` 复制文件元数据（现在会保留 DV 字段），更新路径和指标。对于 DV 文件，额外调用 `rewriteReferencedDataFilePathForDV()` 更新 referencedDataFile 字段。

2. **`rewriteReferencedDataFilePathForDV()` 方法**：检查文件是否为 DV 文件（通过 `ContentFileUtil.isDV()`）且有 referencedDataFile。如果是，将引用路径中的 sourcePrefix 替换为 targetPrefix。非 DV 文件或无引用路径时返回 null。

3. **`rewriteDVFile()` 方法**：这是 DV 文件路径重写的核心方法。
   - 使用 `PuffinReader` 读取 Puffin 文件中的所有 blob。
   - 对每个 blob，检查其元数据属性中的 `referenced-data-file` 属性。
   - 如果该路径以 sourcePrefix 开头，替换为 targetPrefix。
   - 创建新的 `Blob` 对象，保留原始位图数据但更新属性。
   - 使用 `PuffinWriter` 将更新后的 blob 写入新文件。

4. **`rewriteDeleteFile()` 方法修改**：在方法开头添加 DV 文件检查。如果文件是 DV 格式，调用 `rewriteDVFile()` 处理并返回，不再走普通的位置删除文件重写逻辑。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+120/-63 lines)

**修改目的**：将测试改为参数化测试，支持 v2 和 v3 格式版本，验证 DV 文件路径重写。

**工作逻辑**：

1. **参数化测试**：使用 `@ExtendWith(ParameterizedTestExtension.class)` 和 `@Parameters` 注解，将测试参数化为 v2 和 v3 格式版本。所有 `@Test` 注解改为 `@TestTemplate`。

2. **格式版本感知**：在 `createTableWithSnapshots()` 方法中注入 `formatVersion` 参数。在创建删除文件时传入 `formatVersion`（通过 `FileHelpers.writeDeleteFile()` 和 `FileHelpers.writePosDeleteFile()` 的新参数）。

3. **v3 特定处理**：
   - `testPositionDeleteWithRow`：v3 使用 DV 只存储位置信息，不存储行数据，因此只对 v2 验证删除行内容。
   - `testPositionDeletesAcrossFiles` 和 `testNestedDirectoryStructurePreservation`：使用 `assumeThat(formatVersion).isEqualTo(2)` 跳过 v3，因为 v3 不能在单个删除文件中写入多个 DV。

4. **表名参数化**：将硬编码的表名（如 `v2tbl`）改为根据 formatVersion 动态生成（如 `v%stbl`）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+175/-0 lines)

**修改目的**：将 v3.5 的测试同步更新为参数化测试（与 v3.4 相同的改动）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+175/-0 lines)

**修改目的**：将 v4.0 的测试同步更新为参数化测试（与 v3.4 相同的改动）。

## 总结

这个提交修复了 RewriteTablePath 操作中 DV（删除向量）特定字段丢失的问题。DV 文件的引用数据文件路径存储在两个位置（清单元数据和 Puffin blob 元数据），原始实现只更新了前者。该提交通过双重更新策略——更新清单元数据中的 `referencedDataFile` 字段和重写 Puffin 文件内容中的 blob 元数据属性——确保 DV 文件在路径重写后能正确工作。修改涉及核心逻辑（FileMetadata、RewriteTablePathUtil）和三个 Spark 版本的测试文件，测试改为参数化以覆盖 v2 和 v3 格式版本。这是 Iceberg v3 删除向量支持的重要修复。
