# 提交 3803：Build: Bump org.immutables:value from 2.12.1 to 2.12.2 (#16636)

## 提交信息

- **序号**：3803 / 4088
- **哈希**：fa9b3d58559a32494bc4942eef38cf1a6906d4f9
- **短哈希**：fa9b3d585
- **日期**：2026-05-30 22:53:50 -0700
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.immutables:value from 2.12.1 to 2.12.2 (#16636)
- **PR/Issue**：#16636

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目使用的 Immutables `value` 注解处理器库从 `2.12.1` 升级到 `2.12.2`。Immutables 是一个代码生成库，通过注解（如 `@Value.Immutable`）在编译期生成不可变值类，Iceberg 大量使用它来生成各种不可变模型类（如 `ImmutableViewVersion`、`ImmutableAuthConfig` 等）。这是一个 patch 级升级（2.12.1 → 2.12.2），通常只包含 bug 修复和小改进，不会引入破坏性变更，属于常规依赖维护。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中 `immutables-value` 版本有新发布，自动创建 PR 升级版本字符串。由于 Immutables 是注解处理器（annotation processor），升级后会在下次编译时按新版本生成代码，无需修改业务代码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Immutables value 库版本。

**工作逻辑**：
将版本目录中的版本声明从 `2.12.1` 改为 `2.12.2`：
```toml
-immutables-value = "2.12.1"
+immutables-value = "2.12.2"
```

## 总结

这是一次常规的 patch 级依赖升级，由 Dependabot 自动完成，风险低。升级后 Iceberg 在编译期生成的不可变值类将基于 Immutables 2.12.2，可获得上游修复。属于项目依赖治理的常态化工作。
