package org.wishy.theme.core

import java.io.File

/**
 * Checks everything the types cannot: ranges, colour tokens, references between parts of the theme,
 * file paths and the node tree shape. Collects all problems (up to a cap) so authors see them together.
 */
object ThemeChecker {
    private val HEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val TOKEN = Regex("^@(background|surface|surfaceVariant|onSurface|onSurfaceMuted|primary|onPrimary|accent|outline|scrim|custom\\.([a-zA-Z][a-zA-Z0-9]{0,23}))(/[0-9]{1,3})?$")
    private val CUSTOM_NAME = Regex("^[a-zA-Z][a-zA-Z0-9]{0,23}$")
    private val NODE_ID = Regex("^[a-zA-Z][a-zA-Z0-9_-]{0,31}$")
    private val ASPECT = Regex("^[0-9]{1,2}:[0-9]{1,2}$")
    private val EASING = Regex("^(linear|standard|decelerate|accelerate|steps:([1-9]|1[0-6]))$")
    private const val MAX_PROBLEMS = 25

    /** Throws [ThemeRejectedException] listing the problems, or returns normally if the theme is acceptable. */
    fun check(theme: ThemeDoc, dir: File) {
        val problems = problems(theme, dir)
        if (problems.isNotEmpty()) {
            throw ThemeRejectedException(problems.take(8).joinToString("; ") +
                if (problems.size > 8) " (and ${problems.size - 8} more)" else "")
        }
    }

    fun problems(t: ThemeDoc, dir: File): List<String> {
        val c = Collector()
        val files = FileCheck(dir, c)

        if (t.schemaVersion != 1) c.add("schemaVersion must be 1")
        if (!ThemeLimits.ID_REGEX.matches(t.id)) c.add("id is not valid")
        if (!ThemeLimits.VERSION_REGEX.matches(t.version)) c.add("version must look like 1.2.3")
        if (t.name.isBlank() || t.name.length > 48) c.add("name must be 1 to 48 characters")
        if (t.author.isBlank() || t.author.length > 80) c.add("author must be 1 to 80 characters")
        if (t.license.isBlank() || t.license.length > 64) c.add("license must be 1 to 64 characters")
        if (t.description.length > 400) c.add("description is too long")
        if (t.designHeight !in 360..4320) c.add("designHeight out of range")
        if (t.minLauncherVersion < 1) c.add("minLauncherVersion must be at least 1")
        t.preview?.let { files.image(it, "preview") }
        if (t.credits.size > 200) c.add("too many credits")

        if (t.colorSchemes.isEmpty()) c.add("colorSchemes is empty")
        val customNames = HashSet<String>()
        t.colorSchemes.forEach { (name, s) ->
            val where = "colorSchemes.$name"
            listOf(s.background, s.surface, s.onSurface, s.primary, s.onPrimary).forEach { hex(it, where, c) }
            listOfNotNull(s.surfaceVariant, s.onSurfaceMuted, s.accent, s.outline, s.scrim).forEach { hex(it, where, c) }
            if (s.custom.size > 16) c.add("$where.custom has more than 16 colours")
            s.custom.forEach { (k, v) ->
                if (!CUSTOM_NAME.matches(k)) c.add("$where.custom name '$k' is not valid")
                hex(v, "$where.custom.$k", c)
                customNames += k
            }
        }
        val refs = Refs(customNames, c)

        if (t.fonts.size > 8) c.add("more than 8 fonts")
        t.fonts.forEach { (k, f) ->
            if (!CUSTOM_NAME.matches(k)) c.add("font key '$k' is not valid")
            files.font(f.file, "fonts.$k")
            if (f.weight !in 100..900) c.add("fonts.$k.weight out of range")
        }

        if (t.textStyles.size > 24) c.add("more than 24 text styles")
        t.textStyles.forEach { (k, s) ->
            val where = "textStyles.$k"
            s.font?.let { if (it !in t.fonts) c.add("$where.font '$it' is not defined in fonts") }
            if (s.size !in 8f..400f) c.add("$where.size out of range")
            if (s.letterSpacing !in -10f..40f) c.add("$where.letterSpacing out of range")
            s.lineHeight?.let { if (it !in 0.8f..3f) c.add("$where.lineHeight out of range") }
            s.maxLines?.let { if (it !in 1..6) c.add("$where.maxLines out of range") }
            s.color?.let { refs.color(it, "$where.color") }
            s.shadow?.let { shadow(it, "$where.shadow", refs, c) }
        }

        val bg = t.background
        bg.color?.let { refs.color(it, "background.color") }
        bg.image?.let { files.image(it, "background.image") }
        if (bg.dim !in 0f..1f) c.add("background.dim out of range")
        if (bg.effectOpacity !in 0f..1f) c.add("background.effectOpacity out of range")

        if (t.motion.focusMillis !in 0..800) c.add("motion.focusMillis out of range")
        if (t.motion.scrollMillis !in 0..1200) c.add("motion.scrollMillis out of range")
        if (!EASING.matches(t.motion.easing)) c.add("motion.easing is not valid")

        if (t.cardStyles.size > 16) c.add("more than 16 card styles")
        t.cardStyles.forEach { (k, s) -> cardStyle(k, s, t, refs, c) }

        val counts = TreeCounts()
        walk(t.screens.home.root, 1, t, files, refs, c, counts)
        if (counts.apps != 1) c.add("the home screen needs exactly one apps node (found ${counts.apps})")
        if (counts.nodes > ThemeLimits.MAX_NODES) c.add("more than ${ThemeLimits.MAX_NODES} nodes")

        return c.list
    }

