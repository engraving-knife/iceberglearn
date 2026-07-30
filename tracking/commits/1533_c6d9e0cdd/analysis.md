# 提交 1533 c6d9e0cdd 分析

## 提交信息
- 哈希：c6d9e0cdd0f30d550bacdcd51acb4e68b9d5b791
- 日期：2024-12-24（Tue Dec 24 11:05:07 2024 +0100）
- 作者：JB Onofré <jbonofre@apache.org>
- 消息：Gradle: Update `gradlew` with better `APP_HOME` definition (#11869)

## 总体目的

`gradlew` 是 Gradle Wrapper 提供的启动脚本，用于在不依赖本地 Gradle 安装的情况下引导执行 Gradle 构建。脚本中通过 `APP_HOME` 变量定位 wrapper jar 与Gradle 发行版的安装目录，其计算逻辑需在多种 shell 环境（bash、sh、dash 等）与文件系统布局（含符号链接）下都能正确解析出脚本所在目录的绝对物理路径。

原实现使用 `cd "${APP_HOME:-./}" > /dev/null && pwd -P` 来获取物理路径：先切换到 `APP_HOME` 目录（默认当前目录），再调用 `pwd -P` 输出物理路径（解析符号链接）。这种写法在大多数 shell 下可用，但存在两个潜在问题：一是 `pwd -P` 依赖外部 `pwd` 命令的实现，在某些精简或非标准环境（如 BusyBox、某些 BSD 变体）中行为可能不一致；二是 `cd` 未加 `-P` 选项时，若路径中包含符号链接，`cd` 后的 `$PWD` 可能仍是逻辑路径，再调用 `pwd -P` 才解析，多了一层间接。

本提交将 `APP_HOME` 定义更新为更稳健的写法 `cd -P "${APP_HOME:-./}" > /dev/null && printf '%s\n' "$PWD"`。这与上游 Gradle 8.12 发行版中 `gradlew` 模板保持一致，属于对 wrapper 脚本的可移植性增强，而非功能变更。

## 如何达成设计目的

仅修改 `gradlew` 文件中计算 `APP_HOME` 的那一行。具体做法是给 `cd` 加上 `-P` 选项（让 `cd` 在切换目录时直接解析符号链接到物理路径），并用 shell 内建的 `printf` 输出 `$PWD` 变量，替代外部 `pwd -P` 命令。

### 修改详情

#### `gradlew`

**修改目的**：使 `APP_HOME` 的计算在不同 shell 与文件系统布局下更稳健、更可移植。

**工作逻辑**：

修改前：
```sh
APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P ) || exit
```

修改后：
```sh
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && printf '%s\n' "$PWD" ) || exit
```

两处关键变化：

1. **`cd` 增加 `-P` 选项**：`cd -P` 在切换目录时即解析符号链接至物理路径，使后续 `$PWD` 直接反映物理路径，行为在 POSIX shell 间更一致。
2. **用 `printf '%s\n' "$PWD"` 替代 `pwd -P`**：`$PWD` 是 shell 内建变量，`printf` 也是 shell 内建命令，无需调用外部 `pwd`，避免了对系统 `pwd` 实现差异的依赖，提升了在受限/嵌入式 shell 环境下的兼容性。

`> /dev/null` 仍用于丢弃 `cd` 可能因 `$CDPATH` 设置产生的标准输出（参见注释引用的 gradle/gradle#25036）。`|| exit` 保留原行为：若 `cd` 失败则脚本退出。

## 小结

- **成效**：与上游 Gradle 8.12 wrapper 模板对齐，提升 `gradlew` 在不同 shell（特别是符号链接环境与精简 shell）下的可移植性与稳健性。
- **影响范围**：仅 `gradlew` 文件 1 行变更，无代码、构建逻辑或依赖变化。
- **回迁到 1.4.x 的注意事项**：这是 wrapper 脚本的小幅可移植性增强，对 1.4.x 构建产物运行时无任何影响。1.4.x 已发布的 wrapper jar 不受 `gradlew` 脚本变更影响，**无需回迁**；若 1.4.x 后续仍要发版构建，可选择性同步以获得更稳健的 wrapper，但不回迁也不会有问题。
