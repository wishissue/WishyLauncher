package org.wishy.theme.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull

/**
 * Typed form of theme.json (schema version 1). Decoding is strict: unknown keys, wrong types and
 * unknown enum values all fail. Range and reference checks that types cannot express live in [ThemeChecker].
 * Keep this file in step with schema/wishy-theme.schema.v1.json.
 */

/** A length: plain design units, or a percentage of the parent's matching dimension. */
@Serializable(with = LengthSerializer::class)
data class Length(val value: Float, val isPercent: Boolean = false)

object LengthSerializer : KSerializer<Length> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Length", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Length {
        val jd = decoder as? JsonDecoder ?: throw SerializationException("Lengths can only be read from JSON.")
        return fromElement(jd.decodeJsonElement())
    }

    fun fromElement(el: kotlinx.serialization.json.JsonElement): Length {
        val p = el as? JsonPrimitive ?: throw SerializationException("A length must be a number or a string like \"12%\".")
        if (p.isString) {
            val s = p.content
            if (!s.endsWith("%")) throw SerializationException("Bad length: $s")
            val f = s.dropLast(1).toFloatOrNull() ?: throw SerializationException("Bad length: $s")
            return Length(f, true)
        }
        val f = p.floatOrNull ?: throw SerializationException("A length must be a number.")
        return Length(f, false)
    }

    override fun serialize(encoder: Encoder, value: Length) {
        encoder.encodeString(if (value.isPercent) "${value.value}%" else value.value.toString())
    }
}

/** Padding: one length for all sides, or an object with left/top/right/bottom. */
@Serializable(with = InsetsSerializer::class)
data class Insets(val left: Length, val top: Length, val right: Length, val bottom: Length)

object InsetsSerializer : KSerializer<Insets> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Insets", PrimitiveKind.STRING)
    private val ZERO = Length(0f)

    override fun deserialize(decoder: Decoder): Insets {
        val jd = decoder as? JsonDecoder ?: throw SerializationException("Insets can only be read from JSON.")
        val el = jd.decodeJsonElement()
        if (el is JsonObject) {
            val unknown = el.keys - setOf("left", "top", "right", "bottom")
            if (unknown.isNotEmpty()) throw SerializationException("Unknown inset keys: $unknown")
            fun side(k: String) = el[k]?.let { LengthSerializer.fromElement(it) } ?: ZERO
            return Insets(side("left"), side("top"), side("right"), side("bottom"))
        }
        val all = LengthSerializer.fromElement(el)
        return Insets(all, all, all, all)
    }

    override fun serialize(encoder: Encoder, value: Insets) {
        throw SerializationException("Insets are read-only.")
    }
}

