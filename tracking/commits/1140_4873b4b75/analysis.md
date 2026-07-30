# 提交 1140：Core, Kafka, Spark: Use AssertJ instead of JUnit assertions (#11102)

## 提交信息

- **序号**：1140 / 4088
- **哈希**：4873b4b7534de0bcda2e1e0366ffcf83943dc906
- **短哈希**：4873b4b75
- **日期**：2024-09-10（Tue Sep 10 08:05:11 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core, Kafka, Spark: Use AssertJ instead of JUnit assertions (#11102)
- **PR/Issue**：#11102

## 总体目的

Iceberg 项目在测试中同时存在两套断言库：JUnit 5 的 `org.junit.jupiter.api.Assertions`（如 `assertEquals`、`assertNotEquals`、`assertNotNull`、`assertThrows`、`assertTrue`、`assertDoesNotThrow`、`assertNull`）和 AssertJ 的 `org.assertj.core.api.Assertions`（如 `assertThat(...).isEqualTo(...)`、`assertThatThrownBy(...)`）。社区已确立以 AssertJ 为首选的测试风格——AssertJ 提供流式 API、更丰富的断言、更清晰的失败信息，且仓库已有 checkstyle 规则禁止使用 Hamcrest 并建议改用 AssertJ。

本提交完成两件事：

1. **新增 checkstyle 规则**：在 `.baseline/checkstyle/checkstyle.xml` 中新增一个 `IllegalImport` 模块（id=`BanJUnit5Assertions`），把 `org.junit.jupiter.api.Assertions` 标记为非法导入，并附提示信息"Prefer using org.assertj.core.api.Assertions instead."。这样后续任何新代码若 import JUnit 5 断言都会被 checkstyle 拦截，从制度上保证测试统一使用 AssertJ。
2. **清理存量 JUnit 断言**：把三个测试文件中现存的所有 `org.junit.jupiter.api.Assertions.*` 调用改写为等价的 AssertJ 流式断言，并移除对应的 JUnit import。涉及文件：
   - `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`
   - `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/CoordinatorTest.java`
   - `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputeTableStatsAction.java`

## 如何达成设计目的

1. 在 checkstyle 配置中追加一个 `IllegalImport` 模块，禁止导入 `org.junit.jupiter.api.Assertions`，与已有禁止 Hamcrest 的规则并列。
2. 逐文件把 JUnit 断言改写为 AssertJ：
   - `assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`
   - `assertNotEquals(unexpected, actual)` → `assertThat(actual).isNotEqualTo(unexpected)`（本提交中实际多用于 `assertNotEquals(0, x)` → `assertThat(x).isGreaterThan(0)`）
   - `assertNotNull(x)` → `assertThat(x).isNotNull()`
   - `assertNull(x)` → `assertThat(x).isNull()`
   - `assertTrue(cond)` → 等价 AssertJ
   - `assertThrows(Clazz.class, () -> ...)` → `assertThatThrownBy(() -> ...).isInstanceOf(Claud.class)...`
   - `assertDoesNotThrow(() -> ...)` → `assertThatNoException().isThrownBy(() -> ...)`
   - 多个 `assertEquals` 对同一 `Map` 的 key/value 校验合并为 `assertThat(map).containsEntry(k, v).containsEntry(...)`
   - 删除 `import org.junit.jupiter.api.Assertions;` 及其静态导入，替换为 AssertJ 静态导入
3. 部分场景下顺带简化测试代码，例如把 `ImmutableList.copyOf(table.snapshots().iterator()).size()` 与 `assertEquals(0, ...)` 合并为 `assertThat(table.snapshots()).isEmpty()`，既改用 AssertJ 又让断言更直观。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml`

**修改目的**：禁止在测试代码中导入 JUnit 5 的 `Assertions`，强制使用 AssertJ。

**工作逻辑**：在已有的禁止 Hamcrest 的 `IllegalImport` 模块之后，新增：

```xml
<module name="IllegalImport">
    <property name="id" value="BanJUnit5Assertions"/>
    <property name="illegalPkgs" value="org.junit.jupiter.api.Assertions"/>
    <message key="import.illegal" value="Prefer using org.assertj.core.api.Assertions instead."/>
</module>
```

该模块匹配任何 `import org.junit.jupiter.api.Assertions`（含静态导入），命中即在 checkstyle 报告中给出 id=`BanJUnit5Assertions` 的违规与提示语。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：把该测试中 JUnit 5 断言改为 AssertJ。

**工作逻辑**：

- 删除 `import org.junit.jupiter.api.Assertions;`
- 把一个循环里对多个 `unsupportedFormatVersion`（1、2）校验"v3 schema 在 v1/v2 不被支持"的断言，由：

```java
Assertions.assertThrows(
    IllegalStateException.class,
    () -> TableMetadata.newTableMetadata(...),
    String.format("Invalid type in v%s schema: ...", unsupportedFormatVersion));
```

改为：

```java
assertThatThrownBy(
        () -> TableMetadata.newTableMetadata(...))
    .isInstanceOf(IllegalStateException.class)
    .hasMessage(
        "Invalid type in v%s schema: struct.ts_nanos timestamptz_ns is not supported until v3",
        unsupportedFormatVersion);
```

注意：原 JUnit 写法的第三个参数是"失败时的描述消息"（含 `String.format`），改写后用 `.hasMessage(...)` 直接断言异常消息内容，更精确（不仅断言抛异常，还断言消息文本与格式化结果一致）。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/CoordinatorTest.java`

**修改目的**：把 Kafka Connect 协调器测试中的 JUnit 断言改为 AssertJ，并顺带简化集合断言。

**工作逻辑**：

- 删除 `import java.util.Map;` 与 `import org.junit.jupiter.api.Assertions;`（Map 不再需要，因为不再用 `Map<String, String> summary = snapshot.summary();` 单独取变量做多次 assertEquals）。
- `testCommitAppend` / `testCommitDelta` 中：
  - `Assertions.assertEquals(0, ImmutableList.copyOf(table.snapshots().iterator()).size())` → `assertThat(table.snapshots()).isEmpty()`
  - `Assertions.assertEquals(1, snapshots.size())` → `assertThat(snapshots).hasSize(1)`
  - `Assertions.assertEquals(DataOperations.APPEND, snapshot.operation())` → `assertThat(snapshot.operation()).isEqualTo(DataOperations.APPEND)`
  - `Assertions.assertEquals(1, ImmutableList.copyOf(snapshot.addedDataFiles(table.io())).size())` → `assertThat(snapshot.addedDataFiles(table.io())).hasSize(1)`
  - `Assertions.assertEquals(0, ImmutableList.copyOf(snapshot.addedDeleteFiles(table.io())).size())` → `assertThat(snapshot.addedDeleteFiles(table.io())).isEmpty()`
  - 对 summary 的三次 `assertEquals` 合并为：
    ```java
    assertThat(snapshot.summary())
        .containsEntry(COMMIT_ID_SNAPSHOT_PROP, commitId.toString())
        .containsEntry(OFFSETS_SNAPSHOT_PROP, "{\"0\":3}")
        .containsEntry(VALID_THROUGH_TS_SNAPSHOT_PROP, ts.toString());
    ```
- `testCommitDelta` 中对 `addedDeleteFiles` 的断言由 `isEmpty()` 改为 `hasSize(1)`（因为 delta 提交会产生一个删除文件）。
- 另两个用例中 `List<Snapshot> snapshots = ImmutableList.copyOf(table.snapshots()); Assertions.assertEquals(0, snapshots.size());` 简化为 `assertThat(table.snapshots()).isEmpty();`，去掉了多余的中间变量。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputeTableStatsAction.java`

**修改目的**：把 Spark 3.5 `ComputeTableStatsAction` 测试中大量 JUnit 断言改为 AssertJ。

**工作逻辑**：

- 把静态导入从：

```java
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

改为：

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

并删除 `import org.junit.jupiter.api.Assertions;`。

- 典型改写：
  - `Assertions.assertNotEquals(statisticsFile.fileSizeInBytes(), 0)` → `assertThat(statisticsFile.fileSizeInBytes()).isGreaterThan(0)`（语义更明确：文件大小应大于 0）
  - `Assertions.assertEquals(statisticsFile.blobMetadata().size(), 2)` → `assertThat(statisticsFile.blobMetadata()).hasSize(2)`
  - `assertNotNull(results)` → `assertThat(results).isNotNull()`
  - `Assertions.assertNull(result.statisticsFile())` → `assertThat(result.statisticsFile()).isNull()`
  - 对 `blobMetadata.properties()` 的校验：
    ```java
    Assertions.assertEquals(
        blobMetadata.properties().get(APACHE_DATASKETCHES_THETA_V1_NDV_PROPERTY),
        String.valueOf(4));
    ```
    改为：
    ```java
    assertThat(statisticsFile.blobMetadata().get(0).properties())
        .containsEntry(APACHE_DATASKETCHES_THETA_V1_NDV_PROPERTY, "4");
    ```
    （值类型从 `String.valueOf(4)` 即 `"4"` 表达，更直接）
  - `assertThrows(IllegalArgumentException.class, () -> ...)` + `assertTrue(message.contains("..."))` 改为：
    ```java
    assertThatThrownBy(() -> ...)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Can't find column id1 in table");
    ```
    （用 `hasMessageStartingWith` 替代 `assertTrue(message.contains(...))`，断言更精确）
  - `assertDoesNotThrow(() -> ...)` 改为：
    ```java
    assertThatNoException()
        .isThrownBy(() -> ...);
    ```
  - 对 `assertNotNull(blobMetadata.properties().get(KEY))` 改为 `.containsKey(KEY)`，语义更贴合 Map 场景。
  - `Assertions.assertEquals(exception.getMessage(), "No columns found to compute stats")` 改为 `.hasMessage("No columns found to compute stats")`。

整体上，该文件改动量最大（约 -75/+54 行），但全是断言风格的等价改写，无测试逻辑变化。

## 小结

- **成效**：测试代码统一使用 AssertJ 流式断言，可读性与失败信息质量提升；新增 checkstyle 规则从制度上禁止后续代码再导入 JUnit 5 断言，保证风格一致性。AssertJ 的链式断言（如 `containsEntry(...).containsEntry(...)`、`isInstanceOf(...).hasMessage(...)`）让测试意图更清晰，部分场景下顺带简化了集合断言。
- **影响范围**：4 个文件——checkstyle 配置 +5 行；3 个测试文件共计 +90/-120 行左右（净减少约 25 行，主要是 AssertJ 链式断言比 JUnit 多行断言更紧凑）。无产品代码变更，仅测试与构建检查规则。
- **回迁到 1.4.x 的注意事项**：本提交是测试基础设施与风格统一改动，对 1.4.x 运行时无任何影响。
  - **checkstyle 规则**（`BanJUnit5Assertions`）：**建议回迁**，可在 1.4.x 上同样禁止新增 JUnit 断言，保持风格一致；但需注意若 1.4.x 测试中仍大量使用 JUnit 5 断言，回迁该规则会导致 checkstyle 大量失败，需配套清理。若 1.4.x 已进入冻结、不再接受大范围测试改动，可暂不回迁该规则。
  - **测试断言改写**：属于风格优化，非 bug 修复，**可选回迁**，1.4.x 通常无需单独回迁测试风格改动；除非 1.4.x 上某测试因 JUnit 断言失败信息不足而难以排查，否则可不动。
  - 回迁时注意 AssertJ 版本（`assertj-core`）在 1.4.x 上应已具备 `assertThatNoException`、`containsEntry`、`hasMessageStartingWith` 等方法（AssertJ 3.26.3 已支持）。
