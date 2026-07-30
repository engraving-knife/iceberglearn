# 提交 3770：ci: only run site-ci deploy job for apache/iceberg (#16535)

## 提交信息

- **序号**：3770 / 4088
- **哈希**：41a95991aaaa0ec2be5770308925263ff5776e9a
- **短哈希**：41a95991a
- **日期**：2026-05-22 11:02:24 -0700
- **作者**：Kevin Liu
- **提交说明**：ci: only run site-ci deploy job for apache/iceberg (#16535)
- **PR/Issue**：#16535

## 总体目的

这个提交限制 `site-ci` 工作流中的 `deploy` 作业只在 `apache/iceberg` 仓库中运行。当开发者 fork Iceberg 仓库后，fork 仓库的 PR 或推送也会触发 GitHub Actions 工作流。如果不加限制，`deploy` 作业会在 fork 仓库中尝试部署文档站点到 `asf-site` 分支，但 fork 仓库没有相应的写入权限，导致作业失败。通过添加仓库条件判断，确保只有官方仓库才执行部署操作。

## 如何达成设计目的

在 `deploy` 作业中添加 `if: github.repository == 'apache/iceberg'` 条件，使该作业仅在官方仓库中执行。

## 修改详情

### `.github/workflows/site-ci.yml` (+1/-0 lines)

**修改目的**：限制 deploy 作业仅在官方仓库运行。

**工作逻辑**：
```yaml
jobs:
  deploy:
    if: github.repository == 'apache/iceberg'
    runs-on: ubuntu-slim
```

添加 `if` 条件判断，当 `github.repository` 等于 `apache/iceberg` 时才执行该作业。在 fork 仓库中，`github.repository` 会是 `<用户名>/iceberg`，条件不满足，作业会被跳过。

## 总结

这是一个 CI 安全性改进提交，通过添加仓库条件判断确保文档部署作业只在官方 `apache/iceberg` 仓库中运行，避免在 fork 仓库中因缺少权限而失败。这是 GitHub Actions 工作流在开源项目中的常见最佳实践。
