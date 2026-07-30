# 提交 0179：Open-API: Remove pydantic pin (#9110)

## 提交信息

- **序号**：0179 / 4088
- **哈希**：9e94dc85764dd1321c9782a2a0d834a9bfd84b7d
- **短哈希**：9e94dc857
- **日期**：2023-11-19 10:39:06 +0100
- **作者**：Fokko Driesprong
- **提交说明**：Open-API: Remove pydantic pin (#9110)
- **PR/Issue**：#9110

## 总体目的

`open-api/requirements.txt` 中此前有一行 `pydantic<2.4.0`，并附注释 "Add the Pydantic constraint since 2.4.0 has a bug"。这是因为在 pydantic 2.4.0 发布时存在一个影响 `datamodel-code-generator` 与 `openapi-spec-validator` 链路的回归 bug，Iceberg 团队当时通过把 pydantic 钉在 `<2.4.0` 来规避。这种版本上界钉扎虽然能临时止血，但会拖累整个 Python 构建链无法享受 pydantic 2.4.x 及之后版本的修复与改进，也容易与下游消费者（如 Iceberg REST 服务、客户端生成产物）所在环境的 pydantic 版本产生冲突。

本提交的目的就是移除这条临时性的 pydantic 上界钉扎。其前置条件是相邻提交 0177（`datamodel-code-generator` → 0.24.2）与 0178（`openapi-spec-validator` → 0.7.1）已经把这两个直接依赖升级到了与较新 pydantic 兼容的版本；同时上游 pydantic 在 2.4.0 之后的 patch 版本中也已经修复了当初触发钉扎的 bug。因此该钉扎已无必要，可以安全解除，让 `open-api` 模块重新跟随 pydantic 主线。

这是 Iceberg OpenAPI 工具链依赖治理的一环：先把直接依赖升到能适配新 pydantic 的版本，再放开传递依赖的版本钉，恢复依赖图的"可演进性"。

## 如何达成设计目的

通过删除 `open-api/requirements.txt` 中的两行（注释行 + `pydantic<2.4.0` 钉扎行）来达成。删除后，pydantic 不再显式出现在该 requirements 文件中，其版本将由 `openapi-spec-validator==0.7.1` 与 `datamodel-code-generator==0.24.2` 的传递依赖自动解析，从而能自然采用 pydantic 2.4.x 及之后的稳定版本。

## 修改详情

### `open-api/requirements.txt`

**修改目的**：移除临时性的 `pydantic<2.4.0` 上界钉扎及其解释性注释，使 pydantic 版本由上游直接依赖传递解析。

**工作逻辑**：删除文件末尾两行：
```
# Add the Pydantic constraint since 2.4.0 has a bug
pydantic<2.4.0
```
保留 `openapi-spec-validator==0.7.1` 与 `datamodel-code-generator==0.24.2`（前者由 0178 引入，后者由 0177 引入）。删除后该文件不再对 pydantic 做任何显式约束，构建时 pip 会根据这两个直接依赖各自声明的 pydantic 版本范围解析出可用的最新版本——前提正是 0177/0178 已经把直接依赖升到不再受 pydantic 2.4.0 bug 影响的版本。

## 小结

本提交解除了为规避 pydantic 2.4.0 bug 而设的临时版本上界钉扎，配合相邻的依赖升级使 Iceberg OpenAPI 构建链重新能跟随 pydantic 主线演进。
