package com.digia.digiaui.framework

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle

/**
 * Container class for all UI resources used by the Digia UI system.
 *
 * [UIResources] holds mappings for icons, images, text styles, colors, and font factories that can
 * be used throughout the application. This allows for centralized resource management and easy
 * customization.
 */
data class UIResources(
        /** Mapping of icon names to ImageVector objects for use in UI components */
        val icons: Map<String, ImageVector>? = null,

        /** Mapping of image names to ImageBitmap objects for use in UI components */
        val images: Map<String, ImageBitmap>? = null,

        /** Mapping of text style names to TextStyle objects for consistent typography */
        val textStyles: Map<String, TextStyle?>? = null,

        /** Mapping of color token names to Color objects for light theme */
        val colors: Map<String, Color?>? = null,

        /** Mapping of color token names to Color objects for dark theme */
        val darkColors: Map<String, Color?>? = null,

        /** Factory for creating custom fonts and text styles */
        val fontFactory: DUIFontFactory? = null,

        /**
         * Mapping of asset local paths to cloud-hosted URLs.
         *
         * When users upload assets in the builder dashboard, they are stored in the cloud. This map
         * allows VWImage to resolve "asset" source type images to their cloud URLs. Key: localPath
         * (e.g., "assets/images/logo.png") Value: cloud URL (e.g.,
         * "https://cdn.digia.com/path/to/logo.png")
         */
        val assetUrls: Map<String, String>? = null,
)
