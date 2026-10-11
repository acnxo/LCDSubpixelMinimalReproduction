# 最小复现：GraalVM native-image 下 JavaFX LCD 次像素文本合成退化

> 本分支**只包含复现该问题所需的最小代码与文档**，不含任何应用代码。是一个标准 Maven 工程。

<img width="1094" height="465" alt="image" src="https://github.com/user-attachments/assets/2a353f17-4f01-4c7d-aceb-e6157e78b47c" />



## 一句话（问题所在）

**同一份 JavaFX 代码，`native-image` 把 LCD 次像素合成里的「三条独立覆盖」编译成了
「一条共用、跨相邻像素取样并饱和」的标量** ⇒ 三通道输出相同、灰阶被二值化；
HotSpot 上同一段代码是正确的。

即：应为 `out_c = mix(bg_c, fg_c, m_c)`（每个通道各用自己的覆盖 `m_c`），
实为 `out_c = mix(bg, fg, s)`（三通道共用同一个 `s`）。

## 工程结构（就这些）

| 路径 | 作用 |
| --- | --- |
| `pom.xml` | 最小 Maven 工程：只依赖 JavaFX 的 base / graphics / controls |
| `src/main/java/LcdSubpixelRepro.java` | 复现程序（有效代码 44 行）：`Text` 设 `FontSmoothingType.LCD` → snapshot → 统计三通道墨量 |
| `src/main/resources/META-INF/native-image/*.json` | native-image 所需的反射 / JNI / 资源元数据（JavaFX 用，与本复现目标无关） |
| `src/main/resources/META-INF/native-image/native-image.properties` | native-image 的构建参数（`--no-fallback` + JavaFX 运行期初始化） |
| `Dist\RunJvmJar.cmd` / `Dist\RunNativeImage.cmd` | **双击即看窗口：`RunJvmJar.cmd`（JVM 侧）与 `RunNativeImage.cmd`（原生侧）各开一个窗口，并排比较 |
| `.github/workflows/repro-matrix.yml` | 版本矩阵 + 打包发布 |

## 判据（只看一个数）

黑字白底时，正确合成必然 `out_c = 1 − m_c` ⇒ **R/G/B 三通道墨量不可能相等**。

| 运行方式 | 输出 |
| --- | --- |
| HotSpot | `per-channel ink = 73135 / 73618 / 73245   collapsed=false` → `OK` |
| `native-image` | `per-channel ink = 105825 / 105825 / 105825   collapsed=true` → `*** BUG ***` |

## 本地怎么跑

```powershell
# 1) 构建（可指定 JDK/JavaFX 版本做矩阵）
mvn -B -ntp package -Dmaven.compiler.release=25 -Djavafx.version=25.0.4
mvn -B -ntp dependency:copy-dependencies -DoutputDirectory=target/lib

# 2) JVM 侧（应打印 OK）
java -jar target/lcd-repro-fat.jar

# 3) 原生镜像侧（应打印 BUG = 复现）
native-image -cp "target/classes;target/lib/*" -o target/lcd-repro LcdSubpixelRepro
./target/lcd-repro.exe
```

**不要**加 `-Dprism.lcdtext=false` —— 那是绕开本问题的灰度路径。

**默认就带界面**：双击 `Dist\RunNativeImage.cmd`（原生）与 `Dist\RunJvmJar.cmd`（JVM），把两个窗口并排放好即可肉眼对比；
两边的判据数字也会同时打印在同一窗口的控制台里。

只想要数字（脚本 / CI）：加 `--headless`，例如 `lcd-repro.exe --headless`。

## 版本矩阵与发布物

CI 会对 `GraalVM for JDK ∈ {17,20,21,22,23,24,25} × 对应 JavaFX`（外加 **JDK 25 + JavaFX 27**）
各构建一次，跑两个判据，并把**每格的编译产物**（原生镜像 exe + JVM 运行集 + `RESULT.txt`）
打包挂到**发布页（预发行）**，可直接下载到本机验证：

> 资产命名：`lcd-subpixel-repro-jdk<JDK主版本>-openjfx<OpenJFX版本>-<UTC时间戳>.zip`
> （例：`lcd-subpixel-repro-jdk23-openjfx23.0.2-202610111350.zip`）。
>
> 每包内容（无 lib/ 目录，JVM 侧为自包含 fat jar）：`lcd-repro.exe`（原生镜像）、`lcd-repro.jar`（同一程序的 JVM 版，供对照）、
> `Dist\RunJvmJar.cmd`、`Dist\RunNativeImage.cmd`、`RESULT.txt`（脚本随包发布，落在 `Dist` 目录）

> 注意：GitHub Windows runner 无 GPU，D3D 走 WARP（软件实现，仍是真 D3D 后端）；
> 因此 CI 的数字用于"是否复现"的判断，**观感/像素级结论仍以真实机器为准**。

## 已排除（详见主仓库 issue #20 的复盘）

DWrite 字形掩码两构建**逐字节相同**；纹理上传是同一 `prism_d3d.dll` 的原生调用；
着色器 `.obj` 与 `*_Loader` 常量表相同；`MaskData`/`GlyphCache` 几何无 LCD 特判；
`PrismSettings` 44 字段与类初始化策略两构建全同；环境（同机两进程）与优化等级（`-Ob`）均已排除。
