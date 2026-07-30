# 提交 3053：fix test regex (#14939)

## 提交信息

- **序号**：3053 / 4088
- **哈希**：63c923e6bc78e72bb78adb0ba60ab3c6bf3884c9
- **短哈希**：63c923e6b
- **日期**：2025-12-27
- **作者**：Prashant Singh
- **提交说明**：fix test regex (#14939)
- **PR/Issue**：#14939

## 总体目的

此提交同时做了两件相互关联的事：一是把 `aliyun-sdk-oss` 依赖从 `3.10.2` 大幅升级到 `3.18.4`；二是修复因该升级而失效的 `TestOSSURI.invalidBucket` 测试。两者放在同一提交中，因为后者正是前者的直接后果。

`OSSURI` 是 Iceberg 阿里云 OSS 模块中用于解析 `oss://bucket/key` 或 `https://bucket/key` URI 的类，其构造时通过阿里云 SDK 的 `com.aliyun.oss.internal.OSSUtils` 校验 bucket 名合法性。`TestOSSURI.invalidBucket` 原先用 `https://test_bucket/path/to/file` 作为“非法 bucket 名”的测试输入——下划线 `_` 在 OSS bucket 命名规则中不被允许，旧版 SDK（3.10.2）的 bucket 名校验正则会拒绝 `test_bucket` 并抛出 `IllegalArgumentException`，消息含 `BucketNameInvalid`。

升级到 3.18.4 后，SDK 内部的 bucket 名校验正则（即标题中的 "test regex"）发生了变化——可能放宽了对下划线的校验，或把校验时机/消息格式做了调整，导致 `test_bucket` 不再被 `new OSSURI(...)` 阶段拒绝，测试因期望的异常不再抛出而失败。修复方式是把测试输入改为 `https://test#bucket/path/to/file`：`#` 字符在任何合理的 bucket 名正则中都绝对非法（且 OSSURI 自身定义了 `FRAGMENT_DELIM = "#"`），可确保无论 SDK 正则如何演变都稳定触发非法 bucket 校验，使测试不再依赖对下划线的具体处理策略。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中将 `aliyun-sdk-oss` 版本钉从 `3.10.2` 改为 `3.18.4`；同时在 `TestOSSURI.java` 中把非法 bucket 测试输入与断言中的期望值从 `test_bucket` 改为 `test#bucket`，使测试在新版 SDK 的校验规则下仍能正确验证非法 bucket 名的拒绝行为。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级阿里云 OSS SDK 版本。

**工作逻辑**：将 `aliyun-sdk-oss = "3.10.2"` 改为 `aliyun-sdk-oss = "3.18.4"`，跨越约 8 个次版本。该 SDK 是 `iceberg-aliyun` 模块的核心依赖，提供 OSS 客户端、URI/工具类（`OSSUtils`、`OSS_RESOURCE_MANAGER`）等，`OSSURI`、`OSSFileIO`、`OSSInputFile`/`OSSOutputFile` 等均依赖其 API。升级后可获取上游累积的安全修复、性能改进与 bug 修复。属直接生产依赖，预期对 Iceberg OSS 集成的运行时行为是兼容的（bucket 名校验正则的微调除外，已由本提交的测试修改覆盖）。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSURI.java` (+2/-2 lines)

**修改目的**：修复因 SDK 正则变化而失效的非法 bucket 测试。

**工作逻辑**：
- 测试输入由 `new OSSURI("https://test_bucket/path/to/file")` 改为 `new OSSURI("https://test#bucket/path/to/file")`。
- 断言中期望的错误消息参数同步由 `OSS_RESOURCE_MANAGER.getFormattedString("BucketNameInvalid", "test_bucket")` 改为 `...("BucketNameInvalid", "test#bucket")`，确保断言消息与新输入一致。
- 选择 `#` 的原因：它是 OSS bucket 名中绝对不允许的字符，不受 SDK 正则对下划线/连线等边界字符处理策略变化的影响，使测试更健壮。

## 总结

此提交把阿里云 OSS SDK 从 3.10.2 升级到 3.18.4，并修复了因新版 SDK bucket 名校验正则变化而失效的 `TestOSSURI.invalidBucket` 测试——通过改用 `#` 这一绝对非法字符作为测试输入，使测试不再依赖对下划线的具体校验行为，提升了测试的稳定性与对 SDK 演进的鲁棒性。
