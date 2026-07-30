# 提交 0690：Flink: Move flink/v1.18 to flink/v1.19

## 提交信息
- **序号**：0690 / 4088
- **哈希**：fbcd142c5dc1ec99792ef8edc1378e3a027fecf7
- **短哈希**：fbcd142c5
- **日期**：2024-04-16 07:43:58 -0700
- **作者**：Rodrigo Meneses <rmenesespinillos@apple.com>
- **提交说明**：Flink: Move flink/v1.18 to flink/v1.19
- **PR/Issue**：无对应 PR 编号（未在 commit 中标注）

## 总体目的

本提交是 Iceberg Flink 集成模块从 Flink 1.18 升级到 Flink 1.19 的**第一步**：将整个 `flink/v1.18/` 目录机械性地重命名为 `flink/v1.19/`，目录内 286 个文件内容**完全不变**（已通过 `diff` 验证字节级一致）。

**背景**：Iceberg 为每个 Flink 大版本维护一个独立的子模块（`flink/v1.17`、`flink/v1.18`、`flink/v1.19` 等），各子模块独立编译、独立依赖特定版本的 Flink 库（通过 `gradle/libs.versions.toml` 中的 `flink118.*`、`flink119.*` 等版本目录条目区分）。当社区决定升级到新的 Flink 大版本时，通常遵循"先重命名、再恢复旧版本、最后真正适配"的三步走流程，避免把"机械重命名"和"实质性代码改动"混在一个大 PR 里造成 review 困难。

**目的**：本提交只做一件事——把 `flink/v1.18/` 目录下所有 286 个文件移动到 `flink/v1.19/`，包括 `build.gradle`、`flink-runtime/LICENSE`、`flink-runtime/NOTICE`，以及完整的 `flink/src/main/...` 和 `flink/src/test/...` 源码树。文件内容一字不改。

**这一步的成果**：经过此重命名后，`flink/v1.19/build.gradle` 通过 `git log --follow` 可以追溯到原本属于 `v1.18`、`v1.17` 的全部历史提交，新模块的"血脉"完整保留，便于后续审计和代码考古。

## 如何达成设计目的

**迁移策略：rename-first 三步走**

经查证，此次 Flink 1.18 → 1.19 升级在 main 分支上由三个连续提交完成：

1. **本提交 (`fbcd142c5`)**：执行 `git mv flink/v1.18 flink/v1.19`，所有文件原样迁移。这一步的 diff 显示 286 个 `D`（删除）+ 286 个 `A`（新增），无任何内容修改。`build.gradle` 内的 `flinkMajorVersion = '1.18'`、`libs.flink118.*` 等依赖坐标**保持不变**，意味着此提交单独无法真正"以 Flink 1.19 编译"，需要后续步骤才能让 v1.19 实际生效。
2. **下一提交 (`f761d98a1` "Recover flink/1.18 files from history")**：从 git 历史中恢复 `flink/v1.18/` 目录，让 1.18 模块继续存在（便于回溯/backport）。这样 1.18 和 1.19 在仓库中并存一段时间。
3. **再下一提交 (`b3ebcf109` "Refactoring code and properties to make Flink 1.19 to work")**：真正修改 `flink/v1.19/build.gradle` 中的依赖坐标（`libs.flink118.*` → `libs.flink119.*`）、`flinkMajorVersion` 字符串、以及任何必要的代码适配，让 v1.19 子模块真正可用。

**为什么采用这种"先 rename 再修"的策略**：

1. **保留 git 历史**：直接 `git mv` 会让 `git log --follow -- flink/v1.19/build.gradle` 顺滑地回溯到 v1.18、v1.17 时代的提交链，保留每个文件的"族谱"。如果用"删 v1.18、新建 v1.19"的方式，所有历史会断裂。
2. **降低 review 复杂度**：把 286 个文件的纯重命名（每个文件 diff 都是 0）和实质性的依赖升级代码改动分开 review，reviewer 不会被海量"假 diff"淹没，可以专注于真正有改动的提交（`b3ebcf109`）。
3. **支持双版本并存**：通过"恢复旧目录"的步骤让 1.18 仍然可用，使得发布流程可以平稳过渡，无需在迁移期间做出"非此即彼"的硬切换。
4. **可逆性**：纯重命名的提交如果出现问题，revert 极其简单，不会丢失任何代码改动。

**实际验证**：

- `git diff-tree --name-status` 显示 286 个 `D` + 286 个 `A`，总计 572 行，加上 commit hash 一行 = 573 行；
- 对比 `fbcd142c5^:flink/v1.18/build.gradle` 与 `fbcd142c5:flink/v1.19/build.gradle` 内容**完全相同**，包括 `flinkMajorVersion = '1.18'`、所有 `libs.flink118.*` 引用、所有 dependency 声明；
- `git log --follow -- flink/v1.19/build.gradle` 在此提交之后能回溯到 v1.18、v1.17 时代的完整提交历史，证实 history-following 机制工作正常。

