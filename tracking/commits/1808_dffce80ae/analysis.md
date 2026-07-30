# 提交 1808：Build: Ignore README.md/LICENSE/NOTICE in all paths on CI (#12429)

## 提交信息

- **序号**：1808 / 4088
- **哈希**：dffce80ae15469a904fde91409deb8790b30c296
- **短哈希**：dffce80ae
- **日期**：2025-03-02 16:40:08 +0100
- **作者**：Manu Zhang
- **提交说明**：Build: Ignore README.md/LICENSE/NOTICE in all paths on CI (#12429)
- **PR/Issue**：#12429

## 总体目的

此提交用于扩展 Iceberg 多个 GitHub Actions CI 工作流的触发忽略规则，使 `README.md`、`LICENSE`、`NOTICE` 这三类文件在仓库任意子目录下的变更都不会触发 CI。

此前各 CI 工作流的 `paths-ignore` 列表中只忽略了仓库根目录的 `README.md`、`LICENSE`、`NOTICE`（即字面匹配这三个路径），但仓库中存在多个子模块各自的 `README.md`（如 `flink/README.md`、`spark/v3.5/README.md`、`aws/README.md` 等）以及部分子目录的 `LICENSE`/`NOTICE`。仅修改这些子目录下的 README/LICENSE/NOTICE 时，由于忽略规则只匹配根目录，CI 仍会被触发，浪费资源。

本次把忽略模式从 `README.md`/`LICENSE`/`NOTICE` 改为 `**/README.md`/`**/LICENSE`/`**/NOTICE`，其中 `**/` 是 GitHub Actions 路径过滤的通配符，匹配任意目录层级，从而让任意深度的这三类文件变更都不触发 CI。这是对提交 1799（忽略 docker 目录）思路的延续，属于 CI 触发条件的精细化优化。

## 如何达成设计目的

通过在 6 个 CI 工作流 YAML 文件的 `paths-ignore`（或 push/pull_request 的忽略路径）列表中，把 `- 'README.md'` 改为 `- '**/README.md'`，`- 'LICENSE'` 改为 `- '**/LICENSE'`，`- 'NOTICE'` 改为 `- '**/NOTICE'`，统一应用通配符前缀。6 个文件采用相同的修改模式（每文件 3 处替换）。

注意 `CONTRIBUTING.md` 与 `.gitattributes` 保持不变（仍只忽略根目录），因为它们通常只在根目录存在。

## 修改详情

### `.github/workflows/delta-conversion-ci.yml`（修改, +3/-3 lines）

**修改目的**：delta-conversion CI 忽略任意路径的 README/LICENSE/NOTICE 变更。

**工作逻辑**：在 `paths-ignore` 列表中：
- `'README.md'` → `'**/README.md'`
- `'LICENSE'` → `'**/LICENSE'`
- `'NOTICE'` → `'**/NOTICE'`
`CONTRIBUTING.md` 与 `.gitattributes` 不变。

### `.github/workflows/flink-ci.yml`（修改, +3/-3 lines）

**修改目的**：flink CI 忽略任意路径的 README/LICENSE/NOTICE 变更。

**工作逻辑**：同 delta-conversion-ci，三处路径加 `**/` 前缀。

### `.github/workflows/hive-ci.yml`（修改, +3/-3 lines）

**修改目的**：hive CI 忽略任意路径的 README/LICENSE/NOTICE 变更。

**工作逻辑**：同上，三处路径加 `**/` 前缀。

### `.github/workflows/java-ci.yml`（修改, +3/-3 lines）

**修改目的**：java CI 忽略任意路径的 README/LICENSE/NOTICE 变更。

**工作逻辑**：同上，三处路径加 `**/` 前缀。

### `.github/workflows/kafka-connect-ci.yml`（修改, +3/-3 lines）

**修改目的**：kafka-connect CI 忽略任意路径的 README/LICENSE/NOTICE 变更。

**工作逻辑**：同上，三处路径加 `**/` 前缀。

### `.github/workflows/spark-ci.yml`（修改, +3/-3 lines）

**修改目的**：spark CI 忽略任意路径的 README/LICENSE/NOTICE 变更。

**工作逻辑**：同上，三处路径加 `**/` 前缀。

## 小结

- **成效**：6 个 CI 工作流统一忽略任意子目录下的 `README.md`/`LICENSE`/`NOTICE` 变更，避免仅修改这些文档/法律文件时触发无关 CI，节省 CI 资源。
- **影响范围**：仅影响 `.github/workflows/` 下 6 个 CI 配置文件，不涉及代码、构建脚本或测试。
- **回迁到 1.4.x 的注意事项**：纯 CI 配置修改，无前置依赖，回迁无风险。注意事项：
  1. 需确认 1.4.x 分支的这 6 个 CI 工作流文件中存在对应的 `README.md`/`LICENSE`/`NOTICE` 忽略行以便定位替换；
  2. 若 1.4.x 的 CI 工作流结构与 main 有差异（如工作流文件名或忽略路径列表不同），需手动套用 `**/` 前缀逻辑；
  3. 该改动与 1799（忽略 docker 目录）是同一类 CI 优化，若 1.4.x 已回迁 1799，建议一并回迁本提交以保持 CI 忽略规则一致。建议回迁。
