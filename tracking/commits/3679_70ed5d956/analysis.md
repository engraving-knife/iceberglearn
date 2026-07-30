# 提交 3679：Build: Bump junit-platform from 1.14.3 to 1.14.4 (#16272)

## 提交信息

- **序号**：3679 / 4088
- **哈希**：70ed5d9566709450c60088bf61b4672f33d71278
- **短哈希**：70ed5d956
- **日期**：2026-05-09 23:57:51 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit-platform from 1.14.3 to 1.14.4 (#16272)
- **PR/Issue**：#16272

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 JUnit Platform 从 1.14.3 升级到 1.14.4。JUnit Platform 是 JUnit 5 测试框架的核心组件之一，提供了在 JVM 上启动测试框架的基础（包括测试引擎 API、启动器 API 等），是 Iceberg 项目测试基础设施的重要组成部分。

此次升级为补丁版本（patch version）升级，主要包含 bug 修复和小幅改进，涉及以下三个 JUnit Platform 子模块：
- `junit-platform-launcher`：测试启动器
- `junit-platform-suite-api`：测试套件 API
- `junit-platform-suite-engine`：测试套件引擎

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 文件中的 `junit-platform` 版本号定义，将依赖版本从 1.14.3 升级到 1.14.4。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 junit-platform 版本号。

**工作逻辑**：

```toml
-junit-platform = "1.14.3"
+junit-platform = "1.14.4"
```

仅修改 `junit-platform` 版本字符串定义。该变量在 Gradle 构建配置中被引用，会自动应用到所有 JUnit Platform 相关子模块（launcher、suite-api、suite-engine）的版本控制中。补丁版本升级确保测试基础设施保持最新，避免已知问题影响测试稳定性。

## 总结

这是一个常规的测试框架依赖维护提交，将 JUnit Platform 升级到最新的补丁版本以获取 bug 修复。这种持续的小版本升级对于保持测试基础设施的稳定性和可靠性具有重要意义。
