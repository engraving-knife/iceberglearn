# 提交 0187：Spark: Add SQL config to control locality (#9101)

## 提交信息

- **序号**：0187 / 4088
- **哈希**：506cdbfd5309963d84094b58cd58a4d6c97a3cc5
- **短哈希**：506cdbfd5
- **日期**：2023-11-20 15:08:59 -0800
- **作者**：Yujiang Zhong
- **提交说明**：Spark: Add SQL config to control locality (#9101)
- **PR/Issue**：#9101

## 总体目的

本提交为 Spark 3.3、3.4、3.5 三条分支的 Iceberg 读取配置新增一个会话级 SQL 配置项 `spark.sql.iceberg.locality.enabled`，用于在会话级别控制 Iceberg 是否向 Spark 上报数据本地性（locality）信息。

背景：Iceberg 在为 Spark 规划输入分区（input partition）时，可以选择把每个 task 应该读取的数据文件所在节点（block location）信息一并上报给 Spark，让 DAG 调度器在分配 task 时尽量把 task 调度到数据所在节点，减少网络读取。这一行为由 [`SparkReadConf.localityEnabled()`](../../../spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java) 控制。其默认值由 `Util.mayHaveBlockLocations(table.io(), table.location())` 决定——底层 `FileSystem` 是否可能提供 block location（HDFS 通常为 true，对象存储通常为 false）。

改动前的局限：`localityEnabled()` 只从 `readOptions`（即 DataFrameReader 的 `.option(...)` 或 SQL hint）读取 `SparkReadOptions.LOCALITY`，未设置就回退到默认值。这意味着用户若想在整个会话范围内统一关闭/开启 locality（例如对象存储上为避免 master 反复查询不存在的 block location 带来的开销，或某些自定义 FileSystem 上想强制开启），只能逐查询传 option，没有等价于 `SET spark.sql.iceberg.locality.enabled=false` 的会话级开关。这与 Iceberg 已为 `vectorization.enabled`、`aggregate-push-down.enabled`、`distribution-mode`、`data-planning-mode` 等提供的会话级控制不一致。

本提交补齐这一缺口，把 locality 也纳入会话级 SQL 配置体系，并按 Iceberg 既定的"read option > session conf > table metadata/default"三级优先级解析。

## 如何达成设计目的

整体设计沿用 [`SparkReadConf`](../../../spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java) 既有的"配置解析器"模式：

1. 在 [`SparkSQLProperties`](../../../spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java) 中新增常量 `LOCALITY = "spark.sql.iceberg.locality.enabled"`，与同级会话配置项（如 `VECTORIZATION_ENABLED`、`AGGREGATE_PUSH_DOWN_ENABLED`）并列。
2. 把 `SparkReadConf.localityEnabled()` 从直接调 `PropertyUtil.propertyAsBoolean(readOptions, ...)` 改为通过 `confParser.booleanConf().option(...).sessionConf(...).defaultValue(...).parse()` 链式解析，使该方法与同级其他配置（`snapshotId`、`branch`、`caseSensitive` 等）走同一套优先级链路：read option 优先，其次会话配置，最后默认值。

三条 Spark 分支（3.3/3.4/3.5）同步修改，保持维护分支间行为一致。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java`、`spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java`、`spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java`

**修改目的**：定义会话级 SQL 配置键 `spark.sql.iceberg.locality.enabled`。

**工作逻辑**：在各自 `SparkSQLProperties` 类末尾新增：

```java
// Controls whether to report locality information to Spark while allocating input partitions
public static final String LOCALITY = "spark.sql.iceberg.locality.enabled";
```

命名遵循该类既定约定 `spark.sql.iceberg.<feature>.enabled`，与 `VECTORIZATION_ENABLED`、`AGGREGATE_PUSH_DOWN_ENABLED` 风格一致。该常量随后被 `SparkReadConf.localityEnabled()` 引用为 session conf 的键。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`、`spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`、`spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`

**修改目的**：让 `localityEnabled()` 同时支持 read option 与会话级 SQL 配置，按"read option > session conf > default"优先级解析。

**工作逻辑**：

原实现：

```java
public boolean localityEnabled() {
  boolean defaultValue = Util.mayHaveBlockLocations(table.io(), table.location());
  return PropertyUtil.propertyAsBoolean(readOptions, SparkReadOptions.LOCALITY, defaultValue);
}
```

仅查 `readOptions`，未触及会话配置。

新实现：

```java
public boolean localityEnabled() {
  boolean defaultValue = Util.mayHaveBlockLocations(table.io(), table.location());
  return confParser
      .booleanConf()
      .option(SparkReadOptions.LOCALITY)
      .sessionConf(SparkSQLProperties.LOCALITY)
      .defaultValue(defaultValue)
      .parse();
}
```

`confParser` 是 `SparkReadConf` 构造时创建的 `SparkConfParser`实例（`new SparkConfParser(spark, table, readOptions)`），其 `booleanConf()` 返回一个布尔配置构建器，链式方法语义为：

- `.option(SparkReadOptions.LOCALITY)`：声明该配置可由 read option `SparkReadOptions.LOCALITY`（即查询级 `.option("locality", ...)`）提供，优先级最高。
- `.sessionConf(SparkSQLProperties.LOCALITY)`：声明该配置也可由 Spark 会话配置 `spark.sql.iceberg.locality.enabled` 提供（通过 `SET spark.sql.iceberg.locality.enabled=false` 或 `spark.conf.set(...)`），优先级次于 read option。
- `.defaultValue(defaultValue)`：以上都未设置时的回退值，仍由 `Util.mayHaveBlockLocations(table.io(), table.location())` 计算——底层文件系统是否可能提供 block location。
- `.parse()`：按上述优先级解析并返回 `boolean`。

这与 `SparkReadConf` 类 Javadoc 声明的三级优先级（Read options > Session configuration > Table metadata）一致，且默认值仍保留"按底层存储能力自适应"的原有行为：HDFS 等能提供 block location 的存储默认开启，对象存储默认关闭。改动只是新增了中间一层会话级覆盖能力，不改变默认行为，向后兼容。

## 小结

通过新增 `spark.sql.iceberg.locality.enabled` 会话级 SQL 配置并把 `localityEnabled()` 改为走 `SparkConfParser` 三级优先级解析，让数据本地性开关可像 vectorization、aggregate push-down 等其他 Iceberg Spark 配置一样在会话级统一设置，补齐了配置体系的一致性缺口。
