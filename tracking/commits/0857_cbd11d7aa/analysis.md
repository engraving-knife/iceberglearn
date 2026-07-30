# 提交 0857：Run Flink tests on Java 17 (#10477)

## 提交信息
- **序号**：0857 / 4088
- **哈希**：cbd11d7aa54a3b531f59a5e83411a163f9126a0c
- **短哈希**：cbd11d7aa
- **日期**：2024-06-19
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Run Flink tests on Java 17 (#10477)
- **PR/Issue**：#10477

## 总体目的

Iceberg 项目声明支持在 Java 8、11、17 三个版本下构建，仓库里大多数 CI 流水线已经在三个 JDK 上分别运行作业。但 Flink 模块的 CI（`.github/workflows/flink-ci.yml`）此前仅在 `jvm: [8, 11]` 两个版本下执行，缺少对 Java 17 的覆盖。

这与项目的多 JDK 支持承诺不一致，也意味着「本地用 Java 17 构建 + 运行 Flink 测试」是否能通过的信号缺失。该提交通过将 Flink CI 的 JVM 矩阵扩展为 `[8, 11, 17]`，把 Java 17 纳入每次 Flink 模块 CI 验证，从而保证 Flink 子模块在三个支持的 JDK 上都能持续构建并跑通测试，避免悄悄引入只对 Java 8/11 友好的依赖或代码。

## 如何达成设计目的

修改 `.github/workflows/flink-ci.yml` 的 `strategy.matrix`：
1. 将 `jvm: [8, 11]` 扩展为 `jvm: [8, 11, 17]`，让 CI 在 8、11、17 三个 JDK 上分别拉起 Flink 模块的构建与测试。
2. 同时新增 `matrix.exclude` 矩阵规则，将 `jvm: 17` 与 `flink: '1.17'` 的组合排除掉——因为 Flink 1.17 本身不支持 Java 17，强行让两者组合跑会因依赖问题失败，所以必须显式排除。

下游步骤里原来已经用 `${{ matrix.jvm }}` 之类的表达式选择 JDK 版本，因此矩阵扩展后无需改动其它部分，CI 框架会自动为新加的 JDK 17 任务创建对应的 job。`exclude` 机制是 GitHub Actions 矩阵策略的标准用法：先笛卡尔积出全部组合，再把不合法的组合从最终执行列表中剔除，这样既保留了「8/11/17 × 1.17/1.18/1.19」覆盖广度的简洁表达，又能精确排除 Flink 1.17 × Java 17 这一不支持的组合。

## 修改详情

### `.github/workflows/flink-ci.yml`
**修改目的**：将 Flink 模块 CI 的 JDK 矩阵扩展到包含 Java 17，同时排除 Flink 1.17 与 Java 17 的不兼容组合。
**工作逻辑**：
- `jvm: [8, 11]` → `jvm: [8, 11, 17]`：CI 矩阵新增 Java 17 维度。
- 新增 `exclude` 段，配 `- jvm: 17 / flink: '1.17'`，把 Flink 1.17 自身不支持 Java 17 的组合从最终 job 列表中剔除，避免无意义的 CI 失败。
- 其余步骤（`actions/setup-java` 等）原本就以 `${{ matrix.jvm }}` 取值，矩阵扩展后自动生效，无需改动。

## 小结
- **成效**：Flink 子模块 CI 从「8 + 11」升级为「8 + 11 + 17」（去掉 Flink 1.17×17），与项目声明的三版本 JDK 支持策略对齐，能够更早发现仅在高 JDK 下暴露的问题（默认字符集变更、反射受限、模块系统、JDK 内部 API 移除等）。
- **影响范围**：仅影响 CI 配置与构建矩阵，不改动任何源代码或运行时行为；新增的 Java 17 任务会消耗额外 CI 资源（净增 2 个 job：17×1.18、17×1.19）。
- **回迁注意事项**：1.4.x 分支若保留了 `.github/workflows/flink-ci.yml`，可直接 cherry-pick；但需确认该分支下 Flink 子模块的版本目录与 `flink: ['1.17', '1.18', '1.19']` 矩阵一致，如果 1.4.x 分支只维护更老或更新的 Flink 版本，需要按等价规则同步更新 `flink` 列表与 `exclude` 中的「Flink 版本 × Java 17」不兼容组合。同时要确认仓库内的 Flink 测试代码在 Java 17 下确实能通过（依赖、JVM 参数、模块路径、反射访问等），否则 CI 矩阵扩展可能立刻翻红。
