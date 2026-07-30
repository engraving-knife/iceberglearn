# 提交 3968：REST: Fix schema of data-access object in REST spec (#16594)

## 提交信息

- **序号**：3968 / 4088
- **哈希**：be27af46df4316b41787a62382c5aa330a173c1e
- **短哈希**：be27af46d
- **日期**：2026-06-30 13:28:42 -0700
- **作者**：Alexandre Dutra
- **提交说明**：REST: Fix schema of data-access object in REST spec (#16594)
- **PR/Issue**：#16594

## 总体目的

本提交修复了 REST catalog 规范中 `data-access` 查询参数的 schema 定义错误。该参数用于请求加载数据时所需的凭证方式（如 `vended-credentials` 或 `remote-signing`），其示例值 `"vended-credentials,remote-signing"` 表明它应该是一个逗号分隔的列表。

然而，规范中将其定义为单个 `string` 类型的 `enum`（只允许一个值），而非一个 `array`（允许多个值）。这意味着客户端无法同时请求两种数据访问方式，与示例和实际使用场景不符。

## 如何达成设计目的

将 `data-access` 参数的 schema 从 `type: string` + `enum` 改为 `type: array` + `items: { type: string, enum: [...] }`，正确表示可以接受多个枚举值的列表。`style: simple` 和 `explode: false` 保持不变（符合 OpenAPI 中逗号分隔列表参数的约定）。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+6/-4 lines)

**修改目的**：修复 data-access 参数的 schema 类型。

**工作逻辑**：
```yaml
# 旧定义：单个枚举值
schema:
  type: string
  enum:
    - vended-credentials
    - remote-signing

# 新定义：枚举值数组
schema:
  type: array
  items:
    type: string
    enum:
      - vended-credentials
      - remote-signing
```
`style: simple` 和 `explode: false` 保持不变，对应 RFC 6570 的简单风格逗号分隔列表序列化。

## 总结

本提交修复了 REST spec 中的一个 schema 定义错误，使 `data-access` 参数正确支持多值列表，与示例值和实际使用场景一致。这是一个规范正确性修复，确保客户端可以同时请求多种数据访问方式。
