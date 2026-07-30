# 提交 1434：Flink: Test both "new" Flink Avro planned reader and "deprecated" Avro reader (#11430)

## 提交信息

- **序号**：1434 / 4088
- **哈希**：f3560871564c95a0e7b2bff3ca6ecb2e08726d01
- **短哈希**：f35608715
- **日期**：2024-11-26（Tue Nov 26 16:55:09 2024 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Flink: Test both "new" Flink Avro planned reader and "deprecated" Avro reader (#11430)
- **PR/Issue**：#11430
- **作用模块**：Flink v1.20（测试目录）

## 总体目的

Iceberg 的 Flink 集成提供了两种 Avro 数据读取器实现：

1. **「新」的 `FlinkPlannedAvroReader`**：基于 Iceberg 的「planned reader」（按需读取计划字段）机制，使用 `AvroWithPartnerVisitor` 走读 Avro schema 并构建 `ValueReader<RowData>`，是推荐的现代实现。
2. **「已弃用」的 `FlinkAvroReader`**：基于传统的「read schema」（先解析整个 read schema 再读取）方式，已被标记为 `@Deprecated`，计划在 1.8.0 移除。

在本次提交之前，`TestFlinkAvroReaderWriter` 测试类仅通过 `createResolvingReader(FlinkPlannedAvroReader::create)` 验证了新 planned reader，而旧的 deprecated reader 没有任何测试覆盖。这带来两个问题：

- 旧 reader 缺乏回归保护，难以保证其在被移除前持续可用；
- 两种 reader 在行为上应保持一致（输出相同的 RowData），但缺乏对比测试，无法发现潜在的实现差异。

本提交重构既有测试，让两种 reader 都能在同一套测试用例下被验证，确保行为一致并为后续清理 deprecated reader 打下基础。

## 如何达成设计目的

采用经典的「抽象基类 + 子类提供读取器」模板方法模式：

1. 将原 `TestFlinkAvroReaderWriter.java` 重命名为 `AbstractTestFlinkAvroReaderWriter.java`，并将类改为 `abstract`；
2. 在抽象基类中提取一个抽象方法 `createAvroReadBuilder(File recordsFile, Schema schema)`，由子类决定使用哪种 reader；
3. 把原本硬编码的 `Avro.read(...).project(schema).createResolvingReader(FlinkPlannedAvroReader::create)` 调用替换为对 `createAvroReadBuilder(recordsFile, schema).build()` 的调用；
4. 新建两个具体子类：
   - `TestFlinkAvroPlannedReaderWriter`：使用新的 `FlinkPlannedAvroReader::create`（`createResolvingReader`）；
   - `TestFlinkAvroDeprecatedReaderWriter`：使用旧的 `FlinkAvroReader::new`（`createReaderFunc`），并标记 `@Deprecated`、注明将在 1.8.0 随 `FlinkAvroReader` 一起移除。
5. 对 `TestRowProjection` 进行参数化改造，使用 Iceberg 自有的 `ParameterizedTestExtension` + JUnit 5 的 `@TestTemplate`，让原本每个 `@Test` 方法在 `useAvroPlannedReader=false` 与 `true` 两种情况下各跑一次，覆盖两种 reader 的列投影行为。

这样既复用了原测试用例（不重复编写场景），又确保两种 reader 在所有列投影场景下行为一致。

## 修改详情

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkAvroReaderWriter.java` → `AbstractTestFlinkAvroReaderWriter.java`（重命名 + 修改）

**修改目的**：将原具体测试类改造为抽象基类，提供共享测试逻辑。

**工作逻辑**：
- 类名从 `TestFlinkAvroReaderWriter` 改为 `AbstractTestFlinkAvroReaderWriter`，并加 `abstract` 修饰符；
- 新增抽象方法 `protected abstract Avro.ReadBuilder createAvroReadBuilder(File recordsFile, Schema schema);`，由子类实现以提供不同 reader 的 `ReadBuilder`；
- 在 `writeAndValidate` 方法中，把原本硬编码的 reader 创建代码：
  ```java
  Avro.read(Files.localInput(recordsFile))
      .project(schema)
      .createResolvingReader(FlinkPlannedAvroReader::create)
      .build()
  ```
  替换为：
  ```java
  createAvroReadBuilder(recordsFile, schema).build()
  ```
  让子类决定使用哪种 reader；
- 另移除 `testNumericTypes` 方法体内一个多余空行（属顺带的格式清理，无逻辑影响）。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkAvroDeprecatedReaderWriter.java`（新增）

**修改目的**：为「已弃用」的 `FlinkAvroReader` 提供测试覆盖。

**工作逻辑**：继承 `AbstractTestFlinkAvroReaderWriter`，类上标注 `@Deprecated` 并在 Javadoc 中注明「should be removed in 1.8.0; along with FlinkAvroReader」。实现抽象方法：
```java
@Override
protected Avro.ReadBuilder createAvroReadBuilder(File recordsFile, Schema schema) {
  return Avro.read(Files.localInput(recordsFile))
      .project(schema)
      .createReaderFunc(FlinkAvroReader::new);
}
```
注意使用的是 `createReaderFunc(FlinkAvroReader::new)`（旧式 read schema 路径），与新 reader 用的 `createResolvingReader` 区分开。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkAvroPlannedReaderWriter.java`（新增）

**修改目的**：为「新」的 `FlinkPlannedAvroReader` 提供等价测试覆盖（替代原 `TestFlinkAvroReaderWriter` 直接覆盖 planned reader 的角色）。

**工作逻辑**：继承 `AbstractTestFlinkAvroReaderWriter`，无 `@Deprecated`。实现抽象方法：
```java
@Override
protected Avro.ReadBuilder createAvroReadBuilder(File recordsFile, Schema schema) {
  return Avro.read(Files.localInput(recordsFile))
      .project(schema)
      .createResolvingReader(FlinkPlannedAvroReader::create);
}
```

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestRowProjection.java`

**修改目的**：让列投影测试同时覆盖 planned reader 与 deprecated reader，确保两者在投影场景下行为一致。

**工作逻辑**：
- 引入 Iceberg 自有的 `ParameterizedTestExtension`、`Parameter`、`Parameters`，以及 JUnit 5 的 `@TestTemplate` 与 `@ExtendWith`；
- 类上标注 `@ExtendWith(ParameterizedTestExtension.class)`；
- 新增字段 `@Parameter(index = 0) protected Boolean useAvroPlannedReader;` 与参数工厂方法：
  ```java
  @Parameters(name = "useAvroPlannedReader={0}")
  protected static List<Object[]> parameters() {
    return Arrays.asList(new Object[] {Boolean.FALSE}, new Object[] {Boolean.TRUE});
  }
  ```
- 在 `writeAndRead` 内根据 `useAvroPlannedReader` 选择 reader：`false` 时用 `createReaderFunc(FlinkAvroReader::new)`（旧 reader），`true` 时用 `createResolvingReader(FlinkPlannedAvroReader::create)`（新 reader）；
- 把所有原有 `@Test` 方法改为 `@TestTemplate`，使每个测试方法在两组参数下各执行一次，从而两种 reader 都跑完整套投影场景。

## 小结

- **成效**：通过抽象基类 + 子类与参数化测试两种方式，让 Flink Avro 的「新 planned reader」与「deprecated reader」共享同一套测试场景，前者继承覆盖、后者参数化覆盖；为 deprecated reader 补齐了缺失的回归保护，并显式对比两种 reader 的行为一致性，为后续在 1.8.0 移除 deprecated `FlinkAvroReader` 提供了信心基础。改动集中在 Flink v1.20 的测试目录，4 个文件、新增约 116 行、删除约 25 行。
- **影响范围**：仅测试代码（`flink/v1.20/flink/src/test/...`），无主代码或构建变更；不改变任何运行时行为，仅增加测试覆盖。
- **回迁到 1.4.x 的注意事项**：本提交是测试重构，回迁价值在于让 1.4.x 的 Flink Avro reader 同样具备双 reader 测试覆盖。但需注意：1.4.x 的 Flink 模块结构可能与 main 不同（如 1.4.x 可能没有独立的 v1.20 目录，而是 v1.17/v1.18/v1.19），且 1.4.x 是否已包含 `FlinkPlannedAvroReader` 与对应的 `ParameterizedTestExtension` 工具类需先核对。若 1.4.x 的 `FlinkAvroReader` 尚未标记 deprecated 或尚未引入 `FlinkPlannedAvroReader`，本提交的测试结构改动并不直接适用——更应先回迁 reader 实现本身（参见本批后续提交 1440，即把 planned reader 回迁到 v1.18/v1.19），再回迁测试。如果仅关注 1.4.x 现有 reader 的稳定性，本测试改动可按目录结构适配后选择性回迁。
