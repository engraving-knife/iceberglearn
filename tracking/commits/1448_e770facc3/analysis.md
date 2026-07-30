# 提交 1448：Core, GCS, Spark: Replace wrong order of assertion (#11677)

## 提交信息

- **序号**：1448
- **哈希**：e770facc3e7cbccb719b3ae5263cd1ece181f9ea
- **短哈希**：e770facc3
- **日期**：2024-11-30（Sat Nov 30 00:05:44 2024 +0900，提交时间 +0100 Fri Nov 29 16:05:44 2024）
- **作者**：Yuya Ebihara <ebyhry@gmail.com>
- **提交说明**：Core, GCS, Spark: Replace wrong order of assertion (#11677)
- **PR/Issue**：#11677

## 总体目的

AssertJ 的断言 API 约定是 `assertThat(actual).isEqualTo(expected)`——即"实际值"放在 `assertThat(...)` 中，"期望值"放在 `isEqualTo(...)` 中。这样当断言失败时，错误消息会正确地显示为 "expected: <期望值> but was: <实际值>"，便于调试。

但仓库中有 3 处测试代码把参数顺序写反了——`assertThat(expected).isEqualTo(actual)`。虽然当两值相等时测试仍能通过（不影响 CI 绿灯），但当测试失败时，错误消息会反向显示："expected: <实际值> but was: <期望值>"，误导调试人员判断哪个是预期、哪个是实际。

本提交把这 3 处断言的参数顺序修正为 `assertThat(actual).isEqualTo(expected)`，让失败消息正确反映预期与实际的关系。这是一个测试代码质量改进，不改变任何测试逻辑或被测代码。

## 如何达成设计目的

直接把每处 `assertThat(expected).isEqualTo(actual)` 中的两个参数互换位置。对带 `.as(description)` 的断言，保持描述文本不变，只交换 `assertThat` 与 `isEqualTo` 的参数。

## 修改详情

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcUtil.java`

**修改目的**：修正 `JdbcUtil.filterAndRemovePrefix` 测试中的断言参数顺序。

**工作逻辑**：

```java
Properties expected = ...;  // 期望的 Properties
Properties actual = JdbcUtil.filterAndRemovePrefix(input, "jdbc.");

// 修改前：assertThat(expected).isEqualTo(actual);
// 修改后：
assertThat(actual).isEqualTo(expected);
```

### `gcp/src/test/java/org/apache/iceberg/gcp/gcs/GCSFileIOTest.java`

**修改目的**：修正 GCSFileIO 读写字节内容比对测试中的断言参数顺序。

**工作逻辑**：

```java
byte[] expected = new byte[1024 * 1024];
random.nextBytes(expected);
// ... 写入 expected，再读入 actual ...
byte[] actual = new byte[1024 * 1024];
// ... IOUtil.readFully(is, actual, 0, actual.length) ...

// 修改前：assertThat(expected).isEqualTo(actual);
// 修改后：
assertThat(actual).isEqualTo(expected);
```

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java`

**修改目的**：修正分区 spec 列表比对测试中的断言参数顺序。

**工作逻辑**：

```java
List<Integer> actual = ...;  // 从查询结果收集的 spec id 列表

// 修改前：
// assertThat(ImmutableList.of(spec0, spec1))
//     .as("Should have two partition specs")
//     .isEqualTo(actual);
// 修改后：
assertThat(actual)
    .as("Should have two partition specs")
    .isEqualTo(ImmutableList.of(spec0, spec1));
```

`.as("Should have two partition specs")` 描述保持不变。

## 小结

- **成效**：修正了 3 处 AssertJ 断言中"实际值"与"期望值"参数颠倒的问题，确保断言失败时错误消息正确显示 "expected: <期望值> but was: <实际值>"，提升测试失败时的可调试性。
- **影响范围**：仅修改 3 个测试文件各 1-2 行，共 4 行新增、4 行删除。无被测代码变更、无测试逻辑变更（只是参数位置互换）。
- **回迁到 1.4.x 的注意事项**：这是测试代码质量改进，不影响产品功能或运行时行为，**优先级极低**。1.4.x 上若存在同样的参数颠倒问题，可回迁以改善调试体验；若不存在或 1.4.x 测试结构已与 main 分叉，可跳过。回迁时需注意 1.4.x 上 `TestIcebergSourceTablesBase` 可能存在于多个 Spark 版本目录（v3.3/v3.4/v3.5），需同步检查所有版本。另外，若 1.4.x 上这些测试文件的行号或上下文与 main 不同，cherry-pick 可能需要手动解决冲突。
