# 提交 0219：Build: Bump datamodel-code-generator from 0.24.2 to 0.25.0 (#9189)

## 提交信息

- **序号**：0219 / 4088
- **哈希**：68d491e6a344a0e081cc784db28dec60670d4d36
- **短哈希**：68d491e6a
- **日期**：2023-12-05
- **作者**：Fokko Driesprong（dependabot[bot] 共同作者）
- **提交说明**：Build: Bump datamodel-code-generator from 0.24.2 to 0.25.0 (#9189)
- **PR/Issue**：#9189

## 总体目的

这是一次 dependabot 触发的依赖升级：把 `open-api/` 子项目用于"从 OpenAPI YAML 自动生成 Python 客户端模型"的工具 `datamodel-code-generator` 从 0.24.2 升级到 0.25.0（semver minor）。该工具负责把 `rest-catalog-open-api.yaml` 转换为 `rest-catalog-open-api.py`（一组 pydantic `BaseModel` dataclass），是 Iceberg REST Catalog OpenAPI 规范的 Python 客户端代码生成器，用于校验规范、提供示例客户端、以及与 CI 集成确保 yaml 与生成产物保持同步。

此次 minor 版本升级本身没有引入新功能需求，但因 0.25.0 对"多态/union 类型"的命名策略做了调整，重新生成的 `rest-catalog-open-api.py` 中 `ReportMetricsRequest` 相关的若干类名发生了重命名。这是一次例行维护提交，目的是让生成产物与新版本生成器行为对齐，避免后续 CI 校验失败。

## 如何达成设计目的

改动两处：把 `open-api/requirements.txt` 中的版本号固定为 `0.25.0`，然后用新版本生成器重新生成 `rest-catalog-open-api.py`，让生成的 Python 类与新版生成器的命名规则保持一致。生成产物中的命名变化是机械的、由工具决定的，无需手写逻辑。

## 修改详情

### `open-api/requirements.txt`

**修改目的**：固定 `datamodel-code-generator` 版本为 0.25.0。

**工作逻辑**：把 `datamodel-code-generator==0.24.2` 改为 `==0.25.0`。`openapi-spec-validator==0.7.1` 保持不变。`requirements.txt` 是 open-api 子项目的 Python 依赖清单，CI 与本地开发者据此安装生成器。

### `open-api/rest-catalog-open-api.py`

**修改目的**：用新版生成器重新生成的 Python 客户端模型，反映 0.25.0 对 union 类型命名策略的变化。

**工作逻辑**：`ReportMetricsRequest` 在 OpenAPI 规范里是 `CommitReport` 与 `ScanReport` 两个变体的 union（`oneOf`），两个变体各自加上 `report-type` 字段。新旧生成器在为这些隐式生成的子类命名时采用了不同策略：

- **旧（0.24.2）**：union 容器类被命名为 `ReportMetricsRequest2(BaseModel)`，其 `__root__: Union[ReportMetricsRequest, ReportMetricsRequest1]`；两个变体分别是 `ReportMetricsRequest1(CommitReport)` 和 `ReportMetricsRequest(ScanReport)`。即"原始名"被分配给了 `ScanReport` 变体，union 容器得到带 `2` 后缀的名字。
- **新（0.25.0）**：union 容器类被命名为 `ReportMetricsRequest(BaseModel)`，`__root__: Union[ReportMetricsRequest1, ReportMetricsRequest2]`；两个变体分别是 `ReportMetricsRequest1(CommitReport)` 和 `ReportMetricsRequest2(ScanReport)`。即"原始名"被分配给 union 容器本身，变体按 `1`/`2` 顺序编号。

这是更合理的命名：union 容器（用户在 spec 里看到的名字）拿到了原始名，变体用数字后缀。文件末尾的 `update_forward_refs()` 调用也相应从 `ReportMetricsRequest2.update_forward_refs()` 改为 `ReportMetricsRequest.update_forward_refs()`。其余类（`ViewMetadata`/`AddSchemaUpdate`/`CreateTableRequest`/`CreateViewRequest`）的 `update_forward_refs()` 不变。

## 小结

这是一次由 dependabot 驱动的依赖例行升级，把 OpenAPI Python 客户端代码生成器升到 0.25.0 并同步重新生成产物，主要影响是 union 类型命名策略的调整，本身不改变 REST 协议语义。
