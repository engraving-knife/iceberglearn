# 提交 0952：Spec: Clarify which columns can be used for equality delete files. (#8981)

## 提交信息

- **序号**：0952 / 4088
- **哈希**：e02b5c90ef305b4d1ca5c19f0b9b2e99f9392e44
- **短哈希**：e02b5c90e
- **日期**：2024-07-19（Fri Jul 19 10:23:15 2024 -0700）
- **作者**：emkornfield <emkornfield@gmail.com>
- **提交说明**：Spec: Clarify which columns can be used for equality delete files. (#8981)
- **PR/Issue**：#8981

## 总体目的

Iceberg 的相等删除文件（equality delete files）通过一个或多个列值来标识被删除的行，其中用于匹配数据行的列被称为"删除列"（delete columns）。在此提交之前，spec.md 中关于哪些列可作为删除列的描述较为简略，仅明确说明"Float 和 double 列不能作为相等删除文件中的删除列"。这种表述存在两个问题：

1. 限制范围描述不完整，没有说明除 float/double 之外的其它限制（例如某些不支持作为标识字段的列类型）；
2. 与 Iceberg 规范中"标识字段（identifier fields）"的限制规则存在重复且不一致，造成读者难以判断完整规则。

本提交的目的是澄清规范文档，将删除列的限制规则与已有的"标识字段（identifier field ids）"规则对齐，并明确说明二者唯一的不同点：相等删除文件允许使用可选列（optional columns）以及嵌套在可选 struct 下的列（如果父 struct 为 null，则隐含叶子列也为 null），从而避免规范歧义、降低实现者误判风险。

## 如何达成设计目的

实现方式非常直接：仅修改 `format/spec.md` 中描述相等删除文件的一段文字。将原本"Float 和 double 列不能作为删除列"这一单独陈述，替换为"相等删除文件中列的限制与标识字段相同，但额外允许可选列以及嵌套在可选 struct 下的列"的统一陈述。这样既复用了规范中已有的 identifier field 限制（如禁止 float/double、要求非空等），又明确给出二者差异，使描述更完整且单点维护。

## 修改详情

### `format/spec.md`

**修改目的**：澄清相等删除文件中可作为删除列的列的范围，将限制规则与标识字段规则对齐并说明差异。

**工作逻辑**：将原文末尾的限制句从局部列举（"Float 和 double columns cannot be used..."）改为引用标识字段规则并补充例外（"The column restrictions ... are the same as those for identifier fields with the exception that optional columns and columns nested under optional structs are allowed (if a parent struct column is null it implies the leaf column is null)"）。

修改前：

```
... Float and double columns cannot be used as delete columns in equality delete files.
```

修改后：

```
... The column restrictions for columns used in equality delete files are the same as those for identifier fields with the exception that optional columns and columns nested under optional structs are allowed (if a parent struct column is null it implies the leaf column is null).
```

通过这一改动：
- 删除列的限制规则被收敛为"等同 identifier fields 的限制 + 例外允许 optional 列"；
- 同时补充语义：当父 struct 列为 null 时，叶子列也隐含为 null，避免实现者在处理嵌套可选 struct 时产生歧义。

## 小结

- **成效**：澄清了相等删除文件中删除列的限制规则，使其与标识字段规则统一，并明确说明唯一的例外情形（允许 optional 列和嵌套在 optional struct 下的列），消除了规范中的歧义。
- **影响范围**：仅修改 `format/spec.md` 一个文件，单行文字替换，无代码、构建或测试变更。属于纯规范文档澄清。
- **回迁到 1.4.x 的注意事项**：这是规范文档的措辞澄清，不涉及任何代码逻辑变更，**适合回迁到 1.4.x**，且风险极低。1.4.x 分支上的 spec.md 若同样存在该歧义描述，cherry-pick 本提交可让维护分支的规范文档与 main 保持一致，便于实现者理解。需注意确认 1.4.x 的 spec.md 中对应段落尚未因其它改动而变化，以避免合并冲突。
