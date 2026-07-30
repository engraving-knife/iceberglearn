# 提交 1681：Docs: Fix latest and nightly link on javadoc (#12023)

## 提交信息

- **序号**：1681 / 4088
- **哈希**：cac68867ce945e34be2da53662d4c7638dd66aa2
- **短哈希**：cac68867c
- **日期**：2025-02-04（Tue Feb 4 21:40:39 2025 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Docs: Fix latest and nightly link on javadoc (#12023)
- **PR/Issue**：#12023

## 总体目的

Iceberg 网站发布脚本 `site/dev/common.sh` 中有 `create_nightly` 和 `create_latest` 两个函数，分别用于构建 nightly（每夜快照）和 latest（最新正式发布）版本的文档站点。这两个函数会更新文档版本信息，但此前**没有创建 javadoc（Java API 文档）的 `latest` 和 `nightly` 符号链接**。

结果是：网站上的 javadoc 目录只有具体版本号的子目录（如 `docs/javadoc/1.4.0/`），但没有稳定的 `docs/javadoc/latest` 或 `docs/javadoc/nightly` 链接。用户无法用一个固定的 URL 访问"最新"或"每夜"的 javadoc，只能手动查找具体版本号目录，体验差且外部链接容易过期。

本提交在这两个函数末尾各增加一段逻辑：先删除可能已存在的同名链接/目录，再在 `docs/javadoc/` 下创建符号链接——`nightly` → `latest`（因为 nightly 的 javadoc 与 latest 共用同一份最新构建产物），`latest` → `${ICEBERG_VERSION}`（当前正在发布的版本号目录）。

## 如何达成设计目的

在 `create_nightly()` 和 `create_latest()` 函数中各追加 7 行 shell 脚本：

1. `create_nightly()`：`rm -fr docs/javadoc/nightly` 清理旧链接 → `cd docs/javadoc && ln -s latest nightly` 创建 nightly → latest 的符号链接；
2. `create_latest()`：`rm -rf docs/javadoc/latest` 清理旧链接 → `cd docs/javadoc && ln -s "${ICEBERG_VERSION}" latest` 创建 latest → 具体版本号的符号链接。

这样，执行发布脚本后 `docs/javadoc/latest` 始终指向最新版本、`docs/javadoc/nightly` 始终指向 latest（即最新的每夜构建），用户可用稳定 URL 访问。

## 修改详情

### `site/dev/common.sh`（修改，+16 行）

**修改目的**：为 javadoc 目录创建 `latest` 与 `nightly` 符号链接，使网站提供稳定的 javadoc 访问路径。

**工作逻辑**：

- `create_nightly()` 追加部分：
  ```sh
  rm -fr docs/javadoc/nightly
  cd docs/javadoc
  ln -s latest nightly
  cd -
  ```
  nightly 链接到 latest（最新 javadoc），保证 nightly URL 始终可用。

- `create_latest()` 追加部分：
  ```sh
  rm -rf docs/javadoc/latest
  cd docs/javadoc
  ln -s "${ICEBERG_VERSION}" latest
  cd -
  ```
  latest 链接到当前版本号目录（如 `1.5.0`），每次发布时更新。

## 小结

- **成效**：网站 javadoc 现在有稳定的 `latest` 与 `nightly` 符号链接，用户无需知道具体版本号即可访问最新或每夜 javadoc，外部链接不会因版本迭代而过期。
- **影响范围**：仅 `site/dev/common.sh` 发布脚本，无源代码或运行时行为变更。
- **回迁到 1.4.x 的注意事项**：纯发布脚本修复，回迁安全。需确认 1.4.x 分支的 `common.sh` 结构与 main 一致；若 1.4.x 的发布流程不同，需相应调整脚本位置与变量名（如 `ICEBERG_VERSION`）。
