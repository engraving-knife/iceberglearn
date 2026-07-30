# 提交 0777：Core: Replace deprecated Roaring64Bitmap#add call with addRange (#10350)

## 提交信息

- **序号**：0777 / 4088
- **哈希**：236f6255b1c7eadc8338d23239755e539935c580
- **短哈希**：236f6255b
- **日期**：2024-05-18 10:50:04 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Replace deprecated Roaring64Bitmap#add call with addRange (#10350)
- **PR/Issue**：#10350

## 总体目的

本提交将 `BitmapPositionDeleteIndex.delete(long posStart, long posEnd)` 中对 `Roaring64Bitmap` 的调用从已被废弃的 `add(long, long)` 替换为推荐的 `addRange(long, long)`。这是一次纯粹的 API 迁移（API migration），目的是消除对已废弃 API 的依赖，避免在未来 RoaringBitmap 主版本升级时该方法被移除而导致编译失败，同时遵循上游库的 API 演进方向。

### 迁移背景与废弃原因（详细解释）

`Roaring64Bitmap` 是 Java RoaringBitmap 库（`org.roaringbitmap:RoaringBitmap`）中用于支持 64 位长整型集合的压缩位图实现，Iceberg 在 `core` 模块用它实现行级位置删除索引（position delete index）。该类同时存在两个语义相近的方法：

- `add(long x)`：将单个长整型值加入位图。
- `add(long rangeStart, long rangeEnd)`：将闭开区间 `[rangeStart, rangeEnd)` 内的所有整数加入位图（含 `rangeStart`，不含 `rangeEnd`）。

由于方法名 `add` 同时被单值版本与区间版本重载（overload），仅凭方法名和参数个数难以一眼区分调用意图；尤其在代码审查与静态分析中，`add(a, b)` 很容易被误读为"添加两个独立的值"而非"添加一段连续区间"。为消除这种歧义，RoaringBitmap 上游在较新版本中将双参数的 `add(long, long)` 标记为 `@Deprecated`，并引入命名更明确的 `addRange(long rangeStart, long rangeEnd)` 作为替代。

**新 API 用法**：`addRange(long rangeStart, long rangeEnd)` 的语义与原 `add(long, long)` 完全一致——把 `[rangeStart, rangeEnd)` 半开区间内的所有整数置入位图，即 `rangeStart` 含、`rangeEnd` 不含。因此本次替换是行为等价的纯重命名式迁移，不涉及任何语义或边界条件变化。

**与 Iceberg 语义的吻合**：`BitmapPositionDeleteIndex.delete(long posStart, long posEnd)` 的契约正是"标记 `[posStart, posEnd)` 区间内的行位置为已删除"（`posStart` 含、`posEnd` 不含，文件 Javadoc 明确标注 `@param posEnd 区间结束位置（不含）`）。这与 `addRange` 的半开区间语义完全一致，迁移后行为不变。

Iceberg 当前依赖的 RoaringBitmap 版本为 `0.9.47`（见 `gradle/libs.versions.toml` 中 `roaringbitmap = "0.9.47"`），在该版本区间 `add(long, long)` 已被标记 deprecated，故本次替换是及时跟进上游 API 演进的预防性维护。

## 如何达成设计目的

整体设计思路是将单点调用从废弃方法名切换到推荐方法名，不引入任何额外抽象或行为改变。改动集中在 `BitmapPositionDeleteIndex` 的 `delete(long, long)` 方法体内部一行：把 `roaring64Bitmap.add(posStart, posEnd)` 改为 `roaring64Bitmap.addRange(posStart, posEnd)`。由于两者参数列表与区间语义完全相同，调用点无需调整参数顺序或加偏移，迁移零风险。该方法没有其他调用方，也不涉及序列化格式（位图的内部存储结构未变），因此无需配套改动测试或序列化兼容性代码。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`

**修改目的**：将区间删除实现从废弃 API 迁移到推荐 API。

**工作逻辑**：

`delete(long posStart, long posEnd)` 方法用于标记一段连续行位置为已删除。修改前调用 `roaring64Bitmap.add(posStart, posEnd)`，修改后调用 `roaring64Bitmap.addRange(posStart, posEnd)`。

```java
@Override
public void delete(long posStart, long posEnd) {
-  roaring64Bitmap.add(posStart, posEnd);
+  roaring64Bitmap.addRange(posStart, posEnd);
}
```

两处对比要点：

1. **参数不变**：`posStart`（区间起点，含）与 `posEnd`（区间终点，不含）的传递顺序与含义完全一致，无需任何适配。
2. **语义不变**：`addRange` 与原 `add(long, long)` 均为半开区间 `[start, end)` 语义，位图内部存储与序列化格式不变，对读取侧 `isDeleted(long)` 的判定结果无任何影响。
3. **废弃告警消除**：替换后该调用点不再触发编译器的 deprecated 警告，代码对未来 RoaringBitmap 主版本（可能彻底移除 `add(long, long)`）具备前向兼容性。

该类中另一处 `delete(long position)` 单点删除仍使用 `roaring64Bitmap.add(position)`，这是单参数版本，未被废弃，故保持不变。

## 小结

- **成效**：消除了对 RoaringBitmap 已废弃 `add(long, long)` 的调用，避免未来上游移除该方法时编译失败；同时令区间删除的代码意图更清晰（`addRange` 一望即知是区间操作）。本次迁移为行为等价替换，不改变删除索引的任何运行时行为或序列化格式。
- **影响范围**：仅 `core` 模块单文件单行改动，属生产代码但无外部 API/行为变更。`BitmapPositionDeleteIndex` 是包级私有（`class`，非 `public`），影响面进一步收窄于 deletes 包内部。
- **回迁注意事项**：回迁到 1.4.x 分支时需确认该分支所依赖的 RoaringBitmap 版本（`gradle/libs.versions.toml` 中 `roaringbitmap`）已提供 `addRange` 方法。`addRange` 在 0.9.x 系列即已存在，1.4.x 通常使用相近版本，回迁一般无障碍。若 1.4.x 上 RoaringBitmap 版本过低导致 `addRange` 不存在，则需先升级 RoaringBitmap 依赖或暂缓此迁移。建议回迁时同步检查 `BitmapPositionDeleteIndex` 在 1.4.x 上的代码结构是否与本提交一致（Javadoc、方法签名），避免上下文偏移导致 patch 不干净。
