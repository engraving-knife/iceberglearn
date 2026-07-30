# 提交 0308：Core: Use CharSequenceMap for writing unordered deletes (#9365)

## 提交信息

- **序号**：0308 / 4088
- **哈希**：6f4e33ec222299efc19f1bb75e782f058f5d1131
- **短哈希**：6f4e33ec2
- **日期**：2023-12-24 19:56:00 +0100
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Use CharSequenceMap for writing unordered deletes (#9365)
- **PR/Issue**：#9365

## 总体目的

本提交把 Iceberg Core 模块中负责"乱序 position 删除"写入的 `SortingPositionOnlyDeleteWriter` 的内部数据结构从手写的 `Map<CharSequenceWrapper, Roaring64Bitmap>` + 可复用 `CharSequenceWrapper` 字段的写法，替换为 Iceberg 在 #9047 中新引入的 `CharSequenceMap` 工具类，从而简化写入逻辑、消除手动"get-or-create"样板代码、移除对 `CharSequenceWrapper` 这种底层工具类的直接依赖。

`SortingPositionOnlyDeleteWriter` 是 Iceberg 中一个特殊的 position delete 写入器，专门用于处理"未按文件和位置有序到达"的 position delete（普通的 `PositionDeleteWriter` 要求输入已按 `(file, position)` 排序，否则写出的删除文件不符合规范）。该 writer 在内存中为每个数据文件路径维护一个 `Roaring64Bitmap`（按 64 位位置存储删除位图），写入时把所有删除先攒进内存中的 `path -> bitmap` 映射，`close()` 时再按路径字典序、每条路径内按位置升序，把所有 `(path, position)` 重新输出到底层 `FileWriter`——这样写出的删除文件即满足规范要求的有序性。`path` 是 `CharSequence` 类型（通常是数据文件的 file path 字符串），但 Iceberg 中 `CharSequence` 既可能是 `String` 也可能是 `CharSequenceWrapper` 包裹的片段，因此不能直接用普通 `HashMap<CharSequence, V>`（不同 CharSequence 实现的 `hashCode`/`equals` 不保证按字符序列等价），需要通过 `CharSequenceWrapper` 包装来获得"按字符序列内容等价"的语义。

原本的写法存在两个痛点：(1) 每次写入时要手工写"get-or-create"逻辑——用可复用的 `pathWrapper.set(path)` 做 `get` 查找，若返回 null 则创建新 bitmap 并 `put(CharSequenceWrapper.wrap(path), ...)`（注意 `put` 时不能再复用 `pathWrapper`，必须新建一个 wrapper 实例固化进 map，否则下次写入会覆盖掉已存的 key）；(2) 这种"两个 wrapper 角色不同"的微妙约定很容易出错，且把存储细节暴露给了使用方。#9047 引入的 `CharSequenceMap` 正是为这类场景设计的——它在内部用一个 `ThreadLocal<CharSequenceWrapper>` 做查找时的临时包装器、用 `CharSequenceWrapper.wrap(key)` 做存储时的固化包装器，对外暴露 `Map<CharSequence, V>` 接口，让使用方完全不用关心 wrapper 的生命周期。本提交即把 `SortingPositionOnlyDeleteWriter` 切换到这个新工具，跟进 #9047 的设计目标。

## 如何达成设计目的

整体思路是替换字段类型与简化 `write` 方法：把 `Map<CharSequenceWrapper, Roaring64Bitmap> positionsByPath` 字段（外加一个 `CharSequenceWrapper pathWrapper` 复用槽）整体替换为一个 `CharSequenceMap<Roaring64Bitmap> positionsByPath` 字段，构造时由 `CharSequenceMap.create()` 创建。`write` 方法原本的"用 `pathWrapper.set(path)` 查、若 null 则建新 bitmap 并 `put` 新 wrapper"的 9 行手工逻辑，替换为 `CharSequenceMap` 提供的 `computeIfAbsent(path, Roaring64Bitmap::new).add(position)` 一行——`CharSequenceMap` 继承 `Map` 接口的默认 `computeIfAbsent`，其 `get` 与 `put` 在内部完成 `CharSequence` 与 `CharSequenceWrapper` 之间的双向转换，调用方完全无感。下游的 `writeDeletes()` 迭代把 `for (CharSequenceWrapper path : sortedPaths())` 改为 `for (CharSequence path : sortedPaths())`，相应地把 `positionDelete.set(path.get(), position, null)` 简化为 `positionDelete.set(path, position, null)`；`sortedPaths()` 返回类型由 `List<CharSequenceWrapper>` 改为 `List<CharSequence>`，因为 `CharSequenceMap.keySet()` 已经返回 `CharSequenceSet`（含 `CharSequence` 元素），无需再用 wrapper 作为中间表示。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/SortingPositionOnlyDeleteWriter.java`

**修改目的**：将内部 `path -> Roaring64Bitmap` 映射从手写 `Map<CharSequenceWrapper, V>` + 复用 wrapper 改为 `CharSequenceMap<V>`，简化写入与迭代代码。

**工作逻辑**：

1. **import 调整**：移除 `java.util.Map`、`org.apache.iceberg.relocated.com.google.common.collect.Maps`、`org.apache.iceberg.util.CharSequenceWrapper`；新增 `org.apache.iceberg.util.CharSequenceMap`。

2. **字段类型替换**：

   ```java
   // 旧
   private final Map<CharSequenceWrapper, Roaring64Bitmap> positionsByPath;
   private final CharSequenceWrapper pathWrapper;
   // 新
   private final CharSequenceMap<Roaring64Bitmap> positionsByPath;
   ```

   去掉了独立的 `pathWrapper` 字段——`CharSequenceMap` 内部已用 `ThreadLocal<CharSequenceWrapper>` 管理临时 wrapper，外部不再需要持有。

3. **构造器简化**：

   ```java
   // 旧
   this.positionsByPath = Maps.newHashMap();
   this.pathWrapper = CharSequenceWrapper.wrap(null);
   // 新
   this.positionsByPath = CharSequenceMap.create();
   ```

4. **`write` 方法重构**——这是本提交最核心的简化：

   ```java
   // 旧
   @Override
   public void write(PositionDelete<T> positionDelete) {
     CharSequence path = positionDelete.path();
     long position = positionDelete.pos();
     Roaring64Bitmap positions = positionsByPath.get(pathWrapper.set(path));
     if (positions != null) {
       positions.add(position);
     } else {
       positions = new Roaring64Bitmap();
       positions.add(position);
       positionsByPath.put(CharSequenceWrapper.wrap(path), positions);
     }
   }
   // 新
   @Override
   public void write(PositionDelete<T> positionDelete) {
     CharSequence path = positionDelete.path();
     long position = positionDelete.pos();
     Roaring64Bitmap positions = positionsByPath.computeIfAbsent(path, Roaring64Bitmap::new);
     positions.add(position);
   }
   ```

   原代码的"两角色 wrapper"约定被消除：查找时复用 `pathWrapper`（避免每次创建 wrapper 对象），存储时必须 `CharSequenceWrapper.wrap(path)` 新建一个固化进 map（否则下次 `pathWrapper.set(...)` 会把已存 key 改写）。新代码完全不用关心这个细节——`CharSequenceMap.computeIfAbsent` 走 `Map` 默认实现，内部 `get` 时把 `CharSequence` 包进 `ThreadLocal` 的临时 wrapper 查找、`put` 时再新建一个固化 wrapper 存储，语义等价但调用方写法干净得多。

5. **`writeDeletes` 迭代简化**：

   ```java
   // 旧
   for (CharSequenceWrapper path : sortedPaths()) {
     PeekableLongIterator positions = positionsByPath.get(path).getLongIterator();
     while (positions.hasNext()) {
       long position = positions.next();
       writer.write(positionDelete.set(path.get(), position, null /* no row */));
     }
   }
   // 新
   for (CharSequence path : sortedPaths()) {
     PeekableLongIterator positions = positionsByPath.get(path).getLongIterator();
     while (positions.hasNext()) {
       long position = positions.next();
       writer.write(positionDelete.set(path, position, null /* no row */));
     }
   }
   ```

   迭代变量类型从 `CharSequenceWrapper` 改为 `CharSequence`，`path.get()` 改为 `path`（`CharSequenceMap.keySet()` 返回的是 `CharSequenceSet`，元素直接是 `CharSequence`，无需再 unwrap）。`positionsByPath.get(path)` 仍按字符序列等价查找（`CharSequenceMap.get` 内部会包一层 wrapper），语义不变。

6. **`sortedPaths` 返回类型简化**：

   ```java
   // 旧
   private List<CharSequenceWrapper> sortedPaths() {
     List<CharSequenceWrapper> paths = Lists.newArrayList(positionsByPath.keySet());
     paths.sort(Comparators.charSequences());
     return paths;
   }
   // 新
   private List<CharSequence> sortedPaths() {
     List<CharSequence> paths = Lists.newArrayList(positionsByPath.keySet());
     paths.sort(Comparators.charSequences());
     return paths;
   }
   ```

   返回类型由 `List<CharSequenceWrapper>` 改为 `List<CharSequence>`，`Comparators.charSequences()` 是 Iceberg 自定义的针对 `CharSequence` 的字典序比较器，直接对 `CharSequence` 排序即可，无需 wrapper。

## 小结

本次提交是 #9047 引入 `CharSequenceMap` 工具类后的跟进应用，把 `SortingPositionOnlyDeleteWriter` 中"手写 `Map<CharSequenceWrapper, V>` + 可复用 wrapper + 手工 get-or-create"的样板写法替换为 `CharSequenceMap.computeIfAbsent` 一行调用，同时把下游迭代与排序的元素类型从 `CharSequenceWrapper` 简化为 `CharSequence`。改动只涉及 1 个文件、净减 10 行（19 行删除、9 行新增），但消除了"两个 wrapper 角色"的微妙约定、降低了维护与认知成本，让乱序 position delete 写入路径的核心数据结构更贴近"按字符序列等价"这一抽象意图，是工具类落地后的典型清理式重构。
