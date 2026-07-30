# 提交 4083：Build: Fix Trivy failures (#17344)

## 提交信息

- **序号**：4083 / 4088
- **哈希**：72145bfae613a2e6fb04e10952b9b63f653df659
- **短哈希**：72145bfae
- **日期**：2026-07-23 13:03:13 +0200
- **作者**：pvary
- **提交说明**：Build: Fix Trivy failures (#17344)
- **PR/Issue**：#17344

## 总体目的

Iceberg 项目使用 Trivy 对各 runtime 分发包（如 kafka-connect-runtime、spark-runtime-3.5 等）做容器/依赖安全扫描。当扫描发现某个 CVE/GHSA 但当前无法立即修复（例如漏洞来自上游 shaded 依赖、需要等上游发版）时，会通过 `.github/trivyignore/<runtime>.trivyignore` 文件显式忽略该告警，让 CI 不被这些已知且暂无法处理的告警阻塞。

此前 trivyignore 文件中已经忽略了 `CVE-2026-54512` 和 `CVE-2026-54513`（这两个是 parquet-jackson 内 shaded 的 jackson-databind 漏洞，由于 parquet 1.17.1 自带一份 shaded jackson-databind，无法独立升级，必须等 Apache Parquet 发新版才能修复）。

本次 Trivy 扫描又识别出一个新的安全告警 `GHSA-r7wm-3cxj-wff9`（GitHub Security Advisory），同样来自这些 runtime 分发包中无法独立升级的依赖。如果不处理，CI 会失败，阻塞项目正常构建和发布流程。本提交将该 GHSA 加入 kafka-connect-runtime 和 spark-runtime-3.5 两个 runtime 的 trivyignore 文件，使 CI 重新通过。

## 如何达成设计目的

按照已有的 trivyignore 维护模式：在对应的 `<runtime>.trivyignore` 文件末尾追加一行 GHSA 编号。Trivy 在扫描时会读取这些文件，跳过列表中的告警。这是 Iceberg 项目对"上游 shaded 依赖漏洞"的一贯处理方式——既不假装它不存在（trivyignore 文件中保留详细注释说明原因），也不让它阻塞 CI。

## 修改详情

### `.github/trivyignore/kafka-connect-runtime.trivyignore` (+1/-0 lines)

**修改目的**：忽略 kafka-connect-runtime 中新出现的 GHSA 告警。

**工作逻辑**：

在文件已有的 `CVE-2026-54512` 和 `CVE-2026-54513` 之后追加一行：
```
GHSA-r7wm-3cxj-wff9
```

该文件头部注释已说明：kafka-connect runtime 自身的 jackson-databind 已对齐到项目版本（2.22.x），剩余 finding 来自 parquet-jackson 内 shaded 的 jackson-databind（relocated 到 `shaded.parquet.*`），无法独立升级，需要等 Apache Parquet 1.17.1 之后的版本修复。新增的 GHSA 与既有 CVE 同源，沿用同一忽略策略。

### `.github/trivyignore/spark-runtime-3.5.trivyignore` (+1/-0 lines)

**修改目的**：忽略 spark-runtime-3.5 中同一 GHSA 告警。

**工作逻辑**：

同样在文件末尾已有的两个 CVE 后追加：
```
GHSA-r7wm-3cxj-wff9
```

spark-runtime-3.5 文件头注释也说明这些 jackson-databind CVE 只在 jackson >= 2.18.8 修复，受限于 Spark 3.5 的依赖版本无法升级。新增 GHSA 同样属于此类受限依赖，加入忽略列表。

## 总结

一次 CI 修复提交：将新出现的 Trivy 安全告警 `GHSA-r7wm-3cxj-wff9` 加入 kafka-connect-runtime 和 spark-runtime-3.5 两个 runtime 的 trivyignore 文件，使 CI 重新通过。这些告警均来自上游 shaded 依赖（主要是 parquet-jackson 内的 jackson-databind），无法在 Iceberg 侧独立修复，只能通过 trivyignore 暂时忽略并等待上游发版。处理方式与既有 CVE 忽略策略保持一致。
