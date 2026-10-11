# JavaFX LCD 次像素文本合成退化 —— 最小复现（GraalVM native-image）

本仓库是下述渲染问题的**最小可复现工程**：同一份 JavaFX 代码以 `JVM Jar` 方式运行时 LCD 次像素文本合成正常，
经 GraalVM `native-image` 编译后，三个颜色通道的覆盖被压缩为同一个标量，并呈二值化。

仓库只包含复现所需的源码、配置与文档，不含任何应用代码。

![native-image 与 JVM Jar 的窗口对比](https://github.com/user-attachments/assets/2a353f17-4f01-4c7d-aceb-e6157e78b47c)

## 1. 问题描述

### 1.1 观测数据

在同一台 Windows 11 x64 机器（Intel UHD，显示缩放 1.5×）上分别以两种方式运行同一份程序，
输出「黑字白底、14px」样例的三通道覆盖量：

| 运行方式 | `per-channel ink` | 结论 |
| --- | --- | --- |
| `JVM Jar`（`java -jar`） | `73135 / 73618 / 73245`，`collapsed=false` | 三通道互不相等，合成正确 |
| `native-image` | `105825 / 105825 / 105825`，`collapsed=true` | 三通道完全相同，问题复现 |

两点补充：

* `JVM Jar` 侧的数值受运行环境影响，不同机器间约有 ±0.06% 的差异；
* `native-image` 侧的输出在不同环境下**逐位相同** —— 二值化抹平了差异。

### 1.2 行为模型

* 期望：`out_c = mix(bg_c, fg_c, m_c)`，即每个颜色通道各自使用自己的覆盖 `m_c`；
* 实际：`out_c = mix(bg, fg, s)`，即三通道共用同一个标量 `s`，且 `s` 呈二值化。

该模型由上述观测数据推得；推导过程与被排除项见第 7 节。

## 2. 运行环境

| 项 | 要求 |
| --- | --- |
| 操作系统 | Windows x64 |
| 观感观察 | 需真实 GPU 且 ClearType 生效；CI 机器无 GPU（D3D 走 WARP），不适合做观感判断 |
| JDK / GraalVM | GraalVM for JDK 17 / 20 / 21 / 22 / 23 / 24 / 25（版本矩阵覆盖） |
| JavaFX | 与 JDK 对应的版本；另有 JDK 25 + JavaFX 27 一格 |
| 构建工具 | Maven |

## 3. 快速开始

### 3.1 使用发布包（推荐）

```bat
Dist\Update.cmd            :: 列出最新发布里的全部包，按提示输入编号
Dist\Update.cmd jdk23      :: 按关键字挑选（也可用 25.0.4 等）
Dist\Update.cmd stable     :: 只取正式版
```

下载并解压后，在同一目录下双击两个入口脚本即可对比：

```bat
Dist\RunNativeImage.cmd    :: 原生镜像（lcd-repro.exe）
Dist\RunJVMJar.cmd         :: JVM 版（lcd-repro.jar）
```

两个脚本都只运行**自身所在目录**中的程序（用 `%~dp0` 定位），不会误用 `PATH` 中的同名程序；
程序缺失时会给出明确提示。

### 3.2 从源码构建

```bat
mvn -B -ntp package -Dmaven.compiler.release=25 -Djavafx.version=25.0.4
mvn -B -ntp dependency:copy-dependencies -DoutputDirectory=target/lib

:: `JVM Jar` 侧（应打印 collapsed=false / OK）
java -cp "target/classes;target/lib/*" LcdSubpixelRepro

:: 原生镜像侧（应打印 collapsed=true，即复现）
native-image -cp "target/classes;target/lib/*" -o target/lcd-repro LcdSubpixelRepro
target\lcd-repro.exe
```

CI 中另外把 `classes` 与依赖合并为自包含的 `lcd-repro.jar`（见工作流 `BuildFatJar` 步骤），
发布包内的 JVM 版即为该 fat jar。

## 4. 判据

黑字白底时，正确的 LCD 次像素合成必然满足 `out_c = 1 − m_c`，因此 **R/G/B 三通道的覆盖量不可能相等**。

| 运行方式 | 预期输出 |
| --- | --- |
| `JVM Jar` | `per-channel ink = <三值不等>   collapsed=false` → `OK` |
| `native-image` | `per-channel ink = <三值相同>   collapsed=true` → `*** BUG ***` |

* 默认**带界面**：两个入口脚本各开一个窗口，便于并排目视对比，同时判据数字打印到控制台；
* 仅需数字（脚本化 / CI）时加 `--headless`，例如 `lcd-repro.exe --headless`；
* 注意：`-Dprism.lcdtext=false` 是绕开该问题的灰度渲染路径，不属于本复现的观察对象。

## 5. 版本矩阵与发布物

CI 对下列 8 个组合各构建一次，并分别执行上述两个判据：

| # | GraalVM for JDK | JavaFX | # | GraalVM for JDK | JavaFX |
| --- | --- | --- | --- | --- | --- |
| 1 | 17 | 17.0.20 | 5 | 23 | 23.0.2 |
| 2 | 20 | 20.0.2 | 6 | 24 | 24.0.2 |
| 3 | 21 | 21.0.12 | 7 | 25 | 25.0.4 |
| 4 | 22 | 22.0.2 | 8 | 25 | 27 |

工作流（`.github/workflows/LcdSubpixelReproMatrix.yml`）：

* **触发方式**：PR 合并到 `dev` 或 `main` 后自动运行；也可在 Actions 页面手动触发
  （手动触发可填写 PR 编号用于命名，见下）；
* **产物**：每格的编译产物打包后发布到 Releases（预发行）：

| 项 | 规则 |
| --- | --- |
| 发布名（同时作为 tag） | `V<Maven 版本>.<修订号>-LCDSubpixelRepro` |
| 修订号 | 触发构建的 PR 编号，5 位左补 0（PR #1 → `00001`）；手动触发未填编号时为时间戳 `yyyy.MMdd.HHmm` |
| 资产名 | `lcd-subpixel-repro-V<Maven 版本>.<修订号>-jdk<JDK 主版本>-openjfx<OpenJFX 版本>.zip` |

每个包内含（扁平结构；解压后即位于 `Dist` 目录）：

```
lcd-repro.exe           原生镜像
lcd-repro.jar           JVM 版（自包含 fat jar）
RunNativeImage.cmd      双击运行原生镜像
RunJVMJar.cmd           双击运行 JVM 版
RESULT.txt              构建机上的原始判据数字
```

各档实测（本次核对）：JDK 23、JDK 25、JDK 25 × JavaFX 27 三档的包内含
`build-report/lcd-repro-build-report.html`；JDK 17 一档（及其所属的一组旧版本）
所用 GraalVM 不提供该开关，包内不含该目录 —— 不影响判据与复现。

CI 构建机没有 GPU，D3D 走 WARP（软件实现，仍为真实 D3D 后端）。因此 CI 的数字足以判断
**问题是否复现**，但观感与像素级结论仍应以真实机器为准。

## 6. 仓库结构

```text
.
├── .github
│   ├── workflows
│   │   └── LcdSubpixelReproMatrix.yml           8 格版本矩阵构建与发布（PR 合并触发）
│   └── dependabot.yml                           依赖自动升级（Maven + GitHub Actions，每月一次，PR 提交到 `dev`）
├── Dist
│   ├── README.md                                `Dist` 目录的用法说明
│   ├── RunJVMJar.cmd                            双击：运行**本目录**的 `lcd-repro.jar`（JVM 版）
│   ├── RunNativeImage.cmd                       双击：运行**本目录**的 `lcd-repro.exe`（原生镜像）
│   ├── Update.cmd                               双击：下载最新构建包到本目录（列出全部包 / 按关键字挑选 / `stable`）
│   └── Update.ps1                               `Update.cmd` 的实际逻辑：查询发布 → 挑包 → 下载 → 解压
├── src
│   └── main
│       ├── java
│       │   └── LcdSubpixelRepro.java            复现程序（有效代码 44 行）：LCD 文本 → snapshot → 统计三通道覆盖量
│       └── resources
│           └── META-INF
│               └── native-image
│                   ├── jni-config.json          native-image JNI 元数据（供 JavaFX 使用）
│                   ├── native-image.properties  native-image 构建参数（`--no-fallback` + JavaFX 运行期初始化）
│                   ├── reflect-config.json      native-image 反射元数据（供 JavaFX 使用）
│                   └── resource-config.json     native-image 资源元数据（供 JavaFX 使用）
├── .gitignore                                   忽略构建产物与常见工程文件（Java 模板 + `target/`、`build/`）
├── LICENSE                                      GNU GPL v3 完整授权文本
├── pom.xml                                      最小 Maven 工程，仅依赖 JavaFX base / graphics / controls
└── README.md                                    本文件
```

## 7. 已排除的可能原因

以下因素均已核实为**两构建相同**，故不构成本问题的成因：

* DirectWrite 字形掩码：两构建逐字节相同；
* 纹理上传：同一 `prism_d3d.dll` 的原生调用；
* 着色器 `.obj` 与 `*_Loader` 常量表；
* `MaskData` / `GlyphCache` 的几何与行距；
* `PrismSettings` 全部 44 个字段与类初始化策略（审计结果全为运行期初始化）；
* 运行环境（同一机器上两个进程）与编译优化等级（`-Ob`）。

## 8. 依赖维护

Dependabot 每月检查一次，覆盖 Maven 依赖与工作流中的 GitHub Actions，
其 PR 直接提交到 `dev` 分支（配置见 `.github/dependabot.yml`）。
也可在仓库的 Dependabot 页面使用「Check for updates」随时手动触发检查。

## 9. 许可

本项目以 **GNU General Public License v3.0** 授权，完整文本见 [`LICENSE`](LICENSE)。
