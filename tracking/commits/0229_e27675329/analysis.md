# 提交 0229：Core: Fix equality in StructLikeMap (#9236)

## 提交信息

- **序号**：0229 / 4088
- **哈希**：e276753290dad4cefcb289b925d12d7287fbec16
- **短哈希**：e27675329
- **日期**：2023-12-06
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Fix equality in StructLikeMap (#9236)
- **PR/Issue**：#9236

## 总体目的

这个提交修复 `StructLikeMap` 的 entry（`StructLikeEntry`）在 `equals` / `hashCode` 上的实现缺陷，使得两个 `StructLikeMap` 即便用不同的 `StructLike` 实现类（例如 `Row` 与 `GenericRecord`、`CustomRow`）作为 key，只要逻辑上代表同一个 struct，也能正确比较为相等。

背景与问题根因：`StructLike` 是 Iceberg 中表示一行 struct 值的接口，它本身**不规定** `equals` / `hashCode` 的语义——不同的实现类（测试用的 `Row`、`GenericRecord`、Spark 的 `UnsafeRow` 包装等）默认按对象身份比较，互不相等。为了让这些不同实现可以作为 map 的 key 互认，`StructLikeMap` 内部用 `StructLikeWrapper` 包装 key：`StructLikeWrapper` 持有 struct 与其类型，按值计算 `equals` / `hashCode`。所以 map 的 `put` / `get` 一直是正确的。

但 `StructLikeMap` 的 entry 类 `StructLikeEntry` 的旧实现是：

```java
public int hashCode() {
  int hashCode = getKey().hashCode();   // getKey() 返回原始 StructLike，hashCode 是身份相关的
  if (getValue() != null) { hashCode ^= getValue().hashCode(); }
  return hashCode;
}

public boolean equals(Object o) {
  ...
  StructLikeEntry that = (StructLikeEntry<R>) o;
  return Objects.equals(getKey(), that.getKey())   // 用原始 StructLike 的 equals，跨实现类必然不等
      && Objects.equals(getValue(), that.getValue());
}
```

`getKey()` 返回的是原始 `StructLike`（从 `inner.getKey().struct()` 取出），而原始 `StructLike` 的 `equals` / `hashCode` 不基于值。于是两个 entry 即便内部 `inner`（基于 `StructLikeWrapper`）完全等价，因为入口处用原始 key 比较，也会被判不等、hashCode 也不同。这导致 `AbstractMap.equals`（基于 entrySet）、`entrySet().equals(...)`、`keySet().equals(...)` 在跨实现类时全部失效——两份逻辑上相同的 map 比较结果为不等。

这个缺陷在后续提交（如 `PartitionMap`，提交 0230）中会直接暴露，因为 `PartitionMap.keySet()` 返回 `PartitionSet`，而 `PartitionSet` 内部也基于 `StructLikeMap`/`StructLikeSet`，跨实现 key 相等性是其正确性的前提。所以本提交是 0230 的前置修复。

## 如何达成设计目的

修复思路很直接：让 `StructLikeEntry` 的 `equals` / `hashCode` 委托给内部的 `inner`（即 `Entry<StructLikeWrapper, R>`），而不是再去碰原始 `StructLike` key。`StructLikeWrapper` 已经按值实现了 `equals` / `hashCode`，所以 `inner` 的 `equals` / `hashCode` 自然就是基于值的。同时把 `inner` 字段改为 `final`（不可变，语义更清晰、更安全），把 `equals` 的类型检查从 `!(o instanceof StructLikeEntry)` 改为 `o == null || getClass() != o.getClass()`（更严格，避免子类误判），并去掉不再需要的 `@SuppressWarnings("unchecked")`（改用 `StructLikeEntry<?>` 通配）。

为了能可靠测试这个修复，作者在 `TestHelpers` 里新增了一个 `CustomRow` 测试辅助类——一个**自带基于值的 `equals` / `hashCode`** 的 `StructLike` 实现，与已有的、**不带**基于值 equals 的 `Row` 形成对照。这样测试可以同时验证：无论 key 实现类自己有没有值相等，map 都通过 `StructLikeWrapper` 统一归一化。

## 修改详情

### `api/src/test/java/org/apache/iceberg/TestHelpers.java`

**修改目的**：新增一个自带值相等语义的 `StructLike` 测试实现，用于检验 map 在跨实现类 key 下的相等性行为。

**工作逻辑**：新增静态内部类 `CustomRow implements StructLike`，提供 `CustomRow.of(Object... values)` 工厂。内部用 `Object[] values` 存值，实现 `size` / `get` / `set`。关键在于覆盖了 `equals`（同类、`Arrays.equals(values, that.values)`）与 `hashCode`（`17 * Arrays.hashCode(values)`）。注释明确说明这是"similar to Row but has its own hashCode() and equals() implementations; useful for testing custom collections that rely on wrappers"。它的存在让测试能区分"key 自己实现了值相等"与"key 没实现值相等"两种情况，确保 map 的相等性不依赖 key 的自带 equals。

### `core/src/main/java/org/apache/iceberg/util/StructLikeMap.java`

**修改目的**：修复 `StructLikeEntry` 的 `equals` / `hashCode`，让其基于 `StructLikeWrapper` 而非原始 `StructLike`；并把 `inner` 改为 `final`。

**工作逻辑**：
- 移除 `import java.util.Objects;`（不再需要）。
- `StructLikeEntry` 中把 `private Map.Entry<StructLikeWrapper, R> inner;` 改为 `private final Entry<StructLikeWrapper, R> inner;`（加 final、去掉 `Map.` 前缀，因已 import `Entry`）。构造器签名同步。
- `hashCode()` 整体替换为 `return inner.hashCode();`——直接复用 `StructLikeWrapper` entry 的值相关 hashCode。
- `equals(Object o)` 改写：先 `this == o` 返回 true；再 `o == null || getClass() != o.getClass()` 返回 false；最后 `StructLikeEntry<?> that = (StructLikeEntry<?>) o; return inner.equals(that.inner);`。去掉 `@SuppressWarnings("unchecked")`。
- 修复后，两个 `StructLikeMap` 即便一个用 `CustomRow` 作 key、一个用 `Row` 作 key，只要值相同，其 entrySet 比较与 map equals 都会成功。

### `core/src/test/java/org/apache/iceberg/util/TestStructLikeMap.java`

**修改目的**：覆盖修复后的跨实现 key 相等性。

**工作逻辑**：引入 `CustomRow` 与 `Row` 两个测试 key 实现。新增两个测试：
- `testEqualsAndHashCode`：先验证两个空 map 相等且 hashCode 相同；然后在 `map1` 放 `CustomRow.of(1, null) -> "aaa"`、`CustomRow.of(2, null) -> "bbb"`，在 `map2` 放 `Row.of(1, null) -> "aaa"`、`Row.of(2, null) -> "bbb"`，断言 `map1` 等于 `map2` 且 hashCode 相同。这一断言在修复前会失败（`Row` 与 `CustomRow` 互不等价），修复后通过。
- `testKeyAndEntrySetEquality`：类似地验证空 map 与有数据 map 的 `keySet()` 与 `entrySet()` 跨实现类相等。这覆盖了 `AbstractMap` 基于 entrySet 的 equals 路径与 keySet 的 equals 路径。

## 小结

本提交修复了 `StructLikeMap` entry 基于原始 `StructLike` 比较 key 导致的跨实现类不相等缺陷，使 map 的相等性正确归一化到 `StructLikeWrapper`，是后续 `PartitionMap` 等数据结构正确性的必要前置修复。
