package com.meilluer.smartspacerFlashScore

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.graphics.drawable.Icon.createWithResource
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon as SmartspaceIcon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate

class Target : SmartspacerTargetProvider() {

    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        // Resolve the active display match (user selected or auto-recent live feed)
        val match = MatchData.getSelectedMatch()
        
        // If no match is active, or the active match visibility is disabled, hide target
        if (match == null || !match.target_visibility) {
            return emptyList()
        }

        val targets = mutableListOf<SmartspaceTarget>()
        
        // Build the title (e.g., "Real Madrid 2 - 1 Barcelona")
        val displayTitle = "${match.homeTeam} ${match.homeScore} - ${match.awayScore} ${match.awayTeam}"
        
        // Build the subtitle (stating goal events or live highlights)
        val displaySubtitle = match.extras

        targets.add(
            TargetTemplate.Basic(
                id = "smartspacer_falshscore",
                componentName = ComponentName(context!!, Target::class.java),
                title = Text(displayTitle),
                subtitle = Text(displaySubtitle),
                icon = SmartspaceIcon(createWithResource(context!!, R.drawable.football), shouldTint = false)
            ).create()
        )
        
        return targets
    }

    override fun getConfig(smartspacerId: String?): Config {
        return Config(
            label = "FlashScore Live Match",
            description = "Displays live football scores and goal scorers parsed from FlashScore notifications.",
            icon = createWithResource(context!!, R.drawable.football)
        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean {
        // Hide the selected match details from launcher
        MatchData.getSelectedMatch()?.let {
            it.target_visibility = false
        }
        notifyChange()
        return true
    }
}
