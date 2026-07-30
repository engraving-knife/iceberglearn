# 提交 1106：Core: Project data file stats only if there are equality deletes (#11013)

## 提交信息

- **序号**：1106 / 4088
- **哈希**：1898e621642cd0779302ce2c2933f86c3b1f6f6a
- **短哈希**：1898e6216
- **日期**：2024-08-26（Mon Aug 26 17:30:47 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Project data file stats only if there are equality deletes (#11013)
- **PR/Issue**：#11013
- **影响模块**：core（`DeleteFileIndex`、`ManifestGroup`、`DeleteFileIndexTestBase`）

## 总体目的

Iceberg 在读取时通过 `ManifestGroup.plan` 规划 `FileScanTask`。当表上存在 delete 文件时，需要把数据文件的列统计（metrics）投影出来，以便引擎在读取数据时执行 delete 匹配（equality delete 需要根据数据行内容与 delete 谓词匹配；position delete 按行号删除）。

修改前，`ManifestGroup` 的判断是：

```java
if (!deleteFiles.isEmpty()) {
  select(ManifestReader.withStatsColumns(columns));
}
```

只要 `DeleteFileIndex` 非空（即存在任何 equality 或 position delete）就把所有请求列的统计都投影出来。但实际上 **position delete 不需要数据文件列统计**——它仅按 `(file_path, row_position)` 删除，匹配过程不读列值；只有 **equality delete** 才需要列统计（如 min/max/value_counts 等）来决定某行是否可能被某个 equality delete 影响。

本提交把判断收紧为"只有存在 equality delete 时才投影 stats 列"，减少 position-delete-only 场景下不必要的 stats 投影开销，提升扫描性能。

## 如何达成设计目的

为了支持按 delete 类型分别判断，先把 `DeleteFileIndex` 内部原本合并计算 `isEmpty` 的逻辑拆开为两个布尔字段：

- `hasEqDeletes`：`globalDeletes != null || eqDeletesByPartition != null`
- `hasPosDeletes`：`posDeletesByPartition != null || posDeletesByPath != null`
- `isEmpty`：`!hasEqDeletes && !hasPosDeletes`（语义不变）

对外暴露 `hasEqualityDeletes()` 与 `hasPositionDeletes()` 两个查询方法。`ManifestGroup.plan` 中把 `!deleteFiles.isEmpty()` 替换为 `deleteFiles.hasEqualityDeletes()`，让 stats 投影更精准。最后在测试基类 `DeleteFileIndexTestBase` 中加断言验证新方法的语义。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java`

**修改目的**：分别记录 eq/pos delete 的存在性，对外暴露查询接口。

**工作逻辑**：

- 新增两个字段 `private final boolean hasEqDeletes;` 与 `private final boolean hasPosDeletes;`。
- 构造函数中：

  ```java
  this.hasEqDeletes = globalDeletes != null || eqDeletesByPartition != null;
  this.hasPosDeletes = posDeletesByPartition != null || posDeletesByPath != null;
  this.isEmpty = !hasEqDeletes && !hasPosDeletes;
  ```

  原来是 `boolean noEqDeletes = ...; boolean noPosDeletes = ...; this.isEmpty = noEqDeletes && noPosDeletes;`，等价改写。

- 新增 public 方法 `hasEqualityDeletes()` 返回 `hasEqDeletes`；`hasPositionDeletes()` 返回 `hasPosDeletes`。

### `core/src/main/java/org/apache/iceberg/ManifestGroup.java`

**修改目的**：仅在有 equality delete 时投影 stats 列。

**工作逻辑**：`plan` 方法中：

```java
// 改前
if (!deleteFiles.isEmpty()) {
  select(ManifestReader.withStatsColumns(columns));
}
// 改后
if (deleteFiles.hasEqualityDeletes()) {
  select(ManifestReader.withStatsColumns(columns));
}
```

`ManifestReader.withStatsColumns(columns)` 会把请求的列加上对应 stats（min/max/null_count/value_count 等）作为投影 schema。只在 equality delete 场景才需要这些 stats 做谓词匹配；position delete 仅按行号，不需要 stats。

### `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java`

**修改目的**：覆盖新增的 `hasEqualityDeletes`/`hasPositionDeletes` 方法语义。

**工作逻辑**：在 3 处既有测试方法（涉及 equality only / equality + position / equality + position 跨 spec 场景）的既有断言前，加上对 `hasEqualityDeletes()`/`hasPositionDeletes()` 的断言：

1. 仅 equality delete 场景：`hasEqualityDeletes()` 为 true，`hasPositionDeletes()` 为 false。
2. equality + position 场景：两者均为 true。
3. 跨 spec 的 equality + position 场景：两者均为 true。

## 小结

- **成效**：position-delete-only 场景下不再投影数据文件 stats 列，减少扫描时 manifest 读取与上下文传递的数据量，提升扫描性能。功能语义不变（equality delete 仍正确匹配）。新增 `hasEqualityDeletes`/`hasPositionDeletes` 公共查询方法，便于其他场景复用。
- **影响范围**：core 模块，3 个文件（2 个主代码 + 1 个测试基类）。属于性能优化，对结果集无影响。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个小而纯的性能优化，且改动非常局部（`DeleteFileIndex` 内部 + `ManifestGroup` 一行 + 测试），**回迁成本极低**。
  - 1.4.x 的 `DeleteFileIndex`/`ManifestGroup` 结构应该与本提交相近，可直接 cherry-pick；若 1.4.x 这两个文件结构与 main 差异较大，需手工对齐字段名/构造函数。
  - 由于属于内部优化，对外 API/行为不变，回迁不会引入兼容性问题；建议回迁以获得性能收益。
