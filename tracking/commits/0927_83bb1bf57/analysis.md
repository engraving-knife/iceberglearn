# 提交 0927：Bump Nessie from 0.91.3 to 0.92.0 (#10689)

## 提交信息

- **序号**：0927 / 4088
- **哈希**：83bb1bf57c84b9082d81d6e1e7833d04554527d5
- **短哈希**：83bb1bf57
- **日期**：2024-07-12 11:05:59 +0200
- **作者**：Robert Stupp <snazy@snazy.de>
- **提交说明**：Bump Nessie from 0.91.3 to 0.92.0 (#10689)
- **PR/Issue**：#10689

## 总体目的

本提交将 Iceberg 构建中引用的 Nessie 版本从 0.91.3 升级到 0.92.0。Nessie 是 Iceberg 支持的 Catalog 实现之一（提供类似 Git 的数据版本管理），Iceberg 在 `gradle/libs.versions.toml` 中集中管理其版本号，并通过 Nessie 集成测试模块（`nessie`/`flink`/`spark` 等的 Nessie 集成测试）验证兼容性。定期跟进 Nessie 上游发布可以获取 bug 修复、新功能与安全补丁，并保持 Iceberg 与最新 Nessie 服务端的兼容性。

## 如何达成设计目的

实现方式很简单：仅修改 Gradle 版本目录（version catalog）`gradle/libs.versions.toml` 中 `nessie` 这一项的版本字符串，由 `0.91.3` 改为 `0.92.0`。所有引用 `nessie = { module = "...", version.ref = "nessie" }` 的依赖会自动使用新版本，无需逐个修改 build.gradle。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Nessie 依赖版本从 0.91.3 升级到 0.92.0。

**工作逻辑**：把 `[versions]` 段中的 `nessie = "0.91.3"` 改为 `nessie = "0.92.0"`。下游所有通过 `libs.nessie*` 引用的依赖会随之升级。

## 小结

- **成效**：将 Nessie 依赖升级到 0.92.0，跟进上游发布。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，+1/-1。影响 Nessie 相关依赖与集成测试模块。
- **回迁到 1.4.x 的注意事项**：属于依赖版本升级，回迁风险较低，但需在 1.4.x 上运行 Nessie 集成测试验证兼容性（Nessie 客户端与服务端版本需匹配）。若 1.4.x 已有更高版本的 Nessie 则无需回迁；若 1.4.x 仍使用 0.91.3 或更早版本，可考虑回迁以获取修复，但应确认 0.92.0 与 1.4.x 其他依赖（如 Jackson、Protobuf 等）无冲突。建议在 CI 完整跑一遍 Nessie 相关测试后再合入。
