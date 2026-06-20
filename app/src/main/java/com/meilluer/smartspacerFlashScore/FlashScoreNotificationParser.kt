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

        // 4. Handle specific match status inside clean match state
        val contentLower = "$title\n$subtitle".lowercase()
        when {
            "finished" in contentLower || "full-time" in contentLower || "ft" in contentLower || "after extra time" in contentLower -> {
                // Try to parse the final score from subtitle or title (e.g. "Match finished: 2 - 1" or "Wolves 2 - 1 Olympic FC Finished")
                val finalScoreRegex = Regex("""(\d+)\s*-\s*(\d+)""")
                val scoreMatch = finalScoreRegex.find(subtitle) ?: finalScoreRegex.find(title)
                if (scoreMatch != null) {
                    match.homeScore = scoreMatch.groupValues[1]
                    match.awayScore = scoreMatch.groupValues[2]
                }
                
                if (match.flag) { // Only schedule if it was previously active to avoid duplicate handlers
                    match.extras = "Finished"
                    match.flag = false // Match complete
                    match.target_visibility = true // Keep visible for 30 mins
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        match.target_visibility = false
                        try {
                            SmartspacerTargetProvider.notifyChange(context.applicationContext, Target::class.java, "smartspacer_falshscore")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to notify Smartspacer target change in delayed handler", e)
                        }
                        val updateIntent = Intent("com.meilluer.smartspacerFlashScore.UPDATE_MATCH_DATA")
                        updateIntent.putExtra("matchId", match.id)
                        context.sendBroadcast(updateIntent)
                    }, 30 * 60 * 1000L) // 30 minutes delay
                }
            }
            "lineups are available" in contentLower || "lineups" in contentLower || "starts soon" in contentLower || "about to start" in contentLower || "starts in" in contentLower -> {
                match.extras = "Match about to start"
                match.flag = true
                match.target_visibility = true
            }
            "half-time" in contentLower || "half time" in contentLower || "ht" in contentLower -> {
                match.extras = "Half-Time"
                match.flag = true
                match.target_visibility = true
            }
            "postponed" in contentLower -> {
                match.extras = "Postponed"
                match.flag = false
                match.target_visibility = false
            }
            "goal" in contentLower || "⚽" in contentLower -> {
                match.flag = true
                match.target_visibility = true
                parseGoalDetails(context, match, subtitle)
            }
            else -> {
                if (match.extras.isBlank() || match.extras == "Match started" || match.extras == "No active match") {
                    match.extras = "In Play"
                }
                match.flag = true
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
            SmartspacerTargetProvider.notifyChange(context.applicationContext, Target::class.java, "smartspacer_falshscore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify Smartspacer target change", e)
        }
    }

    private fun parseTeamsFromTitle(title: String): Pair<String, String>? {
        // 1. Strip out common match status prefixes case-insensitively (e.g. "FT Real Madrid", "HT Arsenal", "Finished: Liverpool")
        var cleanedTitle = title
            .replace(Regex("""^(?i)(?:FT|HT|Finished|Half-Time|Half Time|Postponed|Live)\s*:?\s*"""), "")
            .trim()

        // 2. Strip any score suffix at the end of the title (e.g. "USA - Australia 2 - 0" -> "USA - Australia")
        cleanedTitle = cleanedTitle
            .replace(Regex("""\s+\[?\d+]?\s*-\s*\[?\d+]?$"""), "")
            .trim()

        var parts = cleanedTitle.split(" - ")
        if (parts.size != 2) {
            parts = cleanedTitle.split("-")
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

    private fun parseGoalDetails(context: Context, match: MatchState, subtitle: String) {
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

                // Extract digits from the score parts ONLY if they represent the actively changed score marked by brackets!
                if (leftPart.contains("[") || leftPart.contains("]")) {
                    bracketsDigitRegex.find(leftPart)?.value?.toIntOrNull()?.let { extractedHomeScore = it }
                }
                if (rightPart.contains("[") || rightPart.contains("]")) {
                    bracketsDigitRegex.find(rightPart)?.value?.toIntOrNull()?.let { extractedAwayScore = it }
                }
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
            
            // Set the subtitle to the goal scorer(s)
            val scorerText = namesList.joinToString(", ")
            match.extras = scorerText

            // Schedule resetting the subtitle back to "In Play" after 2 minutes
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // Check if the match is still active, hasn't transitioned to HT/FT, and still shows the scorer
                if (match.extras == scorerText && match.flag && match.extras != "Half Time" && match.extras != "Finished") {
                    match.extras = "In Play"
                    
                    // Notify Smartspacer
                    try {
                        SmartspacerTargetProvider.notifyChange(context.applicationContext, Target::class.java, "smartspacer_falshscore")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to notify Smartspacer target change in delayed goal handler", e)
                    }

                    // Notify UI
                    val updateIntent = Intent("com.meilluer.smartspacerFlashScore.UPDATE_MATCH_DATA")
                    updateIntent.putExtra("matchId", match.id)
                    context.sendBroadcast(updateIntent)
                }
            }, 2 * 60 * 1000L) // 2 minutes delay
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
