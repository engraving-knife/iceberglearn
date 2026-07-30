# 提交 0898：Flink: Fix `long` casting issues (#10629)

## 提交信息

- **序号**：0898 / 4088
- **哈希**：4aee3076171141bcbc03f381af84419f6afad54e
- **短哈希**：4aee30761
- **日期**：2024-07-04（Thu Jul 4 18:02:47 2024 +0200）
- **作者**：Robert Stupp <snazy@snazy.de>
- **提交说明**：Flink: Fix `long` casting issues (#10629)
- **PR/Issue**：#10629

## 总体目的

本提交是 0893 号提交（#10580，"API, Flink, ORC: Fix implicit `long` casting issues"）在 Flink 模块上的延伸与完善。0893 只修复了 `flink/v1.19` 中的两处 `FlinkParquetReaders` 时间戳读取问题（把 `* 1000` 改为 `* 1000L`），但留下了两个不足：

1. 0893 只覆盖了 `flink/v1.19`，没有同步修复 `flink/v1.17` 与 `flink/v1.18` 中同构的 `FlinkParquetReaders` 与 `TestWatermarkBasedSplitAssigner`；
2. 0893 只把乘法字面量改为 `L` 后缀，但 `Math.floorDiv(value, 1000_000)` 与 `Math.floorMod(value, 1000_000)` 中的除数仍是 `int` 字面量。Java 中 `Math.floorMod(long, int)` 的返回类型是 `int`，而 `Math.floorMod(long, long)` 的返回类型是 `long`——虽然在该场景下 `floorMod` 返回值范围 `[0, 999999]` 不会溢出 `int`，但保留 `int` 除数会触发 IDE 静态检查的"`int` 结果隐式加宽为 `long`"告警，且与乘数 `1000L` 的 `long` 域不一致；
3. 0893 遗漏了 `FlinkParquetReaders` 中 `read(Integer reuse)`（TIME 类型读取）方法的同类问题：`(int) Math.floorDiv(column.nextLong(), 1000)` 中除数 `1000` 是 `int`，虽然 `floorDiv(long, int)` 返回 `long`，但 IDE 仍会提示"建议用 `long` 除数"以保持一致性。

本提交的目的是把 Flink 三个版本目录（v1.17、v1.18、v1.19）中的所有 `long` casting 问题一次性修完，包括 `floorDiv`/`floorMod` 的除数也改为 `L` 后缀，并补充修复 `read(Integer reuse)` 方法。

## 如何达成设计目的

对每个 Flink 版本目录下的 `FlinkParquetReaders.java` 与 `TestWatermarkBasedSplitAssigner.java`，应用以下修改：

1. `FlinkParquetReaders` 中三处时间戳/时间读取：
   - 把 `Math.floorDiv(value, 1000_000)` 改为 `Math.floorDiv(value, 1000_000L)`（除数改 `long`）；
   - 把 `Math.floorMod(value, 1000_000)` 改为 `Math.floorMod(value, 1000_000L)`（除数改 `long`，返回类型也变为 `long`）；
   - 把 `* 1000` 改为 `* 1000L`（乘数改 `long`，0893 在 v1.19 已做）；
   - 把 `Math.floorDiv(column.nextLong(), 1000)` 改为 `Math.floorDiv(column.nextLong(), 1000L)`（新增修复，TIME 类型读取）。
2. `TestWatermarkBasedSplitAssigner` 中一处：把 `splitNum * filesPerSplit + fileNum` 改为 `(long) splitNum * filesPerSplit + fileNum`（0893 在 v1.19 已做，本提交补 v1.17/v1.18）。

这些修改确保所有涉及 `long` 值的 `floorDiv`/`floorMod`/乘法运算都在 `long` 域进行，消除 IDE 静态检查告警与潜在的整数溢出风险。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java`、`flink/v1.18/flink/.../FlinkParquetReaders.java`、`flink/v1.19/flink/.../FlinkParquetReaders.java`

**修改目的**：修复 Flink Parquet 读取器中时间戳与时间类型读取时的 `long` casting 问题，确保 `floorDiv`/`floorMod`/乘法都在 `long` 域进行。

**工作逻辑**：三个版本目录中的 `FlinkParquetReaders.java` 改动完全相同（v1.19 的起始状态因 0893 已改过乘数，所以 diff 略有不同，但最终状态一致）。涉及三处：

1. `TIMESTAMP WITHOUT TIME ZONE` 读取（`TimestampData.fromLocalDateTime` 路径，约 line 422）：

```diff
       long value = readLong();
       return TimestampData.fromLocalDateTime(
           Instant.ofEpochSecond(
-                  Math.floorDiv(value, 1000_000), Math.floorMod(value, 1000_000) * 1000)
+                  Math.floorDiv(value, 1000_000L), Math.floorMod(value, 1000_000L) * 1000L)
               .atOffset(ZoneOffset.UTC)
               .toLocalDateTime());
