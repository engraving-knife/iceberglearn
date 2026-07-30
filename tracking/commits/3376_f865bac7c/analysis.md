# 提交 3376：Core, Spark: Fix equality deletes non-deterministic schema ordering (#13873)

## 提交信息

- **序号**：3376 / 4088
- **哈希**：f865bac7c15dada3543015b993b633760579ad88
- **短哈希**：f865bac7c
- **日期**：2026-03-12
- **作者**：Russell Spitzer
- **提交说明**：Core, Spark: Fix equality deletes non-deterministic schema ordering (#13873)
- **PR/Issue**：#13873

## 总体目的

本提交修复了一个隐蔽而严重的正确性缺陷：equality delete（等值删除）在某些查询投影下会被静默跳过，导致已删除的数据行重新出现在查询结果中。

问题的根源在于 `DeleteFilter.applyEqDeletes` 构建 equality delete 的 schema 时，调用了 `TypeUtil.select(requiredSchema, ids)`。该方法会保留 `requiredSchema` 中字段的出现顺序，而 `requiredSchema` 的字段顺序取决于具体查询的投影方式。当存在 `SparkExecutorCache`（执行器侧缓存）时，一个 reader 可能用一种字段顺序读取了 delete 记录并写入缓存，另一个期望不同字段顺序的 reader 命中同一缓存条目，`StructProjection` 按位置解释数据就会错位——把字段 a 的值当成字段 b，使得本应匹配的删除条件不再匹配，删除被跳过，数据正确性受损。

举例说明：表 schema 为 `[id(1), a(2), b(3)]`，equality delete 文件按 `[b(3), a(2)]` 顺序写入。窄投影（只查 id）时 delete 列按标识符顺序追加为 `[b, a]`，宽投影（查所有列）时 delete 列保持表 schema 顺序 `[a, b]`。两种投影产生不同的 `deleteSchema` 排序，缓存中存储的记录若被另一种投影的 reader 复用，就会产生字段错位的灾难性后果。

## 如何达成设计目的

核心思路是"规范化"（canonicalize）delete schema：不论查询投影如何，equality delete 的 schema 字段一律按 field ID 升序排列。这样所有 reader 产出的 delete schema 都一致，缓存命中返回的记录字段顺序与任何 reader 的期望都相同，消除了因投影差异导致的字段错位。实现上在 `TypeUtil` 中新增 `selectInIdOrder` 方法，并在 `DeleteFilter` 中替换原有 `select` 调用，同时补充单元测试与 Spark 端到端回归测试。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java` (+14/-0 lines)

**修改目的**：新增按 field ID 排序的字段选择方法。

**工作逻辑**：
新增静态方法 `selectInIdOrder(Schema schema, Set<Integer> fieldIds)`。它先调用已有的 `select(schema, fieldIds)` 取出所需字段（保留输入 schema 顺序），再对结果列按 `Types.NestedField::fieldId` 进行升序排序，最后以排序后的列表构造新的 `Schema`。方法注释明确指出其与 `select` 的区别：后者保留输入 schema 的字段顺序，前者始终按 field ID 排序。这一规范化保证了不同投影来源的 schema 能生成统一的字段排列。

### `api/src/test/java/org/apache/iceberg/types/TestTypeUtil.java` (+25/-0 lines)

**修改目的**：为 `selectInIdOrder` 补充单元测试。

**工作逻辑**：
测试构造了一个字段顺序与 ID 顺序不一致的 schema（`id(1), b(3), a(2)`），用 `selectInIdOrder` 选取字段 2、3，断言结果按 field ID 升序排列（先 2 后 3）。随后又构造了一个字段顺序完全相反的 schema 做同样选取，断言两次结果通过 `asStruct()` 相等，从而验证不同输入顺序都能产出统一的规范化结果。

### `data/src/main/java/org/apache/iceberg/data/DeleteFilter.java` (+1/-1 lines)

**修改目的**：将 equality delete 的 schema 构建改为使用规范化排序。

**工作逻辑**：
在 `applyEqDeletes` 中，原先的 `Schema deleteSchema = TypeUtil.select(requiredSchema, ids);` 被替换为 `TypeUtil.selectInIdOrder(requiredSchema, ids)`。这是修复的关键一行：此后构造的 `deleteSchema` 字段顺序不再依赖 `requiredSchema` 的投影顺序，而是固定按 field ID 排序。下游的 `StructProjection.create(requiredSchema, deleteSchema)` 据此进行字段重排，缓存键与缓存内容因此对所有投影一致，杜绝了缓存复用时的字段错位。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java` (+72/-0 lines)

**修改目的**：新增端到端回归测试覆盖缓存字段重排序缺陷。

**工作逻辑**：
新增测试 `testEqualityDeletesAppliedWithCachedFieldReordering`，构造表 schema `[id(1), a(2), b(3)]`，写入 10 行数据，再写入一个字段顺序为 `[b(3), a(2)]` 的 equality delete 文件删除其中 3 行。随后分别执行窄投影（`select("id")`）和宽投影（`select("*")`）查询，断言两者返回的行数都等于 `10 - 3 = 7`。修复前，当窄投影先执行并污染缓存后，宽投影会命中缓存但字段错位，导致删除被跳过、返回行数偏多。该测试精确复现了这一场景，确保修复有效且防止回归。

## 总结

本提交修复了一个由非确定性 schema 字段顺序引发 equality delete 被静默跳过的严重正确性缺陷。通过引入按 field ID 排序的规范化 schema 选择方法，确保所有 reader 产出的 delete schema 一致，从根本上消除了执行器缓存复用时的字段错位风险，并辅以单元测试与端到端测试保障修复的可靠性。
