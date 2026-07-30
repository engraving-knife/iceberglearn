# 提交 3692：Build: Fix transitive dependency CVEs across all distributions (#16290)

## 提交信息

- **序号**：3692 / 4088
- **哈希**：b986d73099d2771f77dc56eb83119b512d0e47bf
- **短哈希**：b986d7309
- **日期**：2026-05-12 09:45:42 -0700
- **作者**：Kevin Liu
- **提交说明**：Build: Fix transitive dependency CVEs across all distributions (#16290)
- **PR/Issue**：#16290

## 总体目的

这个提交修复了在所有分发模块中发现的传递依赖 CVE（漏洞）问题。此前在提交 3687 中建立了对所有分发模块的 Trivy CVE 扫描，扫描结果显示多个传递依赖存在已知安全漏洞，需要通过依赖版本强制和升级来修复。

具体修复的 CVE 包括：
1. **BouncyCastle CVE-2026-5598**：通过强制升级 `bcprov-jdk18on` 版本修复
2. **Netty 4.1.x CVEs**（CVE-2026-42577, CVE-2026-42579, CVE-2026-42583, CVE-2026-42584, CVE-2026-42587）：通过将所有 Netty 4.1.x 传递依赖统一升级到指定版本修复
3. **AWS SDK 补丁升级**：将 awssdk-bom 从 2.44.0 升级到 2.44.4

同时同步更新了所有分发模块的 `runtime-deps.txt` 依赖清单文件。

## 如何达成设计目的

通过以下方式实现：
1. 在 `build.gradle` 中添加 BouncyCastle 的依赖替换规则
2. 在 `build.gradle` 中添加 Netty 4.1.x 的版本强制规则
3. 升级 `awssdk-bom` 和 `netty-buffer` 版本号
4. 同步更新所有 12 个分发模块的 `runtime-deps.txt` 文件

## 修改详情

### `build.gradle` (+7 lines)

**修改目的**：添加 BouncyCastle 和 Netty 的 CVE 修复规则。

**工作逻辑**：

```groovy
dependencySubstitution {
  substitute module("org.bouncycastle:bcprov-jdk18on") using module(libs.bouncycastle.bcprov.get().toString()) because("Enforce BouncyCastle that contains CVE-2026-5598 fix")
}
eachDependency { details ->
  if (details.requested.group == 'io.netty' && details.requested.version?.startsWith('4.1.')) {
    details.useVersion(libs.versions.netty.buffer.get())
    details.because("Fix Netty 4.1.x CVEs (CVE-2026-42577, CVE-2026-42579, CVE-2026-42583, CVE-2026-42584, CVE-2026-42587)")
  }
}
```

BouncyCastle 使用 `dependencySubstitution` 替换策略，将所有 `bcprov-jdk18on` 依赖强制替换为指定版本。Netty 使用 `eachDependency` 规则，将所有 4.1.x 版本的 Netty 依赖统一升级到 `netty-buffer` 配置的版本（4.2.13.Final）。

### `gradle/libs.versions.toml` (+2/-2 lines)

**修改目的**：升级 AWS SDK 和 Netty 版本号。

**工作逻辑**：

```toml
-awssdk-bom = "2.44.0"
+awssdk-bom = "2.44.4"
-netty-buffer = "4.2.12.Final"
+netty-buffer = "4.2.13.Final"
```

### 各分发模块的 `runtime-deps.txt` 文件 (12 个文件)

**修改目的**：同步更新依赖清单中的版本号。

**工作逻辑**：更新以下文件中受影响依赖的版本号：
- `aws-bundle/runtime-deps.txt`：AWS SDK 和 Netty 版本更新（112 行变更）
- `azure-bundle/runtime-deps.txt`：Netty 版本更新
- `gcp-bundle/runtime-deps.txt`：Netty 版本更新
- `kafka-connect/kafka-connect-runtime/runtime-deps.txt`：AWS SDK 和 Netty 版本更新（132 行变更）
- `flink/v1.20`、`flink/v2.0`、`flink/v2.1` 的 `flink-runtime/runtime-deps.txt`：Netty 版本更新
- `spark/v3.4`、`v3.5`、`v4.0`、`v4.1` 的 `spark-runtime/runtime-deps.txt`：Netty 版本更新

## 总结

这是一个重要的安全修复提交，通过依赖版本强制和升级修复了多个传递依赖中的 CVE 漏洞。使用 Gradle 的 `dependencySubstitution` 和 `eachDependency` 机制可以精确控制传递依赖的版本，避免依赖项中包含已知漏洞。同时同步更新所有分发模块的依赖清单确保分发包的安全性。这是提交 3687 建立 CVE 扫描基础设施后的首个大规模修复成果。
