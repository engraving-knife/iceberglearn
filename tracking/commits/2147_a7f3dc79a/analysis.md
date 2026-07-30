# 提交 2147：Core: Copy ByteBuffers when Copying GenericRecord

## 提交信息

- **序号**：2147 / 4088
- **哈希**：a7f3dc79a2f42a4875ac35eec2137ecff15204fc
- **短哈希**：a7f3dc79a
- **日期**：2025-05-19 10:01:40 -0700
- **作者**：hsingh574
- **提交说明**：Core: Copy ByteBuffers when Copying GenericRecord (#12855)
- **PR/Issue**：#12855

## 总体目的

这个提交修复了 GenericRecord 的 copy() 方法中的浅拷贝问题。GenericRecord 是 Iceberg 中用于表示通用数据记录的实现类，其拷贝构造函数此前使用 Arrays.copyOf 来复制值数组。虽然 Arrays.copyOf 会创建新的数组对象，但对于数组中的元素仍然是引用拷贝（浅拷贝）。这意味着如果字段值是 ByteBuffer 类型，拷贝后的记录和原始记录会共享同一个 ByteBuffer 实例，修改其中一个会影响另一个。同样，嵌套的 GenericRecord 也没有被深拷贝。这个提交通过引入深拷贝逻辑，对 ByteBuffer 和嵌套 GenericRecord 进行独立拷贝，确保记录拷贝的完全独立性。

## 如何达成设计目的

1. 修改 GenericRecord 的拷贝构造函数，不再使用 Arrays.copyOf 进行浅拷贝，而是遍历每个值并调用 deepCopyValue 方法进行深拷贝。
2. 新增 deepCopyValue 方法，对 ByteBuffer 使用 ByteBuffers.copy 进行拷贝，对嵌套 GenericRecord 递归调用 copy()，其他类型直接返回引用（不可变类型无需拷贝）。
3. 在 DeleteReadTests、TestGenericReaderDeletes 和 TestGenericRecord 中添加测试用例验证深拷贝行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/data/GenericRecord.java` (修改, +14/-3 lines)

**修改目的**：实现 GenericRecord 的深拷贝，解决 ByteBuffer 和嵌套记录的共享引用问题。

**工作逻辑**：
- 添加了对 `java.nio.ByteBuffer` 和 `org.apache.iceberg.util.ByteBuffers` 的导入。
- 修改拷贝构造函数 `GenericRecord(GenericRecord toCopy)`：将 `this.values = Arrays.copyOf(toCopy.values, toCopy.values.length)` 替换为创建新数组并逐个调用 `deepCopyValue` 进行深拷贝。
- 新增 `deepCopyValue(Object value)` 方法：判断值的类型，如果是 ByteBuffer 则调用 `ByteBuffers.copy()` 创建独立的 ByteBuffer 副本；如果是 GenericRecord 则递归调用其 `copy()` 方法；其他类型（如 String、Integer 等不可变类型）直接返回原引用。

### `core/src/test/java/org/apache/iceberg/data/DeleteReadTests.java` (修改, +103/-2 lines)

**修改目的**：验证深拷贝在删除读取场景下的正确性。

**工作逻辑**：新增测试用例，验证拷贝 GenericRecord 后，修改原始记录的 ByteBuffer 字段不会影响拷贝后的记录，确保数据独立性。

### `core/src/test/java/org/apache/iceberg/data/TestGenericReaderDeletes.java` (修改, +4/-2 lines)

**修改目的**：更新测试以适配深拷贝行为。

**工作逻辑**：调整测试中的记录拷贝相关断言，确保深拷贝后记录的独立性。

### `core/src/test/java/org/apache/iceberg/data/TestGenericRecord.java` (修改, +22/-3 lines)

**修改目的**：添加针对 GenericRecord 深拷贝的单元测试。

**工作逻辑**：新增测试方法，验证拷贝后的 GenericRecord 中 ByteBuffer 字段与原始记录独立，修改其中一个不会影响另一个。

## 总结

这个提交修复了 GenericRecord 拷贝操作中的浅拷贝问题，对 ByteBuffer 和嵌套 GenericRecord 实现了深拷贝。这个修复对于需要安全拷贝记录的场景（如并发读取、数据缓存等）具有重要意义，避免了因共享引用导致的数据不一致风险。
