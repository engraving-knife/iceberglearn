# 提交 3822：Infra: Update collaborators list (#16678)

## 提交信息

- **序号**：3822 / 4088
- **哈希**：4b4c5b554c829d946d7152dbf70dcd987ff6a9ec
- **短哈希**：4b4c5b554
- **日期**：2026-06-03 15:27:13 -0700
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Infra: Update collaborators list (#16678)
- **PR/Issue**：#16678

## 总体目的

本提交更新 Iceberg 仓库的 GitHub 协作者（collaborators）列表，新增 `anuragmantri` 和 `nssalian` 两位协作者，同时移除不再活跃的 `chenjunjiedada` 和 `jun-he`。Apache Iceberg 仓库通过 `.asf.yaml` 配置文件管理 GitHub 仓库的协作者，但 GitHub/ASF 策略限制每个仓库的协作者数量上限为 10 人。因此当需要新增活跃贡献者为协作者时，必须同步移除不再活跃的成员以保持在 10 人限额内。

协作者角色允许成员直接管理 issue、PR、分支等仓库资源（无需每次依赖 PMC 成员操作），是项目治理中授予活跃贡献者更多职责的常见方式。本次新增的 `anuragmantri` 和 `nssalian` 都是近期活跃的贡献者（nssalian 也是 iceberg-go 0.6.0 发布博客的作者），授予协作者权限有助于分散日常仓库管理工作。

## 如何达成设计目的

通过修改仓库根目录的 `.asf.yaml` 文件 `github.collaborators` 列表，删除两个不活跃成员、新增两个活跃成员，保持总数仍为 10 人。`.asf.yaml` 是 ASF 项目专用的配置文件，提交后会由 ASF 基础设施自动同步到 GitHub。

## 修改详情

### `.asf.yaml` (+2/-2 lines)

**修改目的**：更新 GitHub 协作者列表。

**工作逻辑**：
在 `github.collaborators` 列表中：
- 移除 `chenjunjiedada`、`jun-he`（不再活跃）。
- 新增 `anuragmantri`、`nssalian`（活跃贡献者）。
- 其余 8 位协作者（`marton-bod`、`samanthjain`、`SreeramGarlapati`、`RussellSpitzer`、`ajantha-bhat`、`jbonofre`、`manuzhang`、`stevenz3wu` 即隐含的作者本人）保持不变，总数维持在 10 人限额内。

## 总结

本提交是项目治理/基础设施维护类工作，通过更新 `.asf.yaml` 调整 GitHub 协作者名单，反映了项目对活跃贡献者的激励（授予协作者权限）与对不活跃成员的轮换。保持协作者列表的新鲜有助于仓库日常管理的高效运转。
