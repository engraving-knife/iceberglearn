# 提交 0894：API, Flink, ORC: Fix implicit `long` casting issues (#10580)

## 提交信息

- **序号**：0894 / 4088
- **哈希**：3825477b0175d0e24960c89815c217f1ff8d2563
- **短哈希**：3825477b0
- **日期**：2024-07-03（Thu Jul 4 01:36:49 2024 +0200）
- **作者**：Robert Stupp
- **提交说明**：API, Flink, ORC: Fix implicit `long` casting issues (#10580)
- **PR/Issue**：#10580

## 总体目的

Java 的算术运算有一个容易踩坑的规则：当 `int` 与 `int` 做运算时，结果仍然是 `int`，只有在赋值给 `long` 或与 `long` 运算时才会提升为 `long`。这意味着 `int * int + int` 在赋值给 `long` 之前，所有运算都在 `int` 域进行，一旦中间结果超出 `Integer.MAX_VALUE`（约 21 亿），就会发生静默溢出，得到错误的负数或截断值，再赋给 `long` 时已经晚了。

本提交修复 Iceberg 中多处此类"隐式 long 转换"问题——即在算术表达式中，被赋值目标或目标参数类型是 `long`，但中间计算却以 `int` 进行，存在溢出风险。这类问题在 IntelliJ 的 "Implicit narrowing conversion in compound assignment / Implicit long cast" 检查下会暴露，但在功能测试中往往因为数据量小而无法触发。

## 如何达成设计目的

逐处审视 IntelliJ 报告，对存在隐式 `long` 转换风险的表达式显式添加 `L` 后缀或 `(long)` 强制转换，确保整个算术链路在 `long` 域进行：

- 对 `int * int + int` 形式，把其中一个操作数显式转为 `long`，整个表达式就会提升为 `long`；
- 对 `Math.floorMod(int, int) * int` 形式，把字面量 `1000` 改为 `1000L`，让乘法在 `long` 域进行；
- 对 `histogram.update(int * int + int)` 这种调用 `update(long)` 的地方，把首个操作数 `(long)` 转换。

## 修改详情

### `api/src/test/java/org/apache/iceberg/metrics/TestFixedReservoirHistogram.java`

**修改目的**：修复多线程直方图测试中 `threadIndex * samplesPerThread + i` 的隐式 int 溢出。

**工作逻辑**：原代码：

```java
histogram.update(threadIndex * samplesPerThread + i);
```

`threadIndex`、`samplesPerThread`、`i` 都是 `int`，乘法和加法都在 `int` 域进行。当 `threadIndex * samplesPerThread` 超过 `Integer.MAX_VALUE`（约 21 亿）时会发生静默溢出，得到错误的负数或截断值，再传给 `update(long)` 时已经错了。修改为：

```java
histogram.update((long) threadIndex * samplesPerThread + i);
```

把首个操作数转为 `long`，整个表达式就会提升为 `long` 运算，避免溢出。虽然在测试场景里 `threadIndex * samplesPerThread` 不太可能超过 21 亿，但保持代码正确性仍是必要的。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java`

**修改目的**：修复 Parquet 时间戳读取中 `Math.floorMod(value, 1000_000) * 1000` 的隐式 int 溢出，该值会传给 `Instant.ofEpochSecond(long, long)` 的第二个参数（纳秒偏移）。

**工作逻辑**：原代码（两处，分别对应 `TIMESTAMP_WITHOUT_TIME` 和 `TIMESTAMP_WITH_LOCAL_TIME_ZONE`）：

```java
Instant.ofEpochSecond(
    Math.floorDiv(value, 1000_000), Math.floorMod(value, 1000_000) * 1000)
```

`Math.floorMod(long, int)` 返回 `int`，再 `* 1000` 仍在 `int` 域。虽然 `floorMod(value, 1000_000)` 的结果在 0 到 999999 之间，乘以 1000 后最大是 999999000，仍在 `int` 范围内（21 亿以内），所以**实际不会溢出**，但 IntelliJ 仍会报警。修改为：

```java
Math.floorMod(value, 1000_000) * 1000L
```

把字面量改为 `1000L`，让乘法在 `long` 域进行，消除告警并提高未来代码变更时的健壮性。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/assigner/TestWatermarkBasedSplitAssigner.java`

**修改目的**：修复测试中 `splitNum * filesPerSplit + fileNum` 的隐式 int 溢出。

**工作逻辑**：原代码：

```java
RandomGenericData.generate(SCHEMA, 2, splitNum * filesPerSplit + fileNum)
```

`generate` 的第三个参数是 `long`（seed），但 `splitNum * filesPerSplit + fileNum` 全在 `int` 域。修改为：

```java
RandomGenericData.generate(SCHEMA, 2, (long) splitNum * filesPerSplit + fileNum)
```

把首个操作数转为 `long`，整个表达式提升为 `long`，避免在大规模测试场景下 seed 溢出导致不同 split 拿到相同的随机种子。

### `orc/src/main/java/org/apache/iceberg/orc/ExpressionToSearchArgument.java`

**修改目的**：修复 ORC 表达式转换中 `Math.floorMod(microsFromEpoch, 1_000_000) * 1_000` 的隐式 int 溢出。

**工作逻辑**：原代码：

```java
Instant.ofEpochSecond(
    Math.floorDiv(microsFromEpoch, 1_000_000),
    Math.floorMod(microsFromEpoch, 1_000_000) * 1_000))
```

`Math.floorMod(long, int)` 返回 `int`，再 `* 1_000` 仍在 `int` 域。`floorMod` 结果在 0 到 999999 之间，乘以 1000 后最大是 999999000，不会溢出 `int`，但 IntelliJ 仍报警。修改为：

```java
Math.floorMod(microsFromEpoch, 1_000_000) * 1_000L
```

把字面量改为 `1_000L`，让乘法在 `long` 域进行。注意：Flink 与 ORC 这两处改动的本质是消除 IntelliJ 告警，从数值范围看实际溢出风险较低（纳秒分量最大约 10 亿，远小于 21 亿），但保持代码语义清晰、防止未来参数变化（比如单位从微秒改为纳秒）引入真实 bug 仍有价值。

## 小结

- **成效**：修复了 4 处 IntelliJ 报告的隐式 long 转换问题，把算术运算从 `int` 域提升到 `long` 域，消除了潜在的整数溢出风险和静态检查告警。其中 `TestFixedReservoirHistogram` 与 `TestWatermarkBasedSplitAssigner` 的改动属于真实的潜在溢出修复；Flink/ORC 两处改动属于防御性修复（实际数值范围内不会溢出，但保持代码清晰）。
- **影响范围**：涉及 4 个文件，5 行新增、5 行删除，覆盖 api 测试、flink 1.19 main 与 test、orc main 模块。所有改动都是行为正确性提升，不改变 API。
- **回迁到 1.4.x 的注意事项**：可以安全回迁。1.4.x 分支对应的 Flink 版本是 1.17/1.18/1.19，ORC 模块文件路径相同。需要按 Flink 版本号 cherry-pick 对应文件：Flink 1.19 的改动可直接 cherry-pick；Flink 1.17/1.18 的对应文件改动在另一个提交 #10629（即本批 0897 号提交）中单独覆盖。建议把 0893 + 0897 一并回迁以覆盖所有 Flink 版本。ORC 模块改动可直接 cherry-pick。无破坏性，无风险。
