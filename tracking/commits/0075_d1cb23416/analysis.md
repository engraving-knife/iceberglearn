# 提交 0075：Build: Replace deprecated command with environment file (#8666)

## 提交信息

- **序号**：0075 / 4088
- **哈希**：d1cb2341667c975ad04670814e73dd2cfdd0a45b
- **短哈希**：d1cb23416
- **日期**：2023-10-19
- **作者**：Jongwoo Han
- **提交说明**：Build: Replace deprecated command with environment file (#8666)
- **PR/Issue**：#8666

## 总体目的

此提交修复了 Iceberg 仓库中 GitHub Actions 工作流 `.github/workflows/jmh-benchmarks.yml` 里仍使用已废弃的 `::set-output` 工作流命令的问题，将其替换为 GitHub 官方推荐的环境文件（`$GITHUB_OUTPUT`）写法。

背景：GitHub Actions 在 2022 年 10 月发布的安全性更新中披露，`::set-output` 命令存在命令注入风险（输出值中若含特定字符可被恶意构造），并宣布该命令将被废弃。GitHub 官方推荐改用"将 `name=value` 追加写入 `$GITHUB_OUTPUT` 环境变量指向的文件"的方式设置 step 输出。`::set-output` 在过渡期后会停止工作——届时依赖它的 step 输出会变成空值，导致下游 job 拿不到数据。

本工作流 `jmh-benchmarks.yml` 是一个 `workflow_dispatch` 手动触发的 JMH 性能基准测试工作流：`matrix` job 从用户输入的 benchmark 名称列表中过滤出以 `Benchmark` 结尾的项，构造矩阵并输出 `matrix`（矩阵内容）和 `foundlabel`（是否找到合法 benchmark 的布尔标志）。这两个输出被下游的 `show-matrix`（打印矩阵）与 `run-benchmark`（`if: foundlabel == 'true'` 才执行）job 消费。如果 `::set-output` 失效，`foundlabel` 会变成空字符串而非 `true`/`false`，`run-benchmark` 的条件判断会始终不满足，整个性能基准流水线将静默地不再执行任何 benchmark——这是一个隐蔽但影响严重的破坏。

通过提前迁移到 `$GITHUB_OUTPUT`，本提交保证该工作流在 GitHub 移除 `::set-output` 支持后仍能正常运行，是仓库 CI/CD 卫生（CI hygiene）的常规维护。

## 如何达成设计目的

整体设计非常直接：在 `set-matrix` step 的 shell 脚本中，把两处 `echo "::set-output name=<key>::<value>"` 替换为 `echo "<key>=<value>" >> $GITHUB_OUTPUT`。语义完全等价——都是为当前 step 设置名为 `matrix` 和 `foundlabel` 的输出，供后续 job 通过 `steps.set-matrix.outputs.<key>` 引用。`$GITHUB_OUTPUT` 是 GitHub Actions runner 在每个 step 启动时设置的环境变量，指向一个临时文件路径，step 内向该文件追加 `key=value` 行即可声明输出，runner 会在 step 结束时解析这些行并填入 step 的 outputs。这是当前官方推荐且唯一长期支持的写法。

## 修改详情

### `.github/workflows/jmh-benchmarks.yml`

**修改目的**：将 `set-matrix` step 中两处已废弃的 `::set-output` 命令替换为写入 `$GITHUB_OUTPUT` 环境文件。

**工作逻辑**：该 step（[jmh-benchmarks.yml:49-54](../../.github/workflows/jmh-benchmarks.yml)）先用 `jq` 从用户输入的 benchmark 列表中筛选以 `Benchmark` 结尾的项，再用 `sed` 拼成逗号分隔的矩阵字符串，最后输出 `matrix` 与 `foundlabel` 两个 step output。原写法：

```yaml
- id: set-matrix
  run: |
    matrix=$(echo '[${{ github.event.inputs.benchmarks }}]' | jq '.[] | select(endswith("Benchmark")) | .')
    matrix=$(echo $matrix | sed 's/ /,/g' | sed 's/"/\"/g')
    echo "::set-output name=matrix::[$matrix]"
    echo "::set-output name=foundlabel::$(echo "[$matrix]" | jq 'if . | length > 0 then true else false end')"
```

修改后：

```yaml
- id: set-matrix
  run: |
    matrix=$(echo '[${{ github.event.inputs.benchmarks }}]' | jq '.[] | select(endswith("Benchmark")) | .')
    matrix=$(echo $matrix | sed 's/ /,/g' | sed 's/"/\"/g')
    echo "matrix=[$matrix]" >> $GITHUB_OUTPUT
    echo "foundlabel=$(echo "[$matrix]" | jq 'if . | length > 0 then true else false end')" >> $GITHUB_OUTPUT
```

两行的 key、value 表达式均保持不变，仅把输出载体从 stdout 上的 `::set-output` 指令改为追加到 `$GITHUB_OUTPUT` 文件。job 的 `outputs` 声明（`matrix: ${{ steps.set-matrix.outputs.matrix }}`、`foundlabel: ${{ steps.set-matrix.outputs.foundlabel }}`）与下游消费方均无需改动，因为 step id 与 output key 名称不变。

## 小结

通过把 JMH 基准工作流中两处已废弃的 `::set-output` 替换为写入 `$GITHUB_OUTPUT` 环境文件的标准写法，本提交提前消除了 GitHub Actions 移除旧命令支持后该流水线静默失效的风险，属于必要的 CI 维护。
