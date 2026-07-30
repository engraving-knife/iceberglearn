# 提交 1770：Core: Remove additional 'Iceberg' in Puffin footer payload (#12369)

## 提交信息

- **序号**：1770 / 4088
- **哈希**：f186be7c8e419a90c4b2c4af8845e24a20b016f6
- **短哈希**：f186be7c8
- **日期**：2025-02-21 17:32:12 +0100
- **作者**：Tom Tanaka
- **提交说明**：Core: Remove additional 'Iceberg' in Puffin footer payload (#12369)
- **PR/Issue**：#12369

## 总体目的

这个提交修复了 Puffin 文件 footer 中创建者标识（created-by）信息的多余前缀问题。在修改前的代码中，`BaseDVFileWriter` 在生成 Puffin 文件时，将创建者标识构造为 `"Iceberg " + IcebergBuild.fullVersion()`。然而 `IcebergBuild.fullVersion()` 本身返回的字符串已经包含了 "Iceberg" 前缀（例如 "Apache Iceberg 1.x.x"），因此最终生成的 created-by 字符串中出现了重复的 "Iceberg" 字样，如 "Iceberg Apache Iceberg 1.x.x"。

这种重复的 "Iceberg" 前缀属于显示上的瑕疵，虽然不影响 Puffin 文件的功能正确性，但会让 footer 中的创建者元数据显得冗余且不规整，不利于下游工具解析和展示。本提交通过移除手动拼接的 "Iceberg " 前缀，直接使用 `IcebergBuild.fullVersion()` 的返回值作为 created-by 标识，使输出更加规范。

## 如何达成设计目的

提交通过单一文件的修改来达成目标。核心思路是：既然 `IcebergBuild.fullVersion()` 已经返回包含 "Iceberg" 标识的完整版本字符串，就不需要在调用方再额外拼接 "Iceberg " 前缀。修改集中在 `BaseDVFileWriter.newWriter()` 方法中，将原先的字符串拼接改为直接传入 `IcebergBuild.fullVersion()` 的返回值。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/BaseDVFileWriter.java`（修改, +1/-2 lines）

**修改目的**：移除 Puffin 文件 created-by 标识中多余的 "Iceberg" 前缀。

**工作逻辑**：
- 在 `newWriter()` 方法中，原先定义了局部变量 `ident = "Iceberg " + IcebergBuild.fullVersion()`，然后通过 `Puffin.write(outputFile).createdBy(ident).build()` 将其写入 Puffin footer。
- 修改后，直接调用 `Puffin.write(outputFile).createdBy(IcebergBuild.fullVersion()).build()`，不再拼接 "Iceberg " 前缀。这样创建者标识就只包含 `IcebergBuild.fullVersion()` 返回的完整版本字符串（如 "Apache Iceberg 1.x.x"），避免了重复的 "Iceberg" 字样。

## 小结

- **成效**：成功移除了 Puffin footer payload 中重复的 "Iceberg" 前缀，使 created-by 元数据更加规范和简洁。
- **影响范围**：仅涉及 Core 模块的 DV（deletion vector）文件写入逻辑。影响所有使用 DV（Position Delete Index）生成 Puffin 文件的场景，但仅为元数据字符串的格式调整，不影响数据正确性。
- **回迁到 1.4.x 的注意事项**：建议回迁。此提交变更极小（单行修改），无前置依赖，可独立回迁。回迁时需确认 1.4.x 分支中 `IcebergBuild.fullVersion()` 的返回值已包含 "Iceberg" 前缀，否则需要调整。
