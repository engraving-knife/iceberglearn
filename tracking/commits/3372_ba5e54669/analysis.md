# 提交 3372：Core, Spark: add the comment table property key (#15531)

## 提交信息

- **序号**：3372 / 4088
- **哈希**：ba5e54669ae7b668f9ddc1fc6a473cdd5d45ed29
- **短哈希**：ba5e54669
- **日期**：2026-03-10
- **作者**：Steven Zhen Wu
- **提交说明**：Core, Spark: add the comment table property key (#15531)
- **PR/Issue**：#15531

## 总体目的

Iceberg 表属性中一直缺少一个标准化的 `comment` 属性键。在 Spark 中，表的注释（comment）通常通过 Spark Catalog 的 `TableCatalog.PROP_COMMENT` 常量来引用，这意味着 Iceberg 的测试代码需要依赖 Spark 的 API 来断言表属性中是否包含 comment。这造成了不必要的耦合——Core 层和测试代码不应直接依赖 Spark 的 `TableCatalog` 类。

本次提交在 Iceberg Core 的 `TableProperties` 类中新增了 `COMMENT` 常量（值为 `"comment"`），作为 Iceberg 自有的表属性键来表示表的业务含义和使用上下文。随后将 Spark 3.4/3.5/4.0/4.1 四个版本的 `TestCreateTable` 测试中对 `TableCatalog.PROP_COMMENT` 的引用替换为 `TableProperties.COMMENT`，消除了对 Spark API 的不必要依赖。

## 如何达成设计目的

改动非常简洁：在 Core 层的 `TableProperties.java` 中新增 `COMMENT` 常量并附文档注释；在四个 Spark 版本的 `TestCreateTable.java` 中将 `TableCatalog.PROP_COMMENT` 替换为 `TableProperties.COMMENT`，同时移除对 `TableCatalog` 的 import。这使得表属性的 comment 键在 Iceberg 层面有了标准定义。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+3/-0 lines)

**修改目的**：新增 `COMMENT` 表属性键常量。

**工作逻辑**：
在 `TableProperties` 类中新增：
```java
/** A table property that documents the business meaning and usage context of this table. */
public static final String COMMENT = "comment";
```
该常量放在已有的一些默认属性常量之后，值为 `"comment"`，与 Spark 的 `TableCatalog.PROP_COMMENT` 值一致，保证了向后兼容性。文档注释说明该属性用于记录表的业务含义和使用上下文。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestCreateTable.java` (+1/-2 lines)、`spark/v3.5/.../TestCreateTable.java` (+1/-2 lines)、`spark/v4.0/.../TestCreateTable.java` (+1/-2 lines)、`spark/v4.1/.../TestCreateTable.java` (+1/-2 lines)

**修改目的**：将测试中对 Spark `TableCatalog.PROP_COMMENT` 的引用替换为 Iceberg 的 `TableProperties.COMMENT`。

**工作逻辑**：
四个文件的改动完全一致。移除了 `import org.apache.spark.sql.connector.catalog.TableCatalog`，将断言 `.containsEntry(TableCatalog.PROP_COMMENT, "Table doc")` 改为 `.containsEntry(TableProperties.COMMENT, "Table doc")`。由于 `TableProperties` 已经在文件中被 import，无需新增 import。改动后测试不再依赖 Spark 的 Catalog API 来引用 comment 属性键。

## 总结

本次提交在 Iceberg Core 层新增了标准化的 `comment` 表属性键，消除了 Spark 测试代码对 `TableCatalog.PROP_COMMENT` 的直接依赖，降低了模块耦合度。改动虽小但符合 Iceberg 将表属性键集中管理的设计规范，为未来跨引擎一致性提供了基础。
