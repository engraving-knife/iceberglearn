# 提交 1980：Throw on `{write.folder-storage.path,write.object-storage.path}` properties (#12315)

## 提交信息

- **序号**：1980 / 4088
- **哈希**：b15f4ac1795d25f9db5aa1559b677d57d1bacceb
- **短哈希**：b15f4ac17
- **日期**：2025-04-09 15:27:04 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Throw on `{write.folder-storage.path,write.object-storage.path}` properties (#12315)
- **PR/Issue**：#12315

## 总体目的

本提交将两个已废弃的存储路径属性 `write.folder-storage.path`（`WRITE_FOLDER_STORAGE_LOCATION`）和 `write.object-storage.path`（`OBJECT_STORE_PATH`）的处理方式从"静默接受并使用"改为"抛出异常"，以强制用户迁移到推荐的 `write.data.path`（`WRITE_DATA_LOCATION`）属性。

这两个旧属性早已被标记为 `@Deprecated`，计划在 2.0.0 移除。但在修改前，`LocationProviders` 仍会读取并使用它们作为数据写入位置，用户感知不到弃用压力，尤其非 Java 用户（无法看到编译期弃用警告）。为了让弃用更显式、避免用户在升级到 2.0.0 时才发现属性失效，本提交在解析这些属性时直接抛出 `IllegalArgumentException`，明确提示改用 `write.data.path`。

从提交说明的历史看，作者最初尝试仅打印警告日志，后改为直接抛异常以更明确地阻断误用。文档同步说明这两个属性将在 2.0.0 被移除。

## 如何达成设计目的

设计思路是引入一个统一的属性读取校验方法，在读取到已废弃属性时抛异常：

1. **新增校验方法** `getAndCheckLegacyLocation(Map, String)`：读取指定 key 的值；若值非空且该 key 属于 `DEPRECATED_PROPERTIES`（`OBJECT_STORE_PATH`、`WRITE_FOLDER_STORAGE_LOCATION`），则抛 `IllegalArgumentException`，提示该属性已废弃、将于 2.0.0 移除、改用 `WRITE_DATA_LOCATION`；否则返回值。
2. **替换直接读取**：在 `DefaultLocationProvider` 与 `ObjectStoreLocationProvider` 的 `dataLocation(...)` 中，将原本对 `WRITE_DATA_LOCATION`/`OBJECT_STORE_PATH`/`WRITE_FOLDER_STORAGE_LOCATION` 的 `properties.get(...)` 调用统一改为 `getAndCheckLegacyLocation(...)`。注意 `WRITE_DATA_LOCATION` 本身不在废弃集合中，对其调用是安全的（值非空也不会抛异常），统一走该方法保持代码一致。
3. **属性 Javadoc 更新**：将 `OBJECT_STORE_PATH` 与 `WRITE_FOLDER_STORAGE_LOCATION` 的 `@deprecated` 注释由"Use WRITE_DATA_LOCATION instead"明确为"will be removed in 2.0.0, use WRITE_DATA_LOCATION instead"。
4. **测试改写**：原测试验证旧属性会被用作数据位置；改为验证设置旧属性后调用 `newDataLocation` 会抛出带明确信息的 `IllegalArgumentException`。
5. **文档更新**：`aws.md` 中补充说明这两个属性将在 2.0.0 被移除。

## 修改详情

### `core/src/main/java/org/apache/iceberg/LocationProviders.java` (修改, +20/-2 lines)

**修改目的**：在读取废弃存储路径属性时抛异常。

**工作逻辑**：
- 新增 `DEPRECATED_PROPERTIES` 不可变集合，含 `OBJECT_STORE_PATH` 与 `WRITE_FOLDER_STORAGE_LOCATION`。
- 新增 `getAndCheckLegacyLocation(Map, String)`：取值后若 key 在废弃集合中且值非空，抛 `IllegalArgumentException`（消息格式：`Property '<key>' has been deprecated and will be removed in 2.0, use 'write.data.path' instead.`）。
- `DefaultLocationProvider.dataLocation` 中三处 `properties.get(...)`（WRITE_DATA_LOCATION、WRITE_FOLDER_STORAGE_LOCATION）改为 `getAndCheckLegacyLocation(...)`。
- `ObjectStoreLocationProvider.dataLocation` 中三处（WRITE_DATA_LOCATION、OBJECT_STORE_PATH、WRITE_FOLDER_STORAGE_LOCATION）同样改为 `getAndCheckLegacyLocation(...)`。

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (修改, +2/-2 lines)

**修改目的**：明确废弃属性的移除版本。

**工作逻辑**：`OBJECT_STORE_PATH` 与 `WRITE_FOLDER_STORAGE_LOCATION` 的 `@deprecated` Javadoc 由"Use WRITE_DATA_LOCATION instead"改为"will be removed in 2.0.0, use WRITE_DATA_LOCATION instead"。

### `core/src/test/java/org/apache/iceberg/TestLocationProvider.java` (修改, +12/-51 lines)

**修改目的**：将原"旧属性生效"测试改为"旧属性抛异常"测试。

**工作逻辑**：
- `testObjectStorageLocationProviderPathResolution` 改名为 `testObjectStorageLocationProviderThrowOnDeprecatedProperties`：分别设置 `WRITE_FOLDER_STORAGE_LOCATION` 与 `OBJECT_STORE_PATH`，断言 `newDataLocation("file")` 抛 `IllegalArgumentException` 且消息匹配。
- `testDefaultStorageLocationProviderPathResolution` 改名为 `testDefaultStorageLocationProviderThrowOnDeprecatedProperties`：设置 `WRITE_FOLDER_STORAGE_LOCATION`，断言抛异常。
- 移除原先验证旧属性被用作数据位置、以及 `WRITE_DATA_LOCATION` 优先级的断言。

### `docs/docs/aws.md` (修改, +1/-0 lines)

**修改目的**：文档说明废弃属性将在 2.0.0 移除。

**工作逻辑**：在路径解析历史说明列表中追加一条：`at 2.0.0 write.object-storage.path and write.folder-storage.path will be removed`。

## 总结

本提交将已废弃的 `write.folder-storage.path` 与 `write.object-storage.path` 属性从静默使用改为抛出 `IllegalArgumentException`，通过统一的 `getAndCheckLegacyLocation` 校验方法在 `LocationProviders` 两个 Provider 中拦截，强制用户迁移到 `write.data.path`。同步更新属性 Javadoc 的移除版本说明、改写测试验证抛异常行为、并在 AWS 文档中标注 2.0.0 移除计划。
