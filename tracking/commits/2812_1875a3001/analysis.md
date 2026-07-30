# 提交 2812：Build: Bump pymarkdownlnt from 0.9.32 to 0.9.33 (#14473)

## 提交信息

- **序号**：2812 / 4088
- **哈希**：1875a300108ed54a025c366738bb04c122a5d76f
- **短哈希**：1875a3001
- **日期**：2025-11-01 21:25:04 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump pymarkdownlnt from 0.9.32 to 0.9.33 (#14473)
- **PR/Issue**：#14473

## 总体目的

这是 Dependabot 自动生成的依赖版本升级提交。pymarkdownlnt 是一个 Python 的 Markdown lint 工具，用于检查 Markdown 文档的规范性。Iceberg 项目在站点文档构建流程中使用它来对文档质量进行静态检查（参见后续提交 2820、2824 中关于 `make lint` 的改进）。

本次升级将该依赖从 0.9.32 升至 0.9.33，属于补丁版本（patch）升级，通常包含错误修复和小改进，不涉及破坏性变更。Dependabot 会定期检查依赖的新版本并自动创建 PR 来保持依赖的时效性和安全性。

## 如何达成设计目的

通过修改 `site/requirements.txt` 中 pymarkdownlnt 的版本号声明，从 `0.9.32` 更新为 `0.9.33`。该文件由 pip 在站点文档构建时使用，升级后新版本会在下次安装依赖时被拉取。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 pymarkdownlnt 依赖版本。

**工作逻辑**：将文件末尾的 `pymarkdownlnt==0.9.32` 改为 `pymarkdownlnt==0.9.33`，采用精确版本锁定（`==`）确保可重现构建。

## 总结

将站点文档 lint 工具 pymarkdownlnt 从 0.9.32 升级到 0.9.33，属于低风险的补丁版本升级，用于获取最新的错误修复和改进。这是 Dependabot 批量依赖升级（2811-2819）的一部分。
