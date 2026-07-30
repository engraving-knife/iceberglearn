# 提交 0715：修复 TestGlueCatalogTable#testCreateTable 断言顺序敏感问题

## 提交信息
- **序号**：0715 / 4088
- **哈希**：837a4aab37233827ec01168bc2b835f3ffcdb4bd
- **短哈希**：837a4aab3
- **日期**：2024-04-25
- **作者**：Akira Ajisaka
- **提交说明**：AWS: Fix TestGlueCatalogTable#testCreateTable (#10221)
- **PR/Issue**：#10221

## 总体目的

本提交修复 `TestGlueCatalogTable#testCreateTable` 集成测试中的一处 flaky 断言。该断言在比对 Glue 表的 `storageDescriptor().additionalLocations()`（额外存储位置列表）与预期值时，使用了顺序敏感的 `isEqualTo` 断言。由于 Glue 返回的 `additionalLocations` 列表顺序不确定，当返回顺序与 `tableLocationProperties.values()` 的迭代顺序不一致时，断言会失败，导致集成测试间歇性报错。

修复方式是把 `isEqualTo(tableLocationProperties.values())` 改为 `containsExactlyInAnyOrderElementsOf(tableLocationProperties.values())`，即只校验"包含相同的元素集合"而不校验顺序。

## 如何达成设计目的

### Bug 成因

测试在 `GlueTestBase` 中定义了 `tableLocationProperties`，是一个包含 3 个表存储位置属性的 `ImmutableMap`：

```java
static final Map<String, String> tableLocationProperties =
    ImmutableMap.of(
        TableProperties.WRITE_DATA_LOCATION, "s3://" + testBucketName + "/writeDataLoc",
        TableProperties.WRITE_METADATA_LOCATION, "s3://" + testBucketName + "/writeMetaDataLoc",
        TableProperties.WRITE_FOLDER_STORAGE_LOCATION,
            "s3://" + testBucketName + "/writeFolderStorageLoc");
```

创建 Glue 表时，这些属性对应的 S3 路径会被写入 Glue 表 `StorageDescriptor` 的 `additionalLocations` 字段。测试随后断言 Glue 返回的 `additionalLocations` 与 `tableLocationProperties.values()` 一致。

问题在于：
1. **Glue API 不保证 `additionalLocations` 的顺序**。AWS Glue 的 `GetTable` 返回的 `additionalLocations` 是一个 `List<String>`，其元素顺序由 Glue 服务端决定，并不保证与客户端写入时的顺序一致。
2. **`ImmutableMap.values()` 的迭代顺序**虽然是稳定的（Guava 的 `ImmutableMap.of(...)` 按插入顺序迭代），但这个顺序与 Glue 返回的顺序没有必然对应关系。
3. 当两次顺序不一致时，`assertThat(actual).isEqualTo(expected)`（AssertJ 的 `isEqualTo` 对 List 做逐元素顺序敏感比较）就会失败。

### Bug 引入历史

该 bug 是在提交 b3261d07f（AWS: Migrate tests to JUnit5 #10086）迁移 JUnit5 时引入的。迁移前，原代码使用的是顺序无关的比较方式：

```java
// 迁移前（顺序无关，两端都 sorted）
Assert.assertEquals(
    "additionalLocations should match",
    tableLocationProperties.values().stream().sorted().collect(Collectors.toList()),
    response.table().storageDescriptor().additionalLocations().stream()
        .sorted()
        .collect(Collectors.toList()));
```

迁移时为了简化为 AssertJ 风格，错误地写成了顺序敏感的：
```java
// 迁移后（顺序敏感，引入 bug）
assertThat(response.table().storageDescriptor().additionalLocations())
    .isEqualTo(tableLocationProperties.values());
```

迁移丢失了原有的 `sorted()` 排序，导致断言变成顺序敏感，从而产生 flaky test。

### 修复逻辑

使用 AssertJ 的 `containsExactlyInAnyOrderElementsOf` 替换 `isEqualTo`：

```java
assertThat(response.table().storageDescriptor().additionalLocations())
    .containsExactlyInAnyOrderElementsOf(tableLocationProperties.values());
```

`containsExactlyInAnyOrderElementsOf(Iterable)` 语义为"实际集合应包含且仅包含给定 iterable 的所有元素，顺序任意"，这正是此处需要的：
- 校验 3 个预期的 S3 路径都出现在 `additionalLocations` 中（无遗漏）。
- 校验 `additionalLocations` 中没有多余元素（无多余）。
- 不校验顺序（与 Glue 服务端的非确定性顺序兼容）。

相比迁移前的 `stream().sorted().collect(...)` 写法，`containsExactlyInAnyOrderElementsOf` 更简洁、可读性更好，且语义更明确地表达了"集合相等、顺序无关"的意图。

## 修改详情

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogTable.java`
**修改目的**：将 `additionalLocations` 断言从顺序敏感改为顺序无关，消除 flaky test。
**工作逻辑**：

修改前：
```java
assertThat(response.table().storageDescriptor().additionalLocations())
    .isEqualTo(tableLocationProperties.values());
```

修改后：
```java
assertThat(response.table().storageDescriptor().additionalLocations())
    .containsExactlyInAnyOrderElementsOf(tableLocationProperties.values());
```

`isEqualTo` 对 List 做逐元素、按索引比较，顺序敏感；`containsExactlyInAnyOrderElementsOf` 只比较元素集合，顺序无关。修复后，无论 Glue 服务端返回的 `additionalLocations` 顺序如何，只要包含的 3 个 S3 路径与 `tableLocationProperties.values()` 的 3 个值一致，断言即通过。

## 小结
- **成效**：成功修复 `TestGlueCatalogTable#testCreateTable` 的 flaky 断言，消除了因 Glue 返回顺序不确定导致的间歇性测试失败。
- **影响范围**：仅影响 `aws` 模块的集成测试 `TestGlueCatalogTable#testCreateTable`，不影响任何运行时代码或产品行为。
- **回迁到 1.4.x 的注意事项**：
  - 需先确认 1.4.x 分支的 `TestGlueCatalogTable#testCreateTable` 是否已迁移到 JUnit5 + AssertJ。若 1.4.x 仍使用 JUnit4 风格的 `Assert.assertEquals` + `stream().sorted()` 写法（即迁移前版本），则 1.4.x 不存在此 bug，无需 cherry-pick。
  - 若 1.4.x 已迁移到 AssertJ 且使用了 `isEqualTo`，则应 cherry-pick 此修复。
  - 该 bug 是 JUnit5 迁移（b3261d07f）的回归，本质是迁移时简化断言丢失了顺序无关性。回迁时需注意 1.4.x 是否同时回迁了 JUnit5 迁移提交；若未回迁迁移提交，则本修复也无须回迁。
