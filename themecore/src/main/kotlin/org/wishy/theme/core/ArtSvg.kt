package org.wishy.theme.core

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/**
 * The result of checking an SVG. [text] is what must be stored and rendered (a plain DOCTYPE line removed),
 * [aspect] is width / height from the viewBox or the width and height attributes, and [monochrome] is true
 * when the picture uses at most one colour and no gradients or embedded pictures, so it can be tinted safely.
 */
class SvgInfo(val text: String, val aspect: Float, val monochrome: Boolean)

/**
 * Decides whether an SVG may be kept and drawn. The renderer never runs scripts, but an SVG is also XML and can
 * point at other files, so the rules are strict: no scripts, no foreign content, no entity or DOCTYPE tricks, no
 * references to anything outside the file, no event attributes, no animation. Anything malformed is rejected.
 *
 * Throws [ThemeRejectedException] (message is safe to show) when the file is not acceptable.
 */
object SvgSanitizer {
    const val MAX_BYTES = 256 * 1024
    private const val MAX_ELEMENTS = 5000
    private const val MAX_DEPTH = 64
    private const val MAX_STYLE_CHARS = 64 * 1024

    private val DENIED_ELEMENTS = setOf(
        "script", "foreignobject", "iframe", "object", "embed", "applet", "audio", "video", "canvas",
        "handler", "listener", "animate", "animatetransform", "animatemotion", "animatecolor", "set",
        "link", "meta", "style-sheet",
    )
    private val MULTICOLOR_ELEMENTS = setOf("lineargradient", "radialgradient", "pattern", "image", "mask", "filter")
    private val SAFE_DATA_URI = Regex("^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=\\s]*$", RegexOption.IGNORE_CASE)
    private val COLOR_IN_CSS = Regex("(?i)(?:fill|stroke|stop-color|color)\\s*:\\s*([^;}\\s]+)")
    private val IGNORED_COLORS = setOf("none", "currentcolor", "inherit", "transparent", "initial", "unset", "")