    // ---- parts ----

    private fun cardStyle(k: String, s: CardStyle, t: ThemeDoc, refs: Refs, c: Collector) {
        val where = "cardStyles.$k"
        if (!CUSTOM_NAME.matches(k)) c.add("card style key '$k' is not valid")
        if (!ASPECT.matches(s.aspect)) c.add("$where.aspect must look like 16:9")
        else {
            val (w, h) = s.aspect.split(":").map { it.toInt() }
            if (w == 0 || h == 0) c.add("$where.aspect cannot contain zero")
        }
        shape(s.shape, "$where.shape", c)
        s.fill?.let { refs.color(it, "$where.fill") }
        s.padding?.let { insets(it, "$where.padding", c) }
        s.border?.let { border(it, "$where.border", refs, c) }
        s.shadow?.let { shadow(it, "$where.shadow", refs, c) }
        s.artwork.iconSize?.let { length(it, "$where.artwork.iconSize", c) }
        s.artwork.placeholderFill?.let { refs.color(it, "$where.artwork.placeholderFill") }
        if (s.artwork.pixelBlock !in 1..16) c.add("$where.artwork.pixelBlock out of range")
        s.label.style?.let { if (it !in t.textStyles) c.add("$where.label.style '$it' is not defined in textStyles") }
        if (s.label.gap !in 0f..64f) c.add("$where.label.gap out of range")
        listOf("idle" to s.idle, "focused" to s.focused, "moving" to s.moving).forEach { (n, st) ->
            val w = "$where.$n"
            if (st.scale !in 0.5f..1.6f) c.add("$w.scale out of range")
            if (st.alpha !in 0f..1f) c.add("$w.alpha out of range")
            if (st.offsetY !in -128f..128f) c.add("$w.offsetY out of range")
            st.fill?.let { refs.color(it, "$w.fill") }
            st.border?.let { border(it, "$w.border", refs, c) }
            st.shadow?.let { shadow(it, "$w.shadow", refs, c) }
            st.indicator?.let { indicator(it, "$w.indicator", refs, c) }
        }
    }

    private class TreeCounts { var nodes = 0; var apps = 0 }

