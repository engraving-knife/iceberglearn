# 提交 3282：Spark 4.1: Use enum conf parser for isolation level (#15361)

## 提交信息

- **序号**：3282 / 4088
- **哈希**：c51dda5447daa04a0964871bb2ff779ed95fee6e
- **短哈希**：c51dda544
- **日期**：2026-02-18
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Use enum conf parser for isolation level (#15361)
- **PR/Issue**：#15361

## 总体目的

本提交将 `SparkWriteConf.isolationLevel()` 的配置解析方式从手动字符串解析改为使用配置解析器内置的 `enumConf` 方法，使代码风格与项目中其他枚举类型配置的解析保持一致。在此之前，`isolationLevel()` 先用 `confParser.stringConf().option(...).parseOptional()` 取得字符串值，再手动判空并调用 `IsolationLevel.fromName(isolationLevelName)` 转换为 `IsolationLevel` 枚举。这种写法把"字符串到枚举"的转换逻辑散落在调用方，且需要额外的 null 判断，不如直接使用 `enumConf` 优雅。`IsolationLevel` 是 Iceberg 写入隔离级别（`SERIALIZABLE`、`SNAPSHOT`）的枚举，用于控制 row-level 命令（如 `DELETE`/`UPDATE`/`MERGE INTO`）在并发写入下的可见性与冲突检测行为。

## 如何达成设计目的

将 `isolationLevel()` 方法体替换为 `confParser.enumConf(IsolationLevel::fromName).option(SparkWriteOptions.ISOLATION_LEVEL).parseOptional()`，把字符串到枚举的转换通过方法引用 `IsolationLevel::fromName` 委托给 `enumConf` 解析器内部完成，无需手动判空与转换。改动仅涉及一个文件。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+4/-3 lines)

**修改目的**：用 `enumConf` 解析器替代手动字符串解析。

**工作逻辑**：
原实现先调用 `confParser.stringConf().option(SparkWriteOptions.ISOLATION_LEVEL).parseOptional()` 得到 `String isolationLevelName`，再三元判断 `isolationLevelName != null ? IsolationLevel.fromName(isolationLevelName) : null` 返回 `IsolationLevel`。新实现直接调用 `confParser.enumConf(IsolationLevel::fromName).option(SparkWriteOptions.ISOLATION_LEVEL).parseOptional()`：`enumConf` 接受一个 `Function<String, T>` 作为解析函数（此处为 `IsolationLevel::fromName`），在内部完成字符串到 `IsolationLevel` 的转换，当配置不存在时由 `parseOptional()` 返回 null。这样把枚举解析职责交还给配置解析器，消除了手动 null 检查，行为等价但更简洁、更符合项目惯例。

## 总结

本提交将 `SparkWriteConf` 中隔离级别的解析重构为使用 `enumConf` 解析器，消除了手动字符串解析与 null 判断，使代码更简洁并与项目中其他枚举配置的解析方式统一。
