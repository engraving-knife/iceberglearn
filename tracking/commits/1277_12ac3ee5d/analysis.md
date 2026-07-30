# 提交 1277：Kafka Connect: Include third party licenses and notices in distribution (#10829)

## 提交信息

- **序号**：1277 / 4088
- **哈希**：12ac3ee5d358521a52318d276592ba9cf95e8926
- **短哈希**：12ac3ee5d
- **日期**：2024-10-24（Thu Oct 24 10:58:56 2024 -0700）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Kafka Connect: Include third party licenses and notices in distribution (#10829)
- **PR/Issue**：#10829
- **PR 提交历史备注**：开发过程中作者多次反复（"remove kite notice" 后 revert、"Only include notices and licenses for direct deps of Iceberg modules" 后 revert），最终结论是保留 kite notice、并按传递依赖完整收录所有第三方许可与声明。

## 总体目的

Apache 项目在发布二进制分发物（distribution）时，按 Apache 政策（https://www.apache.org/legal/resolved.html、https://www.apache.org/dev/licensing-howto.html）必须随分发物附带：

1. 项目自身的 `LICENSE` 文件（Apache License 2.0 全文）；
2. 项目自身的 `NOTICE` 文件（项目版权声明）；
3. 所有第三方依赖的 `LICENSE`/`NOTICE`（对于采用非 Apache 许可或带 NOTICE 义务的依赖）。

Iceberg 的 Kafka Connect 运行时分发包（`iceberg-kafka-connect-runtime`）在打包时（Gradle `distributions` 闭包）只把仓库根目录的 `$rootDir/LICENSE` 复制进 `doc/` 目录，既没有附带 `NOTICE`，也没有收录任何第三方依赖的许可与声明。这违反了 Apache 二进制分发的合规要求——Kafka Connect 运行时 jar 包含大量第三方依赖（Avro、Parquet、ORC、Hive、Kafka client、Jackson、Guava 等，许多带 NOTICE 义务或采用非 Apache 许可，如 BSD、MIT、LGPL 等）。

本提交为 `iceberg-kafka-connect-runtime` 模块新增模块本地的 `LICENSE`（1970 行，包含 Apache License 2.0 全文 + 全部第三方许可清单）与 `NOTICE`（1723 行，包含项目自身声明 + 全部第三方声明清单），并修改 `build.gradle` 让两个 distribution（`main` 与 `hive`）在打包时复制这两个模块本地文件，替代之前只复制根 `LICENSE` 的做法。这样发布的 tar/zip 就完整携带了合规所需的全部法律文件。

## 如何达成设计目的

通过"模块级 LICENSE/NOTICE + Gradle 复制路径调整"实现：

1. **新建模块级 `kafka-connect/kafka-connect-runtime/LICENSE`**：以 Apache License 2.0 全文开头，其后追加 Kafka Connect 运行时所有传递依赖涉及的第三方许可文本（按依赖分组列出，包括 BSD/MIT/LGPL/Apache 等多种许可的完整条款）。这份文件是 1970 行的大文件，覆盖全部需要随二进制分发的许可条款。
2. **新建模块级 `kafka-connect/kafka-connect-runtime/NOTICE`**：以 Iceberg 项目自身的 NOTICE 开头，其后追加每个第三方依赖的 NOTICE 片段（如 Kite 的 Cloudera 版权声明、commons-math3 的多段第三方版权声明等）。这份文件 1723 行，覆盖全部带 NOTICE 义务的依赖。
3. **修改 `build.gradle`**：把 `distributions.main.contents` 与 `distributions.hive.contents` 中 `into('doc/')` 块的 `from "$rootDir/LICENSE"` 改为 `from "$projectDir/LICENSE"` + `from "$projectDir/NOTICE"`——即从复制仓库根 LICENSE 切换为复制模块本地的 LICENSE/NOTICE，使分发物内 `doc/` 目录同时包含两个法律文件，且内容为 Kafka Connect 专属的（而非仓库根的通用 LICENSE）。

PR 提交历史中的两次 revert 反映了作者在"是否只收录 Iceberg 直接依赖"与"是否保留 Kite notice"两个问题上的反复，最终结论是按 Apache 严格合规要求——保留 Kite notice（因为其代码确实被纳入）、并收录全部传递依赖的许可（而非仅直接依赖），这与 Apache 发布流程的 best practice 一致。

## 修改详情

### `kafka-connect/build.gradle`（修改）

**修改目的**：让两个 distribution 在打包时复制模块本地的 LICENSE 与 NOTICE 而非仓库根的 LICENSE。

**工作逻辑**：`iceberg-kafka-connect-runtime` 子项目下定义了两个 Gradle distribution——`main`（标准运行时，`runtimeClasspath` 配置）与 `hive`（含 Hive 依赖的运行时，`hive` 配置）。两个 distribution 的 `contents` 闭包里都有 `into('doc/') { from "$rootDir/LICENSE" }` 块。本提交把两处都改为：

```groovy
into('doc/') {
  from "$projectDir/LICENSE"
  from "$projectDir/NOTICE"
}
```

`$projectDir` 指向 `kafka-connect/kafka-connect-runtime`，因此打包时会从该模块目录复制新增的 LICENSE 与 NOTICE 进 `doc/`。两个 distribution 共用同一份 LICENSE/NOTICE（hive 额外引入的依赖在文件中已一并覆盖）。

### `kafka-connect/kafka-connect-runtime/LICENSE`（新增，1970 行）

**修改目的**：提供 Kafka Connect 运行时分发物所需的完整 LICENSE 文件。

**工作逻辑**：文件以 Apache License 2.0 全文开头（项目自身许可），其后按依赖分组追加所有第三方依赖的许可条款全文。覆盖的典型类别包括：

- Apache License 2.0 类依赖（Avro、Parquet、ORC、Kafka client、Jackson、Guava 等）：列出依赖坐标 + 许可类型 + 完整 Apache License 2.0 引用；
- BSD/MIT 类依赖：列出完整 BSD/MIT 条款；
- LGPL 类依赖：列出完整 LGPL 条款；
- 公共领域或其它许可（如 WTFPL、CC0 等）：列出相应条款。

由于依赖众多，文件达 1970 行，确保任何下游用户分发该 jar 时都能直接引用该 LICENSE 满足合规要求。

### `kafka-connect/kafka-connect-runtime/NOTICE`（新增，1723 行）

**修改目的**：提供 Kafka Connect 运行时分发物所需的完整 NOTICE 文件。

**工作逻辑**：文件以 Iceberg 项目自身 NOTICE 开头（"Apache Iceberg / Copyright 2017-2024 The Apache Software Foundation"），随后是 Kite 的 Cloudera 版权声明（注释为 "This project includes code from Kite"），其后按 "Group / Name / Version" 分组列出每个带 NOTICE 义务的第三方依赖的 NOTICE 片段。例如 commons-math3 的 NOTICE 包含多段第三方版权（SciPy、Google、University of Chicago、Ernst Hairer、University of Tennessee 等）——这是因为 commons-math3 内部翻译了多个 Fortran 数值库的代码，按其 NOTICE 要求需要随分发物一并传递。文件 1723 行，覆盖全部带 NOTICE 义务的依赖。

## 小结

- **成效**：补齐 Kafka Connect 运行时分发物的法律合规文件，使 `iceberg-kafka-connect-runtime` 的 main 与 hive 两个 distribution 在 `doc/` 目录下同时携带完整的 LICENSE 与 NOTICE，覆盖项目自身与全部第三方传递依赖的许可与声明义务，满足 Apache 二进制分发政策。
- **影响范围**：仅 Kafka Connect 模块的 build 配置与新增法律文件，不影响代码运行时行为；不影响其它模块的分发（其它模块若有类似问题需独立处理）。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯新增 + 配置调整。需注意 1.4.x 分支的 Kafka Connect 依赖清单是否与该 LICENSE/NOTICE 描述的依赖版本一致——若 1.4.x 引入了不同的第三方依赖或不同版本，需相应更新 LICENSE/NOTICE 内容以保持合规准确（这正是为何放在模块本地而非仓库根目录的原因：每个模块的依赖集合不同）。另外文件较大（合计 3693 行），cherry-pick 时无冲突风险。
