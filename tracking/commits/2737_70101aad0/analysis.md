# 提交 2737：Build: Bump datamodel-code-generator from 0.33.0 to 0.35.0

## 提交信息

- **序号**：2737 / 4088
- **哈希**：70101aad06acf22bce7a70f223f17a34b71db7da
- **短哈希**：70101aad0
- **日期**：2025-10-12 16:14:31 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.33.0 to 0.35.0
- **PR/Issue**：#14300

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，但与纯 Java 依赖升级不同，此提交还包含了自动生成代码的更新。datamodel-code-generator 是一个 Python 工具，用于从 OpenAPI 规范文件生成 Pydantic 数据模型代码。Iceberg 的 `open-api` 模块使用该工具从 REST Catalog OpenAPI 规范生成 Python 客户端模型代码（`rest-catalog-open-api.py`）。

本次升级将 datamodel-code-generator 从 0.33.0 升级到 0.35.0，跨越两个次版本（semver-minor），新版本对生成代码的行为有直接影响——生成的 Pydantic 模型中，对于 `__root__` 类型字段，不再默认使用 `Optional[...] = None`，而是直接生成非 Optional 的类型。这反映了新版本生成器对 schema 中未标记为 nullable 的字段的更精确处理。

## 如何达成设计目的

升级分两步：
1. 修改 `requirements.txt` 中的版本声明
2. 使用新版本的 datamodel-code-generator 重新生成 `rest-catalog-open-api.py`，使生成代码与新版本生成器的行为一致

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 的版本声明。

**工作逻辑**：将 `datamodel-code-generator==0.33.0` 修改为 `datamodel-code-generator==0.35.0`。

### `open-api/rest-catalog-open-api.py` (+2/-2 lines)

**修改目的**：使用新版本生成器重新生成的 Python 模型代码。

**工作逻辑**：两处变更反映了新版本生成器对根类型（`__root__`）字段的处理方式变化：
- `SnapshotReferences.__root__`：从 `Optional[Dict[str, SnapshotReference]] = None` 变为 `Dict[str, SnapshotReference]`
- `Metrics.__root__`：从 `Optional[Dict[str, MetricResult]] = None` 变为 `Dict[str, MetricResult]`

新版本不再为这些字段生成 Optional 包装和 None 默认值，这意味着对应的 OpenAPI schema 中这些字段未被标记为可空，生成器行为更加准确。

## 总结

这是依赖升级与代码重新生成的组合提交。升级 datamodel-code-generator 从 0.33.0 到 0.35.0，并更新自动生成的 Python 模型代码。新版本生成器对 `__root__` 类型字段的 Optional 处理更加精确，使生成代码更准确地反映 OpenAPI 规范的定义。该提交由 dependabot 和 Fokko Driesprong 共同完成（包含代码更新部分）。
