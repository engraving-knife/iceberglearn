# 提交 0845：Build: Update NOTICE to include copyright for 2024 (#10471)

## 提交信息
- **序号**：0845 / 4088
- **哈希**：4859b552e961d84ead2e131dd085e05edcbb2453
- **短哈希**：4859b552e
- **日期**：2024-06-18
- **作者**：Cancai Cai <77189278+caicancai@users.noreply.github.com>
- **提交说明**：Build: Update NOTICE to include copyright for 2024 (#10471)
- **PR/Issue**：#10471

## 总体目的

本提交是 Apache Iceberg 项目的法律/合规性维护，目的是将仓库内所有 `NOTICE` 文件中的版权年份范围更新到包含 2024 年，使其反映当前年份，符合 Apache 软件基金会关于版权声明的发布规范。

Apache 项目的每个发布构件（artifact）都需要附带 `NOTICE` 文件，其中包含版权声明 "Copyright YYYY-YYYY The Apache Software Foundation"。ASF 规范要求版权年份范围应覆盖从项目起始到最近发布的年份。Iceberg 仓库内有多个独立的发布构件目录，每个目录都有自己的 `NOTICE` 文件：根 `NOTICE`、各 bundle 模块（`aws-bundle`、`azure-bundle`、`gcp-bundle`、`bundled-guava`）、运行时 jar 模块（`hive-runtime`、`flink/v1.17/flink-runtime`、`flink/v1.18/flink-runtime`、`flink/v1.19/flink-runtime`、`spark/v3.3/spark-runtime`、`spark/v3.4/spark-runtime`、`spark/v3.5/spark-runtime`）。本次提交把这些 `NOTICE` 文件中版权年份的终止年份统一更新为 2024。

值得注意的是，各 `NOTICE` 文件更新前的终止年份不一致：部分文件停留在 2022（根 `NOTICE`、`bundled-guava`、`flink/v1.17`、`flink/v1.18`、`flink/v1.19`、`hive-runtime`、`spark/v3.3`、`spark/v3.4`、`spark/v3.5` 的 runtime），部分已更新到 2023（`aws-bundle`、`azure-bundle`、`gcp-bundle`）。本提交把它们统一拉齐到 2024，既反映当前年份，也消除了各模块间版权年份不一致的问题。

## 如何达成设计目的

提交采用最直接的"批量文本替换"方式达成目的：

1. **统一终止年份**：将每个 `NOTICE` 文件中 `Copyright 2017-YYYY The Apache Software Foundation` 行的 `YYYY` 替换为 `2024`，起始年份 `2017` 保持不变。
2. **覆盖全部 12 个 NOTICE 文件**：根目录 1 个 + bundle 模块 4 个 + flink runtime 3 个 + spark runtime 3 个 + hive-runtime 1 个 = 共 12 个文件。
3. **不修改其它内容**：每个 `NOTICE` 文件除版权年份行外，其它内容（如 "This product includes software developed at The Apache Software Foundation (http://www.apache.org/)." 等声明）保持不变。

改动统计：12 文件、+12 / -12 行，每个文件仅改动 1 行。

## 修改详情

### `NOTICE`
**修改目的**：将根 `NOTICE` 版权年份从 `2017-2022` 更新为 `2017-2024`。
**工作逻辑**：修改 `Copyright 2017-2022 The Apache Software Foundation` 为 `Copyright 2017-2024 The Apache Software Foundation`。根 NOTICE 对应整个 iceberg 项目的发布。

### `aws-bundle/NOTICE`
**修改目的**：将版权年份从 `2017-2023` 更新为 `2017-2024`。
**工作逻辑**：修改 `Copyright 2017-2023 The Apache Software Foundation` 为 `Copyright 2017-2024 The Apache Software Foundation`。

### `azure-bundle/NOTICE`
**修改目的**：将版权年份从 `2017-2023` 更新为 `2017-2024`。
**工作逻辑**：同 aws-bundle，从 `2017-2023` 改为 `2017-2024`。

### `gcp-bundle/NOTICE`
**修改目的**：将版权年份从 `2017-2023` 更新为 `2017-2024`。
**工作逻辑**：同 aws-bundle，从 `2017-2023` 改为 `2017-2024`。

### `bundled-guava/NOTICE`
**修改目的**：将版权年份从 `2017-2022` 更新为 `2017-2024`。
**工作逻辑**：从 `2017-2022` 改为 `2017-2024`。

### `hive-runtime/NOTICE`
**修改目的**：将版权年份从 `2017-2022` 更新为 `2017-2024`。
**工作逻辑**：从 `2017-2022` 改为 `2017-2024`。

### `flink/v1.17/flink-runtime/NOTICE`
**修改目的**：将版权年份从 `2017-2022` 更新为 `2017-2024`。
**工作逻辑**：从 `2017-2022` 改为 `2017-2024`。

### `flink/v1.18/flink-runtime/NOTICE`
**修改目的**：将版权年份从 `2017-2022` 更新为 `2017-2024`。
**工作逻辑**：从 `2017-2022` 改为 `2017-2024`。

### `flink/v1.19/flink-runtime/NOTICE`
**修改目的**：将版权年份从 `2017-2022` 更新为 `2017-2024`。
**工作逻辑**：从 `2017-2022` 改为 `2017-2024`。

### `spark/v3.3/spark-runtime/NOTICE`
**修改目的**：将版权年份从 `2017-2022` 更新为 `2017-2024`。
**工作逻辑**：从 `2017-2022` 改为 `2017-2024`。

### `spark/v3.4/spark-runtime/NOTICE`
**修改目的**：将版权年份从 `2017-2022` 更新为 `2017-2024`。
**工作逻辑**：从 `2017-2022` 改为 `2017-2024`。

### `spark/v3.5/spark-runtime/NOTICE`
**修改目的**：将版权年份从 `2017-2022` 更新为 `2017-2024`。
**工作逻辑**：从 `2017-2022` 改为 `2017-2024`。

## 小结
- **成效**：将仓库内全部 12 个 `NOTICE` 文件的版权年份终止值统一更新为 2024，符合 Apache 软件基金会对发布构件版权声明的要求，同时消除了各模块间版权年份不一致的问题（此前部分模块停留在 2022，部分已更新到 2023）。
- **影响范围**：仅影响 12 个 `NOTICE` 文件（每个文件改动 1 行），不触及任何代码或构建逻辑。这些 `NOTICE` 文件会随各自模块的发布构件（jar）一同打包发布。
- **回迁注意事项**：回迁到 1.4.x 时几乎无风险，纯文本改动可直接 cherry-pick。需注意：(1) 1.4.x 分支若已删除某些模块（如不再支持 flink v1.17），cherry-pick 时会因找不到目标文件而失败，需按 1.4.x 实际存在的 `NOTICE` 文件调整。(2) 若 1.4.x 计划在 2024 年之后发布，应进一步将终止年份更新到实际发布年份（如 2025/2026），本提交只是把年份拉齐到 2024 这个时间点的快照。(3) 注意 1.4.x 上各模块版权年份的起始值是否仍为 2017，若有个别模块起始年份不同需单独处理。
