# 提交 1645：Spark 3.5: Make ColumnVectorWithFilter generic and refactor batch load (#12056)

## 提交信息

- **序号**：1645 / 4088
- **哈希**：2fa6cd855b4e43b383df7b72776eb554b60d8c06
- **短哈希**：2fa6cd855
- **日期**：2025-01-27（Mon Jan 27 07:25:15 2025 -0800）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Spark 3.5: Make ColumnVectorWithFilter generic and refactor batch load
- **PR/Issue**：#12056

## 总体目的

Spark 3.5 向量化读取路径中，`ColumnVectorWithFilter` 用于在批次内应用"行 ID 映射"（`rowIdMapping`）跳过被删除的行：当 `ColumnarBatchReader` 检测到批次有删除（position/equality deletes）时，会构造一个 `rowIdMapping` 数组，把"逻辑行号"映射到"物理行号"，再用 `ColumnVectorWithFilter` 包装每个列向量，让 Spark 通过映射后的物理行号读取数据。

但此前的实现有一个设计缺陷：`ColumnVectorWithFilter extends IcebergArrowColumnVector`，构造时接收 `VectorHolder`（Arrow 向量持有者），强耦合到 Arrow 后端。后果是：

1. **覆盖面不全**：`ColumnarBatchReader.loadDataToColumnBatch` 在包装时只处理 `instanceof IcebergArrowColumnVector` 的列，其他类型的列（如 `DeletedColumnVector` 即 `_deleted` 元数据列、常量列、未来可能的非 Arrow 列）不会被包装。当一个批次同时有删除和 `_deleted` 列时，`_deleted` 列不会被映射，Spark 看到的 `_deleted` 行号与数据列行号错位，结果错误。
2. **重复字段冗余**：`IcebergArrowColumnVector` 内部为支持 `ColumnVectorWithFilter` 的旧构造方式，额外保存了 `VectorHolder holder` 字段并暴露 `public VectorHolder vector()` 方法——这只是为了给 `ColumnVectorWithFilter` 提取底层 holder 用，不属于该类的核心职责。
3. **getter 中冗余 null 检查**：`ColumnVectorWithFilter` 的 `getArray`/`getDecimal`/`getUTF8String`/`getBinary` 在调底层 accessor 前先 `if (isNullAt(rowId)) return null;`，但 `isNullAt` 自己已经走了一次 `rowIdMapping` 查找，且底层 accessor 本身能正确处理 null 行——多一次查找既慢又在某些边界下与底层 null 行为不一致。

本提交把 `ColumnVectorWithFilter` 重写为"通用的 `ColumnVector` 装饰器"：

- 改为 `extends ColumnVector`（Spark 基类），持有一个 `ColumnVector delegate`，可包装**任何** `ColumnVector` 子类，不再依赖 Arrow；
- 构造器接收 `ColumnVector` 而非 `VectorHolder`；
- 新增 `getChild(int ordinal)` 递归包装 struct 子字段（带双重检查锁懒加载），支持嵌套 struct 类型的过滤；
- 删除 getter 中的 `isNullAt` 短路，统一委托给 `delegate`，由 delegate 自身处理 null；
- `ColumnarBatchReader` 改为无条件把每个列向量包成 `ColumnVectorWithFilter`，不再判 `instanceof IcebergArrowColumnVector`，从而覆盖 `DeletedColumnVector` 等所有列类型；
- `IcebergArrowColumnVector` 删除多余的 `holder` 字段与 `vector()` 方法。

## 如何达成设计目的

通过"装饰器模式 + 委托"重构：

