# 提交 3396：API: Fix javadoc of ManageSnapshots.setMaxRefAgeMs (#15642)

## 提交信息

- **序号**：3396 / 4088
- **哈希**：bb8b7434b4e21f360296d62e46599f2f8b256d42
- **短哈希**：bb8b7434b4
- **日期**：2026-03-16 13:13:25 +0100
- **作者**：Yuya Ebihara
- **提交说明**：API: Fix javadoc of ManageSnapshots.setMaxRefAgeMs (#15642)
- **PR/Issue**：#15642

## 总体目的

修复 `ManageSnapshots.setMaxRefAgeMs` 方法的 Javadoc 文档。原有的文档只提到该方法用于更新引用（reference）的保留策略，但参数描述中只写了 "branch name" 和 "tag reference"，存在不准确之处。`setMaxRefAgeMs` 方法实际上同时适用于 branch 和 tag 两种引用类型，文档应准确反映这一点。

## 如何达成设计目的

- 修改 Javadoc 描述，明确说明该方法适用于 branch 或 tag
- 修正参数 `name` 的描述，从 "branch name" 改为 "branch or tag name"
- 修正参数 `maxRefAgeMs` 的描述，从 "tag reference itself" 改为 "reference itself"，使其适用于所有引用类型

## 修改详情

### `api/src/main/java/org/apache/iceberg/ManageSnapshots.java` (+3/-3 lines)

**修改目的**：修复 `setMaxRefAgeMs` 方法的 Javadoc，使其准确描述该方法适用于 branch 和 tag 两种引用类型。

**工作逻辑**：
- 在方法描述中补充 "The reference can be a branch or a tag."
- 将 `@param name` 从 "branch name" 改为 "branch or tag name"
- 将 `@param maxRefAgeMs` 从 "retention age in milliseconds of the tag reference itself" 改为 "retention age in milliseconds of the reference itself"

## 总结

这是一个文档修复提交，修正了 `ManageSnapshots.setMaxRefAgeMs` 方法的 Javadoc，使其准确反映该方法同时适用于 branch 和 tag 引用类型的事实。改动很小，仅涉及注释文本的修改。
