# 提交 2564：Spec: clarify the partition-spec metadata for Avro manifest file (#13895)

## 提交信息

- **序号**：2564 / 4088
- **哈希**：9f266917b658931f3b704cd9c50b3f5d0da90cb7
- **短哈希**：9f266917b
- **日期**：2025-08-26 07:19:13 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Spec: clarify the partition-spec metadata for Avro manifest file (#13895)
- **PR/Issue**：#13895

## 总体目的

该提交澄清了 Iceberg 规范中关于 Avro manifest 文件的 `partition-spec` 元数据字段的描述。此前规范中 `partition-spec` 的描述为"JSON fields representation of the partition spec used to write the manifest"，这个描述存在歧义——"JSON fields representation" 不够明确，读者可能不清楚它到底是指完整的分区规范对象（包含 spec-id 等信息），还是仅指分区字段数组（partition fields array）。

实际上，`partition-spec` 存储的是分区规范中的分区字段数组（fields array），而不是完整的分区规范对象。完整的分区规范对象还包含 `spec-id` 和 `fields`，但 `spec-id` 已经通过单独的 `partition-spec-id` 元数据字段存储，因此 `partition-spec` 只需要存储字段数组即可。

该提交通过更新描述文字，明确说明 `partition-spec` 存储的是"仅分区字段数组的 JSON 表示"（JSON representation of only the partition fields array），并添加了指向 Appendix C（分区规范 JSON 解析器）的链接，方便读者查阅具体的 JSON 格式。

## 如何达成设计目的

- 修改 `format/spec.md` 中 manifest 文件元数据表格中 `partition-spec` 行的 Value 描述。
- 将描述从"JSON fields representation of the partition spec used to write the manifest"改为"JSON representation of only the partition fields array of the partition spec used to write the manifest"。
- 添加指向 Appendix C 的链接。

## 修改详情

### `format/spec.md` (+8/-8)

**修改目的**：澄清 manifest 文件中 partition-spec 元数据的描述。

**工作逻辑**：更新 `partition-spec` 行的 Value 列描述，明确说明是"仅分区字段数组"的 JSON 表示，并添加 `See [Appendix C](#partition-specs)` 链接。由于该值描述变长，表格中其他行的格式也做了对齐调整（仅空格变化，内容不变）。

## 总结

该提交是对 Iceberg 规范文档的澄清性修改，明确了 Avro manifest 文件中 `partition-spec` 元数据存储的是分区字段数组的 JSON 表示而非完整的分区规范对象，并添加了相关附录链接供读者参考。
