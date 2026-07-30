# 提交 0603：Build: Bump datamodel-code-generator from 0.25.4 to 0.25.5

## 提交信息

- **序号**：0603 / 4088
- **哈希**：b0a4a907fd8e27ed48ac936f6b8e9f7dce49f9d0
- **短哈希**：b0a4a907f
- **日期**：2024-03-18（Mon Mar 18 08:40:39 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump datamodel-code-generator from 0.25.4 to 0.25.5 (#9979)

  完整提交说明（节选）：
  > Bumps [datamodel-code-generator](https://github.com/koxudaxi/datamodel-code-generator) from 0.25.4 to 0.25.5.
  > update-type: version-update:semver-patch

- **PR/Issue**：#9979（Dependabot 自动 PR）

## 总体目的

本提交由 Dependabot 自动发起，把 Python 工具 `datamodel-code-generator` 从 `0.25.4` 升级到 `0.25.5`，属于补丁版本（semver-patch）依赖跟进。

`datamodel-code-generator`（命令行名为 `datamodel-codegen`）是 Iceberg `open-api/` 子目录下使用的开发工具，用于从 OpenAPI 规范文件 `rest-catalog-open-api.yaml` 自动生成 Python Pydantic 模型代码 `rest-catalog-open-api.py`。该工具的升级属于构建/CI 工具链维护，目的是跟随上游修复与改进，确保代码生成过程稳定可靠。按 Dependabot 的分类，这是 `version-update:semver-patch`（补丁版本升级），是 0601 提交配置生效后仍会正常发起的 PR 类型。

值得注意的是，这个工具虽然是 Python 生态的依赖，但它在 Iceberg 项目中的角色是"OpenAPI 规范的可观测性工具"——README 明确说明生成的 `rest-catalog-open-api.py` 并不被项目运行时使用，而是帮助开发者直观看到 OpenAPI 定义变更在生成的代码层面带来的差异。因此该升级本身对 Iceberg 的 Java/Scala 运行时行为无任何影响。

## 如何达成设计目的

`open-api/requirements.txt` 是 Python pip 风格的依赖锁定文件（使用 `==` 精确版本固定），其中列出两个工具：
- `openapi-spec-validator==0.7.1`：用于校验 OpenAPI YAML 是否符合规范（`make lint` 调用）
- `datamodel-code-generator==0.25.4`：用于从 YAML 生成 Python 代码（`make generate` 调用）

Dependabot 的 pip 生态扫描识别出 `datamodel-code-generator` 有新版 `0.25.5`，遂把 `requirements.txt` 中该行从 `==0.25.4` 改为 `==0.25.5`，单行修改即完成升级。这种 `==` 精确版本固定的写法是工具链依赖的常见做法，能保证所有开发者和 CI 跑出来的环境完全一致。

CI 流程（`.github/workflows/open-api.yml`）会在 PR 上执行以下步骤来验证升级安全性：
1. `make install`：按 `requirements.txt` 安装 Python 依赖
2. `make lint`：用 `openapi-spec-validator` 校验 `rest-catalog-open-api.yaml`
3. `make generate`：用 `datamodel-codegen` 重新生成 `rest-catalog-open-api.py`
4. `git diff --exit-code`：检查工作树是否有差异，若有差异则 CI 失败

这意味着如果 `0.25.5` 的代码生成行为相对 `0.25.4` 有任何输出格式变化（哪怕只是空白字符），CI 都会因 `rest-catalog-open-api.py` 与仓库中提交版本不一致而失败。本 PR 合并说明该补丁升级未引起生成代码的差异。

## 修改详情

### `open-api/requirements.txt`

**修改目的**：把 `datamodel-code-generator` 的固定版本从 `0.25.4` 升到 `0.25.5`。

**工作逻辑**：

文件最后一行从：
```
datamodel-code-generator==0.25.4
```
改为：
```
datamodel-code-generator==0.25.5
```

**消费链路**：

`open-api/Makefile` 的 `generate` 目标调用 `datamodel-codegen` 命令，参数如下：
```makefile
generate:
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

关键参数说明：
- `--input rest-catalog-open-api.yaml`：输入是 Iceberg REST Catalog 的 OpenAPI 规范，定义了 `/v1/config`、`/v1/namespaces`、`/v1/namespaces/{namespace}/tables` 等 REST 端点的请求/响应模型。
- `--output rest-catalog-open-api.py`：输出是一个 Python 文件，包含基于 Pydantic 的数据模型类（如 `ErrorModel`、`CatalogConfig` 等）。
- `--enum-field-as-literal all`：把所有枚举字段生成为 Python `Literal` 类型而非枚举类。
- `--target-python-version 3.8`：生成兼容 Python 3.8 的代码（CI 用 Python 3.9 跑）。
- `--use-schema-description`：把 OpenAPI schema 中的 `description` 字段保留到生成的 docstring。
- `--field-constraints`：把 schema 中的约束（如 `maxLength`、`minimum`）转成 Pydantic 字段约束。
- `--disable-timestamp`：不在生成文件里写入生成时间戳，避免每次重新生成产生无意义的 diff（这是保证 `git diff --exit-code` 检查有效的关键）。
- `--custom-file-header-path header.txt`：使用自定义文件头（Apache 许可证声明）。

**CI 验证机制**：

`.github/workflows/open-api.yml` 在 PR 触发时（路径过滤 `open-api/**` 或 workflow 文件本身变更）会运行上述四步，并用 `git diff --exit-code` 守护生成代码的"已提交即最新"。这条守护规则把 `datamodel-code-generator` 的版本变化与 `rest-catalog-open-api.py` 的内容强绑定：升级工具后必须同步提交重新生成的 `.py` 文件，否则 CI 失败。本 PR 仅修改 `requirements.txt` 而未修改 `.py`，说明 0.25.4 → 0.25.5 的补丁升级对该 OpenAPI spec 的生成结果无影响。

## 小结

本提交是一个由 Dependabot 自动生成的单行 Python 工具依赖升级，把 `datamodel-code-generator` 从 `0.25.4` 升到 `0.25.5`。改动极小（1 行），不影响 Iceberg 的 Java/Scala 运行时行为，仅作用于 OpenAPI 规范的开发工具链。

- **影响范围**：仅 `open-api/` 子目录的开发与 CI 流程。运行时无影响（生成的 Python 代码本身不被项目使用）。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支若已有 `open-api/requirements.txt`，可直接 cherry-pick，单行修改无冲突风险。
  - 回迁后建议触发一次 `open-api` CI workflow（或本地 `make install && make generate && git diff`）验证生成代码与仓库中 `rest-catalog-open-api.py` 一致；若 1.4.x 上的 OpenAPI spec 与 main 有差异，可能需要重新生成并提交 `.py` 文件。
  - 若 1.4.x 上 `datamodel-code-generator` 当前版本低于 0.25.4，建议先回迁中间版本或直接验证 0.25.5 与 1.4.x spec 的兼容性。
