# 提交 2995：Core: Align CharSequenceSet impl with Data/DeleteFileSet (#11322)

## 提交信息

- **序号**：2995 / 4088
- **哈希**：d894a0283e137c44d910acfc4a77119d9df2a005
- **短哈希**：d894a0283
- **日期**：2025-12-10
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Align CharSequenceSet impl with Data/DeleteFileSet (#11322)
- **PR/Issue**：#11322

## 总体目的

Iceberg 在 `api` 模块中有多个"基于包装器（Wrapper）的 Set 实现"：`CharSequenceSet`（用于分区值/字段名等 `CharSequence` 集合）、`DataFileSet`、`DeleteFileSet` 等。这些 Set 的共同特点是：底层用一个 `Set<Wrapper>` 来承载，通过 `Wrapper` 把元素包装成具备值相等语义的对象（因为 `CharSequence`、`DataFile` 等的默认 `equals` 不一定按内容比较）。

`DataFileSet`/`DeleteFileSet` 较早被重构为继承一个公共抽象基类 `WrapperSet<T>`，该基类统一实现了 `Set` 接口的全套方法（`add`/`remove`/`contains`/`retainAll`/`equals`/`hashCode` 等），并带来两个关键改进：1）底层使用 `Sets.newLinkedHashSet()` 维护插入顺序，便于确定性输出与调试；2）对 null 与类型不匹配采用快速失败策略——`Preconditions.checkNotNull` 抛 `NullPointerException`，`elementClass().cast` 抛 `ClassCastException`，而非静默返回 false。

但 `CharSequenceSet` 当时并未跟随重构，仍保留着自己手写的、基于 `Sets.newHashSet()`（无序）且对 null/类型不匹配静默处理的实现。这种不一致带来隐患：同一套语义在不同 Set 上行为不同（如 `CharSequenceSet.of([null])` 原本会"包含" null，而 `DataFileSet` 会抛 NPE），迭代顺序也不确定，且代码重复。

本提交将 `CharSequenceSet` 改为继承 `WrapperSet<CharSequence>`，删除其自写的全部 Set 方法实现，统一到 `DataFileSet`/`DeleteFileSet` 的行为模型上，从而消除三者的实现差异，减少重复代码，并使 `CharSequenceSet` 获得插入顺序保持与快速失败特性。同时大幅扩展测试以覆盖新行为（null 抛 NPE、类型不匹配抛 CCE、插入顺序、toArray、序列化等）。

## 如何达成设计目的

让 `CharSequenceSet extends WrapperSet<CharSequence>`，只保留三个抽象方法的实现（`wrapper()`、`wrap()`、`elementClass()`）与工厂方法；保留一个 `add` 覆盖仅为不破坏 API 兼容。让 `CharSequenceWrapper` 实现 `WrapperSet.Wrapper<CharSequence>` 接口（其 `get`/`set` 方法加 `@Override`）。其余 Set 行为全部继承自 `WrapperSet`。测试侧改写原 `nullString` 用例为期望 NPE，并新增覆盖空集、插入顺序、addAll/contains/containsAll/retainAll/removeAll/remove/toArray、Kryo/Java 序列化等场景。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/CharSequenceSet.java` (+53/-130 lines, 大幅精简)

**修改目的**：让 `CharSequenceSet` 继承 `WrapperSet`，统一实现模型。

**工作逻辑**：

- 类声明由 `implements Set<CharSequence>, Serializable` 改为 `extends WrapperSet<CharSequence>`（`WrapperSet` 本身已 `implements Set<T>, Serializable`）。
- 保留 `ThreadLocal<CharSequenceWrapper> WRAPPERS`，用于 `wrapper()` 返回的可复用包装器（在 `contains`/`remove` 中复用以避免每次创建对象）。
- 构造器：私有无参构造 `CharSequenceSet()` 供序列化与 `empty()` 使用；私有构造 `CharSequenceSet(Iterable<? extends CharSequence>)` 调用 `super(...)`，传入对每个元素 `Preconditions.checkNotNull` 后 `CharSequenceWrapper::wrap` 的转换结果，使 null 在构造期即快速失败。
- 工厂方法：`of(Iterable<? extends CharSequence>)` 签名放宽为通配符类型；`empty()` 返回 `new CharSequenceSet()`。
- 实现三个抽象方法：`wrapper()` 返回 `WRAPPERS.get()`；`wrap(CharSequence)` 返回 `CharSequenceWrapper.wrap(file)`；`elementClass()` 返回 `CharSequence.class`（供 `WrapperSet` 做 `cast` 校验）。
- 显式保留 `add(CharSequence)` 并调用 `super.add(charSequence)`，注释说明仅为不破坏 API 兼容（`WrapperSet.add` 已是 public final 语义等价）。
- 删除全部自写的 `size/isEmpty/contains/iterator/toArray/add/remove/containsAll/addAll/retainAll/removeAll/clear/equals/hashCode/toString`，以及不再使用的 `Serializable/Collection/Iterator/Set/ImmutableList/Iterators/Sets/Streams/Collectors` 等导入与 `wrapperSet` 字段。

行为变化要点：底层集合由 `newHashSet` 改为 `WrapperSet` 中的 `newLinkedHashSet`，迭代顺序变为插入顺序；null 元素由静默接受改为抛 `NullPointerException("Invalid object: null")`；非 `CharSequence` 元素由静默忽略改为抛 `ClassCastException`。

### `api/src/main/java/org/apache/iceberg/util/CharSequenceWrapper.java` (+5/-2 lines)

**修改目的**：让 `CharSequenceWrapper` 实现 `WrapperSet.Wrapper` 接口。

**工作逻辑**：

- 类声明由 `implements CharSequence, Serializable` 改为 `implements CharSequence, WrapperSet.Wrapper<CharSequence>`。`WrapperSet.Wrapper` 内部接口已继承 `Serializable`，且声明了 `T get()` 与 `Wrapper<T> set(T)`。
- 为 `set(CharSequence)` 与 `get()` 加上 `@Override`，使其正式履行 `Wrapper` 契约。
- 移除不再需要的 `import java.io.Serializable`。

由于 `CharSequenceWrapper` 原本就有这两个方法且签名兼容，改动主要是类型标注与编译期约束，运行时行为不变，但使 `WrapperSet` 能通过 `Wrapper` 接口统一操作包装器。

### `api/src/test/java/org/apache/iceberg/util/TestCharSequenceSet.java` (+175/-7 lines)

**修改目的**：覆盖对齐后的新行为（快速失败、插入顺序、序列化等）。

**工作逻辑**：

- 改写 `nullString`：原断言 `CharSequenceSet.of([null]).contains(null)`，现断言 `assertThatThrownBy(...).isInstanceOf(NullPointerException.class).hasMessage("Invalid object: null")`；`empty()` 仍不含 null。
- 新增 `emptySet`：验证空集为空且不含任意值。
- 新增 `insertionOrderIsMaintained`：依次 add "d","a","c","b","d"，断言 `containsExactly("d","a","c","b")`，验证 `LinkedHashSet` 带来的插入顺序保持。
- 新增 `clear`：清空后为空。
- 新增 `addAll`：覆盖 null 集合/null 元素抛 NPE，以及去重后顺序保持。
- 新增 `contains`/`containsAll`：覆盖 null 抛 NPE，含/不含判断，以及构造期 null 抛 NPE。
- 扩展 `testRetainAll`：覆盖 null 集合/null 元素抛 NPE，非 CharSequence 元素抛 `ClassCastException("Cannot cast java.lang.Integer to java.lang.CharSequence")`，并把原用 `Integer` 123 的用例改为合法的字符串值，验证 retain 行为。
- 新增 `toArray`：覆盖无参、目标数组过小/恰好/过大（尾部补 null）等分支。
- 新增 `remove`：覆盖 null 抛 NPE 与逐个移除直到空。
- 扩展 `testRemoveAll`：与 retainAll 类似覆盖 NPE/CCE，并把原 `Integer` 用例改为字符串。
- 新增 `kryoSerialization` 与 `javaSerialization`：用 `TestHelpers` 对 `CharSequenceSet.of("c","b","a")` 做 Kryo 与 Java 序列化往返，断言等于原集合（验证序列化兼容，对应 `WrapperSet` 与 `CharSequenceSet` 的无参构造供反序列化使用）。

## 总结

本提交将 `CharSequenceSet` 从自写的、无序且对 null/类型错误静默处理的实现，重构为继承公共 `WrapperSet<CharSequence>` 基类，与 `DataFileSet`/`DeleteFileSet` 行为对齐，统一了三套包装器 Set 的实现模型。重构带来插入顺序保持、null/类型快速失败、代码去重与序列化一致性，并伴随大幅扩展的测试覆盖各分支与新契约，是一次质量较高的内部一致性改进，降低了维护成本与潜在的行为歧义。
