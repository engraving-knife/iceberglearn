# 提交 1686 998102546 分析

## 提交信息
- 哈希：998102546d5bef26402b068649f0e8490b9f6495
- 日期：2025-02-05 17:24:53 -0700
- 作者：Amogh Jahagirdar
- 消息：Bump Nessie to 0.120.5 to include updated License/Notice (#12186)

## 总体目的

本提交把 Iceberg 构建中声明的 Nessie 依赖版本从 0.102.4 升级到 0.102.5。提交消息标题写的"0.120.5"与实际改动（0.102.5）不一致，实际改动是 patch 级别的小版本升级（0.102.4 -> 0.102.5）。

升级的核心动机是获取 Nessie 0.102.5 中更新的 LICENSE / NOTICE 文件。Nessie 是 Iceberg 支持的目录服务之一（通过 `nessie` 模块集成）。Apache 发布物要求所含依赖的 LICENSE/NOTICE 必须合规且与实际打包内容一致；如果 Nessie 上游在其 LICENSE/NOTICE 中有合规性修复（例如补全遗漏的第三方版权声明），Iceberg 在打包发布时就需要同步升级到包含这些修复的 Nessie 版本，以避免发布物出现许可合规问题。

这是一次典型的"合规驱动"的依赖升级：技术上 API/行为可能没有变化，但出于许可证文件正确性的考虑必须升级。

## 如何达成设计目的

Iceberg 使用 Gradle 的版本目录（Version Catalog）集中管理依赖版本，文件为 `gradle/libs.versions.toml`。只需把 `nessie` 这一项的版本号改掉，所有引用 `libs.nessie` 的模块（如 `nessie` 模块、`nessie` 测试等）会自动使用新版本，无需逐模块修改 build.gradle。

### 修改详情

#### gradle/libs.versions.toml
`nessie` 版本声明：
- 旧值：`nessie = "0.102.4"`
- 新值：`nessie = "0.102.5"`

其余依赖版本不变。

## 小结

成效：使 Iceberg 构建依赖的 Nessie 升级到包含最新 LICENSE/NOTICE 的 0.102.5，保障 Apache 发布物的许可证合规性。影响范围仅限构建依赖版本，无代码改动，API 层面为 patch 升级，预期无破坏性变更。

回迁到 1.4.x 的注意事项：是否回迁取决于 1.4.x 分支使用的 Nessie 版本。若 1.4.x 仍使用 0.102.x 系列，则可考虑升级到 0.102.5 以获得同样的合规修复；若 1.4.x 使用的是更早的 Nessie 版本（如 0.90.x），则不能直接套用本提交，需要升级到 1.4.x 兼容且包含 LICENSE/NOTICE 修复的 Nessie 版本。这类合规升级对发布物正确性重要，建议根据 1.4.x 实际 Nessie 版本评估是否需要等效升级。注意提交标题中的"0.120.5"与实际改动"0.102.5"不符，回迁时以实际版本号为准。
