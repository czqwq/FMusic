package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoLogger

import cpw.mods.fml.common.Loader

import org.lwjgl.input.Keyboard

import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 1.7.10 平台的文字输入桥：让 DangoMusic 自绘的输入框（搜索、列表筛选、设置里的路径和数值）能用系统输入法（IME）。
 *
 * ## 为什么搜索栏打不了中文
 *
 * 字符进游戏的路在两个运行环境里完全不一样：
 *
 * - **lwjgl3ify（GTNH 现在用的输入层：LWJGL 2 的 API + SDL3 后端）**
 *   SDL 只有在"窗口里有个文本框正在输入"（`SDL_StartTextInput`）时，才会把输入法组字结果交给游戏；
 *   而 lwjgl3ify 只在原版 [net.minecraft.client.gui.GuiTextField] 获得焦点的那一下
 *   （`MixinGuiTextField#setFocused`）才代我们打开它。DangoMusic 的输入框是自绘的、根本不是
 *   `GuiTextField`，于是 SDL 从来没被打开过 —— 英文还能靠按键事件的 keyCode 走进来，
 *   输入法组字结果却一条都不进来。lwjgl3ify 为这种情况留了公开 API
 *   `me.eigenraven.lwjgl3ify.api.InputEvents`（`beginTextInput()` / `endTextInput()` 开关文本输入，
 *   `KeyboardListener#onTextEvent` 收文本），我们按它的约定自己开关、自己收，
 *   做法和 ModularUI2 的输入框一模一样：
 *   **SDL 文本输入开着的时候，可打印字符只认 TextEvent**，按键事件里带的那个字符要丢掉 ——
 *   因为 SDL 对每个可打印键既发 key event 又发 text event，不丢就会一个字母输入两遍。
 *
 * - **纯 LWJGL 2（没装 lwjgl3ify）**
 *   输入法组字结果是一条 `keyCode = 0` + `keyState = false` 的纯字符事件
 *   （LWJGL 2 的 `WindowsKeyboard#handleChar`：没有配对的按键事件可挂时，它就 `putEvent(0, 0, ch)`），
 *   而 1.7.10 的 `GuiScreen#handleKeyboardInput` 只在 `Keyboard.getEventKeyState()` 为真时才调
 *   `keyTyped`，于是整条事件被丢掉。这就是"1.7.10 打不了中文"的根因，也正是 InputFix 修的东西；
 *   这里用同一套判据（见 [imeCharOrNul]）把它补回给我们的界面。装了 InputFix 时让位，
 *   免得同一个字进两遍。
 *
 * lwjgl3ify 的 API 一律反射访问：它不是本模组的编译期依赖（同一份 jar 也可能跑在没装它的
 * 纯 LWJGL 2 环境）。拿不到就自动退化成纯 LWJGL 2 那套逻辑，绝不会因为缺类而崩。
 *
 * 生命周期（都由 [LegacyMusicScreen] 驱动）：
 * 界面打开 [subscribe] / 关闭 [unsubscribe]，每帧 [setFocused] 同步"现在有没有输入框在打字"，
 * 屏幕切换或关闭时 [reset]。
 */
object LegacyTextInput {

    /** lwjgl3ify 的公开输入 API（`api` 包，不是内部实现） */
    private const val INPUT_EVENTS = "me.eigenraven.lwjgl3ify.api.InputEvents"

    /** 已经自己派发 `keyCode = 0` 字符事件的 1.7.10 输入法补丁：它们在的时候我们让位 */
    private val INPUT_FIX_MODS = arrayOf("InputFix")
    private const val INPUT_FIX_CLASS = "lain.mods.inputfix.GuiScreenFix"

    // ---------------------------------------------------------------- lwjgl3ify 的反射句柄

    /** lwjgl3ify 在不在；null = 纯 LWJGL 2 */
    private val api: Class<*>? by lazy { type(INPUT_EVENTS) }
    private val listenerType: Class<*>? by lazy { type("$INPUT_EVENTS\$KeyboardListener") }
    private val textEventType: Class<*>? by lazy { type("$INPUT_EVENTS\$TextEvent") }
    private val textField: Field? by lazy {
        try {
            textEventType?.getField("text")
        } catch (_: Throwable) {
            null
        }
    }
    private val beginMethod: Method? by lazy { static("beginTextInput") }
    private val endMethod: Method? by lazy { static("endTextInput") }
    private val addListenerMethod: Method? by lazy { listenerType?.let { method("addKeyboardListener", it) } }
    private val removeListenerMethod: Method? by lazy { listenerType?.let { method("removeKeyboardListener", it) } }

    private fun type(name: String): Class<*>? = try {
        Class.forName(name, false, LegacyTextInput::class.java.classLoader)
    } catch (_: Throwable) {
        null
    }

    private fun static(name: String): Method? = try {
        api?.getMethod(name)
    } catch (_: Throwable) {
        null
    }

    private fun method(name: String, param: Class<*>): Method? = try {
        api?.getMethod(name, param)
    } catch (_: Throwable) {
        null
    }

    private var warned = false

    private fun warnOnce(t: Throwable) {
        if (warned) return
        warned = true
        DangoLogger.warn("Dango Music: text input (IME) bridge unavailable, falling back: {}", t.toString())
    }

    // ---------------------------------------------------------------- 状态

    /** 有没有输入框在打字（由平台层每帧同步进来） */
    var focused = false
        private set

    /** SDL 文本输入是不是我们打开的（= 字符只能从 TextEvent 来，按键事件里的字符必须丢掉） */
    var begun = false
        private set

