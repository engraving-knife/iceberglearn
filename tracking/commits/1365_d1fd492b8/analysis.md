# 提交 1365：Docs: Update multi-engine support after 1.7.0 release (#11503)

## 提交信息

- **序号**：1365 / 4088
- **哈希**：d1fd492b865191c40d87795746e058f82fc14026
- **短哈希**：d1fd492b8
- **日期**：2024-11-11（Mon Nov 11 21:41:03 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Update multi-engine support after 1.7.0 release (#11503)
- **PR/Issue**：#11503

## 总体目的

Iceberg 官方文档中 `site/docs/multi-engine-support.md` 维护一张"多引擎支持矩阵"表格，列出各引擎版本（Flink、Hive、Spark、Trino 等）与 Iceberg 版本的兼容关系、生命周期阶段（Maintained/Deprecated/End of Life）以及对应的运行时 jar 下载链接。这是用户选择引擎版本与 Iceberg 版本搭配时的关键参考。

1.7.0 版本发布后，文档中部分信息已过时：
- Flink 1.17 之前标注为 Deprecated，1.7.0 发布后应进入 End of Life；
- Flink 1.20 之前标注为"1.7.0 (to be released)"且无 jar 链接，1.7.0 已正式发布需更新为实际版本与链接；
- Flink 1.17 的最新 Iceberg 支持版本应更新为 1.6.1；
- Hive 2 在 1.7.0 发布后应进入 Deprecated 阶段。

本提交根据 1.7.0 实际发布情况更新这些信息，使文档与发布状态一致。

## 如何达成设计目的

直接编辑 `site/docs/multi-engine-support.md` 中的 Flink 和 Hive 兼容性表格，更新生命周期阶段标签、最新 Iceberg 支持版本号，并将原本用占位符 `{{ icebergVersion }}` 的链接替换为具体的 1.6.1 或 1.7.0 版本号对应的 Maven jar 下载链接。

## 修改详情

### `site/docs/multi-engine-support.md`

**修改目的**：根据 1.7.0 发布更新多引擎支持矩阵。

**工作逻辑**：共 4 处修改：

1. **Flink 1.17 行**：
   - 生命周期：`Deprecated` → `End of Life`
   - 最新 Iceberg 支持版本：`1.6.0` → `1.6.1`
   - jar 链接：由占位符 `{{ icebergVersion }}` 改为具体的 `1.6.1`，URL 指向 `iceberg-flink-runtime-1.17-1.6.1.jar`

2. **Flink 1.20 行**：
   - 最新 Iceberg 支持版本：`1.7.0 (to be released)` → `{{ icebergVersion }}`（即 1.7.0，使用文档构建时替换的当前版本变量）
   - jar 链接：由 `-`（无链接）改为完整的 `iceberg-flink-runtime-1.20/{{ icebergVersion }}/iceberg-flink-runtime-1.20-{{ icebergVersion }}.jar` Maven 下载链接

3. **Hive 2 行**：
   - 生命周期：`Maintained` → `Deprecated`

4. **Flink 1.17 的 jar 链接**：从使用占位符变量 `{{ icebergVersion }}` 改为硬编码 `1.6.1`，因为 1.6.1 是该 Flink 版本支持的最终 Iceberg 版本（不再随当前版本变化）。

## 小结

- **成效**：多引擎支持矩阵与 1.7.0 发布状态对齐：Flink 1.17 标记为 End of Life 并锁定 1.6.1 jar 链接；Flink 1.20 补全 1.7.0 支持与 jar 链接；Hive 2 标记为 Deprecated。用户可据此选择正确的引擎与 Iceberg 版本组合。
- **影响范围**：仅 `site/docs/multi-engine-support.md` 一个文件，6 行变更（3 增 3 删），纯文档内容更新，无代码或构建逻辑变更。
- **回迁到 1.4.x 的注意事项**：该文档描述的是 Iceberg 整体的引擎支持矩阵，由 main 分支统一维护并随最新版本发布更新。1.4.x 作为历史维护分支，其文档快照已固化在发布时点，不会再随 main 更新。**无需回迁**。即使 1.4.x 分支的该文档较旧，也不影响其发布产物或运行时行为。
