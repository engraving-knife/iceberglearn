# 提交 1227：Build: Bump datamodel-code-generator from 0.25.9 to 0.26.1 (#11234)

## 提交信息

- **序号**：1227 / 4088
- **哈希**：d3e015822cbeca277f9e7b458c65a2b4f1ca0750
- **短哈希**：d3e015822
- **日期**：2024-10-12（Sat Oct 12 21:09:44 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump datamodel-code-generator from 0.25.9 to 0.26.1 (#11234)
- **PR/Issue**：#11234

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级提交。与同批次其他提交不同，此依赖不是 Java/Gradle 依赖，而是 Python 依赖。`datamodel-code-generator` 是一个 Python 工具，用于从 OpenAPI 规范文件自动生成 Pydantic 数据模型代码。Iceberg 的 `open-api/` 目录维护了 REST Catalog 的 OpenAPI 规范，并使用此工具生成对应的 Python 数据模型。

本次将 `datamodel-code-generator` 从 0.25.9 升级到 0.26.1，属于次版本（semver-minor）升级，目的是获取新版本的工具改进和 bug 修复。

## 如何达成设计目的

直接修改 `open-api/requirements.txt` 中 `datamodel-code-generator` 的版本锁定，从 `0.25.9` 改为 `0.26.1`。该文件是 Python 项目的依赖声明文件，通过 `pip install -r requirements.txt` 安装时使用。

## 修改详情

### `open-api/requirements.txt`

**修改目的**：升级 datamodel-code-generator 版本号。

**工作逻辑**：将文件最后一行的版本锁定从：

```
datamodel-code-generator==0.25.9
```

改为：

```
datamodel-code-generator==0.26.1
```

`open-api/requirements.txt` 是 Iceberg REST Catalog OpenAPI 规范的 Python 工具链依赖文件，包含两个依赖：
1. `openapi-spec-validator==0.7.1`：用于验证 OpenAPI 规范文件的正确性
2. `datamodel-code-generator==0.26.1`：用于从 OpenAPI 规范自动生成 Pydantic 数据模型代码

`datamodel-code-generator` 是一个开发时工具（dev tool），不参与 Iceberg 的运行时构建产物。它在 OpenAPI 规范更新后被用于重新生成 Python 数据模型代码。0.25.9 → 0.26.1 的升级可能带来生成代码格式的细微变化，但不影响已生成的代码。

## 小结

- **成效**：datamodel-code-generator 从 0.25.9 升级到 0.26.1，获取次版本的工具改进和 bug 修复。
- **影响范围**：仅 `open-api/requirements.txt` 一个文件，1 行改动，无 Java 代码逻辑变更。此工具为开发时工具，不影响运行时构建产物。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支同样维护 `open-api/requirements.txt`。此升级属于开发工具升级，不影响运行时。回迁风险极低，可直接同步版本号。但需注意：升级工具版本后，若重新生成 Python 数据模型代码，生成的代码格式可能有细微变化，需 review 生成结果。若 1.4.x 不计划重新生成 OpenAPI Python 模型，则此升级无实际影响。
