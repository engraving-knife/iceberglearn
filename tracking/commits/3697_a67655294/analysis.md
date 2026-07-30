# 提交 3697：Spark 3.4: Support recursive delegate unwrapping to find ExtendedParser in parser chains (#16306)

## 提交信息

- **序号**：3697 / 4088
- **哈希**：a67655294600f92879f4d5d613662ac40c0c7ba2
- **短哈希**：a67655294
- **日期**：2026-05-12 19:02:53 -0700
- **作者**：Kevin Liu
- **提交说明**：Spark 3.4: Support recursive delegate unwrapping to find ExtendedParser in parser chains (#16306)
- **PR/Issue**：#16306

## 总体目的

这个提交是 PR #14483（及其后续 #14497）向 Spark 3.4 模块的回移植。它修复了 `ExtendedParser.parseSortOrder` 方法在 parser 链中找不到 Iceberg 的 `ExtendedParser` 实现的问题。

Iceberg 通过扩展 Spark 的 `ParserInterface` 提供 `ExtendedParser`，用于解析 Iceberg 特有的 SQL 语法（如 sort order）。此前的实现直接检查 `spark.sessionState().sqlParser() instanceof ExtendedParser`，但很多环境下 Spark 的 SQL parser 会被多层包装（delegate），例如通过其他扩展插件包装。这种情况下，直接的 `instanceof` 检查会失败，因为外层包装器不是 `ExtendedParser`，即使内层的委托对象是。

修复方案是递归地遍历 parser 的委托链（delegate chain），通过反射查找类型为 `ParserInterface` 的字段，直到找到 `ExtendedParser` 实例或遍历完整个链。

## 如何达成设计目的

通过在 `ExtendedParser` 接口中添加 `findParser` 和 `getNextDelegateParser` 私有静态方法，递归遍历 parser 的委托链查找目标类型的 parser 实例。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/ExtendedParser.java` (+43/-3 lines)

**修改目的**：添加递归委托解包逻辑。

**工作逻辑**：

1. **修改 parseSortOrder 方法**：
```java
static List<RawOrderField> parseSortOrder(SparkSession spark, String orderString) {
-  if (spark.sessionState().sqlParser() instanceof ExtendedParser) {
-    ExtendedParser parser = (ExtendedParser) spark.sessionState().sqlParser();
+  ExtendedParser extParser = findParser(spark.sessionState().sqlParser(), ExtendedParser.class);
+  if (extParser != null) {
     try {
-      return parser.parseSortOrder(orderString);
+      return extParser.parseSortOrder(orderString);
```

不再直接检查 `instanceof`，而是调用 `findParser` 递归查找。

2. **新增 findParser 方法**：
```java
private static <T> T findParser(ParserInterface parser, Class<T> clazz) {
  ParserInterface current = parser;
  while (current != null) {
    if (clazz.isInstance(current)) {
      return clazz.cast(current);
    }
    current = getNextDelegateParser(current);
  }
  return null;
}
```

遍历委托链，对每个 parser 检查是否为目标类型。

3. **新增 getNextDelegateParser 方法**：
```java
private static ParserInterface getNextDelegateParser(ParserInterface parser) {
  try {
    Class<?> clazz = parser.getClass();
    while (clazz != null) {
      for (Field field : clazz.getDeclaredFields()) {
        field.setAccessible(true);
        Object value = field.get(parser);
        if (value instanceof ParserInterface && value != parser) {
          return (ParserInterface) value;
        }
      }
      clazz = clazz.getSuperclass();
    }
  } catch (Exception e) {
    log().warn("Failed to scan delegate parser in {}: ", parser.getClass().getName(), e);
  }
  return null;
}
```

通过反射扫描当前 parser 对象的所有字段（包括父类），查找类型为 `ParserInterface` 且不是自身的字段作为委托对象。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestExtendedParser.java` (new file, +236 lines)

**修改目的**：添加测试验证递归委托解包。

## 总结

这是一个 Spark 3.4 兼容性修复的回移植提交，通过递归遍历 parser 委托链解决了多层包装环境下找不到 `ExtendedParser` 的问题。使用反射扫描字段的方式具有一定的侵入性，但这是处理 Spark parser 包装链的常见模式。这一修复确保 Iceberg 的 SQL 扩展语法在各种 Spark 环境下都能正确解析。
