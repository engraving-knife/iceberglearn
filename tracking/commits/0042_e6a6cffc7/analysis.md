# 提交 0042：Nessie: Remove deprecated usage of Operation.Put.of() (#8796)

## 提交信息

- **序号**：0042 / 4088
- **哈希**：e6a6cffc7d5e30e02dacb2d70918d054d79a5836
- **短哈希**：e6a6cffc7
- **日期**：2023-10-12
- **作者**：Ajantha Bhat
- **提交说明**：Nessie: Remove deprecated usage of Operation.Put.of() (#8796)
- **PR/Issue**：#8796

## 总体目的

该提交移除 Iceberg Nessie 模块中对 Nessie 客户端已弃用 API `Operation.Put.of(key, newTable, expectedContent)` 的调用，改为使用仅接受两个参数的新签名 `Operation.Put.of(key, newTable)`。

Nessie 的旧版 `Put.of(key, newValue, expectedValue)` 三参数重载接受"期望的旧值"，用于在 Nessie 服务端做乐观并发控制（CAS）——若服务端的当前值与 `expectedValue` 不符则提交失败。新版本 Nessie 移除了这种基于整对象比较的 CAS 语义，改为不再依赖 expectedContent。Iceberg 此前一直把整份 `expectedContent`（一个 `IcebergTable` 对象）传入 Put 操作，依赖 Nessie 进行比较；这次提交将"期望内容"在 Iceberg 侧就降级为只取其 `contentId`，并把这个 id 直接构建进新的 `IcebergTable`，再交给仅二参数的 `Operation.Put.of`。

调整后，Iceberg Nessie 模块与新版 Nessie 客户端 API 兼容，消除了弃用调用导致的编译/运行告警，也为后续升级 Nessie 客户端版本铺路。

## 如何达成设计目的

整体思路是在 [NessieIcebergClient](file:///Users/fengxiaohang/trae/iceberglearn/nessie/src/main/java/org/apache/iceberg/nessie/NessieIcebergClient.java) 内重载 `commitTable` 方法：保留旧的接受 `IcebergTable expectedContent` 的签名为 `@Deprecated`（向后兼容调用方），但内部仅取出 `contentId` 后委托给新增的接受 `String contentId` 的实现。新实现中，把 contentId 直接 `.id(contentId)` 写入新的 `IcebergTable` 构建器，并调用 `Operation.Put.of(key, newTable)`（两参数版）。调用侧 [NessieTableOperations](file:///Users/fengxiaohang/trae/iceberglearn/nessie/src/main/java/org/apache/iceberg/nessie/NessieTableOperations.java) 同步切换到新的 `commitTable` 签名，先从 `table` 抽取 contentId 再传入。这样既消除了对 deprecated API 的依赖，又保留了 Iceberg 侧的接口兼容性（旧签名保留并打 `@Deprecated` 注释，承诺在 1.5.0 后移除）。

## 修改详情

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieIcebergClient.java`

**修改目的**：拆分 `commitTable` 重载，引入基于 `contentId` 的新实现，并切换到非弃用的 `Operation.Put.of(key, newTable)`。

**工作逻辑**：改动分三部分。

第一，给原签名 `commitTable(TableMetadata base, TableMetadata metadata, String newMetadataLocation, IcebergTable expectedContent, ContentKey key)` 加上 `@Deprecated` 注释与 `/** @deprecated will be removed after 1.5.0 */` 的 Javadoc，标记此签名将下线。

第二，新增重载方法 `commitTable(... String newMetadataLocation, String contentId, ContentKey key)`——第四个参数由 `IcebergTable expectedContent` 改为 `String contentId`。原方法体也调整为：从 `expectedContent` 中取出 `contentId` 后调用新方法。

第三，重写实际提交逻辑：原本是根据 `expectedContent != null` 条件性地把 `expectedContent.getId()` 放进 `newTableBuilder`，然后调用 `Operation.Put.of(key, newTable, expectedContent)`。新实现改为：直接 `ImmutableIcebergTable.builder().id(contentId)...` 构建（不再判空——若 `contentId` 为 null 则 builder 接受 null，由 Nessie 处理），调用 `Operation.Put.of(key, newTable)`（去掉 expectedContent 实参）。同时把 `ImmutableIcebergTable.Builder newTableBuilder = ImmutableIcebergTable.builder();` 这行从 if/else 块附近挪到了下面、紧跟 `snapshot`/`snapshotId` 计算之后，使构建顺序更线性。

通过这一改动，Iceberg 不再向 Nessie 提交 expectedContent，而把 contentId 作为新建 IcebergTable 的标识传入。这相当于把"CAS 期望"从"对象级比较"降级为"id 级传递"，与新 Nessie 客户端 API 的语义保持一致。

### `nessie/src/main/java/org/apache/iceberg/nessie/NessieTableOperations.java`

**修改目的**：调整调用方，使用新的基于 contentId 的 `commitTable` 签名。

**工作逻辑**：原本调用 `client.commitTable(base, metadata, newMetadataLocation, table, key)`（`table` 是 `IcebergTable` 类型，可能为 null）。改为先把 `String contentId = table == null ? null : table.getId();` 抽出，再调用 `client.commitTable(base, metadata, newMetadataLocation, contentId, key)`。null-safe 提取逻辑保留了原行为——`table` 为 null 时仍传 null contentId，与旧实现中 `expectedContent == null` 不设置 id 的语义一致。

## 小结

该提交以最小代价让 Iceberg Nessie 模块脱离 Nessie 已弃用的三参数 `Operation.Put.of`，把"期望内容"降级为"contentId"并改由 Iceberg 侧直接构建，从而与新 Nessie 客户端 API 对齐，同时通过保留 `@Deprecated` 旧签名维持了向后兼容。
