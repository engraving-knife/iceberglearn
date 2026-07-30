# 提交 1127：Docs: Fix Flink 1.20 support versions (#11065)

## 提交信息

- **序号**：1127 / 4088
- **哈希**：7830a3b938a2f7b74ada46c21d14d12a6bb9c6e0
- **短哈希**：7830a3b93
- **日期**：2024-09-04（Wed Sep 4 05:07:33 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Fix Flink 1.20 support versions (#11065)
- **PR/Issue**：#11065

## 总体目的

Iceberg 站点的多引擎支持矩阵 `site/docs/multi-engine-support.md` 用一张表展示每个 Flink 版本对应的状态、最早支持的 Iceberg 版本、最晚支持的 Iceberg 版本，以及运行时 jar 的 Maven 链接。该表格用 `{{ icebergVersion }}` 占位符（由站点构建时替换为当前文档版本）填充"最晚支持版本"和 jar 链接。

问题在于：Flink 1.20 的 Iceberg 适配（`iceberg-flink-runtime-1.20`）当时**尚未发布**，最早要等到 Iceberg 1.7.0 才会随发布而出。但表格错误地把"最晚支持版本"填成 `{{ icebergVersion }}`、jar 链接也填了指向 `{{ icebergVersion }}` 的 Maven URL，给读者造成"当前就能下载 iceberg-flink-runtime-1.20"的误导，且点击链接会 404。

本提交把 Flink 1.20 一行修正为：最早支持版本写 `1.7.0 (to be released)`，最晚支持版本与 jar 链接用 `-` 占位，明确表示尚未发布。

## 如何达成设计目的

直接修改 `site/docs/multi-engine-support.md` 中 Flink 1.20 那一行的两个单元格。把 `{{ icebergVersion }}` 和 jar URL 替换为字面量 `1.7.0 (to be released)` 与 `-`。这是纯文档修正，无代码逻辑。

## 修改详情

### `site/docs/multi-engine-support.md`

**修改目的**：纠正 Flink 1.20 支持版本信息，避免误导读者去下载尚未发布的 jar。

**工作逻辑**：单行变更，把

```
| 1.20    | Maintained      | 1.7.0                   | {{ icebergVersion }}   | [iceberg-flink-runtime-1.20](https://search.maven.org/.../iceberg-flink-runtime-1.20-{{ icebergVersion }}.jar) |
```

改为

```
| 1.20    | Maintained      | 1.7.0 (to be released)  | - | - |
```

含义：Flink 1.20 的 Iceberg 适配将从 Iceberg 1.7.0 起支持，但 1.7.0 当时还未发布；当前没有"最晚支持版本"也没有可下载的 jar，故均以 `-` 表示。待 1.7.0 正式发布后，社区可再把这行恢复为 `{{ icebergVersion }}` 与 jar 链接的形式。

## 小结

- **成效**：消除了 Flink 1.20 适配"已可用"的误导，明确标注 `to be released` 与占位 `-`，避免用户点击 404 链接。
- **影响范围**：仅 `site/docs/multi-engine-support.md` 一个文件、一行表格，无代码或构建影响。
- **回迁到 1.4.x 的注意事项**：这是站点文档修正，与 1.4.x 运行时无关。multi-engine-support.md 由 main 统一维护发布，1.4.x **无需回迁**。该修正针对的是 1.7.0 发布前的临时状态，发布后会被再次调整，回迁到 1.4.x 反而会引入与 main 不一致的过时信息。
