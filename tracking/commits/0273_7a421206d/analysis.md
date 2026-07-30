# 提交 0273：Docs: Update readme status paragraph (#9272)

## 提交信息

- **序号**：0273 / 4088
- **哈希**：7a421206d54fcf39d99bd41c3ab25a89051ed028
- **短哈希**：7a421206d
- **日期**：2023-12-14
- **作者**：Ron Korving
- **提交说明**：Docs: Update readme status paragraph (#9272)
- **PR/Issue**：#9272

## 总体目的

`README.md` 中的 `## Status` 段落已多年未更新，内容停留在 Iceberg 早期阶段的描述——原文称"核心 Java 库……已完成但仍在演进"，并强调"格式规范正在被主动更新、开放征求意见，在规范完成并发布之前不提供兼容性保证，规范随 Java 参考实现的变化而演进"，最后只给出 Java API javadocs 链接。这种描述放到 2023 年底的 Iceberg 已经不准确且不利于使用者建立信心：实际上 Iceberg 的格式规范早已稳定并被多个引擎广泛采用，Iceberg 也已是 ASF 的活跃项目，旧的"未完成、无兼容保证"措辞容易让新用户误以为项目尚处于试验阶段。

本提交的目的就是把 `Status` 段落改写为与当前项目状态相符的内容：明确格式规范已稳定、每个版本会增加新特性；指出本仓库中的核心 Java 库是其他库的参考实现；引导用户到统一文档站点；并指向官方 roadmap 以跟踪正在进行的工作。同时移除已过时的 javadocs 引用，替换为指向最新文档站点与 roadmap 的链接。这是一次纯文档更新，动机是让 README 的项目状态描述与 Iceberg 实际成熟度一致，避免误导用户。

## 如何达成设计目的

设计思路是用一段简洁、准确的"状态说明"替换原有偏旧偏试验性口吻的段落，并调整引用链接。具体做法是：保留"在 ASF 下活跃开发"这一句作为开头，重写其后三句——把"规范正在演进、无兼容保证"改为"规范已稳定、新特性随版本加入"；新增"本仓库的核心 Java 库是其他库的参考实现"一句以点明仓库定位；把原先指向 javadocs 的指引替换为指向统一文档站点的 `[Documentation][iceberg-docs]` 链接；新增"当前工作跟踪在 roadmap"一句及对应链接。最后更新链接定义：用 `iceberg-docs`（指向 `https://iceberg.apache.org/docs/latest/`）和 `roadmap`（指向 `https://iceberg.apache.org/roadmap/`）替换掉原来的 `iceberg-javadocs`，保留 `iceberg-spec`。整个改动只涉及 README 的一个段落与几条链接定义，不触碰代码。

## 修改详情

### `README.md`

**修改目的**：更新 `## Status` 段落，使其反映 Iceberg 当前成熟、规范稳定的实际状态，并替换过时的链接。

**工作逻辑**：
- 将原句"The core Java library that tracks table snapshots and metadata is complete, but still evolving. Current work is focused on adding row-level deletes and upserts, and integration work with new engines like Flink and Hive."替换为四句新的状态描述：
  1. "The [Iceberg format specification][iceberg-spec] is stable and new features are added with each version." —— 明确规范已稳定；
  2. "The core Java library is located in this repository and is the reference implementation for other libraries." —— 点明本仓库核心库的参考实现定位；
  3. "[Documentation][iceberg-docs] is available for all libraries and integrations." —— 引导到统一文档站点；
  4. "Current work is tracked in the [roadmap][roadmap]." —— 指向 roadmap。
- 删除原句"The [Iceberg format specification][iceberg-spec] is being actively updated and is open for comment. Until the specification is complete and released, it carries no compatibility guarantees. The spec is currently evolving as the Java reference implementation changes." —— 消除"未完成、无兼容保证"的误导表述。
- 删除原句"[Java API javadocs][iceberg-javadocs] are available for the main." —— 不再单独指向 javadocs。
- 更新链接定义：移除 `[iceberg-javadocs]: https://iceberg.apache.org/javadoc/latest`，新增 `[iceberg-docs]: https://iceberg.apache.org/docs/latest/` 与 `[roadmap]: https://iceberg.apache.org/roadmap/`，保留 `[iceberg-spec]: https://iceberg.apache.org/spec`。

## 小结

该提交通过重写 `README.md` 的 `## Status` 段落，把原本描述项目"仍在演进、规范未完成、无兼容保证"的过时表述更新为"规范已稳定、本仓库为核心参考实现、文档与 roadmap 已就绪"，并相应替换链接定义，使 README 的项目状态描述与 Iceberg 当前的成熟度保持一致。
