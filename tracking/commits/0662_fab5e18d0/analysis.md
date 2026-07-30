# 提交 0662：Hive, JDBC: Avoid NPE on Throwables without error msg

## 提交信息
- **序号**：0662 / 4088
- **哈希**：fab5e18d0db3b6a05ada5e4c2fb9728e21bca35f
- **短哈希**：fab5e18d0
- **日期**：2024-04-05
- **作者**：Naveen Kumar
- **提交说明**：Hive, JDBC: Avoid NPE on Throwables without error msg (#10082)
- **PR/Issue**：PR #10082

## 总体目的

本提交修复 JDBC Catalog 和 Hive Metastore 客户端在处理"没有错误消息"的异常时发生的空指针异常（NPE）问题。

在 JDBC Catalog 的 `JdbcTableOperations` 和 `JdbcViewOperations` 中，捕获 `SQLException` 后会调用 `e.getMessage().contains("constraint failed")` 来判断是否为约束冲突（用于区分"表已存在"等场景）。但 `SQLException.getMessage()` 可能返回 `null`（当异常没有错误消息时），此时调用 `.contains()` 会抛出 `NullPointerException`，导致原本应该被优雅处理的数据库异常变成了未预期的 NPE，掩盖了真实的错误原因。

类似地，在 Hive Metastore 的 `HiveClientPool` 中，捕获 `Throwable` 后会调用 `t.getMessage().contains("Another instance of Derby may have already booted")` 来判断是否为 Derby 嵌入式数据库已被其他实例占用的场景。同样，`Throwable.getMessage()` 可能为 `null`，会触发 NPE。

这个问题的危害在于：当底层抛出无消息的异常时，本应给出友好错误信息或正确分类异常的代码路径，反而因为 NPE 而崩溃，给用户带来困惑，也使得错误诊断更加困难。本提交通过在调用 `.contains()` 之前增加 null 检查来彻底消除这一类 NPE。

## 如何达成设计目的

提交采用统一的防御性编程模式：在所有调用 `getMessage().contains(...)` 的地方，前置一个 `getMessage() != null` 的判空检查。

采用短路求值（`&&`）确保当 `getMessage()` 为 null 时直接跳过 `.contains()` 调用，整体表达式结果为 `false`，从而进入异常的默认处理分支（抛出 `UncheckedSQLException` 或 `RuntimeMetaException` 等通用异常），而非因 NPE 中断。

同时为每个修复点补充了对应的单元测试，验证三种场景：有消息且匹配、有消息但不匹配、无消息。通过 Mockito 的 `MockedStatic` 机制模拟静态方法抛出指定异常，确保修复在各种异常形态下都按预期工作。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcTableOperations.java`
**修改目的**：避免在 `SQLException` 无消息时发生 NPE。
**工作逻辑**：在 `catch (SQLException e)` 块中，原代码 `e.getMessage().contains("constraint failed")` 改为 `e.getMessage() != null && e.getMessage().contains("constraint failed")`。当消息为 null 时短路返回 false，跳过"表已存在"的判断，进入后续通用异常处理（抛出 `UncheckedSQLException`，消息为 "Unknown failure"）。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcViewOperations.java`
**修改目的**：与 `JdbcTableOperations` 相同的修复，针对 View 场景。
**工作逻辑**：同样的 null 检查模式应用于 View 提交路径，避免"View already exists"判断时发生 NPE。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java`
**修改目的**：为 JDBC 表操作的 NPE 修复添加测试覆盖。
**工作逻辑**：新增两个测试 `testCommitExceptionWithoutMessage` 和 `testCommitExceptionWithMessage`。前者用 `MockedStatic<JdbcUtil>` 模拟 `JdbcUtil.loadTable` 抛出无消息的 `SQLException`，断言提交时抛出 `UncheckedSQLException` 且消息以 "Unknown failure" 开头；后者模拟抛出消息为 "constraint failed" 的 `SQLException`，断言抛出 `AlreadyExistsException` 且消息以 "Table already exists" 开头。两个测试共同验证了 null 检查既能正确处理无消息场景，又不破坏有消息场景的分类逻辑。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcViewCatalog.java`
**修改目的**：为 JDBC View 操作的 NPE 修复添加测试覆盖。
**工作逻辑**：新增与表测试对称的两个测试 `testCommitExceptionWithoutMessage` 和 `testCommitExceptionWithMessage`，针对 View 场景验证相同的行为：无消息时抛出 `UncheckedSQLException`，有 "constraint failed" 消息时抛出 `AlreadyExistsException`（消息以 "View already exists" 开头）。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveClientPool.java`
**修改目的**：避免在 `Throwable` 无消息时发生 NPE。
**工作逻辑**：在 `catch (Throwable t)` 块中，原代码 `t.getMessage().contains("Another instance of Derby may have already booted")` 改为 `t.getMessage() != null && t.getMessage().contains(...)`。当消息为 null 时短路返回 false，不进入"Derby 已被占用"的专用错误处理，而是走通用的 `RuntimeMetaException` 路径。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveClientPool.java`
**修改目的**：为 Hive 客户端池的 NPE 修复添加测试覆盖。
**工作逻辑**：新增测试 `testExceptionMessages`，通过 `MockedStatic<MetaStoreUtils>` 模拟 `MetaStoreUtils.newInstance` 抛出四种不同形态的异常：(1) 包裹 `MetaException`（带消息）的 `RuntimeException`；(2) 包裹 `MetaException`（无消息）的 `RuntimeException`；(3) 无消息无 cause 的 `RuntimeException`；(4) 消息为 "Another instance of Derby may have already booted" 的 `RuntimeException`。前三种验证不会 NPE 且统一抛出 "Failed to connect to Hive Metastore"，第四种验证仍能正确识别 Derby 占用场景。

## 小结
- **成效**：成功消除了 JDBC 和 Hive 模块中因异常无消息导致的 NPE，并保留了对"约束冲突"和"Derby 占用"等特定场景的正确识别能力。
- **影响范围**：影响 `iceberg-core` 的 JDBC Catalog（表与视图操作）和 `iceberg-hive-metastore` 的客户端池。属于健壮性修复，不改变正常路径行为。
- **回迁到 1.4.x 的注意事项**：可直接回迁。修复点都是单行 null 检查，依赖少。需注意测试用到的 Mockito `MockedStatic` 在 1.4.x 分支的 Mockito 版本下是否可用（通常需要 Mockito 3.4+）。若 1.4.x 的 Mockito 版本较旧，测试部分可能需要调整，但主代码修复本身无依赖问题。
