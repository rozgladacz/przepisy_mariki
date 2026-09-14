package pl.local.przepisy.data.importer

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Parser for older Alaantkoweblw entries. Those pages predate Recipe JSON-LD and keep
 * ingredients either as <br>-separated lines or as consecutive short divs.
 */
object AlaantkoweblwLegacyParser {
    private val ingredientHeading = Regex("^składniki\\s*:?\$", RegexOption.IGNORE_CASE)
    private val instructionVerb = Regex(
        "\\b(?:wymieszaj|wymieszać|mieszamy|nakładamy|posypujemy|gotuj|gotować|ugotuj|ugotować|dodaj|dodać|" +
            "przełóż|przełożyć|pokrój|pokroić|zetrzyj|zetrzeć|piecz|piec|duś|dusić|" +
            "smaż|smażyć|rozgrzej|rozgrzać|obierz|obrać|zalej|zalać|wyłóż|wyłożyć|" +
            "połącz|połączyć|wrzuć|wrzucić|podsmaż|podsmażyć)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val yieldLine = Regex("^przepis\\s+na\\b", RegexOption.IGNORE_CASE)
    private val boundary = Regex(
        "^(?:ps\\.|nie zapomnij|udostępnij|zobacz także|podobne przepisy|komentarze|wasze zdjęcia)",
        RegexOption.IGNORE_CASE,
    )

    fun parse(document: Document): DomRecipe? {
        val title = document.selectFirst("h1")?.text()?.clean()?.trimRecipeQuotes().orEmpty()
        if (title.isBlank()) return null
        val outer = document.select(".main-content-container.wysiwyg-content").firstOrNull() ?: return null
        val root = outer.children().firstOrNull {
            it.hasClass("container") && it.hasClass("wysiwyg-content")
        } ?: outer
        val blocks = root.leafTextBlocks()
        if (blocks.isEmpty()) return null

        val explicitIndex = blocks.indexOfFirst { block ->
            block.lines.any { ingredientHeading.matches(it.clean()) }
        }
        val multilineIndex = blocks.indexOfFirst { block ->
            block.lines.count { it.length in 2..120 } >= 3 &&
                block.lines.none { it.startsWithNumberedStep() }
        }

        val ingredientLines = mutableListOf<String>()
        val instructionLines = mutableListOf<String>()
        var originalYield = ""
        val ingredientStart: Int
        val instructionStart: Int

        if (explicitIndex >= 0 || multilineIndex >= 0) {
            ingredientStart = if (explicitIndex >= 0) explicitIndex else multilineIndex
            val first = blocks[ingredientStart].lines
                .dropWhile { ingredientHeading.matches(it.clean()) }
                .filterNot { ingredientHeading.matches(it.clean()) }
            ingredientLines += first
            var cursor = ingredientStart + 1
            while (cursor < blocks.size && !blocks[cursor].looksLikeInstruction()) {
                val lines = blocks[cursor].lines
                if (lines.size == 1 && lines.first().trim().endsWith(":")) {
                    cursor += 1
                    continue
                }
                if (lines.isNotEmpty() && lines.all { it.length <= 120 }) {
                    ingredientLines += lines
                    cursor += 1
                } else {
                    break
                }
            }
            instructionStart = cursor
        } else {
            val firstInstruction = blocks.indices.firstOrNull { index ->
                index >= 3 &&
                    blocks[index].looksLikeInstruction() &&
                    blocks.subList((index - 3).coerceAtLeast(0), index)
                        .takeLast(3)
                        .all { it.isShortIngredientBlock() }
            } ?: -1
            if (firstInstruction < 3) return null
            var runStart = firstInstruction - 1
            while (runStart >= 0 && blocks[runStart].isShortIngredientBlock()) runStart -= 1
            runStart += 1
            if (firstInstruction - runStart < 3) return null
            ingredientStart = runStart
            blocks.subList(runStart, firstInstruction).forEach { ingredientLines += it.lines }
            instructionStart = firstInstruction
        }

        instructionLoop@ for (block in blocks.drop(instructionStart)) {
            for (line in block.lines) {
                when {
                    boundary.containsMatchIn(line) -> break@instructionLoop
                    yieldLine.containsMatchIn(line) -> originalYield = line.clean()
                    line.endsWith(":") && line.length < 80 -> Unit
                    line.isNotBlank() -> instructionLines += line.clean()
                }
            }
        }

        val ingredients = ingredientLines
            .map(String::clean)
            .filter { it.isNotBlank() && !ingredientHeading.matches(it) }
        if (ingredients.isEmpty() || instructionLines.isEmpty()) return null
        val description = blocks.take(ingredientStart)
            .flatMap(LegacyTextBlock::lines)
            .filter { it.length >= 25 }
            .joinToString("\n\n")
            .clean()

        return DomRecipe(
            title = title,
            description = description,
            yield = originalYield,
            imageUrl = document.openGraphImage(),
            ingredientGroups = listOf("Składniki" to ingredients),
            instructionGroups = listOf("Wykonanie" to instructionLines),
        )
    }

    private fun LegacyTextBlock.looksLikeInstruction(): Boolean {
        val text = lines.joinToString(" ")
        return lines.any { it.length >= 125 } ||
            (text.length >= 55 && instructionVerb.containsMatchIn(text))
    }

    private fun LegacyTextBlock.isShortIngredientBlock(): Boolean {
        val value = lines.singleOrNull() ?: return false
        return value.length in 2..120 &&
            !value.endsWith(":") &&
            (!value.endsWith(".") || value.length < 50)
    }
}

/**
 * Rozkoszny uses BlogPosting metadata and puts recipe data in Elementor paragraphs.
 * Ingredient cards use <br> separators, while method paragraphs are numbered.
 */
object RozkosznyDomParser {
    private val numberedStep = Regex("^\\s*\\d+[.)]\\s*(.+)\$")
    private val yieldPattern = Regex(
        "\\b(?:tortownic\\w*|form\\w*|blach\\w*|porcj\\w*|sztuk\\w*|średnic\\w*)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun parse(document: Document): DomRecipe? {
        val title = document.selectFirst("h1")?.text()?.clean().orEmpty()
        if (title.isBlank()) return null
        val root = document.selectFirst(
            ".elementor-widget-theme-post-content .elementor-widget-container",
        ) ?: return null
        val paragraphs = root.children()
            .filter { it.tagName() == "p" }
            .map { LegacyTextBlock(it.textLines()) }
            .filter { it.lines.isNotEmpty() }
        if (paragraphs.isEmpty()) return null

        val mainCardIndex = paragraphs.indexOfFirst { block ->
            block.lines.size >= 3 && block.lines.first().sameRecipeTitle(title)
        }
        if (mainCardIndex < 0) return null

        val ingredientGroups = mutableListOf<Pair<String, List<String>>>()
        val mainLines = paragraphs[mainCardIndex].lines.drop(1).toMutableList()
        var originalYield = ""
        if (mainLines.firstOrNull()?.let(yieldPattern::containsMatchIn) == true) {
            originalYield = mainLines.removeAt(0).clean()
        }
        if (mainLines.isNotEmpty()) {
            val firstLineIsSectionHeading = mainLines.size >= 2 &&
                !mainLines.first().looksLikeIngredientStart() &&
                mainLines.drop(1).any { it.looksLikeIngredientStart() }
            val heading = if (firstLineIsSectionHeading) mainLines.removeAt(0).trimEnd(':').clean() else "Składniki"
            if (mainLines.isNotEmpty()) ingredientGroups += heading to mainLines.map(String::clean)
        }

        var cursor = mainCardIndex + 1
        while (cursor < paragraphs.size && paragraphs[cursor].lines.firstOrNull()?.startsWithNumberedStep() != true) {
            val lines = paragraphs[cursor].lines
            if (lines.size >= 2 && lines.first().length <= 60) {
                ingredientGroups += lines.first().trimEnd(':').clean() to lines.drop(1).map(String::clean)
            }
            cursor += 1
        }

        val steps = mutableListOf<String>()
        while (cursor < paragraphs.size) {
            val lines = paragraphs[cursor].lines
            if (lines.firstOrNull()?.startsWithNumberedStep() != true) break
            lines.forEach { line ->
                numberedStep.matchEntire(line)?.groupValues?.get(1)?.clean()?.takeIf(String::isNotBlank)?.let(steps::add)
            }
            cursor += 1
        }
        if (ingredientGroups.sumOf { it.second.size } == 0 || steps.isEmpty()) return null

        val description = paragraphs.take(mainCardIndex)
            .flatMap(LegacyTextBlock::lines)
            .joinToString("\n\n")
            .clean()
        return DomRecipe(
            title = title,
            description = description,
            yield = originalYield,
            imageUrl = document.openGraphImage(),
            ingredientGroups = ingredientGroups,
            instructionGroups = listOf("Wykonanie" to steps),
        )
    }

