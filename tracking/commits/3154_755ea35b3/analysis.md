# 提交 3154：Build: Bump jackson-bom from 2.20.1 to 2.21.0 (#15133)

## 提交信息

- **序号**：3154 / 4088
- **哈希**：755ea35b3734e50b99645da9ff76ac86d8aeb3c1
- **短哈希**：755ea35b3
- **日期**：2026-01-25 09:32:04 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.20.1 to 2.21.0 (#15133)
- **PR/Issue**：#15133

## 总体目的

本提交是 Dependabot 自动发起的依赖升级，将 Jackson BOM（`com.fasterxml.jackson:jackson-bom`）从 `2.20.1` 升级到 `2.21.0`，同时联动升级其管理的 `jackson-core` 与 `jackson-databind` 至 `2.21.0`。Jackson 是 Iceberg 核心的 JSON 序列化/反序列化库，广泛用于元数据文件解析、REST Catalog 协议的 JSON 编解码、配置序列化等场景。`jackson-bom` 作为 BOM（Bill of Materials）统一管理 Jackson 各构件的版本，版本声明在 `gradle/libs.versions.toml` 的 `jackson-bom` 属性中。

需要特别说明的背景是：Iceberg 为兼容不同 Spark/Flink 版本所绑定的 Jackson，在版本目录中同时维护了多个严格固定的 Jackson 版本（`jackson211`、`jackson212`、`jackson213` 等，均使用 `strictly` 富版本约束），这些是供 Spark 模块运行期对齐所用；而本次升级的 `jackson-bom` 是 Iceberg 自身核心使用的版本，与那些为兼容 Spark 而锁定的版本相互独立。本次升级为次版本（minor）升级（`version-update:semver-minor`，2.20.1 → 2.21.0），按语义化版本约定可包含新功能与向后兼容改进。Dependabot 在提交信息中附带了 `jackson-bom`、`jackson-core`、`jackson-databind` 三者的 commits 对比链接。

## 如何达成设计目的

直接在 `gradle/libs.versions.toml` 中将 `jackson-bom` 属性从 `2.20.1` 改为 `2.21.0`，所有通过 BOM 引入 Jackson 构件的配置自动跟随升级。注意 `jackson-annotations` 仍单独固定为 `2.20`，未在本次改动范围内。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 jackson-bom 版本从 2.20.1 提升到 2.21.0。

**工作逻辑**：将 `jackson-bom = "2.20.1"` 修改为 `jackson-bom = "2.21.0"`。该 BOM 统一管理 Iceberg 核心使用的 `jackson-core`、`jackson-databind` 等 Jackson 构件版本，Jackson 用于元数据与 REST Catalog 的 JSON 序列化/反序列化。升级到 2.21.0 为 semver-minor 级别，联动升级 jackson-core 与 jackson-databind，获取上游新功能与改进；与供 Spark 兼容而严格固定的 `jackson211/212/213` 相互独立，不影响那些锁定版本。由于 Jackson 是核心运行期依赖，需关注 minor 版本可能引入的行为变化，但按 semver 约定应保持向后兼容。

## 总结

本提交通过将 Jackson BOM 从 2.20.1 升级到 2.21.0，联动升级 jackson-core 与 jackson-databind，使 Iceberg 核心 JSON 序列化栈获取上游次版本改进；该升级独立于为 Spark 兼容而锁定的 Jackson 版本，属 semver-minor 级别，预期向后兼容。
