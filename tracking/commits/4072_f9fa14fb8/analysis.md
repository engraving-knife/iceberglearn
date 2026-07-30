# 提交 4072：Build: Bump astral-sh/setup-uv from 8.2.0 to 8.3.2 (#17293)

## 提交信息

- **序号**：4072 / 4088
- **哈希**：f9fa14fb88d6102686064f8bb28ed5f8223dce7e
- **短哈希**：f9fa14fb8
- **日期**：2026-07-19 09:30:51 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump astral-sh/setup-uv from 8.2.0 to 8.3.2 (#17293)
- **PR/Issue**：#17293

## 总体目的

Dependabot 自动升级提交，将 `astral-sh/setup-uv` 从 8.2.0 升级到 8.3.2（semver minor 版本升级）。`astral-sh/setup-uv` 是用于在 GitHub Actions 中安装 uv（Astral 出品的高性能 Python 包管理器）的 action，Iceberg 在 `open-api.yml` 工作流中使用它来配置 Python 环境以运行 OpenAPI 规范验证和代码生成。minor 版本升级包含新功能和向后兼容的改进。

## 如何达成设计目的

更新工作流中 `uses` 引用的 SHA 和版本注释，从 `fac544c07dec837d0ccb6301d7b5580bf5edae39`（v8.2.0）改为 `11f9893b081a58869d3b5fccaea48c9e9e46f990`（v8.3.2）。

## 修改详情

### `.github/workflows/open-api.yml` (+1/-1 lines)

**修改目的**：升级 setup-uv action 到 8.3.2。

**工作逻辑**：
```yaml
uses: astral-sh/setup-uv@11f9893b081a58869d3b5fccaea48c9e9e46f990 # v8.3.2
```
将 SHA 和版本注释从 v8.2.0 更新为 v8.3.2。

## 总结

常规的 CI 工具链维护升级，将 uv Python 包管理器的 setup action 升级到 8.3.2 minor 版本。minor 级别升级风险较低。
