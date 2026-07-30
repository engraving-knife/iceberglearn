# 提交 2880：Flink: Set table properties/location on DynamicIcebergSink table creation (#14578)

## 提交信息

- **序号**：2880 / 4088
- **哈希**：31ce47b8b3640be43ca9ac602798f931774b25a8
- **短哈希**：31ce47b8b
- **日期**：2025-11-17 14:03:51 +0100
- **作者**：Jordan Epstein
- **提交说明**：Flink: Set table properties/location on DynamicIcebergSink table creation (#14578)
- **PR/Issue**：#14578

## 总体目的

DynamicIcebergSink 是 Flink 中用于动态创建 Iceberg 表并写入数据的 sink。当输入流中的数据对应一张尚不存在的表时，sink 会自动创建该表。此前，自动创建表时使用 `catalog.createTable(identifier, schema, spec)`，这只能指定表名、schema 和分区规范，无法设置自定义表属性（table properties）或自定义存储位置（location）。

这在实际使用中是一个限制：用户可能需要为新创建的表设置特定的属性（如 `write.format`、`commit.retry.num-retries` 等）或指定自定义的存储路径（如按业务线分目录存储）。由于表是动态创建的，用户没有机会在创建前预设这些配置。

此提交引入了 `TableCreator` 函数式接口，允许用户在 DynamicIcebergSink 的 Builder 中通过 `tableCreator()` 方法注入自定义的表创建逻辑。用户可以基于表名动态决定要设置的属性和位置，实现灵活的动态表创建策略。

## 如何达成设计目的

1. 新建 `TableCreator` 函数式接口，定义 `createTable` 方法，默认实现为 `Catalog::createTable`（即原有的创建逻辑）。
2. 在 `DynamicIcebergSink.Builder` 中增加 `tableCreator` 字段和 setter 方法，默认值为 `TableCreator.DEFAULT`。
3. 将 `tableCreator` 传递给 `DynamicRecordProcessor` 和 `DynamicTableUpdateOperator`，再传递给 `TableUpdater`。
4. 在 `TableUpdater.findOrCreateTable` 中，将 `catalog.createTable(identifier, schema, spec)` 替换为 `tableCreator.createTable(catalog, identifier, schema, spec)`。
5. 更新所有相关测试以适配新签名，并新增测试验证自定义属性和位置的设置。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableCreator.java` (+34/-0 lines, 新文件)

**修改目的**：定义表创建的函数式接口。

**工作逻辑**：`TableCreator` 是一个 `@FunctionalInterface`，继承 `Serializable`（Flink 序列化要求）。定义了 `createTable(Catalog, TableIdentifier, Schema, PartitionSpec)` 方法。默认实现 `DEFAULT = Catalog::createTable`，方法引用直接委托给 `Catalog.createTable`，保持与原有行为完全一致。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+12/-1 lines)

**修改目的**：在 Builder 中暴露 tableCreator 配置并传递给下游算子。

**工作逻辑**：Builder 新增 `tableCreator` 字段（默认 `TableCreator.DEFAULT`）和 `tableCreator(TableCreator)` setter。在构建 `DynamicRecordProcessor` 和 `DynamicTableUpdateOperator` 算子时，将 `tableCreator` 作为构造参数传入。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (+6/-1 lines)

**修改目的**：传递 TableCreator 到 TableUpdater。

**工作逻辑**：新增 `tableCreator` 字段和构造参数。在 `immediateUpdate` 分支调用 `updater.update()` 时，将 `tableCreator` 作为参数传入。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicTableUpdateOperator.java` (+8/-2 lines)

**修改目的**：传递 TableCreator 到 TableUpdater。

**工作逻辑**：新增 `tableCreator` 字段和构造参数。在 `map()` 方法调用 `updater.update()` 时，将 `tableCreator` 作为参数传入。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableUpdater.java` (+10/-4 lines)

**修改目的**：使用 TableCreator 替代直接的 catalog.createTable。

**工作逻辑**：`update()` 方法新增 `TableCreator tableCreator` 参数，传递给 `findOrCreateTable()`。在 `findOrCreateTable()` 中，表不存在时调用 `tableCreator.createTable(catalog, identifier, schema, spec)` 替代 `catalog.createTable(identifier, schema, spec)`。并发创建冲突（`AlreadyExistsException`）的递归调用也传递 `tableCreator`。

### `docs/docs/flink-writes.md` (+1/-0 lines)

**修改目的**：文档记录新增的 tableCreator 配置选项。

**工作逻辑**：在 Dynamic Iceberg Flink Sink 的配置表中新增 `tableCreator(TableCreator creator)` 行，说明其用途。

### 测试文件修改

- `TestDynamicTableUpdateOperator.java`：构造算子时传入 `TableCreator.DEFAULT`。
- `TestTableMetadataCache.java`：调用 `tableUpdater.update()` 时传入 `TableCreator.DEFAULT`。
- `TestTableUpdater.java`：`testTableCreation` 测试改用 `InMemoryCatalog`，自定义 `TableCreator` 设置 location override 和 table properties，验证创建的表确实包含自定义属性和位置。其余测试方法传入 `TableCreator.DEFAULT`。

## 总结

该提交为 Flink DynamicIcebergSink 引入了 `TableCreator` 接口，允许用户自定义动态创建表时的属性和存储位置。默认实现保持原有行为不变（向后兼容），用户可通过 Builder 的 `tableCreator()` 方法注入自定义逻辑。这解决了动态创建表时无法设置表属性和位置的限制，增强了 DynamicIcebergSink 的灵活性。注意此修改仅应用于 Flink v2.1 分支（后续提交 2881 会将其 backport 到 v2.0 和 v1.20）。
