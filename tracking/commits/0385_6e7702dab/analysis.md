# 提交 0385：Core: Remove deprecated operations method from BaseMetadataTable

## 提交信息

- **序号**：0385
- **哈希**：6e7702dabca4e037bb1ab434d6f4a8332af589bc
- **短哈希**：6e7702dab
- **日期**：Thu Jan 18 21:40:49 2024 +0530
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Core: Remove deprecated operations method from BaseMetadataTable (#9298)
- **PR/Issue**：#9298

## 总体目的

这个提交是 Iceberg 1.4.0 清理长期废弃 API 的演进动作。`BaseMetadataTable` 是所有元数据表（如 `DataFilesTable`、`HistoryTable`、`SnapshotsTable`、`ManifestsTable` 等）的抽象基类，它代表"基于某个真实表的元数据视图"。历史上它实现了 `HasTableOperations` 接口，并暴露了一个 `operations()` 方法返回底层 `BaseTable` 的 `TableOperations`。

这个设计的本质问题在于：元数据表本身不应该被当作可独立操作的对象，它只是一个"读取快照"的载体。文档注释明确写道 "do not use metadata table TableOperations"，并标注 `@Deprecated will be removed in 1.4.0`。让元数据表实现 `HasTableOperations` 会让使用者误以为可以对元数据表进行 commit/事务操作，这与元数据表只读、基于静态 `StaticTableOperations` 的设计意图相违背，是一个会带来语义混淆和误用的 API。

因此 1.4.0 在版本说明中承诺要删除该方法。本提交执行这一承诺：从 `BaseMetadataTable` 移除 `HasTableOperations` 实现与 `operations()` 方法，并把 `table()` 方法从 `protected` 提升为 `public`，让需要访问底层表的代码（如获取底层 `TableOperations` 拿 UUID 或 metadata location）改为通过 `table().operations()` 显式访问——这样把"误用元数据表的 operations"路径堵死，把"需要拿到底层表"的合法路径明示出来。

这是一次有破坏性的 API 治理：二进制不兼容（接口集合变化、方法被删除），因此必须同步更新 revapi 的 `acceptedBreaks` 配置显式接受这些破坏，并修改所有内部依赖该 API 的调用点（Spark 各版本的 `BaseFileRewriteCoordinator`、`ScanTaskSetManager`、`SerializableTable`），把"通过 `HasTableOperations` 拿 UUID"的逻辑重写为"通过新的 `Spark3Util.baseTableUUID` 拿 UUID"。

## 如何达成设计目的

整体路径分三层：1）在 `BaseMetadataTable` 中删除 `implements HasTableOperations` 与 `@Deprecated operations()` 方法，并把 `table()` 从 `protected` 改为 `public`，作为新的合法访问入口；2）把所有原本依赖"元数据表是 `HasTableOperations`"的内部代码改为新路径——`SerializableTable` 中新增对 `BaseMetadataTable` 的分支，Spark 各版本 `BaseFileRewriteCoordinator` 和 `ScanTaskSetManager` 中的 `tableUUID(Table)` 私有方法被替换为调用 `Spark3Util.baseTableUUID(table)`，而 `Spark3Util` 新增一个能同时处理 `HasTableOperations` 与 `BaseMetadataTable` 的工具方法；3）在 `.palantir/revapi.yml` 中为所有受影响类添加 `acceptedBreaks` 条目（`java.class.noLongerImplementsInterface` 与 `java.method.removed`），让 API 兼容性检查工具接受这些被显式认可的破坏，避免 CI 失败。

## 修改详情

### core/src/main/java/org/apache/iceberg/BaseMetadataTable.java

**修改目的**：移除已被标记 `@Deprecated` 的 `operations()` 方法以及 `HasTableOperations` 接口实现，避免使用者误把元数据表当作可操作对象。

**工作逻辑**：类签名从 `public abstract class BaseMetadataTable extends BaseReadOnlyTable implements HasTableOperations, Serializable` 改为 `public abstract class BaseMetadataTable extends BaseReadOnlyTable implements Serializable`，去掉对 `HasTableOperations` 的实现。`table()` 方法从 `protected BaseTable table()` 提升为 `public BaseTable table()`，成为新的合法访问底层 `BaseTable` 的入口。删除整段 `@Deprecated public TableOperations operations() { return table.operations(); }` 方法及其 javadoc 注释。这样元数据表不再"假装"自己有 operations，需要拿到底层表操作的代码必须显式 `metadataTable.table().operations()`——语义更清晰，且杜绝了"对元数据表做 commit"这类错误调用。

### core/src/main/java/org/apache/iceberg/SerializableTable.java

**修改目的**：在 `metadataFileLocation()` 路径中补充对 `BaseMetadataTable` 的处理，因为元数据表不再是 `HasTableOperations`，原来的分支不再命中。

**工作逻辑**：原代码 `if (table instanceof HasTableOperations) { ... return ops.current().metadataFileLocation(); } else { return null; }`。现在元数据表会落到 `else` 返回 null，丢失了 metadata location。新增 `else if (table instanceof BaseMetadataTable) { return ((BaseMetadataTable) table).table().operations().current().metadataFileLocation(); }` 分支，通过新公开的 `table()` 方法拿到底层 `BaseTable`，再走 `operations().current().metadataFileLocation()`，恢复了对元数据表的 location 解析能力。这是新设计下的"显式访问底层表"路径的典型示例。

### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/Spark3Util.java（与 v3.4、v3.5 同步）

**修改目的**：新增一个集中处理"如何拿表 UUID"的工具方法，兼容普通表（`HasTableOperations`）与元数据表（`BaseMetadataTable`）两种情况，替代分散在各 coordinator 中的私有方法。

**工作逻辑**：导入 `BaseMetadataTable` 与 `HasTableOperations` 后，新增 `static String baseTableUUID(org.apache.iceberg.Table table)`：若 table 是 `HasTableOperations`，走原路径 `((HasTableOperations) table).operations().current().uuid()`；若 table 是 `BaseMetadataTable`，走新路径 `((BaseMetadataTable) table).table().operations().current().uuid()`——通过 `table()` 拿到底层 `BaseTable` 再取 UUID；否则抛 `UnsupportedOperationException("Cannot retrieve UUID for table " + table.name())`。这种集中化处理把"两种表形态的差异"封装在一处，调用方只需调用 `Spark3Util.baseTableUUID(table)` 即可，符合"封装 + 单一职责"的演进思路。该方法包级可见（`static`，无修饰符），仅供 spark 包内使用。

### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/BaseFileRewriteCoordinator.java（与 v3.4、v3.5 同步）

**修改目的**：把原来依赖"table 是 `HasTableOperations`"的 UUID 提取逻辑替换为对 `Spark3Util.baseTableUUID` 的调用，适配元数据表不再实现该接口的新事实。

**工作逻辑**：删除 `import org.apache.iceberg.HasTableOperations;` 与 `import org.apache.iceberg.TableOperations;`。`fetchSetIds` 中的过滤器从 `e -> e.first().equals(tableUUID(table))` 改为 `e -> e.first().equals(Spark3Util.baseTableUUID(table))`。`toId` 方法体从 `String tableUUID = tableUUID(table); return Pair.of(tableUUID, setId);` 简化为 `return Pair.of(Spark3Util.baseTableUUID(table), setId);`。私有方法 `private String tableUUID(Table table) { TableOperations ops = ((HasTableOperations) table).operations(); return ops.current().uuid(); }` 被整体删除。这些改动让 coordinator 在面对元数据表时也能正确取到 UUID（间接通过底层 `BaseTable`），而不是抛 ClassCastException。

### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/ScanTaskSetManager.java（与 v3.4、v3.5 同步）

**修改目的**：与 `BaseFileRewriteCoordinator` 同理，把 UUID 提取逻辑替换为 `Spark3Util.baseTableUUID` 调用。

**工作逻辑**：删除 `HasTableOperations` 与 `TableOperations` 的 import；`fetchSetIds` 中过滤条件改为 `Spark3Util.baseTableUUID(table)`；删除私有 `tableUUID(Table)` 方法；`toId` 方法体简化为 `return Pair.of(Spark3Util.baseTableUUID(table), setId);`。改动模式与 coordinator 完全一致，是统一重构。

### .palantir/revapi.yml

**修改目的**：对 revapi（API 兼容性检查工具）显式登记本次 API 破坏为"已被认可"，避免 CI 因二进制/源码兼容性破坏而失败。

**工作逻辑**：在 `1.4.0` 节的 `org.apache.iceberg:iceberg-core` 下新增大量 `acceptedBreaks` 条目。一类是 `java.class.noLongerImplementsInterface`，针对每个继承自 `BaseMetadataTable` 的具体元数据表类（`AllDataFilesTable`、`AllDeleteFilesTable`、`AllEntriesTable`、`AllFilesTable`、`AllManifestsTable`、`BaseMetadataTable`、`DataFilesTable`、`DeleteFilesTable`、`FilesTable`、`HistoryTable`、`ManifestEntriesTable`、`ManifestsTable`、`MetadataLogEntriesTable`、`PartitionsTable`、`PositionDeletesTable`、`RefsTable`、`SnapshotsTable`）登记"不再实现 `HasTableOperations`"这一破坏，理由统一为 "Removing deprecated code"。另一类是 `java.method.removed`，针对 `BaseMetadataTable::operations()` 方法被删除登记破坏。这种集中式登记是 Iceberg 项目管理 API 演进的规范操作：每一次有意识的破坏都必须在 revapi 配置中显式说明并给出理由，确保破坏都是有计划、有记录的。

## 小结

这个提交是 1.4.0 履行"删除 1.4.0 废弃 API"承诺的标准演进动作，模式可以概括为"删除废弃接口实现 + 公开合法替代入口（`table()` 由 protected 提升为 public）+ 集中工具方法（`Spark3Util.baseTableUUID`）+ 显式登记 API 破坏（revapi acceptedBreaks）"。其设计意图是把"误以为可以对元数据表做 commit"的危险路径堵死，把"需要拿到底层表"的合法路径明示出来。涉及面较广（12 个文件，跨 core + spark v3.3/v3.4/v3.5 三个版本），但每个 Spark 版本的改动模式高度一致，是典型的"接口瘦身 + 调用点统一重构"演进。
