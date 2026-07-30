# 提交 3082：site infra: when running `make serve`, add a tip on using `make serve-dev` instead

## 提交信息

- **序号**：3082 / 4088
- **哈希**：01e324022f115c1457367872aac9a2d35ef0fdbf
- **短哈希**：01e324022
- **日期**：2026-01-08
- **作者**：Kevin Liu
- **提交说明**：site infra: when running `make serve`, add a tip on using `make serve-dev` instead
- **PR/Issue**：无

## 总体目的

Iceberg 官网使用 MkDocs Material 构建，提供了 `make serve` 和 `make serve-dev` 两个本地开发预览命令。`make serve` 会构建所有版本的文档（包括历史版本），构建时间较长；而 `make serve-dev` 是一个更快的开发模式，只构建 nightly 和 latest 两个版本，适合日常本地开发迭代。

问题在于，许多贡献者习惯性地使用 `make serve`，可能并不知晓 `make serve-dev` 的存在，导致在本地开发时花费不必要的时间等待完整构建。本提交的目的是在运行 `make serve` 时，于脚本启动阶段输出一条醒目的提示信息，告知用户存在更快的 `make serve-dev` 替代方案，引导开发者使用更高效的本地预览方式，从而改善开发体验和效率。

这是一个纯基础设施（site infra）类的改进，不改变任何构建逻辑，仅在控制台输出提示。

## 如何达成设计目的

在 `make serve` 调用的 `site/dev/serve.sh` 脚本开头，插入一段 `echo` 输出，打印一个带边框的提示框，说明 `make serve-dev` 更快且只构建 nightly 和 latest 版本。提示在执行实际构建步骤之前输出，确保用户在等待构建开始前就能看到。

## 修改详情

### `site/dev/serve.sh` (+11/-0 lines)

**修改目的**：在 `serve.sh` 脚本开头输出使用 `make serve-dev` 的提示信息。

**工作逻辑**：
在 `source dev/common.sh` 和 `set -e` 之后、`./dev/setup_env.sh` 之前，插入 11 行 `echo` 语句，输出一个由星号组成的边框包裹的提示信息：

```
**********************************************
**                                          **
**  💡 TIP: Use 'make serve-dev' instead!  **
**                                          **
**  It's FASTER for local development       **
**  (only builds nightly and latest)        **
**                                          **
**********************************************
```

提示包含一个灯泡 emoji（💡）以吸引注意，明确指出 `make serve-dev` 更快且只构建 nightly 和 latest 版本。这段输出在脚本实际执行环境搭建（`setup_env.sh`）和 lint 检查之前出现，用户在等待构建的整个过程中都能看到该提示。输出前后各有一个空 `echo` 以增加视觉间距。

## 总结

本提交在 `make serve` 脚本中添加了一条醒目的控制台提示，引导开发者使用更快的 `make serve-dev` 命令进行本地开发预览。这是一个低风险、高收益的开发体验改进，通过简单的信息提示帮助贡献者节省本地构建时间。
