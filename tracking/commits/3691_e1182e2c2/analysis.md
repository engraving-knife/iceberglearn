# 提交 3691：Spark 4.1: Migrate SparkCopyOnWriteScan to SupportsRuntimeV2Filtering (#16295)

## 提交信息

- **序号**：3691 / 4088
- **哈希**：e1182e2c2a6f4282167f9c4a71889f107b99a011
- **短哈希**：e1182e2c2
- **日期**：2026-05-12 09:32:39 -0700
- **作者**：drexler-sky
- **提交说明**：Spark 4.1: Migrate SparkCopyOnWriteScan to SupportsRuntimeV2Filtering (#16295)
- **PR/Issue**：#16295

## 总体目的

这个提交将 Spark 4.1 模块中的 `SparkCopyOnWriteScan` 类从使用旧的 `SupportsRuntimeFiltering` 接口迁移到新的 `SupportsRuntimeV2Filtering` 接口。这是 Spark 4.x 数据源 API V2 演进的一部分。

`SupportsRuntimeFiltering` 使用 `org.apache.spark.sql.sources.Filter`（V1 API 的过滤类型），而 `SupportsRuntimeV2Filtering` 使用 `org.apache.spark.sql.connector.expressions.filter.Predicate`（V2 API 的过滤类型）。随着 Spark 4.1 对 V2 API 的推进，旧接口可能被废弃或移除，需要迁移到新接口以保持兼容性。

`SparkCopyOnWriteScan` 用于支持 Copy-on-Write 模式下的运行时过滤，主要场景是 UPDATE 操作中通过子查询过滤出需要修改的文件。Spark 在运行时会将文件路径的 IN 过滤条件推送到 scan 中。

## 如何达成设计目的

通过以下修改实现迁移：
1. 更改实现的接口从 `SupportsRuntimeFiltering` 到 `SupportsRuntimeV2Filtering`
2. 更改 `filter` 方法签名，从接收 `Filter[]` 改为接收 `Predicate[]`
3. 重构过滤逻辑，使用 V2 API 的 `Predicate`、`NamedReference`、`Literal` 类型替代 V1 API 的 `In` 类型
4. 新增两个辅助方法处理 V2 Predicate 的解析

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteScan.java` (+38/-16 lines)

**修改目的**：迁移到 SupportsRuntimeV2Filtering 接口。

**工作逻辑**：

1. **接口和导入变更**：
```java
-import org.apache.spark.sql.connector.read.SupportsRuntimeFiltering;
-import org.apache.spark.sql.sources.Filter;
-import org.apache.spark.sql.sources.In;
+import org.apache.spark.sql.connector.expressions.Literal;
+import org.apache.spark.sql.connector.expressions.filter.Predicate;
+import org.apache.spark.sql.connector.read.SupportsRuntimeV2Filtering;

class SparkCopyOnWriteScan extends SparkPartitioningAwareScan<FileScanTask>
-    implements SupportsRuntimeFiltering {
+    implements SupportsRuntimeV2Filtering {
```

2. **filter 方法重构**：
```java
-  public void filter(Filter[] filters) {
-    for (Filter filter : filters) {
-      if (filter instanceof In
-          && ((In) filter).attribute().equalsIgnoreCase(MetadataColumns.FILE_PATH.name())) {
-        In in = (In) filter;
-        Set<String> fileLocations = Sets.newHashSet();
-        for (Object value : in.values()) {
-          fileLocations.add((String) value);
-        }
+  public void filter(Predicate[] predicates) {
+    for (Predicate predicate : predicates) {
+      if (isFilePathInPredicate(predicate)) {
+        Set<String> fileLocations = extractStringLiterals(predicate);
```

3. **新增辅助方法**：

`isFilePathInPredicate` 方法检查 predicate 是否为文件路径的 IN 谓词：
```java
private static boolean isFilePathInPredicate(Predicate predicate) {
  if (!"IN".equals(predicate.name()) || predicate.children().length < 1) {
    return false;
  }
  if (!(predicate.children()[0] instanceof NamedReference)) {
    return false;
  }
  String[] fieldNames = ((NamedReference) predicate.children()[0]).fieldNames();
  return fieldNames.length == 1
      && fieldNames[0].equalsIgnoreCase(MetadataColumns.FILE_PATH.name());
}
```

`extractStringLiterals` 方法从 IN 谓词中提取字符串字面量：
```java
private static Set<String> extractStringLiterals(Predicate predicate) {
  Set<String> values = Sets.newHashSet();
  for (int i = 1; i < predicate.children().length; i++) {
    if (predicate.children()[i] instanceof Literal) {
      Object value = ((Literal<?>) predicate.children()[i]).value();
      // V2 string literals come through as UTF8String; toString() materializes the Java String
      values.add(value.toString());
    }
  }
  return values;
}
```

V2 API 中，IN 谓词的结构是 `Predicate("IN", [NamedReference, Literal1, Literal2, ...])`，第一个子节点是字段引用，后续子节点是字面量值。注意 V2 的字符串字面量以 UTF8String 形式传递，需要通过 `toString()` 转换为 Java String。

## 总结

这是一个 Spark 4.1 API 适配提交，将 Copy-on-Write 扫描的运行时过滤从旧的 V1 Filter API 迁移到新的 V2 Predicate API。这一迁移确保 Iceberg 与 Spark 4.1 的最新数据源 API 保持兼容。新接口的 Predicate 结构更加统一和可扩展，通过 children 数组表示谓词的组成部分。
