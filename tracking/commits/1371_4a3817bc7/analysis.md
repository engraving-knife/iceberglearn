# 提交 1371：Release: Use `dist/release` KEYS (#11526)

## 提交信息

- **序号**：1371 / 4088
- **哈希**：4a3817bc788701973899765ddbdf8b0768c9318a
- **短哈希**：4a3817bc7
- **日期**：2024-11-12（Tue Nov 12 14:30:21 2024 -0500）
- **作者**：Kevin Liu <kevinjqliu@users.noreply.github.com>
- **提交说明**：Release: Use `dist/release` KEYS (#11526)
- **PR/Issue**：#11526

## 总体目的

Apache 项目的发布流程要求发布经理（release manager）用 GPG 私钥对发布物（tarball、checksum）签名，并把公钥发布到项目专属的 `KEYS` 文件，供下游用户验证签名。Apache 的分发仓库（distribution repository，通过 SVN 访问 `https://dist.apache.org/repos/dist/`）有两个主要区域：

- `dist/dev/<project>/`：开发/候选发布物暂存区，存放 release candidate（RC）的 tarball、KEYS 文件等，供 PMC 投票期间使用；
- `dist/release/<project>/`：正式发布物区，存放已通过投票、对外发布的最终 tarball 与 KEYS。

Apache 发布政策（参见 https://www.apache.org/dev/release-publishing）明确规定：**`KEYS` 文件应发布到 `dist/release/<project>/KEYS`（正式发布区），不应只放在 `dist/dev/`**。理由是：
1. `dist/dev/` 是暂存区，理论上 RC 失败后内容会被清理，KEYS 不应依赖暂存区持久性；
2. 下游用户验证已发布版本签名时，应从正式发布区获取 KEYS，与发布物本身位于同一区域；
3. Apache 镜像系统会镜像 `dist/release/` 到全球 `downloads.apache.org/<project>/KEYS`，便于全球访问；`dist/dev/` 不被镜像。

Iceberg 此前的发布脚本（`dev/source-release.sh`）与发布指南（`site/docs/how-to-release.md`）中，多处引用 `https://dist.apache.org/repos/dist/dev/iceberg/KEYS` 作为 KEYS 文件位置，且 SVN checkout 指向 `dist/dev/iceberg`。这违反了 Apache 政策——KEYS 应在 `dist/release/`，并通过 `https://downloads.apache.org/iceberg/KEYS`（镜像地址）访问。

本提交把发布流程中所有 KEYS 引用从 `dist/dev/` 改为 `dist/release/`（对应镜像 URL `downloads.apache.org/iceberg/KEYS`），并修正 SVN checkout 路径，使发布流程符合 Apache 政策。

## 如何达成设计目的

通过修改 2 个文件中的若干 URL 与 SVN 路径：

1. **`dev/source-release.sh`**：投票邮件模板中"KEYS file 位置"链接从 `dist/dev/iceberg/KEYS` 改为 `downloads.apache.org/iceberg/KEYS`。
2. **`site/docs/how-to-release.md`**：发布指南中 4 处 KEYS 引用同步修改——前置条件中的 KEYS 链接、SVN checkout 路径（从 `dist/dev/iceberg` 改为 `dist/release/iceberg`）、投票邮件模板中 KEYS 链接、验证签名时 `curl` 下载 KEYS 的 URL。

URL 选择上，对于面向公众（投票邮件、下载验证）的引用使用镜像 URL `https://downloads.apache.org/iceberg/KEYS`（更快、全球 CDN）；对于 SVN 写操作（发布经理提交 GPG key）使用 SVN 直接路径 `https://dist.apache.org/repos/dist/release/iceberg`（ SVN 写只能走源仓库，不能走镜像）。

## 修改详情

### `dev/source-release.sh`（修改，+1 -1 行）

**修改目的**：修正投票邮件模板中 KEYS 文件的引用 URL。

**工作逻辑**：

```bash
# 旧
* https://dist.apache.org/repos/dist/dev/iceberg/KEYS
# 新
* https://downloads.apache.org/iceberg/KEYS
```

这是发布经理发送 RC 投票邮件时自动生成的模板内容，告知 PMC 投票者去哪里找 KEYS 验证签名。改用镜像 URL 更快、更稳定。

### `site/docs/how-to-release.md`（修改，+4 -4 行）

**修改目的**：同步修正发布指南中的 4 处 KEYS 引用与 SVN 路径。

**工作逻辑**（4 处修改）：

1. **前置条件中的 KEYS 链接**：
   ```markdown
   # 旧
   * A [GPG key for signing](...), published in [KEYS](https://dist.apache.org/repos/dist/dev/iceberg/KEYS)
   # 新
   * A [GPG key for signing](...), published in [KEYS](https://downloads.apache.org/iceberg/KEYS)
   ```

2. **SVN checkout 路径**（发布经理首次发布 GPG key 时）：
   ```shell
   # 旧
   svn co https://dist.apache.org/repos/dist/dev/iceberg icebergsvn
   # 新
   svn co https://dist.apache.org/repos/dist/release/iceberg icebergsvn
   ```
   关键改动：从 `dist/dev/iceberg` 改为 `dist/release/iceberg`。这是写操作（提交 GPG key 到 KEYS 文件），必须走 SVN 源仓库的 `release/` 子目录，符合 Apache 政策。

3. **投票邮件模板中 KEYS 链接**：
   ```markdown
   # 旧
   * https://dist.apache.org/repos/dist/dev/iceberg/KEYS
   # 新
   * https://downloads.apache.org/iceberg/KEYS
   ```

4. **验证签名时 curl 下载 KEYS**：
   ```bash
   # 旧
   curl https://dist.apache.org/repos/dist/dev/iceberg/KEYS -o KEYS
   # 新
   curl https://downloads.apache.org/iceberg/KEYS -o KEYS
   ```

## 小结

- **成效**：Iceberg 发布流程的 KEYS 文件引用从 `dist/dev/`（暂存区）改为 `dist/release/`（正式发布区）及其镜像 `downloads.apache.org/iceberg/KEYS`，符合 Apache 发布政策。下游用户与 PMC 投票者现在从正式发布区/镜像获取 KEYS，避免依赖暂存区，访问更稳定快速。改动是纯文档与脚本 URL 调整，不触碰任何代码逻辑。
- **影响范围**：仅 `dev/source-release.sh`（发布脚本）与 `site/docs/how-to-release.md`（发布指南）两个文件，共 5 处 URL/路径修改。仅影响发布流程，不影响运行时代码。
- **回迁到 1.4.x 的注意事项**：发布流程文档/脚本类变更，回迁安全。需注意：
  1. 1.4.x 上的发布脚本与指南若仍引用 `dist/dev/` 路径，应回迁本提交以符合 Apache 政策；
  2. 回迁前需确认 Iceberg 项目在 `dist/release/iceberg/` 下已有 KEYS 文件（由发布经理通过 SVN 提交），否则回迁后投票者通过 `downloads.apache.org/iceberg/KEYS` 会 404；
  3. `downloads.apache.org` 是镜像，新提交到 `dist/release/` 的 KEYS 文件需要等镜像同步（通常数小时内）才在镜像可见，发布流程中应留出同步时间；
  4. 若 1.4.x 的发布流程文档已演化（如改用 git 替代 SVN），本提交的 SVN 路径需相应调整，但 KEYS 应位于正式发布区的原则不变。
