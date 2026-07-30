# 提交 2833：Follow-up: Optimize support recursive delegate unwrapping to find ExtendedParser in parser chains (#14497)

## 提交信息

- **序号**：2833 / 4088
- **哈希**：caa42f6175e4535c462eeab8abb8c82509699775
- **短哈希**：caa42f617
- **日期**：2025-11-05 18:26:59 -0800
- **作者**：majian
- **提交说明**：Follow-up: Optimize support recursive delegate unwrapping to find ExtendedParser in parser chains (#14497)
- **PR/Issue**：#14497

## 总体目的

Iceberg 的 Spark 集成通过 `ExtendedParser`（扩展自 Spark 的 `ParserInterface`）来解析 sortOrder 等 Iceberg 特有语法。Spark 的 parser 经常以"委托链"形式存在——一个 parser 内部持有另一个 parser 字段（delegate），层层包装。`ExtendedParser.findParser` 通过反射扫描 parser 实例的字段，逐层 unwrap 委托链，直到找到实现了 `ExtendedParser` 的那一层。

之前 2810/2821 引入了递归 unwrap 的能力，但本次 follow-up 发现实现还可以再优化：原 `findParser` 循环中先调用 `getNextDelegateParser(current)`，再判断是否为 null 决定 `break`，多了一层冗余判断；同时 `getNextDelegateParser` 内部对反射异常完全静默吞掉（`// ignore`），不利于排查 parser 链扫描中的意外问题。

本提交对 Spark 3.5 与 4.0 两个版本的 `ExtendedParser` 进行优化：简化循环终止条件，并把静默异常改为通过 SLF4J 输出 warn 级别日志，便于运维定位。

## 如何达成设计目的

1. 在 `findParser` 的 `while (current != null)` 循环中，去掉对 `next == null` 的显式 break，直接 `current = getNextDelegateParser(current)`。由于 `getNextDelegateParser` 返回 null 时 `current` 自然变为 null，循环条件 `current != null` 会让循环退出，逻辑等价但更简洁。
2. 引入 SLF4J 的 `Logger` 与 `LoggerFactory`，新增 `log()` 私有静态方法返回 `ExtendedParser` 的 logger。
3. 将 `getNextDelegateParser` 中 `catch (Exception e)` 的 `// ignore` 替换为 `log().warn("Failed to scan delegate parser in {}: ", parser.getClass().getName(), e);`，保留扫描失败的可观测性，同时不抛出异常影响主流程。
4. 修改同步应用到 Spark 3.5 与 4.0 两个分支（3.4 不含此文件，因为 3.4 路径不同）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/ExtendedParser.java` (+8/-7 lines)

**修改目的**：简化委托链 unwrap 循环，并将静默异常改为 warn 日志。

**工作逻辑**：
- `findParser` 循环体由：
  ```java
  ParserInterface next = getNextDelegateParser(current);
  if (next == null) { break; }
  current = next;
  ```
  简化为：
  ```java
  current = getNextDelegateParser(current);
  ```
  当 `getNextDelegateParser` 返回 null 时，`current` 变为 null，`while (current != null)` 退出，行为不变。
- `getNextDelegateParser` 的 `catch` 块改为 `log().warn("Failed to scan delegate parser in {}: ", parser.getClass().getName(), e);`，并新增 `log()` 方法返回 logger。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/ExtendedParser.java` (+8/-7 lines)

**修改目的**：与 3.5 完全相同的优化应用到 Spark 4.0 分支。逻辑一致。

## 总结

该提交作为 2810/2821 的 follow-up，对 `ExtendedParser` 的 parser 委托链 unwrap 逻辑做了两点优化：简化循环终止写法，并将原本静默吞掉的反射异常改为 warn 级别日志输出，提升可观测性与代码可读性。修改同步应用到 Spark 3.5 与 4.0。这是一个低风险的代码质量改进。
