# 提交 2642：Infra: add .sdkmanrc to .gitignore file (#14085)

## 提交信息

- **序号**：2642 / 4088
- **哈希**：9cc4f5b9620d06cbb411ff73fd50e1a9ef17c8a8
- **短哈希**：9cc4f5b96
- **日期**：2025-09-15 18:47:41 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Infra: add .sdkmanrc to .gitignore file (#14085)
- **PR/Issue**：#14085

## 总体目的

SDKMAN 是一个用于管理多个软件开发工具包版本的工具（如 Java、Gradle 等）。开发者使用 SDKMAN 管理本地构建环境时，会在项目根目录生成 `.sdkmanrc` 文件来声明该项目所需的工具版本。该文件是本地环境配置，不应提交到仓库中，否则会影响其他使用不同环境管理方式的开发者。

此前 `.gitignore` 已忽略了 `.java-version`（jenv 工具的配置文件）等本地环境配置，但尚未忽略 `.sdkmanrc`。本提交将 `.sdkmanrc` 加入 `.gitignore`，避免其被误提交。

## 如何达成设计目的

在 `.gitignore` 文件末尾新增一段，以 `# sdkman` 注释开头，添加 `.sdkmanrc` 模式。

## 修改详情

### `.gitignore` (+3/-0 lines)

**修改目的**：忽略 SDKMAN 配置文件。

**工作逻辑**：在文件末尾（jenv 的 `.java-version` 之后）新增注释和模式：
```
# sdkman
.sdkmanrc
```
这样 git 会忽略工作目录下的 `.sdkmanrc` 文件，防止其被加入版本控制。

## 总结

这是一次简单的 `.gitignore` 维护，将 SDKMAN 工具生成的本地配置文件 `.sdkmanrc` 加入忽略列表，与已有的 `.java-version`（jenv）等本地环境配置保持一致，避免开发者误提交本地环境配置。
