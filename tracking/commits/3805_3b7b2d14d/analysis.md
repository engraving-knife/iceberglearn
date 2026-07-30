# 提交 3805：Build: Bump openapi-spec-validator from 0.8.5 to 0.9.0 (#16629)

## 提交信息

- **序号**：3805 / 4088
- **哈希**：3b7b2d14dea0d82a8855a8ae591137c1c536938c
- **短哈希**：3b7b2d14d
- **日期**：2026-05-31 08:41:20 -0700
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump openapi-spec-validator from 0.8.5 to 0.9.0 (#16629)
- **PR/Issue**：#16629

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 仓库 `open-api/` 目录下用于校验 OpenAPI 规范的 Python 工具 `openapi-spec-validator` 从 `0.8.5` 升级到 `0.9.0`。Iceberg 维护着 REST catalog 的 OpenAPI 规范文档，并在 CI 中使用该 Python 包来校验规范文档的合法性。这是一个 minor 级升级（0.8.5 → 0.9.0），可能引入对更新版本 OpenAPI 规范的支持或校验规则的调整，属于构建/校验工具链的常规维护。

## 如何达成设计目的

Dependabot 检测到 `open-api/requirements.txt` 中固定的 `openapi-spec-validator` 版本有新发布，自动创建 PR 升级版本字符串。`requirements.txt` 是 Python 项目的依赖清单，CI 在校验 OpenAPI 规范时会按此文件安装指定版本。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 OpenAPI 规范校验工具版本。

**工作逻辑**：
将依赖声明从 `0.8.5` 改为 `0.9.0`：
```
-openapi-spec-validator==0.8.5
+openapi-spec-validator==0.9.0
```

## 总结

这是一次构建/校验工具链的常规 minor 级升级，由 Dependabot 自动完成。升级后 CI 在校验 Iceberg REST OpenAPI 规范时将使用 `openapi-spec-validator` 0.9.0，可获得上游的新校验规则与改进。需要注意的是，minor 级升级可能引入更严格的校验规则，理论上若 OpenAPI 规范文档存在以前未被捕获的问题，升级后可能在校验时暴露，但本提交本身未修改规范文档。
