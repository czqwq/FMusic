package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoLogger

import cpw.mods.fml.common.Loader
import net.minecraft.client.gui.GuiScreen

import java.lang.reflect.Field

/**
 * Custom Main Menu（模组 id `custommainmenu`，下称 CMM）的可选兼容层。
 *
 * 为什么要它：装了 CMM 之后，它在 GuiOpenEvent 里把原版 `GuiMainMenu` 换成自己的
 * `lumien.custommainmenu.gui.GuiCustom`，主菜单不再是 `GuiMainMenu`，于是所有
 * `currentScreen is GuiMainMenu` 的判定都不成立：
 *  - 主菜单里按打开键（默认 M）打不开播放器；
 *  - 主菜单上的 Now Playing（主菜单也显示正在播放）不显示；
 *  - 在主菜单打开播放器时下面没有背景可采样（玻璃会变成纯色）。
 *
 * 为什么用反射、为什么是"可选加载"而不是依赖：CMM 不是本模组的依赖，玩家可以只装 FMusic。
 * 代码里只要直接出现 CMM 的类型，没装 CMM 的环境在类加载阶段就会 NoClassDefFoundError
 * （方法里的 try/catch 拦不住），所以这里把对 CMM 的访问全部收在反射里：
 *  - 先查 `Loader.isModLoaded("custommainmenu")`，没装就完全不做任何事，也不去加载它的类；
 *  - 探测失败（CMM 改了类名/字段名等）就按"没装"处理，只少掉这点兼容，不影响原版主菜单；
 *  - 构建上不需要任何 CMM 依赖，也不向 FML 声明依赖关系。
 *
 * 生效条件：CMM 的版本仍是 `GuiCustom` + `guiConfig.name == "mainmenu"` 这套结构。
 */
internal object CustomMainMenuCompat {

    private const val MOD_ID = "custommainmenu"
    private const val GUI_CUSTOM_CLASS = "lumien.custommainmenu.gui.GuiCustom"
    private const val GUI_CONFIG_CLASS = "lumien.custommainmenu.configuration.GuiConfig"
    private const val MAIN_MENU_NAME = "mainmenu"

    /** CMM 的 GuiCustom 类；null = 没装 CMM 或探测失败，此时兼容层整体不生效 */
    private var guiCustomClass: Class<*>? = null

    /** GuiCustom#guiConfig（CMM 里是 public final 字段） */
    private var guiConfigField: Field? = null

    /** GuiConfig#name（CMM 里是 public 字段） */
    private var guiConfigNameField: Field? = null

    init {
        // Loader 是 FML 自己的接口，不碰 CMM 的类；没装 CMM 时下面整段都不会执行
        if (Loader.isModLoaded(MOD_ID)) {
            try {
                val loader = CustomMainMenuCompat::class.java.classLoader
                // initialize = false：只拿 Class 对象，不触发 CMM 自己的静态初始化
                val guiCustom = Class.forName(GUI_CUSTOM_CLASS, false, loader)
                val guiConfig = Class.forName(GUI_CONFIG_CLASS, false, loader)
                guiConfigField = guiCustom.getField("guiConfig")
                guiConfigNameField = guiConfig.getField("name")
                guiCustomClass = guiCustom
                DangoLogger.info("已安装 Custom Main Menu，启用主菜单兼容（可选加载，不依赖它）")
            } catch (t: Throwable) {
                guiCustomClass = null
                guiConfigField = null
                guiConfigNameField = null
                DangoLogger.warn("Custom Main Menu 兼容层初始化失败，按未安装处理（不影响原版主菜单）: {}", t.toString())
            }
        }
    }

    /**
     * 这个界面是不是 CMM 的主菜单。
     *
     * 只看 `guiConfig.name`：CMM 还会用同一个 `GuiCustom` 画 mods/options 等自定义界面，
     * 那些不算主菜单（不应该在那里面接管播放器打开键，也不该画 Now Playing）。
     */
    fun isMainMenuScreen(screen: GuiScreen?): Boolean {
        val cls = guiCustomClass ?: return false
        val configField = guiConfigField ?: return false
        val nameField = guiConfigNameField ?: return false
        if (screen == null || !cls.isInstance(screen)) return false
        return try {
            val config = configField.get(screen) ?: return false
            nameField.get(config) == MAIN_MENU_NAME
        } catch (t: Throwable) {
            // 不该发生；真发生了就整个兼容层退化成"没装 CMM"，回到原版主菜单的老行为
            disable("读取主菜单信息失败", t)
            false
        }
    }

    /**
     * 播放器界面打开时，用 CMM 自己的 `drawScreen` 把它的主菜单画一遍当背景。
     *
     * 这是虚分派到 CMM 覆盖的 `GuiCustom#drawScreen`，不反射它内部的私有绘制流程，
     * 所以 CMM 换版本也不会失效。它的按钮/文字会被播放器几乎铺满屏幕的玻璃盖住，
     * 玻璃采样到的就是用户在 CMM 里配置的自定义背景，和原版主菜单走全景绘制是同一个效果。
     *
     * @return 是否画上了；false 表示这次/以后都不再画，调用方走没有背景的退化路径（铺深色底），
     *         避免玻璃反复采样上一帧把自己叠成亮色。
     */
    fun renderBackdrop(screen: GuiScreen, mouseX: Int, mouseY: Int, partialTicks: Float): Boolean = try {
        screen.drawScreen(mouseX, mouseY, partialTicks)
        true
    } catch (t: Throwable) {
        disable("绘制主菜单背景失败", t)
        false
    }

    private fun disable(reason: String, t: Throwable) {
        if (guiCustomClass == null) return
        guiCustomClass = null
        guiConfigField = null
        guiConfigNameField = null
        DangoLogger.warn("Custom Main Menu 兼容层{}，按未安装处理（不影响原版主菜单）: {}", reason, t.toString())
    }
}
