# 提交 1181：AWS: Fix AWS doc URL (#11198)

## 提交信息

- **序号**：1181 / 4088
- **哈希**：983ede3976e581cdb687968bf28c1d268106f3f4
- **短哈希**：983ede397
- **日期**：2024-09-25（Wed Sep 25 02:13:09 2024 -0700）
- **作者**：Laurent Goujon <laurentgo@users.noreply.github.com>
- **提交说明**：AWS: Fix AWS doc URL (#11198)
- **PR/Issue**：#11198

## 总体目的

`S3FileIOProperties` 是 `iceberg-aws` 模块中定义 `S3FileIO` 所有可配置属性的类，每个属性都带有详细 Javadoc 说明用途、默认值与官方文档链接。其中 `S3_WRITE_STORAGE_CLASS` 属性（用于设置 S3 对象的存储类，如 STANDARD、INTELLIGENT_TIERING 等）的 Javadoc 里附了一条 AWS 官方文档链接，介绍 S3 存储类。

该链接原本写的是中文版 AWS 文档 URL：
```
https://docs.aws.amazon.com/zh_cn/AmazonS3/latest/userguide/storage-class-intro.html
```
其中 `zh_cn` 路径段会让该链接跳转到 AWS 文档的简体中文版。Iceberg 是国际化开源项目，文档与代码注释默认应使用英文，并指向国际通用版本（无 locale 段或 `en_us` 段）的官方文档，避免给非中文用户带来困扰，也避免文档版本因 locale 不同而内容滞后。

本提交的目的是把该 URL 中的 `zh_cn/` 段去掉，改为国际版 URL：
```
https://docs.aws.amazon.com/AmazonS3/latest/userguide/storage-class-intro.html
```
这样所有用户都能看到默认英文版（或按自己浏览器 locale 自动跳转）的 AWS 文档。这是纯 Javadoc 修订，无任何代码逻辑或运行时行为变更。

## 如何达成设计目的

直接编辑 `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`，把 Javadoc 里的 URL 字符串从 `https://docs.aws.amazon.com/zh_cn/AmazonS3/latest/userguide/storage-class-intro.html` 改为 `https://docs.aws.amazon.com/AmazonS3/latest/userguide/storage-class-intro.html`（删除 `zh_cn/` 路径段）。AWS 文档 URL 不带 locale 段时默认返回英文国际版，浏览器或 AWS 账户的 locale 设置会进一步决定展示语言。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java`

**修改目的**：把 `S3_WRITE_STORAGE_CLASS` 属性 Javadoc 里的 AWS 文档链接从中文版改为国际版。

**改动内容**（位于 `S3_WRITE_STORAGE_CLASS` 属性的 Javadoc 内）：

```java
 * <p>For more details, see
- * https://docs.aws.amazon.com/zh_cn/AmazonS3/latest/userguide/storage-class-intro.html
+ * https://docs.aws.amazon.com/AmazonS3/latest/userguide/storage-class-intro.html
 *
 * <p>Example: s3.write.storage-class=INTELLIGENT_TIERING
```

**工作逻辑**：
- 原 URL 的 `zh_cn/` 是 AWS 文档的简体中文 locale 段，访问会强制返回中文版页面。
- 改后 URL 去掉 locale 段，访问返回默认英文版（国际版），符合 Iceberg 项目的国际化惯例。
- 该 URL 仅出现在 Javadoc 注释里，不参与任何代码逻辑——它只是给阅读源码或生成 Javadoc 文档的开发者参考用，所以改动不影响任何运行时行为、编译产物或配置语义。

## 小结

- **成效**：`S3FileIOProperties` 中 `S3_WRITE_STORAGE_CLASS` 的 Javadoc 链接现指向 AWS 国际版（英文）文档，与项目国际化惯例一致，避免非中文用户被跳转到中文版。
- **影响范围**：仅 `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIOProperties.java` 一个文件的一行 Javadoc URL 字符串变更，无代码、构建或运行时影响。
- **回迁到 1.4.x 的注意事项**：纯 Javadoc 修订，对 1.4.x 运行时无任何影响。1.4.x 作为维护分支通常不必单独回迁这类 Javadoc 链接修订。如希望 1.4.x 源码与 main 一致，可选回迁（改动极小，单行字符串）；**否则无需回迁**，跳过不会引发任何技术问题。
