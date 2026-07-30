# 提交 1115：Build: Ignore benchmark output folders across all modules (#11030)

## 提交信息

- **序号**：1115 / 4088
- **哈希**：3c018333b6f108b5811d56fa2f07b1d8e29faeac
- **短哈希**：3c018333b
- **日期**：2024-08-28（Wed Aug 28 16:50:57 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Build: Ignore benchmark output folders across all modules (#11030)
- **PR/Issue**：#11030

## 总体目的

Iceberg 在 `core`、`spark/v3.4`、`spark/v3.5` 等多个模块下提供 JMH 基准测试，运行后会在各模块的 `benchmark/` 目录输出结果文件（如 `benchmark/append-benchmark.txt`）。原 `.gitignore` 中针对 benchmark 输出的忽略规则是逐模块硬编码：

```
spark/v3.4/spark/benchmark/*
spark/v3.4/spark-extensions/benchmark/*
spark/v3.5/spark/benchmark/*
spark/v3.5/spark-extensions/benchmark/*
data/benchmark/*
```

这种写法存在两个问题：

1. **遗漏**：每当新增模块（如 `core`、`flink/*`）或新增 benchmark 子目录（如 `spark-extensions` 之外的新扩展模块），都需要手动追加一行忽略规则；提交 1116 紧接着为 `core` 模块新增了 `AppendBenchmark`，输出会落到 `core/benchmark/`，原规则未覆盖。
2. **维护成本**：列表会随模块增长无限膨胀。

本提交将逐条硬编码替换为通配符 `*/benchmark/*`，使任意一级子目录下的 `benchmark/` 输出文件夹都被自动忽略，未来新增模块无需再修改 `.gitignore`。

## 如何达成设计目的

直接修改 `.gitignore` 中第 40 行那条 `data/benchmark/*`，将其改为 `*/benchmark/*`。其他逐模块的 `spark/v3.x/.../benchmark/*` 行保持不变（仍生效，未被删除），新规则作为兜底覆盖其余所有模块。这是仓库根级 `.gitignore` 的一行替换，无任何代码或构建脚本逻辑改动。

## 修改详情

### `.gitignore`

**修改目的**：把 benchmark 输出文件夹的忽略规则改为通配，覆盖所有模块。

**工作逻辑**：将原第 40 行

```
data/benchmark/*
```

替换为：

```
*/benchmark/*
```

- `*/benchmark/*` 中的第一个 `*` 匹配仓库根下任意一级子目录（如 `core`、`data`、`flink/v1.20`、`spark/v3.5/spark-extensions` 等），随后匹配该子目录下的 `benchmark/` 目录及其内容。
- 该改动与既有的 `spark/v3.4/spark/benchmark/*` 等具体规则并存，不冲突。
- 修改后，新增 `core/benchmark/`、`flink/v1.20/flink-extensions/benchmark/` 等都会被自动忽略，避免开发者误提交基准测试输出。

## 小结

- **成效**：通过一行通配规则替代逐模块硬编码，benchmark 输出文件夹现可在所有模块下被自动忽略，新增模块无需再修改 `.gitignore`，降低维护成本并防止误提交。
- **影响范围**：1 个文件、1 增 1 删，纯仓库忽略规则调整，无任何代码或构建逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是仓库基础设施的小幅改进，与版本功能无关，对 1.4.x 运行时无任何影响。回迁价值低，**通常无需回迁**；若 1.4.x 也常运行 benchmark 且担心误提交，可顺手回迁这一行 `.gitignore` 改动，风险极低（仅影响 git 忽略行为，不影响构建产物）。
