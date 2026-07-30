# 提交 3483：Kafka Connect: Fix CVE-2025-67721 in io.airlift:aircompressor by bumping to 2.0.3 (#15440)

## 提交信息

- **序号**：3483 / 4088
- **哈希**：857c1abb93867d50dd485f39a614baf92d3bc261
- **短哈希**：857c1abb93
- **日期**：2026-03-30 14:04:01 -0600
- **作者**：Robin Moffatt
- **提交说明**：Kafka Connect: Fix CVE-2025-67721 in io.airlift:aircompressor by bumping to 2.0.3 (#15440)
- **PR/Issue**：#15440

## 总体目的

修复 `io.airlift:aircompressor` 组件中的安全漏洞 CVE-2025-67721。该漏洞影响 Kafka Connect 模块（以及其他传递依赖 aircompressor 的模块）。通过将 aircompressor 从 0.27 升级到 2.0.3 来修复该漏洞。由于这是一个跨大版本升级（0.x → 2.x），为避免传递依赖中残留旧版本，还在全局 build.gradle 中添加了依赖替换规则强制使用 2.0.3。

## 如何达成设计目的

1. 在 `gradle/libs.versions.toml` 中将 aircompressor 版本从 `0.27` 升级到 `2.0.3`。
2. 在 `build.gradle` 的全局 `subprojects` 中添加 dependency substitution 规则，强制所有模块使用 aircompressor 2.0.3，防止传递依赖引入旧版本。这与已有的 lz4-java 替换规则模式一致。
3. 更新 `open-api/LICENSE` 文件中的版本引用。

## 修改详情

### `build.gradle` (+1 line)

**修改目的**：添加全局依赖替换规则强制使用 aircompressor 2.0.3。

**工作逻辑**：
```gradle
substitute module("io.airlift:aircompressor") using module(libs.aircompressor.get().toString()) because("Enforce aircompressor that contains CVE-2025-67721 fix")
```
与已有的 lz4-java 替换规则并列，确保所有子项目中传递依赖的 aircompressor 都被替换为指定版本。

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 aircompressor 版本号。

**工作逻辑**：`aircompressor = "0.27"` → `aircompressor = "2.0.3"`。

### `open-api/LICENSE` (+1/-1 lines)

**修改目的**：更新 LICENSE 文件中 aircompressor 版本引用。

**工作逻辑**：`Version: 0.27` → `Version: 2.0.3`。

## 总结

安全漏洞修复提交，将 aircompressor 从 0.27 升级到 2.0.3 以修复 CVE-2025-67721。由于是跨大版本升级，除了更新版本目录外，还在全局 build.gradle 中添加了 dependency substitution 规则，强制所有模块使用新版本，防止传递依赖引入有漏洞的旧版本。这是 Kafka Connect 模块的关键安全修复。
