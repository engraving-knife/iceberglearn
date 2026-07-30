# 提交 2481：Build: Bump org.immutables:value from 2.11.1 to 2.11.2 (#13779)

## 提交信息

- **序号**：2481 / 4088
- **哈希**：0be91dce702de8707fdecfa6fd909cf0d8dae8c9
- **短哈希**：0be91dce70
- **日期**：2025-08-10 22:14:11 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.11.1 to 2.11.2 (#13779)
- **PR/Issue**：#13779

## 总体目的

该提交由 Dependabot 自动生成，将 `org.immutables:value` 从 2.11.1 升级到 2.11.2，以获取 Immutables 注解处理库的最新补丁修复。

Immutables 是一个 Java 注解处理库，通过注解（如 `@Value.Immutable`）自动生成不可变对象类的代码。Iceberg 使用它来生成不可变的数据模型类。2.11.2 是一个补丁版本（semver-patch），包含 bug 修复，不引入破坏性变更。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 版本目录文件中，将 `immutables-value` 的版本号从 `2.11.1` 改为 `2.11.2`。这是单行版本号修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 immutables-value 版本。

**工作逻辑**：

修改前：
```toml
immutables-value = "2.11.1"
```

修改后：
```toml
immutables-value = "2.11.2"
```

在 Gradle 版本目录（Version Catalog）中更新 `immutables-value` 的版本号，所有引用该版本号的模块在构建时会自动使用新版本。

## 总结

这是一个由 Dependabot 自动生成的依赖升级提交，将 org.immutables:value 从 2.11.1 升级到 2.11.2（补丁版本）。该提交仅修改版本目录中一行版本号配置，获取库的最新 bug 修复，属于常规的依赖维护工作。
