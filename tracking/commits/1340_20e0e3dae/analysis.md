# 提交 1340：Spark 3.5: Fix flaky test due to temp directory not empty during delete (#11470)

## 提交信息

- **序号**：1340 / 4088
- **哈希**：20e0e3dae59826ea02bc2c79f8778cdf7a825316
- **短哈希**：20e0e3dae
- **日期**：2024-11-05（Tue Nov 5 18:04:56 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.5: Fix flaky test due to temp directory not empty during delete (#11470)
- **PR/Issue**：#11470

## 总体目的

`TestDataFrameWrites` 是 Spark 3.5 模块下针对 DataFrame 写入 Iceberg 表的参数化测试。该测试存在一个**间歇性失败（flaky test）**：在测试结束时删除表目录会偶发抛出 `NoSuchFileException`（或目录非空导致删除失败）。原因是该测试在 JUnit 5 的 `@TempDir Path temp` 基础上，自己又用 `Files.createTempDirectory(temp, "parquet")` → `new File(parent, "test")` 额外建了一层目录用于放表，并在测试结束（特别是 `testFaultToleranceOnWrite`）时手动 `FileUtils.deleteDirectory(location)` 清理。当 Spark 异步任务（如 speculation 任务、commit 失败回滚后的清理线程）仍在向该目录写临时文件时，删除会失败。

修复思路是简化目录管理：直接用 `@TempDir File location` 作为表目录，让 JUnit 5 在测试结束后统一清理（JUnit 5 的 `@TempDir` 在 `@AfterAll` 之后才会删除，且对并发写入有更好的容忍），同时**移除手动 `FileUtils.deleteDirectory` 的循环重试逻辑**。这是 1341（统一 tableDir 初始化）思路在单个测试文件上的预演——本质都是"用 `@TempDir` 接管目录生命周期"。

## 如何达成设计目的

1. 把测试类的 `@TempDir` 从 `Path temp`（外部目录）改为直接 `@TempDir File location`（表目录本体），不再单独建 `temp/parquet/test` 子目录。
2. 把原本需要 `location` 参数的方法签名收掉该参数（`createTable(schema)`、`writeAndValidateWithLocations(table, expectedDataDir)`、`readTable()`、`writeData(...)`、`writeDataWithFailOnPartition(...)`）。
3. 删除 `createTableFolder()` 私有方法。
4. 删除 `testFaultToleranceOnWrite` 末尾的 `while (location.exists()) { try { FileUtils.deleteDirectory(location); } catch (NoSuchFileException e) { ... } }` 重试块。
5. 删除两个测试方法中局部变量 `File location = temp.resolve("parquet").resolve("test").toFile();`，改用类字段 `location`。
6. 删除不再使用的 import：`java.nio.file.NoSuchFileException`、`org.apache.commons.io.FileUtils`。

通过把目录生命周期完全交给 JUnit 5 `@TempDir`，避免了测试自身与 Spark 异步任务争抢目录删除权的问题。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWrites.java`

**修改目的**：消除因手动管理表目录导致的 flaky 测试。

**工作逻辑**：

1. 新增字段并替换：

```java
// 旧：无 location 字段，方法内 Files.createTempDirectory(temp, "parquet") + new File(parent, "test")
// 新：
@TempDir private File location;
```

2. `writeAndValidate(Schema)` 简化：

```java
// 旧
File location = createTableFolder();
Table table = createTable(schema, location);
writeAndValidateWithLocations(table, location, new File(location, "data"));
// 新
Table table = createTable(schema);
writeAndValidateWithLocations(table, new File(location, "data"));
```

3. `testWriteWithCustomDataLocation` 简化：删除 `createTableFolder()` 调用，`createTable(...)` 不再传 location；保留自定义 `tablePropertyDataLocation` 的逻辑不变。

4. 删除 `createTableFolder()` 方法。

5. `createTable`、`writeAndValidateWithLocations`、`readTable`、`writeData`、`writeDataWithFailOnPartition` 签名移除 `String location` 参数，内部使用类字段 `location.toString()`：

```java
private Table createTable(Schema schema) {
    HadoopTables tables = new HadoopTables(CONF);
    return tables.create(schema, PartitionSpec.unpartitioned(), location.toString());
}

private void writeData(Iterable<Record> records, Schema schema) throws IOException {
    Dataset<Row> df = createDataset(records, schema);
    DataFrameWriter<?> writer = df.write().format("iceberg").mode("append");
    writer.save(location.toString());
}

private List<Row> readTable() {
    Dataset<Row> result = spark.read().format("iceberg").load(location.toString());
    return result.collectAsList();
}
```

6. 两个涉及 `nullable_poc` 的测试方法（`testWriteWithNullabilityCheckForNullableColumn` 等）删除局部 `File location = temp.resolve("parquet").resolve("test").toFile();`，直接使用类字段 `location`。

7. `testFaultToleranceOnWrite` 删除末尾的删除循环：

```java
// 旧：测试结尾有
while (location.exists()) {
    try {
        FileUtils.deleteDirectory(location);
    } catch (NoSuchFileException e) {
        // ignore NoSuchFileException when a file is already deleted
    }
}
// 新：直接删除该块
```

8. 删除 import：

```java
// 旧
import java.nio.file.NoSuchFileException;
import org.apache.commons.io.FileUtils;
// 新：均移除
```

9. 新增 import：

```java
import org.junit.jupiter.api.io.TempDir;
```

整体改动是"减少代码"——把复杂的目录管理与删除重试交给 JUnit 5 `@TempDir`。共 +23/-44 行（净减少 21 行）。

## 小结

- **成效**：消除 `TestDataFrameWrites` 的 flaky 失败。根因是 Spark 异步任务在测试结束时仍在写表目录，与手动 `FileUtils.deleteDirectory` 冲突。改用 JUnit 5 `@TempDir File location` 让框架统一管理目录生命周期，删除测试内手动清理逻辑，从根本上规避竞态。
- **影响范围**：仅 `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWrites.java` 一个测试文件、+23/-44 行；无产品代码改动。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯测试稳定性修复，**建议回迁**到 1.4.x，前提是 1.4.x 也维护 Spark 3.5 模块且该测试在 1.4.x 上也存在 flaky 问题。
  2. 改动局限于单文件、无依赖、无 API 变更，回迁零风险，可直接 cherry-pick。
  3. 若 1.4.x 上 CI 没有出现该 flaky，也可暂不回迁；但回迁后能预防同样的问题。
  4. 注意 1.4.x 上的 JUnit 5 版本需支持 `@TempDir File`（JUnit 5.0+ 即支持），通常无版本兼容问题。
