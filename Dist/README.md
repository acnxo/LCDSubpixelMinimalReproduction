# Dist —— 一键下载最新构建包

这个目录只做一件事：**双击 `Update.cmd`，把最新一次构建的发布包下载并解压到这里。**

## 用法

| 命令 | 作用 |
| --- | --- |
| 双击 `Update.cmd` | 列出最新 release 里的所有包（每个 GraalVM/JDK + OpenJFX 组合一格），输入编号选择 |
| `Update.cmd jdk23` | 按关键字挑选（选 `jdk23` 那一格） |
| `Update.cmd 25.0.4` | 也可以按 OpenJFX 版本关键字挑 |
| `Update.cmd stable` | 只认正式版（默认包含预发行版） |

下载完成后，目录里会有：

```
lcd-repro.exe          原生镜像（GraalVM native-image）
lcd-repro.jar          JVM 版（fat jar，JavaFX 已打进包里）
RunNativeImage.cmd     双击：跑原生镜像（开窗口 + 打印判据数字）
RunJVMJar.cmd          双击：跑 JVM 版（开窗口 + 打印判据数字）
RESULT.txt             构建机上的原始判据数字
```

## 说明

* 脚本走公开的 GitHub API，**不需要令牌**，也**不需要管理员权限**；
* 不走 `/releases/latest`（那个接口只认正式版，本仓库的包都是预发行），而是列最近的发布自己挑；
* 前提是那个仓库**已经有发布** —— 若还没有，先去 Actions 页面手动跑一次构建（`Run workflow`）；
* 文件被占用（例如 `lcd-repro.exe` 还在运行）时会写入失败，先关掉再重试；
* `Update.cmd` 必须保持 **CRLF 行尾 + UTF-8（无 BOM）** 编码，否则中文注释会让 cmd 解析错位。