1. `ColumnVectorWithFilter` 不再继承 `IcebergArrowColumnVector`，改为直接继承 Spark 的 `ColumnVector`，构造时持有 `ColumnVector delegate` 与 `int[] rowIdMapping`；
2. 所有数据访问方法（`getBoolean`/`getInt`/`getLong`/`getDouble`/`getDecimal`/`getUTF8String`/`getBinary`/`getArray`/`getMap`/`isNullAt`）统一改为 `return delegate.xxx(rowIdMapping[rowId])`；
3. 新增 `hasNull`/`numNulls`/`close`/`closeIfFreeable`/`getChild` 等方法委托给 delegate 或在 `getChild` 中递归包装；
4. `ColumnarBatchReader` 调用点改为 `vectors[i] = new ColumnVectorWithFilter(vectors[i], rowIdMapping)`，不再做类型判断与 holder 提取；
5. `IcebergArrowColumnVector` 移除 `holder` 字段与 `vector()` 方法。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnVectorWithFilter.java`（修改，+96/-32）

**修改目的**：把 `ColumnVectorWithFilter` 改造为通用 `ColumnVector` 装饰器。

**工作逻辑**：

- 类签名：`public class ColumnVectorWithFilter extends ColumnVector`（原为 `extends IcebergArrowColumnVector`）；
- 字段：`private final ColumnVector delegate;` + `private final int[] rowIdMapping;` + `private volatile ColumnVectorWithFilter[] children = null;`；
- 构造器：
  ```
  public ColumnVectorWithFilter(ColumnVector delegate, int[] rowIdMapping) {
    super(delegate.dataType());
    this.delegate = delegate;
    this.rowIdMapping = rowIdMapping;
  }
  ```
  注意调 `super(delegate.dataType())` 以正确设置 Spark `ColumnVector` 的 `dataType` 字段；
- 新增方法：
  - `close()` → `delegate.close()`；
  - `closeIfFreeable()` → `delegate.closeIfFreeable()`；
  - `hasNull()` → `delegate.hasNull()`；
  - `numNulls()` → `delegate.numNulls()`，注释说明"计算 rowIdMapping 下的实际 null 数代价大，返回原向量的 null 数（高估）可接受"；
  - `getByte`/`getShort`/`getMap`：之前因继承 `IcebergArrowColumnVector` 而隐式支持，现在显式委托（`IcebergArrowColumnVector` 之前可能未实现这些，新实现统一委托避免漏接口）；
- 已有 getter 改造（统一 `delegate.xxx(rowIdMapping[rowId])`）：
  - `isNullAt` → `delegate.isNullAt(rowIdMapping[rowId])`（原为 `nullabilityHolder().isNullAt(rowIdMapping[rowId]) == 1`）；
  - `getBoolean`/`getInt`/`getLong`/`getFloat`/`getDouble`/`getDecimal`/`getUTF8String`/`getBinary`/`getArray` 同理；
  - 删除 `getArray`/`getDecimal`/`getUTF8String`/`getBinary` 中的 `if (isNullAt(rowId)) return null;` 短路——让 delegate 自己处理 null（delegate 的 accessor 在 null 行上会返回对应类型的 null/默认值，与 Spark `ColumnVector` 契约一致）；
- `getChild(int ordinal)`：新增，递归包装 struct 子字段，带双重检查锁懒加载：
  ```
  if (children == null) {
    synchronized (this) {
      if (children == null) {
        if (dataType() instanceof StructType) {
          StructType structType = (StructType) dataType();
          this.children = new ColumnVectorWithFilter[structType.length()];
          for (int index = 0; index < structType.length(); index++) {
            children[index] = new ColumnVectorWithFilter(delegate.getChild(index), rowIdMapping);
          }
        } else {
          throw new UnsupportedOperationException("Unsupported nested type: " + dataType());
        }
      }
    }
  }
  return children[ordinal];
  ```
  这是原来继承自 `IcebergArrowColumnVector` 的 `getChild` 行为的等价替代，但用 `ColumnVectorWithFilter` 递归包装子字段，保证 struct 嵌套字段也走 rowIdMapping。`volatile` + 双重检查保证线程安全与单次初始化。

类级 Javadoc 新增："A column vector implementation that applies row-level filtering. ... translates the provided row index using the mapping array, effectively filtering the original data to only expose the live subset of rows."

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java`（修改，+21/-19）

**修改目的**：让 `loadDataToColumnBatch` 无条件包装所有列向量，并抽取小工具方法提升可读性。

**工作逻辑**：

```
ColumnarBatch loadDataToColumnBatch() {
  ColumnVector[] vectors = readDataToColumnVectors();
  int numLiveRows = batchSize;

  if (hasIsDeletedColumn) {
    boolean[] isDeleted = buildIsDeleted(vectors);
    for (ColumnVector vector : vectors) {
      if (vector instanceof DeletedColumnVector) {
        ((DeletedColumnVector) vector).setValue(isDeleted);
      }
    }
  } else {
    Pair<int[], Integer> pair = buildRowIdMapping(vectors);
    if (pair != null) {
      int[] rowIdMapping = pair.first();
      numLiveRows = pair.second();
      for (int i = 0; i < vectors.length; i++) {
        vectors[i] = new ColumnVectorWithFilter(vectors[i], rowIdMapping);
      }
    }
  }

  if (deletes != null && deletes.hasEqDeletes()) {
    vectors = ColumnarBatchUtil.removeExtraColumns(deletes, vectors);
  }

  ColumnarBatch batch = new ColumnarBatch(vectors);
  batch.setNumRows(numLiveRows);
  return batch;
}

private boolean[] buildIsDeleted(ColumnVector[] vectors) {
  return ColumnarBatchUtil.buildIsDeleted(vectors, deletes, rowStartPosInBatch, batchSize);
}

private Pair<int[], Integer> buildRowIdMapping(ColumnVector[] vectors) {
  return ColumnarBatchUtil.buildRowIdMapping(vectors, deletes, rowStartPosInBatch, batchSize);
}
```