@Serializable enum class ColorSchemeName { @SerialName("light") LIGHT, @SerialName("dark") DARK }
@Serializable enum class DefaultScheme { @SerialName("light") LIGHT, @SerialName("dark") DARK, @SerialName("system") SYSTEM }
@Serializable enum class ShapeKind {
    @SerialName("rect") RECT, @SerialName("rounded") ROUNDED, @SerialName("pixel") PIXEL,
    @SerialName("chamfer") CHAMFER, @SerialName("bevel") BEVEL
}
@Serializable enum class BorderStyle { @SerialName("solid") SOLID, @SerialName("dashed") DASHED, @SerialName("double") DOUBLE }
@Serializable enum class IndicatorKind {
    @SerialName("none") NONE, @SerialName("ring") RING, @SerialName("corners") CORNERS, @SerialName("arrow") ARROW,
    @SerialName("underline") UNDERLINE, @SerialName("fill") FILL, @SerialName("bar") BAR
}
@Serializable enum class CardAnimation { @SerialName("none") NONE, @SerialName("bob") BOB, @SerialName("pulse") PULSE, @SerialName("blink") BLINK }
@Serializable enum class ArtSource { @SerialName("auto") AUTO, @SerialName("banner") BANNER, @SerialName("icon") ICON }
@Serializable enum class Fit { @SerialName("crop") CROP, @SerialName("fit") FIT, @SerialName("stretch") STRETCH }
@Serializable enum class BgFit { @SerialName("crop") CROP, @SerialName("fit") FIT, @SerialName("stretch") STRETCH, @SerialName("tile") TILE }
@Serializable enum class Sampling { @SerialName("smooth") SMOOTH, @SerialName("nearest") NEAREST }
@Serializable enum class BgEffect { @SerialName("none") NONE, @SerialName("scanlines") SCANLINES, @SerialName("grid") GRID, @SerialName("dither") DITHER }
@Serializable enum class UserWallpaper { @SerialName("allowed") ALLOWED, @SerialName("ignored") IGNORED, @SerialName("required") REQUIRED }
@Serializable enum class LabelPosition {
    @SerialName("none") NONE, @SerialName("below") BELOW, @SerialName("overlayBottom") OVERLAY_BOTTOM,
    @SerialName("overlayCenter") OVERLAY_CENTER, @SerialName("focusedOnly") FOCUSED_ONLY
}
@Serializable enum class TextAlign { @SerialName("start") START, @SerialName("center") CENTER, @SerialName("end") END }
@Serializable enum class Anchor {
    @SerialName("topStart") TOP_START, @SerialName("top") TOP, @SerialName("topEnd") TOP_END,
    @SerialName("start") START, @SerialName("center") CENTER, @SerialName("end") END,
    @SerialName("bottomStart") BOTTOM_START, @SerialName("bottom") BOTTOM, @SerialName("bottomEnd") BOTTOM_END
}
@Serializable enum class ShowWhen {
    @SerialName("always") ALWAYS, @SerialName("clockEnabled") CLOCK_ENABLED,
    @SerialName("dateEnabled") DATE_ENABLED, @SerialName("hasFocusedApp") HAS_FOCUSED_APP
}
@Serializable enum class SectionsArrangement { @SerialName("tabs") TABS, @SerialName("dots") DOTS, @SerialName("list") LIST }
@Serializable enum class AppsArrangement { @SerialName("rail") RAIL, @SerialName("grid") GRID, @SerialName("list") LIST, @SerialName("cross") CROSS }
@Serializable enum class ScrollMode { @SerialName("edge") EDGE, @SerialName("centerFocused") CENTER_FOCUSED }
@Serializable enum class FocusScope { @SerialName("section") SECTION, @SerialName("all") ALL }
@Serializable enum class ScreenTransition { @SerialName("none") NONE, @SerialName("fade") FADE, @SerialName("slide") SLIDE }

@Serializable
data class Credit(
    val file: String,
    val title: String? = null,
    val author: String,
    val license: String,
    val source: String? = null,
    val notes: String? = null,
)

@Serializable
data class ColorScheme(
    val background: String,
    val surface: String,
    val surfaceVariant: String? = null,
    val onSurface: String,
    val onSurfaceMuted: String? = null,
    val primary: String,
    val onPrimary: String,
    val accent: String? = null,
    val outline: String? = null,
    val scrim: String? = null,
    val custom: Map<String, String> = emptyMap(),
)

@Serializable data class FontDef(val file: String, val weight: Int = 400, val pixel: Boolean = false)

@Serializable data class Shadow(val color: String? = null, val dx: Float = 0f, val dy: Float = 0f, val blur: Float = 0f)

@Serializable data class Border(val width: Float = 0f, val color: String? = null, val style: BorderStyle = BorderStyle.SOLID)

@Serializable data class Shape(val kind: ShapeKind = ShapeKind.ROUNDED, val radius: Float = 12f, val pixelSize: Float = 4f)

@Serializable
data class TextStyle(
    val font: String? = null,
    val size: Float = 24f,
    val color: String? = null,
    val letterSpacing: Float = 0f,
    val lineHeight: Float? = null,
    val uppercase: Boolean = false,
    val align: TextAlign? = null,
    val maxLines: Int? = null,
    val shadow: Shadow? = null,
)

