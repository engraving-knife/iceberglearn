# 提交 3456：Core: Add VisibleForTesting annotation in SchemaUpdate constructor (#15756)

## 提交信息

- **序号**：3456 / 4088
- **哈希**：050378663b3513499dc7900d8d368ec07336309d
- **短哈希**：050378663b
- **日期**：2026-03-24 17:26:23 -0700
- **作者**：Mukund Thakur
- **提交说明**：Core: Add VisibleForTesting annotation in SchemaUpdate constructor (#15756)
- **PR/Issue**：#15756

## 总体目的

在 `SchemaUpdate` 的测试构造函数上添加 `@VisibleForTesting` 注解，替代原有的 `/** For testing only. */` Javadoc 注释。`@VisibleForTesting` 是 Guava 提供的注解，用于标记仅因测试需要而暴露的方法或构造函数，使意图更加明确且可被静态分析工具识别。

## 如何达成设计目的

- 导入 `VisibleForTesting` 注解
- 将 Javadoc 注释 `/** For testing only. */` 替换为 `@VisibleForTesting` 注解

## 修改详情

### `core/src/main/java/org/apache/iceberg/SchemaUpdate.java` (+2/-1 lines)

**修改目的**：用 `@VisibleForTesting` 注解替代 Javadoc 注释。

**工作逻辑**：
- 新增 import：`org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting`
- 将构造函数上的 `/** For testing only. */` 替换为 `@VisibleForTesting` 注解

```java
@VisibleForTesting
SchemaUpdate(Schema schema, int lastColumnId) {
    this(null, null, schema, lastColumnId);
}
```

## 总结

该提交是一个小的代码质量改进，将 `SchemaUpdate` 测试构造函数上的 Javadoc 注释替换为 Guava 的 `@VisibleForTesting` 注解，使测试可见性的意图更加明确和可被工具识别。
