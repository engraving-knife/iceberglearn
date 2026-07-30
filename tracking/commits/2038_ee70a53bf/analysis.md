# 提交 2038：Spec: Avoid struct field conflicts in default values

## 提交信息

- **序号**：2038 / 4088
- **哈希**：ee70a53bf19c985382dfc9471499f965a5073764
- **短哈希**：ee70a53bf
- **日期**：2025-04-24 18:20:48 -0500
- **作者**：Ryan Blue
- **提交说明**：Spec: Avoid struct field conflicts in default values (#12841)
- **PR/Issue**：#12841

## 总体目的

Iceberg 规范中，schema 字段可以定义 `initial-default` 和 `write-default` 两种默认值。此前规范允许结构体（struct）类型的字段在其默认值中嵌套包含子字段的默认值。例如，一个 struct 字段 `point` 的默认值可以设为 `{"x": 0, "y": 0}`，其中 `x` 和 `y` 的值同时也在 `point.x` 和 `point.y` 字段自身的 `initial-default`/`write-default` 中定义。

这种设计存在冲突风险：当 struct 字段的默认值中包含了子字段的值，而子字段自身也定义了默认值时，两者可能不一致，导致歧义——到底应该使用 struct 默认值中嵌套的值，还是子字段自身的默认值？

本提交修改 Iceberg 规范（`format/spec.md`），明确规定：
1. struct 类型字段的默认值只能是 null 或空结构体 `{}`，不允许在结构体默认值中嵌套子字段的值
2. 子字段的默认值始终由子字段自身的 `initial-default`/`write-default` 决定
3. 添加 `variant` 类型到必须默认为 null 的类型列表中

同时更新了 Avro 序列化相关的说明，明确有非 null Iceberg 默认值的字段需要转换为对应的 Avro 默认值。

## 如何达成设计目的

通过修改规范文档 `format/spec.md` 来明确约束：

1. **修改默认值类型限制**：在必须默认为 null 的类型列表中添加 `variant`
2. **新增结构体默认值规则**：添加段落说明 struct 字段的默认值只能为 null 或空结构体，子字段默认值由子字段元数据独立跟踪
3. **添加示例表格**：通过 `point` 结构体（字段 x 默认 0，y 默认 0）的四种组合示例，清晰展示 struct 默认值与数据值组合后的结果
4. **修改 struct 演进规则**：将"当新字段添加到有默认值的 struct 时，更新 struct 的默认值是可选的"改为"当 struct 类型字段被添加时，其默认值只能是 null 或无字段值的非 null struct"
5. **更新 Avro 默认值说明**：从"Optional 字段必须设置 Avro 默认值为 null"改为"没有 Iceberg 默认值的 optional 字段设置 Avro 默认值为 null，有非 null Iceberg 默认值的字段需转换为等价的 Avro 默认值"

## 修改详情

### `format/spec.md` (修改, +14/-3 lines)

**修改目的**：明确规范中结构体默认值的约束，避免字段默认值冲突。

**工作逻辑**：

1. 在类型限制部分，将 `unknown, geometry, and geography` 扩展为 `unknown, variant, geometry, and geography`，新增 variant 类型必须默认为 null。

2. 新增结构体默认值规则段落，说明：
   - struct 字段默认值在字段级别通过 `initial-default`/`write-default` 跟踪
   - 嵌套 struct 的默认值不得包含子字段的默认值
   - 嵌套 struct 的默认值只能是 null 或空结构体 `{}`
   - 有效默认值通过在新结构体中设置每个字段的默认值来生成

3. 新增示例表格，展示 struct `point`（x 默认 0，y 默认 0）在不同默认值和数据值组合下的结果：
   - `point` 默认 null + 数据缺失 => 结果 null
   - `point` 默认 null + 数据 `{"x": 3}` => 结果 `{"x": 3, "y": 0}`（字段级默认值仍生效）
   - `point` 默认 `{}` + 数据缺失 => 结果 `{"x": 0, "y": 0}`
   - `point` 默认 `{}` + 数据 `{"y": -1}` => 结果 `{"x": 0, "y": -1}`

4. 修改 struct 演进规则，将"当有默认值的新字段添加到 struct 时，更新 struct 默认值是可选的"替换为"当 struct 类型字段被添加时，其默认值只能是 null 或无字段值的非 null struct。字段的默认值必须存储在字段元数据中。"

5. 修改 Avro 序列化说明，从"Optional 字段必须总是设置 Avro 字段默认值为 null"改为"没有 Iceberg 默认值的 optional 字段必须设置 Avro 字段默认值为 null。有非 null Iceberg 默认值的字段必须将默认值转换为等价的 Avro 默认值。"

## 总结

本提交修改 Iceberg 规范文档，通过禁止在 struct 字段默认值中嵌套子字段值，消除了默认值冲突的歧义。struct 默认值只能为 null 或空结构体 `{}`，子字段默认值始终由子字段自身元数据决定。同时将 variant 类型加入必须默认为 null 的类型列表，并更新了 Avro 默认值转换说明。属于规范层面的设计改进，纯文档修改。
