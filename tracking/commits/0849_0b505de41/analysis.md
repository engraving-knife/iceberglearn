# 提交 0849：Flink: Import Assertions statically (#10532)

## 提交信息
- **序号**：0849 / 4088
- **哈希**：0b505de413acb02441efb46ddc7aa1f1cd5621f4
- **短哈希**：0b505de41
- **日期**：2024-06-18（Tue Jun 18 11:41:48 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Flink: Import Assertions statically (#10532)
- **PR/Issue**：#10532

## 总体目的

本提交对 Iceberg Flink 模块的所有测试文件进行 AssertJ 静态导入风格统一改造，将原本以 `Assertions.assertThat(...)`、`Assertions.assertThatThrownBy(...)`、`Assertions.assertThatExceptionOfType(...)`、`Assertions.assertThatNoException(...)` 形式调用的 AssertJ 断言方法，统一改为静态导入后直接以 `assertThat(...)`、`assertThatThrownBy(...)` 等方式调用。

这是一次大规模、纯机械化的代码风格重构提交。Iceberg 的其它模块（如 core、spark）已经在历史提交中完成了 AssertJ 静态导入改造，Flink 模块此前仍保留着 `Assertions.` 类前缀的形式，属于历史遗留不一致。本次提交将 Flink 模块对齐到项目整体风格。

从测试可读性角度看，去除 `Assertions.` 前缀后断言更短、更聚焦于被断言对象本身，符合 AssertJ 官方推荐的"fluent assertion"使用方式（`assertThat(actual).isEqualTo(expected)` 比 `Assertions.assertThat(actual).isEqualTo(expected)` 更简洁）。同时由于多数测试文件原本只用到 `Assertions` 类的少量方法，将这几个方法改为静态导入后通常可以移除整个 `import org.assertj.core.api.Assertions;`，整体 import 区块更紧凑。

由于改造不涉及任何业务逻辑、断言语义、断言参数或测试用例的修改，本提交不会改变测试覆盖行为，是纯风格层面的批量调整。

## 如何达成设计目的

提交针对 Flink 模块的 76 个测试 Java 文件，按 Flink 版本目录分别改造，覆盖 v1.17、v1.18、v1.19 三个 Flink 版本子模块（各 25 / 25 / 26 个文件，合计 76 个文件，426 行新增、413 行删除）。改造在每个文件内执行统一的两个动作：

1. **添加静态导入**：在文件 `package` 声明之后、其它 `import` 之前，加入按需的静态导入语句，仅导入该文件实际使用到的 AssertJ 静态方法。整个提交涉及以下 4 种静态导入，按文件实际使用情况选择性加入：
   ```java
   import static org.assertj.core.api.Assertions.assertThat;
   import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
   import static org.assertj.core.api.Assertions.assertThatNoException;
   import static org.assertj.core.api.Assertions.assertThatThrownBy;
   ```
2. **移除普通导入**：删除原 `import org.assertj.core.api.Assertions;` 行。
3. **替换调用点**：把 `Assertions.assertThat(...)`、`Assertions.assertThatThrownBy(...)`、`Assertions.assertThatExceptionOfType(...)`、`Assertions.assertThatNoException(...)` 中的 `Assertions.` 前缀去掉。整个提交共替换 395 处调用点，按方法分布为：
   - `assertThat`：206 处
   - `assertThatThrownBy`：162 处
   - `assertThatExceptionOfType`：21 处
   - `assertThatNoException`：6 处

由于这是 Iceberg 多 Flink 版本并行维护的项目结构（每个 Flink 版本对应独立的源码目录），同一个测试类在 v1.17 / v1.18 / v1.19 下各有一份独立副本，改造需要在三个版本目录下分别应用。这也解释了为何改动文件数高达 76 而非 26。改造对 v1.19 目录额外包含一个 `TestSketchDataStatistics.java`（v1.17、v1.18 中不存在该文件，因 sketch shuffle 特性是 Flink 1.19 才引入）。

转换完成后，所有断言语义保持等价：`Assertions.assertThat(x).isEqualTo(y)` 与 `assertThat(x).isEqualTo(y)` 在 AssertJ 中是同一个静态方法的不同调用写法，断言链中后续的 `.isNotNull()`、`.as(message)`、`.containsExactlyInAnyOrderElementsOf(...)`、`.hasMessage(...)`、`.isInstanceOf(...)` 等方法不受影响，保持原状。

## 修改详情

由于本提交涉及 76 个文件且改动模式完全一致，下面选取代表性文件说明，并按改动模式归纳所有文件的统一修改逻辑。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestBase.java`
**修改目的**：将 `Assertions.assertThat(...)` 调用改为静态导入后的 `assertThat(...)`。

**工作逻辑**：
- 新增 `import static org.assertj.core.api.Assertions.assertThat;`
- 删除 `import org.assertj.core.api.Assertions;`
- 两处 `assertSameElements(...)` 方法中：
  - `Assertions.assertThat(actual).isNotNull().containsExactlyInAnyOrderElementsOf(expected);` → `assertThat(actual).isNotNull().containsExactlyInAnyOrderElementsOf(expected);`
  - `Assertions.assertThat(actual).isNotNull().as(message).containsExactlyInAnyOrderElementsOf(expected);` → `assertThat(actual).isNotNull().as(message).containsExactlyInAnyOrderElementsOf(expected);`
- 后续 fluent 链 `.isNotNull()`、`.as(message)`、`.containsExactlyInAnyOrderElementsOf(...)` 保持不变。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/TestHelpers.java`
**修改目的**：移除 `Assertions` 类前缀。

**工作逻辑**：
- 删除 `import org.assertj.core.api.Assertions;`
- 未新增任何 `import static`（因该文件仅用到 `Assertions.assertThat`，而改造后的 `assertThat` 调用通过同包内 import 引入；确切说是该文件未显式添加 `import static org.assertj.core.api.Assertions.assertThat;`——需注意此情况出现在原始 diff 中，但 Java 编译要求 `assertThat` 必须有静态导入来源，请回迁时核对源码确认是否需补 import）。
  - 实际上回迁时若发现该文件未显式新增 `assertThat` 的静态导入，应理解为改造脚本可能未在该文件添加；为保持编译通过，回迁后应保证 `assertThat` 有合法静态导入来源。本分析在审查 diff 时观察到 `TestHelpers.java` 的 diff 仅显示删除 `import org.assertj.core.api.Assertions;` 与调用点替换，未显示新增 `import static`，回迁时需人工补全。
- 调用点 `Assertions.assertThat(results).containsExactlyElementsOf(expected);` → `assertThat(results).containsExactlyElementsOf(expected);`

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/data/TestRowDataProjection.java`
**修改目的**：演示一次性引入多个 AssertJ 静态方法的情况。

**工作逻辑**：
- 新增三个静态导入：
  ```java
  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatNoException;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  ```
- 删除 `import org.assertj.core.api.Assertions;`
- 多处替换：
  - `Assertions.assertThatThrownBy(() -> projection.wrap(null))...` → `assertThatThrownBy(() -> projection.wrap(null))...`
  - 多处 `Assertions.assertThat(idOnly.columns().size()).isGreaterThan(0);` → `assertThat(idOnly.columns().size()).isGreaterThan(0);`（针对 `idOnly`、`latOnly`、`longOnly`、`locationOnly`、`mapOnly` 等多个 Schema 局部变量）
- 后续断言链 `.isInstanceOf(IllegalArgumentException.class)`、`.hasMessage(...)`、`.isGreaterThan(0)` 等保持不变。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestMapRangePartitioner.java`
**修改目的**：典型大批量断言替换样例（该文件含 25 处替换，是改动量最大的文件之一）。

**工作逻辑**：
- 新增 `import static org.assertj.core.api.Assertions.assertThat;`
- 删除 `import org.assertj.core.api.Assertions;`
- 多处替换：
  - `Assertions.assertThat(actualAssignment).isEqualTo(expectedAssignment);` → `assertThat(actualAssignment).isEqualTo(expectedAssignment);`
  - `Assertions.assertThat(actualAssignmentInfo).isEqualTo(expectedAssignmentInfo);` → `assertThat(actualAssignmentInfo).isEqualTo(expectedAssignmentInfo);`
  - `Assertions.assertThat(partitionResults.size()).isEqualTo(expectedAssignmentInfo.size());` → `assertThat(partitionResults.size()).isEqualTo(expectedAssignmentInfo.size());`
  - `Assertions.assertThat(actualAssignedKeyCounts).as("...").isEqualTo(expectedAssignedKeyCounts);` → `assertThat(actualAssignedKeyCounts).as("...").isEqualTo(expectedAssignedKeyCounts);`
- 该文件中 `.isEqualTo(...)`、`.as(...)` 等后续 fluent 方法保持不变。

### 其余 72 个文件
**修改目的**：统一将测试文件中 AssertJ 调用改为静态导入形式。

**工作逻辑**：
- 全部按上述同一模式改造：按文件实际使用情况选择性新增 1-4 个静态导入，删除原 `import org.assertj.core.api.Assertions;`，替换所有调用点。
- 改造覆盖的测试类按功能划分包括：
  - 顶层测试：`HadoopCatalogExtension`、`TestBase`、`TestCatalogTableLoader`、`TestFlinkCatalogDatabase`、`TestFlinkCatalogTable`、`TestFlinkCatalogTablePartitions`、`TestHelpers`、`TestRewriteDataFilesAction`
  - data 子模块：`TestRowDataProjection`
  - sink 子模块：`TestBucketPartitionKeySelector`、`TestBucketPartitioner`、`TestBucketPartitionerFlinkIcebergSink`、`TestFlinkIcebergSink`、`TestFlinkIcebergSinkV2`、`TestFlinkIcebergSinkV2Base`、`TestIcebergStreamWriter`、`TestMapDataStatistics`、`TestMapRangePartitioner`、`TestSketchDataStatistics`（仅 v1.19）
  - source 子模块：`TestScanContext`、`TestStreamScanSql`、`TestFileSequenceNumberBasedSplitAssigner`、`TestContinuousIcebergEnumerator`、`TestContinuousSplitPlannerImpl`、`TestContinuousSplitPlannerImplStartStrategy`、`TestColumnStatsWatermarkExtractor`

## 小结
- **成效**：Flink 模块测试代码风格与项目其他模块（core、spark）对齐，统一使用 AssertJ 静态导入方式调用断言；测试代码可读性提升，断言更简洁；移除冗余类前缀后 import 区块更紧凑。共改造 76 个文件，426 行新增、413 行删除，395 处断言调用点替换。
- **影响范围**：仅影响 Flink 模块的测试代码，覆盖 v1.17、v1.18、v1.19 三个 Flink 版本子模块；不影响主代码、构建产物或运行时行为，不改变测试覆盖与断言语义。改造是纯等价变换，编译后的字节码语义一致。
- **回迁注意事项**：当前 1.4.x 分支 Flink 子模块仅包含 v1.15、v1.16、v1.17（不含 v1.18、v1.19、v2.1 之外的版本），与 main 1.4.x 时期已不一致；本提交影响的是 v1.17/v1.18/v1.19 三个目录，1.4.x 仅 v1.17 目录可直接应用本提交改造（v1.18/v1.19 在 1.4.x 上不存在）。回迁时建议：
  1. 仅回迁 v1.17 目录下的 25 个文件改动，可使用相同 diff 直接 apply，冲突风险低。
  2. 若 1.4.x 已有 v1.18/v1.19 源码（取决于 1.4.x 的演进阶段），可同时回迁对应目录；若不存在则跳过。
  3. 注意 `TestHelpers.java` 等 diff 中未显式新增 `import static` 的文件，回迁后需人工补全静态导入以保证编译通过。
  4. 改造为纯风格变更，回迁后仅需运行 Flink 测试套件确认编译与测试通过即可，无需额外验证逻辑。
