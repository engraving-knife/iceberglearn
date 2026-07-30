# 提交 1915：Docs: Fix lifecycle and versions in multi-engine-support (#12370)

## 提交信息

- **序号**：1915 / 4088
- **哈希**：5cb8715c15d5a9cf840ef0a8a4b2ac35276557e4
- **短哈希**：5cb8715c1
- **日期**：2025-03-24 19:32:39 +0100
- **作者**：Manu Zhang
- **提交说明**：Docs: Fix lifecycle and versions in multi-engine-support (#12370)
- **PR/Issue**：#12370

## 总体目的

`site/docs/multi-engine-support.md` 文档用表格列出各引擎版本的支持状态（生命周期阶段）、初始与最新支持的 Iceberg 版本及运行时 jar 链接。本次提交修正两处与实际状态不符的内容：

1. Spark 3.3 此前被标记为 "End of Life"（生命终止），最新 Iceberg 版本写死为 `1.8.0`。实际上 Spark 3.3 仍处于 "Deprecated"（弃用，未终止），且仍在跟随主线发布。本提交把其生命周期改为 `Deprecated`，并把写死的 `1.8.0` 改为模板变量 `{{ icebergVersion }}`，使文档自动跟随当前发布版本。
2. Hive 2 与 Hive 3 的 "Latest Runtime Jar" 链接此前用模板变量 `{{ icebergVersion }}`，但 Hive runtime 实际已不再随主线发布（最后版本为 1.7.2）。本提交把这两个 jar 链接固定为 `1.7.2`。同时把 Hive 3 的生命周期从 `Maintained` 改为 `Deprecated`。

## 如何达成设计目的

直接编辑 `multi-engine-support.md` 表格单元格：
- Spark 3.3 行：`End of Life` → `Deprecated`，`1.8.0` → `{{ icebergVersion }}`（链接同步）。
- Hive 2 / Hive 3 行：jar 链接 `{{ icebergVersion }}` → `1.7.2`；Hive 3 生命周期 `Maintained` → `Deprecated`。

## 修改详情

### `site/docs/multi-engine-support.md` (修改, +3/-3 lines)

**修改目的**：修正 Spark 3.3 与 Hive 的生命周期和版本链接。

**工作逻辑**：

- Spark 3.3 行：

```
| 3.3 | Deprecated | 0.14.0 | {{ icebergVersion }} | .../{{ icebergVersion }}/... |
```

- Hive 2 / Hive 3 行：jar 链接改为 `1.7.2`，Hive 3 生命周期改为 `Deprecated`：

```
| 2 | 2.3.8 | Deprecated | 0.8.0-incubating | {{ icebergVersion }} | .../1.7.2/iceberg-hive-runtime-1.7.2.jar |
| 3 | 3.1.2 | Deprecated | 0.10.0           | {{ icebergVersion }} | .../1.7.2/iceberg-hive-runtime-1.7.2.jar |
```

## 总结

本提交修正多引擎支持文档：把 Spark 3.3 从 End of Life 改为 Deprecated 并跟随当前版本；把 Hive 2/3 的 runtime jar 链接固定为 1.7.2，并把 Hive 3 标记为 Deprecated，使文档与实际维护状态一致。
