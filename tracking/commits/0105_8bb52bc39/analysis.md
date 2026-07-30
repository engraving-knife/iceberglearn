# 提交 0105：AWS: Remove AssertHelpers usage (#8937)

## 提交信息

- **序号**：0105 / 4088
- **哈希**：8bb52bc39ad48c2b0158990b60c2818702cbffa6
- **短哈希**：8bb52bc39
- **日期**：2023-10-30 14:10:40 +0100
- **作者**：Ashok
- **提交说明**：AWS: Remove AssertHelpers usage (#8937)
- **PR/Issue**：#8937

## 总体目的

本提交将 AWS 集成测试模块中对 Iceberg 内部工具类 `org.apache.iceberg.AssertHelpers` 的所有调用替换为标准的 [AssertJ](https://assertj.github.io/doc/) 断言 API（`Assertions.assertThatThrownBy(...)`），是 Iceberg 社区逐步淘汰 `AssertHelpers` 这一历史工具的整体计划在 AWS 模块上的落地。

`AssertHelpers` 是 Iceberg 早期为弥补 JUnit4 不足而自研的异常断言工具，提供 `assertThrows`、`assertThrowsCause`、`assertThrowsWithCause` 等方法，签名形如 `assertThrows(String message, Class<T> expected, String messageSubstring, Executable executable)`。随着 AssertJ 成为 Iceberg 测试栈的标准断言库，`AssertHelpers` 变得多余——AssertJ 的 `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)` 链式 API 表达力更强、错误信息更友好，且与社区生态一致。保留两套等价工具会增加维护成本、阻碍新贡献者理解测试写法，因此社区逐模块清理 `AssertHelpers` 用法，最终目标是将其从代码库移除。

本提交专门处理 AWS 集成测试模块（`aws/src/integration/...`）下的 8 个测试文件，共 +257/-310 行（净减 53 行），全部为机械式等价替换：把 `AssertHelpers.assertThrows("描述", XxxException.class, "子串", () -> ...)` 改写为 `Assertions.assertThatThrownBy(() -> ...).as("描述").isInstanceOf(XxxException.class).hasMessageContaining("子串")`；对断言异常 cause 的 `assertThrowsCause` 则改写为 `.cause().isInstanceOf(...).hasMessageContaining(...)`；对 `assertThrowsWithCause` 改写为先 `isInstanceOf` 外层异常、再 `.cause().isInstanceOf(...)` 校验内层 cause。

对 Iceberg 演进的意义在于：统一异常断言风格到业界标准的 AssertJ，降低自研工具的维护负担，为后续彻底删除 `AssertHelpers` 类扫清障碍，并使 AWS 集成测试的失败信息更清晰可读。

## 如何达成设计目的

设计思路是纯机械的 API 等价替换，不改变任何测试覆盖的业务逻辑或断言条件。整体改动结构为：

1. 在每个测试文件中删除 `import org.apache.iceberg.AssertHelpers;`，新增/确保存在 `import org.assertj.core.api.Assertions;`。
2. 逐处把 `AssertHelpers.assertThrows/assertThrowsCause/assertThrowsWithCause(...)` 改写为 `Assertions.assertThatThrownBy(...)` 链式调用，保留原描述文本（迁移到 `.as(...)`）、异常类型（迁移到 `.isInstanceOf(...)`）、消息子串（迁移到 `.hasMessageContaining(...)`）。
3. 对原 `AssertHelpers.assertThrows(null, XxxException.class, () -> ...)`（无描述、无消息子串）这种退化形式，改写为 `.isInstanceOf(XxxException.class).hasMessage(null)`。

由于 AssertJ 的 `assertThatThrownBy` 接受 `ThrowingCallable` lambda，与 `AssertHelpers` 接受的 `Executable` lambda 在调用形态上一致，替换后测试行为等价，但失败时 AssertJ 会输出实际抛出异常的完整类型与消息，定位更直观。

## 修改详情

### `aws/src/integration/java/org/apache/iceberg/aws/TestDefaultAwsClientFactory.java`

**修改目的**：将默认 AWS 客户端工厂测试中的 `AssertHelpers` 调用改为 AssertJ。

**工作逻辑**：4 处替换。包括 `AssertHelpers.assertThrowsCause(..., SdkClientException.class, "Unable to execute HTTP request: unknown", () -> glueClient.getDatabase(...))` 改为 `Assertions.assertThatThrownBy(() -> glueClient.getDatabase(...)).cause().isInstanceOf(SdkClientException.class).hasMessageContaining("Unable to execute HTTP request: unknown")`；对 S3 bad access key 用例的 `assertThrows(..., S3Exception.class, "...does not exist...", ...)` 改为 `.isInstanceOf(S3Exception.class).hasMessageContaining(...)`；对 DynamoDB endpoint 用例从 `assertThrowsCause(..., dynamoDbClient::listTables)` 改为 `assertThatThrownBy(dynamoDbClient::listTables).cause()....`。新增 `Assertions` import、移除 `AssertHelpers` import。

### `aws/src/integration/java/org/apache/iceberg/aws/dynamodb/TestDynamoDbCatalog.java`

**修改目的**：将 DynamoDB Catalog 集成测试中的多处 `AssertHelpers` 调用改为 AssertJ。

**工作逻辑**：覆盖建命名空间/建表重复、坏名、rename 不存在/重名、register 重复等多处异常断言。例如 `AssertHelpers.assertThrows("should not create duplicated namespace", AlreadyExistsException.class, "already exists", () -> catalog.createNamespace(namespace))` 改为 `Assertions.assertThatThrownBy(() -> catalog.createNamespace(namespace)).isInstanceOf(AlreadyExistsException.class).hasMessageContaining("already exists")`。值得注意的是原 `assertThrows("metadata location should be deleted", NoSuchKeyException.class, ...)` 这类带描述的断言，改写时把描述迁移到 `.as("metadata location should be deleted")`，且原未校验消息子串的（仅校验类型）也补上了 `.hasMessageContaining("not found")`。`testRegisterExistingTable` 中已有的 AssertJ 断言额外补了 `.hasMessageContaining("already exists")` 以增强校验。

### `aws/src/integration/java/org/apache/iceberg/aws/dynamodb/TestDynamoDbLockManager.java`

**修改目的**：将 DynamoDB 锁管理器测试中的一处 `AssertHelpers` 调用改为 AssertJ。

**工作逻辑**：`AssertHelpers.assertThrows("should fail to initialize the lock manager", IllegalStateException.class, "Cannot find Dynamo table", () -> new DynamoDbLockManager(dynamo2, lockTableName))` 改为 `Assertions.assertThatThrownBy(() -> new DynamoDbLockManager(dynamo2, lockTableName)).as("should fail to initialize the lock manager").isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot find Dynamo table")`。新增 `Assertions` import、移除 `AssertHelpers` import。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogCommitFailure.java`

**修改目的**：将 Glue Catalog 提交失败测试中的多处 `AssertHelpers` 调用改为 AssertJ，是本提交改动量最大的文件（含 `assertThrowsWithCause`）。

**工作逻辑**：覆盖 `CommitFailedException` 直接抛出、`CommitStateUnknownException`、并发修改、`NotFoundException`、`ForbiddenException`、`ValidationException`、`S3Exception`、`GlueException`（300/500 状态码）等多种提交失败路径。常规 `assertThrows` 一律改为 `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)`。其中并发修改用例使用了 `AssertHelpers.assertThrowsWithCause("...", CommitFailedException.class, "Glue detected concurrent update", ConcurrentModificationException.class, null, () -> spyOps.commit(...))`，改写为 `assertThatThrownBy(() -> spyOps.commit(...)).isInstanceOf(CommitFailedException.class).hasMessageContaining("Glue detected concurrent update").cause().isInstanceOf(ConcurrentModificationException.class)`，即用链式 `.cause().isInstanceOf(...)` 表达"外层异常类型 + 内层 cause 类型"的双重校验。对于原 `assertThrows(null, S3Exception.class, () -> ...)` 这类无描述无消息子串的退化形式，改写为 `.isInstanceOf(S3Exception.class).hasMessage(null)`。新增 `Assertions` import、移除 `AssertHelpers` import。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogNamespace.java`

**修改目的**：将 Glue Catalog 命名空间测试中的多处 `AssertHelpers` 调用改为 AssertJ。

**工作逻辑**：覆盖命名空间不存在、重复创建、非法/嵌套名、删除后不存在、删除非空命名空间（含 Iceberg 表与非 Iceberg 表）等异常断言。例如 `AssertHelpers.assertThrows("namespace does not exist before create", EntityNotFoundException.class, "not found", () -> glue.getDatabase(...))` 改为 `Assertions.assertThatThrownBy(() -> glue.getDatabase(...)).as("namespace does not exist before create").isInstanceOf(EntityNotFoundException.class).hasMessageContaining("not found")`。描述文本统一迁移到 `.as(...)`。新增 `Assertions` import、移除 `AssertHelpers` import。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogTable.java`

**修改目的**：将 Glue Catalog 表操作测试中的多处 `AssertHelpers` 调用改为 AssertJ。

**工作逻辑**：覆盖重复建表、坏表名、rename 到已存在表、rename 非 Iceberg 表、表已删除后 loadTable 等异常断言。例如重复建表用例 `AssertHelpers.assertThrows("should not create table with the same name", AlreadyExistsException.class, "Table already exists", ...)` 改为 `Assertions.assertThatThrownBy(...).isInstanceOf(AlreadyExistsException.class).as("should not create table with the same name").hasMessageContaining("Table already exists")`（注意此处 `.as(...)` 的位置在 `isInstanceOf` 之后，AssertJ 允许这种顺序但社区惯例通常将 `.as` 前置；此为与原 PR 一致的写法）。rename 非 Iceberg 表用例涉及两个连续的 `assertThrows`，分别改写为两个独立的 `assertThatThrownBy(...)` 链。新增 `Assertions` import、移除 `AssertHelpers` import。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationDataOperations.java`

**修改目的**：将 Lake Formation 数据操作测试中的 `AssertHelpers` 调用改为 AssertJ。

**工作逻辑**：覆盖无 SELECT 权限 loadTable（`AccessDeniedException` "Insufficient Lake Formation permission(s)"）、无 INSERT 权限 append（`S3Exception` "Access Denied"）、无 DATA_LOCATION_ACCESS 权限 delete（`ForbiddenException` "Glue cannot access the requested resources"）等用例。每处 `AssertHelpers.assertThrows("描述", XxxException.class, "子串", () -> ...)` 改为 `Assertions.assertThatThrownBy(() -> ...).as("描述").isInstanceOf(XxxException.class).hasMessageContaining("子串")`。新增 `Assertions` import、移除 `AssertHelpers` import。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationMetadataOperations.java`

**修改目的**：将 Lake Formation 元数据操作测试中的多处 `AssertHelpers` 调用改为 AssertJ，是本提交改动量较大的文件之一。

**工作逻辑**：覆盖无 CREATE_DATABASE/DROP/CREATE_TABLE/ALTER/SELECT 等权限下各元数据操作的异常断言，共约 8 处。例如 `AssertHelpers.assertThrows("attempt to create a database without CREATE_DATABASE permission should fail", AccessDeniedException.class, "Insufficient Lake Formation permission(s)", () -> glueCatalogPrivilegedRole.createNamespace(...))` 改为 `Assertions.assertThatThrownBy(() -> glueCatalogPrivilegedRole.createNamespace(...)).as("attempt to create a database without CREATE_DATABASE permission should fail").isInstanceOf(AccessDeniedException.class).hasMessageContaining("Insufficient Lake Formation permission(s)")`。无 ALTER 权限 alter table 的用例从 `AssertHelpers.assertThrows(..., ForbiddenException.class, "...", updateProperties::commit)` 改为 `Assertions.assertThatThrownBy(updateProperties::commit).as(...).isInstanceOf(ForbiddenException.class).hasMessageContaining(...)`，对方法引用形式同样适用。新增 `Assertions` import、移除 `AssertHelpers` import。

## 小结

本提交将 AWS 集成测试模块对自研 `AssertHelpers` 的全部调用机械式替换为业界标准的 AssertJ `assertThatThrownBy` 链式断言，统一了测试异常断言风格，是 Iceberg 清退 `AssertHelpers` 历史工具计划在 AWS 模块上的关键一步。