关键变化：

- 变量名 `arrowColumnVectors` → `vectors`（不再特化 Arrow）；
- `hasIsDeletedColumn` 分支：用增强 for 替代索引 for；`buildIsDeleted(vectors)` 抽成私有方法；
- `else` 分支（行 ID 映射）：原代码 `if (vector instanceof IcebergArrowColumnVector) { arrowColumnVectors[i] = new ColumnVectorWithFilter(((IcebergArrowColumnVector) vector).vector(), rowIdMapping); }` 改为 `vectors[i] = new ColumnVectorWithFilter(vectors[i], rowIdMapping);` —— 无类型判断、无 holder 提取，**所有列**（含 `DeletedColumnVector`、常量列、未来其他类型）都被包装，保证整批行号一致；
- `buildRowIdMapping(vectors)` 同样抽成私有方法；
- 末尾构造 `ColumnarBatch` 用 `batch` 变量名，更简洁。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/IcebergArrowColumnVector.java`（修改，+0/-6）

**修改目的**：移除为支持旧 `ColumnVectorWithFilter` 而存在的冗余字段与方法。

**工作逻辑**：

- 删除字段 `private final VectorHolder holder;`；
- 删除构造器中的 `this.holder = holder;` 赋值；
- 删除方法 `public VectorHolder vector() { return holder; }`。

`IcebergArrowColumnVector` 现在只保留 `accessor` 与 `nullabilityHolder` 两个字段，职责更纯粹。`vector()` 方法被移除后，任何外部调用方（如旧 `ColumnarBatchReader`）若依赖该方法会被编译错误暴露——本提交已同步修改 `ColumnarBatchReader`，无遗留调用。

## 小结

- **成效**：把 `ColumnVectorWithFilter` 从"Arrow 专用子类"重构为"通用 `ColumnVector` 装饰器"，使批次内所有列（含 `_deleted` 元数据列、常量列、嵌套 struct 子字段）都能被行 ID 映射一致过滤，修复了"非 Arrow 列在删除批次中行号错位"的潜在正确性问题；同时去除了 getter 中冗余的 `isNullAt` 短路（少一次映射查找），并清理了 `IcebergArrowColumnVector` 上为支持旧设计而存在的 `holder` 字段与 `vector()` 方法，让该类职责更清晰。
- **影响范围**：仅 Spark 3.5 模块的 3 个向量化读取类。无公共 API 变化（`ColumnVectorWithFilter` 构造器签名变了，但该类是包级 public，仅 Iceberg 内部使用）。行为变化：删除批次中所有列现在都被一致映射，原来 `DeletedColumnVector` 在有 `rowIdMapping` 时不会被包装（但 `DeletedColumnVector` 走的是 `hasIsDeletedColumn` 分支，不走 `rowIdMapping` 分支，所以实际影响的是"同时有 equality deletes + 非 Arrow 列"等边缘场景）；getter 不再做 null 短路，但 delegate 自身处理 null，结果等价。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支的 Spark 3.5 模块若 `ColumnVectorWithFilter` 仍是 `extends IcebergArrowColumnVector` + `VectorHolder` 构造，应回迁此重构；
  - 回迁时三处文件需一起改：`ColumnVectorWithFilter`（重写）、`ColumnarBatchReader`（调用点）、`IcebergArrowColumnVector`（删 holder/vector()）；若只回迁一处会编译失败或行为不一致；
  - 1.4.x 上若有其他地方调用 `IcebergArrowColumnVector.vector()`（如自定义向量化 reader），回迁后需调整；
  - `getChild` 的双重检查锁依赖 `volatile` 语义，JVM 上正确；1.4.x 上无需额外同步；
  - 测试：建议回迁后跑 `TestVectorizedReads`/`TestPositionDeletes`/`TestEqualityDeletes` 等向量化读取测试，特别关注"有删除 + `_deleted` 列 + 嵌套 struct"组合场景；
  - 此重构与 #12058（提交 1627，Javadoc 修复）作用于同一文件 `ColumnarBatchUtil.java` 的邻居 `ColumnVectorWithFilter` 等，无冲突，可独立回迁。
