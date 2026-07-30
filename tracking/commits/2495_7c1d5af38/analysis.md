# 提交 2495：Update community.md (#13806)

## 提交信息

- **序号**：2495 / 4088
- **哈希**：7c1d5af380691e4f5748a53d5a0ec6b9cf6d21c0
- **短哈希**：7c1d5af38
- **日期**：2025-08-13 10:08:48 -0700
- **作者**：Kevin Liu
- **提交说明**：Update community.md (#13806)
- **PR/Issue**：#13806

## 总体目的

本提交调整了 `community.md` 文档的章节顺序，将"Apache Iceberg Community Calendar"（社区日历）部分移到更靠前的位置，使其在"Hosting an Apache Iceberg Meetup"之前展示。

社区日历是用户了解和参与 Iceberg 社区活动的重要入口，包含开发同步会议、Python/Rust/Go 子项目同步、Catalog 社区同步以及各种临时会议的日历订阅。将其提前展示可以让用户更快发现并加入社区活动，提升社区参与度。这也是提交说明"Show Apache Iceberg Community Calendar first"所表达的目的。

## 如何达成设计目的

通过调整 Markdown 文档中章节的顺序，将社区日历部分从"Hosting an Apache Iceberg Meetup"之后移到之前。内容本身不变，只调整位置。

## 修改详情

### `site/docs/community.md` (+7/-7 lines)

**修改目的**：将社区日历章节提前展示。

**工作逻辑**：
- 删除原位于"Hosting an Apache Iceberg Meetup"章节之后的"Apache Iceberg Community Calendar"段落。
- 在"Hosting an Apache Iceberg Meetup"之前插入相同的"Apache Iceberg Community Calendar"段落。
- 日历内容包含两个 Google Calendar 订阅链接：Iceberg Dev Events（开发同步会议）和 Iceberg Community Events（会议和聚会活动）。

## 总结

本提交通过简单的章节重排，提升了社区日历在文档中的可见性，有助于用户更方便地发现和参与 Iceberg 社区活动。这是一个以用户体验为导向的文档优化。
