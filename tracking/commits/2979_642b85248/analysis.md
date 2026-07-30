# 提交 2979：OpenAPI: Make namespace separator configurable by server (#14448)

## 提交信息

- **序号**：2979 / 4088
- **哈希**：642b852487c9396448334afd1a277286ee885ae8
- **短哈希**：642b85248
- **日期**：2025-12-08
- **作者**：Eduard Tudenhoefner
- **提交说明**：OpenAPI: Make namespace separator configurable by server (#14448)
- **PR/Issue**：#14448

## 总体目的

Iceberg 的命名空间（namespace）支持多级（multipart），例如 `db.schema`。在 REST Catalog 协议中，命名空间作为单段字符串出现在 URL 路径或查询参数中时，需要一个分隔符把多级命名空间的各部分连接起来。长期以来协议规定使用单元分隔符 `0x1F`（URL 编码为 `%1F`）作为唯一分隔符。该字符不可打印、不直观，给客户端构造请求、调试和日志阅读带来不便，也限制了用户根据自身环境选择更合适分隔符（如 `.`）的能力。

本提交是 PR #14448 的 OpenAPI 规范部分，目的是把命名空间分隔符从"硬编码 `0x1F`"改为"由服务端通过 `/v1/config` 接口返回的 `namespace-separator` 覆盖项 advertised 给客户端，默认仍为 `0x1F`"。规范同时要求服务端在解码命名空间时必须同时接受"advertised 的分隔符"和"`0x1F`"两种分隔符，以保证与旧客户端的向后兼容。这一改动是更大的"可配置 namespace 分隔符"特性（Core 实现见序号 2982 的提交 #10877）的协议侧契约更新，使客户端能根据服务端通告的分隔符构造请求，而不是被硬编码绑死。

需要注意的是：本提交只修改了 OpenAPI YAML 规范的描述文本，没有改动协议字段、schema 或代码生成产物；实际的服务端实现与客户端适配在配套的 Core/各集成模块提交中完成（参见序号 2982）。

## 如何达成设计目的

整体思路是在 OpenAPI 规范中两处提到命名空间分隔符的描述文本里，明确写出"分隔符由 `/config` 覆盖项 `namespace-separator` 指定、默认 `0x1F`、服务端必须同时接受 advertised 分隔符与 `0x1F`、`namespace-separator` 应以 URL 编码形式提供"。这样既保留了默认行为的向后兼容，又为服务端自定义分隔符提供了协议层依据。改动文件仅 `open-api/rest-catalog-open-api.yaml` 一个。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+8/-2 lines)

**修改目的**：更新 OpenAPI 规范中两处关于命名空间分隔符的描述，使其支持服务端通过 `/config` 通告自定义分隔符。

**工作逻辑**：
修改了两处描述：

1. `paths` 下 `List Namespaces` 接口的 `parent` 查询参数描述。原文为 `If parent is a multipart namespace, the parts must be separated by the unit separator (0x1F) byte.`，改为：`If parent is a multipart namespace, the parts must be separated by the namespace separator as indicated via the /config override namespace-separator, which defaults to the unit separator 0x1F byte (url encoded %1F). To be compatible with older clients, servers must use both the advertised separator and 0x1F as valid separators when decoding namespaces. The namespace-separator should be provided in a url encoded form.`

2. `components/parameters` 下 `namespace` 路径参数的描述。原文为 `Multipart namespace parts should be separated by the unit separator (0x1F) byte.`，改为同样的三段说明：分隔符由 `/config` 的 `namespace-separator` 指定（默认 `0x1F`，URL 编码 `%1F`）；服务端必须同时接受 advertised 分隔符与 `0x1F` 以兼容旧客户端；`namespace-separator` 应以 URL 编码形式提供。

这两处改动明确了协议契约：客户端应读取 `/v1/config` 返回的 `namespace-separator` 并据此构造多级命名空间字符串；服务端在解码时既要识别 advertised 分隔符，也要继续识别 `0x1F` 以兼容只懂旧分隔符的客户端。`namespace-separator` 配置项本身应以 URL 编码形式提供（因为分隔符可能是特殊字符）。描述层面没有改动任何字段类型或必填性。

## 总结

该提交更新了 Iceberg REST Catalog OpenAPI 规范中关于命名空间分隔符的两处描述，把原本硬编码的 `0x1F` 分隔符改为可由服务端通过 `/config` 的 `namespace-separator` 覆盖项通告，默认仍为 `0x1F`，并要求服务端同时接受 advertised 分隔符与 `0x1F` 以保证向后兼容。核心价值在于为服务端自定义命名空间分隔符（如改用 `.`）提供协议层契约依据，提升可读性与部署灵活性，是更大特性（Core 实现见序号 2982）的协议侧前置改动。
