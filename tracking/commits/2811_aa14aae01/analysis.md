# 提交 2811：Spark 4.0: Support recursive delegate unwrapping to find ExtendedParser in parser chains (#13625)

## 提交信息

- **序号**：2811 / 4088
- **哈希**：aa14aae0111d757f8cded70b87c1d58da3fe272c
- **短哈希**：aa14aae01
- **日期**：2025-11-01 16:37:22 -0700
- **作者**：majian
- **提交说明**：Spark 4.0: Support recursive delegate unwrapping to find ExtendedParser in parser chains (#13625)
- **PR/Issue**：#13625

## 总体目的

Iceberg 在 Spark 中通过 `ExtendedParser` 接口扩展了 Spark 的 SQL 解析器，用于解析 Iceberg 特有的 SQL 语法（例如 `parseSortOrder` 用于解析排序字段字符串）。在 Spark 4.0 中，Spark 的解析器架构使用了"委托链"（delegate chain）模式：外层解析器可能包装了一个或多个内层解析器，`IcebergSparkSqlExtensionsParser` 作为 `ExtendedParser` 的实现，可能被包装在多层委托解析器之中。

原有的 `parseSortOrder` 方法只检查 `spark.sessionState().sqlParser()` 是否直接是 `ExtendedParser` 的实例（使用 `instanceof` 检查）。如果 Iceberg 的扩展解析器被包在委托解析器内部（例如某些环境或框架在 Spark 的解析器外层再包一层），原有的简单 `instanceof` 检查就会失败，导致无法找到 `ExtendedParser`，最终抛出异常并回退到默认行为（将排序字段当作普通表达式解析），这可能产生错误结果。

本提交通过递归地"解包"（unwrap）委托解析器链，深入查找真正的 `ExtendedParser` 实例，解决了在解析器链中找不到 Iceberg 扩展解析器的问题。

## 如何达成设计目的

设计思路是引入递归查找机制：

1. **新增 `findParser` 方法**：从给定的 `ParserInterface` 开始，递归遍历委托链，检查每一层是否是目标类型（`ExtendedParser`），直到找到或遍历完整个链。

2. **新增 `getNextDelegateParser` 方法**：通过反射获取当前解析器对象中所有类型为 `ParserInterface` 的字段（即委托的内层解析器），并沿类继承层级向上查找（包括父类字段）。

3. **修改 `parseSortOrder`**：用 `findParser` 替换原来的直接 `instanceof` 检查，使查找逻辑能穿透任意层级的委托包装。

4. **新增测试 `TestExtendedParser`**：通过反射替换 Spark 会话中的解析器，验证多种场景：直接使用 Iceberg 解析器、嵌套包装的解析器、父类字段中的解析器、找不到解析器时的异常抛出等。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/ExtendedParser.java` (+45/-3 lines)

**修改目的**：实现递归委托解析器查找逻辑，替代原来的单层 `instanceof` 检查。

**工作逻辑**：
- `parseSortOrder` 方法原为：`if (spark.sessionState().sqlParser() instanceof ExtendedParser)` 直接判断顶层解析器。修改后调用 `findParser(spark.sessionState().sqlParser(), ExtendedParser.class)` 进行递归查找。
- `findParser` 是泛型方法，循环遍历解析器链：若当前解析器是目标类型则返回；否则调用 `getNextDelegateParser` 获取下一层委托解析器继续查找，直到链尾返回 null。
- `getNextDelegateParser` 使用反射：从解析器对象的类开始，遍历其声明的所有字段，找到第一个类型为 `ParserInterface` 且不等于自身的字段值作为委托解析器返回；同时沿 `getSuperclass()` 向上查找父类字段，确保父类中声明的委托字段也能被发现。异常被静默忽略以保证健壮性。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/TestExtendedParser.java` (+231/-0 lines, 新文件)

**修改目的**：为递归查找逻辑提供全面的单元测试覆盖。

**工作逻辑**：
- 测试通过反射替换 `SparkSession` 的 `sessionState` 中的 `sqlParser` 字段来模拟不同的解析器链场景。
- `testParseSortOrderWithRealIcebergExtendedParser`：使用真实的 `IcebergSparkSqlExtensionsParser` 包装原解析器，验证排序字符串 "id ASC NULLS FIRST" 能被正确解析为期望的 `RawOrderField`（方向 ASC，空值在前）。
- `testParseSortOrderFindsNestedExtendedParser`：用 mock 的 `ExtendedParser` 包装在 `WrapperParser` 中，验证能找到嵌套的解析器并调用其 `parseSortOrder`。
- `testParseSortOrderThrowsWhenNoExtendedParserFound`：当解析器链中不存在 `ExtendedParser` 时，验证抛出包含 "Iceberg ExtendedParser" 的 `IllegalStateException`。
- `testParseSortOrderFindsExtendedParserInParentClassField`：使用三层继承（`GrandChildParser` -> `ChildParser` -> `WrapperParser`），验证能通过父类字段找到委托的 `ExtendedParser`，证明反射查找覆盖父类层级的逻辑正确。
- 辅助类 `WrapperParser`、`ChildParser`、`GrandChildParser` 模拟了多级委托和继承场景。

## 总结

本提交解决了 Spark 4.0 环境下 Iceberg 扩展解析器被委托包装后无法被 `parseSortOrder` 正确发现的问题。通过递归反射遍历解析器委托链（包括父类字段），确保在任意层级的包装下都能找到 `ExtendedParser` 实例。新增的测试覆盖了直接使用、嵌套包装、父类字段、未找到等多种场景。此修复对于在解析器链较复杂的环境中使用 Iceberg 的排序语法解析至关重要。注意提交 2821 是同一功能在 Spark 3.5 版本的对应实现。