```

`value` 是从 Parquet 读出的微秒精度时间戳（`long`）。`floorDiv(value, 1000_000L)` 计算秒数，`floorMod(value, 1000_000L)` 计算剩余微秒（返回 `long`），再 `* 1000L` 转成纳秒。所有运算在 `long` 域进行，传给 `Instant.ofEpochSecond(long, long)`。

2. `TIMESTAMP WITH LOCAL TIME ZONE` 读取（`TimestampData.fromInstant` 路径，约 line 444）：

```diff
       long value = readLong();
       return TimestampData.fromInstant(
           Instant.ofEpochSecond(
-              Math.floorDiv(value, 1000_000), Math.floorMod(value, 1000_000) * 1000));
+              Math.floorDiv(value, 1000_000L), Math.floorMod(value, 1000_000L) * 1000L));
```

逻辑与上一处相同，区别在于结果用 `TimestampData.fromInstant` 而非 `fromLocalDateTime`。

3. `TIME` 类型读取（`read(Integer reuse)` 方法，约 line 517）——**本提交新增修复**：

```diff
       // Discard microseconds since Flink uses millisecond unit for TIME type.
-      return (int) Math.floorDiv(column.nextLong(), 1000);
+      return (int) Math.floorDiv(column.nextLong(), 1000L);
```

`column.nextLong()` 返回微秒精度的时间值（`long`），`floorDiv(..., 1000L)` 计算毫秒数，再 `(int)` 截断为 `int` 返回（Flink 的 TIME 类型用 `int` 毫秒表示）。把除数从 `1000`（int）改为 `1000L`（long），使 `floorDiv` 调用 `floorDiv(long, long)` 重载而非 `floorDiv(long, int)` 重载，保持与上下文一致。注意这里 `(int)` cast 是有意的——Flink TIME 类型的范围足以用 `int` 表示。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/assigner/TestWatermarkBasedSplitAssigner.java`、`flink/v1.18/flink/.../TestWatermarkBasedSplitAssigner.java`

**修改目的**：修复测试中 split/file 编号乘法的潜在整数溢出（与 0893 在 v1.19 中做的修复相同，本提交补 v1.17/v1.18）。

**工作逻辑**：

```diff
-                                    SCHEMA, 2, splitNum * filesPerSplit + fileNum))
+                                    SCHEMA, 2, (long) splitNum * filesPerSplit + fileNum))
```

`splitNum`、`filesPerSplit`、`fileNum` 均为 `int`，`splitNum * filesPerSplit` 在 `int` 域计算可能溢出。给 `splitNum` 加 `(long)` cast 后，整个表达式提升到 `long` 域，避免溢出，正确传入 `RandomGenericData.generate(schema, rowCount, seed)` 的 `long` 种子参数。

## 小结

- **成效**：把 Flink 三个版本目录（v1.17、v1.18、v1.19）中 `FlinkParquetReaders` 与 `TestWatermarkBasedSplitAssigner` 的 `long` casting 问题一次性修完。相比 0893，本提交更彻底——不仅把乘数改为 `L` 后缀，还把 `floorDiv`/`floorMod` 的除数也改为 `L` 后缀，并补充修复了 0893 遗漏的 `read(Integer reuse)`（TIME 类型）方法。
- **影响范围**：5 个文件，11 行改动（每个 `FlinkParquetReaders` 改 3 行，每个 `TestWatermarkBasedSplitAssigner` 改 1 行；v1.17、v1.18 各 2 文件，v1.19 只改 `FlinkParquetReaders` 1 文件因为测试已在 0893 中修复）。无 API 签名变更，运行时行为在正常输入下与改动前等价。
- **回迁到 1.4.x 的注意事项**：该提交是 bug fix 性质的清理，**适合回迁到 1.4.x**，但需要注意：
  1. 1.4.x 维护的 Flink 版本目录（通常为 v1.17、v1.18、v1.19）中，`FlinkParquetReaders` 与 `TestWatermarkBasedSplitAssigner` 应同样存在这些模式；
  2. 如果 1.4.x 已回迁 0893（v1.19 的部分修复），本提交是它的完善版本，建议用本提交覆盖 0893 的 Flink 部分，或直接回迁本提交（它包含了 0893 在 v1.19 的改动 + v1.17/v1.18 的同步修复 + `read(Integer reuse)` 的新修复）；
  3. 如果 1.4.x 未回迁 0893，可直接回迁本提交，一次性覆盖所有 Flink 版本目录；
  4. 回迁时需注意 v1.19 的起始状态：若 1.4.x 的 v1.19 已有 0893 的改动（乘数已为 `1000L`），则本提交在 v1.19 的 diff 只是再把除数改为 `L`；若 1.4.x 的 v1.19 未有 0893 的改动，则需要把除数和乘数一起改；
  5. 改动小、风险低、收益明确，建议优先回迁。
