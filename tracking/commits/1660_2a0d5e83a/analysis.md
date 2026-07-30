# 提交 1660 2a0d5e83a 分析

## 提交信息
- 哈希：2a0d5e83a5365db1dcf96855cf26bb998f918a6e
- 日期：2025-01-30 10:49:28 +0100
- 作者：Tom Tanaka
- 消息：Core, Spark: Make view metadata path configurable by `write.metadata.path` (#12017)

## 总体目的

本提交为 Iceberg 的 View（视图）功能增加可配置的元数据文件存储路径支持。在此之前，View 的元数据文件（`v1.metadata.json`、`v2.metadata.json` 等）只能存储在 View 的 location 下的 `metadata/` 子目录中，路径固定为 `{view.location}/metadata/{filename}`。这与 Table 早已支持的 `write.metadata.path` 配置项不对称——Table 可以通过该属性将元数据文件存储到独立位置（例如独立的元数据桶或更快的存储），而 View 缺少此能力。

本次改动让 View 也支持 `write.metadata.path` 属性，使 View 元数据文件可以存储到与 View 数据位置不同的自定义路径。这在以下场景有用：
- 将元数据集中存储到专门的元数据存储桶，便于管理和权限控制。
- 将元数据放到更高性能的存储上以加速 View 元数据加载。
- 数据与元数据分离的存储架构（如数据在对象存储、元数据在本地 HDFS）。

## 如何达成设计目的

设计思路与 Table 的 `write.metadata.path` 实现保持一致：
1. 在 `ViewProperties` 中新增常量 `WRITE_METADATA_LOCATION = "write.metadata.path"`，作为属性键名，与 Table 使用的属性名一致，保持配置语义统一。
2. 在 `BaseViewOperations.metadataFileLocation` 方法中，优先检查 View 元数据的 properties 中是否设置了 `write.metadata.path`：
   - 若设置了，则元数据文件路径为 `{customLocation}/{filename}`。
   - 若未设置，则保持原有行为：`{view.location}/metadata/{filename}`。
3. 通过 View 创建 API（`buildView().withProperty(...)`）或 Spark SQL（`TBLPROPERTIES ('write.metadata.path'='...')`）设置该属性。
4. 添加测试覆盖 Core 层和 Spark v3.4/v3.5 集成层。

### 修改详情

#### core/src/main/java/org/apache/iceberg/view/ViewProperties.java
新增公共常量：
```java
public static final String WRITE_METADATA_LOCATION = "write.metadata.path";
```
该常量定义了用于配置 View 元数据存储路径的属性键名，与 Table 的同名属性保持一致。

#### core/src/main/java/org/apache/iceberg/view/BaseViewOperations.java
修改 `metadataFileLocation(ViewMetadata metadata, String filename)` 方法：
- 旧实现：直接拼接 `{view.location}/metadata/{filename}`。
- 新实现：
  1. 先从 `metadata.properties()` 取 `ViewProperties.WRITE_METADATA_LOCATION` 的值。
  2. 若值非 null，则返回 `{customLocation}/{filename}`（customLocation 会通过 `LocationUtil.stripTrailingSlash` 去掉末尾斜杠以规范化）。
  3. 若值为 null，则走原有逻辑返回 `{view.location}/metadata/{filename}`。

这样在写入新的 View 元数据文件时，会根据属性决定存储位置；读取时则通过 metadata JSON 中记录的 metadata-log location 定位，不受此属性影响（属性只影响"新写入的元数据文件放在哪里"）。

#### core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java
新增测试方法 `createViewWithCustomMetadataLocation`：
- 创建一个 View，通过 `withProperty(ViewProperties.WRITE_METADATA_LOCATION, customLocation)` 设置自定义元数据路径。
- 验证：
  - View 创建成功且存在。
  - View 的 properties 中包含 `write.metadata.path` 条目且值为 customLocation。
  - 通过 `((BaseView) view).operations().current().metadataFileLocation()` 获取当前元数据文件位置，断言其以 customLocation 开头。
- 这是一个抽象测试基类方法，会被各 Catalog 实现的测试子类继承执行（覆盖 JDBC、Hadoop、REST 等 Catalog）。

#### docs/docs/view-configuration.md
更新 View 配置文档表格：
- 新增一行 `write.metadata.path`，默认值为 `view location + /metadata`，描述为"Base location for metadata files"。
- 调整表格列宽以容纳新行。

#### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java
新增测试 `createViewWithCustomMetadataLocation`：
- 通过 Spark SQL `CREATE VIEW ... TBLPROPERTIES ('write.metadata.path'='...')` 创建 View。
- 使用 `DESCRIBE EXTENDED` 验证 View Properties 中包含 `write.metadata.path` 条目及其值。
- 验证 Spark SQL 路径能正确传递并持久化该属性。

#### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java
与 v3.4 类似的测试，使用 `@TestTemplate`（JUnit 5 参数化测试）注解，通过 `Paths.get(temp.toUri().toString(), "custom-metadata-location")` 构造自定义路径。

## 小结

本次改动成效：
- View 与 Table 在元数据路径配置上达成能力对等，提升了一致性。
- 支持元数据与数据分离存储，满足生产环境中对元数据管理、性能、权限的差异化需求。
- 改动最小化：仅修改一个方法逻辑、新增一个常量，对现有行为完全向后兼容（未设置属性时行为不变）。
- 测试覆盖 Core 层（多 Catalog）和 Spark v3.4/v3.5 集成层。

影响范围：Core 模块的 View 操作层、Spark v3.4/v3.5 集成测试、View 配置文档。不改变 View 元数据格式，仅改变元数据文件的存储位置。

回迁到 1.4.x 注意事项：
- 这是一个向后兼容的功能增强，回迁风险低。
- 1.4.x 若已支持 View 功能（View 是 Iceberg 1.x 较新加入的特性），回迁此提交可以让 1.4.x 的 View 也支持自定义元数据路径。
- 回迁时需确认 1.4.x 的 `BaseViewOperations` 和 `ViewProperties` 与本提交基于的版本差异；若结构一致，回迁非常直接。
- 注意 Spark 测试分别针对 v3.4 和 v3.5，回迁时需根据 1.4.x 维护的 Spark 版本范围选择性回迁测试。
- 该属性只影响新写入的元数据文件位置，不影响已有 View 的读取，因此回迁后无需迁移已有元数据。
