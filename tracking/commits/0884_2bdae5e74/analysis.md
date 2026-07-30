# 提交 0884：Fix incorrect double-checked-locking around TestStreamScanSql#tEnv (#10605)

## 提交信息

- **序号**：0884 / 4088
- **哈希**：2bdae5e744dc014963cb6c5e1a2372f1718888c5
- **短哈希**：2bdae5e74
- **日期**：2024-06-29（Sat Jun 29 21:22:01 2024 +0700）
- **作者**：Tai Le Manh <49281946+tlm365@users.noreply.github.com>
- **提交说明**：Fix incorrect double-checked-locking around TestStreamScanSql#tEnv (#10605)
- **PR/Issue**：#10605

## 总体目的

`TestStreamScanSql` 是 Flink 模块中针对流式扫描 SQL 的测试基类（继承自 `CatalogTestBase`），其 `getTableEnv()` 方法采用"双重检查锁定"（Double-Checked Locking, DCL）模式来惰性初始化一个 `TableEnvironment tEnv` 字段，避免在每次测试调用时重复创建 `StreamExecutionEnvironment` 与 `StreamTableEnvironment`。

原实现存在 DCL 经典错误：

1. **字段未声明 `volatile`**（至少 v1.19 版本如此）。在 Java 内存模型（JMM）下，没有 `volatile` 的 DCL 是不安全的：一个线程可能在另一个线程尚未完成对象构造（即 `tEnv` 引用已赋值但对象内部状态尚未对其它线程可见）时读到非 null 引用，从而使用到未完全初始化的对象。
2. **DCL 结构本身可改进**。原代码在无锁快路径上直接读 `tEnv` 做空判断，然后又 `return tEnv`——理论上存在两次读之间字段被改动的隐患（虽然此处 `tEnv` 只赋值一次，实际不会出问题，但模式不够规范）。正确做法是把字段值读到局部变量再判断、再返回，减少对共享字段的访问次数。

本提交的目的就是修正这两个问题：在 v1.19 版本中将 `tEnv` 声明为 `volatile`，并在三个 Flink 版本（1.17/1.18/1.19）中重写 `getTableEnv()` 的 DCL 结构，改用"先读入局部变量再判断"的标准模式。

## 如何达成设计目的

实现方式为纯测试代码重构，无生产代码改动：

- **v1.19**：将字段 `private TableEnvironment tEnv;` 改为 `private volatile TableEnvironment tEnv;`，确保跨线程可见性与 happens-before 语义；同时重写 `getTableEnv()` 方法体。
- **v1.17 / v1.18**：仅重写 `getTableEnv()` 方法体（字段声明未改动——见"注意事项"）。

新方法体的标准 DCL 模式为：

```java
TableEnvironment tableEnv = tEnv;        // 1. 先读入局部变量
if (tableEnv != null) {                  // 2. 快路径：非空直接返回局部变量
    return tableEnv;
}
synchronized (this) {                    // 3. 加锁
    if (tEnv == null) {                  // 4. 再次检查（防止重复初始化）
        // ... 构造 streamTableEnv
        tEnv = streamTableEnv;           // 5. 赋值
    }
}
return tEnv;                             // 6. 返回（此时必非空）
```

关键改进点：快路径仅读一次 `tEnv` 到局部变量 `tableEnv`，后续判断与返回都使用局部变量，避免重复读共享字段。加锁后的二次检查仍然直接读 `tEnv`（在 synchronized 块内是安全的）。

## 修改详情

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java`

**修改目的**：修正 `getTableEnv()` 的 DCL 结构。

**工作逻辑**：将原"外层 if null → synchronized → 内层 if null"结构改为"读局部变量 → 非空返回 → synchronized → 内层 if null → 初始化"。注意此版本**未**将 `tEnv` 字段改为 `volatile`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java`

**修改目的**：同 v1.17，修正 DCL 结构。

**工作逻辑**：与 v1.17 完全相同的改动。此版本同样**未**将 `tEnv` 字段改为 `volatile`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamScanSql.java`

**修改目的**：修正 DCL 结构并补全 `volatile` 关键字。

**工作逻辑**：
- 字段声明由 `private TableEnvironment tEnv;` 改为 `private volatile TableEnvironment tEnv;`，确保 DCL 在 JMM 下正确。
- 方法体改动与 v1.17/v1.18 相同。

## 小结

- **成效**：修正了 `TestStreamScanSql.getTableEnv()` 中不规范的 DCL 实现，改用先读局部变量的标准模式；v1.19 版本额外补全 `volatile` 关键字，使 DCL 在 JMM 下完全正确。
- **影响范围**：仅 3 个 Flink 测试文件（v1.17/v1.18/v1.19 的 `TestStreamScanSql.java`），纯测试代码，无生产逻辑影响。
- **回迁到 1.4.x 的注意事项**：可回迁，风险低。需特别注意一个**潜在遗漏**：v1.17 和 v1.18 版本只改了 DCL 结构，未将 `tEnv` 字段加 `volatile`，而 v1.19 同时加了两处改动。这意味着 v1.17/v1.18 的 DCL 在严格 JMM 语义下仍不完全正确（虽然测试环境中实际触发问题的概率极低）。回迁到 1.4.x 时建议同时检查对应 Flink 版本的 `tEnv` 字段是否为 `volatile`，若否则一并补上，以保证修复完整性。
