# 提交 1024：Build: Enable FormatStringAnnotation error-prone check (#10856)

## 提交信息

- **序号**：1024 / 4088
- **哈希**：87537f995450a098a87b25d72124781b4da99a47
- **短哈希**：87537f995
- **日期**：2024-08-05 09:17:42 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Build: Enable FormatStringAnnotation error-prone check (#10856)
- **PR/Issue**：#10856

## 总体目的

Iceberg 项目使用 Google 的 error-prone 静态分析工具在编译期检测常见 bug 模式。`FormatStringAnnotation` 是 error-prone 提供的一个检查项，用于校验带 `@FormatMethod` 注解的方法调用时传入的格式字符串参数是否与后续参数匹配（类似 `String.format` 的格式串校验）。

此前该检查被设置为 `WARN` 级别（参考 issue #10854），原因是这是一个新加入的检查，社区需要评估是否调整代码或永久抑制。经过评估，本提交将该检查从 `WARN` 提升为 `ERROR`，使其在编译失败时阻断构建，强制代码符合格式字符串规范。这表明社区已确认现有代码不再触发该警告（或已修复所有违规），可以将其作为强制规则。

## 如何达成设计目的

通过修改 `baseline.gradle` 中 error-prone 的配置项，将 `FormatStringAnnotation` 检查级别从 `WARN` 改为 `ERROR`，并删除了原 TODO 注释（指向 issue #10854 的待评估说明）。其余 error-prone 检查项保持不变。

## 修改详情

### `baseline.gradle`

**修改目的**：将 error-prone 的 `FormatStringAnnotation` 检查从警告级别提升为错误级别。

**工作逻辑**：在 error-prone 配置的参数列表中，原本为：

```groovy
// TODO (https://github.com/apache/iceberg/issues/10854) this is a recently added check. Figure out whether we adjust the code or suppress for good
'-Xep:FormatStringAnnotation:WARN',
```

修改后为：

```groovy
'-Xep:FormatStringAnnotation:ERROR',
```

删除了 TODO 注释和指向 issue #10854 的说明，将检查级别从 `WARN` 提升到 `ERROR`。这意味着此后任何违反 `@FormatMethod` 格式字符串校验的代码都将导致编译失败，而非仅发出警告。

## 小结

- **成效**：将 `FormatStringAnnotation` 静态检查提升为编译期错误，强化了格式字符串使用的代码质量约束，防止因格式串与参数不匹配导致的运行时 bug。
- **影响范围**：仅修改 `baseline.gradle` 一个文件，影响所有子项目的 error-prone 编译检查配置。
- **回迁到 1.4.x 的注意事项**：这是代码质量工具配置变更，回迁后可能导致 1.4.x 分支现有代码因触发该检查而编译失败。回迁前需确认 1.4.x 分支代码已无 `FormatStringAnnotation` 违规，否则需先修复违规代码或将该检查降级为 `WARN`。建议谨慎回迁，优先评估 1.4.x 代码库的合规情况。
