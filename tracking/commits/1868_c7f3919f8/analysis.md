# 提交 1868：API: Implement Variant#toString (#12531)

## 提交信息

- **序号**：1868 / 4088
- **哈希**：c7f3919f847ae8c4f661299fd0a64db4dde1a583
- **短哈希**：c7f3919f8
- **日期**：2025-03-17 15:55:19 +0100
- **作者**：Ryan Blue
- **提交说明**：API: Implement Variant#toString (#12531)
- **PR/Issue**：#12531

## 总体目的

Iceberg 的 v3 spec 引入了 `Variant` 类型（半结构化数据类型，类似 JSON/AVRO 的变体），对应 API 模块的 `org.apache.iceberg.variants.Variant` 接口。`Variant` 由两部分组成：`VariantMetadata metadata()`（元数据，含 schema 字典等）与 `VariantValue value()`（实际值）。

此前的 `Variant` 接口没有定义 `toString()`，其唯一实现 `VariantData`（包级私有，位于 `api/src/main/java/org/apache/iceberg/variants/VariantData.java`）也没有覆盖 `Object#toString()`。结果打印或日志记录 `Variant` 实例时，输出的是 `Object` 默认的 `类名@哈希码`（如 `org.apache.iceberg.variants.VariantData@1b6d3586`），对调试与日志毫无帮助。

本提交为 `Variant` 接口新增静态工具方法 `toString(Variant variant)`，输出 `Variant(metadata=..., value=...)` 格式，并让 `VariantData` 的 `toString()` 委托给该方法，使打印 Variant 实例时能看到有意义的元数据与值内容。

## 如何达成设计目的

1. 在 `Variant` 接口新增 `static String toString(Variant variant)` 静态方法，返回 `"Variant(metadata=" + variant.metadata() + ", value=" + variant.value() + ")"`。这利用了 `VariantMetadata` 与 `VariantValue` 自身的 `toString()`（假设它们已有有意义的实现）。
2. 在 `VariantData`（`Variant` 的唯一实现）中 `@Override public String toString()`，委托给 `Variant.toString(this)`。

把 `toString` 逻辑放在接口的静态方法中而非直接在 `VariantData` 内联，是为了让"格式化逻辑"集中在接口侧，便于后续若有其他 `Variant` 实现时复用同一格式。

## 修改详情

### `api/src/main/java/org/apache/iceberg/variants/Variant.java` (修改, +4 lines)

**修改目的**：新增 `toString` 静态工具方法。

**工作逻辑**：

```java
static String toString(Variant variant) {
  return "Variant(metadata=" + variant.metadata() + ", value=" + variant.value() + ")";
}
```

调用 `variant.metadata()` 与 `variant.value()` 获取两部分，拼接成 `Variant(metadata=..., value=...)` 字符串。依赖 `VariantMetadata` 与 `VariantValue` 的 `toString()` 提供可读内容。

### `api/src/main/java/org/apache/iceberg/variants/VariantData.java` (修改, +5 lines)

**修改目的**：让 `VariantData` 的 `toString()` 输出有意义的内容。

**工作逻辑**：

```java
@Override
public String toString() {
  return Variant.toString(this);
}
```

委托给接口的静态方法，输出 `Variant(metadata=..., value=...)`。

## 小结

- **成效**：`Variant` 实例的 `toString()` 现在输出 `Variant(metadata=..., value=...)`，便于调试与日志记录，替代了无意义的默认 `类名@哈希码`。
- **影响范围**：api 模块 2 个文件、+9 行。属于调试体验改进，不影响任何运行时逻辑。
- **回迁到 1.4.x 的注意事项**：**不建议回迁**。`Variant` 类型是 v3 spec 引入的新类型，1.4.x 的 api 模块当前不存在 `org.apache.iceberg.variants.Variant`/`VariantData` 类（本地查找未发现该文件）。本提交依赖 v3 Variant 类型体系在 1.4.x 已支持。若 1.4.x 未引入 Variant 类型，本提交无回迁意义；若 1.4.x 已引入 Variant 但缺 `toString`，则可回迁（改动极小）。
