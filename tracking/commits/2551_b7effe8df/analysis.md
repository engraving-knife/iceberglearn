# 提交 2551：update REST spec to clarify AssertRefSnapshotId's snapshot-id field as required (#13902)

## 提交信息

- **序号**：2551 / 4088
- **哈希**：b7effe8df607fe482a2cc6db0ac331dcbdc1bce3
- **短哈希**：b7effe8df
- **日期**：2025-08-22 14:47:33 -0700
- **作者**：Kevin Liu
- **提交说明**：update REST spec to clarify AssertRefSnapshotId's snapshot-id field as required (#13902)
- **PR/Issue**：#13902

## 总体目的

该提交澄清了 REST Catalog OpenAPI 规范中 `AssertRefSnapshotId` 要求（requirement）的 `snapshot-id` 字段的语义。此前 `AssertRefSnapshotId` 的描述为"如果 `snapshot-id` 为 null 或缺失，则该 ref 必须不存在"，这种措辞暗示 `snapshot-id` 字段可以缺失（missing），但实际上在 OpenAPI 规范中该字段被声明在 `required` 列表中，意味着该字段必须存在于请求中。

这种描述与规范定义之间的歧义可能导致 REST Catalog 实现者和客户端开发者产生困惑：`snapshot-id` 到底是可以缺失的字段，还是必须存在但值可以为 null 的字段？正确的语义应该是：`snapshot-id` 字段必须存在于对象中（required），但其值可以为 null，当值为 null 时表示该 ref 必须不存在。

该提交通过更新描述文字和添加 `nullable: true` 属性来消除这种歧义，使规范清晰表达：字段必须存在但可以为 null。

## 如何达成设计目的

- 重写 `AssertRefSnapshotId` 的描述，明确说明 `snapshot-id` 字段是 required 的，但在值为 null 时表示 ref 必须不存在。
- 在 OpenAPI YAML 规范中为 `snapshot-id` 字段添加 `nullable: true` 属性，与"字段必须存在但可为 null"的语义保持一致。
- 同步更新 Python 版本的 OpenAPI 规范定义文件（`rest-catalog-open-api.py`）中的描述。

## 修改详情

### `open-api/rest-catalog-open-api.py` (+4/-1)

**修改目的**：更新 Python 版本规范中 `AssertRefSnapshotId` 类的 docstring。

**工作逻辑**：将原来模糊的描述替换为更清晰的表述，明确 `snapshot-id` 字段是 required 的，但在值为 null 时 ref 必须不存在。

### `open-api/rest-catalog-open-api.yaml` (+5/-3)

**修改目的**：更新 YAML 规范中 `AssertRefSnapshotId` 的描述并添加 nullable 属性。

**工作逻辑**：
- 使用 YAML 的 `|` 块标量格式重写 description，分两行清晰说明语义：第一行说明 ref 必须引用 `snapshot-id`，第二行说明字段是 required 的但 null 时 ref 必须不存在。
- 在 `snapshot-id` 字段定义中添加 `nullable: true`，明确该字段的值可以为 null，与描述保持一致。

## 总结

该提交澄清了 `AssertRefSnapshotId` 要求中 `snapshot-id` 字段的语义，消除了"字段可缺失"的歧义，明确字段必须存在但值可以为 null，并同步更新了 YAML 和 Python 两份规范文件。
