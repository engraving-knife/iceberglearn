# 提交 0407：Spark 3.4, 3.5: Enable drop table with purge in tests

## 提交信息

- **序号**：0407
- **哈希**：3d66e9dd5cc9473447ad3416aeb2c5957992adea
- **短哈希**：3d66e9dd5
- **日期**：2024 年 1 月 24 日（Wed Jan 24 15:20:15 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.4, 3.5: Enable drop table with purge in tests (#9548)
- **PR/Issue**：#9548

## 总体目的

这个提交是为了"还原"被临时绕过的测试，让 Spark 3.4 / 3.5 下与 `DROP TABLE ... PURGE` 相关的测试重新启用，并把测试清理逻辑从"Iceberg catalog 直接 dropTable 的变通写法"切回"标准 SQL `DROP TABLE IF EXISTS ... PURGE`"的写法。背景是 Spark 此前存在一个缺陷 SPARK-43203，导致 `DROP TABLE ... PURGE` 在 Iceberg catalog 上无法正确工作，Iceberg 团队当时为了让 CI 不卡，采用了两条临时绕过手段：一是把 `@Test`/`@TestTemplate` 标注改成 `@Ignore`/`@Disabled` 跳过相关测试；二是在测试清理方法 `@After` 里不依赖 SQL 的 PURGE，而是直接调用 `validationCatalog.dropTable(tableIdent, true)` 来强制清表，再补一条不带 PURGE 的 `DROP TABLE`。

现在 SPARK-43203 已经在 Spark 侧修复，Iceberg 这边自然要把绕过代码回滚：恢复测试执行、恢复 SQL PURGE 写法。这样做的价值有三点：第一，恢复 purge 相关测试的覆盖，避免这类行为出现回归而无人察觉；第二，让测试代码与生产路径一致，真正验证 Spark SQL 的 `DROP TABLE ... PURGE` 能在 Iceberg catalog 上正常工作；第三，简化测试清理逻辑，从"两步式变通"回到"一句 SQL"，可读性更好。

## 如何达成设计目的

实现分两个层面、四个文件（Spark 3.4 与 3.5 各两个对称的测试类）。在 `TestRemoveOrphanFilesProcedure` 的 `@After removeTable()` 中，删除原有的 `validationCatalog.dropTable(tableIdent, true)` 调用以及不带 PURGE 的 `DROP TABLE`，改为单一的 `sql("DROP TABLE IF EXISTS %s PURGE", tableName)`。在 `TestDropTable` 中，把 `testPurgeTable` 和 `testPurgeTableGCDisabled` 上的 `@Ignore`（3.4，JUnit 4）或 `@Disabled`（3.5，JUnit 5）注解移除，并补回 `@Test` / `@TestTemplate` 让测试重新纳入执行集；同时移除了不再需要的 `Ignore`/`Disabled` 的 import。

## 修改详情

### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java

**修改目的**：把 orphan files 过程测试的表清理逻辑切回标准 SQL `DROP TABLE ... PURGE`。

**工作逻辑**：原 `@After removeTable()` 中因为 SPARK-43203 无法依赖 SQL PURGE，写了两步——先用 `validationCatalog.dropTable(tableIdent, true /* purge */)` 直接通过 Iceberg catalog 删表（purge=true 表示同时删除数据文件），再执行 `sql("DROP TABLE IF EXISTS %s", tableName)` 把 catalog 中的表元数据也清掉。本提交把这两步合并为一句 `sql("DROP TABLE IF EXISTS %s PURGE", tableName)`，与紧随其后的 `sql("DROP TABLE IF EXISTS p PURGE")` 写法保持一致。purge 语义由 SQL 层透传到 Iceberg catalog，省去了直接操作 validationCatalog 的特殊路径。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestDropTable.java

**修改目的**：恢复被 SPARK-43203 临时关闭的两个 purge 测试。

**工作逻辑**：原代码在 `testPurgeTable` 和 `testPurgeTableGCDisabled` 两个方法上加了 `// TODO: enable once SPARK-43203 is fixed` 注释和 `@Ignore` 注解，使它们在 JUnit 4 下被跳过。本提交删除 `@Ignore` 注解和 TODO 注释，并补上 `@Test` 注解，让两个测试重新执行。同时删除了不再使用的 `import org.junit.Ignore;`。这两个测试分别验证：(1) `testPurgeTable` 验证对启用 GC 的表执行 PURGE 能删除所有数据文件；(2) `testPurgeTableGCDisabled` 验证当 `gc.enabled=false` 时 PURGE 的行为（仍执行 drop，但不应物理删除文件）。

### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoveOrphanFilesProcedure.java

**修改目的**：与 3.4 版本对称，把 Spark 3.5 下 orphan files 测试的表清理逻辑切回标准 SQL `DROP TABLE ... PURGE`。

**工作逻辑**：内容与 3.4 版本完全一致——删除 `validationCatalog.dropTable(tableIdent, true)` 调用和不带 PURGE 的 `DROP TABLE`，合并为 `sql("DROP TABLE IF EXISTS %s PURGE", tableName)`。Spark 3.5 与 3.4 在该测试类上共用相同的测试代码骨架，因此改动是镜像式的。

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestDropTable.java

**修改目的**：恢复 Spark 3.5 下被 SPARK-43203 临时关闭的两个 purge 测试。

**工作逻辑**：与 3.4 版本对称，但使用 JUnit 5 的注解体系。原代码用 `@Disabled`（JUnit 5 的 `@org.junit.jupiter.api.Disabled`）跳过 `testPurgeTable` 和 `testPurgeTableGCDisabled`，并把它们标注为 `@TestTemplate`（JUnit 5 下配合 `@ParameterizedTest` 风格的多 catalog 测试基类使用）。本提交删除 `@Disabled` 注解和 TODO 注释，恢复 `@TestTemplate` 注解让测试重新执行，同时删除不再使用的 `import org.junit.jupiter.api.Disabled;`。两个测试的语义与 3.4 版本一致，只是测试框架注解不同。

## 小结

这是一个"解除临时绕过"的测试恢复提交，典型模式是：上游（Spark SPARK-43203）修复缺陷后，下游（Iceberg）回滚此前为绕过缺陷而写的变通代码。改动覆盖 Spark 3.4 与 3.5 两个版本、四个测试文件，核心动作是删除 `@Ignore`/`@Disabled` 跳过注解、把 `validationCatalog.dropTable(...)` 变通写法换回标准 `DROP TABLE IF EXISTS ... PURGE` SQL。意义在于恢复 purge 行为的测试覆盖、让测试与生产 SQL 路径一致、并简化测试清理代码。改动不触及生产代码，风险集中在测试侧，且因 SPARK-43203 已修复而具备明确的回滚依据。
