# 提交 0396：Init git credentials in site-ci

## 提交信息

- **序号**：0396
- **哈希**：e32df0ce08086758c44e9174c582638068244073
- **短哈希**：e32df0ce0
- **日期**：Fri Jan 19 13:14:26 2024 -0800
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Init git credentials in site-ci (#9525)
- **PR/Issue**：#9525

## 总体目的

这个提交修复了 Iceberg 文档站点持续集成（site-ci）部署流程中的一个关键缺陷。Iceberg 项目使用 mkdocs 构建文档站点，并通过 `mkdocs gh-deploy` 命令将构建产物推送到 GitHub Pages 分支（如 gh-pages）。`mkdocs gh-deploy` 的工作原理是构建文档、创建一个 git 提交、然后推送到远程的部署分支，这要求 git 环境必须配置好 `user.name` 和 `user.email` 才能成功执行 commit 操作。

在 GitHub Actions 的运行环境中，默认情况下 git 的全局用户身份信息可能未配置（或配置为不受仓库接受的值），这会导致 `git commit` 失败，进而使整个站点部署流水线中断。本提交通过在 `make deploy` 之前显式设置 git 全局用户名和邮箱来解决这个问题，使用 `GitHub Actions` 作为用户名、`actions@github.com` 作为邮箱，这是 GitHub Actions 自动化提交的惯例配置。

此外，本提交还顺带调整了 `deploy.sh` 部署脚本：移除了 `--remote-name "${REMOTE}"` 参数，改用 `--no-history` 标志。`--no-history` 会让 mkdocs 创建一个孤儿分支（orphan branch），仅包含单个提交而不保留历史提交记录，这是文档站点部署的常见做法，可以避免 gh-pages 分支历史无限膨胀、保持部署分支轻量整洁。

## 如何达成设计目的

实现路径非常直接：在 site-ci 工作流的"Deploy Iceberg documentation"步骤中，将原本单行的 `make deploy` 命令改为多行脚本，在调用 make deploy 之前先用 `git config --global` 设置用户名和邮箱。同时在 `site/dev/deploy.sh` 脚本中调整 mkdocs gh-deploy 的参数，去掉自定义远程名（使用默认 origin）、加上 `--no-history` 标志以创建无历史的部署分支。

## 修改详情

### .github/workflows/site-ci.yml

**修改目的**：为 GitHub Actions 运行环境初始化 git 提交身份，解决 `mkdocs gh-deploy` 执行 `git commit` 时因缺少用户身份信息而失败的问题。

**工作逻辑**：将"Deploy Iceberg documentation"步骤的 `run` 字段从单行命令改为多行脚本（YAML 中使用 `|` 块标量）。新增两行 `git config --global` 命令分别设置 `user.name` 为 `GitHub Actions`、`user.email` 为 `actions@github.com`，然后再执行原有的 `make deploy`。`working-directory: ./site` 保持不变，确保部署命令仍在 site 目录下运行。这种做法是 GitHub Actions 中处理自动化提交的标准模式。

### site/dev/deploy.sh

**修改目的**：调整 mkdocs 部署命令的参数，简化远程配置并启用无历史部署。

**工作逻辑**：将 `mkdocs gh-deploy --remote-name "${REMOTE}"` 改为 `mkdocs gh-deploy --no-history`。移除 `--remote-name "${REMOTE}"` 后，mkdocs 将使用默认远程（origin）进行推送；新增的 `--no-history` 标志让 mkdocs 在 gh-pages 分支上创建一个不带历史提交的孤儿提交，避免分支历史随每次部署不断累积而变得臃肿。注释中保留的 `# --remote-branch asf-site` 提示说明曾经（或将来）可能需要指定远程分支名为 asf-site，这与 Apache 项目托管约定有关。

## 小结

这是一个典型的 CI 基础设施修复提交。它解决了文档站点自动化部署链路中最容易断裂的一环——git 提交身份缺失。通过显式设置 GitHub Actions 标准身份信息和使用 `--no-history` 简化部署分支，使站点部署流程更加健壮和可维护。这类改动虽小，但对保障文档持续发布至关重要。
