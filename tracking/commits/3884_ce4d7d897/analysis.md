# 提交 3884：Build: Bump software.amazon.awssdk:bom from 2.45.1 to 2.46.5 (#16816)

## 提交信息

- **序号**：3884 / 4088
- **哈希**：ce4d7d8973cbe57306ef4927fbafa6ca53b5982c
- **短哈希**：ce4d7d897
- **日期**：2026-06-14 09:12:23 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.45.1 to 2.46.5 (#16816)
- **PR/Issue**：#16816

## 总体目的

由开发者 Yuya Ebihara 完成的依赖升级提交，将 AWS SDK for Java BOM 从 2.45.1 升级到 2.46.5。AWS SDK 是 Iceberg 与 S3、DynamoDB 等 AWS 服务交互的核心依赖，影响范围广泛。

这是 semver-minor 级别的升级（2.45 到 2.46），跨越了多个 patch 版本。由于 AWS SDK 包含大量子模块，且 Iceberg 的 AWS bundle 和 Kafka Connect 模块打包了完整的运行时依赖，升级需要同步更新大量传递依赖的版本。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中的版本变量，并同步更新 `aws-bundle/runtime-deps.txt` 和 `kafka-connect-runtime/runtime-deps.txt` 运行时依赖清单。AWS SDK 的升级会带动大量传递依赖的版本变化，因此运行时依赖清单的更新涉及众多条目。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本变量。

```diff
-awssdk = "2.45.1"
+awssdk = "2.46.5"
```

### `aws-bundle/runtime-deps.txt` (+46/-46 lines approx)

**修改目的**：更新 AWS bundle 运行时依赖清单中所有 AWS SDK 相关模块的版本。

**工作逻辑**：
AWS SDK 2.46.5 相比 2.45.1 会有多个子模块版本变化，runtime-deps.txt 中列出了约 92 行（46 对增删），实际上是各 AWS SDK 子模块（如 `software.amazon.awssdk:s3`、`software.amazon.awssdk:dynamodb`、`software.amazon.awssdk:glue` 等）版本号的同步更新，以及可能新增或移除的传递依赖。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+40/-39 lines approx)

**修改目的**：更新 Kafka Connect 运行时依赖中 AWS SDK 相关模块的版本。

## 总结

将 AWS SDK for Java BOM 从 2.45.1 升级到 2.46.5，是一次影响面较广的 minor 版本升级。由于 AWS SDK 子模块众多且 Iceberg 的 AWS bundle 和 Kafka Connect 模块打包了完整运行时依赖，此次升级涉及约 170 行依赖清单更新，确保各模块打包的 AWS 依赖版本一致且兼容。
