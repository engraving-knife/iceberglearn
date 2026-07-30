# 提交 1687 3bd04bea6 分析

## 提交信息
- 哈希：3bd04bea65296d15b70306d99ee178e8b107627b
- 日期：2025-02-06 00:53:28 -0800
- 作者：Daniel Weeks
- 消息：API: Null check for auto-unboxed field-id (#12165)（含 Eduard Tudenhoefner 协作）

## 总体目的

本提交修复 `Types.NestedField.Builder.build()` 在字段 ID 为 null 时抛出无信息 NullPointerException 的问题。

`NestedField` 的字段 ID 在内部以原始类型 `int` 存储，但 Builder 中的 `id` 是包装类型 `Integer`（初始为 null，需通过 `withId()` 设置）。当用户忘记调用 `withId()` 就直接 `build()` 时，Builder 会把 null `Integer` 传给 `NestedField` 构造函数；构造函数在把 `Integer` 自动拆箱为 `int` 时抛出 `NullPointerException`，但该异常没有任何提示信息，用户很难定位是哪个字段、哪个属性出了问题。

本提交在 `build()` 中显式用 `Preconditions.checkNotNull(id, "Id cannot be null")` 检查 ID，使异常带明确的错误信息，改善调试体验。这是一个位于 `api` 模块的小修复，属于防御性编程改进。

## 如何达成设计目的

在 `NestedField.Builder.build()` 方法首行加入对 `id` 的非空校验。`Preconditions.checkNotNull` 是 Iceberg 依赖的 Guava（relocated）工具，校验失败时抛出带指定消息的 `NullPointerException`，比自动拆箱的无信息 NPE 更友好。注释也相应更新：原注释说"构造函数会校验字段"，现改为"构造函数校验其他字段"，表明 ID 已由 Builder 提前校验。

### 修改详情

#### api/src/main/java/org/apache/iceberg/types/Types.java
`NestedField.Builder.build()` 方法：
```
public NestedField build() {
  Preconditions.checkNotNull(id, "Id cannot be null");
  // the constructor validates the other fields
  return new NestedField(isOptional, id, name, type, doc, initialDefault, writeDefault);
}
```
新增 `Preconditions.checkNotNull(id, "Id cannot be null")` 一行，并把原注释从"the constructor validates the fields"改为"the constructor validates the other fields"。新增 `Preconditions` 的 import（实际依赖已有 relocated Guava）。

#### api/src/test/java/org/apache/iceberg/types/TestTypes.java
新增测试 `testNestedFieldBuilderIdCheck`：
- 对 `optional("field").ofType(StringType.get()).build()` 断言抛出 `NullPointerException` 且消息为 "Id cannot be null"。
- 对 `required("field").ofType(StringType.get()).build()` 同样断言。

新增对 `optional`、`required` 静态方法的 import。

## 小结

成效：当用户忘记给 NestedField 设置 ID 时，异常信息从无信息的 NPE 变为明确的 "Id cannot be null"，显著改善调试体验。影响范围极小，仅 `api` 模块一个方法加一行校验，无行为变更（原本也会抛 NPE，只是消息不同）。

回迁到 1.4.x 的注意事项：本提交独立、低风险，依赖极少（只需 `Preconditions` 已在 `Types.java` 中可用，通常已存在）。可干净 cherry-pick 到 1.4.x。需确认 1.4.x 的 `NestedField.Builder` 结构与 main 一致（build 方法签名、字段名 id），以及测试中 `optional`/`required` 静态工厂方法在 1.4.x 中存在。这是一个纯粹的可用性改进，对 1.4.x 用户同样有价值，建议回迁。
