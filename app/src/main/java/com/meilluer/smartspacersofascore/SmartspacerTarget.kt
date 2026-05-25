package com.meilluer.smartspacersofascore

import android.content.ComponentName
import android.graphics.drawable.Icon
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon as SmartspaceIcon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate

class Target : SmartspacerTargetProvider() {

    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        // If no active match is running, return an empty list so the widget is clean and dismissed
        if (!MatchData.flag) {
            return emptyList()
        }

        val targets = mutableListOf<SmartspaceTarget>()
        
        // Build the title (e.g., "Arsenal 2 - 1 Chelsea")
        val displayTitle = "${MatchData.homeTeam} ${MatchData.homeScore} - ${MatchData.awayScore} ${MatchData.awayTeam}"
        
        // Build the subtitle (using extras which holds current scorers or match events)
        val displaySubtitle = MatchData.extras

        targets.add(
            TargetTemplate.Basic(
                id = "notify",
                componentName = ComponentName(context!!, Target::class.java),
                title = Text(displayTitle),
                subtitle = Text(displaySubtitle),
                icon = SmartspaceIcon(Icon.createWithResource(context!!, R.drawable.soccer))
            ).create()
        )
        
        return targets
    }

    override fun getConfig(smartspacerId: String?): Config {
        return Config(
            label = "FlashScore Live Match",
            description = "Displays live football scores and goal scorers parsed from FlashScore notifications.",
            icon = Icon.createWithResource(context!!, R.drawable.soccer)
        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean {
        MatchData.flag = false
        notifyChange()
        return true
    }
}
