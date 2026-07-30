# 提交 3493：Remove v4 references from javadocs (#15851)

## 提交信息

- **序号**：3493 / 4088
- **哈希**：6103dab58f21ae6758cc8be8cea806e3b9970f48
- **短哈希**：6103dab58f
- **日期**：2026-04-01 14:22:45 -0700
- **作者**：Anoop Johnson
- **提交说明**：Remove v4 references from javadocs (#15851)
- **PR/Issue**：#15851

## 总体目的

修复 PR #15049（提交 3489）中 javadoc 的版本特定语言。Russell Spitzer 在 review 中反馈，应避免在 javadoc 中使用 "v4" 这样的版本特定描述，因为随着版本演进这些描述会过时。将三个新引入接口的 javadoc 中的 "v4" 引用移除。

## 如何达成设计目的

将三个文件中的 javadoc 注释从版本特定语言改为通用描述。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestInfo.java` (+1/-1 line)

**修改目的**：移除 javadoc 中的 v4 引用。

**工作逻辑**：
- "Summary information about a manifest referenced by a v4 root manifest entry." → "Summary information about a manifest referenced by a root manifest entry."

### `core/src/main/java/org/apache/iceberg/TrackedFile.java` (+1/-1 line)

**修改目的**：移除 javadoc 中的 v4 引用。

**工作逻辑**：
- "A content file with optional deletion vector, tracked by a v4 manifest." → "A file tracked by a manifest."

### `core/src/main/java/org/apache/iceberg/Tracking.java` (+1/-1 line)

**修改目的**：移除 javadoc 中的 v4 引用。

**工作逻辑**：
- "Tracking information for a v4 manifest entry." → "Tracking information for a manifest entry."

## 总结

代码审查反馈修复提交，移除三个新接口 javadoc 中的 "v4" 版本特定语言，改为通用描述，避免随版本演进而过时。这是对提交 3489（PR #15049）的后续清理。
