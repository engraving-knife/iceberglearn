# 提交 3544：API: Fix FileRange validation to reject negative offset/length (#15926)

## 提交信息

- **序号**：3544 / 4088
- **哈希**：46c1101ad8f5e13c0efd5d991d8ce22c8d7e7de9
- **短哈希**：46c1101ad
- **日期**：2026-04-15 20:12:56 -0700
- **作者**：Barry
- **提交说明**：API: Fix FileRange validation to reject negative offset/length (#15926)
- **PR/Issue**：#15926（修复 #15922）

## 总体目的

`FileRange` 构造函数本意是校验 `offset` 和 `length` 不能为负数，但实现存在一个顺序 bug：校验语句调用的是 `length()` 和 `offset()` 这两个 getter 方法，而此时构造函数参数还没有赋值给字段，字段仍是默认值 0。

```java
public FileRange(CompletableFuture<ByteBuffer> byteBuffer, long offset, int length) {
  Preconditions.checkArgument(length() >= 0, ...);  // length() 返回字段值，此时=0，校验通过
  Preconditions.checkArgument(offset() >= 0, ...);  // offset() 返回字段值，此时=0，校验通过
  this.byteBuffer = byteBuffer;
  this.offset = offset;   // 赋值发生在校验之后
  this.length = length;
}
```

因为校验发生在赋值之前，`length()`/`offset()` 返回的是字段默认值 0（>=0），校验永远通过。这意味着传入负数的 offset 或 length 会绕过校验，导致创建出非法的 `FileRange` 对象，可能在后续读取文件范围时产生错误行为（如读取负偏移）。

本提交将校验改为直接检查构造函数参数 `length` 和 `offset`（而非 getter），修复这个验证失效 bug。修复 #15922。

## 如何达成设计目的

把 `Preconditions.checkArgument(length() >= 0, ...)` 改为 `Preconditions.checkArgument(length >= 0, ...)`，`offset()` 同理。这样校验的是传入的参数值，而非未赋值的字段默认值。

## 修改详情

### `api/src/main/java/org/apache/iceberg/io/FileRange.java` (+2/-4 lines)

**修改目的**：校验构造函数参数而非 getter 返回值。

**工作逻辑**：
```java
-    Preconditions.checkArgument(
-        length() >= 0, "Invalid length: %s in range (must be >= 0)", length);
-    Preconditions.checkArgument(
-        offset() >= 0, "Invalid offset: %s in range (must be >= 0)", offset);
+    Preconditions.checkArgument(length >= 0, "Invalid length: %s in range (must be >= 0)", length);
+    Preconditions.checkArgument(offset >= 0, "Invalid offset: %s in range (must be >= 0)", offset);
```
关键变化：`length()` → `length`，`offset()` → `offset`。注意错误消息中的 `%s` 参数仍用 `length`/`offset` 变量（之前也是，但之前校验的是 getter）。

### `api/src/test/java/org/apache/iceberg/io/TestFileRange.java` (+62/-0 lines, new file)

**修改目的**：为 `FileRange` 构造函数校验添加单元测试。

**工作逻辑**：4 个测试用例：
- `validRange`：正常输入 (offset=10, length=100)，验证构造成功且字段正确
- `negativeLength`：length=-1，断言抛 `IllegalArgumentException` 且消息精确匹配 `"Invalid length: -1 in range (must be >= 0)"`
- `negativeOffset`：offset=-1, length=0，断言抛 `IllegalArgumentException` 且消息精确匹配 `"Invalid offset: -1 in range (must be >= 0)"`
- `nullByteBuffer`：byteBuffer=null，断言抛 `NullPointerException` 且消息匹配 `"byteBuffer can't be null"`

`negativeLength` 和 `negativeOffset` 测试正是修复前会绕过校验的场景，修复后正确抛异常。

## 总结

本提交修复了 `FileRange` 构造函数中校验顺序 bug：校验调用的是 `length()`/`offset()` getter（返回未赋值的字段默认值 0），导致负数 offset/length 绕过校验。改为直接校验构造函数参数。配有 4 个单元测试覆盖正常输入、负 offset、负 length、null byteBuffer 场景，修复了 #15922 报告的问题。
