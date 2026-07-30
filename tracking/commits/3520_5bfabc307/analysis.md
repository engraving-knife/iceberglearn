# 提交 3520：Build: Bump datamodel-code-generator from 0.55.0 to 0.56.0 (#15949)

## 提交信息

- **序号**：3520 / 4088
- **哈希**：5bfabc307a30225359083fe123966582c89879f4
- **短哈希**：5bfabc307
- **日期**：2026-04-11 23:29:59 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.55.0 to 0.56.0 (#15949)
- **PR/Issue**：#15949

## 总体目的

这是 Dependabot 自动生成的依赖升级 PR。`datamodel-code-generator` 是一个用于从 OpenAPI/JSON Schema 规范生成 Python 数据模型代码的工具，在 Iceberg 项目中用于 open-api 模块，根据 REST catalog 的 OpenAPI 规范生成对应的 Python 模型代码。

本次升级从 0.55.0 升级到 0.56.0，属于 semver minor 版本升级，通常包含新功能和 bug 修复，向后兼容。

## 如何达成设计目的

Dependabot 自动检测到 PyPI 上 `datamodel-code-generator` 的新版本，并更新 `open-api/requirements.txt` 中固定版本号的依赖声明。升级为直接:production 类型，影响 open-api 规范的代码生成流程。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：将 `datamodel-code-generator` 版本从 0.55.0 升级到 0.56.0。

**工作逻辑**：
```
-datamodel-code-generator==0.55.0
+datamodel-code-generator==0.56.0
```
使用 `==` 精确锁定版本，确保 CI 中生成的代码模型基于确定的工具版本。

## 总结

Dependabot 自动升级 `datamodel-code-generator` 至 0.56.0，属于例行依赖维护。该工具用于从 OpenAPI 规范生成 Python 模型，升级版本以获取上游的新功能与修复，保持依赖新鲜度。
