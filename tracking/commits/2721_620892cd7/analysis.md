# 提交 2721：Core, Spark: Deprecate `write.metadata.path` and use `location` to customize view location

## 提交信息

- **序号**：2721 / 4088
- **哈希**：620892cd70b5403e2571c1d119ce095887a0dfa5
- **短哈希**：620892cd7
- **日期**：2025-10-07 18:41:05 -0700
- **作者**：Tom Tanaka
- **提交说明**：Core, Spark: Deprecate `write.metadata.path` and use `location` to customize view location
- **PR/Issue**：#14212

## 总体目的

在 Iceberg 中，View（视图）的元数据存储位置可以通过 `write.metadata.path` 属性来指定。这个属性允许用户将视图的元数据文件存储到与视图默认位置不同的路径。然而，这种设计存在一些问题：

首先，`write.metadata.path` 属性的语义不够清晰。它实际上指定的是元数据的基础路径（metadata 路径会在其后追加 `/metadata`），但属性名暗示的是写入元数据的路径，容易引起误解。

其次，Iceberg 表（Table）使用 `location` 属性来指定存储位置，而视图使用 `write.metadata.path` 来实现类似功能，这种不一致性增加了用户的认知负担。社区希望统一表和视图的配置方式，使用 `location` 属性来指定视图的存储位置。

此提交将 `write.metadata.path` 属性标记为废弃（将在 2.0.0 中移除），并推荐使用 `ViewBuilder.withLocation` 或通过 `TBLPROPERTIES ('location'='...')` 来指定视图位置。这样视图与表使用了统一的 `location` 属性来配置存储位置。

## 如何达成设计目的

主要设计思路：
1. 在 `ViewProperties.java` 中将 `WRITE_METADATA_LOCATION` 常量标记为 `@Deprecated`
2. 在 Spark DDL 文档中添加使用 `location` 属性创建视图的说明
3. 从视图配置文档中移除 `write.metadata.path` 属性的说明
4. 在 Spark 3.5 和 4.0 的测试中添加使用 `location` 属性创建视图的测试用例

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewProperties.java` (+7/-1 lines)

**修改目的**：将 `WRITE_METADATA_LOCATION` 常量标记为废弃。

**工作逻辑**：将 `public static final String WRITE_METADATA_LOCATION = "write.metadata.path"` 拆分为两部分：添加 `@Deprecated` 注解和 Javadoc 说明（将在 2.0.0 中移除，推荐使用 `ViewBuilder.withLocation`），然后保留常量定义。常量本身仍保留以保持向后兼容，但编译时会产生废弃警告。

### `docs/docs/spark-ddl.md` (+12/-0 lines)

**修改目的**：添加使用 `location` 属性创建视图的文档说明。

**工作逻辑**：在 "Creating a view" 相关章节后添加 "Creating a view with location" 小节，说明用户可以通过 `TBLPROPERTIES ('location'='fully-qualified-uri')` 来指定视图元数据位置。文档还说明视图元数据会存储在指定位置下的 `/metadata` 子目录中。

### `docs/docs/view-configuration.md` (+0/-1 lines)

**修改目的**：从视图配置属性表中移除 `write.metadata.path` 行。

**工作逻辑**：由于 `write.metadata.path` 已被废弃，不再在配置文档中列出该属性，避免用户继续使用已废弃的配置项。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java` (+16/-0 lines)

**修改目的**：添加使用 `location` 属性创建视图的测试用例。

**工作逻辑**：新增 `createViewWithCustomMetadataLocationWithLocation` 测试方法：
1. 使用 `CREATE VIEW ... TBLPROPERTIES ('location'='...')` 创建视图
2. 通过 `SHOW TBLPROPERTIES` 验证 `location` 属性已正确设置
3. 通过 `viewCatalog().loadView()` 加载视图并验证其 `location()` 返回值与设置的路径一致

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java` (+16/-0 lines)

**修改目的**：在 Spark 4.0 中添加相同的测试用例。逻辑同上。

## 总结

此提交推进了 Iceberg 视图配置的标准化，将 `write.metadata.path` 废弃，推荐使用与表一致的 `location` 属性来指定视图存储位置。这简化了用户配置模型，使表和视图的存储位置配置保持一致。废弃的属性将在 2.0.0 中移除，给了用户充足的迁移时间。文档和测试的更新确保了新配置方式的可用性和可验证性。