    private fun walk(n: Node, depth: Int, t: ThemeDoc, files: FileCheck, refs: Refs, c: Collector, counts: TreeCounts) {
        counts.nodes++
        if (counts.nodes > ThemeLimits.MAX_NODES + 1) return
        if (depth > ThemeLimits.MAX_DEPTH) { c.add("the screen tree is deeper than ${ThemeLimits.MAX_DEPTH}"); return }
        val where = "node '${n.id ?: n::class.simpleName}'"
        n.id?.let { if (!NODE_ID.matches(it)) c.add("$where: node id is not valid") }
        n.weight?.let { if (it !in 0f..100f) c.add("$where: weight out of range") }
        n.padding?.let { insets(it, "$where.padding", c) }
        n.frame?.let { f ->
            listOfNotNull(f.x, f.y, f.w, f.h).forEach { length(it, "$where.frame", c) }
            if (f.zIndex !in -10..100) c.add("$where.frame.zIndex out of range")
        }
        fun style(s: String?) { s?.let { if (it !in t.textStyles) c.add("$where: text style '$it' is not defined") } }
        when (n) {
            is StackNode -> children(n.children, "$where.children", depth, t, files, refs, c, counts)
            is RowNode -> { gap(n.gap, where, c); children(n.children, "$where.children", depth, t, files, refs, c, counts) }
            is ColumnNode -> { gap(n.gap, where, c); children(n.children, "$where.children", depth, t, files, refs, c, counts) }
            is ShapeNode -> {
                shape(n.shape, "$where.shape", c)
                n.fill?.let { refs.color(it, "$where.fill") }
                n.border?.let { border(it, "$where.border", refs, c) }
                n.shadow?.let { shadow(it, "$where.shadow", refs, c) }
            }
            is ImageNode -> {
                files.image(n.source, "$where.source")
                n.tint?.let { refs.color(it, "$where.tint") }
                if (n.alpha !in 0f..1f) c.add("$where.alpha out of range")
            }
            is TextNode -> { style(n.style); if (n.text.length > 80) c.add("$where.text is longer than 80 characters") }
            is StatusIconsNode -> {
                n.color?.let { refs.color(it, "$where.color") }
                n.inactiveColor?.let { refs.color(it, "$where.inactiveColor") }
                n.focusFill?.let { refs.color(it, "$where.focusFill") }
                shape(n.shape, "$where.shape", c)
                gap(n.gap, where, c)
                if (n.iconSize !in 12f..96f) c.add("$where.iconSize out of range")
                if (n.items.isEmpty() || n.items.size > 3 || n.items.toSet().size != n.items.size) c.add("$where.items must list each of vpn, network, settings at most once")
            }
            is ClockNode -> style(n.style)
            is DateNode -> style(n.style)
            is FocusedAppNameNode -> style(n.style)
            is SectionsNode -> {
                style(n.style)
                gap(n.gap, where, c)
                n.selectedIndicator?.let { indicator(it, "$where.selectedIndicator", refs, c) }
            }
            is AppsNode -> {
                counts.apps++
                if (n.card !in t.cardStyles) c.add("$where.card '${n.card}' is not defined in cardStyles")
                n.ringColor?.let { refs.color(it, "$where.ringColor") }
                n.ringEdgeColor?.let { refs.color(it, "$where.ringEdgeColor") }
                if (n.ringWidth !in 0f..600f) c.add("$where.ringWidth out of range")
                if ((n.ringColor != null || n.ringEdgeColor != null || n.ringWidth != 0f) && n.arrangement != AppsArrangement.WHEEL) c.add("$where: ring settings only apply to the wheel arrangement")
                n.bigCard?.let { if (it !in t.cardStyles) c.add("$where.bigCard '$it' is not defined in cardStyles") }
                if (n.bigCard != null && n.arrangement != AppsArrangement.BENTO) c.add("$where.bigCard only applies to the bento arrangement")
                if (n.arrangement == AppsArrangement.BENTO && n.columns != null && n.columns !in 3..8) c.add("$where.columns for a bento must be 3 to 8")
                n.columns?.let { if (it !in 1..10) c.add("$where.columns out of range") }
                n.rows?.let { if (it !in 1..8) c.add("$where.rows out of range") }
                n.itemWidth?.let { length(it, "$where.itemWidth", c) }
                n.itemHeight?.let { length(it, "$where.itemHeight", c) }
                gap(n.gap, where, c)
                if (n.arrangement == AppsArrangement.GRID && n.columns == null && n.itemWidth == null) {
                    c.add("$where: a grid needs columns or itemWidth")
                }
            }
            is SpacerNode -> n.size?.let { length(it, "$where.size", c) }
        }
    }

