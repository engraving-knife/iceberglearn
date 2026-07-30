# 提交 0474：Build: Bump datamodel-code-generator from 0.25.2 to 0.25.3 (#9639)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0474 |
| 完整哈希 | 4751a37672d4c2cdaaa047a728261baafcd38807 |
| 短哈希 | 4751a3767 |
| 日期 | 2024-02-06（Tue Feb 6 19:55:20 2024 +0100） |
| 作者 | dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com> |
| 说明 | Build: Bump datamodel-code-generator from 0.25.2 to 0.25.3 (#9639) |
| PR | #9639 |
| 依赖类型 | direct:production |
| 更新类型 | version-update:semver-patch（补丁版本升级） |

提交统计：1 个文件修改，1 行新增，1 行删除。

涉及文件：`open-api/requirements.txt`

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目 `open-api` 目录下用于代码生成的 Python 工具 `datamodel-code-generator` 从 `0.25.2` 升级到 `0.25.3`。`datamodel-code-generator`（命令行名为 `datamodel-codegen`）是一个开源工具，能够根据 OpenAPI / JSON Schema 规范文件自动生成 Pydantic 数据模型代码。在 Iceberg 中，它被用来从 REST Catalog 的 OpenAPI 规范 `open-api/rest-catalog-open-api.yaml` 生成对应的 Python 模型文件 `open-api/rest-catalog-open-api.py`，供 Iceberg 的 Python 客户端（pyiceberg）等下游使用。

该工具属于“构建期/开发期”工具链而非运行时依赖：它本身不打包进任何 Iceberg 发布工件，仅在维护者需要重新生成 REST Catalog 模型代码时通过 `open-api/Makefile` 的 `generate` 目标调用。同目录 `requirements.txt` 还固定了 `openapi-spec-validator==0.7.1`（用于 `lint` 目标校验 OpenAPI 规范合法性）。升级 0.25.2 → 0.25.3 属补丁级别（`version-update:semver-patch`），按语义化版本约定为向后兼容更新，通常包含生成器自身的 bug 修复与小幅改进。跟进这类工具链补丁升级，可以确保未来重新生成 `rest-catalog-open-api.py` 时采用更稳定、缺陷更少的生成器版本，减少生成代码出现回归的可能性。由于该工具不进入运行时产物，本次升级对 Iceberg 自身代码、构建产物与公共 API 均无影响，回迁 1.4.x 风险极低。

## 如何达成设计目的

实现路径是单点修改：在 `open-api/requirements.txt` 中把 `datamodel-code-generator==0.25.2` 改为 `datamodel-code-generator==0.25.3`，保持 `==` 精确版本锁定（pip requirements 习惯写法），与同文件中 `openapi-spec-validator==0.7.1` 的风格一致。该 requirements 文件由 `open-api/Makefile` 的 `install` 目标（`pip install -r requirements.txt`）消费，安装后 `generate` 目标即可调用新版本的 `datamodel-codegen` 命令重新生成模型代码。

## 修改详情

### `open-api/requirements.txt`

修改目的：把 `datamodel-code-generator` 的锁定版本从 `0.25.2` 提升到 `0.25.3`。

工作逻辑：

- 该文件位于 `open-api/` 目录，集中声明 REST Catalog OpenAPI 工具链所需的 Python 依赖，含两行有效依赖（其余为许可证头）：`openapi-spec-validator==0.7.1` 与 `datamodel-code-generator==0.25.3`（升级前为 `0.25.2`）。
- `openapi-spec-validator` 用于 `Makefile` 的 `lint` 目标：`openapi-spec-validator --errors all rest-catalog-open-api.yaml`，校验 OpenAPI 规范文件的合法性。
- `datamodel-code-generator` 用于 `Makefile` 的 `generate` 目标，调用形式为：
  ```
  datamodel-codegen \
      --enum-field-as-literal all \
      --target-python-version 3.8 \
      --use-schema-description \
      --field-constraints \
      --input rest-catalog-open-api.yaml \
      --disable-timestamp \
      --custom-file-header-path header.txt \
      --input-file-type openapi \
      --output rest-catalog-open-api.py
  ```
  即以 `rest-catalog-open-api.yaml` 为输入、`rest-catalog-open-api.py` 为输出，目标 Python 版本 3.8，启用枚举字段作 literal、使用 schema 描述、字段约束等选项，并禁用时间戳以避免无意义的 diff，自定义文件头取自 `header.txt`。
- 本次版本提升后，下次执行 `make install && make generate` 时将使用 0.25.3 版本的生成器；若生成器在补丁版本间对输出格式有微调，重新生成的 `rest-catalog-open-api.py` 可能伴随少量格式差异，但本提交本身不包含生成产物的更新，仅更新工具版本锁定。

## 小结

本提交是 Dependabot 触发的构建工具链补丁升级：将 `open-api/requirements.txt` 中 `datamodel-code-generator` 由 `0.25.2` 升至 `0.25.3`。该工具是 Iceberg 用于从 REST Catalog OpenAPI 规范（`rest-catalog-open-api.yaml`）生成 Python Pydantic 模型代码（`rest-catalog-open-api.py`）的开发期工具，通过 `open-api/Makefile` 的 `generate` 目标调用，不进入运行时发布产物。升级属补丁级别、向后兼容，不涉及 Iceberg 自身代码与 API 变更，主要用于跟进生成器上游的缺陷修复，回迁 1.4.x 风险极低。
