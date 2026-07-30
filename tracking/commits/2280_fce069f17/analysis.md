# 提交 2280：Spark 3.5: Fix row lineage inheritance for distributed planning (#13061)

## 提交信息

- **序号**：2280 / 4088
- **哈希**：fce069f1704fe5d1840b50014e8ed966377ee0b7
- **短哈希**：fce069f17
- **日期**：2025-06-27 11:09:53 -0400
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.5: Fix row lineage inheritance for distributed planning (#13061)
- **PR/Issue**：#13061

## 总体目的

本提交修复了 Spark 3.5 中分布式规划（distributed planning）场景下行级谱系（row lineage）信息丢失的问题。Row lineage 是 Iceberg 的一个特性，通过为每行数据分配唯一递增的 row ID 来追踪行的谱系，支持 MERGE/UPDATE/DELETE 等行级操作的正确性。

问题的根因在于 `ManifestFileBean`——这是一个可序列化的 ManifestFile 适配器（JavaBean），用于在 Spark 分布式规划时将 manifest 文件信息从 driver 序列化传输到 executor。`ManifestFileBean` 实现了 `ManifestFile` 接口，但缺少 `firstRowId` 字段的实现。`ManifestFile.firstRowId()` 是 row lineage 的关键字段，记录了该 manifest 中第一行的 row ID。由于 `ManifestFileBean.fromManifest()` 在复制 manifest 信息时未复制 `firstRowId`，导致 manifest 经序列化传输到 executor 后 `firstRowId()` 始终返回 null，分布式规划下的行级操作无法正确继承行谱系。

同时，本提交扩展了 `TestRowLevelOperationsWithLineage` 测试，新增 `@Parameters` 方法使测试在 LOCAL 和 DISTRIBUTED 两种规划模式下运行，确保行谱系在两种模式下都正确工作，防止此类回归。

## 如何达成设计目的

- 在 `ManifestFileBean` 中新增 `firstRowId` 字段（Long 类型）、`firstRowId()` getter 方法（实现 `ManifestFile` 接口）、`setFirstRowId(Long)` setter 方法，并在 `fromManifest()` 中调用 `bean.setFirstRowId(manifest.firstRowId())` 确保序列化时携带该信息。
- 在测试中新增 `@Parameters` 方法，定义两组参数：一组使用 LOCAL 规划模式 + HASH 分布模式 + 向量化读取，另一组使用 DISTRIBUTED 规划模式 + RANGE 分布模式 + 非向量化读取，覆盖分布式规划场景。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/ManifestFileBean.java` (+11/0 lines)

**修改目的**：使 `ManifestFileBean` 正确携带 `firstRowId` 字段，修复分布式规划下行谱系信息丢失。

**工作逻辑**：
- 新增字段 `private Long firstRowId = null;`。
- `fromManifest(ManifestFile)` 中新增 `bean.setFirstRowId(manifest.firstRowId());`，从源 manifest 复制 firstRowId。
- 新增 `setFirstRowId(Long)` setter 方法。
- 新增 `@Override public Long firstRowId() { return firstRowId; }` getter，实现 `ManifestFile` 接口的方法（此前该方法在文件末尾附近返回 null 或不存在）。

这样当 Spark 在分布式规划中将 `ManifestFileBean` 序列化到 executor 时，`firstRowId` 信息得以保留，行级操作能正确读取行谱系。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRowLevelOperationsWithLineage.java` (+41/0 lines)

**修改目的**：扩展测试覆盖分布式规划模式，验证行谱系在分布式规划下正确继承。

**工作逻辑**：新增 `@Parameters` 静态方法，参数名包含 `planningMode`（第 9 个参数），返回两组测试参数：
1. `LOCAL` 规划模式 + `WRITE_DISTRIBUTION_MODE_HASH` + 向量化读取（vectorized=true）+ formatVersion=3
2. `DISTRIBUTED` 规划模式 + `WRITE_DISTRIBUTION_MODE_RANGE` + 非向量化读取（vectorized=false）+ formatVersion=3

通过覆盖两种规划模式，测试能验证 `ManifestFileBean` 的 `firstRowId` 在分布式序列化传输后仍正确，防止回归。`formatVersion=3` 是 row lineage 所需的表格式版本。

## 总结

本提交通过为 `ManifestFileBean` 补充 `firstRowId` 字段的序列化支持，修复了 Spark 3.5 分布式规划下行谱系信息丢失的 bug。`ManifestFileBean` 作为 manifest 在 Spark driver↔executor 间传输的可序列化适配器，必须完整实现 `ManifestFile` 接口的所有字段；遗漏 `firstRowId` 会导致依赖行谱系的行级操作在分布式模式下失效。配套的测试扩展确保该场景被持续覆盖。
