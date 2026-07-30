# 提交 0374：Hive: Unwrap RuntimeException for Hive TException with alter table

## 提交信息

- **序号**：0374
- **哈希**：1da80552c06e749f2b6103f0ab0a184bb77a841c
- **短哈希**：1da80552c
- **日期**：2024-01-17（作者日期 Wed Jan 17 15:44:21 2024 +0530，提交日期 Wed Jan 17 11:14:21 2024 +0100）
- **作者**：Naveen Kumar <nk1506@gmail.com>
- **提交说明**：Hive: Unwrap RuntimeException for Hive TException with alter table (#9432)
- **PR/Issue**：PR #9432，关联 issue #9289

## 总体目的

这个提交修复了 Iceberg HiveCatalog 在执行 `renameTable`（对应 Hive 的 `alter_table` 操作）时的一个异常处理缺陷。问题根源在于 Iceberg 通过 `MetastoreUtil.alterTable` 调用 Hive Metastore 的 `alter_table` 方法时使用了反射（`DynMethods`），而反射调用会把底层方法抛出的受检异常（包括 Hive 的 `TException` 子类如 `AlreadyExistsException`、`InvalidOperationException`）包装成 `RuntimeException` 重新抛出。这导致 `HiveCatalog.renameTable` 中的 `catch (AlreadyExistsException e)` 分支永远不会被命中——因为到达 `HiveCatalog` 时异常已经被包成了 `RuntimeException`，类型不再是 `AlreadyExistsException`。

具体表现是：当用户尝试把表 rename 到一个已存在的目标表名时，Hive Metastore 实际抛出的是 `InvalidOperationException`（消息形如 "new table <to> already exists"），但由于反射包装的缘故，外层 `HiveCatalog` 既无法捕获 `AlreadyExistsException`（因为 Hive 抛的是 `InvalidOperationException`，不是这个类型），也无法捕获 `InvalidOperationException`（因为它被包成了 `RuntimeException`），最终落入 `catch (TException e)` 也失败（同样因为包装），最终抛给用户的是一个原始的 `RuntimeException`，而不是 Iceberg 期望的 `org.apache.iceberg.exceptions.AlreadyExistsException`。这违反了 Iceberg Catalog 抽象层 `CatalogTests.testRenameTableDestinationTableAlreadyExists` 规定的契约——所有 Catalog 实现在目标表已存在时都应抛出 `AlreadyExistsException`。

提交的目标是：(1) 在 `MetastoreUtil.alterTable` 中把反射包装的 `RuntimeException` 拆开，还原出底层的 `TException` 重新抛出；(2) 在 `HiveCatalog.renameTable` 中正确捕获 Hive 真正抛出的 `InvalidOperationException`，根据消息内容判断是否为"目标表已存在"场景，是则抛 Iceberg 的 `AlreadyExistsException`，否则作为通用失败抛 `RuntimeException`；(3) 移除 `TestHiveCatalog` 中针对该 bug 的临时覆盖测试，让标准 `CatalogTests` 的契约测试接管。这对应 issue #9289 的修复。

## 如何达成设计目的

实现路径分三步且环环相扣。第一步在 `MetastoreUtil.alterTable` 中给方法签名加上 `throws TException`，并把反射调用 `ALTER_TABLE.invoke(...)` 包在 try-catch 中：捕获 `RuntimeException`，若其 `getCause()` 是 `TException` 则拆包后作为 `TException` 重新抛出，否则原样抛出 `RuntimeException`。第二步在 `HiveCatalog.renameTable` 中把原来的 `catch (AlreadyExistsException e)` 改为 `catch (InvalidOperationException e)`，并在其中通过 `e.getMessage().contains("new table " + to + " already exists")` 判断是否为目标已存在场景，是则抛 Iceberg 的 `AlreadyExistsException`，否则抛通用 `RuntimeException`。第三步删除 `TestHiveCatalog` 中带有 TODO 注释（"should be removed after fix of #9289"）的 `testRenameTableDestinationTableAlreadyExists` 覆盖方法，使父类 `CatalogTests` 的同名标准测试生效——后者期望抛出 `AlreadyExistsException`，修复后正好满足。

## 修改详情

### hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java

**修改目的**：修正 `renameTable` 的异常捕获分支，正确识别 Hive 抛出的"目标表已存在"错误并转换为 Iceberg 的 `AlreadyExistsException`。

**工作逻辑**：原代码在 `renameTable` 中有 `catch (AlreadyExistsException e)` 分支，但 `AlreadyExistsException`（Hive metastore 的类）在 rename 场景下 Hive 实际不会抛出——Hive 在目标表已存在时抛的是 `InvalidOperationException`。再加上反射包装问题（见下个文件），这个 catch 分支实际上是死代码。修改后改为 `catch (InvalidOperationException e)`，进入分支后通过消息内容判断：若 `e.getMessage()` 包含 `"new table " + to + " already exists"`（Hive Metastore 在此场景下的标准消息格式），则抛 `org.apache.iceberg.exceptions.AlreadyExistsException("Table already exists: %s", to)`，与 Iceberg Catalog 契约一致；否则将该 `InvalidOperationException` 作为通用失败包装成 `RuntimeException` 抛出，避免错误地把其它类型的 `InvalidOperationException`（如权限问题等）误判为"目标已存在"。注意这里依赖 `MetastoreUtil.alterTable` 已经把 `TException` 拆包还原（`InvalidOperationException` 是 `TException` 的子类），否则这个 catch 分支同样无法命中。

### hive-metastore/src/main/java/org/apache/iceberg/hive/MetastoreUtil.java

**修改目的**：拆解反射调用包装出的 `RuntimeException`，还原底层 `TException`，让上层能按具体异常类型精确处理。

**工作逻辑**：`MetastoreUtil` 通过 `DynMethods.UnboundMethod ALTER_TABLE` 以反射方式调用 Hive Metastore 的 `alter_table` 方法（这种反射方式用于兼容不同 Hive 版本的方法签名）。新增 `org.apache.thrift.TException` 导入。两个 `alterTable` 重载方法（四参版和五参版）的签名都加上 `throws TException`。五参版本的实现中，原本直接 `ALTER_TABLE.invoke(client, databaseName, tblName, table, new EnvironmentContext(env))` 改为包在 try-catch 中：捕获 `RuntimeException`，若 `e.getCause() instanceof TException` 则把 cause 强转为 `TException` 重新抛出（这是关键——`DynMethods.invoke` 通过反射调用，受检异常会被包装），否则把原 `RuntimeException` 抛出（保留非 Thrift 异常的原有行为）。这样上层 `HiveCatalog.renameTable` 就能按 `TException` 的具体子类（如 `InvalidOperationException`、`NoSuchObjectException`）做精确捕获和转换。注释明确说明 "TException would be wrapped into RuntimeException during reflection"，让维护者理解为何需要这层拆包。

### hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java

**修改目的**：移除针对 #9289 bug 的临时覆盖测试，回归标准 Catalog 契约测试。

**工作逻辑**：删除 53 行代码，主要包括：移除 `HasTableOperations` 和 `org.assertj.core.api.Assertions` 两个不再使用的导入；删除整个 `testRenameTableDestinationTableAlreadyExists` 方法。该方法原本带有 `@Override` 和 TODO 注释 "This test should be removed after fix of https://github.com/apache/iceberg/issues/9289."，是 #9289 未修复时为了让 HiveCatalog 通过测试而写的临时覆盖——它把父类 `CatalogTests` 期望的 `AlreadyExistsException` 改成期望 `RuntimeException` 并校验消息包含 "new table newdb.table_renamed already exists"，本质上是把 bug 当作既定行为接受。修复后这个覆盖不再需要，删除后父类的标准测试接管，会断言 rename 到已存在的表抛出 `AlreadyExistsException`，正好验证修复正确。同时该方法还断言了源表和目标表在失败后都仍然存在且 UUID 不同（即 rename 失败不应破坏既有表），这部分语义现在隐含在父类测试中。

## 小结

这是一个典型的"异常处理链路修复"提交，体现了分布式系统集成的常见陷阱：反射调用会把受检异常包装成 RuntimeException，导致上层按类型的 catch 失效。修复模式很标准——在反射边界拆包还原底层异常，让上层能按具体异常类型精确处理。同时它还纠正了一个事实性错误：原代码假设 Hive 在目标表已存在时抛 `AlreadyExistsException`，但实际抛的是 `InvalidOperationException`，通过消息内容区分语义。删除带 TODO 的临时覆盖测试、回归标准 `CatalogTests` 契约测试是修复完成的标志——这把一个原本被容忍的 bug 行为（抛 RuntimeException）提升为符合 Iceberg Catalog 抽象契约的正确行为（抛 AlreadyExistsException），让所有 Catalog 实现的行为更一致。这种"修复 + 移除临时绕行 + 回归标准测试"的三联操作是高质量 bug 修复的范式。