    fun inspect(bytes: ByteArray): SvgInfo {
        if (bytes.isEmpty()) throw ThemeRejectedException("That SVG file is empty.")
        if (bytes.size > MAX_BYTES) throw ThemeRejectedException("That SVG file is larger than 256 KB.")
        var text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        if (text.indexOf('\u0000') >= 0) throw ThemeRejectedException("That is not a valid SVG file.")
        if (!text.contains("<svg", ignoreCase = true)) throw ThemeRejectedException("That is not a valid SVG file.")

        if (Regex("<!ENTITY", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            throw ThemeRejectedException("That SVG uses entities, which are not allowed.")
        }
        val docType = Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE).find(text)
        if (docType != null) {
            // A DOCTYPE with an internal subset ([ ... ]) can declare entities. A plain one is only old editor noise.
            if (docType.value.contains('[')) throw ThemeRejectedException("That SVG has an unsafe DOCTYPE.")
            text = text.replaceRange(docType.range, "")
        }
        if (text.contains("<!DOCTYPE", ignoreCase = true)) throw ThemeRejectedException("That SVG has an unsafe DOCTYPE.")
        if (Regex("<\\?(?!xml[\\s?])", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            throw ThemeRejectedException("That SVG has processing instructions, which are not allowed.")
        }

        val handler = Handler()
        try {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = true
            factory.isValidating = false
            for ((feature, value) in listOf(
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            )) runCatching { factory.setFeature(feature, value) }
            val parser = factory.newSAXParser()
            val src = InputSource(StringReader(text))
            parser.parse(src, handler)
        } catch (e: SvgReject) {
            throw ThemeRejectedException(e.message ?: "That SVG is not allowed.")
        } catch (e: SAXException) {
            val cause = e.exception
            if (cause is SvgReject) throw ThemeRejectedException(cause.message ?: "That SVG is not allowed.")
            throw ThemeRejectedException("That is not a valid SVG file.")
        } catch (e: Exception) {
            throw ThemeRejectedException("That is not a valid SVG file.")
        }
        if (!handler.sawRoot) throw ThemeRejectedException("That is not a valid SVG file.")

        val colors = handler.colors.map { it.lowercase() }.filter { it !in IGNORED_COLORS }.toSet()
        val mono = !handler.multicolor && colors.size <= 1
        return SvgInfo(text, handler.aspect, mono)
    }

    /** Thrown inside the XML handler; a SAXException is the one kind the parser passes through unchanged. */
    private class SvgReject(msg: String) : SAXException(msg)

    private fun reject(msg: String): Nothing = throw SvgReject(msg)

    private fun checkUrls(value: String) {
        var i = 0
        val lower = value.lowercase()
        while (true) {
            val at = lower.indexOf("url(", i)
            if (at < 0) return
            val end = lower.indexOf(')', at)
            if (end < 0) reject("That SVG has a broken reference.")
            val target = value.substring(at + 4, end).trim().trim('"', '\'').trim()
            if (!target.startsWith("#") && !SAFE_DATA_URI.matches(target)) reject("That SVG refers to something outside itself.")
            i = end + 1
        }
    }

    private fun checkCss(css: String) {
        val l = css.lowercase()
        if ("@import" in l || "expression(" in l || "javascript:" in l || "behavior:" in l || "-moz-binding" in l) {
            reject("That SVG has unsafe styles.")
        }
        checkUrls(css)
    }

    private class Handler : DefaultHandler() {
        var sawRoot = false
        var aspect = 1f
        var multicolor = false
        val colors = HashSet<String>()
        private var depth = 0
        private var elements = 0
        private var inStyle = false
        private val style = StringBuilder()

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = InputSource(StringReader(""))

        override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes) {
            val name = (localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()).substringAfter(':').lowercase()
            depth++
            elements++
            if (depth > MAX_DEPTH) reject("That SVG is nested too deeply.")
            if (elements > MAX_ELEMENTS) reject("That SVG is too complex.")
            if (!sawRoot) {
                if (name != "svg") reject("That is not an SVG file.")
                sawRoot = true
                aspect = rootAspect(atts)
            }
            if (name in DENIED_ELEMENTS) reject("That SVG contains $name content, which is not allowed.")
            if (name in MULTICOLOR_ELEMENTS) multicolor = true
            if (name == "style") { inStyle = true; style.setLength(0) }

            for (i in 0 until atts.length) {
                // Android's parser may leave qName empty, so read the local name first. A prefix ("xlink:") never matters here.
                val aName = (atts.getLocalName(i)?.takeIf { it.isNotEmpty() } ?: atts.getQName(i).orEmpty()).substringAfter(':').lowercase()
                val value = atts.getValue(i) ?: ""
                if (aName.length > 2 && aName.startsWith("on")) reject("That SVG has event handlers, which are not allowed.")
                val lv = value.lowercase()
                if ("javascript:" in lv || "vbscript:" in lv || "data:text/" in lv || "data:image/svg" in lv || "data:application" in lv) {
                    reject("That SVG has unsafe content.")
                }
                if (aName == "href") {
                    val v = value.trim()
                    if (!v.startsWith("#") && !SAFE_DATA_URI.matches(v)) reject("That SVG refers to another file, which is not allowed.")
                    if (!v.startsWith("#")) multicolor = true
                }
                if ("url(" in lv) checkUrls(value)
                when (aName) {
                    "style" -> {
                        checkCss(value)
                        COLOR_IN_CSS.findAll(value).forEach { colors += it.groupValues[1] }
                    }
                    "fill", "stroke", "stop-color", "color" -> colors += value.trim()
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inStyle && style.length < MAX_STYLE_CHARS) style.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = (localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()).substringAfter(':').lowercase()
            if (name == "style" && inStyle) {
                inStyle = false
                val css = style.toString()
                checkCss(css)
                COLOR_IN_CSS.findAll(css).forEach { colors += it.groupValues[1] }
                style.setLength(0)
            }
            depth--
        }

        private fun number(s: String?): Float? {
            if (s == null) return null
            val m = Regex("^\\s*([0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)\\s*[a-zA-Z]*\\s*$").matchEntire(s) ?: return null
            return m.groupValues[1].toFloatOrNull()?.takeIf { it > 0f }
        }

        private fun rootAspect(atts: Attributes): Float {
            val vb = atts.getValue("viewBox")
            if (vb != null) {
                val p = vb.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
                if (p.size == 4 && p[2] > 0f && p[3] > 0f) return (p[2] / p[3]).coerceIn(0.05f, 20f)
            }
            val w = number(atts.getValue("width")); val h = number(atts.getValue("height"))
            if (w != null && h != null && !(atts.getValue("width") ?: "").contains('%')) return (w / h).coerceIn(0.05f, 20f)
            return 1f
        }
    }
}
