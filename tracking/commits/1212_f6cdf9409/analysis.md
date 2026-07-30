# 提交 1212：Build: Forbid implicit case fall-through without a comment and enable couple more recommendable error-prone checks (#11251)

## 提交信息

- **序号**：1212 / 4088
- **哈希**：f6cdf94094e6c4a4db5e53f3127a04eb3a3a61b1
- **短哈希**：f6cdf9409
- **日期**：2024-10-04（Fri Oct 4 12:53:06 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Build: Forbid implicit case fall-through without a comment and enable couple more recommendable error-prone checks (#11251)
- **PR/Issue**：#11251

## 总体目的

Iceberg 在 `baseline.gradle` 中通过 [error-prone](https://errorprone.info/) 静态分析工具对全部子项目启用一系列代码质量检查（以 `ERROR` 级别失败构建）。本次提交有两个目标：

1. **禁止 switch 语句中无注释的隐式 fall-through**：Java 沿袭 C 的语义，`switch` 的 `case` 分支在缺少 `break` 时会"穿透"到下一个 `case`，这是常见的 bug 来源。本次启用 `FallThrough` 检查，除非代码中显式注释（如 `// fall through`）表明是有意为之，否则编译失败。
2. **启用若干其他推荐的 error-prone 检查**：包括 `ClassCanBeStatic`、`ClassNewInstance`、`Finalize`、`UnicodeEscape`、`UnnecessaryLongToIntConversion`、`UnnecessaryMethodReference`、`UseEnumSwitch` 等，覆盖内部类静态化、反射实例化、`finalize` 滥用、Unicode 转义规范、不必要的类型转换、冗余方法引用、枚举 switch 等代码质量场景。

## 如何达成设计目的

直接编辑 `baseline.gradle`，在 `subprojects` 块的 error-prone `errorprone` 配置数组中，按字母顺序插入若干新的 `-Xep:<CheckName>:ERROR` 项。所有新检查均以 `ERROR` 级别启用，违反规则将导致构建失败。无业务代码改动。

## 修改详情

### `baseline.gradle`

**修改目的**：在 error-prone 检查列表中新增 8 项以 `ERROR` 级别启用的规则。

**新增的检查项及其作用**：

- `-Xep:ClassCanBeStatic:ERROR`：当一个内部类不引用外部实例时，应声明为 `static`，避免持有外部引用造成内存泄漏与不必要的耦合。
- `-Xep:ClassNewInstance:ERROR`：禁止使用 `Class.newInstance()`（已废弃且有缺陷），应改用 `Constructor.newInstance()`。
- `-Xep:FallThrough:ERROR`：禁止 `switch` 中无注释的 case 穿透，需以 `// fall through` 等注释显式声明意图。
- `-Xep:Finalize:ERROR`：禁止重写 `finalize()`，该方法已废弃且语义不可靠。
- `-Xep:UnicodeEscape:ERROR`：要求字符串/字符字面量中的非 ASCII 字符使用 Unicode 转义（`\uXXXX`），提升可读性与可移植性。
- `-Xep:UnnecessaryLongToIntConversion:ERROR`：禁止不必要的 `long` 到 `int` 显式转换。
- `-Xep:UnnecessaryMethodReference:ERROR`：禁止可用 lambda 更清晰表达的方法引用（或反之的冗余用法）。
- `-Xep:UseEnumSwitch:ERROR`：建议对枚举类型使用 `switch` 而非 `if-else` 链。

新增项按字母位置穿插插入到既有列表中（例如 `ClassCanBeStatic` 与 `ClassNewInstance` 紧随 `CatchFail` 之后；`FallThrough` 与 `Finalize` 位于 `ExtendsObject` 与 `FinalClass` 之间；`UnicodeEscape`、`UnnecessaryLongToIntConversion`、`UnnecessaryMethodReference`、`UseEnumSwitch` 集中在列表末尾段）。

## 小结

- **成效**：构建期静态检查更严格，可有效阻止 switch 穿透、非静态内部类、`finalize`、`Class.newInstance()` 等典型代码坏味道进入仓库；新提交的 PR 若违反上述规则将直接构建失败。
- **影响范围**：仅 `baseline.gradle` 一个文件，新增 8 行配置，无业务代码、API 或文档变更。但启用后，仓库中既有的违规代码（若有）可能在下次构建时暴露为编译错误，需后续 PR 修正。
- **回迁到 1.4.x 的注意事项**：1.4.x 是已发布的维护分支，其代码基线可能存在违反这些新规则的既有代码（例如无注释的 switch fall-through、非静态内部类等）。**贸然回迁此提交到 1.4.x 极有可能导致 1.4.x 分支构建直接失败**，需要先扫描 1.4.x 全部子项目代码并修复所有违规点才能安全启用。考虑到 1.4.x 已处于维护态、新功能开发已迁移至 main，**通常不建议回迁**此构建规则收紧类提交；若必须收紧，应先在 1.4.x 上执行一次完整的 error-prone 修复扫描。
