# 提交 0870：Core: Pushdown data_file.content filter in entries metadata table (#10203)

## 提交信息

- **序号**：0870 / 4088
- **哈希**：5733aecd0d010b17b9bd0f10aaccb601b1f13c90
- **短哈希**：5733aecd0
- **日期**：2024-06-24（Mon Jun 24 10:39:26 2024 -0700）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Core: Pushdown data_file.content filter in entries metadata table (#10203)
- **PR/Issue**：#10203

## 总体目的

Iceberg 提供 entries 元数据表（`ManifestEntriesTable`、`EntriesTable` 等继承自 `BaseEntriesTable` 的元数据表），允许用户查询表中所有文件条目（数据文件 + 删除文件）的元信息，包括每个文件对应的 `data_file.content` 字段——其取值为 `0=DATA`（数据文件）、`1=POSITION_DELETES`（位置删除）、`2=EQUALITY_DELETES`（等值删除）。

此前，当用户在 entries 表上施加 `data_file.content = 0` 这类过滤条件时，Iceberg 的 manifest 过滤路径只通过 `ManifestEvaluator`（基于分区/文件统计）过滤 manifest，没有利用"manifest 自身的 content 类型"这一更粗粒度信息进一步剪枝。实际上 manifest 文件本身就有 `ManifestContent` 标记——`DATA(0)` 或 `DELETES(1)`，分别只包含数据文件或删除文件。利用这一信息，可以在 manifest 级别做更激进的剪枝：

- 如果过滤条件是 `data_file.content = 0`（仅数据文件），可跳过所有 `DELETES` manifest；
- 如果过滤条件是 `data_file.content = 1` 或 `2`（仅删除文件），可跳过所有 `DATA` manifest；
- 等值/IN/NOT EQ/NOT IN 等谓词都可类似推导。

本提交的目的就是在 `BaseEntriesTable` 的 manifest 过滤阶段新增一个 `ManifestContentEvaluator`，把对 `data_file.content` 的过滤条件"上推"到 manifest 级别，跳过内容类型明显不匹配的 manifest，减少 manifest 读取与逐文件过滤开销。这对有大量删除文件的表（典型如频繁 UPDATE/DELETE 的表）做 entries 表分析时尤其有价值。

## 如何达成设计目的

整体设计是新增一个内部类 `ManifestContentEvaluator`，它接受行级过滤表达式 `rowFilter` 和表 schema，将表达式绑定到 schema（注意 `data_file.content` 是嵌套字段，绑定器在 struct 上工作），然后用 `ExpressionVisitors.visitEvaluator` 走一遍表达式树，针对 `data_file.content` 相关谓词返回 manifest 是否可能含匹配行：

- `eq/notEq/in/notIn`：根据字面值是 `DATA`/`POSITION_DELETES`/`EQUALITY_DELETES` 与当前 manifest 的 `ManifestContent`（DATA 或 DELETES）做映射判断；
- `isNull/isNaN`：`data_file.content` 不会为 null/nan，故返回 `ROWS_CANNOT_MATCH`；
- 其他谓词（`lt/gt/startsWith` 等）保守返回 `ROWS_MIGHT_MATCH`。

`ManifestContentEvaluator` 与原有的 `ManifestEvaluator`（基于分区统计）并列，在 `BaseEntriesTable.planFiles` 中通过 `&&` 短路组合，对每个 manifest 同时做基于分区的过滤和基于 content 类型的过滤。`ManifestContentEvaluator` 通过 `ignoreResiduals` 控制是否启用——若 `ignoreResiduals=true` 则用 `alwaysTrue()` 表达式，等价于不剪枝，与原有 `ResidualEvaluator` 行为保持一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseEntriesTable.java`

**修改目的**：在 entries 元数据表的 manifest 过滤阶段新增基于 `data_file.content` 的剪枝，减少扫描的 manifest 数量。

**工作逻辑**：

1. 新增多个 import：`Binder`、`BoundReference`、`ExpressionVisitors`、`Literal`。

2. 在 `planFiles` 方法中，把 `filter` 变量提前到 manifest 过滤之前定义（原代码在过滤之后才定义），并构造 `ManifestContentEvaluator`：

```java
Expression filter = ignoreResiduals ? Expressions.alwaysTrue() : rowFilter;

LoadingCache<Integer, ManifestEvaluator> evalCache = ...;
ManifestContentEvaluator manifestContentEvaluator =
    new ManifestContentEvaluator(filter, tableSchema.asStruct(), caseSensitive);

CloseableIterable<ManifestFile> filteredManifests =
    CloseableIterable.filter(
        manifests,
        manifest ->
            evalCache.get(manifest.partitionSpecId()).eval(manifest)
                && manifestContentEvaluator.eval(manifest));
```

注意 `filter` 的定义从原来方法末尾移到开头，确保 `ManifestContentEvaluator` 与 `ResidualEvaluator` 共用同一个 filter。

3. 新增内部静态类 `ManifestContentEvaluator`，约 170 行。核心结构：

   - 构造时：`Expressions.rewriteNot(expr)` 把 `NOT` 重写为否定形式（避免 visitor 中处理 not），然后 `Binder.bind(structType, rewritten, caseSensitive)` 把表达式绑定到表 struct（使 `data_file.content` 字段引用变成 `BoundReference`）。
   - `eval(ManifestFile)`：记录当前 manifest 的 `content().id()`，然后用 `ExpressionVisitors.visitEvaluator(boundExpr, new ManifestEvalVisitor())` 求值。
   - `ManifestEvalVisitor` 继承 `BoundExpressionVisitor<Boolean>`，实现所有谓词：
     - `isNull/isNaN` 对 `data_file.content` 字段返回 `ROWS_CANNOT_MATCH`（content 不会为 null/nan）；
     - `eq/notEq`：通过 `lit.to(IntegerType)` 取整数字面值，调用 `contentMatch(int)` 判断该 file content id 是否可能出现在当前 manifest 中；
     - `in/notIn`：对集合中每个字面值调用 `contentMatch`，按"任一匹配/全部不匹配"返回结果；
     - 其他谓词（`lt/ltEq/gt/gtEq/startsWith/notStartsWith/notNull/notNaN`）一律保守返回 `ROWS_MIGHT_MATCH`。
   - `fileContent(ref)`：判断 `ref.fieldId() == DataFile.CONTENT.fieldId()`，确认当前谓词作用于 `data_file.content` 字段。
   - `contentMatch(fileContentId)`：核心映射逻辑——
     - `FileContent.DATA.id()(0)` ⟷ `ManifestContent.DATA.id()(0)`；
     - `FileContent.EQUALITY_DELETES.id()(2)` 或 `POSITION_DELETES.id()(1)` ⟷ `ManifestContent.DELETES.id()(1)`；
     - 其他 id 返回 `false`（不匹配任何 manifest）。

   设计要点：`ManifestContent` 只有两类（DATA/DELETES），而 `FileContent` 有三类（DATA/POSITION_DELETES/EQUALITY_DELETES），所以 `contentMatch` 把两类删除 file content 都映射到 DELETES manifest；如果用户查 `data_file.content = 1`，则所有 DELETES manifest 都会保留（因为可能含 POSITION_DELETES），但 DATA manifest 会被剪掉。

### `core/src/test/java/org/apache/iceberg/MetadataTableScanTestBase.java`

**修改目的**：把原有 `actualManifestListPaths` 辅助方法重命名为更通用的 `scannedPaths`，使其既能用于 manifests 表也能用于 entries 表的扫描路径断言。

**工作逻辑**：

```java
protected Set<String> scannedPaths(TableScan scan) {
  return StreamSupport.stream(scan.planFiles().spliterator(), false)
      .map(t -> t.file().path().toString())
      .collect(Collectors.toSet());
}
```

原方法签名是 `actualManifestListPaths(TableScan)` 并强制 cast 到 `AllManifestsTable.ManifestListReadTask`，重命名后去掉了 cast，直接用 `t.file().path()`，因此适用于任何返回 `FileScanTask` 的扫描（包括 entries 表）。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java`

**修改目的**：为 `ManifestContentEvaluator` 新增覆盖各谓词（eq/notEq/in/notIn）的测试，并把原 `actualManifestListPaths` 调用改名为 `scannedPaths`。

**工作逻辑**：

1. 引入 `Sets` import。
2. 新增四个测试用例：
   - `testEntriesTableDataFileContentEq`：验证 `data_file.content = 0` 只返回 data manifests；`data_file.content = 3`（不存在）返回空集。
   - `testEntriesTableDateFileContentNotEq`（注意拼写沿用了源码）：验证 `data_file.content != 0` 只返回 delete manifests；`data_file.content != 3` 返回所有 manifests。
   - `testEntriesTableDataFileContentIn`：验证 `in(0)` → data manifests，`in(1,2)` → delete manifests，`in(0,1,2)` → 全部，`in(3,4)` → 空。
   - `testEntriesTableDataFileContentNotIn`：验证 `notIn(0)` → delete manifests，`notIn(1,2)` → data manifests，`notIn(3)` → 全部，`notIn(0,1,2)` → 空。
3. 把原 manifests 表测试中所有 `actualManifestListPaths(...)` 调用改为 `scannedPaths(...)`（共 11 处）。

## 小结

- **成效**：在 entries 元数据表扫描时，把对 `data_file.content` 的过滤条件（eq/notEq/in/notIn）上推到 manifest 级别剪枝，跳过 content 类型明显不匹配的 manifest，减少 manifest 读取与逐文件过滤开销。对包含大量删除文件的表做 entries 表分析时性能改善尤其明显。
- **影响范围**：core 模块 3 个文件。主代码 `BaseEntriesTable.java` 新增 `ManifestContentEvaluator` 内部类（约 170 行）；测试 `MetadataTableScanTestBase.java` 重命名辅助方法（5 行改动），`TestMetadataTableScans.java` 新增 4 个测试用例（约 125 行）并改名 11 处调用。共 322 行新增、16 行删除。
- **回迁到 1.4.x 的注意事项**：本提交是性能优化，行为对外可见为"扫描计划相同结果但更少 manifest"，**回迁价值中等偏高**，特别适合 1.4.x 用户在大表上做 entries 表分析时受益。注意事项：(1) 改动只在 core 模块，与 Spark/Flink 等模块解耦，回迁无外部 API 影响；(2) 1.4.x 分支的 `BaseEntriesTable.java` 结构需与 main 相近，否则需手动适配 `ManifestContentEvaluator` 的插入位置；(3) 测试重命名（`actualManifestListPaths` → `scannedPaths`）涉及 11 处调用，1.4.x 若有自定义测试子类继承该方法需同步改名，否则编译会失败；(4) 该改动不影响存储格式或元数据兼容性，回迁无运行时兼容风险；(5) 与同批次提交（0861-0868、0870）无冲突，可独立回迁。
