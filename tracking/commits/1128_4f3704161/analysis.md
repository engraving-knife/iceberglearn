# 提交 1128：Build: Enable more error-prone checks (#11078)

## 提交信息

- **序号**：1128 / 4088
- **哈希**：4f3704161228efac33eaf831b2582762b21d6a28
- **短哈希**：4f3704161
- **日期**：2024-09-05（Thu Sep 5 08:19:36 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Build: Enable more error-prone checks (#11078)
- **PR/Issue**：#11078

## 总体目的

Iceberg 在 `baseline.gradle` 中通过 Gradle 的 `net.ltgt.errorprone` 插件在编译期运行 Google error-prone 静态分析，并显式声明每条规则的严重级别（`ERROR`/`OFF` 等）。此前已启用了一批规则，但仍有许多有价值的规则未启用，导致一些常见 bug 模式（错误的 `Comparable` 实现、`equals` 用 `hashCode` 比较、`instanceof` 误用、`Optional` 误用为 null、整数窄化运算、可变 public 数组、`@Override` 缺失等）无法在编译期被拦截。

本提交一次性启用 19 条额外的 error-prone 检查，全部设为 `ERROR`，让这些 bug 模式在编译期直接报错，提升整体代码质量基线。注意：这些检查都是"代码正确性/约定"类规则，不涉及风格偏好，因此设为 ERROR 是合理的。

## 如何达成设计目的

在 `baseline.gradle` 的 errorprone 配置数组中，按字母顺序在已有规则之间插入 19 条新规则，全部以 `-Xep:<RuleName>:ERROR` 形式声明。同时保持原有 `OFF` 的规则（如 `ConsistentLoggerName`、`FinalClass`、`EqualsGetClass`、`PreferCommonAnnotations` 等 Palantir 特定或会误报的规则）不变。错误级别为 ERROR 意味着触发即编译失败，强制开发者修复。

## 修改详情

### `baseline.gradle`

**修改目的**：在编译期启用更多 error-prone 静态检查并设为 ERROR。

**工作逻辑**：在 `subprojects` 的 errorprone `options` 数组里新增以下 19 条规则（均 `:ERROR`），插入位置保持数组大致字母序：

| 规则 | 作用 |
|------|------|
| `BadComparable` | 检查 `Comparable`/`Comparator` 实现中的常见错误（如 `Integer` 比较用 `==`、`compareTo` 返回常数等） |
| `BadInstanceof` | 检查 `instanceof` 左侧表达式类型与右侧不兼容等误用 |
| `CatchFail` | 检查 `catch` 块只调用 `fail()`/`assertFail` 而吞掉异常等测试反模式 |
| `EqualsUnsafeCast` | `equals` 中直接强转参数而非先 `instanceof` 判定 |
| `EqualsUsingHashCode` | `equals` 实现依赖 `hashCode`（哈希碰撞会破坏 equals 语义） |
| `ExtendsObject` | 显式 `extends Object`（冗余） |
| `GetClassOnEnum` | 在 enum 上调用 `getClass()`（应用 `==` 比较 enum） |
| `HidingField` | 子类字段隐藏父类字段 |
| `ImmutableSetForContains` | 对 `contains` 频繁调用时应使用 `ImmutableSet` 而非 `List` |
| `InconsistentCapitalization` | 同名字段/参数在不同地方大小写不一致 |
| `InconsistentHashCode` | `hashCode` 与 `equals` 不一致（如 equals 用了部分字段而 hashCode 没用） |
| `JdkObsolete` | 使用了有更优替代的过时 JDK API（如 `Vector`、`Hashtable`、`Stack`） |
| `ModifiedButNotUsed` | 修改了集合/对象但未使用结果 |
| `MutablePublicArray` | 暴露可变 public 数组字段（外部可改内部状态） |
| `NarrowCalculation` | 整型运算可能因窄化丢失精度 |
| `NarrowingCompoundAssignment` | 复合赋值（`+=` 等）隐式窄化转换 |
| `NullOptional` | 把 `Optional` 本身置为 null（破坏 Optional 语义） |
| `NullableOptional` | 给 `Optional` 标 `@Nullable` |
| `NullablePrimitive` | 给原始类型标 `@Nullable` |
| `ObjectEqualsForPrimitives` | 原始类型用 `Object.equals` 而非 `==` |
| `OrphanedFormatString` | 格式化字符串未对应 `Formatter`/`printf` 调用 |
| `Overrides` | `@Override` 缺失或位置错误 |

（注：`ModifiedButNotUsed`、`OrphanedFormatString`、`Overrides` 在 diff 末尾段一并加入，整体 19 条新增 `:ERROR`。）

由于这些规则此前未启用，启用后若有现存代码触发会直接编译失败。本提交本身只改配置，意味着提交时这些规则在当前代码库下已不触发（或在同批 PR 中已预先修复了触发的点）。

## 小结

- **成效**：编译期新增 19 条 error-prone 检查（全部 ERROR），把一批常见 bug 模式（坏 Comparable、equals/hashCode 不一致、Optional 误用、整数窄化、可变 public 数组等）前置到编译期拦截，长期可显著降低此类缺陷率。
- **影响范围**：仅 `baseline.gradle` 一个文件，新增 22 行配置（含空行），影响所有子模块的编译期检查，但不改变运行时行为；前提是当前代码已不触发这些规则。
- **回迁到 1.4.x 的注意事项**：这是构建质量基线提升，对 1.4.x 发布产物无功能影响。1.4.x 作为维护分支通常不主动收紧检查（收紧后若 1.4.x 自身代码触发会导致构建失败）。**不建议回迁**：除非 1.4.x 代码已确认无触发点。若强行回迁，需先在 1.4.x 上跑一次完整构建验证无 error-prone 报错，否则会阻塞 1.4.x 的构建。本提交与 1122（升级 error-prone 注解版本）、1126/1129（修复告警）属于同一波"启用更多检查"的准备工作，回迁时需整体评估。
