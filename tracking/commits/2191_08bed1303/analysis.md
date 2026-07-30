# 提交 2191：Build: Bump testcontainers from 1.21.0 to 1.21.1 (#13199)

## 提交信息

- **序号**：2191 / 4088
- **哈希**：08bed1303ef28e8b15bd2f395e6cb51afedffec1
- **短哈希**：08bed1303
- **日期**：2025-06-02 20:41:10 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump testcontainers from 1.21.0 to 1.21.1 (#13199)
- **PR/Issue**：#13199

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 testcontainers 库从 1.21.0 升级到 1.21.1。testcontainers 是一个用于在测试中提供轻量级、可抛弃式容器实例的 Java 库，Iceberg 项目在集成测试中使用它（包括 minio 容器、junit-jupiter 集成等）来运行依赖外部服务的测试。这是一次补丁版本（patch）升级，属于小版本维护更新，通常包含 bug 修复和小的改进，不涉及破坏性变更。Dependabot 自动检测到新版本可用后提交了此升级，以保持项目依赖的最新状态，获取上游的修复。

## 如何达成设计目的

- 在 `gradle/libs.versions.toml` 版本目录文件中，将 testcontainers 的版本号从 `1.21.0` 修改为 `1.21.1`。
- 该版本号变更会自动应用于所有引用 testcontainers 版本的依赖项（testcontainers、junit-jupiter、minio 等）。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 testcontainers 依赖版本。

**工作逻辑**：将版本目录中 testcontainers 的版本定义从 `1.21.0` 改为 `1.21.1`，这是单个版本号字符串的替换。

## 总结

这是一次常规的依赖版本升级（1.21.0 → 1.21.1），由 Dependabot 自动完成，属于补丁级维护更新，用于获取 testcontainers 上游的 bug 修复和小改进，对项目功能无影响。
