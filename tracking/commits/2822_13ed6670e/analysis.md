# 提交 2822：Spark 3.5: Support recursive delegate unwrapping to find ExtendedParser in parser chains (#14483)

## 提交信息

- **序号**：2822 / 4088
- **哈希**：13ed6670e5f3c7b79830ea1e697c9c15bcb3286b
- **短哈希**：13ed6670e
- **日期**：2025-11-02 21:15:26 -0800
- **作者**：majian
- **提交说明**：Spark 3.5: Support recursive delegate unwrapping to find ExtendedParser in parser chains (#14483)
- **PR/Issue**：#14483

## 总体目的

本提交是提交 2810（Spark 4.0 版本）在 Spark 3.5 上的对应实现（backport）。两者解决完全相同的问题：Iceberg 的 `ExtendedParser.parseSortOrder` 方法仅通过简单的 `instanceof` 检查来判断当前解析器是否为 Iceberg 扩展解析器，当 Iceberg 解析器被委托解析器链包装时无法被发现，导致排序字段解析失败。

由于 Iceberg 同时维护 Spark 3.5 和 Spark 4.0 两个版本分支，相同的修复需要分别应用到两个版本的代码路径中（`spark/v3.5/` 和 `spark/v4.0/`）。本提交将递归委托解包逻辑移植到 Spark 3.5 对应的代码目录。

## 如何达成设计目的

与提交 2810 完全一致的实现方案：

1. **新增 `findParser` 方法**：递归遍历解析器委托链查找目标类型的解析器。
2. **新增 `getNextDelegateParser` 方法**：通过反射获取解析器对象中类型为 `ParserInterface` 的委托字段，沿类继承层级查找。
3. **修改 `parseSortOrder`**：用递归查找替代直接 `instanceof` 检查。
4. **新增 `TestExtendedParser` 测试**：覆盖直接使用、嵌套包装、父类字段、未找到等场景。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/ExtendedParser.java` (+45/-3 lines)

**修改目的**：为 Spark 3.5 版本实现递归委托解析器查找逻辑。

**工作逻辑**：与提交 2810 中 Spark 4.0 版本的修改完全一致：
- `parseSortOrder` 改为调用 `findParser(spark.sessionState().sqlParser(), ExtendedParser.class)` 递归查找
- `findParser` 循环遍历委托链
- `getNextDelegateParser` 反射查找 `ParserInterface` 类型字段，含父类层级

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/TestExtendedParser.java` (+231/-0 lines, 新文件)

**修改目的**：为 Spark 3.5 版本的递归查找逻辑提供测试覆盖。

**工作逻辑**：与提交 2810 中 Spark 4.0 版本的测试完全一致，包含四个测试用例：真实 Iceberg 解析器解析、嵌套包装查找、未找到抛异常、父类字段查找。辅助类 `WrapperParser`、`ChildParser`、`GrandChildParser` 模拟多级委托和继承场景。

## 总结

本提交是提交 2810（Spark 4.0 递归委托解包）在 Spark 3.5 上的对应实现，代码逻辑与测试完全一致，仅目录路径不同（`spark/v3.5/` vs `spark/v4.0/`）。两者共同确保在 Spark 3.5 和 4.0 环境下，Iceberg 扩展解析器在委托解析器链中都能被正确发现，从而正确解析排序字段语法。
