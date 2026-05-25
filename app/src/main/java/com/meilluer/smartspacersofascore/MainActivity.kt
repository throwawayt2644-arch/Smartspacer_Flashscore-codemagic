package com.meilluer.smartspacersofascore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Random

class MainActivity : AppCompatActivity() {

    private lateinit var textPermissionStatus: TextView
    private lateinit var textPermissionDesc: TextView
    private lateinit var btnGrantPermission: Button

    private lateinit var textMatchStatus: TextView
    private lateinit var textHomeTeam: TextView
    private lateinit var textAwayTeam: TextView
    private lateinit var textHomeScore: TextView
    private lateinit var textAwayScore: TextView
    private lateinit var textScorers: TextView

    private lateinit var btnSimStart: Button
    private lateinit var btnSimHomeGoal: Button
    private lateinit var btnSimAwayGoal: Button
    private lateinit var btnSimHalf: Button
    private lateinit var btnSimFull: Button
    private lateinit var btnSimReset: Button

    private val random = Random()

    private val matchUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateMatchUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        initViews()

        // Setup Permission Grant Button
        btnGrantPermission.setOnClickListener {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        }

        // Setup Simulator Button Listeners
        setupSimulatorListeners()

        // Register receiver for match data updates
        val filter = IntentFilter("com.meilluer.smartspacersofascore.UPDATE_MATCH_DATA")
        ContextCompat.registerReceiver(
            this,
            matchUpdateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Load initial data
        updateMatchUI()
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
        updateMatchUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(matchUpdateReceiver)
    }

    private fun initViews() {
        textPermissionStatus = findViewById(R.id.textPermissionStatus)
        textPermissionDesc = findViewById(R.id.textPermissionDesc)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)

        textMatchStatus = findViewById(R.id.textMatchStatus)
        textHomeTeam = findViewById(R.id.textHomeTeam)
        textAwayTeam = findViewById(R.id.textAwayTeam)
        textHomeScore = findViewById(R.id.textHomeScore)
        textAwayScore = findViewById(R.id.textAwayScore)
        textScorers = findViewById(R.id.textScorers)

        btnSimStart = findViewById(R.id.btnSimStart)
        btnSimHomeGoal = findViewById(R.id.btnSimHomeGoal)
        btnSimAwayGoal = findViewById(R.id.btnSimAwayGoal)
        btnSimHalf = findViewById(R.id.btnSimHalf)
        btnSimFull = findViewById(R.id.btnSimFull)
        btnSimReset = findViewById(R.id.btnSimReset)
    }

    private fun checkNotificationPermission() {
        val isPermissionGranted = isNotificationServiceEnabled()
        if (isPermissionGranted) {
            textPermissionStatus.text = "ACTIVE"
            textPermissionStatus.setTextColor(Color.parseColor("#4CD137")) // Vibrant Green
            textPermissionDesc.text = "Successfully intercepting live score feeds from FlashScore."
            btnGrantPermission.visibility = View.GONE
        } else {
            textPermissionStatus.text = "ACTION REQUIRED"
            textPermissionStatus.setTextColor(Color.parseColor("#FF9F43")) // Vibrant Amber
            textPermissionDesc.text = "Notification Access is currently disabled. Tap 'Grant Access' to enable interceptor."
            btnGrantPermission.visibility = View.VISIBLE
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val packageName = packageName
        return cn != null && cn.contains(packageName)
    }

    private fun updateMatchUI() {
        textHomeTeam.text = MatchData.homeTeam
        textAwayTeam.text = MatchData.awayTeam
        textHomeScore.text = MatchData.homeScore
        textAwayScore.text = MatchData.awayScore
        textScorers.text = MatchData.scorers

        if (MatchData.flag) {
            textMatchStatus.text = MatchData.extras.uppercase()
            textMatchStatus.setBackgroundColor(Color.parseColor("#2E3558")) // Sleek blue
            textMatchStatus.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            if (MatchData.extras == "Finished") {
                textMatchStatus.text = "FINISHED"
                textMatchStatus.setBackgroundColor(Color.parseColor("#E84118")) // Vibrant red
                textMatchStatus.setTextColor(Color.parseColor("#FFFFFF"))
            } else {
                textMatchStatus.text = "NO ACTIVE MATCH"
                textMatchStatus.setBackgroundColor(Color.parseColor("#1B1E36")) // Muted dark
                textMatchStatus.setTextColor(Color.parseColor("#A0A5B5"))
            }
        }
    }

    private fun setupSimulatorListeners() {
        btnSimStart.setOnClickListener {
            MatchData.reset()
            FlashScoreNotificationParser.parse(
                this,
                "Real Madrid - Barcelona",
                "Match starts soon\nLineups are available"
            )
        }

        btnSimHomeGoal.setOnClickListener {
            // Guarantee starting match if it hasn't
            if (MatchData.homeTeam == "Home Team") {
                MatchData.homeTeam = "Real Madrid"
                MatchData.awayTeam = "Barcelona"
            }
            val currentHome = MatchData.homeScore.toIntOrNull() ?: 0
            val currentAway = MatchData.awayScore.toIntOrNull() ?: 0
            val nextHome = currentHome + 1
            val minute = random.nextInt(89) + 1
            val scorersList = listOf("Benzema", "Vinicius Jr", "Modric", "Rodrygo", "Bellingham")
            val scorer = scorersList[random.nextInt(scorersList.size)]

            FlashScoreNotificationParser.parse(
                this,
                "Real Madrid - Barcelona",
                "⚽ $minute' Goal! [$nextHome] - $currentAway ($scorer)"
            )
        }

        btnSimAwayGoal.setOnClickListener {
            // Guarantee starting match if it hasn't
            if (MatchData.homeTeam == "Home Team") {
                MatchData.homeTeam = "Real Madrid"
                MatchData.awayTeam = "Barcelona"
            }
            val currentHome = MatchData.homeScore.toIntOrNull() ?: 0
            val currentAway = MatchData.awayScore.toIntOrNull() ?: 0
            val nextAway = currentAway + 1
            val minute = random.nextInt(89) + 1
            val scorersList = listOf("Messi", "Lewandowski", "Pedri", "Gavi", "Raphinha")
            val scorer = scorersList[random.nextInt(scorersList.size)]

            FlashScoreNotificationParser.parse(
                this,
                "Real Madrid - Barcelona",
                "⚽ $minute' Goal! $currentHome - [$nextAway] ($scorer)"
            )
        }

        btnSimHalf.setOnClickListener {
            if (MatchData.homeTeam == "Home Team") {
                MatchData.homeTeam = "Real Madrid"
                MatchData.awayTeam = "Barcelona"
            }
            FlashScoreNotificationParser.parse(
                this,
                "Real Madrid - Barcelona",
                "Half-Time\nScore remains ${MatchData.homeScore} - ${MatchData.awayScore}"
            )
        }

        btnSimFull.setOnClickListener {
            if (MatchData.homeTeam == "Home Team") {
                MatchData.homeTeam = "Real Madrid"
                MatchData.awayTeam = "Barcelona"
            }
            FlashScoreNotificationParser.parse(
                this,
                "Real Madrid - Barcelona",
                "Finished\nFinal Score: ${MatchData.homeScore} - ${MatchData.awayScore}"
            )
        }

        btnSimReset.setOnClickListener {
            MatchData.reset()
            updateMatchUI()
            // Notify target provider to clear widget
            try {
                com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider.notifyChange(
                    this, Target::class.java, "notify"
                )
            } catch (e: Exception) {
                // Ignore if SDK is not active or initialized
            }
        }
    }
}
