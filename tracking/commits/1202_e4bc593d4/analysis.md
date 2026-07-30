# 提交 1202：Core: Add DataFileSet / DeleteFileSet (#11195)

## 提交信息

- **序号**：1202 / 4088
- **哈希**：e4bc593d48df08c66549536266ca960024642295
- **短哈希**：e4bc593d4
- **日期**：2024-10-01（Tue Oct 1 08:14:34 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Add DataFileSet / DeleteFileSet (#11195)
- **PR/Issue**：#11195

## 总体目的

Iceberg 在 `org.apache.iceberg.util` 下已有 `CharSequenceSet`、`StructLikeSet` 等基于 `WrapperSet` 抽象的专用 Set 实现，用于把"非天然 equals/hashCode 的对象"装进 `Set` 并按自定义逻辑去重。但 `DataFile` 和 `DeleteFile` 一直缺少这样的专用集合——业务里要按"文件路径（location）"对数据文件/删除文件去重时，通常只能借助 `Map<String, DataFile>` 之类的间接手段，既不直观也容易出错。

本提交补齐这一缺口，新增 `DataFileSet` 与 `DeleteFileSet` 两个集合：

1. 均继承自新增的抽象基类 `WrapperSet<T>`，按 `location()` 字段做相等性判定和去重；
2. 保持插入顺序（基于 `LinkedHashSet`），不允许 `null` 元素；
3. 实现 `java.util.Set` 接口且 `Serializable`，可直接用于需要序列化的提交/调度路径。

为后续在 commit/写入链路中精确去重数据文件与删除文件提供基础设施。

## 如何达成设计目的

通过三层结构实现：

1. **抽象基类 `WrapperSet<T>`**：泛化"包装型 Set"的通用逻辑——内部持有一个 `Set<Wrapper<T>>`（`LinkedHashSet`），实现 `Set<T>` 的全部方法（`add/contains/remove/iterator/equals/hashCode` 等），其中 `contains/remove` 借助 `ThreadLocal` 复用的 `Wrapper` 实例避免频繁创建对象；`add` 通过子类提供的 `wrap(T)` 创建新 `Wrapper` 入集合；`equals`/`hashCode` 严格遵循 `Set` 契约。
2. **`DataFileSet` / `DeleteFileSet`**：各自实现 `WrapperSet<DataFile>` / `WrapperSet<DeleteFile>`，并提供静态工厂 `create()` 与 `of(Iterable)`；内部 `DataFileWrapper` / `DeleteFileWrapper` 以 `file.location()` 作为 `equals/hashCode` 的判据。
3. **配套单测** `TestDataFileSet` / `TestDeleteFileSet`：覆盖空集、插入顺序、`add/contains/containsAll/remove/removeAll/retainAll/toArray/clear`、`equals/hashCode`、Kryo 与 Java 原生序列化往返等用例。

`DeleteFileWrapper.equals` 中带注释 "this needs to be updated once deletion vector support is added"，说明当前以 location 为键，未来引入 deletion vector（DV）后需调整。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/WrapperSet.java`（新增，177 行）

**修改目的**：抽出"包装型 Set"的通用骨架。

**工作逻辑**：

- `abstract class WrapperSet<T> implements Set<T>, Serializable`，内部 `private final Set<Wrapper<T>> set = Sets.newLinkedHashSet()`；
- 抽象方法：`wrapper()`（返回可复用的 `Wrapper`，用于 `contains/remove` 探测）、`wrap(T)`（创建新 `Wrapper` 入集合）、`elementClass()`（用于把传入的 `Object` cast 回 `T`）；
- 内部接口 `Wrapper<T> extends Serializable`：`T get()` / `Wrapper<T> set(T object)`，可变复用；
- `add(T)`：`Preconditions.checkNotNull` 后 `set.add(wrap(obj))`；
- `contains(Object)`/`remove(Object)`：取 `ThreadLocal` 复用 wrapper，`set` 上对应值后调用 `set.contains`/`set.remove`，再 `wrapper.set(null)` 释放引用，避免内存泄漏；
- `iterator()`：`Iterators.transform(set.iterator(), Wrapper::get)` 还原为 `T`；
- `equals`：与任意 `Set` 比较，先比 size 再 `containsAll`，捕获 `ClassCastException`/`NullPointerException` 返回 `false`，符合 `Set.equals` 契约；
- `hashCode`：`set.stream().mapToInt(Object::hashCode).sum()`，符合 `Set.hashCode` 契约（元素 hashCode 之和）。

### `api/src/main/java/org/apache/iceberg/util/DataFileSet.java`（新增，113 行）

**修改目的**：按 `location` 去重的数据文件集合。

**工作逻辑**：

- `public class DataFileSet extends WrapperSet<DataFile>`；
- `private static final ThreadLocal<DataFileWrapper> WRAPPERS = ThreadLocal.withInitial(...)`，供 `contains/remove` 复用；
- 私有无参构造（注释 "needed for serialization/deserialization"）+ 私有 `Iterable<Wrapper<DataFile>>` 构造；
- 工厂：`create()` 返回空集；`of(Iterable<? extends DataFile>)` 用 `Iterables.transform` 把每个元素包成 `DataFileWrapper`，`Preconditions.checkNotNull` 防御 null；
- 实现三个抽象方法：`wrapper()` 取 ThreadLocal、`wrap(DataFile)` 新建 wrapper、`elementClass()` 返回 `DataFile.class`；
- 内部 `DataFileWrapper implements Wrapper<DataFile>`：`get/set` 持有可变 `file` 字段；`equals` 仅比较 `file.location()`；`hashCode` 用 `Objects.hashCode(file.location())`；`toString` 返回 `file.location()`。

### `api/src/main/java/org/apache/iceberg/util/DeleteFileSet.java`（新增，114 行）

**修改目的**：按 `location` 去重的删除文件集合。

**工作逻辑**：与 `DataFileSet` 镜像，差异仅在元素类型为 `DeleteFile`、内部 wrapper 为 `DeleteFileWrapper`，并在 `equals` 处加注释：当前按 location 比较，待 deletion vector 支持落地后需更新。

### `core/src/test/java/org/apache/iceberg/util/TestDataFileSet.java`（新增，303 行）

**修改目的**：`DataFileSet` 单测，因 `DataFiles.builder` 在 core 模块所以放在 core 测试目录。

**工作逻辑**：构造 4 个不同路径的 `DataFile`（FILE_A/B/C/D），覆盖：
- `emptySet`：空集断言；
- `insertionOrderIsMaintained`：`addAll(D,A,C)` 后 `add(B)`、再 `add(D)`（重复被忽略），断言顺序 `[D, A, C, B]`；
- `clear` / `addAll` / `contains` / `containsAll` / `toArray` / `retainAll` / `remove` / `removeAll`：正常行为 + null 校验（抛 `NullPointerException("Invalid object: null")`）；
- `equalsAndHashCode`：用相同路径但不同 `fileSizeInBytes/recordCount` 的 `DataFile` 验证"按 location 相等"语义，并与 `Collections.unmodifiableSet` 包装后的视图比较；
- `kryoSerialization` / `javaSerialization`：往返序列化后仍 `equals`。

### `core/src/test/java/org/apache/iceberg/util/TestDeleteFileSet.java`（新增，321 行）

**修改目的**：`DeleteFileSet` 单测。

**工作逻辑**：与 `TestDataFileSet` 镜像，使用 `FileMetadata.deleteFileBuilder(...).ofPositionDeletes()` 构造 4 个删除文件（FILE_A_DELETES 等），测试用例结构与 `TestDataFileSet` 一致。

## 小结

- **成效**：补齐 `util` 包下数据文件/删除文件专用 Set 实现，提供按 `location` 去重、保序、可序列化、不允许 null 的集合，后续 commit/写入链路可直接复用，避免手写 `Map<String, DataFile>` 的间接去重模式。
- **影响范围**：仅 `api` 模块新增 3 个生产类、`core` 模块新增 2 个测试类，无对现有代码的修改，纯新增，向后兼容。注意 `WrapperSet` 为包级私有（`abstract class`，无 `public`），仅作为 `DataFileSet`/`DeleteFileSet` 的内部骨架。
- **回迁到 1.4.x 的注意事项**：此为纯新增基础设施类，不修改任何既有行为，回迁安全。需注意 1.4.x 分支上的 `api`/`core` 模块包路径与 main 一致（`org.apache.iceberg.util`），可直接 cherry-pick；若 1.4.x 已有自定义去重逻辑想替换为 `DataFileSet`，应同步审视调用方是否依赖原有相等语义。同时留意 `DeleteFileWrapper.equals` 中"deletion vector 支持落地后需更新"的注释——1.4.x 若已合入 DV 相关改动，需重新评估 location 唯一性是否仍然成立。
