# 提交 3515：Build: Fix zizmor and Spark 4.1 runtime-deps CI failures (#15937)

## 提交信息

- **序号**：3515 / 4088
- **哈希**：4c215926a0f42e7d682339193606fa5775ce07b0
- **短哈希**：4c215926a0
- **日期**：2026-04-10 16:35:33 -0700
- **作者**：Huaxin Gao
- **提交说明**：Build: Fix zizmor and Spark 4.1 runtime-deps CI failures (#15937)
- **PR/Issue**：#15937

## 总体目的

修复两类 CI 失败：
1. **zizmor ref-version-mismatch 审计失败**：`actions/upload-artifact` 的 rolling v7 tag 从 v7.0.0 移动到了 v7.0.1，但工作流中固定的 commit SHA 仍对应 v7.0.0，导致 zizmor 检测到版本注释与实际 SHA 不匹配。需要更新所有工作流中的 SHA 到 v7.0.1 对应的 commit。
2. **Spark 4.1 runtime-deps 基线不匹配**：提交 3512 中创建的 `runtime-deps.txt` 基线反映了创建时的依赖状态，但随后的 dependabot 版本升级（netty-buffer 4.2.10→4.2.12 等）改变了实际依赖，导致 `checkRuntimeDeps` 任务失败。需要重新生成基线。

## 如何达成设计目的

1. 将 9 个工作流文件中 `actions/upload-artifact` 的 commit SHA 从 `bbbca2ddaa5d8feaa63e36b76fdaad77386f024f # v7` 更新为 `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1`。
2. 重新生成 `spark/v4.1/spark-runtime/runtime-deps.txt`，移除因依赖变更而不再存在的依赖，更新版本号。

## 修改详情

### 9 个 `.github/workflows/*.yml` 文件 (各 +1/-1 line)

**修改目的**：更新 actions/upload-artifact 的 SHA 到 v7.0.1。

**工作逻辑**：`actions/upload-artifact@bbbca2ddaa5d8feaa63e36b76fdaad77386f024f # v7` → `actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1`。

涉及文件：api-binary-compatibility.yml、delta-conversion-ci.yml、flink-ci.yml、hive-ci.yml、java-ci.yml、jmh-benchmarks.yml、kafka-connect-ci.yml、recurring-jmh-benchmarks.yml、spark-ci.yml。

### `spark/v4.1/spark-runtime/runtime-deps.txt` (+5/-13 lines)

**修改目的**：重新生成依赖基线以反映 dependabot 升级后的依赖变化。

**工作逻辑**：
- **移除的依赖**（不再传递到 runtime）：
  - `com.aliyun:credentials-java:0.3.12` 和 `com.aliyun:tea:1.4.1`（因 #15858 改为 compileOnly）
  - `com.google.code.gson:gson:2.11.0`
  - `com.squareup.okhttp3:okhttp:4.12.0` 和 `com.squareup.okio:okio-jvm:3.6.0`
  - `com.sun.xml.bind:jaxb-core:2.3.0` 和 `com.sun.xml.bind:jaxb-impl:2.3.0`
  - `org.jacoco:org.jacoco.agent:0.8.8`
  - `org.jetbrains.kotlin:kotlin-stdlib*` 系列（4 个）
- **版本更新**：
  - `com.google.errorprone:error_prone_annotations`: 2.27.0 → 2.10.0
  - `io.netty:netty-buffer`: 4.2.10.Final → 4.2.12.Final（#15891）
  - `io.netty:netty-common`: 4.2.10.Final → 4.2.12.Final

## 总结

CI 修复提交，解决两类失败：1）更新 9 个工作流中 actions/upload-artifact 的 commit SHA 从 v7.0.0 到 v7.0.1 以修复 zizmor ref-version-mismatch 审计；2）重新生成 Spark 4.1 runtime-deps.txt 基线，移除因 #15858（Aliyun compileOnly）和 #15891（netty 升级）等提交导致的依赖变化，反映当前实际依赖状态。
