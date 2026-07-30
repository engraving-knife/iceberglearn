# 提交 0025：Dell: Migrate Files using TestRule to Junit5 (#8707)

## 提交信息

- **序号**：0025 / 4088
- **哈希**：90cf38c50ab4e7b7d85011d8fd69bf60a82ea4c0
- **短哈希**：90cf38c50
- **日期**：2023-10-10 10:03:16 +0200
- **作者**：Ashutosh Roy
- **提交说明**：Dell: Migrate Files using TestRule to Junit5 (#8707)
- **PR/Issue**：#8707

## 总体目的

这个提交把 Iceberg 的 Dell ECS 模块（`dell/`）下使用 JUnit 4 `TestRule` 机制的测试文件迁移到 JUnit 5 的扩展模型（JUnit Jupiter `Extension`），是 Iceberg 仓库整体从 JUnit 4 向 JUnit 5 渐进式迁移工作的一部分。

背景：Iceberg 早期测试基于 JUnit 4，使用 `@Rule` / `@ClassRule` 配合 `org.junit.rules.TestRule` 实现测试前后的 fixture 装配（如启动 mock 服务、初始化客户端、清理资源等）。JUnit 5 引入了完全不同的扩展机制：用 `@RegisterExtension` / `@ExtendWith` 注册实现了 `BeforeEachCallback` / `AfterEachCallback` 等接口的 `Extension`，取代 JUnit 4 的 `TestRule.apply(Statement, Description)` 模式。迁移到 JUnit 5 可以获得更现代的测试 API（如 `@BeforeEach` / `@AfterEach` 替代 `@Before` / `@After`）、更好的扩展组合性、与 AssertJ / Jupiter 断言的统一生态，以及避免 JUnit 4 与 JUnit 5 双引擎并存带来的依赖复杂度。

Dell ECS 模块的特殊之处在于它依赖一个共享的 mock 规则类 `EcsS3MockRule`，所有 7 个测试类都通过 `@Rule` / `@ClassRule` 引用它来搭建 S3 mock 环境。因此迁移的核心与难点在于把这个共享 `TestRule` 改造成 JUnit 5 `Extension`，再同步更新所有使用它的测试类。本提交正是完成这一闭环：改造 mock 规则类 + 迁移 7 个 ECS 测试类。

## 如何达成设计目的

改动分两层：

1. **基础设施层**——把 [EcsS3MockRule.java](file:///Users/fengxiaohang/trae/iceberglearn/dell/src/test/java/org/apache/iceberg/dell/mock/ecs/EcsS3MockRule.java) 从实现 `org.junit.rules.TestRule` 改为实现 JUnit 5 的 `BeforeEachCallback` + `AfterEachCallback`，删除旧的 `apply(Statement, Description)` 方法（该方法用匿名 `Statement` 包装 `initialize()` / `cleanUp()`），改为在 `beforeEach(ExtensionContext)` 中调用 `initialize()`、在 `afterEach(ExtensionContext)` 中调用 `cleanUp()`。`initialize()` 与 `cleanUp()` 的内部逻辑（创建 bucket、装配 `MockDellClientFactory`、清理等）完全保留。

2. **测试类层**——7 个测试类（`TestEcsAppendOutputStream`、`TestEcsCatalog`、`TestEcsInputFile`、`TestEcsOutputFile`、`TestEcsSeekableInputStream`、`TestEcsTableOperations`、`TestExceptionCode`）统一做以下替换：`import org.junit.Rule` / `org.junit.ClassRule` → `import org.junit.jupiter.api.extension.RegisterExtension`；`import org.junit.Test` → `import org.junit.jupiter.api.Test`；`@Rule` / `@ClassRule` → `@RegisterExtension`；`@Before` / `@After` → `@BeforeEach` / `@AfterEach`。`TestEcsCatalog` 额外把 `@Before` / `@After` 方法注解改为 `@BeforeEach` / `@AfterEach`。

整体设计是标准的 JUnit 4 → 5 迁移套路：用 Extension 接口的回调方法替代 TestRule 的 Statement 包装，用 Jupiter 注解替代 JUnit 4 注解，测试方法体本身不变。

## 修改详情

### `dell/src/test/java/org/apache/iceberg/dell/mock/ecs/EcsS3MockRule.java`

**修改目的**：把共享 mock 工具类从 JUnit 4 `TestRule` 改造为 JUnit 5 `Extension`，作为所有 ECS 测试的 fixture 基础设施。

**工作逻辑**：
- import 替换：`org.junit.rules.TestRule` / `org.junit.runner.Description` / `org.junit.runners.model.Statement` → `org.junit.jupiter.api.extension.AfterEachCallback` / `BeforeEachCallback` / `ExtensionContext`。
- 类声明：`implements TestRule` → `implements BeforeEachCallback, AfterEachCallback`。
- 类注释："Mock rule of ECS S3 mock." → "Mock Extension of ECS S3 mock."。
- 删除原 `apply(Statement base, Description description)` 方法：它原本返回一个匿名 `Statement`，在 `evaluate()` 中依次执行 `initialize()`、`base.evaluate()`、`finally cleanUp()`。
- 新增两个回调方法：`beforeEach(ExtensionContext)` 调用 `initialize()`；`afterEach(ExtensionContext)` 调用 `cleanUp()`。两者均使用现有私有方法，逻辑等价于原 `apply` 的包装流程。
- `initialize()` / `cleanUp()` 及其他成员（`create()` 工厂、`randomObjectName()`、`autoCreateBucket` 字段等）保持不变。

注意：原 `@ClassRule`（静态字段）与 `@Rule`（实例字段）在 JUnit 5 中统一为 `@RegisterExtension`。当字段为 `static` 时，JUnit 5 将该扩展视为"类级别"执行一次（等价于原 `@ClassRule`）；为实例字段时按每个测试方法执行（等价于原 `@Rule`）。因此改造后字段是否 `static` 决定了 mock 的生命周期，各测试类维持了原有的 `static` 与否。

### `dell/src/test/java/org/apache/iceberg/dell/ecs/TestEcsAppendOutputStream.java`

**修改目的**：迁移到 JUnit 5，使用 `@RegisterExtension` 注册静态 mock 扩展。

**工作逻辑**：`@ClassRule public static EcsS3MockRule rule` → `@RegisterExtension public static EcsS3MockRule rule`（保持 `static`，对应原 `@ClassRule` 的类级生命周期）；import 从 `org.junit.ClassRule` / `org.junit.Test` 改为 `org.junit.jupiter.api.Test` / `org.junit.jupiter.api.extension.RegisterExtension`。测试方法体不变。

### `dell/src/test/java/org/apache/iceberg/dell/ecs/TestEcsCatalog.java`

**修改目的**：迁移到 JUnit 5，并同步迁移 `@Before` / `@After` 生命周期注解。

**工作逻辑**：`@Rule public EcsS3MockRule rule`（实例字段）→ `@RegisterExtension public EcsS3MockRule rule`，保持实例字段（方法级生命周期）；`@Before` → `@BeforeEach`，`@After` → `@AfterEach`；import 替换为 `org.junit.jupiter.api.AfterEach` / `BeforeEach` / `Test` / `extension.RegisterExtension`，删除 `org.junit.After` / `Before` / `Rule` / `Test`。注意 `org.junit.Assert` 保留未迁移（属于另一批迁移范围）。`before()` / `after()` 方法体（初始化 `EcsCatalog`、关闭 catalog）不变。

### `dell/src/test/java/org/apache/iceberg/dell/ecs/TestEcsInputFile.java`

**修改目的**：迁移到 JUnit 5，`@ClassRule` 静态字段改 `@RegisterExtension`。

**工作逻辑**：与 `TestEcsAppendOutputStream` 同模式：`@ClassRule public static EcsS3MockRule rule` → `@RegisterExtension public static EcsS3MockRule rule`，import 替换为 Jupiter + `RegisterExtension`。测试方法体不变。

### `dell/src/test/java/org/apache/iceberg/dell/ecs/TestEcsOutputFile.java`

**修改目的**：迁移到 JUnit 5，`@ClassRule` 静态字段改 `@RegisterExtension`。

**工作逻辑**：同上模式，`@ClassRule public static` → `@RegisterExtension public static`，import 替换。测试方法体不变。

### `dell/src/test/java/org/apache/iceberg/dell/ecs/TestEcsSeekableInputStream.java`

**修改目的**：迁移到 JUnit 5，`@ClassRule` 静态字段改 `@RegisterExtension`。

**工作逻辑**：同上模式。测试方法体不变。

### `dell/src/test/java/org/apache/iceberg/dell/ecs/TestEcsTableOperations.java`

**修改目的**：迁移到 JUnit 5，`@Rule` 实例字段改 `@RegisterExtension`。

**工作逻辑**：`@Rule public EcsS3MockRule rule`（实例字段）→ `@RegisterExtension public EcsS3MockRule rule`，保持方法级生命周期；import 替换为 Jupiter + `RegisterExtension`。测试方法体不变。

### `dell/src/test/java/org/apache/iceberg/dell/mock/ecs/TestExceptionCode.java`

**修改目的**：迁移到 JUnit 5，`@Rule` 实例字段改 `@RegisterExtension`。

**工作逻辑**：`@Rule public EcsS3MockRule rule` → `@RegisterExtension public EcsS3MockRule rule`；import 替换为 `org.junit.jupiter.api.Test` + `extension.RegisterExtension`。测试方法体不变。

## 小结

将 Dell ECS 模块的共享 mock 工具 `EcsS3MockRule` 从 JUnit 4 `TestRule` 改造为 JUnit 5 `BeforeEachCallback` / `AfterEachCallback` 扩展，并同步迁移 7 个测试类的注解与生命周期回调，完成该模块从 JUnit 4 到 JUnit 5 的测试框架升级。
