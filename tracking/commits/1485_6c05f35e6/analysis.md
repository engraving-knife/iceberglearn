# 提交 1485：Core: Log where the missing metadata file is located for Hadoop (#11643)

## 提交信息

- **序号**：1485 / 4088
- **哈希**：6c05f35e67093491aa054d0b41b1ada367df1072
- **短哈希**：6c05f35e6
- **日期**：2024-12-12（Thu Dec 12 18:58:56 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Core: Log where the missing metadata file is located for Hadoop (#11643)
- **PR/Issue**：#11643

## 总体目的

Iceberg 的 `HadoopTableOperations.refresh()` 在加载表元数据时，会按版本号依次查找对应的 metadata 文件（如 `v3.metadata.json`）。当 `version-hint.text` 写明期望版本为 N、但 `getMetadataFile(N)` 返回 null（即该版本的 metadata 文件在 `<tableLocation>/metadata/` 目录下不存在）时，原实现抛出的异常是：

```
ValidationException: Metadata file for version %d is missing
```

该错误只告知"版本 N 缺失"，但不告知 Iceberg 实际去哪个目录找过。在排查问题时用户无法判断：
- 表 location 是否被错误配置（指向了非表根目录）；
- metadata 目录是否被误删或权限不足；
- 不同 FileIO / FileSystem 实现下解析路径是否正确。

本提交在异常消息中追加 `metadataRoot()`（即 `<tableLocation>/metadata` 路径），让用户一眼看到 Iceberg 实际查找的目录，大幅降低排查成本。

## 如何达成设计目的

在 `HadoopTableOperations.refresh()` 中抛出 `ValidationException` 处，把消息模板从 `"Metadata file for version %d is missing"` 改为 `"Metadata file for version %d is missing under %s"`，并传入 `metadataRoot()` 作为第二个参数。`metadataRoot()` 是已有私有方法，返回 `new Path(location, "metadata")`，即表的 metadata 子目录路径，无需新增逻辑。

同时在 `TestHadoopCatalog` 中新增 `testMetadataFileMissing` 测试用例，模拟 metadata 文件被删除的场景，验证异常类型与异常消息（含 `metadata` 目录路径）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopTableOperations.java`

**修改目的**：在 metadata 文件缺失异常中暴露实际查找目录。

**工作逻辑**：`refresh()` 方法在 `metadataFile == null` 分支抛异常。修改后：

```java
} else if (metadataFile == null) {
  throw new ValidationException(
      "Metadata file for version %d is missing under %s", ver, metadataRoot());
}
```

`metadataRoot()` 返回 `new Path(location, "metadata")`，`location` 是 `HadoopTableOperations` 构造时传入的表 location（如 `hdfs:///warehouse/db/tbl`），故异常消息会形如：`Metadata file for version 3 is missing under hdfs:///warehouse/db/tbl/metadata`。

该改动只影响异常消息文本，不改任何控制流或重试逻辑。

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopCatalog.java`

**修改目的**：补充针对 metadata 文件缺失场景的回归测试。

**工作逻辑**：新增 `testMetadataFileMissing` 测试方法，主要步骤：

1. `addVersionsToTable(table)`：为测试表添加多个版本元数据。
2. 拿到该表的 `HadoopTableOperations` 与 `FileIO`。
3. 重写 `version-hint.text` 为 `"3"`：先删除原 hint 文件，再写一个内容为 `3` 的新 hint 文件。这强制让 `findVersion()` 返回 3。
4. 断言 `tableOperations.findVersion()` 等于 3，并能正常 load 表且 `currentSnapshot().snapshotId()` 与原表一致——验证 hint 指向的版本存在时一切正常。
5. 接着用 `io.deleteFile(tableOperations.getMetadataFile(3).toString())` 把 v3 的 metadata 文件物理删掉。
6. 再次 `TABLES.load(tableLocation)`，断言抛出 `ValidationException`，且消息**精确等于** `"Metadata file for version 3 is missing under " + new Path(tableLocation, "metadata")`。

测试同时验证了：异常类型正确（`ValidationException`）、消息文本含版本号与 metadata 目录路径。

新增 import：`org.apache.iceberg.exceptions.ValidationException`。

## 小结

- **成效**：当 Hadoop 表的 metadata 文件缺失时，异常消息现在会明确告诉用户 Iceberg 在哪个目录下找不到该版本文件（如 `hdfs:///warehouse/db/tbl/metadata`），显著降低排查路径配置、文件误删、权限等问题时的诊断成本。
- **影响范围**：1 个核心类的小幅改动（1 行变 3 行）+ 1 个测试方法（约 26 行）。仅影响 `HadoopTableOperations` 的异常消息，不影响任何正常路径或其它 catalog 实现。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个改善错误诊断体验的小修复，**建议回迁**，对 1.4.x 用户调试 Hadoop 表问题很有帮助。
  - cherry-pick 风险极低：仅修改异常消息文本与新增测试，无 API 变更、无行为变更（除了异常消息内容）。
  - 注意 1.4.x 的 `HadoopTableOperations` 可能与 main 有少量差异，需确认 `metadataRoot()` 方法在 1.4.x 中已存在（该方法历史较久，1.4.x 应当具备）。若 1.4.x 中该方法签名或行为不同，可改用直接拼接 `new Path(location, "metadata")`。
  - 测试中用到的 `addVersionsToTable`、`versionHintFile`、`TABLES` 等 helper 在 1.4.x 的 `HadoopTableTestBase` 中应已存在，cherry-pick 应顺畅。
