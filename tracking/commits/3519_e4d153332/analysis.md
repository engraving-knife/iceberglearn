# 提交 3519：API: Include size unit in avg/max value size fields (#15939)

## 提交信息

- **序号**：3519 / 4088
- **哈希**：e4d15333279594e95869ae8636764cd5f3b51832
- **短哈希**：e4d153332
- **日期**：2026-04-12 07:46:31 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：API: Include size unit in avg/max value size fields (#15939)
- **PR/Issue**：#15939

## 总体目的

`FieldStatistic` 枚举中定义了字段级统计信息，其中 `AVG_VALUE_SIZE` 和 `MAX_VALUE_SIZE` 此前的字段名分别是 `avg_value_size` 和 `max_value_size`，没有标明单位。这容易让使用者误以为单位是字符数、bit 或其他单位，造成歧义。

实际统计的是变长类型（String、Binary）值的字节大小，单位是字节（bytes）。本提交将字段名改为 `avg_value_size_in_bytes` 和 `max_value_size_in_bytes`，并在 schema 描述中也补充 "in bytes"，让单位一目了然。

由于这是新的 stats 元数据特性（offset 为 4、5），还在演进中，改名相对安全，避免了将来发布稳定版后无法修改的窘境。

## 如何达成设计目的

直接修改 `FieldStatistic` 枚举常量的第二个参数（字段名字符串），以及构建 schema 时传入的描述字符串。`fieldName()` 方法返回这个字符串，被 schema 生成与序列化使用，因此改名后会自动传播到生成的统计 schema 字段名。

## 修改详情

### `api/src/main/java/org/apache/iceberg/stats/FieldStatistic.java` (+4/-4 lines)

**修改目的**：明确 avg/max value size 的单位为字节。

**工作逻辑**：
枚举常量字段名修改：
```java
-  AVG_VALUE_SIZE(4, "avg_value_size"),
-  MAX_VALUE_SIZE(5, "max_value_size"),
+  AVG_VALUE_SIZE(4, "avg_value_size_in_bytes"),
+  MAX_VALUE_SIZE(5, "max_value_size_in_bytes"),
```
schema 描述同步修改：
```java
-              "Avg value size of variable-length types (String, Binary)"));
+              "Avg value size in bytes of variable-length types (String, Binary)"));
...
-              "Max value size of variable-length types (String, Binary)"));
+              "Max value size in bytes of variable-length types (String, Binary)"));
```
所有改动只是字符串字面值的变化，不影响代码逻辑。

## 总结

本提交通过将 `avg_value_size`/`max_value_size` 字段重命名为带 `_in_bytes` 后缀的形式，明确统计字段单位为字节，避免歧义。改动小而聚焦，属于字段统计元数据 API 的命名规范化，提升使用者对统计语义的理解准确度。
