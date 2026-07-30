# 提交 2932：Docs: Fix package of iceberg.catalog.io-impl (#14711)

## 提交信息

- **序号**：2932 / 4088
- **哈希**：784f1f4693713b922ebfe43e055135fb3a8441c0
- **短哈希**：784f1f469
- **日期**：2025-11-29 15:55:15 +0900
- **作者**：Yuya Ebihara
- **提交说明**：Docs: Fix package of iceberg.catalog.io-impl (#14711)
- **PR/Issue**：#14711

## 总体目的

`docs/docs/kafka-connect.md` 是 Iceberg 文档中介绍 Kafka Connect 集成的页面，其中给出了一段 Kafka Connect connector 配置示例，演示如何将 Iceberg catalog 配置为 REST 类型并使用 Google Cloud Storage（GCS）作为底层存储。该示例中通过 `iceberg.catalog.io-impl` 配置项指定文件 IO 实现类的全限定类名。

改动前，文档将该 IO 实现类写为 `org.apache.iceberg.google.gcs.GCSFileIO`，这是一个**错误的包名**。Iceberg 中 GCSFileIO 的实际包路径是 `org.apache.iceberg.gcp.gcs.GCSFileIO`（模块包根为 `org.apache.iceberg.gcp`，对应 `gcp` bundle 模块）。用户若直接复制文档中的配置，会因为类名找不到而无法启动 connector，造成实际使用障碍。

本提交将该包名更正为 `org.apache.iceberg.gcp.gcs.GCSFileIO`，使文档示例与代码中真实的类路径一致，避免误导用户。

## 如何达成设计目的

在 `docs/docs/kafka-connect.md` 的 GCS 配置示例代码块中，将 `iceberg.catalog.io-impl` 的值由 `org.apache.iceberg.google.gcs.GCSFileIO` 改为 `org.apache.iceberg.gcp.gcs.GCSFileIO`。改动仅 1 行，纠正了 `google` → `gcp` 的包名错误。

## 修改详情

### `docs/docs/kafka-connect.md` (+1/-1 lines)

**修改目的**：更正 GCSFileIO 的全限定类名包路径。

**工作逻辑**：配置示例段：

```
"iceberg.catalog.io-impl": "org.apache.iceberg.gcp.gcs.GCSFileIO"
```

将原先误写的 `org.apache.iceberg.google.gcs` 包改为正确的 `org.apache.iceberg.gcp.gcs` 包。Iceberg 的 GCP/GCS 相关实现统一位于 `org.apache.iceberg.gcp` 包下（对应 `iceberg-gcp` 模块），文档此前误用 `google` 一词导致包名与实际不符。

## 总结

该提交更正了 Kafka Connect 文档中 GCSFileIO 的包路径（`org.apache.iceberg.google.gcs` → `org.apache.iceberg.gcp.gcs`），使配置示例与代码实际类路径一致，避免用户照搬文档时因类找不到而无法使用 GCS 存储。
