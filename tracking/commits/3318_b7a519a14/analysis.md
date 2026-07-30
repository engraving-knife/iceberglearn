# 提交 3318：JDBC: JDBC Catalog should handle Postgres exception for duplicate keys (#15434)

## 提交信息

- **序号**：3318 / 4088
- **哈希**：b7a519a149961672274c3954afeddb717477d853
- **短哈希**：b7a519a14
- **日期**：2026-02-26
- **作者**：Han You
- **提交说明**：JDBC: JDBC Catalog should handle Postgres exception for duplicate keys (#15434)
- **PR/Issue**：#15434

## 总体目的

Iceberg 的 JdbcCatalog 把表/视图的元数据存储在一张 JDBC 表中，表名或视图名作为主键唯一。当并发创建同名表、或提交时发生主键冲突时，底层数据库会抛出唯一约束违例异常，JdbcCatalog 需要把它识别并转换成语义更明确的 `AlreadyExistsException`（创建场景）或带"already exists"提示的 `UncheckedSQLException`（更新已有表时发生冲突的场景）。

问题在于，原先的识别逻辑只覆盖两种情况：一是 JDBC 标准的 `SQLIntegrityConstraintViolationException`，二是 SQLite 特有的消息 `"constraint failed"`。但 Postgres 在发生唯一约束违例时并不会抛 `SQLIntegrityConstraintViolationException`，而是抛普通的 `SQLException` 并把 SQLState 设为 `23505`（unique_violation）。结果是：当用户使用 Postgres 作为 JdbcCatalog 的后端、触发主键冲突时，原代码无法识别为约束违例，既不会抛 `AlreadyExistsException`，提示消息也丢失了"already exists"语义，退化成含糊的"Unknown failure"，且并发提交时该报 `AlreadyExistsException` 的场景报错类型也不正确。

本提交通过在 `JdbcUtil` 中新增统一的 `isConstraintViolation(SQLException)` 判定方法，把 `SQLIntegrityConstraintViolationException`、Postgres SQLState `23505`、SQLite 消息 `constraint failed` 三种来源一并识别，并统一应用到 table/view 的 rename 与 commit 路径，修复了 Postgres 后端的约束违例识别缺失，并消除了原先各处重复且不一致的判定逻辑。

## 如何达成设计目的

设计上把分散在三处（`JdbcCatalog.renameTable`/`renameView`、`JdbcTableOperations`、`JdbcViewOperations`）的约束违例判定收敛为对 `JdbcUtil.isConstraintViolation(...)` 的单一调用。该方法以"短路或"依次检查异常类型、SQLState、消息文本，覆盖三大主流后端（Postgres、SQLite、其他遵循 JDBC 标准的库）。同时在 commit 路径中合并原先分散的 `SQLIntegrityConstraintViolationException` 专用 catch 块与通用 `SQLException` catch 块中的约束检查，使"创建 vs 更新"两类场景下的异常类型选择逻辑只保留一处。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcUtil.java` (+8/-0 lines)

**修改目的**：新增统一的约束违例判定方法。

**工作逻辑**：
新增常量 `POSTGRES_UNIQUE_VIOLATION_SQLSTATE = "23505"` 与静态方法 `isConstraintViolation(SQLException ex)`。该方法返回 `ex instanceof SQLIntegrityConstraintViolationException || "23505".equals(ex.getSQLState()) || (ex.getMessage() != null && ex.getMessage().contains("constraint failed"))`。三个条件分别对应：JDBC 标准约束违例异常、Postgres 的 unique_violation SQLState、SQLite 的文本消息，从而以一处实现覆盖多种后端。这里把原先 `JdbcUtil` 中对 `SQLIntegrityConstraintViolationException` 的 import 移到此处集中管理。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java` (+4/-7 lines)

**修改目的**：在 renameTable/renameView 中复用统一判定。

**工作逻辑**：
移除 `SQLIntegrityConstraintViolationException` 的 import，并在 `renameTable` 与 `renameView` 的错误回调中把原先 `err instanceof SQLIntegrityConstraintViolationException || (msg != null && msg.contains("constraint failed"))` 的内联判定替换为 `JdbcUtil.isConstraintViolation(err)`，命中则抛 `AlreadyExistsException`。同时移除了因判定分支减少而不再需要的 `@SuppressWarnings("checkstyle:CyclomaticComplexity")`。rename 场景下目标名已存在即视为冲突，故统一抛 `AlreadyExistsException`。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcTableOperations.java` (+7/-12 lines)

**修改目的**：合并并修正表提交路径的约束违例处理。

**工作逻辑**：
原先有一个独立的 `catch (SQLIntegrityConstraintViolationException e)` 块，依据 `currentMetadataLocation()` 是否为 null 分别抛 `AlreadyExistsException`（创建）或 `UncheckedSQLException`（更新已有表）；通用 `catch (SQLException e)` 块里又只针对 `"constraint failed"` 消息抛 `AlreadyExistsException`，两条路径不统一。修改后删除独立的 `SQLIntegrityConstraintViolationException` catch 块，把判定全部并入通用 `SQLException` 块：调用 `JdbcUtil.isConstraintViolation(e)`，若命中则按 `currentMetadataLocation() == null` 区分创建/更新分别抛 `AlreadyExistsException` 或 `UncheckedSQLException`，二者均带上原异常 `e` 与"Table already exists"消息；否则抛 `UncheckedSQLException("Unknown failure")`。这使 Postgres 的 `23505` 也能走入此分支，并修正了 SQLite 路径原先只抛 `AlreadyExistsException` 而不区分创建/更新的问题。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcViewOperations.java` (+7/-12 lines)

**修改目的**：对视图提交路径做与表一致的约束违例处理。

**工作逻辑**：
改动与 `JdbcTableOperations` 完全对称：删除独立的 `SQLIntegrityConstraintViolationException` catch 块，在通用 `SQLException` 块内用 `JdbcUtil.isConstraintViolation(e)` 判定，按 `currentMetadataLocation()` 是否为 null 区分抛 `AlreadyExistsException`（创建视图已存在）或 `UncheckedSQLException`（更新视图冲突），消息为"View already exists"。统一了 Postgres/SQLite/标准 JDBC 三类来源的识别。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcUtil.java` (+24/-0 lines)

**修改目的**：为 `isConstraintViolation` 增加单元测试。

**工作逻辑**：
新增四个测试：分别验证 `SQLIntegrityConstraintViolationException`、SQLState 为 `23505` 的 Postgres 异常、消息含 `constraint failed` 的 SQLite 异常均判定为 `true`，而普通 `SQLException` 判定为 `false`，锁定三条识别路径与一个负样本。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java` (+67/-2 lines)

**修改目的**：验证表提交/创建路径在 Postgres 与 SQLite 异常下的行为，并修正既有断言。

**工作逻辑**：
在既有 mock 测试中补充 `mockedStatic.when(() -> JdbcUtil.isConstraintViolation(any())).thenCallRealMethod()`，使被 mock 的 `JdbcUtil` 类仍能调用真实判定方法。其中一个既有用例的断言由 `AlreadyExistsException` 改为 `UncheckedSQLException`——因为该用例模拟的是"已存在表再次提交"场景（`currentMetadataLocation()` 非 null），按新统一逻辑应抛 `UncheckedSQLException`。新增 `testCommitExceptionWithPostgresUniqueViolation`（更新场景抛 `23505` -> `UncheckedSQLException`）、`testCreateTableConstraintExceptionWithMessage`（创建场景抛 `constraint failed` -> `AlreadyExistsException`）、`testCreateTableConstraintExceptionWithPostgresUniqueViolation`（创建场景抛 `23505` -> `AlreadyExistsException`），覆盖创建/更新 × Postgres/SQLite 四种组合。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcViewCatalog.java` (+88/-2 lines)

**修改目的**：对视图提交/创建路径做与表一致的测试覆盖。

**工作逻辑**：
与表的测试对称：既有用例断言由 `AlreadyExistsException` 改为 `UncheckedSQLException`（更新场景），并新增 `testCommitExceptionWithPostgresUniqueViolation`、`testCreateViewConstraintExceptionWithMessage`、`testCreateViewConstraintExceptionWithPostgresUniqueViolation` 三个用例，分别验证视图更新冲突与创建冲突在 Postgres `23505` / SQLite 消息下的正确异常类型与消息前缀。

## 总结

本提交通过抽取统一的 `JdbcUtil.isConstraintViolation` 方法，修复了 JdbcCatalog 在 Postgres 后端下无法识别唯一约束违例的缺陷，同时收敛了 table/view 的 rename 与 commit 路径中三处重复且不一致的判定逻辑，并统一了创建与更新场景下的异常类型语义。配套的单测与端到端 mock 测试覆盖了三大后端来源与创建/更新两类场景，显著提升了 JdbcCatalog 在多数据库后端下的健壮性与错误语义准确性。
