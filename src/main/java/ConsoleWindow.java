import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

/**
 * 双击启动时隐藏 Windows 为控制台程序创建的窗口；需要看日志时用开关把它显示出来。
 *
 * <p>English: hides the console window Windows creates for a double-clicked console program
 * (the native image), and keeps or shows it on request.
 *
 * <p>为什么保留控制台子系统 / Why the console subsystem is kept：native-image 的产物是**控制台程序**。
 * 双击时 Windows 先开一个控制台窗口，判据数字打在那里，然后才出现 GUI —— 这个窗口是本次需求要隐藏的对象。
 * 但**不能**改成窗口子系统：那样从已有控制台（cmd / CI / `RunNativeImage.cmd`）运行时日志就无处可去，
 * 脚本也拿不到判据数字。所以这里只隐藏「双击自带的那一个」窗口，其余场景一律保持可见。
 *
 * <p>English: the console subsystem is deliberately kept, so that running from an existing console
 * (cmd, CI, or `RunNativeImage.cmd`) still prints the criterion numbers there. Only the window that a
 * double-click creates for this process itself is hidden.
 *
 * <p>开关 / Switches：
 * <pre>
 * 双击启动                               隐藏窗口，只剩 GUI（可能有极短的一闪）
 * --console                              保留控制台窗口（调试用）
 * -Dlcdrepro.console=true                同上；原生启动器会吃掉 -D 参数，所以适合写进快捷方式
 * 从已有控制台（cmd / CI）启动           一律保持可见 —— 这个控制台不是本进程独占的，绝不隐藏
 *
 * double-click                           the window is hidden; the GUI is what you see
 * --console                              keep the console window (debugging)
 * -Dlcdrepro.console=true                same, usable in a shortcut (the native launcher consumes -D)
 * started from an existing console       always kept visible
 * </pre>
 *
 * <p>实现 / Implementation：用 JDK 的 FFM（{@code java.lang.foreign}）直接调 Win32 ——
 * {@code GetConsoleWindow}（取窗口句柄）、{@code GetConsoleProcessList}（判断这个控制台是否只有本进程）、
 * {@code ShowWindow}（隐藏 / 显示）。任何一步失败都**只记录状态、不抛异常**：
 * 一个只管外观的开关绝不该阻断程序启动。
 *
 * <p>English: Win32 is called through FFM; every failure is recorded as a state string only.
 */
final class ConsoleWindow {

    /** 命令行开关 / Command-line switch：保留控制台窗口 */
    private static final String ARG_CONSOLE = "--console";

    /** 系统属性开关 / System property switch：适合写进快捷方式 */
    private static final String PROP_CONSOLE = "lcdrepro.console";

    /** ShowWindow 的两个取值 / The two ShowWindow codes used here */
    private static final int SW_HIDE = 0;
    private static final int SW_SHOW = 5;

    /** 进程列表缓冲区容量 / Capacity of the process-list buffer（控制台同时连接的进程数） */
    private static final int PROCESS_LIST_CAPACITY = 8;

    private static MethodHandle getConsoleWindow;
    private static MethodHandle getConsoleProcessList;
    private static MethodHandle showWindow;

    private static volatile String state = "未判定 / not evaluated";

    private ConsoleWindow() {
    }

    /**
     * 在 {@code main} 的第一条语句调用：按「是否双击」与开关决定控制台窗口的可见性。
     *
     * <p>English: call as the first statement of {@code main}; decides the console window visibility
     * from "was this a double-click?" plus the switches.
     *
     * @param args 同 {@code main} 收到的参数，用于识别 {@code --console} / the same arguments main got
     */
    static void applyStartupVisibility(String[] args) {
        try {
            boolean wanted = consoleRequested(args);

            if (!isWindows()) {
                state = "非 Windows：不介入 / not Windows: no action";
                report();
                return;
            }

            long hwnd = consoleWindowHandle();
            if (hwnd == 0L) {
                state = "无控制台窗口 / no console window";
                report();
                return;
            }

            int owners = consoleProcessCount();
            if (owners > 1) {
                state = "保持可见：控制台由 " + owners + " 个进程共享（不是双击自带的）"
                        + " / visible: console shared by " + owners + " processes";
                report();
                return;
            }

            if (wanted) {
                showWindowHandle(hwnd, SW_SHOW);
                state = "保持可见：显式要求 / visible: requested via " + ARG_CONSOLE;
            } else {
                showWindowHandle(hwnd, SW_HIDE);
                state = "已隐藏：双击启动的默认行为（加 " + ARG_CONSOLE + " 或 -D" + PROP_CONSOLE
                        + "=true 可显示） / hidden: default for double-click";
            }
            report();
        } catch (Throwable t) {
            // 只记状态，绝不抛：外观开关不能阻断复现程序
            // Record the state only, never throw: a cosmetic switch must not break the reproducer
            state = "不介入(" + t.getClass().getSimpleName() + ") / skipped ("
                    + t.getClass().getSimpleName() + ")";
            report();
        }
    }

    /**
     * 供日志与自检读取的判定结果。
     *
     * <p>English: the decision taken, for logging and self-checks.
     *
     * @return 形如「已隐藏：双击启动的默认行为…」的说明
     */
    static String state() {
        return state;
    }

    private static void report() {
        System.out.println("[console] " + state);
    }

    private static boolean consoleRequested(String[] args) {
        for (String arg : args) {
            if (ARG_CONSOLE.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return Boolean.parseBoolean(System.getProperty(PROP_CONSOLE, "false"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** @return 控制台窗口句柄；0 表示本进程没有控制台 */
    private static long consoleWindowHandle() throws Throwable {
        initApi();
        MemorySegment hwnd = (MemorySegment) getConsoleWindow.invokeExact();
        return hwnd.address();
    }

    /** @return 连接在该控制台上的进程数；双击启动时通常为 1，从 cmd 启动时会带上 cmd 本身 */
    private static int consoleProcessCount() throws Throwable {
        initApi();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment list = arena.allocate(ValueLayout.JAVA_INT, PROCESS_LIST_CAPACITY);
            return (int) getConsoleProcessList.invokeExact(list, PROCESS_LIST_CAPACITY);
        }
    }

    private static void showWindowHandle(long hwnd, int code) throws Throwable {
        initApi();
        showWindow.invokeExact(MemorySegment.ofAddress(hwnd), code);
    }

    /**
     * 惰性解析 Win32 符号（只在 Windows 上会成功）。
     *
     * <p>English: lazily resolves the Win32 symbols (only succeeds on Windows).
     */
    private static synchronized void initApi() {
        if (showWindow != null) {
            return;
        }
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());

            getConsoleWindow = linker.downcallHandle(
                    kernel32.find("GetConsoleWindow").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS));
            getConsoleProcessList = linker.downcallHandle(
                    kernel32.find("GetConsoleProcessList").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            showWindow = linker.downcallHandle(
                    user32.find("ShowWindow").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        } catch (Throwable t) {
            throw new IllegalStateException("Win32 符号解析失败 / cannot resolve Win32 symbols", t);
        }
    }
}
