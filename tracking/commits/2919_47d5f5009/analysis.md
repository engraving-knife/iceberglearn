# 提交 2919：Docs: Add gc.enabled table property (#14676)

## 提交信息

- **序号**：2919 / 4088
- **哈希**：47d5f5009eafbbb526e6e2c9cbeac3105bf34670
- **短哈希**：47d5f5009
- **日期**：2025-11-25 10:26:49 -0800
- **作者**：Yuya Ebihara
- **提交说明**：Docs: Add gc.enabled table property
- **PR/Issue**：#14676

## 总体目的

Iceberg 表支持 `gc.enabled` 表属性来控制是否允许垃圾回收操作（如过期快照和移除孤儿文件）。这是一个重要的安全控制属性，当设置为 false 时可以防止意外的数据删除。然而该属性此前未在官方配置文档的表属性表中列出，用户难以发现和了解此功能。

本提交在配置文档的表属性表中补充了 `gc.enabled` 属性的说明，使文档更加完整，帮助用户了解如何控制垃圾回收行为。

## 如何达成设计目的

在 `docs/docs/configuration.md` 文件的表属性表格中，紧接 history.expire 相关属性之后，添加一行 `gc.enabled` 属性的说明，包含属性名、默认值（true）和描述。

## 修改详情

### `docs/docs/configuration.md` (+1/-0 lines)

**修改目的**：在表属性文档中补充 gc.enabled 属性。

**工作逻辑**：
在 history.expire.max-ref-age-ms 行之后添加一行：
`| gc.enabled | true | Allows garbage collection operations such as expiring snapshots and removing orphan files |`

该属性默认值为 true，表示允许垃圾回收；设为 false 可阻止过期快照和孤儿文件清理操作，用于保护数据不被意外删除。

## 总结

本提交是一个纯文档改进，在表属性配置文档中补充了 `gc.enabled` 属性的说明。虽然改动仅一行，但填补了文档空白，使用户能够发现并理解这一重要的数据保护属性。
