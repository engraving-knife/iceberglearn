# 提交 3984：Build: Bump actions/setup-java from 5.3.0 to 5.4.0 (#17104)

## 提交信息

- **序号**：3984 / 4088
- **哈希**：68aa4294ef080755db8f430652b50bbd6efcc2f0
- **短哈希**：68aa4294e
- **日期**：2026-07-06 10:21:32 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/setup-java from 5.3.0 to 5.4.0 (#17104)
- **PR/Issue**：#17104

## 总体目的

Dependabot 自动升级 GitHub Actions 的 `actions/setup-java` 从 v5.3.0 到 v5.4.0，次版本升级。这是提交 3961 升级到 v5.3.0 后的后续升级。

## 如何达成设计目的

批量更新所有 GitHub workflow 文件中的 `actions/setup-java` 引用，更新 commit SHA。

## 修改详情

### 多个 `.github/workflows/*.yml` 文件

**修改目的**：升级 setup-java action 版本。

**工作逻辑**：将 `actions/setup-java@ad2b38190b15e4d6bdf0c97fb4fca8412226d287 # v5.3.0` 更新为 `actions/setup-java@1bcf9fb12cf4aa7d266a90ae39939e61372fe520 # v5.4.0`。

## 总结

常规 CI 工具链升级，将 actions/setup-java 从 v5.3.0 升级到 v5.4.0。
