# 提交 1318：Core: Use RoaringPositionBitmap in position index (#11441)

## 提交信息

- **序号**：1318 / 4088
- **哈希**：caf424a373fa125d427401acda7079b08abea9de
- **短哈希**：caf424a37
- **日期**：2024-11-01（Fri Nov 1 20:18:34 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Use RoaringPositionBitmap in position index (#11441)
- **PR/Issue**：#11441

## 总体目的

`BitmapPositionDeleteIndex` 是 Iceberg 在读取侧用来表达"位置删除（position delete）"的内存索引：对一个数据文件中被删除的行号集合，提供 `delete(pos)`、`delete(start,end)`、`isDeleted(pos)`、`merge(...)`、`forEach(...)` 等操作。它原本基于 `org.roaringbitmap.longlong.Roaring64Bitmap`（RoaringBitmap 库提供的 64 位原生实现）实现。

社区在前序提交 1e3ee1e4e（#11372 "Add portable Roaring bitmap for row positions"）中新增了一个自研的 `RoaringPositionBitmap` 类：

- 它支持正数 64-bit 行位置（最高位必须为 0），但内部用**一个 32-bit `RoaringBitmap` 数组**实现——把 64-bit 拆成"高 32-bit key + 低 32-bit pos"两段，每个 key 对应一个 32-bit Roaring bitmap。
- 这种"可移植"实现的关键价值在于：**on-disk 序列化格式是自定义的、跨语言可读**，而 `Roaring64Bitmap` 使用的是其 Java 库内部的序列化格式（依赖 `LongConsumer` 与 moriarty 推送），其他引擎（C++/Python/Rust 等实现）难以共享。
- 此外，由于实际场景中绝大多数行号都落在 32-bit 以内（一个数据文件通常不会超过 2^32 行），按 key 分桶后多数 key 都集中在前几个桶，整体内存与性能可媲美甚至优于 `Roaring64Bitmap`。

本提交的目的就是把 `BitmapPositionDeleteIndex` 内部依赖从 `Roaring64Bitmap` 切换到 `RoaringPositionBitmap`，使位置删除索引的内存表示与未来跨引擎序列化格式统一，为后续基于 puffin 文件的 deletion vector 等特性铺路。

## 如何达成设计目的

1. 把字段类型从 `Roaring64Bitmap roaring64Bitmap` 改为 `RoaringPositionBitmap bitmap`，并相应改三个构造器中的 `new Roaring64Bitmap()` 为 `new RoaringPositionBitmap()`。
2. 把方法调用从 `Roaring64Bitmap` 的 API 映射到 `RoaringPositionBitmap` 的等价 API：
   - `roaring64Bitmap.or(that.roaring64Bitmap)` → `bitmap.setAll(that.bitmap)`（合并位图）；
   - `roaring64Bitmap.add(position)` → `bitmap.set(position)`（单点删除）；
   - `roaring64Bitmap.addRange(posStart, posEnd)` → `bitmap.setRange(posStart, posEnd)`（区间删除）；
   - `roaring64Bitmap.contains(position)` → `bitmap.contains(position)`；
   - `roaring64Bitmap.isEmpty()` → `bitmap.isEmpty()`；
   - `roaring64Bitmap.forEach(consumer::accept)` → `bitmap.forEach(consumer)`。
3. 移除对 `org.roaringbitmap.longlong.Roaring64Bitmap` 的 import。

注意语义差异：
- `Roaring64Bitmap.or(...)` 是原地 OR；`RoaringPositionBitmap.setAll(...)` 同样是原地修改本对象、把传入位图的所有位置合并进来，语义等价。
- `Roaring64Bitmap.forEach(LongConsumer)` 接受一个 `LongConsumer`；原代码用 `consumer::accept` 把 `LongConsumer` 适配成 `org.roaringbitmap.longlong.LongConsumer` 接口（两者方法签名相同但属于不同类型，需要方法引用适配）。新代码 `RoaringPositionBitmap.forEach(LongConsumer)` 直接接受 `java.util.function.LongConsumer`，因此无需适配，直接传 `consumer` 即可。
- `Roaring64Bitmap.addRange(start, end)` 与 `RoaringPositionBitmap.setRange(start, endExclusive)` 都是左闭右开区间，语义一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`

**修改目的**：把位置删除索引的内部位图实现切换为自研的 `RoaringPositionBitmap`。

**工作逻辑**：

类定义部分：

```java
class BitmapPositionDeleteIndex implements PositionDeleteIndex {
  private final RoaringPositionBitmap bitmap;   // 原：Roaring64Bitmap roaring64Bitmap
  private final List<DeleteFile> deleteFiles;
  ...
}
```

三个构造器把 `new Roaring64Bitmap()` 替换为 `new RoaringPositionBitmap()`。

方法映射（关键点）：

| 原方法（Roaring64Bitmap） | 新方法（RoaringPositionBitmap） | 说明 |
|---|---|---|
| `or(that.roaring64Bitmap)` | `setAll(that.bitmap)` | 原地合并 |
| `add(position)` | `set(position)` | 单点 |
| `addRange(posStart, posEnd)` | `setRange(posStart, posEnd)` | 区间，左闭右开 |
| `contains(position)` | `contains(position)` | 查询 |
| `isEmpty()` | `isEmpty()` | 判空 |
| `forEach(consumer::accept)` | `forEach(consumer)` | 遍历 |

`forEach` 简化：原本因为 `Roaring64Bitmap.forEach` 接受 `org.roaringbitmap.longlong.LongConsumer`（与 `java.util.function.LongConsumer` 是不同接口但签名相同），必须用方法引用 `consumer::accept` 桥接；新 `RoaringPositionBitmap.forEach` 直接接受 `java.util.function.LongConsumer`，可以直接传 `consumer`，代码更简洁。

import 调整：

- 删除 `import org.roaringbitmap.longlong.Roaring64Bitmap;`。
- 由于 `RoaringPositionBitmap` 与 `BitmapPositionDeleteIndex` 同在 `org.apache.iceberg.deletes` 包下，无需新增 import。

对外行为保持一致：`PositionDeleteIndex` 接口的所有方法（`delete`、`isDeleted`、`isEmpty`、`forEach`、`merge`、`deleteFiles`）签名与行为不变，对外部调用者透明。

## 小结

- **成效**：`BitmapPositionDeleteIndex` 内部位图实现从依赖 RoaringBitmap 库的 `Roaring64Bitmap` 切换为 Iceberg 自研的可移植 `RoaringPositionBitmap`，与 #11372 引入的跨语言序列化格式对接，为后续 puffin deletion vector、跨引擎共享位置删除索引等特性铺路。同时消除了对 `Roaring64Bitmap` 这一外部依赖的使用点之一。
- **影响范围**：1 个文件、10 行增、11 行删。属于内部实现替换，`PositionDeleteIndex` 接口对外行为不变。
- **回迁到 1.4.x 的注意事项**：
  - 回迁前必须先回迁前序提交 #11372（`RoaringPositionBitmap` 类的定义及测试）。该提交体量较大（约 1000 行，含基准测试与序列化样例文件），回迁时需配套引入。
  - 1.4.x 已有基于 `Roaring64Bitmap` 的 `BitmapPositionDeleteIndex` 实现工作良好。若 1.4.x 不计划引入跨引擎 deletion vector 或 puffin 序列化新格式，**不强制回迁**此改动。
  - 若回迁，需确认 1.4.x 中 `Roaring64Bitmap` 是否还有其他使用点（如 puffin 写出端、benchmark 等）；若有，需统一规划迁移，避免两种位图实现并存造成混乱。
  - 性能上 `RoaringPositionBitmap` 在多数 32-bit 行号场景下与 `Roaring64Bitmap` 相当甚至更优，但 `setRange` 在 1e3ee1e4e 引入时还是逐点循环实现（后续提交 #15791 才优化为原生 range API），1.4.x 若回迁需注意区间删除大范围时的性能特征，可考虑一并回迁 #15791 的优化。
  - 由于对外接口不变，回迁对下游引擎透明，无破坏性影响。
