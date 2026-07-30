# 提交 0940：Docs: Clarify defaults for distribution mode (#10575)

## 提交信息

- **序号**：0940 / 4088
- **哈希**：4a0ae22199375e34f9033bed9781da3dc90d53c6
- **短哈希**：4a0ae2219
- **日期**：2024-07-16（Tue Jul 16 13:58:13 2024 -0700）
- **作者**：Szehon Ho \<szehon.apache@gmail.com\>
- **提交说明**：Docs: Clarify defaults for distribution mode (#10575)
- **PR/Issue**：#10575

## 总体目的

本提交澄清 Iceberg 文档中关于 `write.distribution-mode`（写分布模式）默认值的描述。此前在 `docs/docs/configuration.md` 的表属性表中，`write.distribution-mode` 的默认值被标注为 `none`。这一标注虽然反映了 Iceberg 核心层面的默认值（即不进行 shuffle），但却与各计算引擎实际使用的默认值不一致——例如 Spark 引擎在写入时实际把 `hash` 作为默认分布模式（参见 `spark-writes.md` 中 "This mode is the new default and requests that Spark uses a hash-based exchange..."）。这种文档与引擎实际行为的不一致容易误导用户，让人误以为在 Spark 中不显式设置 `write.distribution-mode` 就会使用 `none` 模式（不 shuffle），而实际上 Spark 默认会采用 `hash` 模式。

本次修改的动机是消除这一歧义：明确说明 `none` 是 Iceberg 核心规范层面的默认值，但各引擎可能有自己的默认值，并引导用户到对应引擎文档（如 Spark Writes）查看具体默认值。同时在 Spark 配置文档的写选项表中补充 `distribution-mode` 写选项，指明其默认值同样参见 Spark Writes 文档。

## 如何达成设计目的

实现方式是修改两份文档：

1. **`docs/docs/configuration.md`**：把 `write.distribution-mode` 行的"默认值"列从 `none` 改为 `none, see engines for specific defaults, for example [Spark Writes](spark-writes.md#writing-distribution-modes)`，明确核心默认是 `none` 但引擎可覆盖，并给出 Spark 的具体参考链接。

2. **`docs/docs/spark-configuration.md`**：在写选项（Write Options）表中新增一行 `distribution-mode`，其默认值列指向 `[Spark Writes](spark-writes.md#writing-distribution-modes)`，说明该写选项可覆盖表级分布模式，默认值参见 Spark Writes 文档。

两处修改相互呼应：核心配置表说明"核心默认 none，引擎各有不同"，Spark 配置表则补充"Spark 写选项中可覆盖，默认见 Spark Writes"。

## 修改详情

### `docs/docs/configuration.md`

**修改目的**：澄清 `write.distribution-mode` 表属性的默认值，说明 `none` 是核心默认但引擎可有自己的默认值。

**工作逻辑**：修改表属性表中 `write.distribution-mode` 行的默认值列：

```diff
-| write.distribution-mode                              | none                        | Defines distribution of write data: __none__: don't shuffle rows; __hash__: hash distribute by partition key ; __range__: range distribute by partition key or sort key if table has an SortOrder |
+| write.distribution-mode |  none, see engines for specific defaults, for example [Spark Writes](spark-writes.md#writing-distribution-modes) | Defines distribution of write data: __none__: don't shuffle rows; __hash__: hash distribute by partition key ; __range__: range distribute by partition key or sort key if table has an SortOrder |
```

默认值列由单纯的 `none` 改为 `none, see engines for specific defaults, for example [Spark Writes](spark-writes.md#writing-distribution-modes)`，告知读者：核心规范默认是 `none`，但具体引擎（如 Spark）可能采用不同默认值（Spark 默认 `hash`），需查阅对应引擎文档。

### `docs/docs/spark-configuration.md`

**修改目的**：在 Spark 写选项表中补充 `distribution-mode` 选项，指明其默认值参见 Spark Writes 文档。

**工作逻辑**：在写选项表末尾新增一行：

```diff
+| distribution-mode | See [Spark Writes](spark-writes.md#writing-distribution-modes) for defaults | Override this table's distribution mode for this write |
```

该行说明：在 Spark 写入时可通过 `distribution-mode` 写选项覆盖表的分布模式，默认值参见 `spark-writes.md` 的 "writing-distribution-modes" 小节（其中说明 Spark 默认为 `hash`）。这使 Spark 用户在查阅写选项表时能直接知道该选项存在及其默认值出处。

## 小结

- **成效**：澄清了 `write.distribution-mode` 的默认值文档描述，消除"核心默认 none 与 Spark 默认 hash 不一致"带来的歧义，明确核心默认是 `none` 而引擎可覆盖，并在 Spark 配置文档中补充了对应的写选项，引导用户到 Spark Writes 查看具体默认值。
- **影响范围**：仅 `docs/docs/configuration.md` 与 `docs/docs/spark-configuration.md` 两个文档文件，共 2 行改动（1 行修改 + 1 行新增）；不影响任何代码或运行时行为。
- **回迁到 1.4.x 的注意事项**：这是纯文档澄清，回迁到 1.4.x 完全无风险。是否回迁取决于 1.4.x 分支的文档是否同样存在该歧义；若存在，建议回迁以保持文档准确性。cherry-pick 不会产生冲突。
