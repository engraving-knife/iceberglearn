# 提交 2931：infra: notify on github workflow failure (#14609)

## 提交信息

- **序号**：2931 / 4088
- **哈希**：d2d4135724b844107b6a0cc117c1776abbcf69d9
- **短哈希**：d2d413572
- **日期**：2025-11-28 14:15:55 -0800
- **作者**：Kevin Liu
- **提交说明**：infra: notify on github workflow failure (#14609)
- **PR/Issue**：#14609

## 总体目的

Apache Iceberg 是 ASF（Apache 软件基金会）托管项目，仓库根目录的 `.asf.yaml` 是 ASF 的仓库治理配置文件，用于声明 GitHub 仓库的各种设置，包括分支保护、合并策略、通知订阅等。在该文件的 `notifications` 段中，可以针对不同类别的事件配置通知邮件列表。

在此次改动前，`.asf.yaml` 已为 `commits`（提交推送）和 `issues`/`pullrequests`（议题与 PR）配置了对应邮件列表（`commits@iceberg.apache.org`、`issues@iceberg.apache.org`），但并未为 `jobs`（GitHub Actions 工作流运行结果）配置通知邮件列表。这意味着当 CI 工作流失败时，项目维护者只能通过主动查看 GitHub 网页才能发现，无法及时收到邮件告警，可能导致失败的 CI 被长时间忽视、阻塞合入或引入回归。

本提交通过在 `notifications` 段新增 `jobs: ci-jobs@iceberg.apache.org` 配置，使 GitHub Workflow（CI 作业）的运行结果通知发送到专门的 `ci-jobs@iceberg.apache.org` 邮件列表，便于维护者及时感知 CI 失败并响应。提交说明明确表达了意图："Notify ci-jobs@iceberg.apache.org when Github Workflow fails"。

## 如何达成设计目的

在 `.asf.yaml` 的 `notifications` 段中，紧接 `pullrequests` 行之后新增一行 `jobs: ci-jobs@iceberg.apache.org`。ASF 的 infra bot 解析该配置后，会将 GitHub Workflow 作业状态通知路由到该邮件列表。改动仅 1 行新增，无删除，格式与上方 `commits`、`issues`、`pullrequests` 行完全一致。

## 修改详情

### `.asf.yaml` (+1/-0 lines)

**修改目的**：为 GitHub Workflow 失败配置通知邮件列表。

**工作逻辑**：在 `notifications` 段添加：

```yaml
notifications:
  commits:      commits@iceberg.apache.org
  issues:       issues@iceberg.apache.org
  pullrequests: issues@iceberg.apache.org
  jobs:         ci-jobs@iceberg.apache.org   # 新增
  jira_options: link label link label
```

`jobs` 键是 ASF `.asf.yaml` 规范支持的通知类别，对应 GitHub Actions 工作流运行结果。将其指向 `ci-jobs@iceberg.apache.org` 后，CI 失败会自动邮件通知该列表的订阅者。

## 总结

该提交在 `.asf.yaml` 中新增 `jobs: ci-jobs@iceberg.apache.org` 通知配置，使 GitHub Workflow 失败时能自动邮件通知 CI 专用邮件列表，帮助 Iceberg 维护者及时发现并响应 CI 故障，提升了项目基础设施的可观测性。
