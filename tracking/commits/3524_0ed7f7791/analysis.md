# 提交 3524：Flink: Fix checkArgument message for flink streaming (#15907)

## 提交信息

- **序号**：3524 / 4088
- **哈希**：0ed7f7791f5179a924f8c7772428587c1d8cbdfe
- **短哈希**：0ed7f7791
- **日期**：2026-04-13 09:33:16 -0700
- **作者**：genxiong7
- **提交说明**：Flink: Fix checkArgument message for flink streaming (#15907)
- **PR/Issue**：#15907

## 总体目的

`ScanContext` 在校验 Flink 流式读取的起始策略参数时，错误消息中硬编码了策略名称 `SPECIFIC_START_SNAPSHOT_ID` 和 `SPECIFIC_START_SNAPSHOT_TIMESTAMP`，但这些枚举值实际并不存在——真正的枚举名是 `INCREMENTAL_FROM_SNAPSHOT_ID` 和 `INCREMENTAL_FROM_SNAPSHOT_TIMESTAMP`。这导致抛出的异常消息与实际策略不匹配，用户排错时会被误导。

更糟糕的是，在 `INCREMENTAL_FROM_SNAPSHOT_TIMESTAMP` 分支中校验 `startSnapshotId == null` 时，错误消息里写的是 `SPECIFIC_START_SNAPSHOT_ID`，与当前正在校验的策略完全无关，混淆性更强。

本提交将硬编码的策略名替换为通过 `%s` 占位符动态插入实际的 `startingStrategy` 枚举值，保证错误消息始终准确反映当前校验的策略。

## 如何达成设计目的

利用 `Preconditions.checkArgument` 支持的 SLF4J 风格 `{}`/`%s` 格式化参数，将策略名作为参数传入：
```java
Preconditions.checkArgument(
    startSnapshotId != null,
    "Invalid starting snapshot id for %s strategy: null",
    startingStrategy);
```
这样无论将来策略枚举名如何变化，错误消息都自动跟随。同时把一个原本用 `String.format` 包裹的 checkArgument 改为使用 Preconditions 内置的格式化能力，风格统一。改动同步应用到 Flink v1.20、v2.0、v2.1 三个版本分支，以及对应的测试断言。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java` (+9/-6 lines)
**修改目的**：修正流式读取策略校验的错误消息，使用动态策略名。

**工作逻辑**：四处 `Preconditions.checkArgument` 的消息从硬编码策略名改为 `%s` + `startingStrategy` 参数：
```java
-            "Invalid starting snapshot id for SPECIFIC_START_SNAPSHOT_ID strategy: null");
+            "Invalid starting snapshot id for %s strategy: null",
+            startingStrategy);
```
另外把 `tag` 校验从 `String.format(...)` 改为 `Preconditions` 原生格式化：
```java
-          tag == null,
-          String.format("Cannot scan table using ref %s configured for streaming reader", tag));
+          tag == null, "Cannot scan table using ref %s configured for streaming reader", tag);
```

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestScanContext.java` (+5/-4 lines)
**修改目的**：同步更新测试中断言的异常消息，匹配新的动态策略名。

**工作逻辑**：把期望消息从 `SPECIFIC_START_SNAPSHOT_ID` 改为 `INCREMENTAL_FROM_SNAPSHOT_ID`，`SPECIFIC_START_SNAPSHOT_TIMESTAMP` 改为 `INCREMENTAL_FROM_SNAPSHOT_TIMESTAMP`，验证修复后的正确消息。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java` (+9/-6 lines)
**修改目的**：Flink 2.0 版本同步修复（与 v1.20 改动完全相同）。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/source/TestScanContext.java` (+5/-4 lines)
**修改目的**：Flink 2.0 测试同步更新。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/source/ScanContext.java` (+9/-6 lines)
**修改目的**：Flink 2.1 版本同步修复（与 v1.20 改动完全相同）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/source/TestScanContext.java` (+5/-4 lines)
**修改目的**：Flink 2.1 测试同步更新。

## 总结

本提交修复了 Flink 流式读取 `ScanContext` 中错误消息引用了不存在的策略枚举名的 bug，改用动态格式化注入实际策略名，确保异常消息准确。同时统一了 `Preconditions` 的格式化风格。改动跨 Flink v1.20/v2.0/v2.1 三个版本同步应用，并更新对应测试断言，属于提升错误诊断体验的改进。
