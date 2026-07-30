# 提交 3959：Build: Bump org.codehaus.jettison:jettison from 1.5.5 to 1.5.6 (#16988)

## 提交信息

- **序号**：3959 / 4088
- **哈希**：ff1108756294f886863b149125508de79d93c251
- **短哈希**：ff1108756
- **日期**：2026-06-27 22:07:27 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.codehaus.jettison:jettison from 1.5.5 to 1.5.6 (#16988)
- **PR/Issue**：#16988

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 `org.codehaus.jettison:jettison` 库从 1.5.5 升级到 1.5.6。Jettison 是一个用于 JSON 与 XML 互转的 Java 库，在 Kafka Connect 模块中被强制引入以修复已知的安全漏洞。

升级类型为 `version-update:semver-patch`（补丁版本升级），通常包含 bug 修复和小改进，不引入破坏性变更。

## 如何达成设计目的

通过修改 `kafka-connect/build.gradle` 中的 `resolutionStrategy.force` 配置，将强制版本从 `1.5.5` 更新为 `1.5.6`。该配置用于覆盖传递依赖中的旧版本，确保使用安全的版本。

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：升级 jettison 强制版本。

**工作逻辑**：
```gradle
resolutionStrategy {
  force 'org.codehaus.jettison:jettison:1.5.6'  // 原为 1.5.5
  // ... 其他强制版本
}
```

## 总结

常规的依赖安全升级，将 jettison 从 1.5.5 升级到 1.5.6，仅影响 Kafka Connect 模块的运行时依赖。属于 Dependabot 自动维护工作的一部分。
