# 提交 2962：OpenAPI: use yaml linter (#14686)

## 提交信息

- **序号**：2962 / 4088
- **哈希**：55bfc7e82d03b5038bc5d0da852bd16615486926
- **短哈希**：55bfc7e82
- **日期**：2025-12-05
- **作者**：Kevin Liu
- **提交说明**：OpenAPI: use yaml linter (#14686)
- **PR/Issue**：#14686

## 总体目的

Iceberg 的 REST Catalog OpenAPI 规范文件（`rest-catalog-open-api.yaml` 和 `s3-signer-open-api.yaml`）是项目的核心接口契约，被多语言客户端实现引用。此前项目仅使用 `openapi-spec-validator` 校验规范的结构合法性，但未对 YAML 文件本身的格式风格（缩进、尾随空格、空行、花括号/方括号内空格等）进行约束。这导致文件中积累了大量不一致的格式问题，如 JSON example 块缩进不统一、描述文本中存在尾随空格、多余空行、注释缩进层级错误等，影响可读性和维护性。

本提交引入 `yamllint` 工具对 OpenAPI YAML 文件进行严格格式校验，并一次性修复所有现有格式问题使文件通过校验。yamllint 是一个广泛使用的 YAML 格式检查工具（最初为 Ansible 项目开发），能够检测缩进、空行、尾随空格、行长度等风格问题。通过将 yamllint 集成到 Makefile 的 `lint` 目标中，后续贡献者的 YAML 格式问题可在 CI 阶段被及时发现。

## 如何达成设计目的

整体分三步：一是在 `open-api/` 下新增 `.yamllint` 配置文件，基于 `default` 规则集做适度放宽（行长度上限放宽到 350 以适应 OpenAPI 中常见的长 URL 和描述文本，花括号/方括号内空格不做限制以兼容 JSON 风格的内联示例）；二是在 `requirements.txt` 中添加 `yamllint==1.37.1` 依赖；三是在 `Makefile` 中将原 `lint` 目标拆分为 `validate-spec`（规范结构校验）和 `lint-spec`（YAML 格式校验，使用 `--strict` 模式），`lint` 依赖两者。同时对两个 YAML 文件做一次性格式修复以通过校验。

## 修改详情

### `open-api/.yamllint` (+29 lines, 新文件)

**修改目的**：新增 yamllint 配置文件，定义适用于 OpenAPI 规范的格式规则。

**工作逻辑**：
基于 `default` 规则集进行三处定制：`line-length.max: 350`（默认 80 过严，OpenAPI 中 URL、`$ref` 路径和描述文本经常超过 80 字符）；`braces` 的 `max-spaces-inside: -1`（不限制花括号内空格数，兼容 `example: { "key": "value" }` 这类 JSON 风格内联写法）；`brackets` 同理不限制方括号内空格。`min-spaces-inside: 0` 允许无空格。文件头部包含 Apache 2.0 许可证声明。

### `open-api/Makefile` (+6/-2 lines)

**修改目的**：将 yamllint 集成到构建流程的 lint 目标中。

**工作逻辑**：
原 `lint` 目标仅运行 `openapi-spec-validator` 校验两个 YAML 文件。现在拆分为：`validate-spec` 运行 `openapi-spec-validator`（结构校验），`lint-spec` 运行 `yamllint --strict` 对两个 YAML 文件做格式校验（`--strict` 模式将 warning 也视为 error）。`lint` 目标改为依赖 `validate-spec lint-spec`，确保两者都执行。

### `open-api/requirements.txt` (+1 lines)

**修改目的**：添加 yamllint 依赖。

**工作逻辑**：
新增 `yamllint==1.37.1`，与现有的 `openapi-spec-validator==0.7.2` 和 `datamodel-code-generator==0.36.0` 并列。固定版本号确保构建可复现。

### `open-api/rest-catalog-open-api.yaml` (+265/-275 lines)

**修改目的**：一次性修复所有 yamllint 格式问题，使主规范文件通过严格校验。

**工作逻辑**：
修复涵盖多类格式问题（行数多但每处改动均为纯格式调整，不涉及语义变更）：
- **JSON example 缩进统一**：大量 `example: { ... }` 块的内部缩进从不一致的多级缩进（如 18 空格起）统一为从 `example:` 键起 2 空格递进的标准缩进，涉及 CatalogConfig 响应示例、各错误响应（500/502/504/5XX）示例、ReportMetricsRequest 的 metrics 示例、endpoints 数组示例等。
- **尾随空格清除**：描述文本中的空行和内容行尾随空格被移除，如 `snapshots` 参数描述、`pageToken` 描述、`LoadTableResult` 配置说明等多处。
- **多余空行删除**：删除了路径块之间多余的连续空行（如 `/register` 路径前有两行空行）。
- **注释空格规范**：`format: byte # for compatibility` 改为 `format: byte  # for compatibility`（井号前两个空格，符合 yamllint `comments` 规则）。
- **描述文本内的空行尾随空格**：`LoadTableResult` 等长描述中各段之间的空行上的尾随空格被清除。

### `aws/src/main/resources/s3-signer-open-api.yaml` (+3/-3 lines)

**修改目的**：修复 S3 signer OpenAPI 文件的注释缩进问题。

**工作逻辑**：
将 `paths` 块内的分隔注释 `##############################` / `# Application Schema Objects #` / `##############################` 从 2 空格缩进改为顶层（0 缩进），因为 `components:` 键本身就在顶层，注释应与其对齐而非与 `paths` 内的路径项对齐。

## 总结

本提交为 Iceberg 的 OpenAPI 规范文件引入了 yamllint 格式校验工具，通过新增配置文件、添加依赖、集成到 Makefile lint 目标，并一次性修复所有现有格式问题（缩进统一、尾随空格清除、多余空行删除、注释规范），建立了可持续的 YAML 格式质量保障机制。所有修复均为纯格式调整，不改变规范语义，对 API 契约无任何影响。
