# 提交 1245：Spark 3.5: Spark Scan should ignore statistics not of type Apache DataSketches (#11035)

## 提交信息

- **序号**：1245 / 4088
- **哈希**：17f1c4d2205b59c2bd877d4d31bbbef9e90979c5
- **短哈希**：17f1c4d22
- **日期**：2024-10-17（Thu Oct 17 03:48:26 2024 +0530）
- **作者**：Soumya Banerjee <48854046+jeesou@users.noreply.github.com>
- **提交说明**：Spark 3.5: Spark Scan should ignore statistics not of type Apache DataSketches (#11035)
- **PR/Issue**：#11035

## 总体目的

修复 Spark 3.5 中 `SparkScan` 在向 Spark 报告列统计（column statistics）时的一个缺陷：当 Iceberg 表的 statistics 文件中包含**非** `APACHE_DATASKETCHES_THETA_V1` 类型的 blob（例如未来其他统计类型，或第三方写入的 blob），原实现会直接跳过整个字段、不再处理后续 blob，导致该列的 NDV（distinct count）即使有可用的 DataSketches blob 也不会被报告。

原逻辑的问题在于：`for (BlobMetadata blobMetadata : metadataList)` 按列表顺序遍历，一旦遇到一个非 DataSketches 类型的 blob 就走到 else 分支记录日志并跳出该列的处理（实际上没有 break，但每个 blob 都覆盖同一个 `ndv` 局部变量，导致非 DataSketches blob 的处理会"挤掉"前面的有效值或被后面无效值覆盖）。重新设计为按字段分组后逐字段处理，对每个字段的多个 blob 逐一筛选出 DataSketches 类型的来计算 NDV，其他类型只记日志忽略。

## 如何达成设计目的

1. 把 `metadataList` 按 `fields().get(0)`（即字段 id）分组为 `Map<Integer, List<BlobMetadata>>`，外层循环按字段遍历，内层循环按 blob 遍历。
2. 内层循环中：若 blob 类型为 `APACHE_DATASKETCHES_THETA_V1`，读取 `NDV_KEY` 属性并解析为 `Long`；若 `ndvStr` 为空则记 debug 日志；若 blob 类型不是 DataSketches，记 debug 日志 `"Blob type {} is not supported yet"`。
3. 一个字段下多个 blob 中只要有一个 DataSketches blob 命中，`ndv` 即被赋值；最终构造 `SparkColumnStatistics(ndv, null, ...)` 上报给 Spark。
4. 新增两个测试用例覆盖：纯非 DataSketches blob、DataSketches 与非 DataSketches blob 共存；并修正断言助手以处理"列存在但 NDV 为空"的情况。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java`

**修改目的**：按字段分组处理 statistics blob，正确忽略非 DataSketches 类型而不影响同字段其他 blob。

**工作逻辑**：

原代码：

```java
for (BlobMetadata blobMetadata : metadataList) {
  int id = blobMetadata.fields().get(0);
  String colName = table.schema().findColumnName(id);
  NamedReference ref = FieldReference.column(colName);
  Long ndv = null;
  if (blobMetadata.type().equals(...APACHE_DATASKETCHES_THETA_V1)) {
    String ndvStr = blobMetadata.properties().get(NDV_KEY);
    if (!Strings.isNullOrEmpty(ndvStr)) {
      ndv = Long.parseLong(ndvStr);
    } else {
      LOG.debug("ndv is not set in BlobMetadata for column {}", colName);
    }
  } else {
    LOG.debug("DataSketch blob is not available for column {}", colName);
  }
  ColumnStatistics colStats = new SparkColumnStatistics(ndv, null, ...);
  ...
}
```

新代码：

```java
Map<Integer, List<BlobMetadata>> groupedByField =
    metadataList.stream()
        .collect(Collectors.groupingBy(metadata -> metadata.fields().get(0), Collectors.toList()));

for (Map.Entry<Integer, List<BlobMetadata>> entry : groupedByField.entrySet()) {
  String colName = table.schema().findColumnName(entry.getKey());
  NamedReference ref = FieldReference.column(colName);
  Long ndv = null;

  for (BlobMetadata blobMetadata : entry.getValue()) {
    if (blobMetadata.type().equals(...APACHE_DATASKETCHES_THETA_V1)) {
      String ndvStr = blobMetadata.properties().get(NDV_KEY);
      if (!Strings.isNullOrEmpty(ndvStr)) {
        ndv = Long.parseLong(ndvStr);
      } else {
        LOG.debug("{} is not set in BlobMetadata for column {}", NDV_KEY, colName);
      }
    } else {
      LOG.debug("Blob type {} is not supported yet", blobMetadata.type());
    }
  }

  ColumnStatistics colStats = new SparkColumnStatistics(ndv, null, null, null, null, null, null);
  ...
}
```

关键差异：
- 外层按字段 id 分组，每个字段只构造一次 `ColumnStatistics`，避免被同字段的多个 blob 重复创建覆盖。
- 内层遍历同字段的所有 blob，命中 DataSketches 才赋值 `ndv`，非 DataSketches 只记日志，不干扰 `ndv`。
- 日志信息更精确：`NDV_KEY` 未设置时打印具体 key 名；非 DataSketches blob 打印实际 blob 类型，便于排查。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java`

**修改目的**：覆盖非 DataSketches blob 与混合 blob 场景。

**工作逻辑**：
- 新增常量 `DUMMY_BLOB_TYPE = "sum-data-size-bytes-v1"` 模拟非 DataSketches 类型。
- 新增 `testTableWithoutApacheDatasketchColStat`：表上只有一个 dummy 类型的 blob，期望列统计不被报告（或 NDV 全为 null）。
- 新增 `testTableWithOneApacheDatasketchColStatAndOneDifferentColStat`：同一字段同时有 DataSketches blob（`ndv=4`）和 dummy blob，期望仍然能报告 NDV=4，证明非 DataSketches blob 不会"挤掉"有效值。
- 修改 `checkColStatisticsReported` 助手：当 `expectedNDVs` 为空时，校验所有列的 `distinctCount().isEmpty()` 都为 true（即 NDV 为 null 而非 0），避免误判。

## 小结

- **成效**：Spark 3.5 的 `SparkScan` 现在能正确处理 statistics 文件中混合类型的 blob——只识别 `APACHE_DATASKETCHES_THETA_V1`，其他类型静默忽略，不影响同字段有效 NDV 的上报。这提升了 Spark CBO 在含有附加统计 blob 的 Iceberg 表上的准确性。
- **影响范围**：仅 Spark 3.5 模块的 `SparkScan` 主代码与对应测试，属于读路径上列统计报告的鲁棒性改进。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个 bug fix，1.4.x 若存在同样问题（即 1.4.x 的 `SparkScan` 仍是旧版平铺遍历逻辑），**建议回迁**。
  - 回迁时需确认 1.4.x 的 Spark 3.5 模块路径与类签名一致；`SparkColumnStatistics` 构造参数个数需与 1.4.x 一致（本提交中是 7 个 null 参数）。
  - 测试用例依赖 `GenericStatisticsFile`/`GenericBlobMetadata` 等 Immutable 对象，1.4.x 上若 API 有差异需要相应调整。
  - 该改动只影响 `reportColumnStats` 启用时的行为，关闭时无影响，回迁风险低。
