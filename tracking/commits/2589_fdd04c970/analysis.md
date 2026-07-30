# 提交 2589：Infra: update how-to-release.md doc on potential multiple staging repositories in a corp network with floating IPs for outbound requests (#13978)

## 提交信息

- **序号**：2589 / 4088
- **哈希**：fdd04c9706379241b3615d77e1080f7f05a4b841
- **短哈希**：fdd04c970
- **日期**：2025-09-02 16:41:56 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Infra: update how-to-release.md doc on potential multiple staging repositories in a corp network with floating IPs for outbound requests (#13978)
- **PR/Issue**：#13978

## 总体目的

本提交是对发布流程文档 `how-to-release.md` 的一个增量补充。Apache Iceberg 在发布流程中需要将二进制制品上传到 Sonatype Nexus 的 staging 仓库（staging repository），然后关闭该仓库并继续后续发布步骤。正常情况下，运行 `dev/stage-binaries.sh` 脚本应该只创建一个 staging 仓库。

但在某些企业网络（corporate network）环境中，由于出站请求使用了浮动 IP（floating IPs）的代理，会导致 Sonatype Nexus 侧观察到多个不同的客户端 IP 地址，从而误判为多个不同的客户端会话，进而创建出多个 staging 仓库。这一问题会干扰发布流程，使发布者在 Nexus 界面上难以确定哪个仓库才是本次发布对应的正确仓库。

本次提交的目的是在文档中补充说明这一现象的成因、如何验证（通过 staging 仓库的 `Activity` 标签页查看客户端 IP），以及规避方法（在企业网络外运行 `dev/stage-binaries.sh` 脚本），从而帮助后续的发布经理在遇到类似问题时能快速定位并解决。

## 如何达成设计目的

该提交采用最直接的方式达成目的：在现有文档已有的一条"如果创建了多个 staging 仓库，请确认 gradle 并行是否被禁用并重试"的提示之后，新增一条并列的提示说明，描述企业网络浮动 IP 这一额外的成因与对应解决方案。

文档修改位于"关闭 staging 仓库"步骤的子项中，与原有提示保持同层级、同格式，保持了文档结构的一致性。通过补充成因解释（代理浮动 IP）、验证手段（查看 Activity 标签页的客户端 IP）和规避方案（在企业网络外运行脚本）三方面信息，完整覆盖了问题诊断与处理路径。

## 修改详情

### `site/docs/how-to-release.md` (+4/-0 lines)

**修改目的**：补充说明在企业网络环境下因代理浮动 IP 导致创建多个 staging 仓库的成因、验证方法与规避方案。

**工作逻辑**：
该修改在"关闭 staging 仓库"步骤的第 3 点（选中 Iceberg 仓库）下，紧跟已有的"如果创建了多个 staging 仓库，请确认 gradle 并行是否被禁用并重试"提示之后，新增一条提示：

```
   * Multiple staging repositories can be created if the script is run in a corporate network
   with a proxy that has floating IPs for outbound requests. You can verify this by checking
   the client IP address in the `Activity` tab of the staging repositories. To avoid this, you
   can run the `dev/stage-binaries.sh` script outside the corporate network.
```

这条新增提示给出了三个关键信息：一是成因（企业网络中具有浮动 IP 的代理导致出站请求被识别为来自不同客户端）；二是验证方法（在 Nexus staging 仓库的 `Activity` 标签页检查客户端 IP 地址）；三是规避方案（在企业网络外运行 `dev/stage-binaries.sh` 脚本）。

## 总结

这是一个纯文档类提交，针对发布流程中一个实际遇到过的问题（企业网络浮动 IP 代理导致 Sonatype Nexus 创建多个 staging 仓库）做了说明补充。修改量极小（仅 4 行新增），不涉及任何代码逻辑变更，但对后续发布经理在类似网络环境下顺利完成发布具有实际指导价值。
