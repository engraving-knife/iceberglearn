# 提交 3886：Docs: Add guidelines for tests (#16784)

## 提交信息

- **序号**：3886 / 4088
- **哈希**：6ee0a3be308037e280ff17172a5bbd67a2a4131a
- **短哈希**：6ee0a3be3
- **日期**：2026-06-15 12:00:04 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Docs: Add guidelines for tests (#16784)
- **PR/Issue**：#16784

## 总体目的

为 Iceberg 项目的贡献文档新增测试规范指南。随着项目规模增长和贡献者增多，统一测试编码规范有助于保持代码库一致性、降低 review 成本并提升测试质量。这些规范与 JUnit 5 的现代实践和社区最佳实践保持一致。

## 如何达成设计目的

在 `site/docs/contribute.md` 文件的 Testing 章节下新增 "Conventions and recommendations" 子章节，列出三条核心测试规范。

## 修改详情

### `site/docs/contribute.md` (+6 lines)

**修改目的**：在测试章节新增规范和推荐做法。

**工作逻辑**：
在 `## Testing` 标题下新增 `### Conventions and recommendations` 子章节，包含三条规范：

```markdown
### Conventions and recommendations

- Test class names must start with `Test`, for example `TestExample`.
- Omit the `public` modifier for test classes, test methods, and lifecycle methods for newly added tests.
- Omit the `test` prefix for newly added test methods.
```

三条规范的意义：
1. **测试类名以 `Test` 开头**：便于区分测试类和生产代码，也方便构建工具和 IDE 识别
2. **省略 `public` 修饰符**：JUnit 5 不再要求测试类和方法为 public，省略可减少样板代码，与现代 Java 风格一致
3. **省略 `test` 前缀**：避免与测试类名中的 `Test` 前缀冗余，方法名应聚焦描述被测行为而非标记其为测试

## 总结

为贡献文档新增测试编码规范，要求测试类以 `Test` 开头、省略 `public` 修饰符、省略 `test` 方法前缀。这些规范与现代 JUnit 5 实践一致，有助于统一项目测试风格、降低 review 摩擦并提升新贡献者的代码质量。
