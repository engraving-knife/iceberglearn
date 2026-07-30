# 提交 2483：Build: Bump org.assertj:assertj-core from 3.27.3 to 3.27.4 (#13777)

## 提交信息

- **序号**：2483 / 4088
- **哈希**：68a241615e104d97a86c932c381d7dfac802d769
- **短哈希**：68a241615
- **日期**：2025-08-10 22:57:53 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.assertj:assertj-core from 3.27.3 to 3.27.4 (#13777)
- **PR/Issue**：#13777

## 总体目的

该提交由 Dependabot 自动生成，将 `org.assertj:assertj-core` 从 3.27.3 升级到 3.27.4，以获取 AssertJ 测试断言库的最新补丁修复。

AssertJ 是 Java 生态中流行的流式断言库，Iceberg 项目在测试代码中广泛使用它进行断言（如 `assertThat(...)`）。3.27.4 是一个补丁版本（semver-patch），包含 bug 修复，不引入破坏性变更。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 版本目录文件中，将 `assertj-core` 的版本号从 `3.27.3` 改为 `3.27.4`。这是单行版本号修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 assertj-core 版本。

**工作逻辑**：

修改前：
```toml
assertj-core = "3.27.3"
```

修改后：
```toml
assertj-core = "3.27.4"
```

在 Gradle 版本目录中更新 `assertj-core` 的版本号，所有测试模块在构建时会自动使用新版本的 AssertJ 库。

## 总结

这是一个由 Dependabot 自动生成的依赖升级提交，将 assertj-core 从 3.27.3 升级到 3.27.4（补丁版本）。该提交仅修改版本目录中一行版本号配置，获取测试断言库的最新 bug 修复，属于常规的依赖维护工作。
