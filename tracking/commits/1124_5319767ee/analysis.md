# 提交 1124：Core: Refactor ZOrderByteUtils (#10624)

## 提交信息

- **序号**：1124 / 4088
- **哈希**：5319767ee91fd054b272020c58ab25acbe5ec9c2
- **短哈希**：5319767ee
- **日期**：2024-09-03（Tue Sep 3 23:55:51 2024 +0530）
- **作者**：Ajanth Bhat <ajanthabhat@gmail.com>
- **提交说明**：Core: Refactor ZOrderByteUtils (#10624)
- **PR/Issue**：#10624

## 总体目的

`ZOrderByteUtils` 负责把 Iceberg 的原始类型（int、long、short、tinyint、float、double）转换成"字节序可比较"（lexicographically comparable）的字节表示，是 Z-order 排序的底层基础。重构前，代码存在两个问题：

1. **职责命名不清晰**：`intToOrderedBytes`、`shortToOrderedBytes`、`tinyintToOrderedBytes` 都把不同宽度的整型提升为 long 后翻转符号位，实际逻辑完全相同，但分散在不同方法里，注释只挂在 `intToOrderedBytes` 上，导致读者误以为三者实现不同。
2. **浮点分支同理**：`floatToOrderedBytes` 只是转调 `doubleToOrderedBytes`，同样没有点明"两者本质相同"。

本次重构把"真正做字节编排"的逻辑抽成两个语义明确的方法 `wholeNumberOrderedBytes(long, ByteBuffer)`（整数族）和 `floatingPointOrderedBytes(double, ByteBuffer)`（浮点族），让各类型的 `*ToOrderedBytes` 方法变成只调一行的薄封装，并在 Javadoc 中明确写出"内部仅调用某某方法"。这样后续若要新增类型或修改编码逻辑，只需改一处真正的实现。

## 如何达成设计目的

纯重构，不改任何外部行为：

- 新增 `wholeNumberOrderedBytes(long, ByteBuffer)`：把原 `longToOrderedBytes` 的实现（`val ^ 0x8000000000000000L` 翻转符号位）原样搬入，并把符号位翻转的 Javadoc 注释挂到这里。
- 新增 `floatingPointOrderedBytes(double, ByteBuffer)`：把原 `doubleToOrderedBytes` 的实现（`Double.doubleToLongBits` + 符号位/指数位翻转）原样搬入，注释中把"floats"措辞改为更准确的"doubles"。
- 让 `intToOrderedBytes`、`longToOrderedBytes`、`shortToOrderedBytes`、`tinyintToOrderedBytes` 都退化为单行调用 `wholeNumberOrderedBytes`；让 `floatToOrderedBytes`、`doubleToOrderedBytes` 退化为单行调用 `floatingPointOrderedBytes`。

每个薄方法都加了 `/** Internally just calls {@link ...} */` 注释，明确"它就是某个统一实现的别名"。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ZOrderByteUtils.java`

**修改目的**：把整数族、浮点族的字节编码逻辑各自收敛到单一实现，消除重复，改善可读性与可维护性。

**工作逻辑**：

1. 整数族：原先 `intToOrderedBytes` 自带实现，`short/tinyint` 转调 `intToOrderedBytes`，`longToOrderedBytes` 又另写一份相同实现。重构后：

   ```java
   public static ByteBuffer intToOrderedBytes(int val, ByteBuffer reuse) {
     return wholeNumberOrderedBytes(val, reuse);
   }
   public static ByteBuffer longToOrderedBytes(long val, ByteBuffer reuse) {
     return wholeNumberOrderedBytes(val, reuse);
   }
   public static ByteBuffer shortToOrderedBytes(short val, ByteBuffer reuse) {
     return wholeNumberOrderedBytes(val, reuse);
   }
   public static ByteBuffer tinyintToOrderedBytes(byte val, ByteBuffer reuse) {
     return wholeNumberOrderedBytes(val, reuse);
   }
   ```

   `wholeNumberOrderedBytes` 保留原 `longToOrderedBytes` 的实现和"翻转符号位使负数排在前"的注释。

2. 浮点族：原先 `floatToOrderedBytes` 转调 `doubleToOrderedBytes`。重构后两者都改为调用 `floatingPointOrderedBytes`，并把原来描述"floats 可视为 sign-magnitude 整数"的注释措辞修正为"doubles"（因为真正实现操作的是 double 的位表示）。

注意：方法签名（参数类型仍按各自的 `int/short/byte/float/double`）和行为完全不变，只是内部实现统一。由于 short/tinyint/float 在传入时会自动隐式提升为 long/double，符号位翻转逻辑对所有整数族/浮点族类型都正确成立。

## 小结

- **成效**：Z-order 字节编码逻辑从 6 个分散方法收敛到 2 个真实实现 + 6 个薄封装，消除了"看似不同实则相同"的误导，后续维护只需改一处；同时修正了浮点注释里 "floats" 措辞不准确的瑕疵。
- **影响范围**：仅 `ZOrderByteUtils.java` 一个文件，29 增 30 删，纯内部重构，无 API 变更、无行为变更。
- **回迁到 1.4.x 的注意事项**：这是可读性/可维护性重构，对运行时无任何影响。1.4.x **无需回迁**——除非 1.4.x 后续要在此基础上做 Z-order 相关的功能改动。若 1.4.x 已有 ZOrderByteUtils 且想保持与 main 一致以便未来合并，可安全 cherry-pick，因为不改变任何外部行为；但单独回迁收益有限。
