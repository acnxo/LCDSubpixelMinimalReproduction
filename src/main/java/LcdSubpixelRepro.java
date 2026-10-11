import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

/**
 * 最小复现（无应用依赖）：
 * JavaFX 的 LCD 次像素文本合成，在 GraalVM native-image 下退化为「三通道共用同一个标量，且二值化」。
 *
 * Minimal reproduction (no application dependencies):
 * JavaFX LCD sub-pixel text composition degrades under GraalVM native-image into
 * "the three color channels sharing a single scalar, which is binarized".
 *
 * <p>用法 / Usage:
 * <ul>
 *   <li><b>双击（默认）</b>：弹出窗口把 LCD 渲染放大摆给你看，<b>同时</b>把判据数字打到控制台 ——
 *       JVM 版与原生镜像版各开一个窗口并排比较即可；</li>
 *   <li><b>Double-click (default)</b>: opens a window with the magnified LCD rendering and
 *       <b>at the same time</b> prints the criterion numbers to the console — run the JVM build and
 *       the native-image build and compare the two windows side by side;</li>
 *   <li>{@code --headless}：不弹窗、只打印判据 —— 给 CI / 脚本用（无显示环境也不会卡住）。</li>
 *   <li>{@code --headless}: opens no window and prints the criterion only — for CI / scripts
 *       (it does not hang in an environment without a display).</li>
 * </ul>
 *
 * <p>判据 / Criterion:
 * 黑字白底时正确合成必然 {@code out_c = 1 - m_c}（每个通道各用自己的覆盖），
 * 因此三通道墨量**必须不等**；若三个数完全相同 ⇒ 复现。
 *
 * With black text on a white background, correct composition necessarily satisfies
 * {@code out_c = 1 - m_c} (each channel uses its own coverage), so the three per-channel ink values
 * **must differ**; if all three numbers are identical, the problem reproduces.
 *
 * <p>注意 / Note:
 * **不要**设 {@code -Dprism.lcdtext=false}，那会走灰度路径、绕开本问题。
 *
 * Do **not** set {@code -Dprism.lcdtext=false}: that selects the greyscale rendering path and works
 * around the problem, so the criterion would no longer observe it.
 */
public class LcdSubpixelRepro {

    /**
     * 判据用的样例（固定，勿改：README 与 issue 里的期望值都基于它）
     * Sample used by the criterion (fixed, do not change: the expected values in the README and in
     * the issues of this repository are based on it)
     */
    private static final String SAMPLE = "MW#8";

    public static void main(String[] args) {
        // 默认带界面（给人看）；--headless 只出数字（给 CI 用）
        // Windowed by default (for humans); --headless prints the numbers only (for CI)
        boolean headless = args.length > 0 && args[0].equalsIgnoreCase("--headless");
        boolean window = !headless;

        Platform.startup(() -> {
            // 构建形态：用 GraalVM 在原生镜像里写入的属性区分（判据输出以它作前缀）
            // Build flavor: distinguished by the property GraalVM sets in a native image
            // (the criterion output uses it as a prefix)
            String who = System.getProperty("org.graalvm.nativeimage.imagecode") != null
                    ? "native-image" : "JVM";

            // ① 判据用节点：14px、离屏（从不显示），保证数字与文档一致；挂到 Scene 上才会真正渲染
            //    Criterion node: 14px, offscreen (never shown) so the numbers match the documentation;
            //    it must be attached to a Scene to be rendered
            Text sample = new Text(SAMPLE);
            sample.setFont(Font.font("Segoe UI", 14));
            sample.setFontSmoothingType(FontSmoothingType.LCD);
            sample.setFill(Color.BLACK);
            new Scene(new StackPane(sample));

            // 快照参数：白底 + 1.5 倍放大，对齐真机 1.5× 显示缩放下的次像素相位
            // Snapshot parameters: white background + 1.5x zoom, matching the sub-pixel phase at
            // 1.5x display scaling on a real machine
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.WHITE);
            params.setTransform(new Scale(1.5, 1.5));
            Image image = sample.snapshot(params, null);

            // 逐像素累计三通道墨量：白底黑字时「墨量 = 255 − 通道值」
            // Accumulate the ink of the three channels pixel by pixel: with black on white,
            // "ink = 255 − channel value"
            PixelReader reader = image.getPixelReader();
            long r = 0;
            long g = 0;
            long b = 0;
            for (int y = 0; y < (int) image.getHeight(); y++) {
                for (int x = 0; x < (int) image.getWidth(); x++) {
                    Color c = reader.getColor(x, y);
                    r += Math.round((1 - c.getRed()) * 255);
                    g += Math.round((1 - c.getGreen()) * 255);
                    b += Math.round((1 - c.getBlue()) * 255);
                }
            }
            // 判据：三通道墨量完全相等 ⇒ 各通道的覆盖信息已被合并为一个共用标量（且二值化）
            // Criterion: all three per-channel ink values equal ⇒ per-channel coverage has been
            // merged into one shared scalar (and binarized)
            boolean collapsed = r == g && g == b;
            System.out.printf("[%s] per-channel ink = %d / %d / %d   collapsed=%s%n", who, r, g, b, collapsed);
            System.out.println(collapsed
                    ? "*** BUG: LCD subpixel coverage collapsed to one shared scalar (binarized) ***"
                    : "OK: per-channel coverage preserved");

            // ② 给人看的窗口：放大字号，便于肉眼判断抗锯齿与次像素色边
            //    Window for humans: larger font size, so anti-aliasing and sub-pixel color fringes
            //    can be judged visually
            if (window) {
                Text display = new Text(SAMPLE + "  88°C  6223RPM");
                display.setFont(Font.font("Segoe UI", 34));
                display.setFontSmoothingType(FontSmoothingType.LCD);
                display.setFill(Color.BLACK);
                Stage stage = new Stage();
                stage.setTitle("LCD repro [" + who + "] —— 与另一个构建并排对比（看边缘灰阶与色边）");
                stage.setScene(new Scene(new StackPane(display), 720, 120, Color.WHITE));
                stage.show();
                System.out.println("（已弹出窗口；关掉窗口即退出）");
            } else {
                // 让工具包启动跑完再退出，避免收尾竞态
                // Let the toolkit finish starting up before exiting, to avoid a shutdown race
                Platform.runLater(Platform::exit);
            }
        });
    }
}
