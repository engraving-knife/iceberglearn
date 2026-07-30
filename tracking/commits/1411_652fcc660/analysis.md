# 提交 1411：Spark 3.5: Fix flaky TestRemoveOrphanFilesAction3 (#11616)

## 提交信息

- **序号**：1411 / 4088
- **哈希**：652fcc66073f996af5515eca8a094886c2fd1312
- **短哈希**：652fcc660
- **日期**：2024-11-21（Thu Nov 21 23:14:23 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.5: Fix flaky TestRemoveOrphanFilesAction3 (#11616)
- **PR/Issue**：#11616

## 总体目的

`TestRemoveOrphanFilesAction3` 是 Spark 3.5 模块下用于验证 remove_orphan_files 行为的测试类（覆盖 `mycat` / `hadoop` / `hive` / session catalog 等多种 catalog）。它继承自 `TestRemoveOrphanFilesAction`。父类中的 `testHiveCatalogTable` 也涉及类似命名。

这些测试在创建表与「trash file」（用于验证孤儿文件清理的伪孤儿文件）时，原本用：

```java
ThreadLocalRandom.current().nextInt(1000)
```

生成随机后缀，例如 `Identifier.of(database, "table" + ThreadLocalRandom.current().nextInt(1000))` 与 `"/data/trashfile" + ThreadLocalRandom.current().nextInt(1000)`。

问题：随机空间只有 0–999 共 1000 个值，碰撞概率不可忽略。

- 跨测试用例并行执行时，多个用例可能取到相同后缀 → 表名冲突（`createTable` 失败）或 trash file 路径冲突（`createNewFile` 已存在或被其它用例误删）。
- 跨测试运行（同一临时目录未清理时）也可能撞名。
- 现象表现为偶发的 `Table already exists`、`file already exists`、孤儿文件清理结果与期望不符等，使 `TestRemoveOrphanFilesAction3` 成为 flaky test。

本提交的目的是：用基于 UUID 的全局唯一后缀替代 `ThreadLocalRandom.nextInt(1000)`，消除命名碰撞，让测试稳定通过。

## 如何达成设计目的

在父类 `TestRemoveOrphanFilesAction` 中新增 `protected` 辅助方法：

```java
protected String randomName(String prefix) {
  return prefix + UUID.randomUUID().toString().replace("-", "");
}
```

UUID 的随机空间远大于 1000，碰撞概率可视为 0；去掉 `-` 是为了让结果可安全用于表名/文件名片段（不含分隔符）。

然后把父类 `testHiveCatalogTable` 与子类 `TestRemoveOrphanFilesAction3` 中所有 `prefix + ThreadLocalRandom.current().nextInt(1000)` 形式的调用替换为 `randomName(prefix)`：

- `testHiveCatalogTable`：表名 `hivetestorphan<random>` → `randomName("hivetestorphan")`；
- `TestRemoveOrphanFilesAction3` 的 5 个测试方法（`testSparkCatalogTable` / `testHadoopCatalogTable` / `testHiveCatalogTable` / `testSparkSessionCatalogTable` / `testSparkSessionCatalogDelete`）：
  - 表名：`Identifier.of(database, "table" + random)` → `randomName("table")`；
  - trash file 路径：`"/data/trashfile" + random` → `randomName("/data/trashfile")`。

同时移除不再需要的 `import java.util.concurrent.ThreadLocalRandom;`，并新增 `import java.util.UUID;`。

由于 `randomName` 在父类以 `protected` 暴露，子类可直接复用，无需重复实现。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java`

**修改目的**：提供 UUID-based 的随机命名工具方法，并修复父类自身的同名 flaky 问题。

**工作逻辑**：

1. import 调整：
   - 新增 `import java.util.UUID;`；
   - 移除 `import java.util.concurrent.ThreadLocalRandom;`。
2. `testHiveCatalogTable` 中：
   ```java
   // 修改前
   TableIdentifier identifier =
       TableIdentifier.of("default", "hivetestorphan" + ThreadLocalRandom.current().nextInt(1000));
   // 修改后
   TableIdentifier identifier = TableIdentifier.of("default", randomName("hivetestorphan"));
   ```
3. 新增 protected 工具方法（放在 `executeTest` 之前）：
   ```java
   protected String randomName(String prefix) {
     return prefix + UUID.randomUUID().toString().replace("-", "");
   }
   ```

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction3.java`

**修改目的**：让 5 个 catalog 测试用例使用 UUID 后缀，避免碰撞导致的 flaky。

**工作逻辑**：

1. import 调整：移除 `import java.util.concurrent.ThreadLocalRandom;`。
2. 在 5 个测试方法中，把 4 处表名生成与 5 处 trash file 路径生成都改为调用继承自父类的 `randomName(...)`：
   - `testSparkCatalogTable`（mycat）：表名 `randomName("table")`、trash `randomName("/data/trashfile")`；
   - `testHadoopCatalogTable`：同上；
   - `testHiveCatalogTable`：同上；
   - `testSparkSessionCatalogTable`：同上；
   - `testSparkSessionCatalogDelete`：trash 路径改 `randomName("/data/trashfile")`（此方法用固定表名 `sessioncattest`，故只改 trash 一处）。

具体替换模式：

```java
// 修改前
Identifier id = Identifier.of(database, "table" + ThreadLocalRandom.current().nextInt(1000));
String trashFile = "/data/trashfile" + ThreadLocalRandom.current().nextInt(1000);

// 修改后
Identifier id = Identifier.of(database, randomName("table"));
String trashFile = randomName("/data/trashfile");
```

注意 `randomName("/data/trashfile")` 会生成形如 `/data/trashfile<uuid>` 的路径，前导 `/data/` 部分作为 prefix 传入，UUID 拼在 `trashfile` 之后，整体仍是合法的相对路径片段，与 `new File(location + trashFile).createNewFile()` 的拼接逻辑兼容。

## 小结

- **成效**：`TestRemoveOrphanFilesAction3` 与 `testHiveCatalogTable` 不再因 `ThreadLocalRandom.nextInt(1000)` 的有限随机空间发生命名碰撞，消除了「表已存在 / 文件已存在 / 孤儿清理结果偶发不匹配」等 flaky 失败。UUID 后缀让跨用例与跨运行的命名冲突概率降到可忽略。
- **影响范围**：仅 Spark 3.5 测试代码：父类 `TestRemoveOrphanFilesAction` 新增 1 个工具方法 + 1 处调用替换 + import 调整，子类 `TestRemoveOrphanFilesAction3` 替换 9 处调用 + import 调整。无产品代码变更。
- **回迁到 1.4.x 的注意事项**：这是纯测试稳定性修复，不影响 1.4.x 的产品行为。若 1.4.x 上 `TestRemoveOrphanFilesAction3` 同样出现 flaky，建议回迁以提升 CI 稳定性。回迁要点：
  1. 父类 `TestRemoveOrphanFilesAction` 在 1.4.x 中若结构一致，直接新增 `randomName(String)` protected 方法即可；
  2. 子类 5 个用例的替换模式与 main 一致，可整体套用；
  3. 确认 1.4.x 测试 JDK 版本支持 `UUID.randomUUID()`（基本所有 JDK 都支持）；
  4. 无依赖其他改动，可独立回迁；如 1.4.x 的 `TestRemoveOrphanFilesAction` 父类与 Spark 3.3/3.4 共享，回迁时这些版本的子类也可一并受益（同样替换为 `randomName`）。
