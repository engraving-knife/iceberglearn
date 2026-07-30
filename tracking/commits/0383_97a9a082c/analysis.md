# 提交 0383：Build: Upgrade to Apache RAT 0.16, scanning hidden directories and adding missing ASF header

## 提交信息

- **序号**：0383
- **哈希**：97a9a082c1c5e76280dc391f25b17a25f949219b
- **短哈希**：97a9a082c
- **日期**：Thu Jan 18 12:25:34 2024 +0100
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Build: Upgrade to Apache RAT 0.16, scanning hidden directories and adding missing ASF header (#9495)
- **PR/Issue**：#9495

## 总体目的

这个提交是 Apache Iceberg 作为 Apache 顶级项目满足发布合规（release approval）要求的一项基础设施治理。Apache 项目发布前必须通过 Apache RAT（Release Audit Tool）扫描，确认仓库内所有源文件都带有 ASF（Apache Software Foundation）标准许可证头，且第三方依赖许可证可被接受。在 1.4.x 之前，仓库使用 RAT 0.15，且没有显式启用对隐藏目录（`.github`、`.baseline` 等）的扫描，导致这些目录下若干配置/模板文件处于"扫描盲区"，缺少 ASF 头的文件未被发现、未补全。

这次升级与策略调整做了三件关联的事：1）把 RAT 版本从 0.15 升到 0.16；2）在 `dev/check-license` 中加上 `--scan-hidden-directories` 让 RAT 进入以点号开头的隐藏目录；3）为扫描后暴露出缺失头部的文件补上标准的 ASF 许可证头。三者形成闭环：版本升级提供能力，开启隐藏目录扫描消除盲区，补全头部让扫描结果转绿。

从合规意义上看，这不是普通的"功能改进"，而是发布前必须满足的硬性要求——Apache 发布流程要求 RAT 报告通过且无未批准的许可证问题。所以这是一次"发布前合规治理"，对 1.4.x 维护分支尤其关键。

## 如何达成设计目的

实现路径简洁明确：先在 `dev/check-license` 中将 `RAT_VERSION=0.15` 改为 `0.16`，并在 RAT 调用行追加 `--scan-hidden-directories` 标志；运行新的扫描后，识别出四个原本被忽略的隐藏目录文件缺少 ASF 头（三个 GitHub issue 模板 yml、一个 Eclipse 配置 dotfile.checkstyle）；为这四个文件分别添加符合各自语法的 ASF 头（yml 用 `#` 注释风格、checkstyle 用 XML 注释风格）；同时在 `dev/.rat-excludes` 中追加若干新条目，将 IDE/构建产物目录（`.git`、`.gradle`、`.idea`）以及 `.prefs` 文件纳入排除，避免新开启的隐藏目录扫描把无关文件误报为问题。

## 修改详情

### dev/check-license

**修改目的**：升级 RAT 版本并启用对隐藏目录的扫描。

**工作逻辑**：将 `export RAT_VERSION=0.15` 改为 `export RAT_VERSION=0.16`，使脚本下载并使用新版 RAT jar；将调用行 `$java_cmd -jar "$rat_jar" -E "$FWDIR"/dev/.rat-excludes -d "$FWDIR" > build/rat-results.txt` 改为加上 `--scan-hidden-directories`。RAT 默认会跳过以 `.` 开头的目录（认为那是隐藏目录/版本控制目录），开启该标志后才会进入 `.github`、`.baseline` 等 Apache 项目常用于模板与基线配置的目录，从而让许可证审计覆盖到这些位置。

### dev/.rat-excludes

**修改目的**：为新开启的隐藏目录扫描预先排除不应被审计的目录与文件，避免误报。

**工作逻辑**：在排除列表中追加 `.git`（Git 元数据目录，显然不应被审计）、`.gradle`（Gradle 构建缓存）、`.idea`（IntelliJ IDEA 项目配置），以及在正则排除区追加 `.*\.prefs`（Eclipse `.settings/org.eclipse.*.prefs` 这类 IDE 偏好文件）。这样 `--scan-hidden-directories` 开启后，真正需要审计的隐藏目录文件（如 `.github/ISSUE_TEMPLATE/*.yml`、`.baseline/eclipse/dynamic/dotfile.checkstyle`）会被扫描并补头，而构建/IDE 噪声被排除。

### .github/ISSUE_TEMPLATE/iceberg_bug_report.yml

**修改目的**：为该 GitHub Bug 报告模板补上 ASF 许可证头以满足 RAT 扫描要求。

**工作逻辑**：在文件最顶部、`---` 分隔符之前插入 ASF 标准 `#` 注释风格的许可证头（包含"Licensed to the Apache Software Foundation (ASF) ..."完整声明、Apache License 2.0 引用与免责条款）。YAML 顶层注释以 `#` 开头不会被解析为数据，因此这种插入对模板功能完全无影响，只是文件头部多了 19 行法律声明。

### .github/ISSUE_TEMPLATE/iceberg_improvement.yml

**修改目的**：为该改进/功能请求模板补上 ASF 许可证头。

**工作逻辑**：与 `iceberg_bug_report.yml` 完全相同，在文件顶部插入同样的 `#` 风格 ASF 头，长度与内容一致。

### .github/ISSUE_TEMPLATE/iceberg_question.yml

**修改目的**：为该提问模板补上 ASF 许可证头。

**工作逻辑**：与上两个模板一致，在文件顶部插入同样的 `#` 风格 ASF 头。

### .baseline/eclipse/dynamic/dotfile.checkstyle

**修改目的**：为该 Eclipse Checkstyle dotfile 模板补上 ASF 许可证头。

**工作逻辑**：该文件是 XML 格式（`<fileset-config ...>`），因此在 `<?xml ... ?>` 声明之后、根元素 `<fileset-config>` 之前插入 XML 注释风格的 ASF 头 `<!-- ... -->`（与 Java 源文件头风格一致）。这种插入方式对 XML 解析无副作用，且能被 RAT 正确识别为合法许可证头。

## 小结

这是一个典型 Apache 项目发布合规治理提交：通过升级工具版本（RAT 0.15 → 0.16）、开启隐藏目录扫描消除审计盲区、补全被发现的缺失 ASF 头，形成完整的"工具能力 + 扫描策略 + 头部修复"闭环。同时通过精确扩展 `.rat-excludes` 排除构建/IDE 噪声，避免开启扫描后产生误报。这种维护对 1.4.x 维护分支尤为重要，是发布投票（release vote）前必须通过的检查项之一，体现了 Apache 治理流程对许可证一致性的严格要求。
