# 提交 1348：Flink: Fix config key typo in error message of SplitComparators (#11482)

## 提交信息

- **序号**：1348 / 4088
- **哈希**：1e82c476df58ba467342eec98cb02ec0e2c75bd1
- **短哈希**：1e82c476d
- **日期**：2024-11-06（Wed Nov 6 14:45:41 2024 -0800）
- **作者**：Mingliang Liu <liuml07@apache.org>
- **提交说明**：Flink: Fix config key typo in error message of SplitComparators (#11482)
- **PR/Issue**：#11482

## 总体目的

`SplitComparators` 是 Flink Iceberg source 的工具类，提供按文件序列号、watermark 等对 `IcebergSourceSplit` 进行排序的比较器。其中 `forFileSequenceNumber` 在比较两个 split 时，要求每个 split 只包含一个文件（即不能是 combined task），否则抛出 `IllegalArgumentException`，错误信息提示用户调整某个 read option 来防止 Iceberg 把多个文件合并到一个 split。

错误信息中原本写的是 `'split-open-file-cost'`，但 `FlinkReadOptions.SPLIT_FILE_OPEN_COST` 实际定义的配置键是 `"split-file-open-cost"`（参见 `flink/v1.16+/flink/src/main/java/org/apache/iceberg/flink/FlinkReadOptions.java` 中 `public static final String SPLIT_FILE_OPEN_COST = "split-file-open-cost";`）。这是一个词序错误：`split-open-file-cost` vs 正确的 `split-file-open-cost`。

用户按错误信息去设置 `'split-open-file-cost'` 不会生效，反而会困惑为何配置不工作。本提交修正这一拼写错误，并用 `Preconditions.checkArgument` 的格式化能力直接引用 `FlinkReadOptions.SPLIT_FILE_OPEN_COST` 常量，避免后续再出现"消息与常量不一致"的问题。修改覆盖 Flink 1.18/1.19/1.20 三个版本的代码与对应测试。

## 如何达成设计目的

- **直接修正字符串**：把错误信息中硬编码的 `'split-open-file-cost'` 改为通过 `%s` 占位符输出 `FlinkReadOptions.SPLIT_FILE_OPEN_COST`（即 `"split-file-open-cost"`）。
- **使用常量引用**：新增 import `org.apache.iceberg.flink.FlinkReadOptions`，在 `Preconditions.checkArgument` 的消息模板里用 `'%s'` 占位，第二个参数传入 `FlinkReadOptions.SPLIT_FILE_OPEN_COST`。这样未来如果常量值变更，错误信息会自动跟随。
- **同步更新测试断言**：三个版本的 `TestFileSequenceNumberBasedSplitAssigner` 中原本断言 `.hasMessageContaining("Please use 'split-open-file-cost'")`，改为 `"Please use 'split-file-open-cost'"`。
- **跨版本同步**：在 Flink 1.18/1.19/1.20 三个目录下应用完全相同的改动，保证各 Flink 版本行为一致。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/split/SplitComparators.java`（以及 1.19、1.20 同名文件）

**修改目的**：修正 `forFileSequenceNumber` 比较器中错误信息里的配置键拼写。

**工作逻辑**：

新增 import：
```java
import org.apache.iceberg.flink.FlinkReadOptions;
```

原代码：
```java
Preconditions.checkArgument(
    o1.task().files().size() == 1 && o2.task().files().size() == 1,
    "Could not compare combined task. Please use 'split-open-file-cost' to prevent combining multiple files to a split");
```

改为：
```java
Preconditions.checkArgument(
    o1.task().files().size() == 1 && o2.task().files().size() == 1,
    "Could not compare combined task. Please use '%s' to prevent combining multiple files to a split",
    FlinkReadOptions.SPLIT_FILE_OPEN_COST);
```

即用 `Preconditions.checkArgument` 的格式化变体，直接引用常量。Guava 的 `Preconditions.checkArgument(boolean, String, Object...)` 支持 `%s` 占位符。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/assigner/TestFileSequenceNumberBasedSplitAssigner.java`（以及 1.19、1.20 同名文件）

**修改目的**：同步修正测试断言中的字符串。

**工作逻辑**：
```java
// 原
.hasMessageContaining("Please use 'split-open-file-cost'");
// 改为
.hasMessageContaining("Please use 'split-file-open-cost'");
```

## 小结

- **成效**：Flink Iceberg source 的 `SplitComparators.forFileSequenceNumber` 在抛出"split 包含多个文件"异常时，错误信息现正确指向配置键 `split-file-open-cost`，且通过引用常量避免未来再次不一致。三个支持的 Flink 版本（1.18/1.19/1.20）同步修复。
- **影响范围**：仅 Flink 模块的错误信息字符串与对应测试断言，无功能逻辑变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个纯错误信息修复，不影响任何运行时行为，**建议回迁**到 1.4.x（如果 1.4.x 支持的 Flink 版本中有同样拼写错误）。
  - 1.4.x 通常对应较早的 Flink 版本（如 1.15/1.16/1.17/1.18）。需先确认 1.4.x 的 `flink/v1.X/flink/.../SplitComparators.java` 是否存在同样的拼写错误；如果存在，按相同方式修正。
  - 注意 1.4.x 的 `FlinkReadOptions.SPLIT_FILE_OPEN_COST` 常量名应与 main 一致（实际值都是 `"split-file-open-cost"`），如不一致需使用对应分支的实际常量名。
  - 改动小、无依赖，cherry-pick 安全。