@Serializable
data class Background(
    val color: String? = null,
    val image: String? = null,
    val fit: BgFit = BgFit.CROP,
    val sampling: Sampling = Sampling.SMOOTH,
    val dim: Float = 0f,
    val userWallpaper: UserWallpaper = UserWallpaper.ALLOWED,
    val effect: BgEffect = BgEffect.NONE,
    val effectOpacity: Float = 0.15f,
)

@Serializable
data class Motion(
    val focusMillis: Int = 180,
    val scrollMillis: Int = 260,
    val easing: String = "standard",
    val screenTransition: ScreenTransition = ScreenTransition.FADE,
)

@Serializable
data class FocusIndicator(
    val kind: IndicatorKind = IndicatorKind.RING,
    val color: String? = null,
    val width: Float = 3f,
    val gap: Float = 4f,
    val length: Float = 16f,
    val blink: Boolean = false,
    val blinkMillis: Int = 600,
)

@Serializable
data class CardState(
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val offsetY: Float = 0f,
    val fill: String? = null,
    val border: Border? = null,
    val shadow: Shadow? = null,
    val indicator: FocusIndicator? = null,
    val animation: CardAnimation = CardAnimation.NONE,
    val grayscale: Boolean = false,
)

@Serializable
data class Artwork(
    val source: ArtSource = ArtSource.AUTO,
    val fit: Fit = Fit.CROP,
    val sampling: Sampling = Sampling.SMOOTH,
    val pixelBlock: Int = 1,
    val iconSize: Length? = null,
    val placeholderFill: String? = null,
    /** Hard black-and-white (1-bit) picture: every colour becomes black or white by brightness. */
    val mono: Boolean = false,
    /** Use the theme's own covers: assets/art/<package>.png for an app, else the app's banner, else assets/art/default.png. */
    val themeArt: Boolean = false,
)

@Serializable
data class CardLabel(val position: LabelPosition = LabelPosition.BELOW, val style: String? = null, val gap: Float = 8f)

@Serializable
data class CardStyle(
    val aspect: String = "16:9",
    val shape: Shape = Shape(),
    val fill: String? = null,
    val padding: Insets? = null,
    val border: Border? = null,
    val shadow: Shadow? = null,
    val artwork: Artwork = Artwork(),
    val label: CardLabel = CardLabel(),
    val idle: CardState = CardState(),
    val focused: CardState = CardState(),
    val moving: CardState = CardState(),
) {
    /** Width divided by height, from "W:H". Falls back to 16:9 for a malformed value (the checker rejects those). */
    val aspectRatio: Float
        get() {
            val parts = aspect.split(":")
            val w = parts.getOrNull(0)?.toFloatOrNull()
            val h = parts.getOrNull(1)?.toFloatOrNull()
            return if (w != null && h != null && w > 0f && h > 0f) w / h else 16f / 9f
        }
}

@Serializable
data class Frame(
    val x: Length? = null,
    val y: Length? = null,
    val w: Length? = null,
    val h: Length? = null,
    val anchor: Anchor = Anchor.TOP_START,
    val zIndex: Int = 0,
)

@Serializable
sealed class Node {
    abstract val id: String?
    abstract val frame: Frame?
    abstract val weight: Float?
    abstract val padding: Insets?
    abstract val visible: Boolean
    abstract val showWhen: ShowWhen
}

@Serializable @SerialName("stack")
data class StackNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val children: List<Node>,
) : Node()

@Serializable @SerialName("row")
data class RowNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val gap: Float = 0f, val align: TextAlign = TextAlign.START,
    val children: List<Node>,
) : Node()

@Serializable @SerialName("column")
data class ColumnNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val gap: Float = 0f, val align: TextAlign = TextAlign.START,
    val children: List<Node>,
) : Node()

@Serializable @SerialName("shape")
data class ShapeNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val shape: Shape = Shape(), val fill: String? = null, val border: Border? = null, val shadow: Shadow? = null,
) : Node()

