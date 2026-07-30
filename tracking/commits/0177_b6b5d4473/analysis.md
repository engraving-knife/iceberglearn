# 提交 0177：Build: Bump datamodel-code-generator from 0.23.0 to 0.24.2 (#9109)

## 提交信息

- **序号**：0177 / 4088
- **哈希**：b6b5d4473f7ecbd0961caf528728ef024f70a927
- **短哈希**：b6b5d4473
- **日期**：2023-11-19 07:12:36 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.23.0 to 0.24.2 (#9109)
- **PR/Issue**：#9109

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，针对 `open-api` 模块下的 Python 依赖 `datamodel-code-generator`。该工具用于从 OpenAPI 规范生成 Pydantic 数据模型代码，是 Iceberg REST Catalog OpenAPI 客户端/服务端代码生成的构建链依赖之一。

此次升级把 `datamodel-code-generator` 从 0.23.0 升到 0.24.2（semver minor 升级），属于常规的依赖维护操作：跟随上游新版本以获取 bug 修复、新特性以及对较新 Pydantic 版本的兼容性改进。保持构建链工具的现代化有助于减少未来与 Pydantic 2.x 生态的兼容性摩擦（在同一时间窗内 Iceberg 团队也在处理 pydantic 的版本钉扎问题，见相邻提交 0178/0179）。

## 如何达成设计目的

设计目的很简单：把 `open-api/requirements.txt` 中 `datamodel-code-generator` 的固定版本号从 `0.23.0` 改为 `0.24.2`，其余依赖（`openapi-spec-validator==0.5.2`、`pydantic<2.4.0`）保持不变。Dependabot 通过比较版本号并提交单一行 diff 完成升级，且在 PR 说明中附上了上游 release notes 与 commits 对比链接以便人工评审。

## 修改详情

### `open-api/requirements.txt`

**修改目的**：将 `datamodel-code-generator` 版本钉从 0.23.0 提升到 0.24.2。

**工作逻辑**：该文件以 `==` 精确钉版本，`datamodel-code-generator==0.23.0` 改为 `datamodel-code-generator==0.24.2`。文件中其余两行（`openapi-spec-validator==0.5.2` 与 `pydantic<2.4.0`）未改动。值得注意的是，`pydantic<2.4.0` 的注释说明 2.4.0 存在 bug，因此本次升级刻意没有同步放开 pydantic 钉扎——这一钉扎随后会在提交 0179 中被移除。

## 小结

一次常规的 Dependabot 依赖升级，把 OpenAPI 代码生成工具 `datamodel-code-generator` 推进到 0.24.2，保持 Iceberg REST OpenAPI 构建链与上游同步。
