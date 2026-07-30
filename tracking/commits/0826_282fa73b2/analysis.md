# 提交 0826：Pin 3rd party CI action version (#10481)

## 提交信息
- **序号**：0826 / 4088
- **哈希**：282fa73b2c2b51b1d519a91e8bb05b2d2bdf3cf4
- **短哈希**：282fa73b2
- **日期**：2024-06-11
- **作者**：Piotr Findeisen
- **提交说明**：Pin 3rd party CI action version (#10481)
- **PR/Issue**：#10481

## 总体目的

这个提交针对 GitHub Actions CI 工作流中的第三方 action 引用方式进行加固。

提交说明中明确指出："GitHub allows to delete and re-publish a tag, so referencing 3rd party action by tag name should be discouraged."（GitHub 允许删除并重新发布 tag，因此应避免通过 tag 名称引用第三方 action）。

由于 GitHub 的 tag 是可变（mutable）的，仓库所有者可以删除一个已发布的 tag 并重新发布指向不同 commit 的 tag。如果恶意攻击者或维护者修改了 tag 指向的 commit，CI 流水线就会无声地拉取并执行新的、可能被篡改的代码，从而带来供应链安全风险。本次提交将第三方 action 引用从「tag 引用」改为「commit SHA 引用 + tag 注释」，即所谓的 "pin by SHA" 模式，这是一种供应链安全的最佳实践。

## 如何达成设计目的

提交将 `.github/workflows/spark-ci.yml` 中三处对 `jlumbroso/free-disk-space@v1.3.1` 的引用，全部替换为 `jlumbroso/free-disk-space@54081f138730dfa15788a46383842cd2f914a1be # v1.3.1`。

这种写法的核心在于：

1. **SHA 不可变**：commit SHA 是该 commit 内容的哈希值，无法被篡改后再用同一个 SHA 发布。引用 SHA 后，CI 拉取的代码版本完全确定，供应链安全得以保证。
2. **保留 tag 注释**：在 SHA 后用 `# v1.3.1` 注释保留版本号信息，方便人类阅读时快速识别当前 pin 的版本，便于后续升级时检索和比对。
3. **三处一致性替换**：spark-ci.yml 中有 3 个 job（spark 不同的版本/矩阵任务）都使用了同一个 action，本次提交对三处进行了统一替换，确保所有 job 一致采用 SHA 引用。

## 修改详情

### `.github/workflows/spark-ci.yml`
**修改目的**：将第三方 GitHub Action 从 tag 引用改为 commit SHA 引用，提升供应链安全。

**工作逻辑**：文件中三个 job（在第 86、119、152 行附近，对应不同的 Spark 版本矩阵任务）的 `free-disk-space` 步骤原本都用 `jlumbroso/free-disk-space@v1.3.1`，现统一改为：

```yaml
- uses: jlumbroso/free-disk-space@54081f138730dfa15788a46383842cd2f914a1be # v1.3.1
  with:
    tool-cache: false
```

这个 action 用于在 GitHub Actions runner 上释放磁盘空间（因为 Iceberg 的 Spark 测试矩阵需要较大的磁盘空间，runner 默认 14GB 经常不够用）。`tool-cache: false` 参数表示保留 tool-cache，仅清理其他可释放的空间。改用 SHA 引用后，每次 CI 运行的 action 代码版本完全固定，避免 tag 被重新发布带来的潜在风险。

## 小结
- **成效**：通过 SHA pin 锁定了 `jlumbroso/free-disk-space` 这个第三方 action 的具体版本，避免 tag 被重发导致 CI 行为改变或被注入恶意代码，提升供应链安全。注释保留了 `# v1.3.1` 便于可读性和后续升级追踪。
- **影响范围**：仅影响 `.github/workflows/spark-ci.yml` 文件，涉及 3 处 action 引用，CI 行为本身不变（仍使用 v1.3.1 对应的同一 commit），属于安全加固，对运行时无影响。
- **回迁注意事项**：回迁到 1.4.x 时，需检查 1.4.x 分支的 `.github/workflows/spark-ci.yml` 中是否同样引用了 `jlumbroso/free-disk-space@v1.3.1`（或其它 tag），如有则一并替换为 SHA `54081f138730dfa15788a46383842cd2f914a1be`。若 1.4.x 分支 spark-ci.yml 结构与 main 有差异，应逐处比对后再应用。该改动仅是 CI 配置，无运行时副作用，回迁风险极低。
