# 提交 0789：Build: Bump io.airlift:aircompressor from 0.26 to 0.27 (#10383)

## 提交信息

- **序号**：0789 / 4088
- **哈希**：ca8af31f7dc1044096964b84df6aac2732fee3fa
- **短哈希**：ca8af31f7
- **日期**：2024-05-27 12:31:07 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.airlift:aircompressor from 0.26 to 0.27 (#10383)
- **PR/Issue**：#10383
- **提交正文摘要**：Dependabot 自动生成，`dependency-type: direct:production`，`update-type: version-update:semver-minor`，链接了 airlift/aircompressor 仓库 `0.26...0.27` 的 compare 页面。

## 总体目的

由 Dependabot 自动发起的依赖版本升级，将 Airlift 的 `aircompressor` 从 `0.26` 升至 `0.27`。`aircompressor` 提供多种压缩算法（LZ4、Snappy、Zstd、Brotli、LZO 等）的纯 Java 实现，Iceberg 在 Parquet/ORC/Avro 等文件格式的读写路径中依赖它进行数据块的压缩与解压。本次为次版本号递增（minor +1，0.26 → 0.27），按语义化版本约定，次版本升级允许向后兼容的功能新增与缺陷修复，但相对补丁级升级需稍多留意潜在的 API 增补。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml`，将 `[versions]` 节中的 `aircompressor` 版本字面量从 `"0.26"` 改为 `"0.27"`。下游模块（如 `parquet`、`orc`、`core` 等使用压缩的模块）通过 `${libs.versions.aircompressor}` 引用，自动获取新版本，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `aircompressor` 版本号从 `0.26` 升级到 `0.27`。

**工作逻辑**：在 `[versions]` 节中（位于 `aliyun-sdk-oss`、`antlr` 之后，`arrow` 之前），原行
```toml
aircompressor = "0.26"
```
修改为
```toml
aircompressor = "0.27"
```
该 key 在版本目录中被各处 `${libs.versions.aircompressor}` 引用，升级后所有引用处自动指向新版本。

统计：1 file changed, 1 insertion(+), 1 deletion(-)。

## 小结

- **成效**：将 `aircompressor` 升级至 0.27，获取次版本带来的功能新增与缺陷修复（如对压缩算法的改进或新编解码器支持），保持压缩栈依赖最新。
- **影响范围**：仅依赖版本配置改动，无源码、API 改动。影响所有引用 `aircompressor` 的模块（主要为文件格式读写路径：Parquet/ORC/Avro 的压缩/解压）的构建产物依赖版本。Dependabot 标注为次版本升级，运行时预期保持兼容，但相对补丁级需略多关注。
- **回迁注意事项**：回迁到 1.4.x 分支无障碍，仅需修改同一行 `aircompressor` 版本号。`aircompressor` 0.27 对 JDK 8+ 兼容，与 1.4.x 的 JDK 基线无冲突。由于是次版本升级，回迁后建议重点运行 Parquet/ORC/Avro 相关的读写与压缩测试（如 `TestParquetCompression`、ORC 读写测试等）以验证编解码行为无回归。若 1.4.x 分支已存在对 0.26 特定行为的依赖（如某测试断言压缩后字节数），需留意 0.27 是否改变了压缩输出。
