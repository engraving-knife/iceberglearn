# 提交 1813：Build: Bump jackson-bom from 2.18.2 to 2.18.3 (#12434)

## 提交信息

- **序号**：1813 / 4088
- **哈希**：1ab3ef882454c71efb3eb04ae185729608c95e67
- **短哈希**：1ab3ef882
- **日期**：2025-03-03 12:54:50 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.18.2 to 2.18.3 (#12434)
- **PR/Issue**：#12434

## 总体目的

这是一个由 dependabot 自动生成的依赖版本升级提交。该提交将 `jackson-bom` 从 2.18.2 升级到 2.18.3，涉及多个 Jackson 制品，包括 `jackson-bom`、`jackson-core`、`jackson-databind`、`jackson-annotations` 以及若干 Jackson 模块（如 jackson-datatype-jsr310、jackson-jaxrs 等）。

Jackson 是 Java 生态中最主流的 JSON 处理库，Iceberg 在元数据序列化、REST Catalog 通信等场景中广泛使用 Jackson。保持 Jackson 处于最新版本有助于获得 JSON 处理的缺陷修复、性能改进与安全补丁。此次升级属于 semver-patch 级别（补丁版本升级），按照语义化版本约定，2.18.3 相对于 2.18.2 仅包含向后兼容的缺陷修复。

此外，该提交还同步更新了多个 LICENSE 和 NOTICE 文件中记录的 Jackson 版本号，以保持法务文件与实际依赖版本的一致性。

## 如何达成设计目的

dependabot 通过修改 `gradle/libs.versions.toml` 版本目录文件中的版本变量声明，将 `jackson-bom` 从 `2.18.2` 改为 `2.18.3`。jackson-bom 作为 BOM（Bill of Materials），统一控制所有 Jackson 制品的版本，因此升级 bom 变量即可联动升级全部 Jackson 制品。同时更新了 gcp-bundle、kafka-connect 运行时下 LICENSE 与 NOTICE 文件中记录的各 Jackson 制品版本号。

## 修改详情

### gradle/libs.versions.toml (修改, 1 line)

修改了版本目录中的 `jackson-bom = "2.18.2"` 为 `jackson-bom = "2.18.3"`。该变量位于版本目录的 `[versions]` 块中，通过 BOM 机制统一管理所有 Jackson 制品版本。

### gcp-bundle/LICENSE (修改, 1 line)

将 jackson-core 版本声明从 2.18.2 更新为 2.18.3。

### gcp-bundle/NOTICE (修改, 1 line)

将 jackson-core 版本声明从 2.18.2 更新为 2.18.3。

### kafka-connect/kafka-connect-runtime/hive/LICENSE (修改, 多行)

将 jackson-annotations、jackson-core、jackson-databind、jackson-datatype-jsr310、jackson-jaxrs-base、jackson-jaxrs-json-provider、jackson-module-jaxb-annotations 等多个 Jackson 制品的版本从 2.18.2 更新为 2.18.3。

### kafka-connect/kafka-connect-runtime/hive/NOTICE (修改, 多行)

同步更新上述 Jackson 制品的 NOTICE 版本声明。

### kafka-connect/kafka-connect-runtime/main/LICENSE (修改, 多行)

将 jackson-annotations、jackson-core、jackson-databind、jackson-datatype-jsr310 等制品版本从 2.18.2 更新为 2.18.3。

### kafka-connect/kafka-connect-runtime/main/NOTICE (修改, 多行)

同步更新上述 Jackson 制品的 NOTICE 版本声明。

## 小结

这是一个低风险的依赖补丁版本升级，核心变更仅版本目录一行，附带较多 LICENSE/NOTICE 文件的法务声明同步。回迁到 1.4.x 分支时，核心的 libs.versions.toml 变更可直接 cherry-pick；但 LICENSE/NOTICE 文件的变更需注意 1.4.x 分支可能使用不同的 Jackson 子集或不同的打包模块，需核对路径与内容是否匹配。由于是补丁升级，回迁风险极低。
