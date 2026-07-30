# 提交 0707：Core: Lazily compute & cache hashCode in CharSequenceWrapper

## 提交信息
- **序号**：0707 / 4088
- **哈希**：a23021d05d882f6c7b621159e151b4b076a7e98b
- **短哈希**：a23021d05
- **日期**：2024-04-22
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Lazily compute & cache hashCode in CharSequenceWrapper (#10023)
- **PR/Issue**：#10023

## 总体目的

`CharSequenceWrapper` 是 Iceberg 核心模块（`api` 模块）中的一个工具类，用于将任意 `CharSequence`（包括 `String`、`StringBuffer`、`StringBuilder` 等）包装成可作为 `HashMap`/`HashSet` 键的对象。它被 `CharSequenceMap` 和 `CharSequenceSet` 等集合类大量使用，在 Iceberg 的元数据解析、属性匹配、字段名查找等热点路径上被频繁调用。

本次提交要解决的核心性能问题是：**原实现中每次调用 `hashCode()` 都会重新计算哈希值，没有缓存**。`JavaHashes.hashCode(CharSequence)` 需要遍历整个字符序列逐字符计算，对于长字符串这是 O(n) 的开销。当 `CharSequenceWrapper` 被用作 HashMap 的键时，每次 `get`/`put`/`containsKey` 操作都会触发 `hashCode()` 调用，导致重复计算相同字符串的哈希值，造成不必要的 CPU 消耗。

此外，本次提交还修复了一个潜在的空指针问题：当 `CharSequenceWrapper` 包装的对象为 `null` 时，原 `JavaHashes.hashCode()` 会抛出 `NullPointerException`，且 `equals()` 方法对 `null` 的处理也不完整。

通过引入**惰性计算 + 缓存**的哈希值机制（参照 `java.lang.String` 的实现模式），本次提交在保持原有语义不变的前提下显著降低了热点路径上的重复哈希计算开销。

## 如何达成设计目的

提交采用了 JDK 中 `java.lang.String` 同款的标准哈希缓存模式，其设计要点如下：

1. **新增两个 transient 字段**：
   - `private transient int hashCode = 0;` —— 缓存计算出的哈希值，默认 0 表示"未计算"。
   - `private transient boolean hashIsZero = false;` —— 标记哈希值是否真的为 0，用于区分"未计算"和"计算结果恰为 0"两种情况。这是必要的，因为单靠 `hashCode == 0` 无法区分"还没算过"和"算出来就是 0"。

2. **为什么用 `transient`**：`CharSequenceWrapper` 实现了 `Serializable`。哈希缓存属于派生状态，不应参与序列化，序列化后应重新计算，因此标记为 `transient`。

3. **惰性计算逻辑**：`hashCode()` 方法只在第一次被调用时计算哈希值并缓存。后续调用直接返回缓存值，将 O(n) 操作降为 O(1)。

4. **`set()` 方法重置缓存**：`CharSequenceWrapper` 是可变对象，`set(CharSequence)` 会替换内部包装的对象，此时必须清除缓存（`hashCode = 0; hashIsZero = false;`），否则会用旧字符串的哈希匹配新字符串。

5. **`hashIsZero` 的作用**：这是整个设计的精妙之处。如果仅用 `hashCode == 0` 判断是否已计算，那么当真实哈希值为 0 时，每次调用都会重新计算，缓存形同虚设。引入 `hashIsZero` 后：
   - 若 `hashCode != 0`，直接返回缓存值。
   - 若 `hashCode == 0 && hashIsZero == true`，说明已计算过且结果为 0，直接返回 0。
   - 若 `hashCode == 0 && hashIsZero == false`，说明从未计算，执行计算：若结果为 0 则置 `hashIsZero = true`，否则存入 `hashCode`。

6. **空值兼容**：`JavaHashes.hashCode()` 新增 `null` 检查返回 0；`CharSequenceWrapper.equals()` 新增 `null == wrapped && null == that.wrapped` 的判断。这样两个包装 `null` 的 wrapper 视为相等且哈希为 0，可安全作为 Map 键。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/JavaHashes.java`
**修改目的**：为 `hashCode(CharSequence)` 方法增加空值保护。

**工作逻辑**：在方法入口增加 `if (null == str) { return 0; }`，使 `null` 字符序列的哈希值为 0，避免 NPE。这与 `java.util.Objects.hashCode(null)` 返回 0 的约定一致，也使 `CharSequenceWrapper` 能安全包装 `null`。

### `api/src/main/java/org/apache/iceberg/util/CharSequenceWrapper.java`
**修改目的**：引入哈希缓存的惰性计算机制，并修复 `null` 处理。

**工作逻辑**：

1. **新增字段**（在 `private CharSequence wrapped;` 之后）：
   ```java
   private transient int hashCode = 0;
   private transient boolean hashIsZero = false;
   ```

2. **`set()` 方法**：在 `this.wrapped = newWrapped;` 之后追加 `this.hashCode = 0; this.hashIsZero = false;` 重置缓存。这保证可变对象在替换内容后哈希能正确重算，维护 `equals`/`hashCode` 一致性。

3. **`equals()` 方法**：在已有的 `wrapped.equals(that.wrapped)` 短路判断之后，新增：
   ```java
   if (null == wrapped && null == that.wrapped) {
     return true;
   }
   ```
   处理双方都为 `null` 的情形。注意此判断必须放在 `length()` 调用之前，否则 `null.length()` 会 NPE。

4. **`hashCode()` 方法**（核心改动）：从原来的 `return JavaHashes.hashCode(wrapped);` 改为：
   ```java
   int hash = hashCode;
   if (hash == 0 && !hashIsZero) {
     hash = JavaHashes.hashCode(wrapped);
     if (hash == 0) {
       hashIsZero = true;
     } else {
       this.hashCode = hash;
     }
   }
   return hash;
   ```
   注意：当计算结果为 0 时不写回 `this.hashCode`（保持 0），只置 `hashIsZero = true`；当结果非 0 时才写回 `this.hashCode`。这种写法避免了对 `hashIsZero` 已为 true 时的多余写操作。

### `api/src/test/java/org/apache/iceberg/util/TestCharSequenceMap.java`
**修改目的**：补充 `CharSequenceMap` 对 `null` 键/值的边界测试。

**工作逻辑**：新增 `nullString()` 测试，断言空 map 不包含 `null` 键、不包含 `null` 值，验证 null 处理不会误判。

### `api/src/test/java/org/apache/iceberg/util/TestCharSequenceSet.java`
**修改目的**：补充 `CharSequenceSet` 对 `null` 元素的边界测试。

**工作逻辑**：新增 `nullString()` 测试，验证 `CharSequenceSet.of(Arrays.asList((String) null))` 确实包含 `null`，而空 set 不包含 `null`。

### `api/src/test/java/org/apache/iceberg/util/TestCharSequenceWrapper.java`（新增文件）
**修改目的**：为 `CharSequenceWrapper` 的哈希缓存与 null 处理建立完整的单元测试覆盖。

**工作逻辑**：包含 4 个测试用例：
- `nullWrapper()`：两个包装 `null` 的 wrapper 在哈希未计算时即相等；计算后哈希均为 0；之后仍相等。
- `equalsWithLazyHashCode()`：用 `String`、`StringBuffer`、`StringBuilder` 三种不同类型包装相同内容 `"v1"`，验证跨类型相等性在哈希计算前后都成立。
- `notEqualsWithLazyHashCode()`：验证 `"v1"` 与 `"v2"` 不等，且哈希也不等。
- `hashCodeIsRecomputed()`：验证 `set()` 替换内容后哈希能正确重算，覆盖 `String`、`StringBuffer`、`StringBuilder`、`null` 多种情形，并断言具体哈希值（如 `"v1"` → 173804、`"v2"` → 173805、`null` → 0），确保缓存被正确重置。

## 小结
- **成效**：成功实现哈希值的惰性计算与缓存，将 HashMap/HashSet 操作中重复的 O(n) 哈希计算降为首次 O(n) + 后续 O(1)，参照 `java.lang.String` 的成熟模式，实现稳健。同时修复了 null 兼容性问题。
- **影响范围**：`api` 模块核心工具类 `CharSequenceWrapper`，间接影响所有使用 `CharSequenceMap`/`CharSequenceSet` 的代码路径，包括元数据属性匹配、字段查找等热点路径，性能收益明显。
- **回迁到 1.4.x 的注意事项**：此为纯优化，API 兼容。回迁时需确保 `JavaHashes`、`CharSequenceWrapper` 及相关测试类一并迁移。注意 `hashCode` 缓存依赖 `set()` 方法正确重置，若 1.4.x 有其他修改 `wrapped` 字段的地方（绕过 `set()`），需同步加入缓存重置逻辑，否则会导致哈希不一致的隐蔽 bug。新增字段为 `transient`，不影响已有序列化兼容性。