@Serializable @SerialName("image")
data class ImageNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val source: String, val fit: Fit = Fit.FIT, val sampling: Sampling = Sampling.SMOOTH,
    val tint: String? = null, val alpha: Float = 1f,
) : Node()

@Serializable @SerialName("text")
data class TextNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val text: String, val style: String? = null,
) : Node()

@Serializable @SerialName("clock")
data class ClockNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val style: String? = null, val showSeconds: Boolean = false,
) : Node()

@Serializable @SerialName("date")
data class DateNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val style: String? = null,
) : Node()

@Serializable @SerialName("focusedAppName")
data class FocusedAppNameNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val style: String? = null,
) : Node()

@Serializable @SerialName("sections")
data class SectionsNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val arrangement: SectionsArrangement = SectionsArrangement.TABS, val style: String? = null,
    val selectedIndicator: FocusIndicator? = null, val gap: Float = 16f,
) : Node()

@Serializable @SerialName("apps")
data class AppsNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val arrangement: AppsArrangement, val card: String,
    val columns: Int? = null, val rows: Int? = null, val itemWidth: Length? = null, val itemHeight: Length? = null,
    val gap: Float = 16f, val scroll: ScrollMode = ScrollMode.EDGE, val wrap: Boolean = false,
    val focusScope: FocusScope = FocusScope.SECTION,
) : Node()

@Serializable @SerialName("spacer")
data class SpacerNode(
    override val id: String? = null, override val frame: Frame? = null, override val weight: Float? = null,
    override val padding: Insets? = null, override val visible: Boolean = true, override val showWhen: ShowWhen = ShowWhen.ALWAYS,
    val size: Length? = null,
) : Node()

@Serializable data class Screen(val root: Node)
@Serializable data class Screens(val home: Screen)

@Serializable
data class ThemeDoc(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String = "",
    val license: String,
    val minLauncherVersion: Int = 1,
    val preview: String? = null,
    val credits: List<Credit> = emptyList(),
    val designHeight: Int = 1080,
    val colorSchemes: Map<ColorSchemeName, ColorScheme>,
    val defaultColorScheme: DefaultScheme = DefaultScheme.SYSTEM,
    val fonts: Map<String, FontDef> = emptyMap(),
    val textStyles: Map<String, TextStyle> = emptyMap(),
    val background: Background = Background(),
    val cardStyles: Map<String, CardStyle> = emptyMap(),
    val motion: Motion = Motion(),
    val screens: Screens,
) {
    /** The scheme to draw with: the wanted one if the theme has it, otherwise the one it does have. */
    fun schemeFor(dark: Boolean): ColorScheme {
        val want = if (dark) ColorSchemeName.DARK else ColorSchemeName.LIGHT
        return colorSchemes[want] ?: colorSchemes.values.first()
    }

    fun schemeNameFor(dark: Boolean): ColorSchemeName {
        val want = if (dark) ColorSchemeName.DARK else ColorSchemeName.LIGHT
        return if (colorSchemes.containsKey(want)) want else colorSchemes.keys.first()
    }

    val appsNode: AppsNode
        get() = findApps(screens.home.root) ?: throw ThemeRejectedException("The theme has no apps node.")

    val hasSections: Boolean
        get() = anySections(screens.home.root)

    private fun findApps(n: Node): AppsNode? = when (n) {
        is AppsNode -> n
        is StackNode -> n.children.firstNotNullOfOrNull { findApps(it) }
        is RowNode -> n.children.firstNotNullOfOrNull { findApps(it) }
        is ColumnNode -> n.children.firstNotNullOfOrNull { findApps(it) }
        else -> null
    }

    private fun anySections(n: Node): Boolean = when (n) {
        is SectionsNode -> true
        is StackNode -> n.children.any { anySections(it) }
        is RowNode -> n.children.any { anySections(it) }
        is ColumnNode -> n.children.any { anySections(it) }
        else -> false
    }
}

/** The one strict JSON configuration used for themes. */
val ThemeJson: Json = Json {
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
    explicitNulls = false
}
