# 提交 1626：Flink: Backport default values support in Parquet reader on Flink v1.18 and v1.19 (#12072)

## 提交信息

- **序号**：1626 / 4088
- **哈希**：17bda20b2901ab76adac1339dccd79656b913d33
- **短哈希**：17bda20b2
- **日期**：2025-01-23（Thu Jan 23 22:02:42 2025 +0100）
- **作者**：JB Onofré <jb.onofre@dremio.com>
- **提交说明**：Flink: Backport default values support in Parquet reader on Flink v1.18 and v1.19 (#12072)
- **PR/Issue**：#12072
- **关联提交**：#11839（提交 1625，Flink 1.20 版本的同一改动）

## 总体目的

提交 1625（#11839）为 Flink 1.20 的 Iceberg Parquet 读取器（`FlinkParquetReaders`）添加了 Iceberg v3 `initial-default` 默认值支持与 required 字段缺失校验。但 Iceberg 同时维护多个 Flink 版本（1.18、1.19、1.20），1.18 与 1.19 版本的 `FlinkParquetReaders` 仍是旧代码——读取缺失字段时填 null 而非默认值、required 字段缺失时静默填 null。

本提交把 1625 的改动逐字回迁到 Flink 1.18 与 1.19 两个版本模块，使三个 Flink 版本的 Parquet 读取器行为一致：都支持 `initial-default`、都对 required 字段缺失抛异常。这确保用户无论使用哪个 Flink 版本，都能获得一致的 v3 默认值语义。

## 如何达成设计目的

把提交 1625 对 `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` 与 `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java` 的修改，原样复制到 `flink/v1.18/` 与 `flink/v1.19/` 对应路径。四个文件的 diff 与 1625 完全相同（相同行号、相同改动），只是路径前缀不同。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java`（修改）

**修改目的**：回迁 `initial-default` 支持与 required 字段校验到 Flink 1.18。

**工作逻辑**：与提交 1625 中 `flink/v1.20` 版本的修改完全相同——`struct` 方法字段处理分支从嵌套 if-else 改为扁平 else-if 链，新增 `field.initialDefault() != null` 分支（用 `RowDataUtil.convertConstant` 转换默认值后用 `ParquetValueReaders.constant` 填充）与 required 字段缺失抛 `IllegalArgumentException` 分支；加 `@SuppressWarnings("checkstyle:CyclomaticComplexity")`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java`（修改）

**修改目的**：回迁默认值测试到 Flink 1.18。

**工作逻辑**：与提交 1625 完全相同——`supportsDefaultValues()` 返回 `true`；`writeAndValidate` 拆分为 `writeSchema`/`expectedSchema` 双参数版本支持 schema 演进测试；新增 `writeAndValidate(Schema writeSchema, Schema expectedSchema)` 重写。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java`（修改）

**修改目的**：同上，回迁到 Flink 1.19。

**工作逻辑**：与 v1.18 版本修改完全相同。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java`（修改）

**修改目的**：同上，回迁到 Flink 1.19。

**工作逻辑**：与 v1.18 版本修改完全相同。

## 小结

- **成效**：把 Flink 1.20 的 Parquet 默认值支持（提交 1625）回迁到 Flink 1.18 与 1.19，使三个 Flink 版本的 `FlinkParquetReaders` 行为一致——都支持 Iceberg v3 `initial-default` 语义、都对 required 字段缺失抛异常。用户在 1.18/1.19 上读取含默认值字段的旧文件时能正确获得默认值而非 null。
- **影响范围**：`flink/v1.18` 与 `flink/v1.19` 两个模块各 2 个文件（生产 + 测试），共 4 个文件。改动内容与提交 1625 完全相同。
- **回迁到 1.4.x 的注意事项**：本提交本身即是回迁操作（从 1.20 回迁到 1.18/1.19）。若 1.4.x 也支持 Flink 1.18/1.19/1.20，则需把 1625 + 1626 一起回迁到 1.4.x 对应的 Flink 版本模块。前提条件与 1625 相同：1.4.x 的 Iceberg core 需已支持 `field.initialDefault()` API（v3 默认值支持），`RowDataUtil.convertConstant` 需可用。行为变更（required 字段缺失从静默 null 改为抛异常）需通知 1.4.x 用户。若 1.4.x 的 Flink 模块结构与 main 一致（每个 Flink 版本独立目录），可按版本逐个 cherry-pick；若 1.4.x 不支持某些 Flink 版本（如 1.4.x 可能不含 v1.20），则只回迁存在的版本。
