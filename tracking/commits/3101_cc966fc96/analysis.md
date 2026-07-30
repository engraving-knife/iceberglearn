# 提交 3101：Build: Bump org.immutables:value from 2.12.0 to 2.12.1 (#15026)

## 提交信息

- **序号**：3101 / 4088
- **哈希**：cc966fc96374366b17ed39b1cbd8916438b4bb84
- **短哈希**：cc966fc96
- **日期**：2026-01-11
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.12.0 to 2.12.1 (#15026)
- **PR/Issue**：#15026

## 总体目的

该提交由 Dependabot 自动生成，将 `org.immutables:value` 从 2.12.0 升级到 2.12.1。Immutables 是一个 Java 编译期注解处理器，通过 `@Value.Immutable` 等注解在编译时生成不可变值类型（value object）的代码，常用于构建配置、数据记录与不可变 API 模型。在 Iceberg 中，`immutables-value` 作为生产依赖引入，源码中以 `@Value.Immutable` 等注解标注的类型会在编译期生成对应的 `ImmutableXxx` 构建器与实例类，提供线程安全、可缓存的不可变对象。

版本号从 2.12.0 升级到 2.12.1，属于语义版本中的 patch 级别升级（`version-update:semver-patch`）。根据提交元数据，该依赖归类为 `direct:production`。注解处理器工件的 patch 升级通常只包含代码生成逻辑的 bug 修复与边界场景改进，不改变注解契约与生成 API 形态。对 Iceberg 而言，预期影响是：编译期生成的不可变类行为保持一致，但可能修复在特定泛型/嵌套结构下生成代码的缺陷，使编译产物更稳定可靠。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中的 `immutables-value` 版本变量，从 `2.12.0` 改为 `2.12.1`。该变量被对应的 lib 坐标 `org.immutables:value` 通过 `version.ref` 引用，单点修改即生效。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 immutables-value 版本变量。

**工作逻辑**：
将 `immutables-value = "2.12.0"` 修改为 `immutables-value = "2.12.1"`。该变量被 `immutables-value = { module = "org.immutables:value", version.ref = "immutables-value" }` 引用。升级后，注解处理器在编译期处理 Iceberg 源码中 `@Value.Immutable` 等注解时使用 2.12.1 的代码生成逻辑。由于仅 patch 升级，生成类的公共 API 形态不变，运行时行为不受影响。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 Immutables 注解处理器 `org.immutables:value` 从 2.12.0 提升到 2.12.1（patch 级别）。Immutables 在 Iceberg 编译期生成不可变值类型。作为 patch 升级，预期仅含代码生成逻辑的 bug 修复，不改变生成 API 形态，对运行时行为无影响。
