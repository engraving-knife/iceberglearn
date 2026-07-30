# 提交 2791：[Doc Update] Iceberg type to Spark type table (#14413)

## 提交信息

- **序号**：2791 / 4088
- **哈希**：2f719657a9a36f2d69b3e6e63294e1ae51aabac1
- **短哈希**：2f719657a
- **日期**：2025-10-24 14:54:09 -0700
- **作者**：Kurtis Wright
- **提交说明**：[Doc Update] Iceberg type to Spark type table (#14413)
- **PR/Issue**：#14413

## 总体目的

本提交更新 Iceberg 类型到 Spark 类型转换表，将 `unknown` 类型的映射从"Not supported"改为映射到 Spark 的 `null` 类型（仅 Spark 4.0+ 支持）。

Iceberg 规范中引入了 `unknown` 类型，用于表示字段类型未知的情况。之前文档中标注该类型在 Spark 中不支持，但随着 Spark 4.0 对 Iceberg 类型支持的扩展，`unknown` 类型现在可以映射为 Spark 的 `null` 类型。这一变更反映了 Spark 4.0 中 Iceberg 集成的改进。

PR 说明中提到，后续还会提交另一个 PR 为 Spark 3.5 添加 parquet 支持，届时将移除表格中的"Spark 4.0+"限定说明。

## 如何达成设计目的

通过修改 `docs/docs/spark-getting-started.md` 中的类型转换表，将 `unknown` 类型行的 Spark 类型列从空白改为 `null`，并将备注列从"Not supported"改为"Spark 4.0+"。

## 修改详情

### `docs/docs/spark-getting-started.md` (+1/-1 lines)

**修改目的**：更新 Iceberg `unknown` 类型到 Spark 类型的映射文档。

**工作逻辑**：在类型转换表中，`unknown` 类型行原来是空映射且标注"Not supported"。修改后，Spark 类型列填写 `null`，备注列改为"Spark 4.0+"，表示在 Spark 4.0 及以上版本中，Iceberg 的 `unknown` 类型会被映射为 Spark 的 `null` 类型。

## 总结

本提交是文档更新，反映了 Spark 4.0 对 Iceberg `unknown` 类型的支持。将 `unknown` 类型映射为 Spark 的 `null` 类型，使文档与实际代码实现保持一致。
