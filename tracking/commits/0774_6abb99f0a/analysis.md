# 提交 0774：Spark 3.4, 3.3: Fix the setting of equalAuthorities in RemoveOrphanFilesProcedure (#10342)

## 提交信息

- **序号**：0774 / 4088
- **哈希**：6abb99f0a72e27165131cf73b6e0ff587a8759c5
- **短哈希**：6abb99f0a
- **日期**：2024-05-17 15:30:28 +0800
- **作者**：dongwang
- **提交说明**：Spark 3.4, 3.3: Fix the setting of equalAuthorities in RemoveOrphanFilesProcedure (#10342)
- **PR/Issue**：#10342

## 总体目的

这个提交修复了 Spark 3.3 与 3.4 模块中 `RemoveOrphanFilesProcedure`（清理孤儿文件存储过程）的一个低级但影响明确的 bug：用户通过 `equal_authorities` 参数传入"等价 authority 映射"时，这些映射被错误地写入了 `equalSchemes`（等价 scheme 映射）而不是 `equalAuthorities`，导致 `equal_authorities` 功能完全失效。当表的存储路径与文件清单（file list view）中文件路径的 authority 部分（即 URI 中的 host:port）不同但语义等价时，用户期望通过 `equal_authorities` 告知系统二者等价以避免误判孤儿文件，但因该 bug 映射被存错位置，系统仍会报"authorities 冲突"错误。

## 如何达成设计目的

### Bug 成因

`RemoveOrphanFilesProcedure` 是 Iceberg Spark 扩展提供的一个系统存储过程，用于清理表中不再被引用的"孤儿"数据文件。在执行清理前，过程需要将表元数据中记录的文件路径与用户提供的"文件清单"（通过 `file_list_view` 参数指定的 Spark 视图）进行比对。由于文件路径是 URI，可能存在 scheme（协议，如 `s3a`/`s3`）或 authority（主机:端口）不同但实际指向同一文件的情况，因此过程提供了 `equal_schemes` 和 `equal_authorities` 两个可选参数，让用户声明等价关系。

处理这两个参数的代码结构类似，都是从用户传入的 Spark map 中迭代键值对，分别填入 `equalSchemes` 和 `equalAuthorities` 两个本地 `Map<String, String>`。问题出在 `equal_authorities` 的处理分支中：

```java
// 处理 equal_authorities 参数的 lambda
(k, v) -> {
  equalSchemes.put(k.toString(), v.toString());  // BUG: 应为 equalAuthorities
  return BoxedUnit.UNIT;
}
```

可以看到，在处理 `equal_authorities` 的 lambda 中，put 的目标 map 写成了 `equalSchemes` 而非 `equalAuthorities`。这很可能是从 `equal_schemes` 的处理代码复制粘贴后遗漏修改导致的。结果是：用户传入的 authority 等价映射全部被塞进了 `equalSchemes`，而 `equalAuthorities` 始终为空。后续路径比对逻辑在发现 authority 不一致时，因 `equalAuthorities` 为空无法识别等价关系，于是抛出 `ValidationException`（"Conflicting authorities/schemes: ..."），`equal_authorities` 参数形同虚设。

### 修复逻辑

修复极其简洁：将 `equal_authorities` 处理分支中的 `equalSchemes.put(...)` 改为 `equalAuthorities.put(...)`，使映射写入正确的 map：

```java
(k, v) -> {
  equalAuthorities.put(k.toString(), v.toString());  // FIXED
  return BoxedUnit.UNIT;
}
```

修复同时覆盖 Spark 3.3 与 3.4 两个模块的 `RemoveOrphanFilesProcedure.java`（两个文件中的 bug 完全相同）。

### 测试验证

为防止回归，在 Spark 3.3 与 3.4 的 `TestRemoveOrphanFilesProcedure` 中各新增 `testRemoveOrphanFilesProcedureWithEqualAuthorities` 测试。测试构造了一个典型场景：

1. 建表并获取表路径 `originalPath`，记录其原始 authority。
2. 构造两个数据文件，路径使用与原路径相同 scheme 但 authority 为 `"localhost"` 的 `newParentPath`（即文件路径的 authority 与表路径的 authority 不同）。
3. 将这两个文件 append 到表中。
4. 构造文件清单视图 `files_view`，其中文件路径使用原始 authority（即与表元数据中文件路径的 authority `localhost` 不同）。
5. 调用 `remove_orphan_files` 并传入 `equal_authorities => map('localhost', '<原始authority>')`，期望系统识别二者等价，返回 0 个孤儿文件（`Assert.assertEquals(0, orphanFiles.size())`）。修复前此处会因映射写错而抛异常或误报。
6. 反向验证：不传 `equal_authorities` 再次调用，期望抛出 `ValidationException`，消息以 `"Conflicting authorities/schemes: [(localhost, null)]."` 结尾，确认在不声明等价时确实会冲突。
7. 测试末尾主动 `DROP TABLE`，因为 afterEach 中的 purge 会因 `localhost` authority 无效而失败。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/RemoveOrphanFilesProcedure.java`（及 v3.3 同名文件）

**修改目的**：修正 `equal_authorities` 参数处理分支中错误的目标 map。

**工作逻辑**：在处理 `equal_authorities` 参数的 `foreach` lambda 中，将 `equalSchemes.put(k.toString(), v.toString())` 改为 `equalAuthorities.put(k.toString(), v.toString())`。改动仅 1 行，但修复了整个 `equal_authorities` 功能。

**diff 摘要（单文件）**：2 files changed（含 v3.3/v3.4），每文件 1 insertion(+), 1 deletion(-)。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java`（及 v3.3 同名文件）

**修改目的**：新增端到端测试，验证 `equal_authorities` 功能正常工作，并防止回归。

**工作逻辑**：新增 `testRemoveOrphanFilesProcedureWithEqualAuthorities` 测试方法（约 83 行）。测试逻辑如上"测试验证"所述，覆盖正向（传 equal_authorities 后无冲突）与反向（不传时报冲突）两种场景。两个 Spark 版本的测试代码完全一致。

**diff 摘要**：4 files changed, 168 insertions(+), 2 deletions(-)（含 v3.3/v3.4 的源码与测试）。

## 小结

### 成效

修复了 `RemoveOrphanFilesProcedure` 中 `equal_authorities` 参数完全失效的 bug。修复后，当表文件路径与文件清单中路径的 authority 不同但语义等价时（常见于跨集群、跨 endpoint 的 S3/HDFS 路径），用户可通过 `equal_authorities` 正确声明等价关系，使孤儿文件清理流程正常执行，不再误报冲突。该修复影响 Spark 3.3 与 3.4 两个版本。

### 影响范围

改动局限于 `RemoveOrphanFilesProcedure` 的 `equal_authorities` 处理分支，不影响其他参数（`equal_schemes`、`file_list_view` 等）的处理逻辑，也不影响不使用 `equal_authorities` 时的现有行为。对使用 `remove_orphan_files` 存储过程且涉及跨 authority 路径的用户而言，这是一个关键修复。

### 回迁注意事项

- 回迁时需同时覆盖 Spark 3.3 与 3.4 两个模块。若 1.4.x 分支还支持其他 Spark 版本（如 3.5），应检查对应版本的 `RemoveOrphanFilesProcedure` 是否存在相同 bug（很可能是同样的复制粘贴问题），需一并修复。
- 该 bug 是 1 字符级修复，cherry-pick 冲突风险极低。但新增的测试方法约 83 行，若 1.4.x 分支的 `TestRemoveOrphanFilesProcedure` 已有其他改动，可能需手动调整测试插入位置。
- 注意测试中使用了 `Assertions.assertThatThrownBy`（AssertJ）与 `Assert.assertEquals`（JUnit）混用，回迁时确认 1.4.x 分支的测试依赖中 AssertJ 可用。
- 测试末尾的 `sql("DROP TABLE %s", tableName)` 是必要的，因为测试构造的 `localhost` authority 会导致 afterEach 的 purge 失败。回迁时需保留该行，否则测试在清理阶段报错。
