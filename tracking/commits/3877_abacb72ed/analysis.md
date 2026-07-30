# 提交 3877：Build: Bump nessie from 0.107.6 to 0.107.9 (#16807)

## 提交信息

- **序号**：3877 / 4088
- **哈希**：abacb72edde06bf949dbbfbe80d90b2861006679
- **短哈希**：abacb72ed
- **日期**：2026-06-14 00:07:41 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.107.6 to 0.107.9 (#16807)
- **PR/Issue**：#16807

## 总体目的

由 Dependabot 自动生成的依赖升级提交，将 ProjectNessie 从 0.107.6 升级到 0.107.9。Nessie 是一个提供 Git 风格版本化数据湖能力的目录服务，Iceberg 通过 `nessie-client` 与之集成，并在测试中使用 `nessie-jaxrs-testextension` 等测试扩展。

这是一次 semver-patch 级别的升级，主要包含 bug 修复和小幅改进，属于低风险的维护性升级。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中的 `nessie` 版本变量，从 `0.107.6` 改为 `0.107.9`。该变量被多个 Nessie 组件共享，包括 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests` 和 `nessie-versioned-storage-testextension`，因此一处修改即可完成所有相关组件的同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 nessie 版本变量。

**工作逻辑**：
```toml
-nessie = "0.107.6"
+nessie = "0.107.9"
```
修改版本号定义，使所有引用该变量的 Nessie 组件同步从 0.107.6 升级到 0.107.9。

## 总结

常规的 patch 级依赖升级，将 ProjectNessie 相关组件从 0.107.6 提升到 0.107.9，主要获取上游的 bug 修复。由于是 patch 版本升级，向后兼容性风险较低。
