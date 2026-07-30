# 提交 3910：Docs: Clarify geography type serialization (#16799)

## 提交信息

- **序号**：3910 / 4088
- **哈希**：41c8ee43b46017843b304241d250737fd10837af
- **短哈希**：41c8ee43b
- **日期**：2026-06-19 10:44:02 -0700
- **作者**：Kevin Liu
- **提交说明**：Docs: Clarify geography type serialization (#16799)
- **PR/Issue**：#16799

## 总体目的

修正 Iceberg 格式规范中 geography 类型序列化描述的格式问题。geography 类型的 JSON 序列化格式描述中，参数分隔符的空格不一致，导致规范与实际实现可能不匹配，也给读者造成困惑。

## 如何达成设计目的

修改 `format/spec.md` 中 geography 类型的序列化格式描述，统一参数间的空格。

## 修改详情

### `format/spec.md` (+1/-1 lines)

**修改目的**：修正 geography 类型序列化格式描述。

**工作逻辑**：
```markdown
-| **`geography(C, A)`** |`JSON string: "geography(<C>,<E>)"`|`"geography(srid:4326,spherical)"`|
+| **`geography(C, A)`** |`JSON string: "geography(<C>, <A>)"`|`"geography(srid:4326, spherical)"`|
```

修正了两处：
1. 格式描述中 `"<C>,<E>"` 改为 `"<C>, <A>"`（添加空格，并将 E 改为 A 与参数名一致）
2. 示例中 `"srid:4326,spherical"` 改为 `"srid:4326, spherical"`（添加空格）

使格式描述与 geography 类型参数 `(C, A)` 的命名一致，并在逗号后添加空格提高可读性。

## 总结

修正了 Iceberg 格式规范中 geography 类型序列化描述的格式不一致问题，统一了参数分隔符的空格并修正了参数名引用（E -> A）。这是一个文档准确性修复，确保规范描述与实际实现一致。
