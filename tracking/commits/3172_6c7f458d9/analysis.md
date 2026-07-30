# 提交 3172：Build/Release: Upgrade to RAT 0.17 (#15145)

## 提交信息

- **序号**：3172 / 4088
- **哈希**：6c7f458d949c22e8fc06fa5b617429c29bed1cec
- **短哈希**：6c7f458d9
- **日期**：2026-01-28
- **作者**：Kevin Liu
- **提交说明**：Build/Release: Upgrade to RAT 0.17 (#15145)
- **PR/Issue**：#15145

## 总体目的

Apache RAT（Release Audit Tool）是 Apache 软件基金会用于发布前许可证审计的工具，扫描代码仓库中所有文件，检查是否包含合规的 Apache 许可证头，并依据排除列表跳过无需检查的文件（如二进制、配置、第三方文件等）。Iceberg 在 `dev/check-license` 脚本中调用 RAT，并维护 `dev/.rat-excludes` 排除文件。此次提交由 Kevin Liu 通过 PR #15145 将 RAT 从 `0.16.1` 升级到 `0.17`。

RAT 0.17 相较 0.16.x 对命令行接口和排除模式语法做了不兼容变更：旧版本使用 `-E <file>` 指定排除文件、`--scan-hidden-directories` 启用隐藏目录扫描，且排除文件使用旧式 glob/正则模式（如 `build`、`.git`、`.*\.sql`）；新版本则改为 `--input-exclude-file`、`--input-include-std HIDDEN_DIR`、`--output-style missing-headers`、`--log-level ERROR` 等显式子命令选项，排除文件模式也要求使用 `**` 通配的 Ant 风格 glob（如 `**/build/**`、`**/*.sql`）。

因此本次升级必须同步重写 `dev/.rat-excludes` 的模式语法与 `dev/check-license` 脚本的调用方式，否则许可证检查将无法正常运行。从提交信息的多条子提交记录（"translate exclusion expression"、"fix script"、"simplify"、"config"、"explicit"、"scan hidden dir"）可看出，作者经过多次迭代才完成旧语法到新语法的正确转换与验证。

## 如何达成设计目的

两条改动线并行：(1) 将 `.rat-excludes` 中所有排除模式从旧式语法翻译为新版 RAT 要求的 `**` 通配语法；(2) 重写 `check-license` 脚本，使用新版 RAT 的显式命令行选项替换旧选项，并简化结果解析逻辑（新版本的 `--output-style missing-headers` 直接输出缺失头部的文件，无需再用 grep 解析 `??` 标记）。

## 修改详情

### `dev/.rat-excludes` (+24/-24 lines)

**修改目的**：将排除模式翻译为 RAT 0.17 的新 glob 语法。

**工作逻辑**：
逐条将旧模式转换为新语法，保持排除范围等价：
- 目录类：`build` → `**/build/**`；`.git` → `.git/**`；`.gradle` → `.gradle/**`；`.idea` → `.idea/**`；`examples/*` → `examples/**`；`gradle/*` → `gradle/**`。
- 文件名类：`LICENSE` → `**/LICENSE`；`NOTICE` → `**/NOTICE`；`revapi.yml` → `**/revapi.yml`；`derby.log` → `**/derby.log`；`package-list` → `**/package-list`；`.rat-excludes` → `dev/.rat-excludes`（精确到 dev 目录，避免误排除同名文件）。
- 扩展名正则类（`.*\.sql` 等）改为 glob 类（`**/*.sql` 等），共覆盖 sql、iml、iws、html、css、js、svg、lock、json、bin、prefs 等 11 种扩展名。
- `.*_index.md` → `**/*_index.md`。

`.gitignore`、`.java-version`、`books.json`、`new-books.json`、`gradlew`、`sitemap.xml`、`.python-version` 等无通配的模式保持原样。

### `dev/check-license` (+8/-17 lines)

**修改目的**：适配 RAT 0.17 的新命令行接口并简化结果处理。

**工作逻辑**：
- 版本变量：`export RAT_VERSION=0.16.1` → `export RAT_VERSION=0.17`。
- RAT 调用：旧命令 `mkdir -p build; $java_cmd -jar "$rat_jar" --scan-hidden-directories -E "$FWDIR"/dev/.rat-excludes -d "$FWDIR" > build/rat-results.txt` 被替换为新命令：
  ```
  $java_cmd -jar "$rat_jar" \
    --input-exclude-file "$FWDIR"/dev/.rat-excludes \
    --input-include-std HIDDEN_DIR \
    --output-style missing-headers \
    --log-level ERROR \
    -- "$FWDIR" || exit 1
  ```
  其中 `--input-exclude-file` 替代 `-E`，`--input-include-std HIDDEN_DIR` 替代 `--scan-hidden-directories`（显式声明包含隐藏目录这一标准集），`--output-style missing-headers` 使输出只含缺失头部的文件，`--log-level ERROR` 抑制噪音日志，`-- "$FWDIR"` 指定扫描根目录。
- 结果解析：旧逻辑将输出重定向到 `build/rat-results.txt`，再用 `grep -e "??"` 提取未授权文件并判断非空则失败；新逻辑因 RAT 在有缺失头部时以非零退出码结束，故直接 `|| exit 1` 处理失败，成功时打印 `RAT checks passed.`。删除了 `mkdir -p build`、中间文件写入与 grep 解析，简化了流程。注意新版本不再生成 `build/rat-results.txt` 中间产物。

## 总结

本次提交将 Apache RAT 从 0.16.1 升级到 0.17，并同步重写排除文件模式语法与许可证检查脚本调用方式，适配新版 RAT 的不兼容命令行接口。升级后许可证审计流程更简洁（利用新版的退出码与专用输出样式），排除规则更精确（`**` 通配与目录限定），保障了 Iceberg 发布前的合规性检查持续有效。
