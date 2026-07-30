# 提交 1311：Build: Bump Hadoop to 3.4.1 (#11428)

## 提交信息

- **序号**：1311 / 4088
- **哈希**：7c390861935874d999aad66ebafd4ef9aba648d9
- **短哈希**：7c3908619
- **日期**：2024-10-30（Wed Oct 30 15:49:37 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Build: Bump Hadoop to 3.4.1
- **PR/Issue**：#11428

## 总体目的

Iceberg 在 `gradle/libs.versions.toml` 中维护两个 Hadoop 主版本依赖：

- `hadoop2 = "2.7.3"`：用于兼容遗留 Hadoop 2.x 集群（主要在 `iceberg-gcp`、`iceberg-hive-metastore` 等模块的 hadoop2 配置中）；
- `hadoop3 = "3.3.6"`：用于 Hadoop 3.x 集群（绝大多数模块默认依赖）。

本提交把 `hadoop3` 从 `3.3.6` 升级到 `3.4.1`。Hadoop 3.4.x 是 2024 年发布的最新稳定大版本，相比 3.3.6 主要带来：

1. **安全修复**：3.4.0/3.4.1 修复了若干 CVE（包括与 HDFS RPC、S3A、ABFS 客户端相关的安全问题），3.3.6 已逐渐停止维护；
2. **bug 修复与稳定性改进**：S3A/ABFS/GCS 等对象存储集成、HDFS 联邦、Router-based federation 等模块的 bug 修复；
3. **新特性**：如 S3A 的 Delegation Token 改进、ABFS 的 OAuth 流程改进、HDFS 的 erasure coding 优化等，对 Iceberg 而言主要体现在底层 FileSystem 实现更稳定；
4. **依赖更新**：Hadoop 3.4.x 内部升级了 guava、protobuf、netty 等依赖版本，与 Iceberg 当前其他依赖更接近，减少传递依赖冲突。

Iceberg 对 Hadoop 的使用集中在 `FileSystem`/`Path`/`Configuration`/`UserGroupInformation` 等核心 API，3.3.x → 3.4.x 在这些公共 API 上保持兼容，因此升级风险较低。

## 如何达成设计目的

仅修改 `gradle/libs.versions.toml` 中 `hadoop3` 这一行版本号：

```toml
# before
hadoop3 = "3.3.6"
# after
hadoop3 = "3.4.1"
```

所有通过 `libs.hadoop3.common`、`libs.hadoop3.client`、`libs.hadoop3.mapreduce` 等依赖别名引用 Hadoop 3.x 的模块（如 `iceberg-core`、`iceberg-aws`、`iceberg-gcp`、`iceberg-azure`、`iceberg-hive-metastore` 等）会自动拉取 3.4.1。Gradle 的版本目录机制保证版本号集中管理、单一来源。

不修改任何源代码，因为 3.3.x 与 3.4.x 在 Iceberg 使用到的 API 表面兼容。CI 会跑全套测试验证。

## 修改详情

### `gradle/libs.versions.toml`（修改，+1/-1 行）

**修改目的**：升级 Hadoop 3.x 依赖版本。

**工作逻辑**：

```toml
hadoop2 = "2.7.3"
hadoop3 = "3.4.1"   # was "3.3.6"
```

`hadoop2` 保持 `2.7.3` 不变，因为 `iceberg-gcp` 等模块仍维护 hadoop2 兼容路径。`hadoop3` 升到 `3.4.1`，所有引用该别名的模块在下次 `./gradlew --refresh-dependencies` 时拉取新版本。

## 小结

- **成效**：Hadoop 3.x 依赖从 3.3.6 升级到 3.4.1，获得最新安全修复、bug 修复与依赖更新。版本号集中管理，所有引用 `libs.hadoop3.*` 的模块自动跟随。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、一行版本号。影响所有依赖 Hadoop 3.x 的模块的传递依赖图（guava、protobuf、netty 等 Hadoop 内部依赖的版本会变化），可能触发 `build.gradle` 中针对 Hadoop 传递依赖的 `exclude` 规则需要复核（例如 1306 提交就为 `iceberg-open-api` 列出了详细的 hadoop3.common exclude 清单）。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯依赖版本升级，无源代码改动，回迁技术上是单行变更；
  2. 但 1.4.x 分支若 Hadoop 3.x 当前为 3.3.x，升级到 3.4.1 需评估：
     - Hadoop 3.4.x 要求 JDK 8+（与 1.4.x 一致，无问题）；
     - Hadoop 3.4.x 内部 guava 升级到 33.x、protobuf 3.25.x，可能与 1.4.x 现有 `exclude` 规则、`iceberg-bundled-guava` shadow 配置产生交互，需要跑 `./gradlew dependencies` 检查冲突；
     - 1.4.x 上若有依赖 Hadoop 3.3.x 特定 API（已在 3.4.x 移除或改签名）的代码，升级会编译失败——但 Iceberg 本身只用稳定公共 API，风险低；
     - 测试侧，`iceberg-aws` 的 S3A mock、`iceberg-hive-metastore` 的 Hive 集成测试可能因 Hadoop 内部行为变化而出现 flaky，回迁后需跑完整测试套件验证；
  3. 建议在 1.4.x 上单独建一个 PR 升级 Hadoop 并跑 CI，而不是与其它回迁混在一起，便于定位潜在问题；
  4. Hadoop 3.4.0 与 3.4.1 之间也有差异（3.4.1 是 3.4.0 的 bugfix 版本），建议直接用 3.4.1 而非 3.4.0。
