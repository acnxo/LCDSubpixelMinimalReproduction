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
build-report/lcd-repro-build-report.html   native-image 构建报告（JDK 23 及以上各档）
```

> 注：JDK 17 一档（及其所属的一组旧版本）所用 GraalVM 不提供 `--emit build-report`，包内不含该文件。


## 说明

* 脚本走公开的 GitHub API，**不需要令牌**，也**不需要管理员权限**；
* 不走 `/releases/latest`（那个接口只认正式版，本仓库的包都是预发行），而是列最近的发布自己挑；
* 前提是那个仓库**已经有发布** —— 若还没有，先去 Actions 页面手动跑一次构建（`Run workflow`）；
* 文件被占用（例如 `lcd-repro.exe` 还在运行）时会写入失败，先关掉再重试；
* `Update.cmd` 必须保持 **CRLF 行尾 + UTF-8（无 BOM）** 编码，否则中文注释会让 cmd 解析错位。

## 控制台窗口（原生镜像）

`lcd-repro.exe` 是**控制台子系统**程序，双击时 Windows 会先开一个控制台窗口。现在的行为：

| 启动方式 | 控制台窗口 |
| --- | --- |
| 双击 `lcd-repro.exe` | **自动隐藏**（只剩 GUI；可能有极短的一闪） |
| 双击 `RunNativeImage.cmd` | **保持可见** —— 窗口由 cmd 与程序共享，判据数字打在这里 |
| 从 cmd / CI 直接运行 | 同上，保持可见（脚本采集数字的方式不变） |
| 要调试日志时 | 加 `--console` 或 `-Dlcdrepro.console=true`，窗口保持显示 |

> 为什么不干脆改成窗口子系统？那样「从控制台运行时打印判据数字」这条通路就断了 —— 脚本与上游核对都依赖它。
> 所以只隐藏**双击自带的那一个**窗口。
