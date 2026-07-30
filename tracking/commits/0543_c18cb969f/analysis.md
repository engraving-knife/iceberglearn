# 提交 0543：Spec 澄清多参数 transform 在不同格式版本下的行为

## 提交信息

- **序号**：0543 / 4088
- **哈希**：c18cb969f6d62d41d296571e6a193fc636934fe7
- **短哈希**：c18cb969f
- **日期**：2024-02-26（AuthorDate/CommitDate 均 2024-02-26 15:21:31 -0800）
- **作者**：Szehon Ho <szehon.apache@gmail.com>
- **提交说明**：Spec: Clarify multi-arg transform behavior for different versions (#9661)
- **PR/Issue**：#9661。这是对 0409（PR #8579，"Spec: Add multi-arg transform"）引入的多参数 transform 规范的后续澄清。

## 总体目的

本提交是 Iceberg 表格式规范（`format/spec.md`）的纯文档变更，不涉及任何代码。它要解决的是 0409 引入多参数 transform（multi-arg transform）后遗留下来的若干规范模糊与版本间行为不一致问题。

**背景**：0409（PR #8579）首次在规范层引入多参数 transform 概念——允许一个 partition transform 或 sort transform 接受多个源列作为输入。0409 给出的序列化约定是：

- 单参数 transform：写 `source-id`，省略 `source-ids`
- 多参数 transform：写 `source-ids`，并把 `source-id` 设为 **-1** 作为"这是多参数 transform"的哨兵信号

但 0409 留下了几个未明确的问题：

1. **V3 的定位不清**：规范没有说明 V3 是否已正式发布、多参数 transform 与 V3 的关系。读者容易误以为多参数 transform 已在 V1/V2 落地，或误以为 V3 已被社区采纳。
2. **V1/V2/V3 三版本下 `source-id` 与 `source-ids` 各自的 required/optional/omitted 状态没有清晰矩阵**：原规范只在 Notes 里用文字描述单参数/多参数两种情形，没有按版本维度系统化呈现，实现者难以一眼判断"在 V3 metadata 里写 partition field 时 `source-id` 该不该写"。
3. **多参数 transform 下 `source-id` 设为 -1 的语义不理想**：-1 不是合法的 field id，老版本 reader 若不识别 `source-ids` 而尝试按 `source-id` 查 schema 字段，会查不到，可能导致异常或静默错误。
4. **未知 transform（unknown transform）的跨版本读取行为不统一**：参考实现的老版本会忽略未知 transform，但规范没有要求所有实现都这么做，也没有明确 V3 reader 的义务。
5. **版本迁移规则缺失**：V3 reader 读 V1/V2 metadata 时如何补全 `source-ids`？V3 writer 写 V1/V2 metadata 时如何处理 `source-id`？这些跨版本读写规则没有在 Appendix E（Format version changes）里写清楚。

本提交的目标就是把这五个问题在规范层面一次性澄清，使多参数 transform 的序列化与跨版本兼容行为有明确、可遵循的契约。

## 如何达成设计目的

设计思路是完全通过编辑 `format/spec.md` 一个文件达成，改动分布在五个区域，核心是：

1. **明确 V3 状态**：在规范开头补一句"Version 3 is under active development and has not been formally adopted"，让读者知道 V3 尚未正式发布，多参数 transform 的 V3 行为是面向未来的约定。

2. **单数→复数措辞修正**：把 partition spec 章节里"The source column, selected by id"改为"The source columns, selected by ids"，与多参数 transform 语义对齐。

3. **用 V1/V2/V3 三列兼容性矩阵替换原 Notes**：把 partition field 和 sort field 的 JSON 字段表从"单行描述 + 编号 Notes"重写为"每个字段一行、带 V1/V2/V3 三列 required/optional/omitted 状态"的矩阵表。这样实现者可以直接查表判断每个字段在每个版本下的写入义务。

4. **改变多参数 transform 在 V1/V2 下的 `source-id` 取值**：从"设为 -1"改为"设为 `source-ids` 的第一个元素"。这是一个有实质语义变化的澄清——它让老版本 reader 即使不识别 `source-ids`，也能在 `source-id` 里看到一个合法的（虽然是部分的）源字段 id，从而更优雅地降级，而不是踩到 -1 这个非法值。

5. **在 Appendix E 的 Version 3 小节补全跨版本读写规则**：新增"Writing v3 metadata / Reading v1 or v2 metadata for v3 / Writing v1 or v2 metadata"三组规则，明确各方向下 `source-id` 与 `source-ids` 的处理方式；并新增"所有 reader 必须能读取带未知 partition transform 的表（忽略之）"的硬性要求。

## 修改详情

### `format/spec.md`

本提交只改 `format/spec.md` 一个文件（+47/-12）。改动按区域分述如下。

#### 区域一：规范开头新增 V3 状态说明

**修改目的**：明确 V3 尚未正式发布，避免读者误解多参数 transform 的版本归属。

**工作逻辑**：在"Versions 1 and 2 of the Iceberg spec are complete and adopted by the community."之后新增一行：

```
**Version 3 is under active development and has not been formally adopted.**
```

这句话把 V3 定位为"开发中、未正式采纳"，与 V1/V2 的"complete and adopted"形成对照。它为后续所有 V3 相关规则定了基调——这些规则是面向未来的约定，实现者可以提前对齐，但不应误以为 V3 已是正式版本。

#### 区域二：Partition Spec 章节单数→复数

**修改目的**：让分区字段定义的措辞与多参数 transform 语义一致。

**工作逻辑**：把

```
The source column, selected by id, must be a primitive type ...
```

改为

```
The source columns, selected by ids, must be a primitive type ...
```

`column` → `columns`、`by id` → `by ids`，反映一个 partition field 现在可以由多个源列导出。

#### 区域三：Sort Order 章节新增序列化引用

**修改目的**：与 Partition Spec 章节对称，补上 sort order 的 JSON 序列化引用指引。

**工作逻辑**：在 sort order 字段定义之后新增一句"For details on how to serialize a sort order to JSON, see Appendix C."。此前 partition spec 章节已有同样的引用句，sort order 章节缺失，本次补齐。

#### 区域四：Partition Field JSON 表重写为 V1/V2/V3 矩阵

**修改目的**：用清晰的版本兼容性矩阵替换原来的单行描述 + 编号 Notes，并改变多参数 transform 下 `source-id` 的取值约定。

**工作逻辑**：原表格只有一个 `Partition Field` 行展示整体 JSON 结构，并用两条 Notes 说明单参数/多参数两种情形。新表格拆分为按字段逐行列出，每行带 V1/V2/V3 三列状态：

| V1 | V2 | V3 | Field | JSON representation | Example |
|----------|----------|----------|------------------|---------------------|--------------|
| required | required | omitted | `source-id` | `JSON int` | 1 |
| optional | optional | required | `source-ids` | `JSON list of ints` | `[1,2]` |
| | required | required | `field-id` | `JSON int` | 1000 |
| required | required | required | `name` | `JSON string` | `id_bucket` |
| required | required | required | `transform` | `JSON string` | `bucket[16]` |

关键信息：
- `source-id`：V1/V2 required，V3 omitted（V3 完全用 `source-ids` 取代）
- `source-ids`：V1/V2 optional（多参数时才写），V3 required
- `field-id`：V1 无要求（空），V2/V3 required（V2 引入）
- `name`/`transform`：三版本均 required

同时删除原表格里单独的 `Partition Field` 行（其信息已分散到新矩阵各字段行）。

随后用一段说明替换原 Notes 1、2：

```
In v3 metadata, writers must use only `source-ids` because v3 requires reader support for multi-arg transforms. In v1 and v2 metadata, writers must always write `source-id`; for multi-arg transforms, writers must produce `source-ids` and set `source-id` to the first ID from the field ID list.
```

**这是本提交最重要的语义变化**：多参数 transform 在 V1/V2 下，`source-id` 从原来的 **-1** 改为 **`source-ids` 的第一个元素**。理由是 -1 不是合法 field id，老 reader 不识别 `source-ids` 时按 `source-id` 查 schema 会失败；改用首元素后，老 reader 至少能看到一个合法的（虽不完整的）源字段 id，降级更优雅。

再补一段未知 transform 的处理约定：

```
Older versions of the reference implementation can read tables with transforms unknown to it, ignoring them. But other implementations may break if they encounter unknown transforms. All v3 readers are required to read tables with unknown transforms, ignoring them. Writers should not write using partition specs that use unknown transforms.
```

这段区分了"参考实现老版本"（会忽略未知 transform，但不强制）、"其他实现"（可能崩溃）、"V3 reader"（强制要求忽略未知 transform）三者的义务，并要求 writer 不要写未知 transform。

#### 区域五：Sort Field JSON 表重写为 V1/V2/V3 矩阵

**修改目的**：与 Partition Field 表对称，把 sort field 也改为版本兼容性矩阵。

**工作逻辑**：原表格只有一个 `Sort Field` 行 + 两条 Notes（与 partition field 几乎相同）。新表格拆为按字段逐行：

| V1 | V2 | V3 | Field | JSON representation | Example |
|----------|----------|----------|------------------|---------------------|-------------|
| required | required | required | `transform` | `JSON string` | `bucket[4]` |
| required | required | omitted | `source-id` | `JSON int` | 1 |
| | | required | `source-ids` | `JSON list of ints` | `[1,2]` |
| required | required | required | `direction` | `JSON string` | `asc` |
| required | required | required | `null-order` | `JSON string` | `nulls-last` |

与 partition field 矩阵的差别：sort field 的 `source-ids` 在 V1/V2 是空（未定义），而 partition field 的 `source-ids` 在 V1/V2 是 optional。这反映了 0409 引入多参数 transform 时对两种字段的处理略有差异。`source-id` 同样在 V3 omitted。

随后的说明文字与 partition field 完全相同（同样的 V3 用 `source-ids`、V1/V2 多参数时 `source-id` 取首元素、未知 transform 处理三段），保证两种字段语义一致。

#### 区域六：Appendix E Version 3 小节新增跨版本读写规则

**修改目的**：在版本变更附录里系统化给出 V3 与 V1/V2 之间 `source-id`/`source-ids` 的迁移规则。

**工作逻辑**：在原有的"Default values are added to struct fields in v3"和"Types `timestamp_ns` and `timestamptz_ns` are added in v3"之后，新增一段"All readers are required to read tables with unknown partition transforms, ignoring them."（与正文的未知 transform 约定呼应，但在附录里作为 V3 的硬性要求列出）。

随后新增三组规则：

**Writing v3 metadata:**
- Partition Field and Sort Field JSON:
  - `source-ids` was added and is required
  - `source-id` is no longer required and should be omitted; always use `source-ids` instead

**Reading v1 or v2 metadata for v3:**
- Partition Field and Sort Field JSON:
  - `source-ids` should default to a single-value list of the value of `source-id`

这条规则解决了 V3 reader 读老表时的兼容问题：老表只有 `source-id`，V3 reader 把它包装成单元素列表 `[source-id]` 作为 `source-ids`，统一后续处理逻辑。

**Writing v1 or v2 metadata:**
- Partition Field and Sort Field JSON:
  - For a single-arg transform, `source-id` should be written; if `source-ids` is also written it should be a single-element list of `source-id`
  - For multi-arg transforms, `source-ids` should be written; `source-id` should be set to the first element of `source-ids`

这条规则把正文的"V1/V2 多参数时 `source-id` 取首元素"约定在附录里再次明确，并补充了单参数时可选写 `source-ids`（须为单元素列表）的细节。

## 小结

**成效**：本提交以纯文档变更（+47/-12）系统化澄清了多参数 transform 在 V1/V2/V3 三个格式版本下的序列化与跨版本兼容行为。最关键的语义变化是把 V1/V2 多参数 transform 的 `source-id` 从哨兵值 -1 改为 `source-ids` 的首元素，提升了老版本 reader 的降级体验；同时通过 V1/V2/V3 三列矩阵表和 Appendix E 的迁移规则，让实现者有清晰、可查的契约可依，并为 V3 的正式发布做了规范铺垫。

**影响范围**：仅影响 `format/spec.md`，不涉及任何代码。但由于它改变了多参数 transform 在 V1/V2 下的 `source-id` 取值约定（-1 → 首元素），各客户端实现（Java 参考实现、Spark、Flink、Python、Go 等）的元数据序列化/反序列化代码需要跟进这一约定。本提交本身不含代码改动，是实现跟进的规范依据。

**与 0409 的关联**：0409（PR #8579）首次引入多参数 transform，规定了 `source-id`=-1 的哨兵方案；0543 是对 0409 的后续打磨，修正了 -1 方案的缺陷并补全版本矩阵。两个提交都是规范先行、实现跟进的产物。

**回迁到 1.4.x 的注意事项**：

1. 本提交是纯 spec 文档变更，无代码冲突风险，cherry-pick 到 1.4.x 安全。1.4.x 的 `format/spec.md` 若已包含 0409 的多参数 transform 规范，则可直接应用本提交的澄清；若 1.4.x 尚未包含 0409，则需先回迁 0409 再回迁 0543，否则本提交的"从 -1 改为首元素"等改动会缺乏上下文。
2. 回迁后需注意：1.4.x 上的 Java 参考实现（及 Spark/Flink 集成）若已按 0409 的 -1 方案实现多参数 transform 序列化，则需同步代码改动以符合新规范（`source-id` 取首元素）。本提交不含这些代码改动，代码改动需另找对应 PR。
3. 由于 V3 尚未正式发布（本提交明确声明），1.4.x 作为维护分支可以选择只回迁 spec 文档而不跟进 V3 相关代码实现，不影响 V1/V2 表的现有行为。但"多参数 transform 在 V1/V2 下 `source-id` 取首元素"这一变化若要生效，仍需配套代码改动。
