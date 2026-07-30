# 提交 3257：Build: Bump datamodel-code-generator from 0.53.0 to 0.54.0 (#15331)

## 提交信息

- **序号**：3257 / 4088
- **哈希**：323ab162f007307c114bb78cae31ab6c74c932cc
- **短哈希**：323ab162f
- **日期**：2026-02-15
- **作者**：Manu Zhang
- **提交说明**：Build: Bump datamodel-code-generator from 0.53.0 to 0.54.0 (#15331)
- **PR/Issue**：#15331

## 总体目的

Iceberg 的 REST Catalog 规范以 OpenAPI YAML 文件（`open-api/rest-catalog-open-api.yaml`）形式定义，并通过 `datamodel-code-generator` 工具将其自动生成对应的 Python Pydantic 模型文件 `rest-catalog-open-api.py`，供客户端/服务端代码及文档校验使用。生成命令在 `open-api/Makefile` 的 `generate` 目标中通过 `datamodel-codegen` 调用，并启用了 `--use-schema-description`、`--field-constraints`、`--enum-field-as-literal all` 等选项。

本次提交将该生成工具从 `0.53.0` 升级到 `0.54.0`（次版本升级，`version-update:semver-minor` 级别），并重新运行生成器以同步刷新自动生成的 Python 模型文件。新版本生成器在行为上的一个显著变化是：对于使用了 `Literal` 类型约束的字段，它会将 OpenAPI 规范中已定义但旧版本未输出的 `example` 示例值和 `description` 描述信息一并写入到 Pydantic 的 `Field()` 调用中。因此本次提交除升级依赖外，还包含了对生成文件 `rest-catalog-open-api.py` 的大量重新生成内容。

具体来说，YAML 中 `ExpressionType` schema 定义了一个包含全部表达式类型（`eq`、`and`、`or`、`not`、`in`、`not-in`、`lt`、`lt-eq`、`gt`、`gt-eq`、`not-eq`、`starts-with`、`not-starts-with`、`is-null`、`not-null`、`is-nan`、`not-nan`）的 `example` 列表。各类表达式模型（`TrueExpression`、`FalseExpression`、`UnaryExpression`、`LiteralExpression`、`SetExpression`、`AndOrExpression`、`NotExpression`）的 `type` 字段都引用了该 schema。0.54.0 版本生成器现在会把这份 example 列表展开到每个字段的 `Field(example=[...])` 中。同时，服务端规划（server-side planning）相关结果模型（`AsyncPlanningResult`、`EmptyPlanningResult`、`FailedPlanningResult`、`CompletedPlanningResult`）的 `status` 字段现在也带上了 `description='Status of a server-side planning operation'` 描述。

这些改动纯粹是生成器输出更完整元数据的结果，不改变模型的结构、字段类型或校验逻辑，运行时行为保持一致。

## 如何达成设计目的

思路分两步：首先在 `open-api/requirements.txt` 中把 `datamodel-code-generator` 的固定版本从 `0.53.0` 改为 `0.54.0`；然后执行 `make generate` 重新生成 `rest-catalog-open-api.py`，并将生成结果一并提交。两个文件的改动共同构成一次完整的工具升级。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：将 datamodel-code-generator 锁定版本升级到 0.54.0。

**工作逻辑**：
将 `datamodel-code-generator==0.53.0` 改为 `datamodel-code-generator==0.54.0`。该 requirements 文件被 Makefile 的 `install` 目标使用，确保开发者和 CI 使用一致的生成器版本。这是一次次版本（minor）升级，可能引入新的生成特性（如本提交所见 example/description 输出能力）但应保持向后兼容。

### `open-api/rest-catalog-open-api.py` (+184/-12 lines)

**修改目的**：用新版本生成器重新生成 Python Pydantic 模型，反映 0.54.0 对 example 与 description 元数据的输出改进。

**工作逻辑**：
改动集中在两类字段上，均为生成器输出行为变化所致：

1. **表达式类型字段补充 example**：对 `TrueExpression`、`FalseExpression`（原本为 `Field('true', const=True)` / `Field('false', const=True)`）、`UnaryExpression`、`LiteralExpression`、`SetExpression`（原本为裸 `Literal[...]` 无 Field 包装）、`AndOrExpression`、`NotExpression` 的 `type` 字段，现在统一加上 `Field(..., example=[...])`，example 列表为 YAML 中 `ExpressionType` schema 定义的完整表达式类型枚举值。对于原本没有 `Field()` 包装的字段，新版本额外补充了 `Field(...)` 包装以承载 example。

2. **规划状态字段补充 description**：`AsyncPlanningResult.status`、`EmptyPlanningResult.status`、`FailedPlanningResult.status`、`CompletedPlanningResult.status` 现在加上 `description='Status of a server-side planning operation'`。其中 `EmptyPlanningResult.status`（值为 `'cancelled'`）原本是裸 `Literal['cancelled']`，现在也补充了 `Field(..., description=...)` 包装。

这些 example/description 在 OpenAPI 规范中早已定义，0.54.0 生成器在 `--use-schema-description` 选项下将它们透传到生成的 Pydantic 字段中，使生成的模型在文档展示（如 Swagger/OpenAPI 文档生成）和 IDE 提示中能展示更丰富的示例与说明，但不影响模型的校验语义。

## 总结

本次提交将 OpenAPI Python 模型生成工具 datamodel-code-generator 从 0.53.0 升级到 0.54.0，并同步重新生成 `rest-catalog-open-api.py`。新版本生成器把规范中已定义的 example 示例值和 description 描述补充输出到表达式类型字段与规划状态字段中，使生成的 Pydantic 模型携带更完整的文档元数据，而模型的实际结构与校验逻辑保持不变。
