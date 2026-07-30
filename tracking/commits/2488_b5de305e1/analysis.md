# 提交 2488：site: reorganize navbar so that "community" is top level (#13774)

## 提交信息

- **序号**：2488 / 4088
- **哈希**：b5de305e111c315a9c3b331cf19c13aa0540b5d7
- **短哈希**：b5de305e1
- **日期**：2025-08-12 11:12:33 -0700
- **作者**：Kevin Liu
- **提交说明**：site: reorganize navbar so that "community" is top level (#13774)
- **PR/Issue**：#13774

## 总体目的

该提交重新组织了 Iceberg 网站的导航栏结构，将"Community"提升为顶级导航项，并将 Blogs、Talks、Vendors 等社区相关内容归入其下，提升社区内容的可见性和访问便捷性。

此前网站导航栏中，Community 页面被嵌套在"Project"下拉菜单下，而 Blogs、Talks、Vendors 作为独立的顶级导航项分散展示。这种结构没有突出社区的重要性，社区相关内容分散在不同位置，不利于用户发现和参与社区活动。将 Community 提升为顶级导航项，并将相关内容归组其下，使社区板块更加突出和集中。

## 如何达成设计目的

通过修改 `site/nav.yml` 导航配置文件来重新组织导航栏结构：

1. **新增 Community 顶级导航项**：创建独立的 Community 下拉菜单，包含 Community、Blogs、Talks、Vendors 四个子项。

2. **从 Project 菜单移除 Community**：Community 不再嵌套在 Project 下拉菜单中。

3. **移除独立顶级项**：Blogs、Talks、Vendors 不再作为独立顶级导航项，而是归入 Community 下拉菜单。

## 修改详情

### `site/nav.yml` (+5/-4 lines)

**修改目的**：重新组织网站导航栏结构。

**工作逻辑**：

修改前导航结构（相关部分）：
```yaml
  - Releases: releases.md
  - Blogs: blogs.md
  - Talks: talks.md
  - Vendors: vendors.md
  - Project:
    - Community: community.md
    - Contributing: contribute.md
    ...
```

修改后导航结构（相关部分）：
```yaml
  - Releases: releases.md
  - Project:
    - Contributing: contribute.md
    ...
  - Community:
    - Community: community.md
    - Blogs: blogs.md
    - Talks: talks.md
    - Vendors: vendors.md
```

具体变更：
- 从"Project"下拉菜单中移除"Community: community.md"子项。
- 移除"Blogs: blogs.md"、"Talks: talks.md"、"Vendors: vendors.md"三个独立顶级导航项。
- 新增"Community"顶级下拉菜单，包含 Community、Blogs、Talks、Vendors 四个子项。

## 总结

这是一个网站导航结构优化提交，将"Community"从 Project 下拉菜单的子项提升为顶级导航项，并将 Blogs、Talks、Vendors 等社区相关内容归入其下。该提交仅修改导航配置文件，不涉及任何功能代码，旨在提升社区内容的可见性和访问便捷性，突出社区在项目中的重要地位。
