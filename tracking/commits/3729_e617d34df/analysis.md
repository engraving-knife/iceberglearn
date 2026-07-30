# 提交 3729：Spec: Clarify non-default CRS conventions (#15834)

## 提交信息

- **序号**：3729 / 4088
- **哈希**：e617d34dfe948d05998414563f04c49a05e56c24
- **短哈希**：e617d34df
- **日期**：2026-05-18 14:13:54 +0200
- **作者**：Milan Stefanovic
- **提交说明**：Spec: Clarify non-default CRS conventions (#15834)
- **PR/Issue**：#15834

## 总体目的

本提交旨在澄清 Iceberg 规范中关于 `geometry` 和 `geography` 类型非默认 CRS（Coordinate Reference System，坐标参考系统）的约定。原规范对非默认 CRS 的描述较为狭窄，仅允许两种格式：`srid:<identifier>`（空间参考标识符）和 `projjson:<property-name>`（PROJJSON 定义引用）。这种限制性的描述存在以下问题：

1. 未能涵盖业界常用的其他 CRS 标识格式，如 `OGC:CRS84`、`EPSG:4326`、`IGNF:ATI` 等，这些格式广泛存在于 GIS 生态中。
2. 没有明确禁止内联 PROJJSON 定义，可能导致实现者误以为可以将冗长的 PROJJSON 文本直接嵌入 schema，从而造成性能问题。
3. 表述上把"建议格式"写成了"枚举格式"，降低了互操作性。

本次澄清使规范更开放、更明确，既扩展了支持的 CRS 标识格式，又对 PROJJSON 内联行为做出了明确禁止性规定。

## 如何达成设计目的

通过重写 `format/spec.md` 中关于非默认 CRS 的段落实现：
1. 将"Custom CRS values can be specified by a string of the format `type:identifier`..."改为"Non-default CRS values are specified by any string that uniquely identifies a coordinate reference system"，开放为任意唯一标识 CRS 的字符串。
2. 将原先的两个固定格式（srid、projjson）改为"建议格式"列表，并扩展示例，新增 `<context>:<identifier>` 通用格式，给出 `OGC:CRS84`、`EPSG:4326`、`IGNF:ATI`、`SRID:0` 等常见示例。
3. 新增一段明确禁止内联 PROJJSON 定义的规定，要求实现不得将 CRS 内容解析为 PROJJSON，如需使用 PROJJSON 必须存于表属性并通过 `projjson:<property-name>` 引用。

## 修改详情

### `format/spec.md` (+5/-3 lines)

**修改目的**：澄清非默认 CRS 的标识约定，扩展支持格式并禁止内联 PROJJSON。

**工作逻辑**：
原内容：
```
Custom CRS values can be specified by a string of the format `type:identifier`, where `type` is one of the following values:

* `srid`: Spatial reference identifier, `identifier` is the SRID itself.
* `projjson`: PROJJSON, `identifier` is the name of a table property where the projjson string is stored.
```
新内容：
```
Non-default CRS values are specified by any string that uniquely identifies a coordinate reference system associated with this type.
To maximize interoperability, suggested formats for CRS include, but are not limited to:
* `<context>:<identifier>`: Identifies a CRS by name or other identifier in some well-documented context. Examples: `OGC:CRS84`, `EPSG:4326`, `IGNF:ATI` and `SRID:0`
* `projjson:<property-name>` - where <property-name> refers to a table property where CRS definition in PROJJSON format is stored.

CRS value must not contain inlined PROJJSON definitions and implementations must not parse the contents of the CRS as PROJJSON. PROJJSON definitions are very verbose, hence inlining them as part of schema would cause significant performance degradation. If the intention is for a PROJJSON definition to be part of the table metadata, then it must be stored in a table property and referenced from the CRS field using the `projjson:<property-name>` form described above.
```
关键变化：
- 把"必须使用 `type:identifier` 格式"改为"任意唯一标识 CRS 的字符串"，并把格式列表降级为"建议格式"。
- 新增 `<context>:<identifier>` 通用格式，合并了原先的 `srid` 格式，并给出业界常见示例。
- 明确禁止内联 PROJJSON 定义，要求实现不得解析 CRS 内容为 PROJJSON；如需 PROJJSON 必须通过表属性 + `projjson:<property-name>` 引用，理由是 PROJJSON 过于冗长会拖累性能。

## 总结

本提交对 Iceberg 规范中 `geometry`/`geography` 类型的非默认 CRS 约定进行了重要澄清：将原本狭窄的两种固定格式扩展为开放的"任意唯一标识"+ 建议格式列表，提升了与业界常见 CRS 标识（如 EPSG、OGC、IGNF、SRID）的互操作性；同时明确禁止内联 PROJJSON 定义，避免冗长文本嵌入 schema 导致性能下降，并要求 PROJJSON 通过表属性引用。这是规范层面的语义增强，有助于实现方更准确地遵循规范并提升跨实现互操作性。
