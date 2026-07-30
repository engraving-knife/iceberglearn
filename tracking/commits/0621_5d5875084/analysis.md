# 提交 0621：根据新反馈修复 REST 分页规范要求

## 提交信息

- **序号**：0621 / 4088
- **哈希**：5d58750841d3a35a352fcf99d315f10626399e71
- **短哈希**：5d5875084
- **日期**：2024-03-22（Fri Mar 22 17:58:14 2024 -0400）
- **作者**：Rahil C <32500120+rahil-c@users.noreply.github.com>（与 Rahil Chertara <rchertar@amazon.com> 共同作者）
- **提交说明**：Spec: Fix REST pagination requirements based on new feedback (#9917)
- **PR/Issue**：#9917

## 总体目的

本提交是对 0554 号提交（PR #9660，为 namespaces/tables/views 列表 API 引入分页协议）的**规范层修复**。0554 把基于 opaque token 的游标分页写入 OpenAPI 规范后，社区在后续评审与潜在实现准备中提出了一些反馈，主要集中在原描述在"服务端到底应该怎么做"上不够明确，存在多种合理读法，会导致不同服务端实现行为不一致、客户端无法可靠判断是否已到末页。

具体要解决的问题：

1. **末页信号不明确**：0554 的描述只说"还有更多结果时返回 `next-page-token`"，但没说最后一页时 `next-page-token` 应该是 `null`、空字符串、还是字段缺失。客户端因此无法用统一规则判断"是否结束"。
2. **未发送 `pageToken` 时服务端行为不一致**：0554 暗示"客户端通过发送空 `pageToken` 来发起分页"，但若客户端**根本不发送** `pageToken` 参数，支持分页的服务端是该一次性返回全部结果、还是按默认页大小分页？原描述含糊。
3. **不支持分页的服务端响应字段处理不清**：0554 说"不支持分页的服务端会忽略 `next-page-token` 并返回所有结果"，但"忽略"一词既可以理解为"不返回该字段"，也可以理解为"返回空值"——两种实现都合规，客户端却难以统一处理。
4. **`PageToken` 类型本身不允许 null**：0554 中 `PageToken` 是 `type: string`（不可空），但协议语义要求末页时 `next-page-token` 必须能表达"无下一页"，类型层与语义层不一致。

本提交通过重写 `PageToken` 的 description 并将其改为 nullable，把上述每种服务端/客户端场景的"必须/应该"行为一次性钉死，消除歧义。

## 如何达成设计目的

设计者选择**只改规范文本 + 类型可空性**，不动端点参数、不动响应结构、不引入新字段，是一次最小化、聚焦的协议澄清。整体思路：

### 1. 把 `PageToken` 改为 nullable

在 YAML 中加入 `nullable: true`，在 Python（Pydantic v1）中把 `__root__: str = Field(...)` 改为 `__root__: Optional[str] = Field(None)`。这从类型层面允许 `next-page-token` 取值为 `null`，与下文"末页必须返回 null"的语义对齐。注意：查询参数 `pageToken` 本身通过 `allowEmptyValue: true` 已可接受空值，本次类型放宽主要服务于**响应字段** `next-page-token`。

### 2. 重写 description，按"支持分页 / 不支持分页 / 客户端"三类角色给出 MUST/SHOULD 规则

新描述用 RFC 2119 风格的关键词（must / should）把每一种场景的合规行为写死，避免实现者自由发挥：

- **客户端发起首次请求**：从"will initiate"改为"may initiate"——发送空 `pageToken` 只是"可以"做的发起方式之一，不再是强制语义。
- **支持分页的服务端（还有更多结果）**：should 识别 `pageToken` 并返回 `next-page-token`。
- **支持分页的服务端（已到末页）**：**必须**返回 `null` 值的 `next-page-token`。这是新增的硬性要求。
- **支持分页的服务端（请求未带 `pageToken`）**：**必须**一次性返回全部结果，且 `next-page-token` 设为 `null`。这把"不发 pageToken = 一次性全量"明确为 MUST，消除"是否默认分页"的歧义。
- **不支持分页的服务端**：should 忽略 `pageToken` 并一次性返回全部；`next-page-token` **必须**从响应中省略（omitted）——注意这里不是返回 null，而是不出现该字段，与"支持分页但末页"的 null 形成对照。
- **客户端终止条件**：**必须**把 `next-page-token` 为 `null` **或字段缺失**都视为列表结束。这条让客户端能用同一套逻辑兼容"支持分页的末页（null）"和"不支持分页的响应（缺失）"两种情况。

### 3. 双语言同步

与 0554 一致，YAML 与 Python 双侧同步修改，保证两种生成路径产物一致。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：澄清 `PageToken` schema 的语义并允许 null。

**工作逻辑**：

- `components.schemas.PageToken.description`：用一段更长的多行描述替换原描述，按"客户端发起 → 支持分页服务端的末页/未带 token 两种情况 → 不支持分页服务端 → 客户端终止判断"的顺序逐一写明 MUST/SHOULD 规则（详见上文"如何达成设计目的"）。
- `components.schemas.PageToken` 新增 `nullable: true`，与 description 中"server must return `null` value for the `next-page-token` in the last response"在类型层对齐。
- `type: string` 保留不变——OpenAPI 3.0 中 `nullable: true` 是让 `string` 类型可取 null 的标准写法。

注意：本提交**没有改动** `components.parameters.page-token`（查询参数定义）与 `components.parameters.page-size`，也没有改动 `ListTablesResponse` / `ListNamespacesResponse` 中的 `next-page-token` 字段引用——它们仍然 `$ref` 到 `PageToken`，但因为 `PageToken` 现在可空，这些响应字段自动获得了"可取 null"的能力。这是一次典型的"改一处 schema，多处响应受益"的设计。

### `open-api/rest-catalog-open-api.py`

**修改目的**：同步 Pydantic 模型，让 Python 客户端能正确表达"可空的 PageToken"。

**工作逻辑**：

```python
class PageToken(BaseModel):
    __root__: Optional[str] = Field(
        None,
        description='An opaque token that allows clients to make use of pagination ...'
    )
```

- `__root__` 类型从 `str` 改为 `Optional[str]`，默认值从 `...`（Pydantic v1 的"必填"占位符）改为 `None`。这意味着 `PageToken` 实例现在可以持有 `None` 值，序列化时会输出 `null`。
- description 与 YAML 完全同步重写。
- 由于 `ListTablesResponse.next_page_token` 与 `ListNamespacesResponse.next_page_token` 在 0554 中已声明为 `Optional[PageToken]`，本身就可空；本次改动让 `PageToken` 内部也可空，使得"字段存在但值为 null"（`{"next-page-token": null}`）与"字段缺失"都能被 Pydantic 正确建模与解析。

## 小结

本提交是对 0554 分页规范的**协议澄清补丁**，通过把 `PageToken` 改为 nullable 并用 MUST/SHOULD 关键词重写描述，解决了原规范在"末页信号、未带 token 行为、不支持分页响应字段处理"三处的歧义，使不同服务端实现能产出客户端可统一消费的响应。

**影响范围**：

- 仅影响 `open-api/` 目录两个文件，不动任何运行时代码、端点签名、参数列表。
- 由于只改描述与可空性，对已有客户端的反序列化无破坏：原本把 `next-page-token` 当 string 处理的客户端，遇到 null 时需要额外判空——但这正是本提交要强制客户端正确处理的场景，属于"促使客户端合规"的良性变化。
- 对服务端实现者，本提交把"末页返回 null"、"未带 pageToken 时一次性返回全部并以 null 收尾"、"不支持分页时省略 next-page-token"三类行为从建议升级为硬性要求，未来在 1.4.x 或 main 上实现分页的服务端必须按此规则实现，否则不合规。

**回迁到 1.4.x 的注意事项**：

- 回迁非常安全：纯规范文本与类型可空性调整，不引入新字段、不删除已有字段、不改端点。
- 若 1.4.x 已回迁 0554，则本提交是**强烈建议同步回迁**的配套修复——只回迁 0554 不回迁本提交，会让 1.4.x 规范保留歧义，未来基于 1.4.x 实现分页的服务端/客户端可能走偏。
- 若 1.4.x 完全未回迁 0554，则可考虑把 0554 + 0621 一起回迁，作为完整的分页协议基础。
- Pydantic 模型仍使用 v1 风格（`__root__`、`Optional`），若 1.4.x 已升级到 Pydantic v2，`__root__` 需改写为 `RootModel` 风格（与 0554 相同的注意点）。
- 规范层那个预先存在的复用（`listViews` 响应引用 `ListTablesResponse`）本提交仍未修复，回迁时无需额外处理，`next-page-token` 的可空性会自动通过 `$ref` 传递到 `listViews` 响应。
