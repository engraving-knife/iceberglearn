# 提交 3681：Build: Bump junit from 5.14.3 to 5.14.4 (#16271)

## 提交信息

- **序号**：3681 / 4088
- **哈希**：a6a0b8131691b089dd5ff9909d7f871bc8f3a996
- **短哈希**：a6a0b8131
- **日期**：2026-05-10 11:20:09 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit from 5.14.3 to 5.14.4 (#16271)
- **PR/Issue**：#16271

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 JUnit Jupiter 从 5.14.3 升级到 5.14.4。JUnit Jupiter 是 JUnit 5 的核心编程模型和扩展模型 API，包含 `junit-jupiter` 和 `junit-jupiter-engine` 两个组件，是 Iceberg 项目测试用例编写和执行的核心框架。

此次升级为补丁版本（patch version）升级，与提交 3679 升级 `junit-platform` 配套进行。两者共同构成了完整的 JUnit 5 测试框架升级，主要包含 bug 修复和小幅改进。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 文件中的 `junit` 版本号定义，将依赖版本从 5.14.3 升级到 5.14.4。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 junit 版本号。

**工作逻辑**：

```toml
-junit = "5.14.3"
+junit = "5.14.4"
```

仅修改 `junit` 版本字符串定义。该变量在 Gradle 构建配置中被引用，会自动应用到 `junit-jupiter` 和 `junit-jupiter-engine` 子模块的版本控制中。此升级与 #16272 中 junit-platform 的升级配套进行，两者共同升级到 5.14.4 / 1.14.4 版本，确保测试框架各组件的版本一致性。

## 总结

这是一个常规的测试框架依赖维护提交，将 JUnit Jupiter 升级到最新的补丁版本以获取 bug 修复。与之前的 junit-platform 升级配套进行，确保 JUnit 5 测试框架各组件版本一致性。
