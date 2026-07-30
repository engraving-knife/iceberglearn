# 提交 1538 fc3f705ab 分析

## 提交信息
- 哈希：fc3f705ab45a6eefcf2be547c5193173ed42b807
- 日期：2024-12-26（Thu Dec 26 16:19:16 2024 +0900）
- 作者：Yuya Ebihara <ebyhry@gmail.com>
- 消息：API: Replace deprecated `asList` with `asInstanceOf` (#11875)

## 总体目的

Iceberg 的 `api` 模块测试 `TestExceptionUtil` 使用 AssertJ 来验证 `ExceptionUtil.runSafely(...)` 的异常抑制行为。测试中通过 `.extracting(e -> Arrays.asList(e.getSuppressed()))` 从抛出的异常中提取被抑制异常列表，随后需要把断言对象"窄化"为 List 类型断言，以链式调用 `hasSize(...)`、`containsExactly(...)` 等 List 专属断言。

AssertJ 早期提供了 `asList()` 方法用于这种类型窄化。但在 AssertJ 3.21+ 中，`asList()` 被标记为 `@Deprecated`，官方推荐改用类型安全的 `asInstanceOf(InstanceOfAssertFactories.LIST)`。`asInstanceOf` 接收一个 `InstanceOfAssertFactory` 参数，明确指定目标断言类型，既避免了不安全的强转，又能在编译期提供更好的类型检查。

随着 Iceberg 升级 AssertJ 版本，测试编译时会出现 `asList()` 的弃用告警。本提交将 `TestExceptionUtil` 中 4 处 `.asList()` 调用统一替换为 `.asInstanceOf(InstanceOfAssertFactories.LIST)`，并新增对应 import，消除告警并与新版 AssertJ 推荐用法对齐。这是与提交 1534、1536 同一波"清理弃用 API"工作的一部分。

## 如何达成设计目的

在 `TestExceptionUtil.java` 中新增 `import org.assertj.core.api.InstanceOfAssertFactories;`，然后将 4 个测试方法中相同的链式断言片段 `.asList()` 替换为 `.asInstanceOf(InstanceOfAssertFactories.LIST)`。替换后断言语义不变：仍将上游 `extracting` 返回的泛化断言窄化为 List 断言，后续 `hasSize(2)`、`containsExactly(...)` 调用方式完全一致。

### 修改详情

#### `api/src/test/java/org/apache/iceberg/util/TestExceptionUtil.java`

**修改目的**：消除 4 处 AssertJ `asList()` 弃用调用。

**工作逻辑**：

该测试类验证 `ExceptionUtil.runSafely(...)` 的行为：当 block、catch handler、finally handler 分别抛出异常时，原异常作为主异常抛出，catch 与 finally 抛出的异常作为 `suppressed` 附着其上。4 个测试方法（`testRunSafely`、`testRunSafelyTwoExceptions`、`testRunSafelyThreeExceptions`、以及一个 RuntimeException 变体）使用相同的断言模式：

```java
assertThatThrownBy(...)
    .isInstanceOf(CustomCheckedException.class)
    .isEqualTo(exc)
    .extracting(e -> Arrays.asList(e.getSuppressed()))
    .asInstanceOf(InstanceOfAssertFactories.LIST)  // 原 .asList()
    .hasSize(2)
    .containsExactly(suppressedOne, suppressedTwo);
```

`extracting(...)` 返回一个 `AbstractAssert`（泛化），无法直接调用 List 专属方法。`asInstanceOf(InstanceOfAssertFactories.LIST)` 通过工厂对象将其转换为 `ListAssert<Object>`，使 `hasSize`/`containsExactly` 可用。`InstanceOfAssertFactories.LIST` 是 AssertJ 预定义的工厂实例，等价于旧 `asList()` 的语义但类型安全。

新增 import：
```java
import org.assertj.core.api.InstanceOfAssertFactories;
```

4 处替换分布在 4 个 `@Test` 方法中，每处 1 行，共 4 行替换 + 1 行 import。

## 小结

- **成效**：消除了 `TestExceptionUtil` 中 4 处 AssertJ `asList()` 弃用调用，改用类型安全的 `asInstanceOf(InstanceOfAssertFactories.LIST)`，避免构建告警并保持与新版 AssertJ 兼容。
- **影响范围**：仅 1 个测试文件（`api` 模块），4 行替换 + 1 行 import，不进入发布产物，对运行时无影响。
- **回迁到 1.4.x 的注意事项**：属于代码整洁/兼容性改进，不修复任何 bug，**无需回迁**。若 1.4.x 依赖的 AssertJ 版本尚无 `InstanceOfAssertFactories`（AssertJ < 3.21），回迁会编译失败——需确认 1.4.x 的 AssertJ 版本再决定。
