# 提交 0769：Spark 3.5: Fix the setting of equalAuthorities in RemoveOrphanFilesProcedure (#10334)

## 提交信息

- **序号**：0769 / 4088
- **哈希**：788bea2695d78f8d16e7595d41de5ae7ca96b91e
- **短哈希**：788bea269
- **日期**：2024-05-16 20:05:38 +0800
- **作者**：dongwang
- **提交说明**：Spark 3.5: Fix the setting of equalAuthorities in RemoveOrphanFilesProcedure (#10334)
- **PR/Issue**：#10334

## 总体目的

修复 Spark 3.5 模块 `RemoveOrphanFilesProcedure` 中一个复制粘贴错误：解析 `equal_authorities` 参数时，错误地将键值对写入了 `equalSchemes` Map，而非 `equalAuthorities` Map。该 bug 导致 `equal_authorities` 参数完全失效——用户传入的"等价 authority"映射被错误地当作"等价 scheme"使用，既污染了 `equalSchemes`，又使 `equalAuthorities` 始终为空。本提交将那一行赋值的目标 Map 从 `equalSchemes` 改回 `equalAuthorities`，并新增测试覆盖该参数的实际行为。

## 如何达成设计目的

### Bug 成因

`RemoveOrphanFilesProcedure` 是 Spark 3.5 中 `system.remove_orphan_files` 存储过程的实现，用于删除 Iceberg 表中不再被任何快照引用的"孤儿"数据文件。该过程支持多个可选参数，其中两个与"等价"映射有关，用于在比对文件路径时容忍 authority 或 scheme 的差异（例如表 location 用了 `hdfs://nameservice1/...`，而文件清单中用了 `hdfs://localhost/...`）：

- 参数索引 6：`equal_schemes`（等价 scheme 映射，如 `hdfs -> hdfs`），写入 `Map<String, String> equalSchemes`。
- 参数索引 7：`equal_authorities`（等价 authority 映射，如 `localhost -> nameservice1`），写入 `Map<String, String> equalAuthorities`。

修复前，两个参数的解析代码几乎完全相同（典型的复制粘贴），但参数索引 7 的 lambda 中目标 Map 被错误地写成了 `equalSchemes`：

```java
Map<String, String> equalSchemes = Maps.newHashMap();
if (!args.isNullAt(6)) {
  args.getMap(6)
      .foreach(
          DataTypes.StringType,
          DataTypes.StringType,
          (k, v) -> {
            equalSchemes.put(k.toString(), v.toString());   // 正确：写入 equalSchemes
            return BoxedUnit.UNIT;
          });
}

Map<String, String> equalAuthorities = Maps.newHashMap();
if (!args.isNullAt(7)) {
  args.getMap(7)
      .foreach(
          DataTypes.StringType,
          DataTypes.StringType,
          (k, v) -> {
            equalSchemes.put(k.toString(), v.toString());   // BUG：应为 equalAuthorities
            return BoxedUnit.UNIT;
          });
}
```

后果：
1. `equalAuthorities` 始终为空 Map。下游 `DeleteOrphanFilesSparkAction` 拿到空的 `equalAuthorities`，无法识别用户声明的等价 authority，导致本应被容忍的 authority 差异被判定为"冲突"或"孤儿"，要么误报 `Conflicting authorities/schemes` 校验异常，要么误删文件。
2. `equalSchemes` 被用户传入的 `equal_authorities` 键值对污染（把 authority 当 scheme 用），进一步引发错误的 scheme 等价判定。

这是一个纯粹的复制粘贴 typo，根因是两段几乎相同的 lambda 中只差目标 Map 名，编写时漏改。

### 修复逻辑

修复仅一行：将参数索引 7 的 lambda 中的 `equalSchemes.put(k.toString(), v.toString())` 改为 `equalAuthorities.put(k.toString(), v.toString())`。修复后：

- `equal_authorities` 参数正确写入 `equalAuthorities` Map。
- `equalSchemes` 不再被 `equal_authorities` 输入污染，`equal_schemes` 参数行为恢复正常。

### 下游影响

`equalSchemes` 与 `equalAuthorities` 最终传给 `DeleteOrphanFilesSparkAction`，用于在比对"表引用的文件路径"与"文件系统实际存在的文件路径"时做 authority/scheme 归一化。修复前，authority 归一化完全失效；修复后，用户可通过 `equal_authorities` 参数声明如 `map('localhost', 'nameservice1')`，使 procedure 在比对时把 `localhost` 视同 `nameservice1`，避免因 HA 命名服务切换、location 与文件清单 authority 不一致等场景导致的误判。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/RemoveOrphanFilesProcedure.java`

**修改目的**：修正 `equal_authorities` 参数的写入目标。

**工作逻辑**：参数索引 7（`equal_authorities`）的 lambda 中，`equalSchemes.put(k.toString(), v.toString())` 改为 `equalAuthorities.put(k.toString(), v.toString())`。这是 diff 中唯一的生产代码改动（1 行增、1 行删）。其余解析逻辑（`args.isNullAt(7)` 判空、`foreach` 遍历、`BoxedUnit.UNIT` 返回）不变。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java`

**修改目的**：新增测试验证 `equal_authorities` 参数生效，并覆盖"未传该参数时冲突报错"的负向场景。

**新增测试 `testRemoveOrphanFilesProcedureWithEqualAuthorities`**，覆盖完整流程：

1. **建表**：根据 catalog 类型（`testhadoop` 用默认 location，其他用临时目录 location）创建测试表 `tableName`。
2. **构造 authority 不一致的文件路径**：
   - 加载表得到 `originalPath`，取其 URI 的 authority 作为 `originalAuthority`（可能为空串）。
   - 构造 `newParentPath = new Path(uri.getScheme(), "localhost", uri.getPath())`，即把 authority 强制改为 `localhost`。
   - 用 `newParentPath` 拼出 `path/to/data-a.parquet`、`path/to/data-b.parquet` 两个数据文件路径，构建 `DataFile` 并 `newFastAppend` 提交到表。这样表中记录的文件路径 authority 是 `localhost`，而表 location 的 authority 是 `originalAuthority`。
3. **构造 file_list_view**：模拟外部文件清单，包含以 `originalPath`（authority 为 `originalAuthority`）为前缀的两个数据文件路径、version hint 文件、metadata 文件、manifest 文件，时间戳统一为 `Timestamp(10000)`。注册为临时视图 `files_view`。
4. **正向验证（传 equal_authorities）**：调用 `system.remove_orphan_files`，传入 `equal_authorities => map('localhost', originalAuthority)` 与 `file_list_view => 'files_view'`。断言返回的 orphan 文件列表为空——因为 `equal_authorities` 让 procedure 把表文件路径的 `localhost` 归一化为 `originalAuthority`，与 file_list_view 中的 `originalAuthority` 路径匹配，故无孤儿文件。这验证了修复后 `equal_authorities` 确实生效。
5. **负向验证（不传 equal_authorities）**：再次调用但不传 `equal_authorities`，断言抛出 `ValidationException`，消息以 `"Conflicting authorities/schemes: [(localhost, null)]."` 结尾。这验证了未提供等价映射时，authority 差异会被正确识别为冲突（说明修复后冲突检测逻辑恢复正常）。
6. **清理**：测试末尾 `DROP TABLE`，因为 `afterEach` 的 purge 会因 `localhost` 这个无效 authority 失败，需提前手动 drop。

## 小结

- **成效**：修复了 `RemoveOrphanFilesProcedure` 中 `equal_authorities` 参数因复制粘贴错误被写入 `equalSchemes` 的 bug。修复后 `equal_authorities` 参数真正生效，用户可在 authority 不一致场景（如 HA nameservice、location 与文件清单 authority 不同）下正确执行孤儿文件清理；同时消除了对 `equalSchemes` 的污染。新增的测试同时覆盖了"传参生效"与"不传参报冲突"的正反两面场景，验证充分。
- **影响范围**：仅影响 Spark 3.5 模块的 `RemoveOrphanFilesProcedure`（`system.remove_orphan_files` 存储过程）。影响所有通过该过程清理孤儿文件且使用 `equal_authorities` 参数的用户。修复前这些用户的 authority 等价配置完全不生效；修复后恢复正常。其他 Spark 版本模块（3.3、3.4）不受影响（需确认它们是否有同样的复制粘贴 bug，若有应同步修复）。
- **回迁注意事项**：
  1. 此提交针对 Spark 3.5 模块（`spark/v3.5/`），回迁到 1.4.x 时需确认 1.4.x 的 Spark 3.5 模块存在同样的 bug（很可能存在，因为该 typo 在更早版本就引入）。
  2. 修复是单行改动，cherry-pick 冲突风险极低。
  3. 新增测试依赖 `ExtensionsTestBase`、`Spark3Util.loadIcebergTable`、`ReachableFileUtil`、`TestHelpers.dataManifests`、`FilePathLastModifiedRecord` 等测试基础设施，回迁时需确认 1.4.x 的 Spark 3.5 测试模块具备这些依赖。
  4. 测试中根据 `catalogName.equals("testhadoop")` 分支建表，回迁时需确认 1.4.x 测试环境的 catalog 名称约定一致。
  5. 若 1.4.x 分支的 Spark 3.3/3.4 模块存在完全相同的复制粘贴 bug（同一份代码模板复制），建议一并检查修复，避免遗漏。
