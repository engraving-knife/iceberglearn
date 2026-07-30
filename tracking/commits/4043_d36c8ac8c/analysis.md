# 提交 4043：Build: Bump at.yawk.lz4:lz4-java from 1.11.0 to 1.11.1 (#17226)

## 提交信息

- **序号**：4043 / 4088
- **哈希**：d36c8ac8c112b6ec05c53217e521b460aae53314
- **短哈希**：d36c8ac8c
- **日期**：2026-07-15 18:52:12 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump at.yawk.lz4:lz4-java from 1.11.0 to 1.11.1 (#17226)
- **PR/Issue**：#17226

## 总体目的

Dependabot 自动升级提交，将 `at.yawk.lz4:lz4-java`（LZ4 压缩库的 Java 绑定）从 1.11.0 升级到 1.11.1（semver patch 补丁版本升级）。LZ4 是一种高速无损压缩算法，Iceberg 在读取 Parquet/ORC 等格式时可能用到 LZ4 解压。`at.yawk.lz4:lz4-java` 是 LZ4-Java 的一个 fork/分发。本次 patch 升级通常包含 bug 修复，属于低风险维护升级。

## 如何达成设计目的

通过 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `lz4Java` 版本变量管理。Dependabot 将该变量从 `1.11.0` 改为 `1.11.1`，引用该变量的依赖随之升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 lz4-java 版本。

**工作逻辑**：
```toml
lz4Java = "1.11.1"
```
将 `lz4Java` 变量从 `1.11.0` 改为 `1.11.1`。该变量被 LZ4 相关依赖引用。

## 总结

常规的 LZ4 Java 压缩库补丁版本升级，保持压缩解压栈基于最新补丁版本。patch 级别升级风险很低。
