# 提交 0499：Build: Bump io.airlift:aircompressor from 0.25 to 0.26 (#9700)

## 提交信息

- **序号**：0499 / 4088
- **哈希**：053d54172fd903be9eda78957f3cbd3aadef1f7b
- **短哈希**：053d54172
- **日期**：2024-02-11 20:55:48 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.airlift:aircompressor from 0.25 to 0.26 (#9700)
- **PR/Issue**：#9700

## 总体目的

这个提交是由 Dependabot 自动生成的依赖版本升级，将 Iceberg 项目所使用的 Airlift Aircompressor 库从 0.25 升级到 0.26。Aircompressor 是 Airlift 项目提供的一个压缩编解码库，支持多种压缩算法（如 Zstd、LZ4、Snappy、Brotli 等），Iceberg 使用它来对数据文件进行压缩和解压缩操作。

Iceberg 作为数据湖表格式，其数据文件（Parquet、ORC、Avro）以及元数据文件（JSON 格式的 metadata、manifest 文件等）在存储时通常采用压缩以节省存储空间和 I/O 带宽。Aircompressor 提供的压缩算法实现是 Iceberg 在读写数据路径上的重要依赖之一，其性能和正确性直接影响数据读写的效率与可靠性。

本次升级属于次版本（minor）升级，从 0.25 到 0.26。根据语义化版本规范，次版本升级通常包含向后兼容的新功能添加和改进，不引入破坏性 API 变更。相比补丁版本升级，次版本升级可能包含更实质性的改进，例如新增压缩算法支持、性能优化、或对新版本原生库的绑定更新。对于 Aircompressor 这类底层压缩库，版本升级通常涉及对底层原生压缩库（如 zstd-jni、lz4-java）的版本绑定更新，从而获得更好的压缩率和解压性能。

值得注意的是，Dependabot 在 PR 描述中只提供了 Commits 对比链接，没有提供 Release notes 和 Changelog 链接，这是因为 Aircompressor 项目的 GitHub 仓库没有维护正式的发布说明页面，维护者需要通过对比 commit 列表来审查变更。保持 Aircompressor 的版本更新有助于 Iceberg 获得最新的压缩算法优化和 bug 修复，对数据读写路径的性能有积极影响。

## 如何达成设计目的

实现路径是修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将其中 `aircompressor` 版本常量从 `0.25` 改为 `0.26`。Iceberg 使用 Gradle 的版本目录机制集中管理所有依赖版本，在该 TOML 文件中定义版本别名，各模块的 `build.gradle` 通过别名引用，因此只需修改一处即可统一升级所有引用 Aircompressor 的模块。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Aircompressor 依赖版本从 0.25 升级到 0.26。

**工作逻辑**：

文件中相关行（位于版本声明的字母序位置，`antlr` 之后、`arrow` 之前）从：

```toml
aircompressor = "0.25"
```

改为：

```toml
aircompressor = "0.26"
```

`gradle/libs.versions.toml` 是 Gradle 的版本目录文件，用于集中声明项目所有依赖的版本。在该文件中，`aircompressor = "0.25"` 定义了一个名为 `aircompressor` 的版本常量，后续在 `[libraries]` 段中通过 `module = { module = "io.airlift:aircompressor", version.ref = "aircompressor" }` 的方式引用。Iceberg 的核心模块（`core`）以及各集成模块在读写数据时通过该别名引入 Aircompressor，用于对数据进行压缩和解压缩。修改这一处版本常量，Gradle 会在构建时统一解析为 0.26，所有引用该别名的模块同步升级，确保版本一致。这种集中管理方式使 Dependabot 能以最小改动完成全项目范围的依赖升级。

## 小结

本提交是 Dependabot 自动发起的生产依赖次版本升级，将 Airlift Aircompressor 压缩库从 0.25 升级到 0.26。改动仅涉及 Gradle 版本目录中一行版本常量的修改，属于常规的依赖维护工作。Aircompressor 作为 Iceberg 数据读写路径上的压缩编解码依赖，保持其版本更新有助于获取压缩算法的性能优化和 bug 修复。本次为次版本升级，相比补丁升级可能包含更实质的功能改进，但遵循语义化版本规范，保持向后兼容。
