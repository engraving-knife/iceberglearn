# 提交 3893：Spark: Spark tests cache rewrite input (#16740)

## 提交信息

- **序号**：3893 / 4088
- **哈希**：e5151f35a09e05fcbc846fb6f1f76627b8313740
- **短哈希**：e5151f35a
- **日期**：2026-06-17 18:45:42 +0200
- **作者**：Sebastian Baunsgaard
- **提交说明**：Spark: Spark tests cache rewrite input (#16740)
- **PR/Issue**：#16740

## 总体目的

优化 `TestRewriteDataFilesAction` 测试套件的执行速度。该测试在每次测试前通过 Spark 写入大量（SCALE=400,000 行）输入数据到表中，然后才执行被测试的 rewrite 操作。由于许多测试使用相同的输入数据形状（相同的 format version、spec、文件数、行数、分区数、属性），重复的 Spark 写入成为测试的主要瓶颈。

通过缓存已写入的输入数据文件（按表形状键控），在同一个 JVM fork 内复用，使 Spark 写入只执行一次而非每个测试执行一次。被测试的 rewrite 操作仍然在每个测试的独立表上运行，且由于数据生成是确定性的（固定 RNG 种子），复用的数据与重新生成的完全一致。

## 如何达成设计目的

在 Spark 3.5、4.0、4.1 三个版本的 `TestRewriteDataFilesAction.java` 中引入静态输入文件缓存机制：
1. 使用静态 `@TempDir` 存储缓存的输入文件（生命周期跨越整个测试类）
2. 按表形状构建缓存键（包含 formatVersion、spec、属性、文件数、行数、分区数等）
3. 使用 per-key 锁实现双重检查的缓存模式，避免并发重复构建
4. 缓存命中时将 DataFiles 重新追加到新的测试表中
5. 在 `@AfterAll` 中清理缓存，防止 IDE 重运行时引用已删除的临时目录

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+127/-4 lines)
### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+127/-4 lines)
### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+127/-4 lines)

**修改目的**：实现输入数据文件缓存机制。

**工作逻辑**：

1. **缓存基础设施**：
```java
@TempDir private static Path inputCacheDir;
private static final Map<String, List<DataFile>> INPUT_FILE_CACHE = Maps.newConcurrentMap();
private static final Map<String, Object> INPUT_CACHE_LOCKS = Maps.newConcurrentMap();
private static final AtomicInteger INPUT_CACHE_SEQ = new AtomicInteger();
```
使用 `Maps.newConcurrentMap()` 而非 `new ConcurrentHashMap<>()` 以符合 Iceberg 的 checkstyle 规则。

2. **缓存键构建**：
```java
// 非分区表
String key = String.format(
    "unpartitioned|fv=%d|rowGroup=%d|files=%d|rows=%d",
    formatVersion, INPUT_PARQUET_ROW_GROUP_SIZE_BYTES, files, SCALE);

// 分区表
String key = String.format(
    "partitioned|fv=%d|spec=%s|opts=%s|files=%d|rows=%d|partitions=%d",
    formatVersion, spec, new TreeMap<>(options), files, numRecords, partitions);
```
使用 `String.format` 替代字符串拼接，缓存键显式编码 formatVersion、spec、属性等，确保不同表形状不会碰撞。分区表的 options 使用 `TreeMap` 排序确保键的确定性。

3. **per-key 锁的双重检查缓存**：
```java
private List<DataFile> cachedInputFiles(String key, Supplier<Table> goldenBuilder) {
  List<DataFile> cached = INPUT_FILE_CACHE.get(key);
  if (cached != null) return cached;
  Object lock = INPUT_CACHE_LOCKS.computeIfAbsent(key, ignored -> new Object());
  synchronized (lock) {
    List<DataFile> existing = INPUT_FILE_CACHE.get(key);
    if (existing != null) return existing;
    // 重量级 Spark 写入在锁内但锁外构建（distinct shapes 可并行）
    Table golden = goldenBuilder.get();
    List<DataFile> files = scanFiles(golden);
    INPUT_FILE_CACHE.put(key, ImmutableList.copyOf(files));
    return files;
  }
}
```

4. **资源管理**：
- 使用 try-with-resources 关闭 `planFiles()` 返回的 CloseableIterable，避免 manifest reader 泄漏
- 缓存的 DataFiles 收集为不可变列表，防止调用者修改共享缓存实例

5. **生命周期管理**：
```java
@AfterAll
public static void clearInputFileCache() {
  INPUT_FILE_CACHE.clear();
  INPUT_CACHE_LOCKS.clear();
  INPUT_CACHE_SEQ.set(0);
}
```
防止 IDE 重运行（forkCount=0）时返回指向已删除 @TempDir 的 DataFiles。

6. **孤儿文件说明**：
```java
// Cached input files live under the static inputCacheDir, outside table.location(), so
// deleteOrphanFiles (which only scans the table prefix) never sees them by design.
```
注释说明缓存输入文件有意位于表目录之外，不在 deleteOrphanFiles 扫描范围内。

## 总结

通过引入按表形状键控的静态输入文件缓存，将 `TestRewriteDataFilesAction` 中 400,000 行数据的 Spark 写入从每测试一次减少到每 JVM fork 一次，显著加速测试套件。实现中考虑了并发安全（per-key 锁）、资源管理（关闭扫描迭代器）、缓存键健壮性（显式编码所有形状参数）和生命周期管理（@AfterAll 清理）等多个方面，是一个经过多轮 review 打磨的测试基础设施优化。
