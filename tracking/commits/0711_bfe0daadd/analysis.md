# 提交 0711：发布二进制包时改用 `svn mv`

## 提交信息
- **序号**：0711 / 4088
- **哈希**：bfe0daadd5ab1107a721b73cc0e72c3ad76cef23
- **短哈希**：bfe0daadd
- **日期**：2024-04-24
- **作者**：Fokko Driesprong
- **提交说明**：Docs: Use `svn mv` when releasing the binaries (#9926)
- **PR/Issue**：#9926

## 总体目的

本提交修改 Iceberg 发布流程文档 `site/docs/how-to-release.md`，把"将候选版本（rc）从 SVN dev 目录搬到 release 目录"这一步，从多步的 `svn co` + `cp -r` + `svn add` + `svn ci` 流程，简化为单条 `svn mv`（远端移动）命令。

原流程需要发布经理在本地先 `svn co`（checkout）dev 与 release 两个 SVN 工作副本，再用 `cp -r` 把候选目录拷贝到 release 副本中，然后 `svn add` 把新目录纳入版本控制，最后 `svn ci` 提交。该流程存在几个问题：操作步骤多、本地需要维护两份工作副本、容易遗漏 `svn add` 导致提交不完整、且本地拷贝速度较慢。

`svn mv` 是 SVN 服务端的原子移动操作，可直接在仓库 URL 之间移动目录（`https://.../dev/iceberg/<VERSION>-rcN` → `https://.../release/iceberg/<VERSION>`），无需本地 checkout，一步完成移动与提交。这样既减少了人为出错的可能性，也避免了维护本地工作副本的负担。

此外，本提交顺手修复了一处拼写错误："credentals" → "credentials"。

## 如何达成设计目的

策略非常直接：把原来 6 行的 bash 代码块替换为单行 `svn mv` 命令，命令中显式给出 dev 与 release 的完整远端 URL，并通过 `-m` 参数内联提交信息。这样既保留了文档的可读性，又让发布流程更稳健。

修复拼写错误则是在同一次编辑中一并完成，属于顺带的文档质量改进。

## 修改详情

### `site/docs/how-to-release.md`
**修改目的**：简化发布流程的 SVN 操作步骤，并修复拼写错误。
**工作逻辑**：

1. **拼写修复**：将 "Apache LDAP credentals for Nexus and SVN" 中的 `credentals` 改为 `credentials`。

2. **发布命令简化**：将原 6 行 bash 代码块：
   ```bash
   mkdir iceberg
   cd iceberg
   svn co https://dist.apache.org/repos/dist/dev/iceberg candidates
   svn co https://dist.apache.org/repos/dist/release/iceberg releases
   cp -r candidates/apache-iceberg-<VERSION>-rcN/ releases/apache-iceberg-<VERSION>
   cd releases
   svn add apache-iceberg-<VERSION>
   svn ci -m 'Iceberg: Add release <VERSION>'
   ```
   替换为单行：
   ```bash
   svn mv https://dist.apache.org/repos/dist/dev/iceberg/apache-iceberg-<VERSION>-rcN https://dist.apache.org/repos/dist/release/iceberg/apache-iceberg-<VERSION> -m "Iceberg: Add release <VERSION>"
   ```

   关键变化：
   - 不再需要本地 `svn co` checkout 两份工作副本。
   - 不再需要 `cp -r` 本地拷贝。
   - 不再需要 `svn add` 显式纳入版本控制——`svn mv` 在远端完成移动即自动纳入。
   - 提交信息直接通过 `-m` 参数内联，无需进入编辑器。
   - 注意目标路径从 `apache-iceberg-<VERSION>-rcN` 重命名为 `apache-iceberg-<VERSION>`（去掉 `-rcN` 后缀），这是发布时从候选版本转为正式版本的命名约定。

## 小结
- **成效**：成功简化发布流程文档，将 6 步本地操作缩减为 1 步远端原子操作，降低发布经理出错风险。
- **影响范围**：仅影响 `site/docs/how-to-release.md` 文档，不影响任何代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯文档改动，若 1.4.x 分支的发布文档仍使用旧的多步流程，可直接 cherry-pick；需确认 1.4.x 的 `how-to-release.md` 中对应代码块未发生显著变化。无任何风险。
