# 提交 3535：Build: Fix codeql-action version comment to match pinned SHA (#15985)

## 提交信息

- **序号**：3535 / 4088
- **哈希**：b710f476c6509b25435fb573888bd34cea17a4a2
- **短哈希**：b710f476c
- **日期**：2026-04-15 10:52:33 -0700
- **作者**：Robin Moffatt
- **提交说明**：Build: Fix codeql-action version comment to match pinned SHA (#15985)
- **PR/Issue**：#15985

## 总体目的

在 GitHub Actions 工作流 `codeql.yml` 中，`github/codeql-action/init` 和 `analyze` 两个 step 通过 pin SHA（`c10b8064...`）固定版本，并在行尾注释 `# v4` 标注版本号。但 zizmor（一个 GitHub Actions 安全扫描工具）的 `ref-version-mismatch` 规则检测到：SHA `c10b8064` 实际对应的版本是 `v4.35.1`，而注释写的是 `v4`（rolling tag），两者不匹配。

zizmor 之所以关注这个，是因为 `v4` 是一个会移动的 rolling tag，而项目实际 pin 的是具体 SHA。注释写 `v4` 会让人误以为用的是 rolling tag 的内容，造成审计困惑。本提交将注释从 `# v4` 更正为 `# v4.35.1`，与 pin 的 SHA 实际对应版本一致，消除 zizmor 告警。

## 如何达成设计目的

直接修改两处 `uses` 行尾的注释，从 `# v4` 改为 `# v4.35.1`，与 SHA `c10b8064de6f491fea524254123dbe5e09572f13` 对应的实际 release 版本一致。SHA 本身不变。

## 修改详情

### `.github/workflows/codeql.yml` (+2/-2 lines)

**修改目的**：修正 codeql-action 版本注释，与 pin 的 SHA 实际版本对齐。

**工作逻辑**：
```yaml
-      uses: github/codeql-action/init@c10b8064de6f491fea524254123dbe5e09572f13 # v4
+      uses: github/codeql-action/init@c10b8064de6f491fea524254123dbe5e09572f13 # v4.35.1
...
-      uses: github/codeql-action/analyze@c10b8064de6f491fea524254123dbe5e09572f13 # v4
+      uses: github/codeql-action/analyze@c10b8064de6f491fea524254123dbe5e09572f13 # v4.35.1
```
仅注释文本变化，`uses` 的 SHA 引用不变。

## 总结

本提交修正了 `codeql.yml` 工作流中 codeql-action 版本注释与 pin SHA 不匹配的问题，将 `# v4` 更正为 `# v4.35.1`，消除 zizmor 安全扫描的 `ref-version-mismatch` 告警。属于 CI 配置的文档准确性改进，不影响实际执行的 action 版本。
