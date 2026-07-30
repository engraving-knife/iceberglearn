# 提交 1581 c0bd4bfbc 分析

## 提交信息
- 哈希：c0bd4bfbceeaf3cb6e4ead675fcb47232361af3c
- 日期：2025-01-15（Wed Jan 15 00:00:24 2025 +1000）
- 作者：John Bampton <jbampton@users.noreply.github.com>
- 消息：docs: update `README.md` fix brand name `macOS` (#11964)

## 总体目的

本提交修正 `README.md` 中 Apple 桌面操作系统品牌名称的大小写写法，将"MacOS"改为正确的"macOS"。

背景：Apple 自 2016 年起将其桌面操作系统正式品牌名定为"macOS"（首字母小写 m，OS 大写），与 iOS、iPadOS、tvOS、watchOS 等命名风格保持一致。Iceberg 仓库 `README.md` 在"测试需要 Docker"的提示段落中，指导 macOS + Docker Desktop 用户为 docker socket 创建符号链接以便测试检测到 Docker，但原文将该品牌名写作"MacOS"（M 大写、OS 大写），不符合 Apple 官方品牌写法。

这是一处典型的文档品牌名规范化修正：提交者（John Bampton，常见于开源项目的文档/拼写修正贡献）将"MacOS"纠正为"macOS"，使文档在品牌名称上准确无误，体现对细节的严谨。

## 如何达成设计目的

修改集中在 `README.md` 一个文件，将 NOTE 段落中"On MacOS (with Docker Desktop)"的"MacOS"改为"macOS"。这是单单词级别的字符修正，不改变任何语义、命令或链接。

### 修改详情

#### `README.md`

**修改目的**：将 Docker 测试提示段落中的操作系统品牌名"MacOS"修正为官方写法"macOS"。

**工作逻辑**：该段落位于 README 的"引擎模块"介绍与"引擎兼容性"之间，是一个 NOTE 块，提示用户测试依赖 Docker，并针对 macOS 上 Docker Desktop 可能无法被测试检测到的情况，给出创建符号链接的命令：

```bash
sudo ln -s $HOME/.docker/run/docker.sock /var/run/docker.sock
```

修改前后对照：
```markdown
# 修改前
The tests require Docker to execute. On MacOS (with Docker Desktop), you might need to create a symbolic name to the docker socket in order to be detected by the tests:

# 修改后
The tests require Docker to execute. On macOS (with Docker Desktop), you might need to create a symbolic name to the docker socket in order to be detected by the tests:
```

仅"On MacOS"→"On macOS"一处字符变化，其余文字与命令块不变。

## 小结

- **成效**：修正了 `README.md` 中 macOS 品牌名的大小写写法（"MacOS"→"macOS"），使文档符合 Apple 官方品牌命名规范，提升文档专业度。
- **影响范围**：仅 `README.md` 一个文件，1 行 1 处字符改动，无代码、构建、测试逻辑变更。
- **回迁到 1.4.x 的注意事项**：纯文档品牌名修正，与 1.4.x 运行时无关。1.4.x 的 README 若存在同样写法可顺手回迁；若不存在或已使用正确写法，则无需回迁，对发布产物无任何影响。
