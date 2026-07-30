# 提交 0279：API: Fix equals and hashCode in CharSequenceSet (#9245)

## 提交信息

- **序号**：0279 / 4088
- **哈希**：d6a4ca7a33b7be3426ed119b1fb1529a160613eb
- **短哈希**：d6a4ca7a3
- **日期**：2023-12-16 09:15:35 +0100
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：API: Fix equals and hashCode in CharSequenceSet (#9245)
- **PR/Issue**：#9245

## 总体目的

本提交修复 `CharSequenceSet` 的 `equals` 与 `hashCode` 实现，使其真正符合 Java `Set` 契约。修复前，`CharSequenceSet.equals` 使用 `getClass() != o.getClass()` 严格类型判断，导致一个 `CharSequenceSet` 只能与另一个 `CharSequenceSet` 相等，而无法与包含相同字符内容的其它 `Set` 实现（例如 `Collections.unmodifiableSet(charSequenceSet)` 返回的视图、或一个 `Set<CharSequenceWrapper>`）相等。这违反了 `Set` 接口的通用契约——`Set.equals` 应基于"两个 Set 大小相同且包含相同元素"判定，而不应限定对方的具体实现类。

`CharSequenceSet` 是 Iceberg 在 api 模块提供的工具集合：内部以 `CharSequenceWrapper` 包装元素，使不同类型但字符内容相同的 `CharSequence`（如 `String`、`StringBuilder`、`StringBuffer`）在集合中按内容去重。它被广泛用于 core 模块及表达式求值（如 IN 谓词的字面量集合）。当这种集合参与到通用 Set 比较时，旧的严格 `equals` 会引发可见的语义问题：最典型的就是**对称性被破坏**——`Collections.unmodifiableSet(charSequenceSet)` 返回的视图会调用 `AbstractSet.equals`（基于 containsAll），认为它与原 `CharSequenceSet` 相等；但反过来原 `CharSequenceSet.equals(view)` 因 `getClass` 不同而返回 false。equals 必须满足对称性（`a.equals(b)` 当且仅当 `b.equals(a)`），旧实现直接违反了这一 `Object.equals` 基本契约。

修复的动机正是消除这种不对称与契约违反：让 `CharSequenceSet` 遵循 `AbstractSet` 的标准 equals 模式——只要对方是 `Set`、大小相同、且 `containsAll` 为真即判等，并用 try/catch 处理对方元素类型不兼容（`ClassCastException`/`NullPointerException`）的情形。同时把 `hashCode` 重写为"所有 `CharSequenceWrapper` 哈希之和"的显式形式，使其与基于内容的判等语义保持一致。修复后，`CharSequenceSet` 能与任何按 Set 契约实现的集合正确互比，消除了在缓存命中判断、断言、去重等场景下因不对称相等导致的潜在隐蔽 bug。

## 如何达成设计目的

提交遵循"修复实现 + 补测验证"两步：

1. **重写 `equals`**：采用 `AbstractSet.equals` 的标准模板——`this == other` 短路；非 `Set` 直接 false；大小不同直接 false；否则 `containsAll(that)` 判定，并用 try/catch 把元素类型不兼容（`ClassCastException`、`NullPointerException`）归为 false。这把判等从"同类 + 内部 wrapperSet 相等"放宽到"任意 Set + 内容一致"，对称性、自反性、传递性随之满足。

2. **重写 `hashCode`**：由原来的 `Objects.hashCode(wrapperSet)` 改为 `wrapperSet.stream().mapToInt(CharSequenceWrapper::hashCode).sum()`。两者数值上等价（`AbstractSet.hashCode()` 本身就是元素哈希之和），但新写法直接表达"hashCode = 所有 wrapper 内容哈希之和"的意图，不再依赖 `wrapperSet` 这个具体 Set 实现的 `hashCode` 语义，与基于 `CharSequenceWrapper` 内容判等的 `equals` 形成清晰对应。

3. **新增测试 `testEqualsAndHashCode`**：覆盖空集相等、混合 CharSequence 类型（String/StringBuilder/StringBuffer）相等、与 `unmodifiableSet` 视图相等、与 `Set<CharSequenceWrapper>` 相等，并断言四者 `hashCode` 一致，验证对称性与一致性。

关键点在于 `CharSequenceWrapper` 的 `equals`/`hashCode` 本就是按字符内容判定的（`equals` 用 `Comparators.charSequences().compare`，`hashCode` 用 `JavaHashes.hashCode(CharSequence)`，初始值 177、乘数 31 的多项式哈希）。所以 `containsAll(that)` 在比对时，无论 `that` 里的元素是 `String`、`StringBuilder` 还是 `CharSequenceWrapper`，只要字符内容相同就能命中 `CharSequenceSet` 的元素，这正是放宽 `equals` 后仍能正确判等的基础。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/CharSequenceSet.java`

**修改目的**：修复 `equals` 违反 `Set` 契约的问题，并使 `hashCode` 与新的判等语义显式对齐。

**工作逻辑**：

首先删除 `import java.util.Objects;`（旧 `hashCode` 用到的 `Objects.hashCode` 不再使用）。

`equals` 改写前后对比：

- 旧实现：`if (o == null || getClass() != o.getClass()) return false;` 然后 `CharSequenceSet that = (CharSequenceSet) o; return wrapperSet.equals(that.wrapperSet);`。`getClass` 严格判断使跨 Set 类型判等全部为 false，是契约违反的根源。
- 新实现：

```java
public boolean equals(Object other) {
  if (this == other) {
    return true;
  } else if (!(other instanceof Set)) {
    return false;
  }

  Set<?> that = (Set<?>) other;

  if (size() != that.size()) {
    return false;
  }

  try {
    return containsAll(that);
  } catch (ClassCastException | NullPointerException unused) {
    return false;
  }
}
```

逐段解析：

- `this == other`：自反性短路。
- `!(other instanceof Set)`：非 Set 对象（包括 null，因 `null instanceof Set` 为 false）直接 false。这是与 `AbstractSet.equals` 一致的口径——Set 只与 Set 相等。
- `size() != that.size()`：大小不同必不相等，快速失败，避免无谓的 containsAll 遍历。
- `try { return containsAll(that); }`：核心判定。`containsAll` 是 `CharSequenceSet` 已有方法，它对 `that` 的每个元素调用 `this.contains`。而 `contains` 的实现是：若元素 `instanceof CharSequence`，则用线程局部 `CharSequenceWrapper` 包装后查 `wrapperSet`，否则返回 false。注意 `CharSequenceWrapper` 自身也实现 `CharSequence`，所以当 `that` 是 `Set<CharSequenceWrapper>` 时也能正确命中——wrapper 作为 CharSequence 传入，再被包装一层比较内容，最终靠 `CharSequenceWrapper.equals`（基于 `Comparators.charSequences()`）判等。这就是为何新 equals 能与 `Set<CharSequenceWrapper>` 也判等。
- `catch (ClassCastException | NullPointerException unused)`：当 `that` 含有与 `CharSequence` 不兼容的元素、或元素为 null 时，`containsAll`/`contains` 可能抛出异常。按 `AbstractSet.equals` 的约定，这种情况应视为不相等而非抛异常传播，故捕获后返回 false。这是标准 Set 实现的稳健写法。

`hashCode` 改写：

- 旧：`return Objects.hashCode(wrapperSet);`——即 `wrapperSet.hashCode()`，对 `HashSet<CharSequenceWrapper>` 而言等于所有元素（wrapper）哈希之和（`AbstractSet.hashCode` 语义）。
- 新：`return wrapperSet.stream().mapToInt(CharSequenceWrapper::hashCode).sum();`——直接对所有 wrapper 的 `hashCode` 求和。

两者数值等价，但新写法把"hashCode = 内容哈希之和"这一意图显式化，不再借助 `wrapperSet` 具体实现类的 `hashCode` 行为，可读性更好、对底层 Set 类型变化更稳健。由于 `CharSequenceWrapper.hashCode()` 走 `JavaHashes.hashCode(CharSequence)`（177 起始、31 乘数的多项式哈希），它与 `CharSequenceWrapper.equals` 的内容判等保持一致，从而满足"两个相等对象必有相同 hashCode"的契约。

### `api/src/test/java/org/apache/iceberg/util/TestCharSequenceSet.java`

**修改目的**：为 equals/hashCode 修复补充回归测试，覆盖跨 CharSequence 类型与跨 Set 类型的判等场景。

**工作逻辑**：

新增必要的 import（`java.util.Collections`、`ImmutableSet`）后，新增 `testEqualsAndHashCode` 测试：

- 构造两个空集 `set1`、`set2`，断言相等且 hashCode 相等——验证空集基础情况。
- 向 `set1` 加入三个 `String`：`"v1"`、`"v2"`、`"v3"`。
- 向 `set2` 加入相同内容但不同 CharSequence 实现类型：`new StringBuilder("v1")`、`new StringBuffer("v2")`、`"v3"`。这验证了 `CharSequenceSet` 的核心价值——跨类型按内容判等；旧 equals 在此场景下虽然两个都是 CharSequenceSet 能相等，但新 equals 同样成立，且为后续跨 Set 类型比较铺路。
- 构造 `set3 = Collections.unmodifiableSet(set2)`：这是修复的关键回归点。旧实现下 `set1.equals(set3)` 因 `getClass` 不同返回 false，而 `set3.equals(set1)`（走 `AbstractSet.equals`）返回 true，造成不对称。新实现下两者均返回 true。测试通过 `set1` 等于 `set3` 间接验证了对称性恢复。
- 构造 `set4`：一个 `ImmutableSet<CharSequenceWrapper>`，元素是包装了相同内容的 `CharSequenceWrapper`（混合 String 与 StringBuffer 包装）。这验证 `CharSequenceSet` 能与"元素类型为 `CharSequenceWrapper`"的 Set 正确判等——`containsAll` 依赖 `CharSequenceWrapper implements CharSequence` 且按内容比较的特性。
- 链式断言 `set1` 等于 `set2`、`set3`、`set4`，且四者 `hashCode` 全部相等。其中 `set4.hashCode()` 是 `ImmutableSet` 对 wrapper 元素求和，与 `set1.hashCode()`（新写法对 wrapper 求和）一致，验证了 hashCode 显式重写的正确性。

测试用 `assertThat(...).isEqualTo(...).isEqualTo(...)` 链式写法一次性覆盖多个等价对象，并显式断言 hashCode 链式相等，是验证 `equals`/`hashCode` 契约的精炼写法。

## 小结

本提交修复了 `CharSequenceSet` 违反 `Set` 契约的 `equals`：旧的 `getClass` 严格判断导致跨 Set 类型（尤其是 `unmodifiableSet` 视图）出现不对称相等，是潜在的隐蔽 bug 源。修复采用 `AbstractSet` 标准模板（Set + 等大小 + containsAll + 异常归 false），把判等放宽到任意 Set，恢复对称性与通用契约。`hashCode` 同步重写为 wrapper 哈希之和的显式表达，数值不变但意图清晰、与内容判等对齐。新增的测试覆盖了混合 CharSequence 类型、unmodifiable 视图、`Set<CharSequenceWrapper>` 三类关键场景，并对 hashCode 一致性做了断言。修复提升了 `CharSequenceSet` 在缓存/断言/去重等依赖 Set 通用语义场景下的正确性，是 api 模块工具类健壮性的重要补强。
