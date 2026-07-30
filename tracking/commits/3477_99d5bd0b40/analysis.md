# 提交 3477：ci: add zizmor github workflow (#15793)

## 提交信息

- **序号**：3477 / 4088
- **哈希**：99d5bd0b405253963f5b1890de0b24e4cc1cca27
- **短哈希**：99d5bd0b40
- **日期**：2026-03-27 19:52:38 -0700
- **作者**：Kevin Liu
- **提交说明**：ci: add zizmor github workflow (#15793)
- **PR/Issue**：#15793

## 总体目的

为 Iceberg 项目添加 GitHub Actions 安全分析工作流，使用 zizmor 工具对 GitHub Actions workflows 进行静态安全扫描。zizmor 是一个用于检测 GitHub Actions 配置中安全问题的工具（如不安全的权限、注入风险等），有助于在 CI/CD 配置层面预防安全漏洞。

## 如何达成设计目的

1. 新建一个 `.github/workflows/zizmor.yml` 工作流文件。
2. 工作流在 push 到 main 分支或针对任意分支的 pull request 时触发。
3. 使用 `zizmorcore/zizmor-action` 来运行 zizmor 扫描，并将结果上传为 SARIF 安全事件。

## 修改详情

### `.github/workflows/zizmor.yml` (+43 lines)

**修改目的**：新增 zizmor 安全扫描工作流。

**工作逻辑**：
- 工作流名称为 "GitHub Actions Security Analysis with zizmor 🌈"。
- 触发条件：push 到 `main` 分支，或针对任意分支（`**`）的 pull request。
- 顶层 `permissions: {}` 表示默认无权限，遵循最小权限原则。
- `zizmor` job 运行在 `ubuntu-latest`，仅授予 `security-events: write` 权限，用于上传 SARIF 文件。
- 步骤：
  1. 使用 `actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2` 检出代码，设置 `persist-credentials: false` 以避免凭证泄露。
  2. 使用 `zizmorcore/zizmor-action@71321a20a9ded102f6e9ce5718a2fcec2c4f70d8 # v0.5.2` 运行扫描。

## 总结

新增了一个 CI 安全扫描工作流，使用 zizmor 工具对项目自身的 GitHub Actions 配置进行静态安全分析。这是项目 CI/CD 安全加固的一部分，遵循最小权限原则配置权限。
