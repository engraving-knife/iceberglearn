# 提交 0811：Core: Use TestTemplate instead of Test annotation in TestPartitionSpecParser/Info (#10435)

## 提交信息

- **序号**：0811
- **哈希**：40da6f1e430a233ac82e4b5fb41b29615daf298f
- **短哈希**：40da6f1e4
- **日期**：2024-06-04 12:32:07 +0200
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Use TestTemplate instead of Test annotation in TestPartitionSpecParser/Info (#10435)
- **PR/Issue**：#10435

## 总体目的

这个提交修复了 `TestPartitionSpecInfo` 和 `TestPartitionSpecParser` 两个测试类中参数化测试未正确初始化的隐患。作者在提交说明中明确指出：这两个测试类中的测试方法使用了 `@Test` 注解，但该类实际上是通过 `@ExtendWith(ParameterizedTestExtension.class)` 注册了参数化扩展的。JUnit 5 的参数化扩展依赖 `@TestTemplate` 注解来驱动带参数的测试执行，而非 `@Test`。使用 `@Test` 时，参数（本提交中即 `formatVersion`）不会被正确注入/初始化，导致测试虽然能跑过，但并未真正在多个参数上下文中被执行，削弱了测试的覆盖效果。

总体而言，这是一个测试质量修复：把 11 处 `@Test` 替换为 `@TestTemplate`，让参数化测试机制真正生效，确保 `formatVersion` 参数被正确传入每个测试方法，使测试在 v1/v2 两种表格式版本下都执行。

## 如何达成设计目的

设计思路是直接且最小化的：将两个测试类中所有非 `@ParameterizedTest` 标注的测试方法上的 `@Test` 注解替换为 `@TestTemplate`，同时把对应的 import 从 `org.junit.jupiter.api.Test` 改为 `org.junit.jupiter.api.TestTemplate`。

背景逻辑：在 JUnit 5 中，`@Test` 标记的是一个普通的、无参的测试方法，由 JUnit 引擎直接调用一次；而 `@TestTemplate` 标记的是一个"测试模板"方法，引擎会为它调用注册的扩展（如 `ParameterizedTestExtension`）来生成多个调用上下文。当一个类用 `@ExtendWith(ParameterizedTestExtension.class)` 注册了参数化扩展，并提供了一个返回参数集合的方法时，类中需要被参数化的方法必须用 `@TestTemplate` 才能拿到参数。错用 `@Test` 会导致方法被当作普通测试执行，参数字段（如 `formatVersion`）保持默认值/null，测试实质上只在一种上下文下运行，与设计意图不符。

本提交涉及的 11 处替换分布在两个文件中，均为注解级别的机械替换，不改变任何测试逻辑或断言。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestPartitionSpecInfo.java`

**修改目的**：使该类中 6 个测试方法成为参数化测试模板，确保 `formatVersion` 被正确初始化。

**修改内容**：
- import 行：`org.junit.jupiter.api.Test` → `org.junit.jupiter.api.TestTemplate`。
- 6 处方法注解 `@Test` → `@TestTemplate`，覆盖：`testSpecIsUnpartitionedForVoidTranforms`、`testSpecInfoUnpartitionedTable`、`testSpecInfoPartitionedTable`、`testColumnDropWithPartitionSpecEvolution`、`testSpecInfoPartitionSpecEvolutionForV1Table` 等。这些方法内部均使用 `formatVersion` 字段创建 `TestTables.TestTable`，修复前该字段未被参数化扩展注入，修复后每次执行会按参数列表正确赋值。

### `core/src/test/java/org/apache/iceberg/TestPartitionSpecParser.java`

**修改目的**：使该类中 5 个测试方法成为参数化测试模板。

**修改内容**：
- import 行：`org.junit.jupiter.api.Test` → `org.junit.jupiter.api.TestTemplate`。
- 5 处方法注解 `@Test` → `@TestTemplate`，覆盖：`testToJsonForV1Table`、`testFromJsonWithFieldId`、`testFromJsonWithoutFieldId`、`testTransforms` 等。该类继承 `TestBase` 并用 `@ExtendWith(ParameterizedTestExtension.class)` 注册扩展，修复后这些方法能正确接收参数化上下文。

## 小结

**成效**：修复后，两个测试类的参数化测试机制真正生效，`formatVersion` 参数被正确注入，测试覆盖从"单一上下文名义执行"变为"按参数列表完整执行"，提升了测试有效性。改动是纯测试注解替换，无功能代码变更，风险极低。

**影响范围**：仅影响 `core` 模块的两个测试类，不影响任何生产代码或公开 API。

**回迁到 1.4.x 的注意事项**：本提交是低风险的测试修复，回迁时需确认 1.4.x 分支上这两个测试类是否已存在相同的 `@Test`/`@ExtendWith(ParameterizedTestExtension.class)` 结构（即是否已引入参数化扩展但注解未跟上）。若 1.4.x 上这些测试类尚未参数化，则回迁无意义；若已参数化，则应回迁以保证测试质量。建议直接 cherry-pick，并验证测试在参数化模式下能通过。

**验证建议**：回迁后建议执行这两个测试类，确认参数化执行生效（可通过测试运行次数或日志判断是否在多个 formatVersion 上下文下运行），以保证回迁达到预期效果。