## 修改详情

### `flink/v1.18/* → flink/v1.19/*`（286 个文件）

**修改目的**：把整个 Flink 1.18 子模块的源码树、构建脚本、LICENSE、NOTICE 等全部文件原样迁移到 `flink/v1.19/` 目录下，建立新的 v1.19 子模块目录结构。

**工作逻辑**：
- 操作等价于 `git mv flink/v1.18 flink/v1.19`；
- 286 个文件中包括：
  - `build.gradle`：子模块构建脚本（依赖坐标尚未更新）；
  - `flink-runtime/LICENSE`、`flink-runtime/NOTICE`：bundle jar 的法务文件；
  - `flink-runtime/src/integration/java/.../IcebergConnectorSmokeTest.java`：集成测试；
  - `flink/src/jmh/java/.../MapRangePartitionerBenchmark.java`：JMH 基准测试；
  - `flink/src/main/java/org/apache/iceberg/flink/...`：约 130 个核心源文件，覆盖 CatalogLoader、FlinkCatalog、FlinkSink、FlinkSource、Sink/Source 各子包、shuffle 数据统计、reader/split/assigner/enumerator 等；
  - `flink/src/main/resources/META-INF/services/...`：SPI 服务发现配置文件；
  - `flink/src/test/java/org/apache/iceberg/flink/...`：约 130 个测试文件，与 main 一一对应；
  - `flink/src/test/resources/META-INF/services/...`：测试用 SPI 配置。

**文件内容零修改**：所有 286 个文件的"diff"在 git 看来都是 0 行新增、0 行删除，只是路径前缀从 `flink/v1.18/` 变为 `flink/v1.19/`。这意味着此提交**单独无法让 v1.19 子模块编译通过 Flink 1.19 适配**——它只是建立了目录骨架，依赖升级与代码适配留待后续提交。

## 小结
- **成效**：成功完成了 v1.18 → v1.19 的目录重命名，建立了 v1.19 子模块的目录骨架并完整保留了 git history-following 能力。这是三步走升级流程的第一步，本身不修改任何代码内容。
- **影响范围**：
  - 仓库结构：增加 `flink/v1.19/` 目录（286 个文件），暂时与 `flink/v1.18/` 共存（待后续提交恢复）；
  - 构建：本提交不改变实际编译产物——`flink/v1.19/build.gradle` 仍指向 `libs.flink118.*`，需要后续 `b3ebcf109` 提交才真正切换到 Flink 1.19；
  - 测试：未触动任何测试逻辑，所有测试代码原样迁移；
  - 对用户：单独 cherry-pick 此提交意义不大，必须连同 `f761d98a1` 和 `b3ebcf109` 一起回迁才能获得"可工作的 v1.19 模块 + 仍可用的 v1.18 模块"的最终状态。
- **回迁到 1.4.x 的注意事项**：
  1. **不可单独回迁**：本提交只完成重命名，必须配套回迁后续两个提交才能形成完整可用的 v1.19 模块。建议作为"提交组"整体回迁。
  2. **依赖基础设施**：1.4.x 的 `gradle/libs.versions.toml` 必须已包含 `flink119.*` 版本目录条目，否则后续 `b3ebcf109` 类型的修改无法编译。回迁前先确认 `libs.versions.toml` 中是否已添加 Flink 1.19 依赖定义。
  3. **1.4.x 与 Flink 版本兼容矩阵**：1.4.x 通常维护特定的 Flink 版本集合。需先确认 1.4.x 是否打算支持 Flink 1.19；若仅作为 bug-fix分支不升级大版本，则本提交可不回迁。
  4. **测试基础设施**：v1.18→v1.19 的迁移伴随着 JUnit5 迁移（如 `943321ee6` "Flink: Migrate tests to JUnit5 (#10130)"），1.4.x 的 v1.18 测试基类可能仍是 JUnit4。回迁 v1.19 时需要同步回迁 JUnit5 基础设施，或保持 v1.19 在 JUnit4 上不迁移。
  5. **双版本并存策略**：1.4.x 若回迁，需要决定是否保留 v1.18 模块（即是否同时回迁 `f761d98a1` "Recover flink/1.18 files from history"）。如果 1.4.x 用户仍依赖 v1.18 模块，必须保留；否则可以只保留 v1.19。
  6. **本次提交本身的低风险**：纯重命名，几乎不会引入编译或运行时错误；主要的回迁风险在后续两个提交。
