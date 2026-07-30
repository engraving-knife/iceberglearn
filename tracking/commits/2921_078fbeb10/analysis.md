# 提交 2921：Build: Don't ignore major version upgrade for GH actions in dependabot (#14687)

## 提交信息

- **序号**：2921 / 4088
- **哈希**：078fbeb1050bc22a665513b5a20a5742d690660e
- **短哈希**：078fbeb10
- **日期**：2025-11-26 07:29:40 +0100
- **作者**：Manu Zhang
- **提交说明**：Build: Don't ignore major version upgrade for GH actions in dependabot
- **PR/Issue**：#14687

## 总体目的

Iceberg 项目的 dependabot 配置此前对 GitHub Actions 生态系统设置了忽略所有 semver-major 版本升级的规则。这意味着 dependabot 不会为 GitHub Actions 提交主版本升级 PR（如从 v5 升级到 v6），只能做 minor 和 patch 升级。这导致 GitHub Actions 的版本逐渐落后，无法获取主版本带来的新功能、性能改进和重要修复。

本提交移除了该忽略规则，允许 dependabot 为 GitHub Actions 提交主版本升级 PR。这与后续 2922-2926 等一系列 GitHub Actions 主版本升级提交直接相关——正是此配置变更使得这些升级成为可能。

## 如何达成设计目的

通过修改 `.github/dependabot.yml` 配置文件，删除 github-actions 生态系统下的 `ignore` 规则块，该规则此前匹配所有依赖名（`*`）并忽略 `version-update:semver-major` 类型的更新。

## 修改详情

### `.github/dependabot.yml` (+0/-3 lines)

**修改目的**：移除 dependabot 对 GitHub Actions 主版本升级的忽略规则。

**工作逻辑**：
删除了以下配置块：
```yaml
    ignore:
      - dependency-name: "*"
        update-types: ["version-update:semver-major"]
```
删除后，dependabot 将为所有 GitHub Actions 依赖（包括主版本升级）创建 PR。

## 总结

本提交是一个构建配置变更，移除了 dependabot 对 GitHub Actions 主版本升级的忽略规则。这是后续一系列 GitHub Actions 版本升级（labeler v5->v6、setup-python v5->v6、stale v9->v10、upload-artifact v4->v5）的前置条件，使项目能够保持 GitHub Actions 的最新状态。
