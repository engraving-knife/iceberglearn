# 提交 2894：Build: Upgrade setup-java to v5 (#14616)

## 提交信息

- **序号**：2894 / 4088
- **哈希**：cc38966a84775a369bb4e35e8158845a0e8a54a6
- **短哈希**：cc38966a8
- **日期**：2025-11-19 19:06:06 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Upgrade setup-java to v5 (#14616)
- **PR/Issue**：#14616

## 总体目的

本提交将 Iceberg 仓库所有 GitHub Actions 工作流中使用的 `actions/setup-java` 从 v4 升级到 v5。

`actions/setup-java` 是 GitHub 官方提供的 Action，用于在 CI 运行器上配置指定发行版与版本的 JDK 环境。Iceberg 作为一个跨多引擎（Spark、Flink、Hive、Kafka Connect 等）的 Java/Scala 项目，几乎所有 CI 流水线都依赖该 Action 来安装 Zulu 发行版的 JDK（版本 11/17/21 不等）以执行 Gradle 构建、测试、JMH 基准测试、快照发布与 Docker 镜像发布等任务。

将 setup-java 升级到 v5 属于 CI 基础设施的版本迭代。新的大版本通常伴随着运行时（Node.js）升级、依赖更新、性能与稳定性改进，以及潜在的新特性。及时跟进官方 Action 的大版本有助于保持 CI 的可靠性，避免旧版本在未来被弃用或停止维护而导致的流水线故障，同时获得上游修复。

## 如何达成设计目的

遍历 `.github/workflows/` 目录下所有使用 `actions/setup-java@v4` 的工作流文件，将引用统一替换为 `actions/setup-java@v5`，其余参数（如 `distribution: zulu`、`java-version`、缓存配置）保持不变。共涉及 11 个工作流文件、14 处引用。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+1/-1 lines)

**修改目的**：升级 API 二进制兼容性检查任务的 JDK 配置 Action。

**工作逻辑**：将该文件中 `- uses: actions/setup-java@v4` 改为 `actions/setup-java@v5`，用于在 API 兼容性检查（通常基于 japicmp）前安装 JDK 17。

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：升级 Delta 转换 CI 中两个 job 的 JDK 配置 Action。

**工作逻辑**：文件内有两处 `actions/setup-java@v4` 引用（对应两个矩阵 job），均改为 `@v5`，JDK 版本沿用矩阵变量 `${{ matrix.jvm }}`。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：升级 Flink CI 任务的 JDK 配置 Action。

**工作逻辑**：将 Flink 集成测试 job 中的 `actions/setup-java@v4` 改为 `@v5`，JDK 版本沿用 `${{ matrix.jvm }}`。

### `.github/workflows/hive-ci.yml` (+1/-1 lines)

**修改目的**：升级 Hive CI 任务的 JDK 配置 Action。

**工作逻辑**：将 Hive 集成测试 job 中的 `actions/setup-java@v4` 改为 `@v5`。

### `.github/workflows/java-ci.yml` (+3/-3 lines)

**修改目的**：升级 Java CI 中三个 job 的 JDK 配置 Action。

**工作逻辑**：该文件包含三个 job，各有 `actions/setup-java@v4`，共 3 处全部改为 `@v5`，覆盖主构建与多 JVM 矩阵（11/17/21）测试。

### `.github/workflows/jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级 JMH 基准测试任务的 JDK 配置 Action。

**工作逻辑**：将手动触发的 JMH 基准测试 job 中的 `actions/setup-java@v4` 改为 `@v5`，安装 JDK 17。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 lines)

**修改目的**：升级 Kafka Connect CI 任务的 JDK 配置 Action。

**工作逻辑**：将 Kafka Connect 集成测试 job 中的 `actions/setup-java@v4` 改为 `@v5`。

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+1/-1 lines)

**修改目的**：升级 REST Fixture Docker 镜像发布任务的 JDK 配置 Action。

**工作逻辑**：将发布 REST fixture Docker 镜像 job 中的 `actions/setup-java@v4` 改为 `@v5`，安装 JDK 21。

### `.github/workflows/publish-snapshot.yml` (+1/-1 lines)

**修改目的**：升级快照发布任务的 JDK 配置 Action。

**工作逻辑**：将 SNAPSHOT 版本发布 job 中的 `actions/setup-java@v4` 改为 `@v5`，安装 JDK 17。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：升级定期 JMH 基准测试任务的 JDK 配置 Action。

**工作逻辑**：将定期调度的 JMH 基准测试 job 中的 `actions/setup-java@v4` 改为 `@v5`，安装 JDK 17。

### `.github/workflows/spark-ci.yml` (+1/-1 lines)

**修改目的**：升级 Spark CI 任务的 JDK 配置 Action。

**工作逻辑**：将 Spark 集成测试 job 中的 `actions/setup-java@v4` 改为 `@v5`，JDK 版本沿用 `${{ matrix.jvm }}`。

## 总结

这是一次纯 CI 基础设施升级，将全部 11 个 GitHub Actions 工作流中的 `actions/setup-java` 从 v4 统一推进到 v5，共 14 处引用。改动不涉及任何业务代码或构建逻辑，仅更新 Action 版本以跟进官方维护，保持 CI 流水线的稳定性与可靠性。
