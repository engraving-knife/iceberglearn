# 提交 2162：Spark 4.0: Bump to Official Release

## 提交信息

- **序号**：2162 / 4088
- **哈希**：b504f9c51c6c0e0a5c0c5ff53f295e69b67d8e59
- **短哈希**：b504f9c51
- **日期**：2025-05-23 15:06:06 -0700
- **作者**：Huaxin Gao
- **提交说明**：Spark 4.0: Bump to Official Release
- **PR/Issue**：无（无 PR 号，直接提交）

## 总体目的

Spark 4.0 此前处于预发布（RC/SNAPSHOT）阶段，Iceberg 项目在 `build.gradle` 中配置了 Apache SNAPSHOT 仓库来获取 Spark 4.0 的预发布构件。随着 Spark 4.0 正式发布（Official Release），不再需要依赖 SNAPSHOT 仓库。该提交将默认 Spark 版本从 3.5 切换到 4.0，并移除 Apache SNAPSHOT 仓库配置，使 Iceberg 的构建使用 Spark 4.0 的正式发布版本。

## 如何达成设计目的

- 从 `build.gradle` 的 `repositories` 配置中移除指向 Apache Spark 预发布仓库（`https://repository.apache.org/content/repositories/orgapachespark-1484/`）的 Maven 仓库。
- 将 `gradle.properties` 中的 `systemProp.defaultSparkVersions` 从 `3.5` 修改为 `4.0`，使 Spark 4.0 成为默认构建版本。

## 修改详情

### `build.gradle` (修改, +0/-4 lines)

**修改目的**：移除 Spark 4.0 预发布阶段使用的 Apache SNAPSHOT 仓库。

**工作逻辑**：删除 `allprojects.repositories` 中指向 `https://repository.apache.org/content/repositories/orgapachespark-1484/` 的 Maven 仓库配置块。该仓库在 Spark 4.0 正式发布后不再需要。

### `gradle.properties` (修改, +1/-1 lines)

**修改目的**：将默认 Spark 版本从 3.5 切换为 4.0。

**工作逻辑**：将 `systemProp.defaultSparkVersions` 的值从 `3.5` 改为 `4.0`。`knownSparkVersions` 仍保持 `3.4,3.5,4.0` 不变，表示 4.0 现在是默认版本但仍支持其他版本。

## 总结

该提交标志着 Iceberg 对 Spark 4.0 支持的正式化：移除了预发布仓库依赖，并将 Spark 4.0 设为默认构建版本。这反映了 Spark 4.0 已正式发布，Iceberg 项目对其支持已达到生产就绪状态。
