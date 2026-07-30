# 提交 2714：Build: Bump runner image from Ubuntu 22.04 to 24.04

## 提交信息

- **序号**：2714 / 4088
- **哈希**：042f01a240439691ac67c6d67bd51cf5a4167dd2
- **短哈希**：042f01a24
- **日期**：2025-10-02 13:50:53 -0700
- **作者**：slfan1989
- **提交说明**：Build: Bump runner image from Ubuntu 22.04 to 24.04
- **PR/Issue**：#14240

## 总体目的

GitHub Actions CI/CD 流水线中使用的运行器镜像（runner image）需要从 Ubuntu 22.04 升级到 24.04。Ubuntu 22.04（Jammy Jellyfish）虽然仍在 LTS 支持期内，但 Ubuntu 24.04（Noble Numbat）作为更新的 LTS 版本提供了更新的工具链、系统库和安全补丁。

GitHub Actions 的 `ubuntu-22.04` runner 镜像已经进入维护模式，GitHub 推荐项目迁移到 `ubuntu-24.04`。继续使用旧版镜像可能导致构建环境中缺少新版本的编译器、运行时和系统工具，以及潜在的安全风险。

此提交将所有 GitHub Actions 工作流文件中的 runner 镜像引用从 `ubuntu-22.04` 更新为 `ubuntu-24.04`。

## 如何达成设计目的

通过在所有 14 个 GitHub Actions 工作流 YAML 文件中将 `runs-on: ubuntu-22.04` 替换为 `runs-on: ubuntu-24.04` 来完成升级。这是一个全局的文本替换操作，覆盖了项目中所有 CI/CD 工作流。

## 修改详情

### `.github/workflows/api-binary-compatibility.yml` (+1/-1 lines)

**修改目的**：将 API 二进制兼容性检查的 runner 镜像升级为 Ubuntu 24.04。

### `.github/workflows/delta-conversion-ci.yml` (+2/-2 lines)

**修改目的**：将 Delta 转换 CI 的两个测试作业（scala-2-12 和 scala-2-13）的 runner 镜像升级。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：将 Flink CI 测试作业的 runner 镜像升级。

### `.github/workflows/hive-ci.yml` (+1/-1 lines)

**修改目的**：将 Hive CI 测试作业的 runner 镜像升级。

### `.github/workflows/java-ci.yml` (+3/-3 lines)

**修改目的**：将 Java CI 的三个作业（core-tests、build-checks、build-javadoc）的 runner 镜像升级。

### `.github/workflows/jmh-benchmarks.yml` (+3/-3 lines)

**修改目的**：将 JMH 基准测试工作流的三个作业（matrix、show-matrix、run-benchmark）的 runner 镜像升级。

### `.github/workflows/kafka-connect-ci.yml` (+1/-1 lines)

**修改目的**：将 Kafka Connect CI 测试作业的 runner 镜像升级。

### `.github/workflows/labeler.yml` (+1/-1 lines)

**修改目的**：将 PR 自动标签作业的 runner 镜像升级。

### `.github/workflows/license-check.yml` (+1/-1 lines)

**修改目的**：将许可证检查作业的 runner 镜像升级。

### `.github/workflows/open-api.yml` (+1/-1 lines)

**修改目的**：将 OpenAPI 规范校验作业的 runner 镜像升级。

### `.github/workflows/publish-snapshot.yml` (+1/-1 lines)

**修改目的**：将快照发布作业的 runner 镜像升级。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-1 lines)

**修改目的**：将定期 JMH 基准测试作业的 runner 镜像升级。

### `.github/workflows/spark-ci.yml` (+1/-1 lines)

**修改目的**：将 Spark CI 测试作业的 runner 镜像升级。

### `.github/workflows/stale.yml` (+1/-1 lines)

**修改目的**：将 stale issue/PR 自动处理作业的 runner 镜像升级。

## 总结

此提交将所有 14 个 GitHub Actions 工作流的 runner 镜像从 Ubuntu 22.04 升级到 24.04。这是一个基础设施维护性变更，确保 CI/CD 环境运行在最新支持的 LTS 版本上，获得更新的工具链和安全补丁。虽然变更本身简单，但需要验证所有 CI 作业在新的 runner 镜像上能正常运行。
