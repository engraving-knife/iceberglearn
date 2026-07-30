# 提交 1420：Spark 3.4: IcebergSource extends SessionConfigSupport (#7732)

## 提交信息

- **序号**：1420 / 4088
- **哈希**：9cc13b11fbcb0091d6ad94882a1019dddf6b9023
- **短哈希**：9cc13b11f
- **日期**：2024-11-23（Sat Nov 23 14:57:50 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Spark 3.4: IcebergSource extends SessionConfigSupport (#7732)
- **PR/Issue**：#7732

## 总体目的

Iceberg 的 Spark 3.4 数据源入口 `IcebergSource` 实现了 `DataSourceRegister`（注册短名 `iceberg`）与 `SupportsCatalogOptions`（支持以 catalog 方式解析表），用户通过 `df.write.format("iceberg").option(...).save(path)` 或 `spark.read.format("iceberg").option(...).load(path)` 使用时，需要逐次在 `option` 中传入读写参数（如 `snapshot-property.foo=bar`、`snapshot-id=xxx`）。

Spark 提供了 `SessionConfigSupport` 接口：实现该接口的数据源可以自动从 Spark 会话配置（`spark.sql.conf`）中以 `spark.datasource.<keyPrefix>.<option>` 前缀读取参数，作为该数据源的默认选项。这样用户可以在会话级别一次性设置参数（如 `spark.sql.set spark.datasource.iceberg.snapshot-property.foo=bar`），后续所有 `iceberg` 格式的读写操作都自动带上该参数，避免每次 `option(...)` 重复指定。

本提交让 `IcebergSource` 实现 `SessionConfigSupport`，`keyPrefix()` 返回 `shortName()`（即 `"iceberg"`），从而支持会话级配置注入，提升批量场景下的可用性。

## 如何达成设计目的

修改 `IcebergSource.java`：
- 类声明追加 `implements SessionConfigSupport`（保留原有 `DataSourceRegister`、`SupportsCatalogOptions`）。
- 实现 `keyPrefix()` 方法，返回 `shortName()`（即 `"iceberg"`），让 Spark 以 `spark.datasource.iceberg.*` 作为前缀收集会话配置。

由于类声明变长，把类声明拆为多行格式以符合代码风格。

同时新增单元测试 `testSessionConfigSupport` 验证：
- 写入路径：通过 `spark.sql.conf` 设置 `spark.datasource.iceberg.snapshot-property.foo=bar`，验证写入后 snapshot summary 中包含 `foo=bar`。
- 读取路径：通过 `spark.sql.conf` 设置 `spark.datasource.iceberg.snapshot-id=<s1>`，验证读取时定位到指定快照。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java`

**修改目的**：让数据源支持会话级配置注入。

**工作逻辑**：
- 新增 import：`org.apache.spark.sql.connector.catalog.SessionConfigSupport`。
- 类声明由单行 `public class IcebergSource implements DataSourceRegister, SupportsCatalogOptions {` 改为多行：
  ```java
  public class IcebergSource
      implements DataSourceRegister, SupportsCatalogOptions, SessionConfigSupport {
  ```
- 在 `shortName()` 方法（返回 `"iceberg"`）之后新增 `keyPrefix()` 方法：
  ```java
  @Override
  public String keyPrefix() {
    return shortName();
  }
  ```
  即把前缀也设为 `"iceberg"`，与短名一致。Spark 在解析数据源选项时，会自动把 `spark.datasource.iceberg.<key>=<value>` 形式的会话配置注入到数据源的 options 中。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java`

**修改目的**：验证会话级配置对读写路径的生效。

**工作逻辑**：新增测试方法 `testSessionConfigSupport`（使用 `@Test` 注解，符合 3.4 测试基类约定）：

1. 准备：按 `id` 分区创建表 `db.session_config_table`，写入 3 条初始记录，记录当前快照 `s1`。
2. 验证写入路径会话配置：
   - 通过 `withSQLConf(ImmutableMap.of("spark.datasource.iceberg.snapshot-property.foo", "bar"), ...)` 在会话内设置 snapshot 属性。
   - 在闭包内再次 `df.write.format("iceberg").mode(Append).save(...)`，**不显式传 option**。
   - `table.refresh()` 后断言 `table.currentSnapshot().summary()` 包含 `foo=bar`，证明会话配置已被自动注入到写入选项。
3. 验证读取路径会话配置：
   - 通过 `withSQLConf(ImmutableMap.of("spark.datasource.iceberg.snapshot-id", String.valueOf(s1)), ...)` 在会话内设置读取快照 ID。
   - 在闭包内 `spark.read.format("iceberg").load(...)` 读取表，**不显式传 option**。
   - 断言读取结果与初始 3 条记录一致，证明读取时定位到了 `s1` 快照（而非最新快照），会话配置生效。

`withSQLConf` 是测试基类提供的工具方法，用于在闭包内临时设置 Spark 会话配置并在退出时恢复。

## 小结

- **成效**：Spark 3.4 的 `IcebergSource` 现支持会话级配置注入，用户可通过 `spark.datasource.iceberg.<key>=<value>` 在会话级统一设置数据源参数（如 snapshot-property、snapshot-id 等），避免每次读写都显式 `option(...)`，提升批量与 Notebook 场景的可用性。
- **影响范围**：2 文件、+54/-1 行。生产代码仅 `IcebergSource.java` 一处（新增 1 个接口实现 + 1 个方法 + 1 行 import），其余为新增测试。
- **回迁到 1.4.x 的注意事项**：本提交是对外行为增强（新增会话级配置支持），**回迁价值较高**，尤其对在 Spark 3.4 上大量使用 `iceberg` 数据源短名读写的用户。回迁风险低：`SessionConfigSupport` 接口自 Spark 3.0 起就存在，1.4.x 的 Spark 3.4 模块必然可用。回迁时需确认 1.4.x 的 `IcebergSource` 类声明与 `shortName()` 实现与 main 一致；若 1.4.x 已有其他对 `IcebergSource` 的修改（如新增其他接口实现），需合并不冲突。同时建议把测试用例一并回迁以保证回归覆盖。注意：本提交与 1421（Spark 3.5 版本）是配套的，若 1.4.x 同时维护两代 Spark，应配套回迁以保持行为一致。
