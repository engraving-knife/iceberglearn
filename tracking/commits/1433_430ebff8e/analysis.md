# 提交 1433：Build: Delete branch automatically on PR merge (#11635)

## 提交信息

- **序号**：1433 / 4088
- **哈希**：430ebff8ea4f947a8cef1ad181b19ed5157ef26b
- **短哈希**：430ebff8e
- **日期**：2024-11-26（Tue Nov 26 15:37:46 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Build: Delete branch automatically on PR merge (#11635)
- **PR/Issue**：#11635

## 总体目的

Iceberg 仓库使用 `.asf.yaml`（Apache Software Foundation 仓库的标准配置文件）来集中管理 GitHub 仓库的各项设置（如分支保护、合并按钮、协作者权限等）。在日常贡献流程中，PR 合并后通常会残留对应的远程特性分支，这些分支若不及时清理会逐渐堆积，造成仓库分支列表混乱，增加维护者识别有效分支的成本，也可能让贡献者误以为某些已合并的分支仍在使用。

本提交通过在 `.asf.yaml` 中启用 GitHub 的「合并后自动删除分支」功能，让 GitHub 在 PR 合并到主分支后自动删除源分支，从而保持仓库分支列表的整洁，减少人工清理工作。

## 如何达成设计目的

直接在 `.asf.yaml` 文件的 `github:` -> `protected_branches:` -> `main:` 配置块下、紧随 `required_linear_history: true` 之后，新增一行 `del_branch_on_merge: true`，开启 GitHub 仓库的 `delete_branch_on_merge` 设置项。这是 ASF `asf.yaml` 支持的标准键，会被 ASF 基础设施脚本同步到 GitHub 仓库设置。改动是纯配置层，不涉及任何代码、构建或测试逻辑。

## 修改详情

### `.asf.yaml`

**修改目的**：启用「PR 合并后自动删除源分支」。

**工作逻辑**：在原有 `required_linear_history: true`（位于 `protected_branches:` -> `main:` 保护规则块中，6 空格缩进）之后追加一个空行及新配置项 `del_branch_on_merge: true`（与 `required_linear_history` 同级缩进）：

```yaml
     protected_branches:
       main:
         required_pull_request_reviews:
           required_approving_review_count: 1

         required_linear_history: true

+        del_branch_on_merge: true

     features:
       wiki: true
```

共新增 2 行（一行空行 + 一行 `del_branch_on_merge: true`），无删除。`del_branch_on_merge` 本质是 GitHub 仓库级布尔开关（控制合并 PR 后是否自动删除源分支），ASF `asf.yaml` 将其同步至仓库设置。

## 小结

- **成效**：通过在 `.asf.yaml` 中设置 `del_branch_on_merge: true`，使 GitHub 在 PR 合并后自动删除源分支，保持仓库分支列表整洁，降低维护者人工清理成本，提升贡献流程的卫生度。
- **影响范围**：仅 `.asf.yaml` 一个文件，新增 2 行（含空行），无任何代码、构建、测试或文档逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是仓库级基础设施配置（ASF/GitHub 仓库设置），与产品版本功能无关。该设置作用于整个 GitHub 仓库（main 与所有维护分支共享同一仓库），无需在 1.4.x 分支上单独回迁；一旦在 main 上启用即对全仓库生效。即便 1.4.x 分支上 `.asf.yaml` 内容与 main 不同，也不会影响 1.4.x 的发布产物。
