# 提交 2641：Site: change default remote name back to origin. otherwise, site CI could fail. (#14086)

## 提交信息

- **序号**：2641 / 4088
- **哈希**：96040dc600c5ff122cf8774c8a5d3fd75404f6a2
- **短哈希**：96040dc60
- **日期**：2025-09-15 18:19:16 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Site: change default remote name back to origin. otherwise, site CI could fail. (#14086)
- **PR/Issue**：#14086

## 总体目的

在提交 2639 中，`deploy.sh` 的默认远程仓库名被设为 `apache`。然而，文档站点的 CI 流程依赖于默认远程名为 `origin`（git clone 后的标准默认远程名）。将默认值改为 `apache` 后，CI 在未显式指定远程名时会找不到 `apache` 远程，导致站点部署 CI 失败。

本提交将默认远程名改回 `origin`，以修复 CI 失败问题。如果本地环境的远程名不是 `origin`，仍可通过 `make deploy remote_name=apache` 显式指定。

## 如何达成设计目的

1. 将 `deploy.sh` 中 `remote_name` 的默认值从 `apache` 改回 `origin`。
2. 同步更新 `README.md` 中的注释说明，将"Default remote name is 'apache'"改为"Default remote name is 'origin'"。

## 修改详情

### `site/dev/deploy.sh` (+1/-1 lines)

**修改目的**：将默认远程名改回 origin。

**工作逻辑**：`remote_name="${1:-apache}"` 改为 `remote_name="${1:-origin}"`，使未传参时使用 `origin`，与 CI 环境一致。

### `site/README.md` (+1/-1 lines)

**修改目的**：同步更新文档说明。

**工作逻辑**：将注释"Default remote name is 'apache'"改为"Default remote name is 'origin'"。

## 总结

本提交是对提交 2639 的快速修复，将部署脚本的默认远程仓库名从 `apache` 改回 `origin`，以避免文档站点 CI 因找不到 `apache` 远程而失败。这体现了默认值应与最常见环境（git clone 默认远程名 `origin`）保持一致的原则，同时保留了显式指定远程名的能力。
