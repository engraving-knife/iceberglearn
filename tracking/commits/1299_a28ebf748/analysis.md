# 提交 1299：Build: Bump datamodel-code-generator from 0.26.1 to 0.26.2 (#11356)

## 提交信息

- **序号**：1299 / 4088
- **哈希**：a28ebf748cc00f66b80c797dc13181b4e81fe252
- **短哈希**：a28ebf748
- **日期**：2024-10-28（Mon Oct 28 18:12:48 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump datamodel-code-generator from 0.26.1 to 0.26.2 (#11356)
- **PR/Issue**：#11356

## 总体目的

Iceberg 仓库在 `open-api/` 目录下维护 OpenAPI 规范和相关的 Python 工具链。`datamodel-code-generator` 是一个 Python 工具，用于从 OpenAPI/JSON Schema 规范自动生成 Pydantic 数据模型代码。本次提交将该工具从 0.26.1 升级到 0.26.2，属于 semver-patch 级别的补丁更新，获取上游 bug 修复。

## 如何达成设计目的

Dependabot 检测到 `open-api/requirements.txt` 中 `datamodel-code-generator` 的版本固定为 0.26.1，自动将其更新为 0.26.2。该文件是 Python 依赖清单，用于安装 OpenAPI 规范验证和代码生成所需的 Python 包。

## 修改详情

### `open-api/requirements.txt`

**修改目的**：将 datamodel-code-generator 从 0.26.1 升级到 0.26.2。

**工作逻辑**：在 Python 依赖清单中，将：

```
datamodel-code-generator==0.26.1
```

修改为：

```
datamodel-code-generator==0.26.2
```

该行位于 `openapi-spec-validator==0.7.1` 之后，是文件的最后一行。`==` 操作符表示精确版本锁定。该工具用于从 Iceberg 的 OpenAPI 规范生成 Python Pydantic 模型，升级后生成的代码可能包含 bug 修复或格式改进。

## 小结

- **成效**：datamodel-code-generator 升级到 0.26.2，获取补丁级别的 bug 修复。
- **影响范围**：仅 `open-api/requirements.txt` 一个文件，1 行改动，仅影响 OpenAPI 工具链，不影响 Java 产品代码运行时。
- **回迁到 1.4.x 的注意事项**：这是 Python 工具链升级，与 Java 产品代码无关。1.4.x 分支如果需要维护 OpenAPI 规范和生成代码，可以考虑回迁以获取工具修复。补丁级升级风险极低。如果 1.4.x 不涉及 OpenAPI 代码生成流程，则无需回迁。
