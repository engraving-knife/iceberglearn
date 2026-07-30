# 提交 3565：Add .factorypath to .gitignore (#16067)

## 提交信息

- **序号**：3565 / 4088
- **哈希**：bbd121220a79baa18f305a3666530db32065f614
- **短哈希**：bbd121220
- **日期**：2026-04-20 18:46:04 -0700
- **作者**：Yuming Wang
- **提交说明**：Add .factorypath to .gitignore (#16067)
- **PR/Issue**：#16067

## 总体目的

该提交将 Eclipse IDE 生成的 `.factorypath` 文件添加到 `.gitignore` 中。`.factorypath` 是 Eclipse 中 Java 开发工具（JDT）和注解处理插件（如 Apt/M2E）生成的文件，用于存储工厂路径配置（注解处理器的类路径）。该文件是 IDE 特定的本地配置，不应纳入版本控制，与已忽略的 `.classpath`、`.project`、`.settings` 等 Eclipse 文件类似。

## 如何达成设计目的

在 `.gitignore` 的 "vscode/eclipse files" 注释块中，在 `.classpath` 后面添加 `.factorypath` 条目，使 Git 忽略该文件。

## 修改详情

### `.gitignore` (+1/-0 lines)

**修改目的**：忽略 Eclipse 生成的 `.factorypath` 文件。

**工作逻辑**：
在已有的 Eclipse 文件忽略列表中新增一行：
```
.classpath
+.factorypath
.project
```

## 总结

这是一个简单的项目维护提交，防止 Eclipse IDE 生成的 `.factorypath` 文件被意外提交到版本库，保持工作目录整洁。
