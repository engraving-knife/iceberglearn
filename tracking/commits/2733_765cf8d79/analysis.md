# 提交 2733：Build: Bump org.immutables:value from 2.11.4 to 2.11.6

## 提交信息

- **序号**：2733 / 4088
- **哈希**：765cf8d79ce2c920f65301ef288b46a21a95f50e
- **短哈希**：765cf8d79
- **日期**：2025-10-11 22:23:05 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.11.4 to 2.11.6
- **PR/Issue**：#14301

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。Immutables 是一个 Java 代码生成库，通过注解处理器在编译期生成不可变对象（immutable value objects）的代码。Iceberg 使用 Immutables 来定义一些不可变的数据模型类，确保对象一旦创建后不可修改，提高代码的健壮性和线程安全性。

本次升级将 org.immutables:value 从 2.11.4 升级到 2.11.6，属于 semver-patch（补丁版本）升级，主要包含 bug 修复和编译器兼容性改进，不引入 API 破坏性变更。

## 如何达成设计目的

Dependabot 修改 Gradle 版本目录中的版本声明即可完成升级。Immutables 作为注解处理器在编译期工作，升级版本会影响编译期生成的代码，但对运行时无直接影响。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Immutables 依赖版本。

**工作逻辑**：将 `immutables-value = "2.11.4"` 修改为 `immutables-value = "2.11.6"`。项目中引用该版本的模块在编译时会使用新版本的注解处理器生成不可变类代码。

## 总结

这是常规的依赖维护升级，将 Immutables 从 2.11.4 升级到 2.11.6。作为 semver-patch 升级，风险很低，主要获取注解处理器的 bug 修复。该库仅在编译期工作，对运行时行为无直接影响。
