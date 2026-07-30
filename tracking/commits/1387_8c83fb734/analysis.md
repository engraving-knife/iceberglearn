# 提交 1387：Build: Bump datamodel-code-generator from 0.26.2 to 0.26.3 (#11572)

## 提交信息

- **序号**：1387 / 4088
- **哈希**：8c83fb73402d8ef1e3f8f673a1851d1d244fcc10
- **短哈希**：8c83fb734
- **日期**：2024-11-17（Sun Nov 17 08:29:05 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump datamodel-code-generator from 0.26.2 to 0.26.3
- **PR/Issue**：#11572

## 总体目的

`datamodel-code-generator` 是一个 Python 工具，用于从 OpenAPI / JSON Schema 规范文件自动生成 Pydantic 数据模型代码。Iceberg 仓库的 `open-api/` 模块维护了一份 REST Catalog 的 OpenAPI 规范，并在 `open-api/requirements.txt` 中固定了该代码生成器的版本，以保证生成的 Python 模型代码可复现。

本提交是 Dependabot 发起的补丁版本升级（0.26.2 → 0.26.3），目的是引入上游的 bug 修复与小幅改进，保持代码生成器为较新版本，避免积累过大的版本差距。属于日常依赖维护，无功能变更。

## 如何达成设计目的

Dependabot 自动检测到 `open-api/requirements.txt` 中 `datamodel-code-generator==0.26.2` 有新版本 0.26.3 发布，遂创建 PR 将版本号升级。这是 `version-update:semver-patch` 类型升级，仅变更一行版本约束。

## 修改详情

### `open-api/requirements.txt`（修改，1 行）

**修改目的**：升级 `datamodel-code-generator` 版本约束。

**工作逻辑**：

```diff
-datamodel-code-generator==0.26.2
+datamodel-code-generator==0.26.3
```

该文件以 `==` 精确固定版本，确保 CI 环境中执行 `pip install -r requirements.txt` 时安装的版本与预期一致。0.26.3 是 0.26.x 系列的补丁版本，按语义化版本约定仅含 bug 修复，不引入破坏性变更。

## 小结

- **成效**：将 OpenAPI 代码生成器从 0.26.2 升级到 0.26.3，引入上游补丁修复，属纯依赖维护。
- **影响范围**：仅 `open-api/requirements.txt` 一行变更，不影响 Java 主代码或运行时行为。
- **回迁到 1.4.x 的注意事项**：可直接 cherry-pick。需确认 1.4.x 分支的 `open-api/requirements.txt` 仍存在且版本为 0.26.2；若已有其他 Dependabot 升级覆盖该行，按版本号较大者保留即可。
