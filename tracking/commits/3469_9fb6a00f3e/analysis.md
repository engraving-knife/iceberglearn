# 提交 3469：Build: Harden GitHub Actions workflows against zizmor findings (#15790)

## 提交信息

- **序号**：3469 / 4088
- **哈希**：9fb6a00f3e066d302fd29ac19329fdd951afc818
- **短哈希**：9fb6a00f3e
- **日期**：2026-03-27 11:30:05 -0700
- **作者**：Kevin Liu
- **提交说明**：Build: Harden GitHub Actions workflows against zizmor findings (#15790)
- **PR/Issue**：#15790

## 总体目的

继续修复 zizmor 安全扫描发现的问题，针对非 PR 触发的工作流（如发布、基准测试等）进行安全加固。这是提交 #15788 的补充，覆盖剩余的工作流文件。

## 如何达成设计目的

- 在 `jmh-benchmarks.yml`、`recurring-jmh-benchmarks.yml` 中添加 `persist-credentials: false` 和缓存分离
- 在 `labeler.yml` 中添加 `persist-credentials: false`
- 在 `publish-iceberg-rest-fixture-docker.yml` 和 `publish-snapshot.yml` 中添加权限限制
- 在 `site-ci.yml` 中添加 `persist-credentials: false`

## 修改详情

### `.github/workflows/jmh-benchmarks.yml` (+20/-9 lines)
**修改目的**：添加 persist-credentials: false 和缓存分离。

### `.github/workflows/labeler.yml` (+1/-1 lines)
**修改目的**：添加 persist-credentials: false。

### `.github/workflows/publish-iceberg-rest-fixture-docker.yml` (+10/-4 lines)
**修改目的**：添加权限限制和 persist-credentials: false。

### `.github/workflows/publish-snapshot.yml` (+8/-3 lines)
**修改目的**：添加权限限制和 persist-credentials: false。

### `.github/workflows/recurring-jmh-benchmarks.yml` (+1/-0 lines)
**修改目的**：添加 persist-credentials: false。

### `.github/workflows/site-ci.yml` (+4/-1 lines)
**修改目的**：添加 persist-credentials: false。

## 总结

该提交是提交 #15788 的补充，继续对剩余的非 PR 触发工作流（发布、基准测试、labeler、site 等）进行 zizmor 安全加固，包括添加 `persist-credentials: false`、缓存分离和权限限制。
