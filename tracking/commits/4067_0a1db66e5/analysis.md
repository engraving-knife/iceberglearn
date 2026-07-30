# 提交 4067：API: Fix incorrect Javadoc on ManageSnapshots tag methods (#17243)

## 提交信息

- **序号**：4067 / 4088
- **哈希**：0a1db66e54faf31dfb14dc1b9c1fb4df43e2ba12
- **短哈希**：0a1db66e5
- **日期**：2026-07-18 10:30:33 -0700
- **作者**：Eunbin Son
- **提交说明**：API: Fix incorrect Javadoc on ManageSnapshots tag methods (#17243)
- **PR/Issue**：#17243

## 总体目的

这个提交修复了 `ManageSnapshots` 接口中标签（tag）相关方法的 Javadoc 文档错误。这些错误是由于从同类的分支（branch）方法复制粘贴后未修改文案导致的。

具体有两处错误：
1. `createTag` 方法的 `@param snapshotId` 文档写的是「snapshotId for the head of the new **branch**」（新分支的头），但 `createTag` 创建的是标签而非分支，应描述为「the head of the new **tag**」。
2. `removeTag` 方法的 `@throws` 文档写的是「if the **branch** does not exist」（如果分支不存在），但 `removeTag` 删除的是标签，应描述为「if the **tag** does not exist」。

这两处错误会误导 API 使用者，使其以为 tag 方法与 branch 有关。提交由 Claude Code 辅助生成。

## 如何达成设计目的

直接修改两处 Javadoc 文案，将「branch」改为「tag」，使文档与方法实际语义一致。

## 修改详情

### `api/src/main/java/org/apache/iceberg/ManageSnapshots.java` (+2/-2 lines)

**修改目的**：修正 tag 方法的 Javadoc 文案错误。

**工作逻辑**：

`createTag` 的 `@param`：
```java
// 修改前：
@param snapshotId snapshotId for the head of the new branch.
// 修改后：
@param snapshotId snapshotId for the head of the new tag.
```

`removeTag` 的 `@throws`：
```java
// 修改前：
@throws IllegalArgumentException if the branch does not exist
// 修改后：
@throws IllegalArgumentException if the tag does not exist
```

## 总结

纯文档修复提交，修正了 `ManageSnapshots` 接口中 `createTag` 和 `removeTag` 方法的 Javadoc 文案错误——由于复制粘贴分支方法文档而遗留的「branch」字样，改为正确的「tag」。改动极小但提升了 API 文档的准确性。由 Claude Code 辅助生成。
