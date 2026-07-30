# 提交 3883：Build: Bump jackson-bom from 2.21.4 to 2.22.0 (#16815)

## 提交信息

- **序号**：3883 / 4088
- **哈希**：ded7266156aafcea295db23209abd7f3ab8e6f21
- **短哈希**：ded726615
- **日期**：2026-06-14 08:56:30 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump jackson-bom from 2.21.4 to 2.22.0 (#16815)
- **PR/Issue**：#16815

## 总体目的

由开发者 Yuya Ebihara 手动完成的依赖升级提交，将 Jackson BOM（Bill of Materials）从 2.21.4 升级到 2.22.0。这是对提交 3880（单独升级 jackson-annotations 到 2.22）的后续配套升级，使 Jackson 全家桶（jackson-core、jackson-databind、jackson-bom 等）统一到 2.22 版本线。

Jackson 是 Iceberg 核心的 JSON 处理库，其升级影响面广，因此需要同步更新多个模块的运行时依赖清单。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中的 `jackson-bom` 版本变量，并同步更新 Flink、Spark、Kafka Connect 等模块的 `runtime-deps.txt` 运行时依赖清单，确保各模块打包时包含正确版本的 Jackson 依赖。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 jackson-bom 版本变量。

```diff
-jackson-bom = "2.21.4"
+jackson-bom = "2.22.0"
```

### `flink/v1.20/flink-runtime/runtime-deps.txt` (+3/-3 lines)
### `flink/v2.0/flink-runtime/runtime-deps.txt` (+3/-3 lines)
### `flink/v2.1/flink-runtime/runtime-deps.txt` (+3/-3 lines)

**修改目的**：同步更新 Flink 各版本运行时依赖中的 Jackson 版本。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+5/-5 lines)

**修改目的**：更新 Kafka Connect 运行时依赖中的 Jackson 版本，涉及较多条目。

### `spark/v3.5/spark-runtime/runtime-deps.txt` (+2/-2 lines)
### `spark/v4.0/spark-runtime/runtime-deps.txt` (+2/-2 lines)
### `spark/v4.1/spark-runtime/runtime-deps.txt` (+2/-2 lines)

**修改目的**：更新 Spark 各版本运行时依赖中的 Jackson 版本。

**工作逻辑**：
所有 runtime-deps.txt 文件中将 jackson-core、jackson-databind 等相关依赖的版本从 2.21.x 更新为 2.22.x，确保各模块打包的依赖与 BOM 版本一致。

## 总结

将 Jackson BOM 从 2.21.4 升级到 2.22.0，是对提交 3880 单独升级 jackson-annotations 的配套操作，使整个 Jackson 生态统一到 2.22 版本线。由于 Jackson 是核心 JSON 处理库且被多个模块使用，此次升级涉及 8 个文件的同步修改，确保 Flink、Spark、Kafka Connect 各运行时包的依赖一致性。