    private fun String.sameRecipeTitle(title: String): Boolean =
        clean().trimEnd('.', ':').equals(title.clean().trimEnd('.', ':'), ignoreCase = true)

    private fun String.looksLikeIngredientStart(): Boolean = Regex(
        "^(?:\\d|[¼½¾⅓⅔⅛⅜⅝⅞]|pół(?:tora|torej)?\\b|ćwierć\\b|garść\\b|szczypta\\b|kilka\\b)",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(clean())
}

private data class LegacyTextBlock(val lines: List<String>)

private fun Element.leafTextBlocks(): List<LegacyTextBlock> {
    val elements = listOf(this) + select("p,div").filterNot { it === this }
    return elements
        .filterNot { element ->
            (element.parents() + element).any {
                val marker = "${it.id()} ${it.className()} ${it.attr("role")}"
                Regex("comment|komentar|advert|reklam|related|polecan|newsletter|social|share", RegexOption.IGNORE_CASE)
                    .containsMatchIn(marker)
            }
        }
        .map { LegacyTextBlock(it.directTextLines()) }
        .filter { it.lines.isNotEmpty() }
}

private fun Element.directTextLines(): List<String> {
    val lines = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        current.toString().clean().takeIf(String::isNotBlank)?.let(lines::add)
        current.clear()
    }
    for (node in childNodes()) {
        when (node) {
            is TextNode -> current.append(node.wholeText)
            is Element -> when (node.tagName()) {
                "br" -> flush()
                "p", "div" -> break
                else -> current.append(' ').append(node.text())
            }
        }
    }
    flush()
    return lines
}

private fun Element.textLines(): List<String> {
    val copy = clone()
    copy.select("br").forEach { it.replaceWith(TextNode("\n")) }
    return copy.wholeText()
        .split(Regex("[\\r\\n]+"))
        .map(String::clean)
        .filter(String::isNotBlank)
}

private fun Document.openGraphImage(): String? =
    selectFirst("meta[property=og:image]")?.attr("content")?.takeIf(String::isNotBlank)

private fun String.startsWithNumberedStep(): Boolean = Regex("^\\s*\\d+[.)]\\s+").containsMatchIn(this)

private fun String.clean(): String = replace('\u00a0', ' ')
    .replace(Regex("\\s+"), " ")
    .trim()
    .trimEnd(';')

private fun String.trimRecipeQuotes(): String = trim(' ', '"', '\'', '“', '”', '„')
