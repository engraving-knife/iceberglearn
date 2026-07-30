# 提交分析：Spec: Note position delete files are only deprecated in v3

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2120 |
| 短哈希 | `d2124f277` |
| 完整哈希 | `d2124f2771e6e200ef50d98316bb2b01f46ed29a` |
| 作者 | Manu Zhang |
| 邮箱 | OwenZhang1990@gmail.com |
| 日期 | 2025-05-14 05:25:05 2025 +0800 |
| 提交信息 | Spec: Note position delete files are only deprecated in v3 (#12983) |

## 总体目的

本提交修改 Iceberg 格式规范文档，澄清位置删除文件（position delete files）的弃用说明。原规范简单标注位置删除文件为"deprecated"，但没有说明弃用是从哪个版本开始的。本提交将其改为"deprecated in v3"，明确位置删除文件仅在 v3 格式中被弃用。

## 设计目的的实现方式

在 `format/spec.md` 文件中，将位置删除文件的描述从 `(**deprecated**)` 修改为 `(**deprecated** in v3)`。

## 修改详情

### 修改 `format/spec.md`

**文件**：`format/spec.md`

**修改内容**：

在行级删除的三种类型描述中：

```
// 修改前：
* Position delete files identify deleted rows by file location and row position (**deprecated**)

// 修改后：
* Position delete files identify deleted rows by file location and row position (**deprecated** in v3)
```

**目的**：明确位置删除文件的弃用范围。位置删除文件在 v1 和 v2 格式中仍然有效，仅在 v3 格式中被弃用（由删除向量 Deletion Vectors 替代）。这个澄清对于阅读规范的用户很重要，避免误解为位置删除文件在所有版本中都被弃用。

## 总结

本提交是一个单行文档修复，将 Iceberg 规范中位置删除文件的弃用标注从 `(**deprecated**)` 改为 `(**deprecated** in v3)`，明确弃用仅适用于 v3 格式。这避免了用户对位置删除文件在 v1/v2 中可用性的误解。
