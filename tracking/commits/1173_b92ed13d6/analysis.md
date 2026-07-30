# 提交 1173：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#11186)

## 提交信息

- **序号**：1173 / 4088
- **哈希**：b92ed13d6f2162c7d910b901399cc92de8bf0a6e
- **短哈希**：b92ed13d6
- **日期**：2024-09-23（Mon Sep 23 14:42:47 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#11186)
- **PR/Issue**：#11186

## 总体目的

本提交由 Dependabot 自动生成，目的是将 Apache HttpComponents Client 5（`org.apache.httpcomponents.client5:httpclient5`）从 5.3.1 升级到 5.4，这是 `update-type: version-update:semver-minor` 的次版本升级。该库是 AWS SDK 同步 HTTP 传输（ApacheHttpClient）所依赖的底层 HTTP 客户端实现，Iceberg 在 `iceberg-aws` 等模块中通过 AWS SDK 间接使用它进行 S3 等服务的 HTTP 通信。

Dependabot 定期检查依赖的新版本并自动创建 PR，维护者审核后合入，以保证依赖处于最新状态、获取 bug 修复、安全补丁与新特性。

## 如何达成设计目的

仅修改集中式依赖版本目录 `gradle/libs.versions.toml`，把 `httpcomponents-httpclient5` 版本号从 `5.3.1` 改为 `5.4`。所有通过 `libs.httpcomponents-httpclient5` 引用该版本的模块（主要是 AWS SDK 传递依赖管理）会在下次构建时自动使用新版本。无代码改动，无 API 调整。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 httpclient5 版本号。

**工作逻辑**：在 `[versions]` 段把：

```toml
httpcomponents-httpclient5 = "5.3.1"
```

改为：

```toml
httpcomponents-httpclient5 = "5.4"
```

该变量在 `[libraries]` 段被引用（`httpcomponents-httpclient5 = { module = "org.apache.httpcomponents.client5:httpclient5", version.ref = "httpcomponents-httpclient5" }`，本提交未改这一行），下游模块的 `build.gradle` 通过 `libs.httpcomponents.httpclient5` 或对 AWS SDK 的依赖约束使用此版本。

**升级背景**：根据 5.4 发布说明，本次升级包含若干 bug 修复与改进（如 HTTP/2 支持、连接管理优化等）。次版本升级理论上向后兼容，但需关注与 AWS SDK 的兼容性。

## 小结

- **成效**：httpclient5 升级至 5.4，Iceberg 通过 AWS SDK 间接使用的 HTTP 客户端保持最新。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更，无代码改动。运行时影响取决于 AWS SDK 与 httpclient5 5.4 的兼容性（5.4 是次版本升级，API 兼容）。
- **回迁到 1.4.x 的注意事项**：1.4.x 作为维护分支，依赖升级一般仅限于安全修复或必要 bug 修复。httpclient5 5.3.1 → 5.4 是次版本升级，主要带来功能增强与 bug 修复。回迁前需验证 1.4.x 使用的 AWS SDK 版本是否与 httpclient5 5.4 兼容（1.4.x 的 AWS SDK 版本可能比 main 旧）。若 1.4.x 不存在 httpclient5 相关已知 bug，**可不必回迁**；如有安全或稳定性需求再回迁，并跑完整 AWS 模块测试。
