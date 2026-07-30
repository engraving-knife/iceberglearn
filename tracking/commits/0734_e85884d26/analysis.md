# 提交 0734：Build: Bump com.azure:azure-sdk-bom from 1.2.22 to 1.2.23

## 提交信息
- **序号**：0734 / 4088
- **哈希**：e85884d269555a5608ba4dacc0c1d4e2278c5509
- **短哈希**：e85884d26
- **日期**：2024-04-30
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.22 to 1.2.23 (#10238)
- **PR/Issue**：#10238

## 总体目的

本提交由 Dependabot 自动生成，将 Azure SDK for Java 的 BOM（Bill of Materials）`com.azure:azure-sdk-bom` 从 `1.2.22` 升级到 `1.2.23`，属于一个 patch 版本级别的依赖升级。目的在于获取上游 Azure SDK 的 bug 修复、安全补丁和小幅改进，保持依赖处于最新稳定状态。

**背景**：

1. **Azure SDK BOM 用于统一管理 Azure SDK 各模块（如 azure-storage-blob、azure-identity、azure-cosmos 等）的版本**，确保各模块版本互相兼容。Iceberg 项目通过 `gradle/libs.versions.toml` 中的 `azuresdk-bom` 属性引用该 BOM，并在使用 Azure 相关存储/catalog 的模块（如 `azure`、`gcp` 间接依赖等）中通过 `platform("com.azure:azure-sdk-bom:${azuresdk-bom}")` 引入依赖管理。

2. **从 `1.2.22` 到 `1.2.23` 是 patch 版本升级**（ Dependabot 元数据中 `update-type: version-update:semver-patch`），按语义化版本约定，patch 升级仅包含向后兼容的 bug 修复，不含破坏性变更，风险低。

3. Dependabot 提交信息中包含了上游 release notes 和 commits 对比链接，便于审查者核对变更内容。提交由 dependabot 签名（`Signed-off-by: dependabot[bot]`），并以 `Co-authored-by` 标注 bot 身份。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `azuresdk-bom` 版本属性即可。Gradle 的版本目录（Version Catalog）机制会自动将该版本应用到所有引用 `azuresdk-bom` 的位置（即 `platform("com.azure:azure-sdk-bom:${azuresdk-bom}")`），无需修改其他文件。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 `azuresdk-bom` 版本从 `1.2.22` 升级到 `1.2.23`。

**修改统计**：1 file changed, 1 insertion(+), 1 deletion(-)

**修改内容**（位于文件第 32 行附近）：
```toml
# 改动前
azuresdk-bom = "1.2.22"
# 改动后
azuresdk-bom = "1.2.23"
```

**工作逻辑**：该属性位于版本目录的 `[versions]` 段，与 `awssdk-bom`、`avro`、`caffeine` 等其它依赖版本并列。所有通过 `${azuresdk-bom}` 引用该属性的依赖声明会自动使用新版本 `1.2.23`。由于是 BOM 管理的 patch 升级，下游各 Azure SDK 模块的 API 保持兼容，运行时行为变化极小。

**未改动部分**：文件中其它依赖版本（如 `awssdk-bom = "2.25.40"`、`awssdk-s3accessgrants = "2.0.0"`、`caffeine = "2.9.3"`、`calcite = "1.10.0"` 等）均未改动。

## 小结
- **成效**：成功完成 Azure SDK BOM 的 patch 版本升级（`1.2.22` → `1.2.23`），保持依赖最新。
- **影响范围**：影响所有使用 Azure SDK 的模块（如 `azure` 模块及其依赖链），但因是 BOM 管理的 patch 升级，API 兼容，运行时行为变化极小。对非 Azure 模块无影响。
- **回迁到 1.4.x 的注意事项**：可直接回迁，风险低。注意事项：
  1. 确认 1.4.x 分支的 `gradle/libs.versions.toml` 中 `azuresdk-bom` 当前版本（若 1.4.x 已停留在更早的 `1.2.22` 或更旧版本，可直接套用本升级；若 1.4.x 已独立升级到 `1.2.23` 或更新，则无需回迁）；
  2. patch 升级通常无需额外测试，但建议回迁后跑一遍 Azure 相关模块的集成测试（若有 Azure 凭据环境）以确保无回归；
  3. 与其它 Azure 相关依赖（如 `azure-storage-blob`、`azure-identity` 等通过 BOM 管理的模块）无版本冲突风险，BOM 会统一接管。
