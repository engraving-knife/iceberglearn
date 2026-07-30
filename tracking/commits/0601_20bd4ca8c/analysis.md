# 提交 0601：Build: Fix ignoring major version update in dependabot

## 提交信息

- **序号**：0601 / 4088
- **哈希**：20bd4ca8cb2fdb8e2bb51bb41684d4bf7260d474
- **短哈希**：20bd4ca8c
- **日期**：2024-03-18（Mon Mar 18 15:39:05 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Build: Fix ignoring major version update in dependabot (#9981)

  完整提交说明：
  > I got the config wrong in the previous attempt #9806. This PR fixes it following the [official example](https://github.blog/changelog/2021-05-21-dependabot-version-updates-can-now-ignore-major-minor-patch-releases/)

- **PR/Issue**：#9981（修复 PR #9806 / 序号 0538 的配置错误）

## 总体目的

本提交是对前序提交 0538（PR #9806，"Build: Ignore major version update in dependabot"）的紧急修复。0538 试图通过 `.github/dependabot.yml` 让 Dependabot 不再为依赖自动发起主版本（semver-major）升级 PR，但由于 YAML 缩进写错，导致 `ignore` 规则被解析为 `updates` 列表下的一个独立列表项，而不是挂载在某个 `package-ecosystem` 条目下。Dependabot 规范要求 `updates` 下每个列表项必须包含 `package-ecosystem` 与 `directory`，单独的 `- ignore:` 条目是无效配置，因此 0538 实际并未生效。

本提交修复该 YAML 结构缺陷，按照 Dependabot 官方示例把 `ignore` 规则正确地嵌套到三个生态（`github-actions`、`gradle`、`pip`）各自的配置块内部，从而真正达成"屏蔽所有依赖主版本自动升级 PR"的设计目标。

## 如何达成设计目的

提交者遵循 Dependabot 官方博客示例的写法，把 `ignore` 作为各 `package-ecosystem` 条目的子字段（与 `schedule`、`open-pull-requests-limit` 同级），而非作为 `updates` 列表下的新列表项。具体做法是：

1. **删除错误的尾部块**：移除原文件末尾的 `- ignore:` 列表项及其下属两行（`dependency-name: "*"` 与 `update-types: [...]`），这部分在 YAML AST 中被解析为 `updates` 数组的第四个元素，不符合 Dependabot schema。

2. **为三个生态分别添加 ignore 子块**：在 `github-actions`、`gradle`、`pip` 三个 `package-ecosystem` 条目内部，紧跟 `schedule` 或 `open-pull-requests-limit` 之后，分别添加：
   ```yaml
       ignore:
         - dependency-name: "*"
           update-types: ["version-update:semver-major"]
   ```
   这里 `ignore` 字段值为一个列表，列表项通过 `dependency-name: "*"` 通配匹配该生态下所有依赖，通过 `update-types: ["version-update:semver-major"]` 指定只忽略主版本升级类型。Dependabot 仍会发起次要版本（semver-minor）和补丁版本（semver-patch）的 PR。

3. **生态覆盖范围扩展**：相比 0538 原本只针对 gradle 一处（且未生效）的意图，本提交把忽略规则均匀应用到全部三个生态，符合"对所有依赖统一屏蔽主版本升级"的总体策略。

通过这种"分别嵌套"的写法，每条 `ignore` 规则在 YAML 结构上明确归属到对应的 `package-ecosystem` 块，Dependabot 在解析时能正确识别并应用，从而实现预期效果。

## 修改详情

### `.github/dependabot.yml`

**修改目的**：修复 0538 引入的 YAML 缩进错误，让 Dependabot 真正忽略三个生态下所有依赖的主版本升级。

**工作逻辑**：

修改前（0538 留下的状态）的 `updates` 列表结构如下（伪 YAML）：
```yaml
updates:
  - package-ecosystem: "github-actions"   # 无 ignore
    ...
  - package-ecosystem: "gradle"           # 无 ignore
    ...
  - package-ecosystem: "pip"
    ...
  - ignore:                               # 错误：作为第四个列表项，schema 非法
    dependency-name: "*"
    update-types: ["version-update:semver-major"]
```
Dependabot 解析时会把第四项当作一个新的更新配置，但它缺少必填的 `package-ecosystem` 与 `directory`，因此该条目被忽略，三个真实生态的更新配置都没有 `ignore` 规则，主版本升级 PR 仍会被发起。

修改后（本提交）的结构：
```yaml
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "sunday"
    ignore:
      - dependency-name: "*"
        update-types: ["version-update:semver-major"]
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "sunday"
    open-pull-requests-limit: 50
    ignore:
      - dependency-name: "*"
        update-types: ["version-update:semver-major"]
  - package-ecosystem: "pip"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "sunday"
    open-pull-requests-limit: 5
    ignore:
      - dependency-name: "*"
        update-types: ["version-update:semver-major"]
```

关键差异：
- `ignore` 字段缩进为 4 空格，与 `package-ecosystem`、`directory`、`schedule`、`open-pull-requests-limit` 同级，明确作为某个 `package-ecosystem` 条目的属性；
- `ignore` 下的列表项 `- dependency-name: "*"` 缩进为 6 空格，`update-types` 缩进为 8 空格，符合 YAML 嵌套规范；
- 文件末尾不再有游离的 `- ignore:` 列表项，`updates` 列表正好三个元素，与三个生态一一对应。

这样 Dependabot 在为每个生态生成 PR 前会先过滤掉 `version-update:semver-major` 类型的更新，例如 `junit:junit 4.x → 5.x`、`org.apache.spark:spark-sql_2.12 3.x → 4.x` 这类跨主版本升级会被自动跳过，而 `4.13.1 → 4.13.2` 这类补丁升级仍会正常发起 PR。

## 小结

本提交是 0538 的配套修复，通过把 `ignore` 块正确嵌套到三个 `package-ecosystem` 条目内部，使 Dependabot 真正按预期忽略所有依赖的主版本升级 PR。改动只涉及 `.github/dependabot.yml` 一个文件，9 行新增、4 行删除，无任何代码或测试影响。

- **影响范围**：仅 CI/CD 自动化流程。修复生效后，Dependabot 周期运行时不再为主版本升级开 PR，次要版本与补丁版本升级不受影响。
- **关联提交**：必须与 0538 一起看。0538 引入了错误配置（未生效），本提交修正了配置。若仅回迁本提交而不回迁 0538，则原始 `dependabot.yml` 中没有 ignore 意图、本提交的 diff 上下文也无法对齐；若仅回迁 0538，则会引入无效配置。
- **回迁到 1.4.x 的注意事项**：建议直接采用本提交的最终写法（嵌套 ignore 形式）。若 1.4.x 分支的 `dependabot.yml` 与 main 一致，可干净 cherry-pick；若 1.4.x 上 0538 也已存在，则本提交作为修复 cherry-pick 后即可。回迁后可在下一次 Dependabot 周期运行后观察是否还出现主版本升级 PR 来验证配置生效。
