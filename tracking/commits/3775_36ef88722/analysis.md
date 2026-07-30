# 提交 3775：[SPEC] Add relative paths to v4 spec (#15630)

## 提交信息

- **序号**：3775 / 4088
- **哈希**：36ef88722bad12973c081fd6d7bbbaf3e0b06210
- **短哈希**：36ef88722
- **日期**：2026-05-22 18:08:30 -0700
- **作者**：Daniel Weeks
- **提交说明**：[SPEC] Add relative paths to v4 spec (#15630)
- **PR/Issue**：#15630

## 总体目的

这个提交为 Iceberg 规范的 V4 版本添加了相对路径（relative paths）支持。这是 V4 规范的核心特性之一，允许元数据中的路径使用相对路径而非绝对路径，使表可以在不重写元数据文件的情况下被迁移（relocate）到不同的存储位置。

在 V3 及之前版本中，所有元数据中的路径字段都必须是全限定路径（fully-qualified paths，包含 URI scheme 如 `s3://`、`hdfs://` 等）。这导致当表需要从一个存储位置迁移到另一个位置时（例如从 S3 迁移到 GCS，或更换 bucket 名称），必须重写所有清单文件和元数据文件中的路径。V4 的相对路径支持解决了这个问题。

## 如何达成设计目的

通过修改 `format/spec.md` 规范文档，新增以下内容：
1. 在版本概述中添加 V4 版本说明。
2. 新增"File Locations in Metadata"章节描述相对路径概念。
3. 新增"Paths in Metadata"章节详细定义绝对路径和相对路径。
4. 新增"Path Resolution"和"Path Relativization"子章节定义转换规则。
5. 新增"Table Location Specification"章节定义表位置的处理。
6. 添加 V4 表元数据字段表。
7. 在附录 E 中添加 V4 版本变更说明。
8. 新增"Path Construction"建议章节。

## 修改详情

### `format/spec.md` (+130/-2 lines)

**修改目的**：为 V4 规范添加相对路径支持的详细定义。

**工作逻辑**：

1. **V4 版本概述**：新增"Version 4: Metadata Structure and Representation"章节，说明 V4 添加了相对位置支持。

2. **File Locations in Metadata**：说明 V3 及之前使用全限定路径，V4 新增相对路径支持，相对路径相对于表的基础位置解析。

3. **Paths in Metadata**：
   - **Absolute path**：以 URI scheme 开头的路径（如 `s3:`、`gs:`、`hdfs:`、`file:`），直接使用。
   - **Relative path**：不以 URI scheme 开头的路径，必须相对于表位置解析。
   - V4 起路径字段可以包含绝对或相对路径。不支持 `.` 和 `..` 等 URI 内相对解析。

4. **Path Resolution**：定义相对路径到绝对路径的转换规则——如果路径以 URI scheme 开头则直接使用，否则将表位置和相对路径用 `/` 连接。包含详细的示例表格，展示各种路径组合的解析结果。

5. **Path Relativization**：定义绝对路径到相对路径的转换——如果绝对路径以表位置开头且后跟分隔符，则去除前缀存储相对路径；否则存储绝对路径。

6. **Table Location Specification**：V4 中 `location` 字段变为可选，当不存在时由 catalog 提供表位置。

7. **V4 表元数据字段表**：完整列出 V4 的表元数据字段，`location` 标记为可选。

8. **Appendix E: Version 4**：定义 V4 的读写规则：
   - 读取 V3 元数据时，所有路径视为绝对路径，缺失 scheme 的需补全。
   - 写入 V4 元数据时，`location` 可选，位置字段可使用相对路径，默认应为相对路径。
   - 读取 V4 元数据时，需检查 URI scheme 判断绝对/相对路径。

9. **Path Construction（建议）**：描述 `write.metadata.path` 和 `write.data.path` 属性如何控制文件写入位置，以及持久化时应相对化路径。

## 总结

这个提交是 Iceberg V4 规范的重要里程碑，定义了相对路径支持的完整规范。相对路径使表可以在不重写元数据文件的情况下迁移存储位置，大大简化了表迁移、备份恢复和跨存储系统操作。规范定义了路径分类、解析、相对化和构造的完整规则，为后续实现提供了明确的指导。这是由 Daniel Weeks 和 Talat Uyarer 共同贡献的规范级变更。
