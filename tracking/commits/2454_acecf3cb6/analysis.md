# 提交 2454：Spark: Print unknown catalog type in exception when configuring validation catalog (#13588)

## 提交信息

- **序号**：2454 / 4088
- **哈希**：acecf3cb697a7fa6bfcb1b58fc5d7d70914436e7
- **短哈希**：acecf3cb69
- **日期**：2025-08-05 09:29:58 +0200
- **作者**：Manu Zhang
- **提交说明**：Spark: Print unknown catalog type in exception when configuring validation catalog (#13588)
- **PR/Issue**：#13588

## 总体目的

该提交改进了 Spark 测试基类 `TestBaseWithCatalog` 中的错误诊断信息。在配置验证目录（validation catalog）时，如果遇到未知的目录类型，原有代码抛出的异常消息仅包含 "Unknown catalog type"，不包含实际收到的类型值。这使得开发者在排查配置问题时难以判断到底是哪个类型值未被识别。

该提交将实际的目录类型值添加到异常消息中，便于调试和问题定位。这是一个纯粹的可观测性/可调试性改进，不影响任何功能逻辑。

## 如何达成设计目的

通过以下方式改进错误信息：

1. 将 `catalogConfig.get(ICEBERG_CATALOG_TYPE)` 的结果提取到局部变量 `catalogType` 中，避免在 switch 语句和异常消息中重复调用。
2. 在 `default` 分支的异常消息中拼接实际的 `catalogType` 值：`"Unknown catalog type: " + catalogType`。

这种重构既减少了重复的 map 查找，又在异常消息中提供了关键的诊断信息。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java` (+2/-1 lines)

**修改目的**：在 Spark 3.4 测试基类中改进未知目录类型的异常消息。

**工作逻辑**：
```java
// 修改前
switch (catalogConfig.get(ICEBERG_CATALOG_TYPE)) {
    ...
    default:
        throw new IllegalArgumentException("Unknown catalog type");

// 修改后
String catalogType = catalogConfig.get(ICEBERG_CATALOG_TYPE);
switch (catalogType) {
    ...
    default:
        throw new IllegalArgumentException("Unknown catalog type: " + catalogType);
```

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java` (+2/-1 lines)

**修改目的**：对 Spark 3.5 测试基类应用相同的改进。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java` (+2/-1 lines)

**修改目的**：对 Spark 4.0 测试基类应用相同的改进。

## 总结

这是一个小型的测试基础设施改进提交，通过在未知目录类型的异常消息中包含实际的类型值，提升了测试失败时的可诊断性。修改覆盖了 Spark 3.4、3.5 和 4.0 三个版本，属于纯粹的错误信息增强，不影响任何功能逻辑。
