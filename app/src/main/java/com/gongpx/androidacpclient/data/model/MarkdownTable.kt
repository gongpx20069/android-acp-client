package com.gongpx.androidacpclient.data.model

enum class MarkdownTableAlignment {
    Start,
    Center,
    End,
}

data class MarkdownTable(
    val headers: List<String>,
    val alignments: List<MarkdownTableAlignment>,
    val rows: List<List<String>>,
)

data class MarkdownTableParseResult(
    val table: MarkdownTable,
    val consumedLineCount: Int,
)

fun parseMarkdownTable(lines: List<String>, startIndex: Int): MarkdownTableParseResult? {
    if (startIndex < 0 || startIndex + 1 >= lines.size) return null
    if (startsSupportedMarkdownBlock(lines[startIndex])) return null
    val headers = splitMarkdownTableRow(lines[startIndex]) ?: return null
    val separators = splitMarkdownTableRow(lines[startIndex + 1]) ?: return null
    if (headers.isEmpty() || separators.size != headers.size) return null

    val alignments = separators.map { separator ->
        val trimmed = separator.trim()
        if (!TABLE_SEPARATOR.matches(trimmed)) return null
        when {
            trimmed.startsWith(":") && trimmed.endsWith(":") -> MarkdownTableAlignment.Center
            trimmed.endsWith(":") -> MarkdownTableAlignment.End
            else -> MarkdownTableAlignment.Start
        }
    }

    val rows = mutableListOf<List<String>>()
    var lineIndex = startIndex + 2
    while (lineIndex < lines.size && lines[lineIndex].isNotBlank()) {
        if (startsSupportedMarkdownBlock(lines[lineIndex])) break
        val cells = splitMarkdownTableRow(lines[lineIndex]) ?: break
        rows += List(headers.size) { columnIndex -> cells.getOrElse(columnIndex) { "" } }
        lineIndex++
    }

    return MarkdownTableParseResult(
        table = MarkdownTable(headers = headers, alignments = alignments, rows = rows),
        consumedLineCount = lineIndex - startIndex,
    )
}

private fun startsSupportedMarkdownBlock(line: String): Boolean {
    val trimmed = line.trimStart()
    return markdownCodeFenceDelimiterLength(trimmed, openFenceLength = null) != null ||
        trimmed.startsWith("> ") ||
        trimmed.startsWith("- ") ||
        trimmed.startsWith("* ") ||
        HEADING_PREFIX.matches(trimmed)
}

fun markdownCodeFenceDelimiterLength(line: String, openFenceLength: Int?): Int? {
    val trimmedLine = line.trimStart()
    if (!trimmedLine.startsWith("```")) return null
    val delimiterLength = countBackticks(trimmedLine, 0)
    if (delimiterLength < 3) return null
    val remainder = trimmedLine.drop(delimiterLength)
    return if (openFenceLength == null) {
        delimiterLength.takeIf { !remainder.contains('`') }
    } else {
        delimiterLength.takeIf { it >= openFenceLength && remainder.isBlank() }
    }
}

private fun splitMarkdownTableRow(line: String): List<String>? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null

    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var codeDelimiterLength = 0
    var delimiterCount = 0
    var index = 0

    while (index < trimmed.length) {
        val character = trimmed[index]
        when {
            codeDelimiterLength > 0 -> {
                if (character == '`') {
                    val runLength = countBackticks(trimmed, index)
                    cell.append("`".repeat(runLength))
                    if (runLength == codeDelimiterLength) codeDelimiterLength = 0
                    index += runLength
                } else {
                    cell.append(character)
                    index++
                }
            }
            character == '\\' -> {
                val nextCharacter = trimmed.getOrNull(index + 1)
                if (nextCharacter == '|' || nextCharacter == '\\') {
                    cell.append(nextCharacter)
                    index += 2
                } else {
                    cell.append(character)
                    index++
                }
            }
            character == '`' -> {
                val runLength = countBackticks(trimmed, index)
                cell.append("`".repeat(runLength))
                if (hasClosingBacktickRun(trimmed, index + runLength, runLength)) {
                    codeDelimiterLength = runLength
                }
                index += runLength
            }
            character == '|' -> {
                cells += cell.toString().trim()
                cell.clear()
                delimiterCount++
                index++
            }
            else -> {
                cell.append(character)
                index++
            }
        }
    }
    cells += cell.toString().trim()

    if (delimiterCount == 0) return null
    if (trimmed.startsWith("|")) cells.removeAt(0)
    if (endsWithUnescapedPipe(trimmed)) cells.removeAt(cells.lastIndex)
    return cells
}

private fun countBackticks(value: String, startIndex: Int): Int {
    var index = startIndex
    while (index < value.length && value[index] == '`') index++
    return index - startIndex
}

private fun hasClosingBacktickRun(value: String, startIndex: Int, delimiterLength: Int): Boolean {
    var index = startIndex
    while (index < value.length) {
        if (value[index] != '`') {
            index++
            continue
        }
        val runLength = countBackticks(value, index)
        if (runLength == delimiterLength) return true
        index += runLength
    }
    return false
}

private fun endsWithUnescapedPipe(value: String): Boolean {
    if (!value.endsWith("|")) return false
    var backslashCount = 0
    var index = value.lastIndex - 1
    while (index >= 0 && value[index] == '\\') {
        backslashCount++
        index--
    }
    return backslashCount % 2 == 0
}

private val TABLE_SEPARATOR = Regex("^:?-{3,}:?$")
private val HEADING_PREFIX = Regex("^#{1,6}\\s.*")
