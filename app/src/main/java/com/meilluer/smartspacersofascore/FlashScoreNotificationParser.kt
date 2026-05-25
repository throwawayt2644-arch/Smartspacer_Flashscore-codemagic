package com.meilluer.smartspacersofascore

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider

object FlashScoreNotificationParser {
    private const val TAG = "FlashScoreParser"

    fun parse(context: Context, title: String, subtitle: String) {
        Log.d(TAG, "Parsing - Title: \"$title\" | Subtitle: \"$subtitle\"")

        // 1. Reset or set active flag
        MatchData.title = title
        MatchData.subtitle = subtitle
        MatchData.flag = true // Keep tracking active match

        // 2. Parse Team Names from Title
        parseTeamsFromTitle(title)

        // 3. Handle specific match status in title or subtitle
        val contentLower = "$title\n$subtitle".lowercase()
        when {
            "lineups are available" in contentLower || "lineups" in contentLower -> {
                MatchData.extras = "Lineups are available"
            }
            "half-time" in contentLower || "half time" in contentLower -> {
                MatchData.extras = "Half-Time"
            }
            "finished" in contentLower || "full-time" in contentLower || "ft" in contentLower || "after extra time" in contentLower -> {
                MatchData.extras = "Finished"
                MatchData.flag = false // Match is complete
            }
            "postponed" in contentLower -> {
                MatchData.extras = "Postponed"
                MatchData.flag = false
            }
            "goal" in contentLower || "⚽" in contentLower -> {
                MatchData.extras = "Goal scored!"
                parseGoalDetails(subtitle)
            }
            else -> {
                if (MatchData.extras.isBlank() || MatchData.extras == "No active match") {
                    MatchData.extras = "In-Play"
                }
            }
        }

        Log.d(TAG, "Parsed MatchData state: " +
                "Home: ${MatchData.homeTeam} (${MatchData.homeScore}) | " +
                "Away: ${MatchData.awayTeam} (${MatchData.awayScore}) | " +
                "Scorers: ${MatchData.scorers} | " +
                "Status: ${MatchData.extras}")

        // 4. Send a local broadcast to MainActivity to refresh UI dynamically
        val updateIntent = Intent("com.meilluer.smartspacersofascore.UPDATE_MATCH_DATA")
        context.sendBroadcast(updateIntent)

        // 5. Notify Smartspacer of Target/Widget changes
        try {
            SmartspacerTargetProvider.notifyChange(context, Target::class.java, "notify")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify Smartspacer target change", e)
        }
    }

    private fun parseTeamsFromTitle(title: String) {
        val parts = title.split(" - ")
        if (parts.size == 2) {
            MatchData.homeTeam = cleanTeamName(parts[0])
            MatchData.awayTeam = cleanTeamName(parts[1])
        } else {
            // Fallback: If hyphen is differently spaced or format differs
            val singleHyphenParts = title.split("-")
            if (singleHyphenParts.size == 2) {
                MatchData.homeTeam = cleanTeamName(singleHyphenParts[0])
                MatchData.awayTeam = cleanTeamName(singleHyphenParts[1])
            }
        }
    }

    /**
     * Cleans team names by removing any scores and square brackets (e.g. "Real Madrid 2" -> "Real Madrid")
     */
    private fun cleanTeamName(name: String): String {
        return name.replace(Regex( AndrewsRegexPatterns.SCORE_PATTERN ), "")
            .replace(Regex( AndrewsRegexPatterns.BRACKETS_PATTERN ), "")
            .trim()
    }

    private fun parseGoalDetails(subtitle: String) {
        val lines = subtitle.lines()
        val namesList = mutableListOf<String>()
        var extractedHomeScore: Int? = null
        var extractedAwayScore: Int? = null

        // Regex patterns for extracting goals, score updates, and scorers
        // e.g. Goal! [1] - 0 or Goal! 1 - [2] or ⚽ 44' Goal! [1] - 0 Benzema
        val scoreRegex = Regex("""(?:Goal!|⚽)?\s*(\[\d+]|\d+)\s*-\s*(\[\d+]|\d+)""", RegexOption.IGNORE_CASE)
        val bracketsDigitRegex = Regex("""\d+""")

        for (line in lines) {
            if (line.isBlank()) continue

            // 1. Try to find the live score
            val scoreMatch = scoreRegex.find(line)
            if (scoreMatch != null) {
                val leftPart = scoreMatch.groupValues[1]
                val rightPart = scoreMatch.groupValues[2]

                // If a score has brackets, it signifies the team that just scored (e.g. "[2] - 1" or "1 - [2]")
                // Extract digits from the score parts
                bracketsDigitRegex.find(leftPart)?.value?.toIntOrNull()?.let { extractedHomeScore = it }
                bracketsDigitRegex.find(rightPart)?.value?.toIntOrNull()?.let { extractedAwayScore = it }
            }

            // 2. Extract scorer using robust elimination parsing
            // Let's filter only lines containing Goal! or ⚽
            if (line.contains("Goal!", ignoreCase = true) || line.contains("⚽")) {
                val scorer = extractScorerFromLine(line)
                if (scorer.isNotBlank() && scorer != "Goal!") {
                    namesList.add(scorer)
                }
            }
        }

        // Apply scores to global variable
        extractedHomeScore?.let { MatchData.homeScore = it.toString() }
        extractedAwayScore?.let { MatchData.awayScore = it.toString() }

        if (namesList.isNotEmpty()) {
            val currentScorersList = MatchData.scorers
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "No goal scorers yet" && it != "Scorer not available" }
                .toMutableList()

            // Avoid duplicating the same goal scorer if notification triggers twice
            for (name in namesList) {
                if (name !in currentScorersList) {
                    currentScorersList.add(name)
                }
            }

            MatchData.scorers = currentScorersList.joinToString(", ").ifBlank { "Scorer not available" }
            MatchData.extras = "Goal: ${namesList.last()}"
        }
    }

    /**
     * Extracts scorer name by removing known prefixes/suffixes and patterns from a line
     */
    private fun extractScorerFromLine(line: String): String {
        // Strip out the ball emoji, timestamps (e.g. 45+2', 12'), the word Goal!, and the scores
        var cleaned = line.replace("⚽", "")
            .replace(Regex("""\b\d+(?:\+\d+)?'\b"""), "") // removes timestamps like 44', 90+2'
            .replace(Regex("""Goal!""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[?\d+]?\s*-\s*\[?\d+]?"""), "") // removes score updates like "[1] - 0" or "1 - [2]"
            .replace(Regex("""\(\)"""), "") // removes empty parenthesis
            .trim()

        // Strip leading/trailing punctuation and parenthesis if any
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        cleaned = cleaned.trim(',', '.', ' ', '(', ')')

        return cleaned
    }
}

/**
 * Inner constant patterns for robust regex operations
 */
object AndrewsRegexPatterns {
    const val SCORE_PATTERN = """\b\d+\b"""
    const val BRACKETS_PATTERN = """[\[\]]"""
}
