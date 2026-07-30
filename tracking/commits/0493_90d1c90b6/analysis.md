# 提交 0493：Docs: Fix hidden-partition-animation not showing (#9686)

## 提交信息

| 字段 | 内容 |
|------|------|
| 序号 | 0493 |
| 完整哈希 | 90d1c90b6e6f26fdfe7c0c6c09a1ecb2fc2b3f2a |
| 短哈希 | 90d1c90b6 |
| 日期 | 2024-02-08 17:47:53 +0300 |
| 作者 | Muna Bedan <45054928+munabedan@users.noreply.github.com> |
| 说明 | Docs: Fix hidden-partition-animation not showing (#9686) |
| PR | #9686 |

文件统计：2 个文件，2 行新增 / 1 行删除。
- site/docs/assets/lottie/hidden-partitioning-animation.json：新增文件（1 行，约 60KB 单行 JSON）
- site/overrides/home.html：1 行修改

## 总体目的

本提交修复 Iceberg 官方文档站点首页上一个"隐藏分区（hidden partitioning）"动画无法显示的问题。首页通过 `lottie-player` Web 组件加载一个 Lottie 动画，用于直观展示 Iceberg 隐藏分区特性——即用户写入数据时无需显式指定分区列，Iceberg 会根据表的分区 transform 自动将数据路由到对应分区的机制。该动画原本通过绝对 URL 指向 `https://iceberg.apache.org/lottie/hidden-partitioning-animation.json` 加载，但由于该远程资源缺失或路径变化，导致动画无法在页面上正常播放。

修复思路是把动画资源本地化：将 Lottie 动画 JSON 文件直接放入文档站点的静态资源目录 `site/docs/assets/lottie/`，并把 `lottie-player` 的 `src` 从远程绝对 URL 改为站内相对路径 `assets/lottie/hidden-partitioning-animation.json`。这样动画文件随文档仓库一同版本化管理，不再依赖外部站点的资源可用性，从根本上消除了"远程文件丢失导致动画不显示"的故障。

这一改动也体现了文档站点对静态资源管理的改进方向：关键展示资源应纳入仓库自有资产目录，而非依赖外部 URL，以保证离线构建、镜像部署和长期可访问性。

## 如何达成设计目的

实现路径分两步：第一步在 `site/docs/assets/lottie/` 下新增 `hidden-partitioning-animation.json` 动画资源文件（这是 Lottie 格式的 JSON，单行约 60KB，包含动画的图层、形状、关键帧等完整定义）；第二步修改 `site/overrides/home.html` 中 `<lottie-player>` 标签的 `src` 属性，将远程地址替换为本地相对路径，使页面渲染时直接从站点自身资源加载动画。

## 修改详情

### site/docs/assets/lottie/hidden-partitioning-animation.json

**修改目的**：将隐藏分区动画的 Lottie 资源纳入文档仓库，作为本地静态资源提供。

**工作逻辑**：这是一个新增文件（`new file mode 100644`），内容为单行 Lottie JSON。文件头部元数据表明其由 LottieFiles AE 0.1.20 导出，原始作者标记为 "Blockcities"，帧率 `fr:30`，总帧数 `op:600`（即 20 秒动画），画布尺寸 `w:2500, h:2160`，名称 `nm:"TechnoCub"`。该 JSON 描述了动画的全部图层（`layers`）、形状（`shapes`）、关键帧动画（带 `a:1` 的属性）以及填充效果等，是 `lottie-player` 渲染动画所需的完整数据。文件体积约 60KB，作为静态资源由 mkdocs 直接托管。由于是二进制性质的动画资源，其内容本身不需人工维护，重点在于其存在性与路径正确性。

### site/overrides/home.html

**修改目的**：将首页 Lottie 动画的加载源从远程 URL 切换为本地相对路径。

**工作逻辑**：该文件是 mkdocs Material 主题的首页覆盖模板，第 216 行附近使用 `lottie-player` Web 组件加载动画。修改前：

```html
<script src="https://unpkg.com/@lottiefiles/lottie-player@latest/dist/lottie-player.js"></script>
<lottie-player src="https://iceberg.apache.org/lottie/hidden-partitioning-animation.json"
  background="transparent"
  speed="0.5"
  style="width: 430px; height: 400px"
```

修改后：

```html
<script src="https://unpkg.com/@lottiefiles/lottie-player@latest/dist/lottie-player.js"></script>
<lottie-player src="assets/lottie/hidden-partitioning-animation.json"
  background="transparent"
  speed="0.5"
  style="width: 430px; height: 400px"
```

唯一变化是 `lottie-player` 的 `src` 属性由 `https://iceberg.apache.org/lottie/hidden-partitioning-animation.json` 改为 `assets/lottie/hidden-partitioning-animation.json`。注意 `lottie-player` 的加载脚本（`lottie-player.js`）仍从 unpkg CDN 加载，本次未改动；仅动画数据源被本地化。由于该相对路径基于文档站点根，而 `site/docs/` 是 mkdocs 的 docs 目录，`assets/lottie/` 下的文件会被原样复制到站点输出，因此相对路径 `assets/lottie/...` 能在最终站点正确解析。

## 小结

本次提交是 Iceberg 1.4.x 周期内的一个文档站点 bug 修复，规模小（新增 1 个资源文件 + 改 1 行 HTML），但精准解决了首页隐藏分区动画不显示的问题。根因是动画资源依赖的远程 URL 失效，修复方式是将动画 JSON 本地化到 `site/docs/assets/lottie/` 并把 `lottie-player` 的 `src` 改为站内相对路径。该改动使动画资源随仓库版本化、不再受外部站点可用性影响，提升了文档站点的自包含性与稳定性，回溯风险极低。
