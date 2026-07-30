# 提交 1705：Fix LICENSE and NOTICE for the kafka-connect-runtime distributions (#12195)

## 提交信息

- **序号**：1705 / 4088
- **哈希**：c277c2014a1b37fe755cfe37f173b6465bb8cb73
- **短哈希**：c277c2014
- **日期**：2025-02-10（Mon Feb 10 02:58:36 2025 +0530）
- **作者**：JB Onofré <jb.onofre@dremio.com>
- **提交说明**：Fix LICENSE and NOTICE for the kafka-connect-runtime distributions (#12195)
- **PR/Issue**：#12195

## 总体目的

`kafka-connect-runtime` 模块打包两种分发制品（distribution）：

1. **main 分发**：仅包含 `runtimeClasspath` 配置的依赖（不含 Hive）；
2. **hive 分发**：额外包含 `hive` 配置的依赖（Hive 相关 jar）。

这两种分发的依赖集合不同——hive 分发额外包含 Hive 及其传递依赖，而这些依赖大多有自己的 LICENSE/NOTICE 要求。此前该模块只有**一份** `LICENSE` 和 `NOTICE` 文件，被两种分发共用。这意味着：

- main 分发中包含了 Hive 相关依赖的许可证声明（实际上 main 分发并不打包 Hive 依赖），声明与实际内容不符；
- 或者 hive 分发中缺少 Hive 依赖的许可证声明（合规风险）；
- 总之，单一 LICENSE/NOTICE 无法同时正确描述两种不同依赖集合的分发。

本提交把单一的 `LICENSE`/`NOTICE` 拆分为 `main/` 与 `hive/` 两套，各自准确描述对应分发的依赖清单与许可证/NOTICE 文本，并更新 `build.gradle` 让两种分发的打包过程分别引用正确的 LICENSE/NOTICE。

## 如何达成设计目的

1. 删除根目录下原有的单一 `NOTICE` 文件（1723 行）；
2. 把原有的 `LICENSE` 移动到 `hive/LICENSE`（因为原有 LICENSE 实际上包含 Hive 依赖的声明，更接近 hive 分发的需求），并相应调整内容；
3. 新建 `hive/NOTICE`（668 行），包含 Hive 分发相关的完整 NOTICE 文本；
4. 新建 `main/LICENSE`（1450 行）与 `main/NOTICE`（323 行），仅包含 main 分发（不含 Hive）的依赖许可证与 NOTICE；
5. 修改 `build.gradle` 中的两个分发打包任务：
   - main 分发的 `doc/` 目录从 `$projectDir/LICENSE`/`NOTICE` 改为 `$projectDir/main/LICENSE`/`NOTICE`；
   - hive 分发的 `doc/` 目录从 `$projectDir/LICENSE`/`NOTICE` 改为 `$projectDir/hive/LICENSE`/`NOTICE`。

## 修改详情

### `kafka-connect/build.gradle`（修改，+4/-4 行）

**修改目的**：让两种分发的打包任务引用各自正确的 LICENSE/NOTICE 文件。

**工作逻辑**：

- main 分发的 `into('doc/')` 块：`from "$projectDir/LICENSE"` / `from "$projectDir/NOTICE"` → `from "$projectDir/main/LICENSE"` / `from "$projectDir/main/NOTICE"`；
- hive 分发的 `into('doc/')` 块：同上 → `from "$projectDir/hive/LICENSE"` / `from "$projectDir/hive/NOTICE"`。

### `kafka-connect/kafka-connect-runtime/NOTICE`（删除，-1723 行）

**修改目的**：删除原有的单一 NOTICE 文件，由 `main/NOTICE` 与 `hive/NOTICE` 替代。

### `kafka-connect/kafka-connect-runtime/LICENSE` → `kafka-connect/kafka-connect-runtime/hive/LICENSE`（移动+修改，+298/-276 行）

**修改目的**：把原 LICENSE 移动到 `hive/` 子目录并调整为 hive 分发的准确依赖清单。

**工作逻辑**：文件从根目录移动到 `hive/` 目录，内容更新为 hive 分发实际打包的依赖许可证清单（含 Hive 及其传递依赖）。

### `kafka-connect/kafka-connect-runtime/hive/NOTICE`（新增，668 行）

**修改目的**：为 hive 分发创建完整的 NOTICE 文件。

**工作逻辑**：包含 hive 分发中所有要求保留 NOTICE 的第三方组件的 NOTICE 文本（如 commons-math3、httpcore5、kerby、orc-core、hive-shims 等）。

### `kafka-connect/kafka-connect-runtime/main/LICENSE`（新增，1450 行）

**修改目的**：为 main 分发（不含 Hive）创建准确的 LICENSE 文件。

**工作逻辑**：仅包含 main 分发实际打包的依赖（`runtimeClasspath` 配置）的许可证清单，不含 Hive 相关依赖。

### `kafka-connect/kafka-connect-runtime/main/NOTICE`（新增，323 行）

**修改目的**：为 main 分发创建完整的 NOTICE 文件。

**工作逻辑**：仅包含 main 分发中要求保留 NOTICE 的第三方组件的 NOTICE 文本。

## 小结

- **成效**：kafka-connect-runtime 的两种分发（main/hive）现在各自拥有准确的 LICENSE 与 NOTICE 文件，消除合规风险——main 分发不再包含多余的 Hive 许可证声明，hive 分发则有完整的 Hive 相关 NOTICE 文本。
- **影响范围**：仅 `kafka-connect` 模块的构建脚本与许可证文档，无源代码变更。
- **回迁到 1.4.x 的注意事项**：纯合规性文档修复，回迁安全且必要。需确认 1.4.x 分支的 kafka-connect-runtime 是否也有 main/hive 两种分发；若是则需同样拆分 LICENSE/NOTICE。回迁后建议用 `gradle dependencies` 核对两种分发的实际依赖与 LICENSE/NOTICE 声明一致。
