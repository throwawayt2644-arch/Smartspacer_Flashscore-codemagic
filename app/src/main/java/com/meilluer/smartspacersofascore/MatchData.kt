package com.meilluer.smartspacersofascore

object MatchData {
    var title: String = ""
    var subtitle: String = ""
    var homeTeam: String = "Home Team"
    var awayTeam: String = "Away Team"
    var homeScore: String = "0"
    var awayScore: String = "0"
    var extras: String = "No active match"
    var scorers: String = "No goal scorers yet"
    var flag: Boolean = false // Indicates if match is in progress

    fun reset() {
        title = ""
        subtitle = ""
        homeTeam = "Home Team"
        awayTeam = "Away Team"
        homeScore = "0"
        awayScore = "0"
        extras = "No active match"
        scorers = "No goal scorers yet"
        flag = false
    }
}
