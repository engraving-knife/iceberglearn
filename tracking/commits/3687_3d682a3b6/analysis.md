# 提交 3687：INFRA: Expand Trivy CVE scan to all bundle and runtime modules (#16291)

## 提交信息

- **序号**：3687 / 4088
- **哈希**：3d682a3b65838aba29f16141b45633a6cbe51178
- **短哈希**：3d682a3b6
- **日期**：2026-05-11 15:05:09 -0700
- **作者**：Kevin Liu
- **提交说明**：INFRA: Expand Trivy CVE scan to all bundle and runtime modules (#16291)
- **PR/Issue**：#16291

## 总体目的

这个提交将此前仅针对 Kafka Connect 模块的 Trivy CVE 扫描扩展到所有 bundle 和 runtime 分发模块。在提交 3685 中，仅为 Kafka Connect 模块建立了 CVE 扫描能力，但 Iceberg 项目还有许多其他分发包（AWS/Azure/GCP bundle、Spark/Flink runtime 等），这些同样包含大量第三方依赖，也需要进行漏洞扫描。

扩展后的统一工作流使用矩阵策略（matrix strategy）并行扫描所有 12 个分发模块，大幅提升了扫描效率。同时移除了旧的 Kafka Connect 专用扫描工作流，统一到新的 `cve-scan.yml` 工作流中。

## 如何达成设计目的

通过以下方式实现：
1. 新建统一的 `cve-scan.yml` 工作流，使用矩阵策略定义所有需要扫描的分发模块
2. 删除旧的 `kafka-connect-cve-scan.yml` 工作流
3. 更新其他 6 个 CI 工作流的 `paths-ignore` 引用，将旧工作流替换为新工作流

## 修改详情

### `.github/workflows/cve-scan.yml` (new file, +168 lines)

**修改目的**：新建统一的 CVE 扫描工作流。

**工作逻辑**：

工作流仅在 push 到 main/release 分支和标签时触发（不在 PR 上运行），因为扫描是信息性的，不阻塞构建。

使用矩阵策略定义 12 个分发模块的扫描配置：

```yaml
matrix:
  include:
  - distribution: kafka-connect-runtime
    build-task: "-DkafkaVersions=3 :iceberg-kafka-connect:iceberg-kafka-connect-runtime:distZip"
    scan-path: kafka-connect/kafka-connect-runtime/build/distributions
    unpack: true
  - distribution: aws-bundle
    build-task: :iceberg-aws-bundle:shadowJar
    scan-path: aws-bundle/build/libs
    unpack: false
  # ... 其他 10 个模块
```

每个矩阵项定义：
- `distribution`：分发模块名称
- `build-task`：构建该分发包的 Gradle 任务
- `scan-path`：构建产物路径
- `unpack`：是否需要解压（ZIP 分发包需要解压，JAR 不需要）

主要步骤：
1. 构建分发包
2. 准备扫描目录（根据 `unpack` 标志决定解压还是直接复制 JAR）
3. 运行 Trivy 扫描（`exit-code: '0'` 表示不因发现漏洞而失败）
4. 打印扫描结果
5. 上传 SARIF 到 GitHub Security（使用 `category` 区分不同分发模块）

### `.github/workflows/kafka-connect-cve-scan.yml` (-136 lines)

**修改目的**：删除旧的 Kafka Connect 专用 CVE 扫描工作流。

**工作逻辑**：该工作流的功能已被新的统一 `cve-scan.yml` 工作流完全覆盖。

### 其他 6 个 CI 工作流文件 (各 +1/-1 lines)

**修改目的**：更新 `paths-ignore` 引用。

**工作逻辑**：将 `delta-conversion-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`kafka-connect-ci.yml`、`spark-ci.yml` 中的 `- '.github/workflows/kafka-connect-cve-scan.yml'` 替换为 `- '.github/workflows/cve-scan.yml'`。

## 总结

这是一个 CI/CD 基础设施改进提交，将 CVE 安全扫描从单一模块扩展到所有 12 个分发模块，实现了全面的安全覆盖。通过矩阵策略并行扫描提高了效率，统一工作流简化了维护。与之前的 Kafka Connect 专用工作流相比，新工作流仅在 push 时运行（不在 PR 上运行），且 `exit-code` 设为 0 不阻塞构建，定位为纯信息性安全监控。
