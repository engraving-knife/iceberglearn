# 提交 0861：Build: Sort error-prone configuration options (#10540)

## 提交信息

- **序号**：0861 / 4088
- **哈希**：a2a679f7c085ab77c215e00e091d3cea7fc57188
- **短哈希**：a2a679f7c
- **日期**：2024-06-20 15:36:38 +0200
- **作者**：Piotr Findeisen
- **提交说明**：Build: Sort error-prone configuration options (#10540)
- **PR/Issue**：#10540

## 总体目的

Iceberg 项目通过 Palantir 的 `baseline.gradle` 脚本为所有子项目统一配置 Error Prone 静态分析工具。Error Prone 的各条检查规则通过 `-Xep:<规则名>:<级别>` 的形式在 `errorproneArgs` 列表中声明，级别为 `ERROR`（视为编译错误）或 `OFF`（关闭）。随着项目演进，这个列表不断追加新规则，但缺乏统一的排列顺序，导致条目按提交时间散乱分布，难以阅读和维护。

本提交的目的是将 `baseline.gradle` 中 Error Prone 配置选项按规则名字母序重新排列，使配置列表清晰有序。这样做的动机有三点（对应提交说明）：

1. **明确新增条目的位置**：当开发者需要新增一条 Error Prone 规则时，字母序排列让"应该插在哪里"变得确定，避免随意追加在末尾造成的再次混乱。
2. **语义分组更清晰**：字母序天然将同一前缀（如多个 `Slf4j*` 规则、多个 `Prefer*` 规则）聚拢，配合既有的分组注释（如 `// specific to Palantir`、`// enforce logging conventions`），让同类规则一目了然。
3. **降低 review 与合并冲突成本**：有序列表在多人同时修改时产生的 diff 更可预测，减少合并冲突。

这是一个纯构建配置整理提交，不改变任何 Error Prone 规则的开关状态或级别，只调整声明顺序并补充/调整分组注释。

## 如何达成设计目的

实现方式非常直接：对 `baseline.gradle` 中 `errorproneArgs.addAll(...)` 调用内的所有 `-Xep:...` 条目按规则名（`-Xep:` 之后、`:` 级别之前的部分）做字母升序排序，同时保留并微调原有的分组注释，使注释与其下方条目的语义保持一致。

具体来说：
- 将原来约 18 条规则按字母序重排；
- 保留所有既有级别（`ERROR` / `OFF`）不变，即不改变任何检查的启用状态；
- 调整分组注释位置，使如 `// specific to Palantir` 注释紧跟在对应的 Palantir 专属规则（`ConsistentLoggerName`、`FinalClass`、`PreferSafeLoggableExceptions` 等）上方；
- 新增一条 `// Enforce missing override` 注释的位置随 `MissingOverride` 规则移动到字母序对应处。

由于 Error Prone 对 `errorproneArgs` 列表的顺序不敏感（它按规则名查找，与声明顺序无关），所以重排不会改变实际的静态检查行为，是纯粹的"可读性重构"。

## 修改详情

### `baseline.gradle`

**修改目的**：将 Error Prone 配置选项按规则名字母序排列，提升配置可读性与可维护性，明确新增规则的插入位置。

**工作逻辑**：该文件在第 73 行起的 `subprojects { ... }` 块内，通过 `options.errorprone.errorproneArgs.addAll(...)` 注入 Error Prone 参数。改动只涉及这一处列表内部条目的顺序调整与注释微调，前后对比要点如下：

- 排序前：条目大致按"Palantir 专属 → 日志约定 → 模式允许 → 杂项"的历史顺序排列，无字母序。
- 排序后：条目严格按规则名字母升序排列。例如 `AnnotateFormatMethod`、`CollectionUndefinedEquality`、`ConsistentLoggerName`、`DangerousThreadPoolExecutorUsage`、`EqualsGetClass`、`FinalClass`、`IntLongMath`、`LambdaMethodReference`、`LoggerEnclosingClass`、`MissingCasesInEnumSwitch`、`MissingOverride`、`MissingSummary`、`ObjectsHashCodeUnnecessaryVarargs`、`PreferSafeLoggableExceptions`、`PreferSafeLogger`、`PreferSafeLoggingPreconditions`、`PreferStaticLoggers`、`RawTypes`、`Slf4jLogsafeArgs`、`Slf4jThrowable`、`StringSplitter`、`StrictUnusedVariable`、`TypeParameterShadowing`、`TypeParameterUnusedInFormals`。
- 分组注释被重新放置到字母序对应位置，并保持语义准确：例如 `// specific to Palantir - Uses name log but we use name LOG` 注释从 `ConsistentLoggerName` 上方保留，但补充了对该规则的说明；多条 Palantir 专属规则各自上方保留 `// specific to Palantir` 注释。
- 所有规则的级别（`ERROR`/`OFF`）完全不变，仅顺序变化。

由于 Error Prone 解析参数时按规则名匹配而非按位置，这种重排对实际编译期检查结果零影响。

## 小结

- **成效**：完成 Error Prone 配置项的字母序整理，使 `baseline.gradle` 中的静态检查规则列表清晰可读，新增规则有确定的插入位置，同类规则因字母序而自然聚拢。
- **影响范围**：仅影响仓库根目录的 `baseline.gradle` 一个文件，约 18 条配置行的顺序调整与注释微调，不涉及任何源代码、不改变检查行为。
- **回迁到 1.4.x 的注意事项**：该提交是纯配置整理，零功能影响、零风险。是否回迁取决于 1.4.x 分支的 `baseline.gradle` 是否已经处于无序状态——若 1.4.x 的 Error Prone 配置列表同样散乱，回迁此整理可改善可维护性；但若 1.4.x 的配置与 main 已分叉（例如 1.4.x 仍使用不同的 Palantir baseline 版本、规则集合不同），机械 cherry-pick 可能产生不匹配的注释或顺序，需对照 1.4.x 实际规则集合手动调整。整体优先级低，可选回迁。
