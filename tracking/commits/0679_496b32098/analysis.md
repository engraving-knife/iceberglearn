# 提交 0679：Flink, Spark: Replace Boolean.getBoolean() with Boolean.parseBoolean()

## 提交信息
- **序号**：0679 / 4088
- **哈希**：496b32098587d80ad5812b24598c98ceb6625ab7
- **短哈希**：496b32098
- **日期**：2024-04-14 05:19:55 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Flink, Spark: Replace Boolean.getBoolean() with Boolean.parseBoolean() (#10136)
- **PR/Issue**：#10136

## 总体目的

本提交修复了一个由 Java API 误用导致的潜在 bug：在 Flink 与 Spark 模块的多处代码中，错误地使用了 `Boolean.getBoolean(String)` 来解析字符串布尔值，而应使用的是 `Boolean.parseBoolean(String)`。这两个方法语义截然不同，误用会导致布尔值解析结果错误。

**`Boolean.getBoolean(String name)` 的真实语义**：该方法并非"把字符串解析为布尔值"，而是"查找名为 `name` 的 JVM 系统属性（System Property），若该系统属性存在且其值（忽略大小写）等于 `"true"`，则返回 `true`，否则返回 `false`"。也就是说，`Boolean.getBoolean("true")` 的含义是：检查是否存在名为 `"true"` 的系统属性且其值为 `"true"`——除非启动时显式加了 `-Dtrue=true`，否则永远返回 `false`。这与"把字符串 `"true"` 解析为布尔值 `true`"完全是两回事。

**`Boolean.parseBoolean(String s)` 的语义**：把字符串参数解析为布尔值。当字符串（忽略大小写）等于 `"true"` 时返回 `true`，否则返回 `false`。这才是"把字符串解析为布尔值"的正确方法。

这个误用造成了两类问题：

1. **Flink 测试中的活跃 bug**（`TestFlinkSource`，v1.16/v1.17/v1.18 三处）：测试从 options map 中取出 `"case-sensitive"` 配置值（如 `"true"` 或 `"false"` 字符串），调用 `builder.caseSensitive(Boolean.getBoolean(value))`。由于 `Boolean.getBoolean("true")` 实际检查的是名为 `"true"` 的系统属性（几乎不存在），返回 `false`；`Boolean.getBoolean("false")` 检查名为 `"false"` 的系统属性，也返回 `false`。结果是无论 options 中 `case-sensitive` 设为 `true` 还是 `false`，传给 builder 的都是 `false`，测试永远在 case-insensitive 模式下运行，case-sensitive 路径从未被真正测试到。这是一个测试有效性 bug——测试看似覆盖了两种模式，实际只覆盖了一种。

2. **Spark 生产代码中的潜在 bug**（`SparkTableUtil`，v3.3/v3.4/v3.5 三处）：`PropertyUtil.propertyAsBoolean(table.properties(), WRITE_AUDIT_PUBLISH_ENABLED, Boolean.getBoolean(WRITE_AUDIT_PUBLISH_ENABLED_DEFAULT))`。这里 `WRITE_AUDIT_PUBLISH_ENABLED_DEFAULT` 是一个常量字符串（默认值，通常为 `"false"`），本意是"当表属性中未设置 `write-audit-publish.enabled` 时，用该默认字符串解析出布尔默认值"。但 `Boolean.getBoolean("false")` 检查的是名为 `"false"` 的系统属性，返回 `false`；恰好默认值就是 `false`，所以"歪打正着"地得到了正确结果。这是一个**潜伏 bug**（latent bug）：目前因为默认值是 `false` 而碰巧结果正确，但语义完全错误——如果将来默认值常量改为 `"true"`，`Boolean.getBoolean("true")` 仍会返回 `false`（除非设了系统属性），导致默认值失效，write-audit-publish 永远无法默认启用。即便不改默认值，这也违反了代码意图，且若用户设置了 `-Dfalse=true` 之类异常系统属性，还会改变行为，是不可接受的语义错误。

## 如何达成设计目的

修复策略是机械替换：在所有 6 处误用点，把 `Boolean.getBoolean(...)` 改为 `Boolean.parseBoolean(...)`，参数不变。替换后：

- Flink 测试中：`Boolean.parseBoolean(value)` 正确解析 options 中的字符串，`"true"`→`true`、`"false"`→`false`，case-sensitive 的两种模式都能被测试覆盖。
- Spark 生产代码中：`Boolean.parseBoolean(WRITE_AUDIT_PUBLISH_ENABLED_DEFAULT)` 正确解析默认值常量字符串为布尔值，语义与意图一致，消除潜伏 bug。

修改分布在 Flink 1.16/1.17/1.18 三个版本的同一测试文件（`TestFlinkSource.java`），以及 Spark 3.3/3.4/3.5 三个版本的同一工具类（`SparkTableUtil.java`），每处仅一行改动。

## 修改详情

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSource.java`
### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSource.java`
### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestFlinkSource.java`
**修改目的**：修复 case-sensitive 配置解析 bug，使测试能真正覆盖 case-sensitive 与 case-insensitive 两种模式。
**工作逻辑**：在 `runWithOptions` 方法中，`.ifPresent(value -> builder.caseSensitive(Boolean.getBoolean(value)))` 改为 `.ifPresent(value -> builder.caseSensitive(Boolean.parseBoolean(value)))`。`value` 来自 `options.get("case-sensitive")`，是用户传入的字符串。修复后，`parseBoolean` 正确将 `"true"`/`"false"` 字符串转为对应布尔值，测试逻辑与配置意图一致。三个 Flink 版本的修改完全相同。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`
### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`
### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`
**修改目的**：修复 write-audit-publish 默认值解析的潜伏 bug，使默认值常量被正确解析为布尔值。
**工作逻辑**：在 `wapEnabled`（或等价方法）中，`Boolean.getBoolean(TableProperties.WRITE_AUDIT_PUBLISH_ENABLED_DEFAULT)` 改为 `Boolean.parseBoolean(TableProperties.WRITE_AUDIT_PUBLISH_ENABLED_DEFAULT)`。`WRITE_AUDIT_PUBLISH_ENABLED_DEFAULT` 是默认值字符串常量，`parseBoolean` 直接按字符串内容解析，不依赖任何系统属性，语义正确。当前默认值为 `false`，修复前后行为一致（都返回 false）；但修复消除了语义错误与未来默认值变更时的隐患。三个 Spark 版本的修改完全相同。

## 小结
- **成效**：成功达成目的。Flink 测试的 case-sensitive 路径现在能被真正测试；Spark 的 wap 默认值解析语义正确，潜伏 bug 消除。
- **影响范围**：Flink 1.16/1.17/1.18 测试代码（`TestFlinkSource`），Spark 3.3/3.4/3.5 生产代码（`SparkTableUtil`）。Flink 侧改动仅影响测试有效性；Spark 侧改动当前不改变运行时行为（默认值碰巧为 false），但修复了语义错误。
- **回迁到 1.4.x 的注意事项**：
  - 该 bug 在 1.4.x 同样存在（若 1.4.x 基于这些版本），回迁是有益的纯修复，风险极低。
  - 需确认 1.4.x 各 Flink/Spark 版本目录结构是否与 main 一致；若 1.4.x 支持的 Flink/Spark 版本集合不同（如少了 v1.16 或多了其他版本），需对相应版本目录做同样修改。
  - Spark 侧改动当前不改变行为，但建议回迁以避免未来默认值变更时引入 bug。
  - 确认 1.4.x 的 `WRITE_AUDIT_PUBLISH_ENABLED_DEFAULT` 常量值与 main 一致，若不同需单独评估。
