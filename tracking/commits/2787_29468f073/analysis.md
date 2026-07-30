# 提交 2787：Core, Flink: Remove dependency on Hadoop Sets, Lists and Preconditions classes (#14405)

## 提交信息

- **序号**：2787 / 4088
- **哈希**：29468f0731a97594b0a1230e8ccd259d152de492
- **短哈希**：29468f073
- **日期**：2025-10-23 14:38:14 +0200
- **作者**：aiborodin
- **提交说明**：Core, Flink: Remove dependency on Hadoop Sets, Lists and Preconditions classes (#14405)
- **PR/Issue**：#14405

## 总体目的

本提交将 Iceberg 代码中对 Hadoop 工具类（`org.apache.hadoop.util.Sets`、`org.apache.hadoop.util.Lists`、`org.apache.hadoop.util.Preconditions`）的依赖替换为 Iceberg 自带的 relocated Guava 类。

Hadoop 提供了一些与 Guava 类似的工具类（`Sets`、`Lists`、`Preconditions`），但 Iceberg 项目已经通过 `bundled-guava` 模块提供了 relocated 版本的 Guava 类（`org.apache.iceberg.relocated.com.google.common.collect.Sets`、`Lists`、`base.Preconditions`）。使用 Hadoop 的工具类存在以下问题：

1. **不必要的依赖耦合**：代码仅因为工具类而依赖 Hadoop 的某些模块，增加了依赖链的复杂度。
2. **版本冲突风险**：Hadoop 不同版本中这些工具类的行为可能略有差异，而 relocated Guava 版本由 Iceberg 自己控制。
3. **一致性**：项目应统一使用同一套工具类，避免混用 Hadoop 和 Guava 的实现。

本提交将所有使用 `org.apache.hadoop.util.Sets`/`Lists`/`Preconditions` 的地方替换为对应的 relocated Guava 类，并添加 checkstyle 规则防止未来再次引入 Hadoop 工具类依赖。

## 如何达成设计目的

1. **添加 checkstyle 规则**：在 `checkstyle.xml` 中新增 `BanHadoopUtils` 模块，使用正则匹配禁止 import `org.apache.hadoop.util.Sets`、`org.apache.hadoop.util.Lists`、`org.apache.hadoop.util.Preconditions`，错误消息提示使用 `org.apache.iceberg.relocated.*` 类。

2. **替换 import**：在 core 和 flink（v1.20/v2.0/v2.1）模块中，将所有 `import org.apache.hadoop.util.Sets` 替换为 `import org.apache.iceberg.relocated.com.google.common.collect.Sets`，将 `import org.apache.hadoop.util.Preconditions` 替换为 `import org.apache.iceberg.relocated.com.google.common.base.Preconditions`。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (+6/-0 lines)

**修改目的**：添加 checkstyle 规则禁止使用 Hadoop 工具类。

**工作逻辑**：新增 `IllegalImport` 模块，id 为 `BanHadoopUtils`，使用正则 `^org\.apache\.hadoop\.util\.(Sets|Lists|Preconditions)` 匹配非法 import，错误消息为 "Use org.apache.iceberg.relocated.* classes from bundled-guava module instead."

### `core/src/main/java/org/apache/iceberg/rest/ParserContext.java` (+1/-1 lines)

**修改目的**：将 Hadoop Preconditions 替换为 relocated Guava Preconditions。

**工作逻辑**：`import org.apache.hadoop.util.Preconditions` 改为 `import org.apache.iceberg.relocated.com.google.common.base.Preconditions`。

### Flink 模块（v1.20/v2.0/v2.1）各 5 个文件（共 15 个文件，每文件 +1/-1 lines）

**修改目的**：将 Flink dynamic sink 模块中的 Hadoop Sets 替换为 relocated Guava Sets。

**工作逻辑**：在三个 Flink 版本的以下文件中，将 `import org.apache.hadoop.util.Sets` 替换为 `import org.apache.iceberg.relocated.com.google.common.collect.Sets`：
- `DynamicRecordInternalSerializer.java`
- `DynamicSinkUtil.java`
- `WriteTarget.java`
- `TestDynamicWriteResultAggregator.java`
- `TestDynamicWriteResultSerializer.java`

## 总结

本提交将 Iceberg 代码中对 Hadoop 工具类（Sets、Lists、Preconditions）的依赖统一替换为 Iceberg 自带的 relocated Guava 类，减少了不必要的 Hadoop 依赖耦合。通过添加 checkstyle 规则确保未来不会再次引入 Hadoop 工具类。这是一个代码质量改进，使项目的工具类使用更加一致和可控。修改覆盖 core 和三个 Flink 版本（1.20/2.0/2.1）共 17 个文件。
