package com.meilluer.smartspacerFlashScore

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider

object FlashScoreNotificationParser {
    private const val TAG = "FlashScoreParser"

    fun parse(context: Context, title: String, subtitle: String) {
        Log.d(TAG, "Parsing incoming feed - Title: \"$title\" | Subtitle: \"$subtitle\"")

        // 1. Identify Match Teams and derive clean MatchId
        val teams = parseTeamsFromTitle(title)
        if (teams == null) {
            Log.e(TAG, "Failed to parse teams from title: \"$title\". Ignoring notification.")
            return
        }
        val (homeTeamClean, awayTeamClean) = teams
        val matchId = "$homeTeamClean - $awayTeamClean"

        // 2. Retrieve or instantiate a new MatchState for this clean matchId
        val match = MatchData.activeMatches.getOrPut(matchId) {
            Log.d(TAG, "Discovered new match: \"$matchId\". Creating dynamic instance.")
            MatchState(
                id = matchId,
                homeTeam = homeTeamClean,
                awayTeam = awayTeamClean
            )
        }

        // 3. Update standard metadata and timestamp
        match.title = title
        match.subtitle = subtitle
        match.lastUpdated = System.currentTimeMillis()
        match.flag = true // Keep tracking active match

        // 4. Handle specific match status inside clean match state
        val contentLower = "$title\n$subtitle".lowercase()
        when {
            "lineups are available" in contentLower || "lineups" in contentLower || "starts soon" in contentLower || "about to start" in contentLower || "starts in" in contentLower -> {
                match.extras = "Lineups are available"
                match.target_visibility = true
            }
            "half-time" in contentLower || "half time" in contentLower -> {
                match.extras = "Half-Time"
                match.target_visibility = true
            }
            "finished" in contentLower || "full-time" in contentLower || "ft" in contentLower || "after extra time" in contentLower -> {
                match.extras = "Finished"
                match.flag = false // Match complete
                match.target_visibility = true // Keep visible for 30 mins
                
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    match.target_visibility = false
                    try {
                        SmartspacerTargetProvider.notifyChange(context, Target::class.java, "smartspacer_falshscore")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to notify Smartspacer target change in delayed handler", e)
                    }
                    val updateIntent = Intent("com.meilluer.smartspacerFlashScore.UPDATE_MATCH_DATA")
                    updateIntent.putExtra("matchId", match.id)
                    context.sendBroadcast(updateIntent)
                }, 30 * 60 * 1000L) // 30 minutes delay
            }
            "postponed" in contentLower -> {
                match.extras = "Postponed"
                match.flag = false
                match.target_visibility = false
            }
            "goal" in contentLower || "⚽" in contentLower -> {
                match.extras = "Goal scored!"
                match.target_visibility = true
                parseGoalDetails(match, subtitle)
            }
            else -> {
                if (match.extras.isBlank() || match.extras == "Match started") {
                    match.extras = "In-Play"
                }
                match.target_visibility = true
            }
        }

        Log.d(TAG, "Successfully updated match state: \"$matchId\" | " +
                "Score: ${match.homeScore} - ${match.awayScore} | " +
                "Scorers: ${match.scorers} | " +
                "Status: ${match.extras}")

        // 5. Send a local broadcast to MainActivity to refresh UI Spinner and Card details
        val updateIntent = Intent("com.meilluer.smartspacerFlashScore.UPDATE_MATCH_DATA")
        updateIntent.putExtra("matchId", matchId)
        context.sendBroadcast(updateIntent)

        // 6. Notify Smartspacer of Target/Widget changes
        try {
            SmartspacerTargetProvider.notifyChange(context, Target::class.java, "smartspacer_falshscore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify Smartspacer target change", e)
        }
    }

    private fun parseTeamsFromTitle(title: String): Pair<String, String>? {
        var parts = title.split(" - ")
        if (parts.size != 2) {
            parts = title.split("-")
        }
        if (parts.size == 2) {
            val home = cleanTeamName(parts[0])
            val away = cleanTeamName(parts[1])
            if (home.isNotBlank() && away.isNotBlank()) {
                return Pair(home, away)
            }
        }
        return null
    }

    /**
     * Cleans team names by removing any scores and square brackets (e.g. "Real Madrid 2" -> "Real Madrid")
     */
    private fun cleanTeamName(name: String): String {
        return name.replace(Regex( AndrewsRegexPatterns.SCORE_PATTERN ), "")
            .replace(Regex( AndrewsRegexPatterns.BRACKETS_PATTERN ), "")
            .trim()
    }

    private fun parseGoalDetails(match: MatchState, subtitle: String) {
        val lines = subtitle.lines()
        val namesList = mutableListOf<String>()
        var extractedHomeScore: Int? = null
        var extractedAwayScore: Int? = null

        // Regex patterns for extracting goals, score updates, and scorers
        val scoreRegex = Regex("""(?:Goal!|⚽)?\s*(\[\d+]|\d+)\s*-\s*(\[\d+]|\d+)""", RegexOption.IGNORE_CASE)
        val bracketsDigitRegex = Regex("""\d+""")

        for (line in lines) {
            if (line.isBlank()) continue

            // 1. Try to find the live score
            val scoreMatch = scoreRegex.find(line)
            if (scoreMatch != null) {
                val leftPart = scoreMatch.groupValues[1]
                val rightPart = scoreMatch.groupValues[2]

                // Extract digits from the score parts
                bracketsDigitRegex.find(leftPart)?.value?.toIntOrNull()?.let { extractedHomeScore = it }
                bracketsDigitRegex.find(rightPart)?.value?.toIntOrNull()?.let { extractedAwayScore = it }
            }

            // 2. Extract scorer using robust elimination parsing
            if (line.contains("Goal!", ignoreCase = true) || line.contains("⚽")) {
                val scorer = extractScorerFromLine(line)
                if (scorer.isNotBlank() && scorer != "Goal!") {
                    namesList.add(scorer)
                }
            }
        }

        // Apply scores to this specific match state
        extractedHomeScore?.let { match.homeScore = it.toString() }
        extractedAwayScore?.let { match.awayScore = it.toString() }

        if (namesList.isNotEmpty()) {
            val currentScorersList = match.scorers
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

            match.scorers = currentScorersList.joinToString(", ").ifBlank { "Scorer not available" }
            match.extras = "Goal: ${namesList.last()}"
        }
    }

    private fun extractScorerFromLine(line: String): String {
        var cleaned = line.replace("⚽", "")
            .replace(Regex("""\b\d+(?:\+\d+)?'\b"""), "") // removes timestamps like 44', 90+2'
            .replace(Regex("""Goal!""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[?\d+]?\s*-\s*\[?\d+]?"""), "") // removes score updates like "[1] - 0" or "1 - [2]"
            .replace(Regex("""\(\)"""), "") // removes empty parenthesis
            .trim()

        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        cleaned = cleaned.trim(',', '.', ' ', '(', ')')

        return cleaned
    }
}

object AndrewsRegexPatterns {
    const val SCORE_PATTERN = """\b\d+\b"""
    const val BRACKETS_PATTERN = """[\[\]]"""
}
