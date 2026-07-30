# 提交 3040：Build: Bump datamodel-code-generator from 0.43.1 to 0.46.0 (#14905)

## 提交信息

- **序号**：3040 / 4088
- **哈希**：1c5bb017c2161aa568d75e17a4de7f42bcb37d02
- **短哈希**：1c5bb017c
- **日期**：2025-12-22
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump datamodel-code-generator from 0.43.1 to 0.46.0 (#14905)
- **PR/Issue**：#14905

## 总体目的

本提交升级 Python 工具 `datamodel-code-generator`（命令名 `datamodel-codegen`）从 `0.43.1` 到 `0.46.0`，并将由该工具重新生成的 Pydantic 模型文件 `rest-catalog-open-api.py` 一并更新。`datamodel-code-generator` 是一个根据 OpenAPI/JSON Schema 规范自动生成 Pydantic 模型代码的工具，在 Iceberg 项目中用于 `open-api/` 目录下的代码生成流程：`open-api/Makefile` 的 `generate` 目标调用 `datamodel-codegen`，以 `rest-catalog-open-api.yaml`（REST Catalog 的 OpenAPI 规范）为输入，输出 `rest-catalog-open-api.py`（Pydantic 模型定义）。

该生成的 Python 文件是 Iceberg REST Catalog OpenAPI 规范的可执行表达，供 Python 客户端/服务端实现参考与校验使用。当 OpenAPI YAML 规范更新时，维护者需运行 `make generate` 重新生成 `.py` 文件并提交。本次升级在规范本身未变的情况下，仅因工具版本变化而触发了生成代码的风格迁移。

Dependabot 元数据显示该依赖为 `direct:production` 类型、`version-update:semver-minor`（次版本升级）。本次升级的核心影响在于：0.46.0 版本的生成器改变了类型注解的输出风格，将旧的 `typing` 模块导入式注解替换为 Python 3.9+ 的原生内置类型语法。这是一个跨 3 个次版本（0.44/0.45/0.46）的累积升级，工具行为的变化直接体现在重新生成的 500 余行代码差异中。

## 如何达成设计目的

改动分两部分：一是更新 `open-api/requirements.txt` 中工具版本钉死，二是在升级后重新运行生成器并用新输出替换 `rest-catalog-open-api.py`。整体思路是通过工具升级使生成的 Pydantic 模型代码跟上现代 Python 类型注解惯例，同时保持文件开头的 `from __future__ import annotations`（PEP 563）使注解以字符串形式求值，从而在较低的 `--target-python-version 3.8` 下也能使用 `dict[str, str]`、`X | None` 等新语法而不引发运行时错误。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 工具版本钉死。

**工作逻辑**：
将 `datamodel-code-generator==0.43.1` 修改为 `datamodel-code-generator==0.46.0`。该 requirements.txt 由 `open-api/Makefile` 的 `install` 目标（`pip install -r requirements.txt`）使用，钉死工具版本以保证 `make generate` 的可重复性。文件中同时包含 `openapi-spec-validator==0.7.2`（规范校验）与 `yamllint==1.37.1`（YAML lint），本次仅升级生成器。

### `open-api/rest-catalog-open-api.py` (+251/-259 lines)

**修改目的**：用升级后的 datamodel-code-generator 0.46.0 重新生成 Pydantic 模型，迁移到原生类型注解风格。

**工作逻辑**：
重新生成后，文件内容在工作逻辑上与旧版本等价（同样是 REST Catalog 规范的 Pydantic 模型），但类型注解的写法发生系统性迁移，主要变化模式如下：

1. **去除 `typing` 聚合导入**：文件顶部由 `from typing import Dict, List, Literal, Optional, Union` 简化为 `from typing import Literal`。`Dict`、`List`、`Optional`、`Union` 不再使用，仅保留 `Literal`（因为 PEP 604 的 `Literal` 仍需从 typing 导入）。

2. **容器类型内置化**：所有 `Dict[str, str]` → `dict[str, str]`、`List[str]` → `list[str]`、`List[PartitionField]` → `list[PartitionField]` 等。例如 `PartitionSpec` 的 `fields: List[PartitionField]` 变为 `fields: list[PartitionField]`，`SetPropertiesUpdate` 的 `updates: Dict[str, str]` 变为 `updates: dict[str, str]`。

3. **可选类型用 `| None`**：所有 `Optional[X]` → `X | None`。例如 `Snapshot` 的 `parent_snapshot_id: Optional[int]` 变为 `parent_snapshot_id: int | None`，`OAuthError` 的 `error_description: Optional[str]` 变为 `error_description: str | None`。

4. **联合类型用 `|`**：所有 `Union[A, B]` → `A | B`，多分支联合改为括号包裹的多行 `|` 形式。例如 `MetricResult.__root__: Union[CounterResult, TimerResult]` 变为 `__root__: CounterResult | TimerResult`；长联合如 `PrimitiveTypeValue.__root__` 由 `Union[BooleanTypeValue, ...]` 变为多行括号形式 `BooleanTypeValue | IntegerTypeValue | ...`。

5. **带默认值的字段格式微调**：对于 `__root__` 同时带 `Field(...)` 描述符的联合类型，生成器改用括号包裹后赋值的写法（如 `FetchPlanningResult.__root__` 将 `Field(...)` 包裹在括号内）。

6. **多行列表折叠**：部分原来展开多行的简单字段被压缩为单行（如 `identifier_field_ids: Optional[List[int]]` 折叠），这是新版本格式化策略的差异。

这些变化纯粹是代码风格迁移，模型结构、字段别名（如 `alias='snapshot-id'`）、约束（`ge`/`le`/`unique_items`）、描述文本均未改变。由于文件首部保留 `from __future__ import annotations`，所有注解在运行时以字符串形式惰性求值，因此 `dict`/`list` 下标与 `|` 联合语法即使在 `--target-python-version 3.8` 下也能正常工作，不会引发运行时 `TypeError`。

## 总结

本提交升级 OpenAPI 代码生成工具 datamodel-code-generator（0.43.1 → 0.46.0），并随之重新生成 `rest-catalog-open-api.py`。核心价值是让生成的 Pydantic 模型代码迁移到 Python 3.9+ 原生类型注解风格（`dict`/`list`/`X | None`/`A | B`），减少对 `typing` 模块的依赖，代码更现代简洁。改动是纯风格层面的，模型语义不变，借助 `from __future__ import annotations` 保证了在目标 Python 3.8 下的兼容性。
