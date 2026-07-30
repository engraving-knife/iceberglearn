# 提交 1756：Parquet: Fix performance regression in reader init (#12305) (#12329)

## 提交信息

- **序号**：1756 / 4088
- **哈希**：686bae5e9ff8a52ce55f7609668b69bdb667955a
- **短哈希**：686bae5e9
- **日期**：2025-02-19 10:19:39 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Parquet: Fix performance regression in reader init (#12305) (#12329)
- **PR/Issue**：#12329（原始修复来自 #12305）

## 总体目的

本提交是一个空提交（empty commit），没有实际修改任何文件。它是对 PR #12305（提交 1752，`c1d4182b3`）中"Parquet: Fix performance regression in reader init"修复的重复 cherry-pick。

PR #12329 尝试将 #12305 的修复 cherry-pick 到另一个分支（可能是 maintenance 分支），当该分支合并回 main 时，由于 #12305 的修改已经存在于 main 分支中（通过提交 1752），cherry-pick 操作产生了空提交——即提交的 tree hash 与其父提交完全相同，没有任何文件变更。

通过 `git merge-base --is-ancestor` 确认，提交 1752（`c1d4182b3`）是提交 1756（`686bae5e9`）的祖先，且两者的 tree hash 一致（父提交 1755 的 tree 为 `058462f700`，本提交的 tree 也是 `058462f700`），证明这是一个无文件变更的空提交。

## 如何达成设计目的

由于这是一个空提交，实际的设计目的已由提交 1752（#12305）达成。本提交只是通过 GitHub 的合并流程将一个 cherry-pick PR（#12329）合并到 main 分支，但由于变更已存在，不产生实际文件修改。

## 修改详情

无文件修改。提交的 tree hash（`058462f700e351a33c32b63556304c4aab8ba999`）与其父提交（1755，`3a23f8578`）的 tree hash 完全相同，确认没有任何文件变更。

## 小结

- **成效**：本提交本身不产生任何代码变更，是 PR #12329 对 #12305 修复的重复 cherry-pick。实际的性能修复已由提交 1752（#12305）完成。
- **影响范围**：无实际影响，空提交。
- **回迁到 1.4.x 的注意事项**：无需回迁此提交。应回迁的是实际的修复提交 1752（#12305），而非此空提交。在回迁 1752 时需注意其中描述的 Parquet 库版本兼容性问题。
