# 提交 3761：Build: Ban Preconditions.checkState with %d placeholder (#16407)

## 提交信息

- **序号**：3761 / 4088
- **哈希**：2f05390e0730200866f52a7e8585480bd6c4596e
- **短哈希**：2f05390e0
- **日期**：2026-05-21 09:34:00 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Build: Ban Preconditions.checkState with %d placeholder (#16407)
- **PR/Issue**：#16407

## 总体目的

这个提交修复了代码中 `Preconditions.checkState` 使用 `%d` 占位符的问题，并通过 Checkstyle 规则禁止未来再使用 `%d`。Iceberg 使用的 `Preconditions` 类（来自 relocated Guava）的 `checkState` 和 `checkArgument` 方法只支持 `%s` 占位符，不支持 `%d`。使用 `%d` 会导致错误消息中的数字参数无法正确替换，在运行时抛出异常时显示错误的格式化结果。

之前已有规则禁止 `Preconditions.checkArgument` 使用 `%d`，但遗漏了 `checkState`，这个提交补上了这个漏洞。

## 如何达成设计目的

1. 在 Checkstyle 配置中新增一条 `RegexpMultiline` 规则，匹配 `Preconditions.checkState` 调用中包含 `%d` 的代码，并给出明确的错误提示信息。
2. 修复现有代码中所有违反该规则的调用，将 `%d` 替换为 `%s`。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (+6/-0 lines)

**修改目的**：新增 Checkstyle 规则禁止 `Preconditions.checkState` 使用 `%d`。

**工作逻辑**：
新增一个 `RegexpMultiline` 模块，配置 `matchAcrossLines=true` 以支持跨行匹配（因为 `checkState` 调用经常跨多行），正则表达式为 `Preconditions\.checkState\([^;]+%d[^;]+\);`，错误消息为 `"Preconditions.checkState does not support %d. use %s instead"`。这与已有的 `checkArgument` 规则保持一致。

### `api/src/main/java/org/apache/iceberg/PartitionStatistics.java` (+1/-1 lines)

**修改目的**：修复 `checkState` 中的 `%d` 占位符。

**工作逻辑**：将 `"Invalid format version: %d"` 改为 `"Invalid format version: %s"`。

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+1/-1 lines)

**修改目的**：修复 `checkState` 中的 `%d` 占位符。

**工作逻辑**：将 `"Invalid format version: %d"` 改为 `"Invalid format version: %s"`。

### `core/src/main/java/org/apache/iceberg/encryption/AesGcmInputFile.java` (+1/-1 lines)

**修改目的**：修复 `checkState` 中的 `%d` 占位符。

**工作逻辑**：将 `"Invalid encrypted stream: %d is shorter than..."` 改为 `%s`。

### `core/src/main/java/org/apache/iceberg/encryption/AesGcmInputStream.java` (+1/-1 lines)

**修改目的**：修复 `checkState` 中的两个 `%d` 占位符。

**工作逻辑**：将 `"Invalid GCM stream: block size %d != %d"` 改为 `"Invalid GCM stream: block size %s != %s"`。

### `flink/v1.20/flink/src/main/java/.../BaseCoordinator.java` (+1/-1 lines)
### `flink/v1.20/flink/src/main/java/.../DataStatisticsCoordinator.java` (+2/-2 lines)
### `flink/v2.0/flink/src/main/java/.../BaseCoordinator.java` (+1/-1 lines)
### `flink/v2.0/flink/src/main/java/.../DataStatisticsCoordinator.java` (+2/-2 lines)
### `flink/v2.1/flink/src/main/java/.../BaseCoordinator.java` (+1/-1 lines)
### `flink/v2.1/flink/src/main/java/.../DataStatisticsCoordinator.java` (+2/-2 lines)

**修改目的**：修复三个 Flink 版本（v1.20、v2.0、v2.1）中 Coordinator 类的 `%d` 占位符。

**工作逻辑**：
- `BaseCoordinator` 中将 `"Coordinator of %s already has a subtask gateway for (#%d)"` 改为 `(#%s)`。
- `DataStatisticsCoordinator` 中两处类似修改：`"for %d (#%d)"` 改为 `"for %s (#%s)"`，`"subtask %d is not ready"` 改为 `"subtask %s is not ready"`。

## 总结

这个提交通过添加 Checkstyle 规则并修复所有现有违规代码，确保 `Preconditions.checkState` 调用中不再使用不支持的 `%d` 占位符。这是一个代码质量改进，防止了因占位符不匹配导致的错误消息格式化问题，同时通过静态检查防止问题再次出现。修改覆盖了 api、core 和三个 Flink 版本模块中的所有相关代码。
