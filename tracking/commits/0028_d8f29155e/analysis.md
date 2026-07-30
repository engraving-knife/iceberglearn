# 提交 0028：Core: Use more permissive check when registering existing table (#8759)

## 提交信息

- **序号**：0028 / 4088
- **哈希**：d8f29155e505b42a1a7279099f768e0684c397d6
- **短哈希**：d8f29155e
- **日期**：2023-10-10
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Use more permissive check when registering existing table (#8759)
- **PR/Issue**：#8759

## 总体目的

这个提交修改的是 Iceberg 通用 catalog 测试基类 `CatalogTests` 中 `testRegisterExistingTable` 这一个测试用例的断言方式，把对 `AlreadyExistsException` 错误消息的校验从精确匹配（`hasMessage`）放宽为前缀匹配（`hasMessageStartingWith`）。

`CatalogTests` 是一个抽象测试基类，所有兼容的 catalog 实现（`HiveCatalog`、`JdbcCatalog`、`NessieCatalog`、`InMemoryCatalog`、REST 服务端 `CatalogHandlers` 等）都继承它并复用其中的测试用例。这个测试用例 `testRegisterExistingTable` 的场景是：先创建一张表 `a.t1`，再尝试用相同 identifier 调用 `registerTable` 注册，期望抛出 `AlreadyExistsException`。

问题在于：不同 catalog 实现在抛出 `AlreadyExistsException` 时构造的错误消息并不完全一致。例如 [BaseMetastoreCatalog.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/BaseMetastoreCatalog.java) 第 128 行用的是 `"Table already exists: %s", identifier`，会得到 `"Table already exists: a.t1"`；但其他实现可能在消息前面加上 catalog 名、后面追加 location 信息或附加上下文，导致完整消息并不是恰好等于 `"Table already exists: a.t1"`。原断言用 `hasMessage` 做严格相等比较，就会让这些合规但消息格式略不同的 catalog 实现无法通过共享测试套件。

放宽为 `hasMessageStartingWith` 后，只要错误消息以 `"Table already exists: a.t1"` 开头即可通过，给各 catalog 实现留出了在消息后追加额外上下文（如 metadata location、catalog 名等）的余地，同时仍能保证关键信息（异常类型 + 表标识符）被正确包含。这是测试基础设施层面的兼容性改进，本身不改变任何运行时行为。

## 如何达成设计目的

整体设计思路很简单：将 AssertJ 的 `hasMessage` 替换为 `hasMessageStartingWith`，从"全等"放宽为"前缀匹配"。这种做法既保留了断言对核心错误描述的检验（防止 catalog 实现抛出风马牛不相及的错误消息），又允许实现方在消息末尾附加实现特定的补充信息。这种"前缀锚定 + 后缀开放"的断言模式是处理跨实现共享测试时常见的折中方案。

## 修改详情

### [core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java)

**修改目的**：放宽 `testRegisterExistingTable` 测试用例中对 `AlreadyExistsException` 错误消息的断言，使不同 catalog 实现都能通过这个共享测试。

**工作逻辑**：被修改的代码位于 `testRegisterExistingTable` 方法内（修改前在第 2697 行附近，文件后续版本中位于 `testRegisterExistingTable` 方法体内）。原代码：

```java
String metadataLocation = ops.current().metadataFileLocation();
Assertions.assertThatThrownBy(() -> catalog.registerTable(identifier, metadataLocation))
    .isInstanceOf(AlreadyExistsException.class)
    .hasMessage("Table already exists: a.t1");
Assertions.assertThat(catalog.dropTable(identifier)).isTrue();
```

修改后：

```java
String metadataLocation = ops.current().metadataFileLocation();
Assertions.assertThatThrownBy(() -> catalog.registerTable(identifier, metadataLocation))
    .isInstanceOf(AlreadyExistsException.class)
    .hasMessageStartingWith("Table already exists: a.t1");
Assertions.assertThat(catalog.dropTable(identifier)).isTrue();
```

测试整体逻辑不变：先创建表 `a.t1`，再尝试用同一 identifier 调用 `registerTable` 注册相同 metadata，期望抛出 `AlreadyExistsException`，最后清理表。唯一变化是断言方法从 `hasMessage`（精确相等）改为 `hasMessageStartingWith`（前缀匹配）。

值得注意：`registerTable` 在 [BaseMetastoreCatalog.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/BaseMetastoreCatalog.java) 第 119-137 行的实现中，会先 `tableExists(identifier)` 检查，存在则抛 `AlreadyExistsException("Table already exists: %s", identifier)`，identifier 在测试中为 `TableIdentifier.of("a", "t1")`，其 `toString()` 即 `"a.t1"`，因此基础实现的消息恰好为 `"Table already exists: a.t1"`。但其他实现如 [JdbcTableOperations.java](file:///Users/fengxiaohang/trae/iceberglearn/core/src/main/java/org/apache/iceberg/jdbc/JdbcTableOperations.java) 第 144-160 行在 commit 阶段也可能抛出 `AlreadyExistsException`，且消息格式可能因 SQL 约束异常链路而附加更多上下文，前缀匹配就能让这些实现顺利通过。

## 小结

通过把共享测试基类 `CatalogTests` 中 `testRegisterExistingTable` 的错误消息断言从精确匹配改为前缀匹配，提升了测试套件对不同 catalog 实现错误消息差异的兼容性，使各 catalog 子类无需为此专门覆盖测试即可通过，是测试基础设施层面的微小但实用的兼容性改进。