    /**
     * 字符是不是改由 TextEvent 提供。
     *
     * 为真时平台层必须忽略按键事件里带的那个可打印字符，否则英文会输入两遍。
     * 只有真正打开成功（[begun]，而 [begun] 又要求已经订阅到文本事件）才为真 ——
     * 半套生效比不生效更糟，所以这两件事严格绑在一起。
     */
    val textEventsActive: Boolean get() = begun && listener != null

    /**
     * 有没有输入框在打字。每帧调一次即可：内部只在状态变化时真的去开关 SDL 文本输入，
     * 不会反复 `StartTextInput`（那会把正在组字的内容打断）。
     */
    fun setFocused(active: Boolean) {
        if (active == focused) return
        focused = active
        if (active) begin() else end()
    }

    /** 界面切换 / 关闭时复位（lwjgl3ify 会在 `GuiOpenEvent` 里把自己的文本输入计数清零，我们跟着走） */
    fun reset() {
        focused = false
        end()
    }

    // ---------------------------------------------------------------- SDL 文本输入（lwjgl3ify）

    private fun begin() {
        if (begun) return
        // 收不到 TextEvent 就绝不打开 SDL 文本输入：开了只会让每个字符多进来一遍
        if (listener == null) return
        val m = beginMethod ?: return
        begun = try {
            m.invoke(null)
            true
        } catch (t: Throwable) {
            warnOnce(t)
            false
        }
    }

    private fun end() {
        if (!begun) return
        begun = false
        val m = endMethod ?: return
        try {
            m.invoke(null)
        } catch (t: Throwable) {
            warnOnce(t)
        }
    }

    // ---------------------------------------------------------------- 文本事件订阅

    /** 注册进 lwjgl3ify 的监听器（是一个代理对象），界面关掉时要移除，所以得存着 */
    private var listener: Any? = null

    /** 当前订阅者；[unsubscribe] 会清掉，避免界面对象被这个全局监听器一直引用着 */
    private var onText: ((String) -> Unit)? = null

    /**
     * 订阅 SDL 文本输入（lwjgl3ify 的 `InputEvents.KeyboardListener#onTextEvent`）。
     *
     * [callback] 每收到一段文本调一次 —— 输入法一次提交好几个字也只调一次。
     * 返回是否订阅成功；纯 LWJGL 2（没有 lwjgl3ify）必然返回 false，调用方不用特殊处理。
     */
    fun subscribe(callback: (String) -> Unit): Boolean {
        onText = callback
        if (listener != null) return true
        val iface = listenerType ?: return false
        val add = addListenerMethod ?: return false
        val field = textField ?: return false
        val proxy = try {
            Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), InvocationHandler { self, m, args ->
                when (m.name) {
                    // 输入法 / 普通按键提交的文本：SDL 已经把整段给你了
                    "onTextEvent" -> {
                        val event = args?.firstOrNull()
                        val text = if (event == null) null else try {
                            field.get(event) as? String
                        } catch (_: Throwable) {
                            null
                        }
                        if (!text.isNullOrEmpty()) onText?.invoke(text)
                    }
                    // Proxy 必须自己实现这三个 Object 方法
                    "hashCode" -> System.identityHashCode(self)
                    "equals" -> self === args?.firstOrNull()
                    "toString" -> "FMusicTextInput"
                }
                null
            })
        } catch (t: Throwable) {
            warnOnce(t)
            return false
        }
        return try {
            add.invoke(null, proxy)
            listener = proxy
            // 界面已经停在输入框上时（先 setFocused 后 subscribe 的顺序）补一次打开
            if (focused) begin()
            true
        } catch (t: Throwable) {
            warnOnce(t)
            false
        }
    }

    /** 取消订阅（界面关闭时调用）：先把 SDL 文本输入关掉，再撤监听器 */
    fun unsubscribe() {
        end()
        val proxy = listener
        listener = null
        onText = null
        if (proxy == null) return
        val remove = removeListenerMethod ?: return
        try {
            remove.invoke(null, proxy)
        } catch (t: Throwable) {
            warnOnce(t)
        }
    }

    // ---------------------------------------------------------------- 纯 LWJGL 2：输入法组字结果

    /** 装了 InputFix 这类补丁就让位：它已经把同一个字符派发进去了 */
    private val imeFixInstalled: Boolean by lazy {
        INPUT_FIX_MODS.any { id ->
            try {
                Loader.isModLoaded(id)
            } catch (_: Throwable) {
                false
            }
        } || type(INPUT_FIX_CLASS) != null
    }

    /**
     * 取出当前这条键盘事件里的输入法组字结果；不是就返回 `'\u0000'`。
     *
     * 判据和 InputFix 的 Windows 实现一致：`keyState = false` + `keyCode = 0` + 是个真字符。
     * 这三条同时成立的事件，原版 1.7.10 一定丢掉（`GuiScreen#handleKeyboardInput` 只看 keyState），
     * 所以补派发不会让任何东西输入两遍；反过来普通按键（`keyState = true`）一律不碰，
     * 那些原版自己会派发。
     */
    fun imeCharOrNul(): Char {
        if (api != null) return '\u0000' // lwjgl3ify 下组字结果走 TextEvent，不从这里补
        if (imeFixInstalled) return '\u0000'
        if (Keyboard.getEventKeyState()) return '\u0000'
        if (Keyboard.getEventKey() != 0) return '\u0000'
        val c = Keyboard.getEventCharacter()
        return if (c != '\u0000' && Character.isDefined(c) && !Character.isISOControl(c)) c else '\u0000'
    }
}
