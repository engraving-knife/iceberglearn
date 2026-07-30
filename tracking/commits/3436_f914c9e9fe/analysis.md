# 提交 3436：Build: Bump org.codehaus.jettison:jettison from 1.5.4 to 1.5.5 (#15719)

## 提交信息

- **序号**：3436 / 4088
- **哈希**：f914c9e9fe54ba9e8eb5e7aa7bc9c7207a0fa58c
- **短哈希**：f914c9e9fe
- **日期**：2026-03-21 23:41:51 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.codehaus.jettison:jettison from 1.5.4 to 1.5.5 (#15719)
- **PR/Issue**：#15719

## 总体目的

这是一个由 Dependabot 自动生成的依赖版本升级提交。将 `org.codehaus.jettison:jettison` 库从 1.5.4 升级到 1.5.5，这是一个补丁版本（patch）升级，通常包含 bug 修复和安全补丁，不引入破坏性变更。

Jettison 是一个用于处理 JSON 的 Java 库，它实现了 StAX API 的 JSON 版本。在 Iceberg 项目中，该依赖被 kafka-connect 模块使用。

## 如何达成设计目的

- Dependabot 自动检测到 jettison 有新版本发布
- 自动创建 PR 将版本号从 1.5.4 更新到 1.5.5
- 经过 CI 测试验证后合并

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：升级 jettison 依赖版本号。

**工作逻辑**：
- 将 jettison 的版本号从 `1.5.4` 修改为 `1.5.5`
- 这是 kafka-connect 模块构建配置中唯一变更的内容

## 总结

这是 Dependabot 自动生成的常规依赖升级提交，将 kafka-connect 模块使用的 jettison JSON 库从 1.5.4 升级到 1.5.5，属于补丁版本升级，风险较低。
