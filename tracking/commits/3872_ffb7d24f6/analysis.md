# 提交分析：3872 - Data: Skip equality-delete filter when there are no equality deletes

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 3872 |
| 短哈希 | ffb7d24f6 |
| 完整哈希 | ffb7d24f6f4e5fa06aeccc06c7590af1d206455c |
| 日期 | 2026-06-13 21:47:09 -0700 |
| 作者 | Vova Kolmakov |
| 提交说明 | Data: Skip equality-delete filter when there are no equality deletes (#16742) |

## 总体目的

在 `DeleteFilter.applyEqDeletes` 方法中添加一个快速路径：当没有 equality delete 文件时，直接返回原始记录迭代器，跳过不必要的谓词构建和应用开销。

### 背景

在 Iceberg 的 merge-on-read 读取路径中，`DeleteFilter` 负责将 position deletes 和 equality deletes 应用到数据记录上。当表中只有 position deletes（或 deletion vectors）而没有 equality deletes 时，`applyEqDeletes` 方法仍然会构建一个空的谓词链并创建一个 `DeleteIterable` 来包装记录迭代器。这引入了不必要的每行处理开销——每条记录都需要经过谓词评估（虽然始终返回 false），以及额外的迭代器包装层。

## 修改详情

### 1. 修改 `DeleteFilter.java`

**文件路径**: `data/src/main/java/org/apache/iceberg/data/DeleteFilter.java`

在 `applyEqDeletes` 方法开头添加了空集合检查：

```java
private CloseableIterable<T> applyEqDeletes(CloseableIterable<T> records) {
    if (eqDeletes.isEmpty()) {
      return records;
    }

    Predicate<T> isEqDeleted = applyEqDeletes().stream().reduce(Predicate::or).orElse(t -> false);

    return createDeleteIterable(records, isEqDeleted);
  }
```

**工作逻辑**：
- `eqDeletes` 是 equality delete 文件列表
- 当该列表为空时，直接返回原始的 `records` 迭代器，跳过谓词构建和 `DeleteIterable` 包装
- 当列表非空时，执行原有的谓词链构建逻辑

这个优化消除了无 equality deletes 场景下的以下开销：
1. `applyEqDeletes()` 方法的调用（读取 delete 文件并构建谓词）
2. 谓词的 `stream().reduce(Predicate::or)` 操作
3. 每条记录的谓词评估
4. `DeleteIterable` 迭代器包装层的开销

### 2. 新增 JMH 基准测试 `DeleteFilterBenchmark.java`

**文件路径**: `data/src/jmh/java/org/apache/iceberg/data/DeleteFilterBenchmark.java`

新增了一个完整的 JMH 基准测试类，用于测量 `DeleteFilter` 在不同删除类型下的性能：

**测试参数**:
- `deleteType`: `NONE`（无删除）、`POSITION`（position deletes，5% 删除率）、`EQUALITY`（equality deletes，5% 删除率）
- 数据量: 250 万行

**测试场景**:
- `scan`: 端到端读取（Parquet 解码 + delete 过滤），通过 `IcebergGenerics.read(table)` 
- `filterOnly`: 仅 delete 过滤应用到预物化的内存记录上，隔离文件解码开销

**基准测试配置**:
- `@Fork(1)`, `@Warmup(iterations=5)`, `@Measurement(iterations=15)`
- `@BenchmarkMode(Mode.SingleShotTime)`, `@OutputTimeUnit(TimeUnit.MILLISECONDS)`

该基准测试的设计目的是量化此次优化的效果：在 `NONE` 和 `POSITION` 场景下（没有 equality deletes），对比优化前后的 `filterOnly` 耗时差异。

## 总结

此提交通过在 `applyEqDeletes` 方法中添加空集合快速路径，避免了无 equality deletes 时不必要的谓词构建和迭代器包装开销。这是一个典型的"快速路径"优化，在常见场景（只有 position deletes / DV 的表）下可以减少每行处理开销。同时附带了一个全面的 JMH 基准测试来量化优化效果。
