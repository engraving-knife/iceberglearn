# 提交 1917：Build: Bump calcite from 1.10.0 to 1.39.0 (#12617)

## 提交信息

- **序号**：1917 / 4088
- **哈希**：80f07c02da413de79054e18d1a9a14c1ecda4ecc
- **短哈希**：80f07c02d
- **日期**：2025-03-24 19:36:29 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump calcite from 1.10.0 to 1.39.0 (#12617)
- **PR/Issue**：#12617

## 总体目的

dependabot 自动把 Apache Calcite 依赖从 1.10.0 升级到 1.39.0。该版本同时影响 `calcite-core` 与 `calcite-druid` 两个 artifact（共享版本号）。Calcite 主要用于 Iceberg 的 SQL 解析与优化（如 `spark-extensions` 中的 procedure 解析等）。这是一次跨度较大的 minor 版本升级（1.10.0 → 1.39.0），但只改了版本目录一处声明，未涉及代码适配（说明 API 兼容或 Iceberg 用到的部分未受影响）。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中把 `calcite = "1.10.0"` 改为 `"1.39.0"`，作为 calcite 版本单一来源。无 LICENSE 同步更新（calcite 未被打包进分发 bundle 的 LICENSE 清单，或本次未触发）。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 calcite 版本声明。

**工作逻辑**：

```toml
-calcite = "1.10.0"
+calcite = "1.39.0"
```

## 总结

dependabot 自动把 Apache Calcite 从 1.10.0 升级到 1.39.0，仅修改版本目录一处声明，无业务代码改动。
