# 提交 1271：Spark 3.5: Reset Spark Conf for each test in TestCompressionSettings (#11333)

## 提交信息

- **序号**：1271 / 4088
- **哈希**：043757c0a1cb79392a3dc81054b75d8cfb3bd95e
- **短哈希**：043757c0a
- **日期**：2024-10-23（Wed Oct 23 12:36:34 2024 -0700）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.5: Reset Spark Conf for each test in TestCompressionSettings (#11333)
- **PR/Issue**：#11333

## 总体目的

`TestCompressionSettings` 是 Spark 3.5 模块中验证 Iceberg 写入压缩配置的参数化测试类。该类通过 `@TestTemplate` 配合 `@ParameterizedTestExtension` 对多种压缩格式（PARQUET、ORC、AVRO）和压缩参数组合进行参数化测试。每个测试运行时，会通过 `spark.conf().set(key, value)` 设置 Spark 会话级别的压缩配置（如 `COMPRESSION_CODEC`、`COMPRESSION_LEVEL`、`COMPRESSION_STRATEGY`），然后写入数据并验证生成文件的实际压缩类型。

问题在于：这些 Spark 会话级配置在测试之间**不会被自动清理**。当一个测试用例设置了 `spark.conf().set(COMPRESSION_CODEC, "zstd")` 后，如果该测试完成（或失败），配置值仍然残留在共享的 `SparkSession` 中。下一个参数化组合的测试运行时，如果它设置的 `properties` 中不包含某个压缩配置键（例如某组合只设置了 `COMPRESSION_CODEC` 而未设置 `COMPRESSION_STRATEGY`），那么 `COMPRESSION_STRATEGY` 会沿用上一个测试残留的值，导致写入时使用了错误的压缩策略，测试断言失败。

本提交的目标是：在每个测试用例执行前重置 Spark 会话级的压缩配置，确保每个参数化组合都在干净的配置环境下运行；同时在写入前增加断言验证配置已正确设置，防止配置泄漏导致的隐蔽错误。

## 如何达成设计目的

通过两层保障实现测试隔离：

1. **`@BeforeEach` 重置配置**：新增 `resetSpecificConfigurations()` 方法，在每个测试用例执行前 `unset` 三个压缩相关的 Spark 会话级配置项（`COMPRESSION_CODEC`、`COMPRESSION_LEVEL`、`COMPRESSION_STRATEGY`），清除上一个测试可能残留的配置值。

2. **写入前断言验证**：新增 `assertSparkConf()` 私有方法，在 `spark.conf().set(...)` 设置完当前测试的配置后、实际写入数据前，断言 Spark 会话中的配置值与期望值一致。若因某种原因配置未正确设置（或被其他代码覆盖），断言会立即失败并给出明确的错误信息，而非等到文件压缩类型不匹配时才间接发现。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestCompressionSettings.java`（修改，+19 / -0）

**修改目的**：在每个测试前重置 Spark 压缩配置，并在写入前验证配置正确性。

**工作逻辑**：

改动包含三部分：

#### 1. 新增 `BeforeEach` 导入（第 81 行）

```java
import org.junit.jupiter.api.BeforeEach;
```

#### 2. 新增 `@BeforeEach` 方法（第 150-155 行）

```java
@BeforeEach
public void resetSpecificConfigurations() {
  spark.conf().unset(COMPRESSION_CODEC);
  spark.conf().unset(COMPRESSION_LEVEL);
  spark.conf().unset(COMPRESSION_STRATEGY);
}
```

在 `@BeforeAll`（启动 SparkSession）之后、每个 `@TestTemplate` 之前执行。三个常量来自 `SparkSQLProperties`：
- `COMPRESSION_CODEC`：写入时使用的压缩编解码器（如 `zstd`、`snappy`、`gzip`）
- `COMPRESSION_LEVEL`：压缩级别（如 `3`）
- `COMPRESSION_STRATEGY`：压缩策略（如 `compression`、`speed`）

`unset` 操作确保无论上一个测试设置了什么值，当前测试都从"未设置"状态开始。当配置未设置时，Iceberg 会回退到表属性中定义的默认压缩配置。

#### 3. 新增 `assertSparkConf()` 方法及调用（第 202 行和第 266-273 行）

在 `testWriteDataWithDifferentSetting` 方法中，设置完 `spark.conf().set(entry.getKey(), entry.getValue())` 之后、调用 `df.writeTo(...)` 之前，插入断言调用：

```java
for (Map.Entry<String, String> entry : properties.entrySet()) {
  spark.conf().set(entry.getKey(), entry.getValue());
}

assertSparkConf();  // 新增：写入前验证配置

df.select("id", "data")
    .writeTo(TABLE_NAME)
    ...
```

`assertSparkConf()` 方法实现：

```java
private void assertSparkConf() {
  String[] propertiesToCheck = {COMPRESSION_CODEC, COMPRESSION_LEVEL, COMPRESSION_STRATEGY};
  for (String prop : propertiesToCheck) {
    String expected = properties.getOrDefault(prop, null);
    String actual = spark.conf().get(prop, null);
    assertThat(actual).isEqualToIgnoringCase(expected);
  }
}
```

遍历三个压缩配置项，将期望值（来自当前参数化组合的 `properties` Map，若不存在则为 `null`）与 Spark 会话中的实际值比较。`isEqualToIgnoringCase` 用于大小写不敏感比较（压缩编解码器名称通常不区分大小写）。若 `properties` 中不包含某键，期望值为 `null`，断言 Spark 会话中该键也未被设置（`spark.conf().get(prop, null)` 返回 `null`），这与 `@BeforeEach` 的 `unset` 操作配合，确保配置隔离。

## 小结

- **成效**：通过 `@BeforeEach` 重置和写入前断言双重保障，彻底消除了 `TestCompressionSettings` 中因 Spark 会话级压缩配置在参数化测试间泄漏导致的间歇性失败。改动为纯测试代码，不影响生产行为。
- **影响范围**：仅修改 `spark/v3.5/spark` 测试模块的一个测试文件，无 API/行为变更。
- **回迁到 1.4.x 的注意事项**：可直接 cherry-pick。需确认 1.4.x 分支的 `SparkSQLProperties` 中已定义 `COMPRESSION_CODEC`、`COMPRESSION_LEVEL`、`COMPRESSION_STRATEGY` 三个常量（这些在更早的版本中已存在）。`@BeforeEach` 来自 JUnit 5（Jupiter），需确认 1.4.x 的测试依赖中已包含 `junit-jupiter-api`（Iceberg 1.4.x 已使用 JUnit 5，无兼容性问题）。
