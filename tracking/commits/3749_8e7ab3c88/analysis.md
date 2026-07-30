# 提交 3749：CI: Make CVE scan blocking on PRs, informational on main (#16287)

## 提交信息

- **序号**：3749 / 4088
- **哈希**：8e7ab3c881391487d3676fe31f53077e78f6375b
- **短哈希**：8e7ab3c88
- **日期**：2026-05-19 17:09:09 -0700
- **作者**：Kevin Liu
- **提交说明**：CI: Make CVE scan blocking on PRs, informational on main (#16287)
- **PR/Issue**：#16287

## 总体目的

本提交改进了 Iceberg 的 CVE（安全漏洞）扫描工作流，使其在 PR 上变为阻塞式（blocking），在 main/release 分支上保持信息式（informational），从而在引入新依赖时及早发现安全漏洞，同时避免 main 分支因已知漏洞而持续失败。

原先 CVE 扫描工作流仅在 push 到 main/release 分支时触发，且 `exit-code: '0'` 表示即使发现漏洞也不阻塞构建，只是将 SARIF 结果上传到 GitHub Security 标签页供跟踪。这意味着：
1. PR 中引入含 CVE 的依赖不会被发现，直到合并到 main 后才在 Security 标签页显示。
2. main 分支上如果存在已知的、暂时无法修复的漏洞（如被 Spark 版本钉住的传递依赖），扫描虽然不阻塞但会持续报告。

本提交的策略调整：
- **PR 上**：CVE 扫描阻塞 CI（exit-code 1），强制开发者在合并前处理新引入的漏洞。SARIF 上传跳过（GitHub Security 标签页只接受默认/受保护分支的结果）。
- **main/release 分支上**：保持信息式（exit-code 0），上传 SARIF 供跟踪。
- 为已知的、暂时无法修复的漏洞（如 Spark 3.4 钉住的 jackson-core CVE）新增 `.trivyignore` 文件进行豁免。

## 如何达成设计目的

1. 在工作流的 `on` 触发器中新增 `pull_request`，使 PR 也触发扫描。
2. 将 Trivy 扫描的 `exit-code` 改为根据事件类型动态设置：PR 为 `1`（阻塞），push 为 `0`（信息式）。
3. 将 SARIF 上传步骤限制为仅在 `push` 事件时执行。
4. 新增 `.github/trivyignores/spark-runtime-3.4_2.12.trivyignore` 文件，豁免 Spark 3.4 运行时因 jackson-core 2.14.2 的 CVE-2025-52999（被 Spark 3.4 钉住，且 Spark 3.4 支持即将移除）。
5. 在矩阵中为 spark-runtime-3.4_2.12 条目指定 `trivyignores` 路径，并在 Trivy 扫描步骤中传入 `trivyignores` 参数。
6. 将 PR 的并发取消设为 `cancel-in-progress: true`（PR 上节省资源），push 保持 `false`。

## 修改详情

### `.github/trivyignores/spark-runtime-3.4_2.12.trivyignore` (+29/-0 lines, 新增文件)

**修改目的**：为 spark-runtime-3.4_2.12 模块豁免已知的、暂时无法修复的 CVE。

**工作逻辑**：
新建 Trivy 忽略文件，包含 Apache 许可证头和说明注释，豁免 CVE-2025-52999：
```
# CVE-2025-52999 — jackson-core 2.14.2 StackoverflowError on deeply-nested input.
# Pinned by Spark 3.4 runtime compatibility (Spark 3.4 ships jackson 2.14).
# Spark 3.4 support is being removed from Iceberg in the near term; track the
# removal and drop this file when the spark-runtime-3.4 module goes away.
CVE-2025-52999
```
注释说明该 CVE 来自 jackson-core 2.14.2，而 Spark 3.4 钉死了 jackson 2.14，无法单独升级。由于 Spark 3.4 支持即将移除（见 #14122），此豁免是临时措施，待 spark-runtime-3.4 模块移除后删除该文件。

### `.github/workflows/cve-scan.yml` (+16/-7 lines)

**修改目的**：让 CVE 扫描在 PR 上阻塞、main 上信息式，并支持 trivyignore。

**工作逻辑**：
- 触发器新增 `pull_request`，使 PR 也触发扫描。
- 并发取消策略改为 `cancel-in-progress: ${{ github.event_name == 'pull_request' }}`，PR 上取消旧运行，push 上不取消。
- 更新注释说明新的行为策略：PR 阻塞（exit-code 1）、main 信息式（exit-code 0）。
- 矩阵中 spark-runtime-3.4_2.12 条目新增 `trivyignores: .github/trivyignores/spark-runtime-3.4_2.12.trivyignore`。
- setup-gradle 步骤移除 zizmor 注释（不再需要）。
- Trivy 扫描步骤：
  - 新增 `trivyignores: ${{ matrix.trivyignores || '' }}`，传入矩阵指定的忽略文件。
  - `exit-code` 从固定 `'0'` 改为 `${{ github.event_name == 'pull_request' && '1' || '0' }}`，PR 上为 1（阻塞），push 上为 0（信息式）。
- SARIF 上传步骤的 `if` 从 `always()` 改为 `always() && github.event_name == 'push'`，仅 push 事件上传（GitHub Security 标签页只接受默认分支结果）。

## 总结

本提交将 Iceberg 的 CVE 扫描工作流从"仅在 main 上信息式运行"改为"PR 上阻塞、main 上信息式"的双模式策略，使新引入的安全漏洞能在 PR 阶段被及早发现并处理，同时避免 main 分支因已知漏洞持续失败。为 Spark 3.4 运行时因 jackson-core 2.14.2 的 CVE-2025-52999（被 Spark 钉死）新增了临时豁免文件。这是 CI 安全策略的重要改进，平衡了安全严格性与开发效率。
