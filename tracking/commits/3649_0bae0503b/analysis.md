# 提交 3649：Build: Correct actions/labeler version comment to v6.0.1 (#16225)

## 提交信息

- **序号**：3649 / 4088
- **哈希**：0bae0503bcec99e5b725da18430458f38749888c
- **短哈希**：0bae0503b
- **日期**：2026-05-06 11:22:23 +0200
- **作者**：Maximilian Michels
- **提交说明**：Build: Correct actions/labeler version comment to v6.0.1 (#16225)
- **PR/Issue**：#16225

## 总体目的

这个提交修正了 GitHub Actions `labeler.yml` 工作流中 `actions/labeler` action 的版本注释。

Iceberg 项目在 GitHub Actions 中使用 pin 到具体 commit SHA 的方式引用 actions（出于安全考虑），并在注释中标注版本号以便追踪。此前 `actions/labeler` 的 commit SHA `634933edcd8ababfe52f92936142cc22ac488b1b` 对应的实际版本是 v6.0.1，但注释只写了 `# v6`，不够精确。本提交将注释更正为 `# v6.0.1`，使版本标注与实际 release 版本一致，便于后续升级和维护时识别具体版本。

## 如何达成设计目的

直接修改 `labeler.yml` 中的注释文本，从 `# v6` 改为 `# v6.0.1`，commit SHA 保持不变。

## 修改详情

### `.github/workflows/labeler.yml` (+1/-1 line)

**修改目的**：更正 labeler action 的版本注释。

**工作逻辑**：
```yaml
# 旧
- uses: actions/labeler@634933edcd8ababfe52f92936142cc22ac488b1b # v6
# 新
- uses: actions/labeler@634933edcd8ababfe52f92936142cc22ac488b1b # v6.0.1
```

## 总结

这是一个极小的 CI 配置修正提交，仅更正了 `labeler.yml` 中 `actions/labeler` action 的版本注释，从模糊的 `# v6` 改为精确的 `# v6.0.1`，使版本标注与实际使用的 release 一致。commit SHA 不变，不影响实际行为。
