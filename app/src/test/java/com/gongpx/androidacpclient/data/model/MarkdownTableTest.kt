package com.gongpx.androidacpclient.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownTableTest {
    @Test
    fun parsesHeadersAlignmentAndRows() {
        val parsed = parseMarkdownTable(
            """
            | Name | Status | Score |
            | :--- | :----: | ----: |
            | AgentLink | Ready | 10 |
            | Bridge | Busy | 8 |

            trailing text
            """.trimIndent().lines(),
            startIndex = 0,
        )!!

        assertEquals(listOf("Name", "Status", "Score"), parsed.table.headers)
        assertEquals(
            listOf(
                MarkdownTableAlignment.Start,
                MarkdownTableAlignment.Center,
                MarkdownTableAlignment.End,
            ),
            parsed.table.alignments,
        )
        assertEquals(
            listOf(
                listOf("AgentLink", "Ready", "10"),
                listOf("Bridge", "Busy", "8"),
            ),
            parsed.table.rows,
        )
        assertEquals(4, parsed.consumedLineCount)
    }

    @Test
    fun preservesEscapedAndInlineCodePipes() {
        val parsed = parseMarkdownTable(
            listOf(
                "| Expression | Meaning |",
                "| --- | --- |",
                "| `a | b` | A code expression |",
                "| A \\| B | An escaped pipe |",
                "| ``a | b`` | A multi-backtick expression |",
                "| `\\|` | A literal backslash in code |",
            ),
            startIndex = 0,
        )!!

        assertEquals("`a | b`", parsed.table.rows[0][0])
        assertEquals("A | B", parsed.table.rows[1][0])
        assertEquals("``a | b``", parsed.table.rows[2][0])
        assertEquals("`\\|`", parsed.table.rows[3][0])
    }

    @Test
    fun padsRowsWithMissingCellsAndIgnoresExtraCells() {
        val parsed = parseMarkdownTable(
            listOf(
                "A | B",
                "--- | ---",
                "one | two | ignored",
                "only one |",
            ),
            startIndex = 0,
        )!!

        assertEquals(listOf("one", "two"), parsed.table.rows[0])
        assertEquals(listOf("only one", ""), parsed.table.rows[1])
    }

    @Test
    fun rejectsTextWithoutMarkdownSeparatorRow() {
        assertNull(
            parseMarkdownTable(
                listOf(
                    "Name | Status",
                    "AgentLink | Ready",
                ),
                startIndex = 0,
            ),
        )
    }

    @Test
    fun stopsBeforeCodeFenceWithPipeInInfoString() {
        val parsed = parseMarkdownTable(
            listOf(
                "| Name | Status |",
                "| --- | --- |",
                "| AgentLink | Ready |",
                "```kotlin title=a|b",
                "val value = \"x|y\"",
                "```",
            ),
            startIndex = 0,
        )!!

        assertEquals(listOf(listOf("AgentLink", "Ready")), parsed.table.rows)
        assertEquals(3, parsed.consumedLineCount)
    }

    @Test
    fun stopsBeforeFollowingMarkdownBlocksContainingPipes() {
        listOf(
            "# Heading | detail",
            "> Quote | detail",
            "- List item | detail",
            "* List item | detail",
        ).forEach { followingBlock ->
            val parsed = parseMarkdownTable(
                listOf(
                    "| Name | Status |",
                    "| --- | --- |",
                    "| AgentLink | Ready |",
                    followingBlock,
                ),
                startIndex = 0,
            )!!

            assertEquals(listOf(listOf("AgentLink", "Ready")), parsed.table.rows)
            assertEquals(3, parsed.consumedLineCount)
        }
    }

    @Test
    fun rejectsSupportedMarkdownBlocksAsTableHeaders() {
        listOf(
            "# Name | Status",
            "> Name | Status",
            "- Name | Status",
            "* Name | Status",
        ).forEach { header ->
            assertNull(
                parseMarkdownTable(
                    listOf(
                        header,
                        "--- | ---",
                    ),
                    startIndex = 0,
                ),
            )
        }
    }

    @Test
    fun preservesTripleBacktickInlineCodeInCells() {
        val parsed = parseMarkdownTable(
            listOf(
                "| Expression | Meaning |",
                "| --- | --- |",
                "| ```a|b``` | Inline code |",
            ),
            startIndex = 0,
        )!!

        assertEquals("```a|b```", parsed.table.rows.single().first())
    }

    @Test
    fun codeFenceClosingRunMustMatchOrExceedOpeningLength() {
        assertEquals(4, markdownCodeFenceDelimiterLength("````", openFenceLength = null))
        assertNull(markdownCodeFenceDelimiterLength("```", openFenceLength = 4))
        assertEquals(4, markdownCodeFenceDelimiterLength("````", openFenceLength = 4))
        assertEquals(5, markdownCodeFenceDelimiterLength("`````", openFenceLength = 4))
        assertNull(markdownCodeFenceDelimiterLength("```value```", openFenceLength = null))
    }
}
