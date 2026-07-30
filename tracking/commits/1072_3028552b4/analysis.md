# 提交 1072：Prevent implicit default locale/charset usage (#10969)

## 提交信息

- **序号**：1072 / 4088
- **哈希**：3028552b41d672fa94cbc5a037481db55dd6601c
- **短哈希**：3028552b4
- **日期**：2024-08-20 13:50:00 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Prevent implicit default locale/charset usage (#10969)
- **PR/Issue**：#10969

## 总体目的

在 Java 平台上，存在大量"隐式依赖系统默认 locale 或字符集（charset）"的 API，例如 `new String(byte[])`、`String.toLowerCase()`、`String.toUpperCase()`、`String.getBytes()` 等不带显式 locale/charset 参数的方法。这些方法的行为依赖 JVM 运行环境的 `user.language`、`user.country`、`file.encoding` 等系统属性，导致同一段代码在不同机器、不同容器（Docker 镜像基础 OS 不同）、不同区域设置的 CI/生产环境下可能产生不同结果，给数据正确性和跨环境一致性带来隐患。

对于 Iceberg 这样一个跨多种执行引擎（Spark、Flink、Trino、Kafka Connect 等）、跨多种部署环境的表格式库而言，这种隐式依赖尤其危险。例如 `toLowerCase()` 在土耳其语 locale 下会将大写 `I` 转为 `ı`（无点），从而导致表名/字段名匹配错误；`new String(byte[])` 在不同 JVM 默认 charset 下会得到不同字符串，破坏 manifest、partition data 等二进制结构的解析正确性。

本提交的目的是通过开启 error-prone 静态检查工具的 `DefaultCharset` 和 `DefaultLocale` 两个规则（设为 ERROR 级别），在编译期就禁止代码中出现对默认 locale/charset 的隐式使用，并修复现有代码中两处违反规则的调用，从而保证代码行为在所有环境下完全确定、可复现。

## 如何达成设计目的

设计思路分两步：

1. **开启编译期检查**：在 `baseline.gradle` 中向 error-prone 检查列表新增两条规则：`DefaultCharset:ERROR` 和 `DefaultLocale:ERROR`，使其在每个子项目的编译阶段被强制执行。一旦开启，任何使用默认 charset 或 locale 的代码都会让编译直接失败，从而在源头阻断此类问题。

2. **修复现有违规代码**：扫描代码库找出当前两处违规并改为显式形式：
   - `MapRangePartitionerBenchmark` 中将 `byte[]` 转 `String` 时未指定字符集，改为显式指定 `StandardCharsets.US_ASCII`；
   - `SinkWriter` 中 `toLowerCase()` 未指定 locale，改为显式指定 `Locale.ROOT`，保证不受运行环境 locale 影响。

## 修改详情

### `baseline.gradle`

**修改目的**：在 error-prone 静态检查规则集中开启 `DefaultCharset` 和 `DefaultLocale` 两条 ERROR 级别检查，编译期阻止代码隐式使用系统默认 charset 或 locale。

**工作逻辑**：在 `subprojects` 块的 error-prone 规则列表中（按字母顺序）插入两行：

```diff
           '-Xep:DangerousJavaDeserialization:ERROR',
           '-Xep:DangerousThreadPoolExecutorUsage:OFF',
+          '-Xep:DefaultCharset:ERROR',
+          '-Xep:DefaultLocale:ERROR',
           // subclasses are not equal
           '-Xep:EqualsGetClass:OFF',
```

- `DefaultCharset:ERROR`：禁止 `new String(byte[])`、`String.getBytes()`、`InputStreamReader`/`OutputStreamWriter` 无 charset 参数的构造等隐式使用平台默认字符集的调用；
- `DefaultLocale:ERROR`：禁止 `String.toLowerCase()`/`toUpperCase()`、`String.format()`/`printf()` 无 locale 参数等隐式使用默认 locale 的调用。

启用 ERROR 级别意味着违规代码会导致编译失败，是 Iceberg 项目一贯采用的"零容忍"防御式编码策略。

### `flink/v1.19/flink/src/jmh/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitionerBenchmark.java`

**修改目的**：修复 JMH 基准测试中字节数组转字符串时隐式使用默认字符集的违规，改为显式使用 US-ASCII。

**工作逻辑**：在生成随机键字符串的工具方法中，将 `new String(buffer)` 改为 `new String(buffer, StandardCharsets.US_ASCII)`：

```diff
-    return prefix + new String(buffer);
+    return prefix + new String(buffer, StandardCharsets.US_ASCII);
```

`buffer` 中的字节是从 `CHARS`（仅含 ASCII 字符）中随机选取填入的，因此使用 US-ASCII 字符集在语义上完全等价且最准确。显式指定字符集后，无论 JVM 默认 charset 是 UTF-8、ISO-8859-1 还是其他，都能得到一致的解码结果，保证 benchmark 数据生成稳定。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SinkWriter.java`

**修改目的**：修复 Kafka Connect Sink 写入器中对路由表名做小写转换时隐式使用默认 locale 的违规，改为显式使用 `Locale.ROOT`。

**工作逻辑**：

```diff
-      String tableName = routeValue.toLowerCase();
+      String tableName = routeValue.toLowerCase(Locale.ROOT);
```

`routeValue` 来自 Kafka 记录中的字段值，用于根据字段值路由到不同 Iceberg 表。`toLowerCase()` 不带 locale 时使用默认 locale，在土耳其语等环境下会出现 `I → ı`（点变无点）的异常转换，可能导致表名错误匹配、数据写入错误的目标表。改用 `Locale.ROOT`（语言中立 locale）后，转换规则固定为标准 Unicode 小写规则，保证跨环境一致。同时新增了 `import java.util.Locale`。

## 小结

- **成效**：成功在编译期启用了 `DefaultCharset` 和 `DefaultLocale` 两条 error-prone ERROR 级别规则，并修复了已有 2 处违规调用，使代码库不再有隐式依赖默认 locale/charset 的代码，跨环境行为完全可预测。
- **影响范围**：3 个文件共 6 行改动：构建脚本 `baseline.gradle`、Flink v1.19 的 JMH benchmark、Kafka Connect 模块的 SinkWriter。开启的两条规则后续会作用于所有子项目编译。
- **回迁到 1.4.x 的注意事项**：本提交属于代码质量改进，适合回迁到 1.4.x 维护分支以避免后续 patch 版本出现新的 locale/charset 相关 bug。回迁时需确认 1.4.x 分支的 `baseline.gradle` 中 error-prone 规则列表位置与 main 分支一致；同时由于 1.4.x 分支可能没有 `flink/v1.19` 模块或该模块的 benchmark 文件路径不同（1.4.x 主要对应 Flink 1.17/1.18/1.19，需检查），如果对应文件不存在可仅回迁 `baseline.gradle` 和 `SinkWriter.java` 部分。注意：开启 ERROR 级别后，1.4.x 分支中其它未修复的违规会导致编译失败，需先扫描该分支是否还有其它隐式调用并一并修复。