    private fun children(list: List<Node>, where: String, depth: Int, t: ThemeDoc, files: FileCheck, refs: Refs, c: Collector, counts: TreeCounts) {
        if (list.size > 48) { c.add("$where has more than 48 children"); return }
        list.forEach { walk(it, depth + 1, t, files, refs, c, counts) }
    }

    private fun gap(g: Float, where: String, c: Collector) { if (g !in 0f..256f) c.add("$where.gap out of range") }
    private fun length(l: Length, where: String, c: Collector) {
        if (l.value.isNaN() || l.value !in -4096f..8192f) c.add("$where: length out of range")
    }
    private fun insets(i: Insets, where: String, c: Collector) =
        listOf(i.left, i.top, i.right, i.bottom).forEach { length(it, where, c) }
    private fun shape(s: Shape, where: String, c: Collector) {
        if (s.radius !in 0f..512f) c.add("$where.radius out of range")
        if (s.pixelSize !in 1f..32f) c.add("$where.pixelSize out of range")
    }
    private fun border(b: Border, where: String, refs: Refs, c: Collector) {
        if (b.width !in 0f..64f) c.add("$where.width out of range")
        b.color?.let { refs.color(it, "$where.color") }
    }
    private fun shadow(s: Shadow, where: String, refs: Refs, c: Collector) {
        if (s.dx !in -64f..64f || s.dy !in -64f..64f) c.add("$where offset out of range")
        if (s.blur !in 0f..128f) c.add("$where.blur out of range")
        s.color?.let { refs.color(it, "$where.color") }
    }
    private fun indicator(i: FocusIndicator, where: String, refs: Refs, c: Collector) {
        if (i.width !in 0f..32f) c.add("$where.width out of range")
        if (i.gap !in -16f..64f) c.add("$where.gap out of range")
        if (i.length !in 0f..128f) c.add("$where.length out of range")
        if (i.blinkMillis !in 200..3000) c.add("$where.blinkMillis out of range")
        i.color?.let { refs.color(it, "$where.color") }
    }
    private fun hex(v: String, where: String, c: Collector) {
        if (!HEX.matches(v)) c.add("$where: '$v' is not a #RRGGBB or #AARRGGBB colour")
    }

    // ---- helpers ----

    private class Collector {
        val list = ArrayList<String>()
        fun add(msg: String) { if (list.size < MAX_PROBLEMS) list.add(msg) }
    }

    private class Refs(private val custom: Set<String>, private val c: Collector) {
        fun color(v: String, where: String) {
            if (HEX.matches(v)) return
            val m = TOKEN.matchEntire(v)
            if (m == null) { c.add("$where: '$v' is not a valid colour or token"); return }
            val customName = m.groupValues[2]
            if (customName.isNotEmpty() && customName !in custom) c.add("$where: custom colour '$customName' is not defined")
            val pct = m.groupValues[3].removePrefix("/")
            if (pct.isNotEmpty() && pct.toInt() > 100) c.add("$where: alpha percentage above 100")
        }
    }

    private class FileCheck(private val dir: File, private val c: Collector) {
        private val root = dir.canonicalFile
        private fun exists(path: String, where: String, exts: Set<String>, label: String): Boolean {
            if (!ThemePaths.isSafeRelativePath(path)) { c.add("$where: '$path' is not a safe path"); return false }
            if (ThemePaths.extension(path) !in exts) { c.add("$where: '$path' is not a $label file"); return false }
            val f = File(root, path).canonicalFile
            if (!f.path.startsWith(root.path + File.separator) || !f.isFile) {
                c.add("$where: file '$path' does not exist in the theme"); return false
            }
            return true
        }
        fun image(path: String, where: String) = exists(path, where, ThemeLimits.IMAGE_EXTENSIONS, "image")
        fun font(path: String, where: String) = exists(path, where, ThemeLimits.FONT_EXTENSIONS, "font")
    }
}
