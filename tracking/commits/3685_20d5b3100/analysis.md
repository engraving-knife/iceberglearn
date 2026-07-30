# 提交 3685：Kafka Connect: Add Trivy CVE scan to CI (#15430)

## 提交信息

- **序号**：3685 / 4088
- **哈希**：20d5b310033a0881a79f882683edb162df77a126
- **短哈希**：20d5b3100
- **日期**：2026-05-11 18:15:58 +0200
- **作者**：Robin Moffatt
- **提交说明**：Kafka Connect: Add Trivy CVE scan to CI (#15430) (#15430)
- **PR/Issue**：#15430

## 总体目的

这个提交为 Kafka Connect 模块新增了一个独立的 CVE（Common Vulnerabilities and Exposures）安全扫描 CI 工作流。此前 Kafka Connect 模块虽然已有构建和测试 CI，但缺乏对打包后的 JAR 文件进行已知漏洞扫描的能力。

新增的工作流使用 Trivy 工具（一个流行的容器和文件系统漏洞扫描器）以 rootfs 模式扫描 Kafka Connect 分发包中的所有 JAR 文件。设计上该检查为非必需（non-required），即发现 CVE 不会阻塞 PR 合并，但会在 CI 日志和 GitHub Security 标签页中可见，便于持续跟踪和分诊。这种设计平衡了安全可见性和开发效率。

## 如何达成设计目的

通过以下方式实现：
1. 新建 `kafka-connect-cve-scan.yml` 工作流文件，定义完整的扫描流程
2. 在其他 6 个相关 CI 工作流的 `paths-ignore` 列表中添加对新工作流的引用，避免无关变更触发此扫描

## 修改详情

### `.github/workflows/kafka-connect-cve-scan.yml` (new file, +136 lines)

**修改目的**：新建 Kafka Connect CVE 扫描工作流。

**工作逻辑**：

工作流触发条件：
- `push`：main 分支和 0.x/1.x/2.x 分支，以及 apache-iceberg-** 标签
- `pull_request`：使用 `paths-ignore` 过滤，仅当 Kafka Connect 相关代码变更时触发

主要步骤：

1. **构建分发包**：
```bash
./gradlew -DsparkVersions= -DflinkVersions= -DkafkaVersions=3 \
  :iceberg-kafka-connect:iceberg-kafka-connect-runtime:distZip \
  -Pquick=true -x test -x javadoc
```
禁用 Spark/Flink 模块，仅构建 Kafka Connect 3 的分发包，使用 `-Pquick` 跳过测试和文档以加速构建。

2. **解压分发包**：将 ZIP 解压到 `/tmp/kafka-connect-scan` 目录用于扫描。

3. **运行 Trivy 扫描**：
```yaml
uses: lhotari/sandboxed-trivy-action@f01374b6cc3bf7264ab238293e94f6db7ada6dd0 # v1.0.2
with:
  scan-type: 'rootfs'
  scan-ref: '/tmp/kafka-connect-scan'
  scanners: 'vuln'
  severity: 'HIGH,CRITICAL'
  limit-severities-for-sarif: true
  exit-code: '1'
  format: 'sarif'
  output: 'trivy-results.sarif'
```
使用沙箱化的 Trivy action（`lhotari/sandboxed-trivy-action`），扫描 HIGH 和 CRITICAL 级别的漏洞，输出 SARIF 格式结果。`exit-code: '1'` 表示发现漏洞时步骤失败。

4. **打印扫描结果**：使用 jq 解析 SARIF 文件并输出可读的结果摘要。

5. **上传到 GitHub Security**：
```yaml
if: always() && github.event_name == 'push'
uses: github/codeql-action/upload-sarif@c10b8064de6f491fea524254123dbe5e09572f13 # v4.35.1
```
仅在 push 事件（非 PR）时上传 SARIF 到 GitHub Security 标签页，因为 GitHub Security 仅接受默认/保护分支的结果。

### 其他 6 个 CI 工作流文件 (各 +1 line)

**修改目的**：在 `paths-ignore` 列表中添加对新工作流的引用。

**工作逻辑**：

在 `delta-conversion-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`java-ci.yml`、`kafka-connect-ci.yml`、`spark-ci.yml` 的 `paths-ignore` 列表中添加 `- '.github/workflows/kafka-connect-cve-scan.yml'`，确保这些工作流在 Kafka Connect CVE 扫描工作流文件变更时不会触发。

## 总结

这是一个重要的 CI/CD 安全增强提交，为 Kafka Connect 模块建立了自动化的 CVE 漏洞扫描能力。通过使用 Trivy 以 rootfs 模式扫描打包后的 JAR 文件，可以及时发现依赖库中的已知安全漏洞。非阻塞式的设计确保安全可见性不影响开发效率，而 SARIF 上传到 GitHub Security 标签页则提供了持续的安全态势跟踪。这种模式也为后续其他模块（如提交 3687）扩展 CVE 扫描奠定了基础。
