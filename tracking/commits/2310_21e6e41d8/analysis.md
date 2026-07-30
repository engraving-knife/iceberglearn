# 提交 2310：Build: Fix error-prone warning (#13447)

## 提交信息

- **序号**：2310 / 4088
- **哈希**：21e6e41d8a70c45640836240f049bb3fbe40dbeb
- **短哈希**：21e6e41d8
- **日期**：2025-07-02 18:46:30 +0200
- **作者**：Yu-Chuan Hung
- **提交说明**：Build: Fix error-prone warning (#13447)
- **PR/Issue**：#13447

## 总体目的

这个提交修复了 error-prone（静态代码分析工具）报告的 `ImplicitPublicBuilderConstructor` 警告。error-prone 是 Google 开发的 Java 编译器插件，用于在编译时检测常见的编程错误和反模式。

`ImplicitPublicBuilderConstructor` 警告指的是：当一个内部 Builder 类没有显式声明构造函数时，Java 会隐式提供一个公开的无参构造函数。对于 Builder 模式来说，这通常不是期望的行为，因为 Builder 的构造应该通过外部类工厂方法来控制，而不是直接被外部实例化。

在 `ParserContext` 类中，其内部 `Builder` 类没有显式构造函数，因此会触发此警告。修复方式是添加一个私有的无参构造函数，使构造意图更加明确，同时阻止外部直接实例化 Builder。

## 如何达成设计目的

通过在 `ParserContext.Builder` 内部类中添加一个 `private` 构造函数，明确表达该 Builder 不应被外部直接通过 `new Builder()` 的方式实例化。这是一个小而精确的代码质量改进。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ParserContext.java` (+2/-0 lines)

**修改目的**：为 `ParserContext.Builder` 内部类添加私有构造函数，消除 error-prone 的 `ImplicitPublicBuilderConstructor` 警告。

**工作逻辑**：在 `Builder` 类中添加了 `private Builder() {}`。这行代码将原本隐式的公开构造函数替换为私有的构造函数。由于该 Builder 通常通过 `ParserContext.builder()` 等工厂方法创建，将构造函数设为 private 不会影响正常使用，但确保了 Builder 实例的创建只能通过受控的工厂方法路径。

## 总结

这是一个代码质量改进提交，通过添加私有构造函数修复了 error-prone 的隐式公开构造函数警告。变更极小（仅增加 2 行），不影响功能逻辑，但提升了代码的严谨性和可维护性。
