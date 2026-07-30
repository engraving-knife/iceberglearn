# 提交 0402：Revert "Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9260)" (#9544)

## 提交信息

- **序号**：0402
- **哈希**：26efa7a3e62866c253a4a4935f14f00dd19211d8
- **短哈希**：26efa7a3e
- **日期**：2024-01-22（AuthorDate: 2024-01-22 18:21:57 +0100；CommitDate: 2024-01-22 09:21:57 -0800）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Revert "Build: Bump org.apache.httpcomponents.client5:httpclient5 (#9260)" (#9544)

  完整说明：
  ```
  This needs to be reverted due to a NPE, which has been fixed by
  https://github.com/apache/httpcomponents-client/commit/9e3d79bedeee494d24bdb03cc598bae5184a3abf
  but hasn't been released yet.

  This reverts commit 3112ec91617ef080604f47ac92c255b517458522.
  ```
- **PR/Issue**：#9544（revert 原 PR #9260）

## 总体目的

这是一个紧急回滚（revert）提交。背景是：dependabot 在 2023-12-13 通过 PR #9260（commit 3112ec916）把 Apache HttpComponents HttpClient5 从 5.2.3 升到 5.3。但 5.3 版本引入了一个 NPE（空指针异常）缺陷，会影响 Iceberg 在 AWS 等场景下通过 HTTP 客户端访问远端服务（S3、Glue、DynamoDB 等）的稳定性。该缺陷上游已经修复（修复提交为 httpcomponents-client 仓库的 9e3d79bedeee），但修复尚未随任何正式版本发布，Iceberg 无法依赖一个尚未面世的修复。

在这种"上游已修但未发版"的尴尬窗口期，最稳妥的工程处理就是把版本退回到上一个已知良好的 5.2.3，等上游发布包含修复的新版本后再重新升级。这正是一个 revert 提交的标准用法——它把 `gradle/libs.versions.toml` 中的 `httpcomponents-httpclient5` 从 `5.3` 改回 `5.2.3`，等价于撤销 dependabot 那次版本号变更，但保留了完整的 revert 说明，便于未来 git blame 追溯回滚原因。

之所以选择 revert 而不是等上游发版后再修，是因为 HTTP 客户端是 Iceberg AWS 模块的基础设施依赖，NPE 会在生产读写路径上直接打断操作，影响面太大，不能等到下一个 dependabot bump 周期。

## 如何达成设计目的

实现极为简单：`git revert 3112ec916`，把 `gradle/libs.versions.toml` 中 `httpcomponents-httpclient5 = "5.3"` 一行改回 `httpcomponents-httpclient5 = "5.2.3"`。提交说明里嵌入了上游修复 commit 的 URL，作为日后重新升级时的入口凭证——一旦 httpcomponents-client 发布包含 9e3d79bed 的新版本，就可以再次 bump 并移除这个 revert。

## 修改详情

### gradle/libs.versions.toml

**修改目的**：把 httpclient5 依赖版本回滚到 5.2.3，规避 5.3 的 NPE 缺陷。

**工作逻辑**：该文件是 Gradle 版本目录（version catalog），集中声明所有依赖的版本坐标。`httpcomponents-httpclient5` 这个 key 控制 `org.apache.httpcomponents.client5:httpclient5` 的版本。本次仅修改一行：

```
- httpcomponents-httpclient5 = "5.3"
+ httpcomponents-httpclient5 = "5.2.3"
```

由于 dependabot 的原 PR 只动了这一行，revert 也只动这一行，无任何代码逻辑变更。AWS 模块中所有依赖该版本的 HTTP 客户端构建（UrlConnection/Apache HttpClient 配置）会随之回落到 5.2.3 行为。

## 小结

本提交是依赖治理中"快速回退到已知良好版本"模式的范例：单行修改，但提交说明承载了完整的上下文（NPE 缺陷、上游修复 commit 链接、被 revert 的原 commit）。它提醒两点：(1) 自动化依赖升级（dependabot）带来的 minor 版本也可能含阻塞性缺陷，需要 CI 之外的生产观测兜底；(2) revert 不是终点，提交说明里的上游修复链接是后续重新升级的关键线索。对 1.4.x 用户而言，这意味着如果还在 5.3 上遇到 NPE，应回到 5.2.3 或等待 httpcomponents-client 发布修复版本后跟随 Iceberg 后续 bump。
