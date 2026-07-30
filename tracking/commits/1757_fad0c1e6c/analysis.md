# 提交 1757：Checkstyle: Apply the same generic type naming rules to interfaces and classes (#12333)

## 提交信息

- **序号**：1757 / 4088
- **哈希**：fad0c1e6c68fbc7e48b5b17c02ed9c26a2693afb
- **短哈希**：fad0c1e6c
- **日期**：2025-02-19 11:49:09 +0100
- **作者**：pvary
- **提交说明**：Checkstyle: Apply the same generic type naming rules to interfaces and classes (#12333)
- **PR/Issue**：#12333

## 总体目的

本提交旨在统一 Checkstyle 中泛型类型参数命名规则的应用范围，使其同时覆盖类和接口。

在此之前，Checkstyle 配置中只有 `ClassTypeParameterName` 模块来约束类的泛型类型参数命名（遵循 Java 风格指南：类型变量名应为单个大写字母 `[A-Z]`、大写字母加数字 `[A-Z][0-9]`，或以 `T` 结尾的驼峰命名 `[A-Z][a-zA-Z0-9]*[T]$`）。但缺少对应的 `InterfaceTypeParameterName` 模块，导致接口中的泛型类型参数命名不受同样的规则约束。

这造成了不一致：类的泛型参数必须遵循命名规范，但接口的泛型参数可以随意命名。本提交通过添加 `InterfaceTypeParameterName` 模块并使用相同的正则表达式来消除这一不一致。

## 如何达成设计目的

提交在 Checkstyle 配置文件 `.baseline/checkstyle/checkstyle.xml` 中，紧接在已有的 `ClassTypeParameterName` 模块之后，新增一个 `InterfaceTypeParameterName` 模块，使用完全相同的 `format` 属性值，确保接口和类的泛型类型参数命名遵循相同规则。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml`（修改, +3/-0 lines）

**修改目的**：为接口添加与类相同的泛型类型参数命名规则。

**工作逻辑**：在 `ClassTypeParameterName` 模块之后新增 `InterfaceTypeParameterName` 模块：

```xml
<module name="InterfaceTypeParameterName">
    <property name="format" value="(^[A-Z][0-9]?)$|([A-Z][a-zA-Z0-9]*[T]$)"/>
</module>
```

该正则表达式允许以下泛型参数命名：
- `^[A-Z][0-9]?$`：单个大写字母，可选跟一个数字（如 `T`、`E`、`K`、`V`、`T1`）
- `[A-Z][a-zA-Z0-9]*[T]$`：以大写字母开头、以 `T` 结尾的驼峰命名（如 `InputT`、`OutputT`、`RowTypeT`）

这与 `ClassTypeParameterName` 使用的规则完全一致，确保命名风格统一。

## 小结

- **成效**：统一了类和接口的泛型类型参数命名规则，使 Checkstyle 对接口中的泛型参数也执行相同的命名约束。
- **影响范围**：仅修改 Checkstyle 配置文件，影响所有后续代码提交的静态检查。如果接口中存在不符合命名规则的泛型参数，将在构建时产生 Checkstyle 错误。
- **回迁到 1.4.x 的注意事项**：回迁时需注意 1.4.x 分支中是否有接口使用了不符合此命名规则的泛型参数。如果存在违规代码，启用此规则后会导致构建失败。建议先扫描 1.4.x 分支中的接口泛型参数命名情况，修复违规项后再回迁此配置变更。
