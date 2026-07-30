# 提交 3686：Build: Bump software.amazon.awssdk:bom from 2.42.41 to 2.44.0 (#16279)

## 提交信息

- **序号**：3686 / 4088
- **哈希**：f86a6727a0abfbbf4793f9a3637ed8b344e8ab25
- **短哈希**：f86a6727a
- **日期**：2026-05-11 10:30:10 -0700
- **作者**：Huaxin Gao
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.42.41 to 2.44.0 (#16279)
- **PR/Issue**：#16279

## 总体目的

这个提交将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.42.41 升级到 2.44.0。AWS SDK 是 Iceberg 项目与 AWS 云服务（S3、DynamoDB、Glue、KMS 等）交互的核心依赖，几乎所有的 AWS 集成功能都依赖此 SDK。

此次升级跨越了两个次版本（2.42 → 2.44），属于次版本升级，可能包含新服务支持、API 改进和 bug 修复。BOM 升级会同步影响所有 AWS SDK 模块的版本，因此需要同步更新 aws-bundle 和 kafka-connect 模块的运行时依赖清单。

## 如何达成设计目的

通过修改三个文件完成升级：
1. `gradle/libs.versions.toml`：更新 BOM 版本号
2. `aws-bundle/runtime-deps.txt`：同步更新 AWS bundle 的运行时依赖清单
3. `kafka-connect/kafka-connect-runtime/runtime-deps.txt`：同步更新 Kafka Connect 运行时依赖清单

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 awssdk-bom 版本号。

**工作逻辑**：

```toml
-awssdk-bom = "2.42.41"
+awssdk-bom = "2.44.0"
```

### `aws-bundle/runtime-deps.txt` (+44/-44 lines)

**修改目的**：同步更新 AWS bundle 模块的所有 AWS SDK 依赖版本。

**工作逻辑**：将清单中所有 `software.amazon.awssdk:*` 依赖的版本从 2.42.41 更新为 2.44.0，共涉及 44 个依赖项。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+39/-39 lines)

**修改目的**：同步更新 Kafka Connect 运行时模块的所有 AWS SDK 依赖版本。

**工作逻辑**：将清单中所有 `software.amazon.awssdk:*` 依赖的版本从 2.42.41 更新为 2.44.0，共涉及 39 个依赖项。

## 总结

这是一个重要的依赖升级提交，将 AWS SDK for Java 升级了两个次版本。由于 AWS SDK 是 Iceberg 云存储集成的核心依赖，此次升级涉及大量子模块版本的同步更新。保持 AWS SDK 的最新版本对于获取新服务支持、性能改进和安全修复具有重要意义。
