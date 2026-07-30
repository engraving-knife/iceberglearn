# 提交 1422：Spark 3.3: IcebergSource extends SessionConfigSupport (#11625)

## 提交信息

- **序号**：1422 / 4088
- **哈希**：eddf9a161c47def40bd1104cda41a38c45dada44
- **短哈希**：eddf9a161
- **日期**：2024-11-23（Sat Nov 23 14:59:46 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Spark 3.3: IcebergSource extends SessionConfigSupport (#11625)
- **PR/Issue**：#11625

## 总体目的

Spark 3.3 中 Iceberg 的 `IcebergSource` 实现了 `DataSourceRegister` 和 `SupportsCatalogOptions`，但用户在使用 DataFrame API（如 `df.write.format("iceberg").option(...)`）时，必须把每个读写选项显式地拼接在 `.option(...)` 上，无法借助 Spark Session 级别的全局配置统一注入 Iceberg 数据源选项。

Spark 的 `SessionConfigSupport` 接口允许数据源声明一个 `keyPrefix()`，Spark 会自动把 `spark.datasource.<prefix>.<key>` 形式的 Session 配置项合并到该数据源的 options 中。这样用户就可以通过 `spark.sql.session-conf` 或 `SET` 语句在会话级统一控制 Iceberg 的读写参数（如 `snapshot-property.*`、`snapshot-id`、`as-of-timestamp` 等），无需在每次 `read`/`write` 时重复书写 `.option(...)`。

本提交让 `IcebergSource` 实现 `SessionConfigSupport` 接口，并把 `keyPrefix()` 设为 `shortName()`（即 `"iceberg"`），从而让所有形如 `spark.datasource.iceberg.xxx` 的配置自动作为 Iceberg 数据源的默认选项。这是 Spark 3.4+ 已有的能力（参考 main 分支对应的 Spark 模块），此次提交将同样的能力补齐到 Spark 3.3 模块。

## 如何达成设计目的

1. 在 `IcebergSource` 类声明上新增实现 `SessionConfigSupport` 接口。
2. 引入 `org.apache.spark.sql.connector.catalog.SessionConfigSupport` 的 import。
3. 新增 `keyPrefix()` 方法并返回 `shortName()`（即 `"iceberg"`）。
4. 新增单元测试 `testSessionConfigSupport` 验证：
   - 通过 `spark.datasource.iceberg.snapshot-property.foo=bar` 在写数据时把 `foo=bar` 注入到快照 summary；
   - 通过 `spark.datasource.iceberg.snapshot-id=<id>` 在读数据时把读取行为限定到指定快照。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java`

**修改目的**：让 `IcebergSource` 实现 `SessionConfigSupport` 接口，开启 Spark Session 配置到数据源选项的自动透传。

**工作逻辑**：

- 新增 import：`org.apache.spark.sql.connector.catalog.SessionConfigSupport`。
- 类声明由 `public class IcebergSource implements DataSourceRegister, SupportsCatalogOptions` 改为多实现一个接口：
  ```java
  public class IcebergSource
      implements DataSourceRegister, SupportsCatalogOptions, SessionConfigSupport {
  ```
- 在 `shortName()` 方法之后新增 `keyPrefix()` 实现，返回 `shortName()`（即 `"iceberg"`）。Spark 在构建 DataSourceV2 的 options 时，会读取 `keyPrefix()`，然后把 `spark.datasource.iceberg.*` 前缀的所有配置项剥离前缀后作为 options 注入到 `IcebergSource` 的 `CaseInsensitiveStringMap` 中。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java`

**修改目的**：新增测试覆盖 SessionConfigSupport 行为。

**工作逻辑**：

- 新增 `testSessionConfigSupport` 测试方法。
- 先建表、写入一批基础数据，记录第一次写入后的 `snapshotId` 为 `s1`。
- 用 `withSQLConf` 设置 `spark.datasource.iceberg.snapshot-property.foo=bar`，再写入一批数据；之后刷新表，断言当前快照的 summary 包含 `foo=bar`，说明写选项已通过 Session 配置成功注入。
- 再用 `withSQLConf` 设置 `spark.datasource.iceberg.snapshot-id=<s1>`，读取该表，断言读到的数据正好是第一次写入的初始数据，说明读选项也通过 Session 配置成功注入。

## 小结

- **成效**：Spark 3.3 模块的 `IcebergSource` 现已支持通过 Session 配置统一注入读写选项，与 Spark 3.4/3.5 模块保持一致，便于在 SQL 场景或重复写入场景统一管理 Iceberg 选项。
- **影响范围**：仅 Spark 3.3 模块的 `IcebergSource` 主类与对应测试，无运行时元数据或表格式变更，无 API 破坏性变化。
- **回迁到 1.4.x 的注意事项**：1.4.x 维护分支对应 Spark 3.3/3.4/3.5 模块，若 1.4.x 的 Spark 3.3 模块尚缺此能力，可以无风险回迁——这是纯增强、向后兼容的改动，仅新增 `keyPrefix()` 方法实现接口。需注意 1.4.x 引用的 Spark 3.3 编译期 API 是否已包含 `SessionConfigSupport`（Spark 3.2 起即存在该接口，3.3 同样可用）。回迁后建议同时回迁对应测试 `testSessionConfigSupport` 以避免回归。
