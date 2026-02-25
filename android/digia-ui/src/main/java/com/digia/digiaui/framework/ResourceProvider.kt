import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import com.digia.digiaui.framework.DUIFontFactory
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.init.DigiaUIManager
import com.digia.digiaui.init.SDKThemeResolver
import com.digia.digiaui.init.ThemeMode
import com.digia.digiaui.network.APIModel

/* ---------------------------------------------------------
 * CompositionLocal (replacement for InheritedWidget)
 * --------------------------------------------------------- */

val LocalUIResources =
        compositionLocalOf<UIResources> {
            error("UIResources not provided. Wrap your UI with ResourceProvider.")
        }

val LocalApiModels = compositionLocalOf<Map<String, APIModel>> { emptyMap() }

/* ---------------------------------------------------------
 * ResourceProvider (top-level provider)
 * --------------------------------------------------------- */

@Composable
fun ResourceProvider(
        resources: UIResources,
        apiModels: Map<String, APIModel>? = null,
        content: @Composable () -> Unit
) {
    val themeMode = DigiaUIManager.getInstance().themeMode
    val systemIsDark = isSystemInDarkTheme()

    val isDarkTheme =
            when (themeMode) {
                ThemeMode.SYSTEM -> systemIsDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

    // 🔑 Push resolved value into SDK (side-effect)
    SideEffect { SDKThemeResolver.update(isDarkTheme) }
    CompositionLocalProvider(
            LocalUIResources provides resources,
            LocalApiModels provides (apiModels ?: emptyMap())
    ) { content() }
}

/* ---------------------------------------------------------
 * Resource access helpers (NO Context)
 * --------------------------------------------------------- */

@Composable
fun resourceColor(key: String): Color? {
    val resources = LocalUIResources.current
    val isDark = SDKThemeResolver.isDark()

    return (if (isDark) resources.darkColors else resources.colors)?.get(key)
            ?: ColorUtil.fromString(key)
}

@Composable
fun resourceTextStyle(token: String): TextStyle? {
    return LocalUIResources.current.textStyles?.get(token)
}

@Composable
fun resourceImage(key: String): Painter? {
    return LocalUIResources.current.images?.get(key) as Painter?
}

@Composable
fun resourceIcon(key: String): ImageVector? {
    return LocalUIResources.current.icons?.get(key)
}

@Composable
fun resourceApiModel(key: String): APIModel? {
    return LocalApiModels.current[key]
}

@Composable
fun resourceFontFactory(): DUIFontFactory? {
    return LocalUIResources.current.fontFactory
}

/**
 * Returns the cloud-hosted URL for an asset by its local path key.
 *
 * This is used by VWImage to resolve "asset" source type images to their cloud-hosted URLs when the
 * asset was uploaded via the builder dashboard.
 *
 * @param key The local path key of the asset (e.g., "assets/images/logo.png")
 * @return The cloud URL if found, null otherwise
 */
@Composable
fun resourceAssetUrl(key: String): String? {
    return LocalUIResources.current.assetUrls?.get(key)
}

/* ---------------------------------------------------------
 * Resource access helpers (NON-Composable; for Actions)
 * --------------------------------------------------------- */

fun resourceColor(key: String, resources: UIResources?): Color? {
    val isDark = SDKThemeResolver.isDark()

    return (if (isDark) resources?.darkColors else resources?.colors)?.get(key)
            ?: ColorUtil.fromString(key)
}

fun resourceTextStyle(token: String, resources: UIResources?): TextStyle? {
    return resources?.textStyles?.get(token)
}

fun resourceIcon(key: String, resources: UIResources?): ImageVector? {
    return resources?.icons?.get(key)
}

fun resourceFontFactory(resources: UIResources?): DUIFontFactory? {
    return resources?.fontFactory
}
