# 提交分析：Build: Bump nessie from 0.79.0 to 0.80.0 (#10237)

## 提交信息

- **提交哈希**: 01bc864b8eb8c4ca4240af85f00f4e67c78c466e
- **短哈希**: 01bc864b8
- **作者**: dependabot[bot] (49699333+dependabot[bot]@users.noreply.github.com)
- **提交日期**: Sun Apr 28 08:04:31 2024 +0200
- **提交信息**: Build: Bump nessie from 0.79.0 to 0.80.0 (#10237)
- **影响文件**: 1 个文件，1 行新增，1 行删除

## 总体目的

由 Dependabot 自动生成的依赖版本升级，将 Nessie 版本从 0.79.0 升级到 0.80.0。Nessie 是 Iceberg 支持的目录服务之一（提供 Git 风格的版本化数据目录），保持依赖版本更新可获取 bug 修复、安全补丁和新功能。

## 如何达成设计目的

通过修改 Gradle 版本目录（version catalog）文件 `gradle/libs.versions.toml` 中的 `nessie` 版本声明，将 `0.79.0` 改为 `0.80.0`。所有引用该版本的模块会自动通过 Gradle 依赖解析获取新版本。

## 修改详情

### gradle/libs.versions.toml
- **变更**: 第 68 行，`nessie = "0.79.0"` 改为 `nessie = "0.80.0"`。
- **影响**: 该版本声明被 Nessie 相关测试和集成模块引用，升级后所有依赖 nessie 的模块将使用 0.80.0 版本。

## 小结

### 成效
- 将 Nessie 依赖从 0.79.0 升级到 0.80.0，获取上游版本的改进和修复。

### 影响范围
- 仅影响构建配置，不涉及源代码逻辑变更。
- 影响所有使用 Nessie 的模块（主要是 nessie 相关的测试和集成模块）。

### 回迁注意事项
- 此为 main 分支的依赖升级，1.4.x 分支若回迁需确认 Nessie 0.80.0 与该分支的其他依赖兼容。
- 版本升级为 minor 版本变更（0.79 -> 0.80），可能包含 API 变化，回迁后需运行 Nessie 相关测试验证兼容性。
