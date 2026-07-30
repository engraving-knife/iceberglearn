# 提交 0414：Build/Release: Upgrade to RAT 0.16.1 (#9579)

## 提交信息

- **序号**：0414
- **哈希**：f901ad20583f7dcf6801eba41e7beb4391e89b63
- **短哈希**：f901ad205
- **日期**：2024-01-29 09:07:58 +0100
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Build/Release: Upgrade to RAT 0.16.1 (#9579)
- **PR/Issue**：#9579

## 总体目的

本提交将 Apache Iceberg 项目 License 检查脚本 `dev/check-license` 中使用的 Apache RAT（Release Audit Tool）版本从 0.16 升级到 0.16.1。Apache RAT 是 Apache 软件基金会用于审计源码许可证头的工具，Iceberg 作为一个 Apache 顶级项目，在 CI 与发布流程中依赖 RAT 来确保所有源文件都带有正确的 Apache License 头部。

升级到 0.16.1 属于补丁版本升级，通常是为了获取上游的 bug 修复与稳定性改进。由于 RAT 0.16 在某些场景下可能存在误报或解析问题，0.16.1 作为同一次要版本下的补丁版本，保持 API 与行为兼容，升级风险极低。这种"跟进上游补丁版本"的维护工作是 Apache 项目发布管理的常规动作，确保许可证检查工具链保持最新且稳定。

## 如何达成设计目的

实现路径非常直接：在 `dev/check-license` 脚本中，将 `export RAT_VERSION=0.16` 这一行改为 `export RAT_VERSION=0.16.1`。脚本后续通过 `RAT_VERSION` 变量拼接出 jar 包路径 `$FWDIR/lib/apache-rat-${RAT_VERSION}.jar`，并在本地不存在该 jar 时调用 `acquire_rat_jar` 从 Maven 中央仓库下载对应版本。因此只需修改版本号变量，下载与执行逻辑会自动适配新版本，无需改动其他代码。

## 修改详情

### dev/check-license

**修改目的**：将 License 检查工具 Apache RAT 的版本从 0.16 升级到 0.16.1。

**工作逻辑**：`dev/check-license` 是 Iceberg 项目的许可证检查脚本，整体流程为：检测 `JAVA_HOME`，确定 `RAT_VERSION` 与对应 jar 路径，若本地 `lib/` 目录下不存在该 jar 则调用 `acquire_rat_jar` 下载，然后执行 `java -jar $rat_jar --scan-hidden-directories -E $FWDIR/dev/.rat-excludes -d $FWDIR` 对项目根目录扫描，输出结果写入 `build/rat-results.txt`，最后通过 grep " ??" 检测是否有未找到许可证头的文件。本次修改仅将第 61 行的 `export RAT_VERSION=0.16` 改为 `export RAT_VERSION=0.16.1`，由于 jar 路径与下载逻辑都基于该变量动态拼接，整个工具链会自动切换到 0.16.1 版本。`.rat-excludes` 排除规则与扫描参数保持不变，因此对项目本身的许可证检查行为无影响。

## 小结

这是一个典型的依赖版本升级提交，属于项目维护类工作。它体现了 Apache 项目对发布合规工具链的持续维护：及时跟进上游补丁版本以保证许可证检查的稳定性与准确性。改动极小（一行版本号变更），风险低，但属于发布流程不可或缺的一环。这类提交通常由项目的发布管理者（Release Manager）发起，本提交作者 JB Onofré 正是 Apache Iceberg 的活跃贡献者与发布相关维护者。
