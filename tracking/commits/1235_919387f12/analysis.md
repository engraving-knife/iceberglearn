# 提交 1235：Build: Bump org.apache.datasketches:datasketches-java (#11307)

## 提交信息

- **序号**：1235 / 4088
- **哈希**：919387f12bdf449091f0a12aeda6d517290cc47c
- **短哈希**：919387f12
- **日期**：2024-10-14（Mon Oct 14 14:37:24 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.apache.datasketches:datasketches-java (#11307)
- **PR/Issue**：#11307

## 总体目的

这是 Dependabot 自动生成的依赖升级 PR。`org.apache.datasketches:datasketches-java` 是 Apache DataSketches 库，提供用于近似计算（如 distinct count、分位数、频繁项等）的随机化算法（theta sketch、HLL、kll 等）。Iceberg 在 `core` 模块使用 DataSketches 实现 manifest 中的列统计（例如 NDV——distinct values 数量），用于查询优化与规划。

本次把 `datasketches` 从 `6.0.0` 升级到 `6.1.1`，跨一个 minor 版本与一个 patch 版本。Dependabot 元信息标注 `update-type: version-update:semver-minor`，即 minor 升级。按 DataSketches 项目的版本惯例，minor 版本通常保持序列化与算法兼容，仅引入新功能或小幅修复。

## 如何达成设计目的

只修改 `gradle/libs.versions.toml` 中的 `datasketches` 版本字符串。仓库内所有依赖 datasketches 的模块（主要是 `core`）的版本号会随之统一变更。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 datasketches-java 版本。

**工作逻辑**：

```diff
-datasketches = "6.0.0"
+datasketches = "6.1.1"
```

`libs.versions.toml` 是 Gradle 版本目录，`datasketches` 这一项被 `core` 等模块通过 `org.apache.datasketches:datasketches-java:${Versions.datasketches}` 引用。修改后，引入的 datasketches 库版本会从 6.0.0 升到 6.1.1。

## 小结

- **成效**：datasketches-java 从 6.0.0 升级到 6.1.1，引入该区间内的修复与改进，预期保持 sketch 序列化兼容。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更；运行时影响 `core` 模块的列统计计算（NDV 等）。无代码改动，按 datasketches 6.x 兼容约定应可平滑升级。
- **回迁到 1.4.x 的注意事项**：1.4.x 中的 datasketches 版本可能更老（如 4.x 或 5.x）。
  - 若 1.4.x 当前是 4.x → 5.x → 6.x 跨大版本升级需重点验证 sketch 序列化兼容性：旧 manifest 中已序列化的 sketch bytes 能否被新版本读取（Iceberg 在 manifest 文件中持久化了 sketch 的二进制表示）。
  - 若 1.4.x 已是 6.0.0，则升级到 6.1.1 风险较低。
  - 建议回迁后运行 `core` 模块的 sketch 相关测试（如 `TestMetrics`、`TestManifest` 等）以及跨版本读取测试，确保读取旧快照中的 sketch 仍能得到正确结果。
