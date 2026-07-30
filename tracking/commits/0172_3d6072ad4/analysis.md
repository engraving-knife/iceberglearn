# 提交 0172：API: Add CharSequenceMap (#9047)

## 提交信息

- **序号**：0172 / 4088
- **哈希**：3d6072ad4d94ba8fb83fcecfdaa2a2a326e99e15
- **短哈希**：3d6072ad4
- **日期**：2023-11-16 15:41:29 -0800
- **作者**：Anton Okolnychyi
- **提交说明**：API: Add CharSequenceMap (#9047)
- **PR/Issue**：#9047

## 总体目的

这个提交在 Iceberg `api` 模块下新增 `CharSequenceMap<V>` —— 一个以 `CharSequence` 为键的 Map 实现，填补 CharSequence 工具族中"Set 已有、Map 缺失"的空白。Iceberg 在很多地方用 `CharSequence` 作为键的语义（分区路径、文件路径、列名、字段名等），但 `String`、`StringBuilder`、`StringBuffer`、`CharBuffer` 等不同实现即使字符序列相同，其 `equals`/`hashCode` 也未必一致（`String` 与 `StringBuilder` 的 equals 就直接返回 false）。如果直接用 `Map<CharSequence, V>`（如 `HashMap`），同一个逻辑键用不同 CharSequence 实现存取就会取不到值，造成隐蔽 bug。

Iceberg 已有的 `CharSequenceWrapper`（包装 CharSequence 提供基于字符内容的 equals/hashCode）和 `CharSequenceSet`（基于 `CharSequenceWrapper` 的 Set）就是为解决该问题而存在的。本提交把同样的思路扩展到 Map：内部用 `Map<CharSequenceWrapper, V>` 存储，对外暴露 `Map<CharSequence, V>` 接口，使得无论调用方传入 `String`、`StringBuilder` 还是 `StringBuffer`，只要字符内容相同就视为同一个键。

这是 CharSequence 工具系列（`CharSequenceWrapper` → `CharSequenceSet` → `CharSequenceMap`）的自然延伸，统一了 Iceberg 内部对"按字符内容等同"语义的容器支持，对后续重构（把现有的 `Map<String, V>` 或自定义 wrapper map 替换为类型更准确的 `CharSequenceMap`）提供基础设施。

## 如何达成设计目的

新建 [`CharSequenceMap`](../../../api/src/main/java/org/apache/iceberg/util/CharSequenceMap.java) 实现 `Map<CharSequence, V>` 与 `Serializable`，内部委托给 `Map<CharSequenceWrapper, V>`。读操作（`containsKey` / `get` / `remove`）通过 `ThreadLocal<CharSequenceWrapper>` 复用 wrapper 实例避免反复分配；写操作（`put`）则 `CharSequenceWrapper.wrap(key)` 创建新 wrapper 长期持有作为内部 map 的键。`keySet()` 返回 `CharSequenceSet`，`entrySet()` 返回基于内部 `Map.Entry<CharSequenceWrapper, V>` 的转发视图 `CharSequenceEntry`。同时新增完整 `TestCharSequenceMap` 覆盖空 map、跨实现键等价、增删、并发读、compute/merge 等场景。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/CharSequenceMap.java`

**修改目的**：提供以 `CharSequence` 为键、按字符内容等价的 Map 实现。

**工作逻辑**：

核心字段：

```java
private static final ThreadLocal<CharSequenceWrapper> WRAPPERS =
    ThreadLocal.withInitial(() -> CharSequenceWrapper.wrap(null));
private final Map<CharSequenceWrapper, V> wrapperMap;
```

`ThreadLocal<CharSequenceWrapper>` 用于瞬态读操作，避免每次 `get`/`containsKey`/`remove` 都 `new` 一个 wrapper。线程私有，配合"无并发写、可并发读"的约定，安全且无锁。

- `put(CharSequence key, V value)`：调用 `CharSequenceWrapper.wrap(key)` 创建独立 wrapper 作为长期键存入 `wrapperMap`。这里不能用 ThreadLocal wrapper，因为内部 map 会持有该 wrapper 作为键，ThreadLocal wrapper 是临时复用的，会被后续操作 `set(null)` 清空。
- `get` / `containsKey` / `remove(Object key)`：先 `instanceof CharSequence` 判定（非 CharSequence 直接返回 false / null），再取 `WRAPPERS.get()`、`.set((CharSequence) key)` 复用，操作完后 `.set(null)` 释放对键的引用以防内存泄漏。这三处是热点路径，复用 wrapper 显著降低 GC 压力。
- `keySet()`：构造 `CharSequenceSet`（Iceberg 已有的同类工具），把内部 `wrapperMap.keySet()` 中每个 `CharSequenceWrapper.get()` 加入，对外返回 `Set<CharSequence>`。
- `entrySet()`：构造新 `HashSet<Entry<CharSequence, V>>`，每个内部 entry 用 `CharSequenceEntry<V>` 装饰；`CharSequenceEntry` 的 `getKey()` 返回 `inner.getKey().get()`（即解包回原始 CharSequence），`setValue` 抛 `UnsupportedOperationException`（不可变 entry），`equals`/`hashCode` 委托给内部 entry。
- `putAll`：`otherMap.forEach(this::put)`，逐项包装写入。
- `equals` / `hashCode`：直接比较/聚合 `wrapperMap`，因 `wrapperMap` 已基于 `CharSequenceWrapper` 的内容相等语义，所以两个 `CharSequenceMap` 即使各自存入时用的 CharSequence 实现不同（一个用 String、一个用 StringBuilder），只要内容相同就相等、hashCode 相同——这正是测试 `testEquals` / `testHashCode` 验证的。
- `toString`：用 entrySet 流式拼接 `"key=value"`，对 `(this Map)` 自引用做了保护，与 JDK `AbstractMap` 行为一致。
- 类级 Javadoc 明确说明：不支持并发写但支持并发读、不支持 null 键。

### `api/src/test/java/org/apache/iceberg/util/TestCharSequenceMap.java`

**修改目的**：覆盖 `CharSequenceMap` 的功能与并发读安全性。

**工作逻辑**：覆盖如下场景：

- `testEmptyMap`：空 map 的 size/containsKey/containsValue/values/keySet/entrySet 全部为空。
- `testDifferentCharSequenceImplementations`：put 时用 `String` 和 `StringBuffer`，断言时用 `StringBuilder` 和 `String`，验证跨实现键等价——这是 `CharSequenceMap` 的核心价值。
- `testPutAndGet` / `testRemove` / `testPutAll` / `testClear` / `testValues` / `testEntrySet`：常规 Map 契约。
- `testEquals` / `testHashCode`：两个 map 分别用 `StringBuilder("key")` 和 `"key"` 写入，断言 equals 与 hashCode 一致。
- `testToString`：空、单条目、多条目三种情况。
- `testComputeIfAbsent`：测试默认方法（`Map` 接口默认实现走 `get`+`put`，会经过 ThreadLocal wrapper 路径）。
- `testMerge`：覆盖存在键、不存在键、`BiFunction` 返回 null（应删除键）、原值为 null（应直接放入新值）四种分支。
- `testConcurrentReadAccess`：10 线程并发读 3 个键，验证 ThreadLocal wrapper 在多线程下互不干扰，1 分钟内必须完成——这是对"无并发写、可并发读"约定的回归保护。

## 小结

`CharSequenceMap` 把 `CharSequenceWrapper` 系列从 Set 扩展到 Map，为 Iceberg 内部"按字符内容等价"的键值存储提供了类型安全且高效的基础容器，统一并替代了原本散落各处的临时 wrapper map 方案。
