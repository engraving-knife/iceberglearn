# 提交 3174：Build/Release: use RAT collection for ignore file patterns (#15174)

## 提交信息

- **序号**：3174 / 4088
- **哈希**：b9f6660bea16668e6e12c047446ba35269ec1a59
- **短哈希**：b9f6660be
- **日期**：2026-01-28
- **作者**：Kevin Liu
- **提交说明**：Build/Release: use RAT collection for ignore file patterns (#15174)
- **PR/Issue**：#15174

## 总体目的

这是对前一个提交（#15145，RAT 0.17 升级）的后续优化。在升级到 RAT 0.17 时，作者将旧的排除模式翻译为 `dev/.rat-excludes` 文件中的自定义 glob 模式，包括对 `.gitignore`、`.git/**`、`.idea/**`、`**/*.iml`、`**/*.iws` 等文件的处理。然而 RAT 0.17 本身内置了若干"标准集合"（standard collections），即对常见忽略文件类型的预定义排除集，例如 `GIT`（git 元数据与忽略文件）、`IDEA`（IntelliJ IDEA 工程文件）、`MAC`（macOS 系统文件如 .DS_Store）等。

本次提交由 Kevin Liu 通过 PR #15174 将这些与 RAT 内置标准集合重复的自定义排除项从 `.rat-excludes` 中移除，转而在 `check-license` 脚本中通过 `--input-exclude-std GIT IDEA MAC` 选项启用对应的标准集合。这样做的动机是：(1) 减少自定义排除文件中的冗余条目，避免与标准集合重复维护；(2) 依赖 RAT 官方维护的标准集合，使其随 RAT 版本更新自动覆盖新增的忽略文件类型，降低维护成本；(3) 使排除配置更清晰——通用类型走标准集合，项目特定类型留在 `.rat-excludes`。

这是一个纯粹的配置优化，不改变实际被排除的文件范围（标准集合与原自定义模式的覆盖面等价），只是将维护责任从项目转移到 RAT 工具本身。

## 如何达成设计目的

两处改动配合完成：(1) 从 `dev/.rat-excludes` 删除 `.gitignore`、`.git/**`、`.idea/**`、`**/*.iml`、`**/*.iws` 五项（这些由 GIT 与 IDEA 标准集合覆盖）；(2) 在 `dev/check-license` 的 RAT 调用中新增 `--input-exclude-std GIT IDEA MAC --` 选项，启用三个标准集合。

## 修改详情

### `dev/.rat-excludes` (+3/-6 lines)

**修改目的**：移除与 RAT 标准集合重复的自定义排除项。

**工作逻辑**：
删除以下 5 行：`.gitignore`、`.git/**`、`.idea/**`、`**/*.iml`、`**/*.iws`。这些分别对应 git 忽略文件/git 元数据目录、IDEA 工程目录、IDEA 模块文件（.iml/.iws），均被 RAT 0.17 的 `GIT` 与 `IDEA` 标准集合覆盖。保留 `.gradle/**`（Gradle 未被标准集合覆盖，需自定义）。该改动使 `.rat-excludes` 更聚焦于项目特有的排除项。

### `dev/check-license` (+1/-0 lines)

**修改目的**：启用 RAT 标准忽略集合替代被移除的自定义项。

**工作逻辑**：
在 RAT 调用命令中，于 `--input-exclude-file "$FWDIR"/dev/.rat-excludes \` 之后新增一行 `--input-exclude-std GIT IDEA MAC -- \`。该选项一次性启用三个标准集合：`GIT`（覆盖 .git 目录、.gitignore 等）、`IDEA`（覆盖 .idea 目录、.iml/.iws 等）、`MAC`（覆盖 macOS 的 .DS_Store 等，此前自定义列表中虽未显式列出但属于合理补充）。注意 `--` 作为选项终结符，后接扫描路径 `"$FWDIR"`。

最终 RAT 调用形如：
```
$java_cmd -jar "$rat_jar" \
  --input-exclude-file "$FWDIR"/dev/.rat-excludes \
  --input-exclude-std GIT IDEA MAC -- \
  --input-include-std HIDDEN_DIR \
  --output-style missing-headers \
  --log-level ERROR \
  -- "$FWDIR" || exit 1
```

## 总结

本次提交将 Iceberg 的 RAT 许可证检查配置从纯自定义排除模式优化为"标准集合 + 项目特定排除"的组合模式，移除与 RAT 0.17 内置 `GIT`/`IDEA`/`MAC` 标准集合重复的自定义项。该优化降低了排除规则的维护成本，使通用忽略类型由 RAT 官方维护，配置更清晰且更易跟进上游变化，同时不改变实际排除范围。
