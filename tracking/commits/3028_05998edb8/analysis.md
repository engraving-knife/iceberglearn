# 提交 3028：Fix: Enable metadata tables support for REST scan planning (#14881)

## 提交信息

- **序号**：3028 / 4088
- **哈希**：05998edb8808ae21739072cca17b621925a2081a
- **短哈希**：05998edb8
- **日期**：2025-12-18
- **作者**：Prashant Singh
- **提交说明**：Fix: Enable metadata tables support for REST scan planning (#14881)
- **PR/Issue**：#14881

## 总体目的

本提交修复了远程扫描计划（remote scan planning）启用后对元数据表（metadata tables）的支持问题。在提交 3026 引入远程扫描计划时，`TestRemoteScanPlanning` 测试套件中 `testMetadataTables` 被标记为 `@Disabled("Metadata tables are currently not supported")`，因为当时的实现会导致元数据表错误地走远程扫描计划路径。

问题的根源在于 `RESTSessionCatalog.loadTable()` 方法中的逻辑顺序。原实现在加载表时，先调用 `restTableForScanPlanning()` 尝试构造用于远程扫描计划的 `RESTTable`，然后判断如果构造成功就直接返回。但元数据表（如 manifests、snapshots、history 等）不应该走远程扫描计划，因为客户端只需访问 catalog 即可获取元数据文件，而远程扫描计划服务端无法正确处理这些元数据表的扫描。原代码注释也说明了这一点："RestTable should be only be returned for non-metadata tables, because client would not have access to metadata files for example manifests, since all it needs is catalog."，但实际逻辑却没有正确地用 `metadataType` 来区分。

本提交通过将 `restTableForScanPlanning()` 的调用包裹在 `if (metadataType == null)` 条件中，确保只有非元数据表（`metadataType == null`）才会尝试构造远程扫描计划的 `RESTTable`，而元数据表（`metadataType != null`）则跳过此路径，走正常的 `BaseTable` 构建流程。同时移除了测试中被禁用的 `testMetadataTables` 用例，使其恢复执行。

## 如何达成设计目的

改动涉及两个文件。在 `RESTSessionCatalog.java` 中，将远程扫描计划的 `RESTTable` 构建逻辑用 `metadataType == null` 条件守卫，使元数据表不受影响。在 `TestRemoteScanPlanning.java` 中，删除被 `@Disabled` 标注的 `testMetadataTables` 方法，使其继承自父类 `TestSelect` 的原始实现正常运行。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+6/-4 lines)

**修改目的**：确保元数据表不走远程扫描计划路径。

**工作逻辑**：
原代码先无条件调用 `RESTTable restTable = restTableForScanPlanning(ops, finalIdentifier, tableClient);`，再判断 `if (restTable != null) return restTable;`。修改后变为先判断 `if (metadataType == null)`，只有当不是元数据表时才调用 `restTableForScanPlanning()` 并在非空时返回。当 `metadataType != null`（即加载的是元数据表）时，直接跳过远程扫描计划，继续构建普通 `BaseTable`。注释也从原来的两段式改为更清晰的一句话描述。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoteScanPlanning.java` (+0/-6 lines)

**修改目的**：恢复元数据表测试用例的执行。

**工作逻辑**：
删除了原先用 `@Disabled("Metadata tables are currently not supported")` 标注的 `testMetadataTables()` 方法及其注解。删除后，该测试将继承父类 `TestSelect` 中的 `testMetadataTables()` 实现，在远程扫描计划启用的 REST Catalog 环境下正常执行，验证元数据表查询的正确性。

## 总结

本提交通过在 `RESTSessionCatalog.loadTable()` 中用 `metadataType == null` 条件守卫远程扫描计划路径，修复了元数据表被错误地路由到远程扫描计划的问题，使元数据表在远程扫描计划启用时仍能正常工作，并恢复了对应的测试覆盖。
