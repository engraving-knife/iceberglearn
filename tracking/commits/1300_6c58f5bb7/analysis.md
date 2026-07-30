# 提交 1300：Revert "Core: Snapshot `summary` map must have `operation` key (#11354)" (#11409)

## 提交信息

- **序号**：1300 / 4088
- **哈希**：6c58f5bb7f32ec3b322f68f15c82a62a95267e77
- **短哈希**：6c58f5bb7
- **日期**：2024-10-28（Mon Oct 28 13:32:19 2024 -0400）
- **作者**：Kevin Liu <kevinjqliu@users.noreply.github.com>
- **提交说明**：Revert "Core: Snapshot `summary` map must have `operation` key (#11354)" (#11409)
- **PR/Issue**：#11409（回退 PR #11354，原始提交 7ad11b2df1a266d29f9e4f6bb5b499cb68c0afb7）

## 总体目的

此前 PR #11354（提交 7ad11b2）修改了 `SnapshotParser` 的 JSON 解析逻辑，强制要求 Snapshot 的 `summary` map 中必须包含 `operation` 键。具体做法是在解析 summary 时，先通过 `JsonUtil.getString(OPERATION, sNode)` 单独提取 `operation` 字段（如果缺失则抛出 `IllegalArgumentException: Cannot parse missing string: operation`），然后再遍历其余字段构建 summary map。

这一改动导致了向后兼容性问题：某些旧版本或外部工具生成的 Snapshot 元数据中，`summary` map 可能不包含 `operation` 键（operation 信息可能仅存在于 Snapshot 的顶层字段，或由于历史原因缺失）。强制要求 `operation` 键会导致这些 Snapshot 无法被解析，从而破坏了对历史元数据的读取能力。

本提交回退该改动，恢复原有的解析逻辑：在遍历 summary 字段时，如果遇到 `operation` 字段则提取它，如果未遇到则 operation 为 null，不强制要求其存在。同时删除了 PR #11354 新增的三个测试用例。

## 如何达成设计目的

通过 `git revert` 回退原始提交 7ad11b2 的全部改动，涉及两个文件：

1. `SnapshotParser.java`：恢复原有的 operation 提取逻辑（在循环内条件提取，而非循环外强制提取）。
2. `TestSnapshotJson.java`：删除 PR #11354 新增的三个测试方法及相关 import。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotParser.java`

**修改目的**：恢复 summary 解析时 `operation` 字段的非强制性提取逻辑。

**工作逻辑**：在 `fromJson` 方法中解析 summary 对象的部分，回退改动如下：

```java
// 修改前（PR #11354 的逻辑：强制提取）
operation = JsonUtil.getString(OPERATION, sNode);  // 缺失则抛异常
ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
Iterator<String> fields = sNode.fieldNames();
while (fields.hasNext()) {
  String field = fields.next();
  if (!field.equals(OPERATION)) {
    builder.put(field, JsonUtil.getString(field, sNode));
  }
}

// 修改后（回退后的逻辑：条件提取）
ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
Iterator<String> fields = sNode.fieldNames();
while (fields.hasNext()) {
  String field = fields.next();
  if (field.equals(OPERATION)) {
    operation = JsonUtil.getString(OPERATION, sNode);  // 遇到才提取
  } else {
    builder.put(field, JsonUtil.getString(field, sNode));
  }
}
```

关键区别：
- **回退前（#11354）**：在循环外先调用 `JsonUtil.getString(OPERATION, sNode)`，该方法在字段缺失时抛出 `IllegalArgumentException`。这意味着 summary 中必须有 `operation` 键，否则解析失败。
- **回退后（本提交）**：在循环内遍历字段时，只有遇到名为 `operation` 的字段才调用 `JsonUtil.getString` 提取。如果 summary 中没有 `operation` 键，`operation` 变量保持为初始值（null），不报错，解析继续。其余非 `operation` 字段正常加入 summary map。

这一恢复确保了不含 `operation` 键的旧 Snapshot 元数据仍可被正常解析。

### `core/src/test/java/org/apache/iceberg/TestSnapshotJson.java`

**修改目的**：删除 PR #11354 新增的三个测试方法及相关 import。

**工作逻辑**：删除以下内容：

1. **删除 import 语句**：
   - `import static org.assertj.core.api.Assertions.assertThatThrownBy;`
   - `import com.fasterxml.jackson.core.type.TypeReference;`
   - `import com.fasterxml.jackson.databind.JsonNode;`
   - `import com.fasterxml.jackson.databind.ObjectMapper;`
   - `import java.util.Map;`

2. **删除测试方法 `testToJsonWithoutOperation()`**：验证没有 operation 的 Snapshot 序列化后 JSON 中不包含 summary 字段。

3. **删除测试方法 `testToJsonWithOperation()`**：验证有 operation 的 Snapshot 序列化后 summary 中包含 operation 键。

4. **删除测试方法 `testJsonConversionSummaryWithoutOperationFails()`**：验证反序列化不含 `operation` 键的 summary JSON 时抛出 `IllegalArgumentException("Cannot parse missing string: operation")`。这个测试正是验证被回退的强制行为，回退后该行为不再存在，因此测试被删除。

保留的测试方法 `testJsonConversion()` 等不受影响。

## 小结

- **成效**：恢复了 `SnapshotParser` 对不含 `operation` 键的 summary 的兼容性，避免了读取历史/外部 Snapshot 元数据时的解析失败。这是一个及时的回退（原始 PR 合入后约 4 天即回退），防止了破坏性变更进入发布版本。
- **影响范围**：涉及 `core` 模块的两个文件：`SnapshotParser.java`（主代码，3 行净增）和 `TestSnapshotJson.java`（测试，79 行净删）。影响 Snapshot JSON 序列化/反序列化行为。
- **回迁到 1.4.x 的注意事项**：**关键**——如果 1.4.x 分支已合入了 PR #11354 的强制 `operation` 键逻辑，则**必须回迁此回退提交**，否则 1.4.x 将无法读取不含 `operation` 键的旧 Snapshot，这是严重的向后兼容性问题。如果 1.4.x 从未合入 #11354，则无需回迁（本提交对 1.4.x 是无操作）。需检查 1.4.x 分支的 `SnapshotParser.java` 是否包含 `JsonUtil.getString(OPERATION, sNode)` 在循环外的强制提取逻辑来判断。这是一个涉及核心数据解析的改动，回迁时需仔细验证。
