# 提交 4031：Spark: Add session-level split size override (#16154)

## 提交信息

- **序号**：4031 / 4088
- **哈希**：f1c5c188144a7d7d12b05bf313d061d80064c3e4
- **短哈希**：f1c5c1881
- **日期**：2026-07-14 11:26:56 -0700
- **作者**：Gera Shegalov
- **提交说明**：Spark: Add session-level split size override (#16154)
- **PR/Issue**：#16154

## 总体目的

本提交为 Spark Iceberg 读取添加会话级别的 split 大小覆盖（split size override）能力，新增 Spark SQL 配置 `spark.sql.iceberg.read.split-size`。

背景：Iceberg 的 split 大小（目标分片大小）原本可通过表属性 `read.split.size` 或读选项 `split-size` 设置，但缺少会话级别的全局覆盖。用户若想在不同 Spark 会话或临时查询中调整 split 大小，需要修改表属性或在每个查询加读选项，不方便。本次新增会话级 SQL 配置，使管理员/用户能在 Spark 会话中一次性设置 split 大小，影响该会话所有 Iceberg 读取，优先级介于读选项和表属性之间。

## 如何达成设计目的

1. 在 `SparkSQLProperties` 新增 `READ_SPLIT_SIZE = "spark.sql.iceberg.read.split-size"` 常量。
2. 在 `SparkReadConf.splitSizeOption()` 和 `splitSize()` 的配置解析链中加入 `.sessionConf(SparkSQLProperties.READ_SPLIT_SIZE)`，使会话级配置成为解析链的一环。
3. 配置解析优先级：读选项 > 会话级 SQL 配置 > 表属性 > 默认值（具体优先级由 confParser 的链式顺序决定）。
4. 补充文档和测试。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+3/-0 lines)

**修改目的**：定义会话级 split size 配置键。

**工作逻辑**：
```java
// Overrides the split target size for scan planning
public static final String READ_SPLIT_SIZE = "spark.sql.iceberg.read.split-size";
```

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+6/-1 lines)

**修改目的**：在 split size 解析链中加入会话级配置。

**工作逻辑**：
- `splitSizeOption()`：
  ```java
  return confParser.longConf()
      .option(SparkReadOptions.SPLIT_SIZE)
      .sessionConf(SparkSQLProperties.READ_SPLIT_SIZE)  // 新增
      .parseOptional();
  ```
- `splitSize()`：
  ```java
  return confParser.longConf()
      .option(SparkReadOptions.SPLIT_SIZE)
      .sessionConf(SparkSQLProperties.READ_SPLIT_SIZE)  // 新增
      .tableProperty(TableProperties.SPLIT_SIZE)
      .defaultValue(TableProperties.SPLIT_SIZE_DEFAULT)
      .parse();
  ```

### `docs/docs/spark-configuration.md` (+1/-0 lines)

**修改目的**：文档中补充该配置项说明。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkReadConf.java` (+38/-0 lines)

**修改目的**：测试会话级 split size 配置的解析。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java` (+41/-0 lines)

**修改目的**：端到端测试会话级 split size 对扫描分片的影响。

## 总结

本提交为 Spark Iceberg 读取新增会话级 split 大小覆盖配置 `spark.sql.iceberg.read.split-size`，使用户能在 Spark 会话级别全局调整分片大小，无需修改表属性或每查询加读选项。改动小而聚焦，接入现有配置解析链，配套补齐文档和测试。该提交随后在 4033 被 backport 到 Spark 3.5 和 4.0。
