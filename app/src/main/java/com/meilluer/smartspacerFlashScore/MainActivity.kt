package com.meilluer.smartspacerFlashScore

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    }

    private lateinit var textPermissionStatus: TextView
    private lateinit var textPermissionDesc: TextView
    private lateinit var btnGrantPermission: Button

    private lateinit var spinnerMatchSelector: Spinner
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private val spinnerOptionsList = mutableListOf<String>()

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
    private var hasPromptedListenerPermission = false
    private var hasPromptedPostNotification = false

    private val matchUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateSpinnerOptions()
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
            showNotificationListenerDialog()
        }

        // Setup Spinner Adapter and Event Listener
        setupSpinner()

        // Setup Simulator Button Listeners
        setupSimulatorListeners()

        // Register receiver for match data updates
        val filter = IntentFilter("com.meilluer.smartspacerFlashScore.UPDATE_MATCH_DATA")
        ContextCompat.registerReceiver(
            this,
            matchUpdateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Load initial data
        updateSpinnerOptions()
        updateMatchUI()
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
        updateSpinnerOptions()
        updateMatchUI()
        
        // Sequential popups trigger
        showSequentialPermissionPopups()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(matchUpdateReceiver)
    }

    private fun initViews() {
        textPermissionStatus = findViewById(R.id.textPermissionStatus)
        textPermissionDesc = findViewById(R.id.textPermissionDesc)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)

        spinnerMatchSelector = findViewById(R.id.spinnerMatchSelector)

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

    private fun setupSpinner() {
        spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            spinnerOptionsList
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerMatchSelector.adapter = spinnerAdapter

        spinnerMatchSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < spinnerOptionsList.size) {
                    val selectedOption = spinnerOptionsList[position]
                    MatchData.selectedMatchId = if (selectedOption == "Auto (Most Recent Live)") {
                        "AUTO"
                    } else {
                        selectedOption
                    }
                    updateMatchUI()
                    notifyTargetChanges()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateSpinnerOptions() {
        val currentSelection = if (MatchData.selectedMatchId == "AUTO") {
            "Auto (Most Recent Live)"
        } else {
            MatchData.selectedMatchId
        }

        spinnerOptionsList.clear()
        spinnerOptionsList.add("Auto (Most Recent Live)")
        
        // Add all active matches tracked in global state
        spinnerOptionsList.addAll(MatchData.activeMatches.keys)
        
        spinnerAdapter.notifyDataSetChanged()

        // Restore selected item index
        val selectIndex = spinnerOptionsList.indexOf(currentSelection)
        if (selectIndex >= 0) {
            spinnerMatchSelector.setSelection(selectIndex)
        } else {
            spinnerMatchSelector.setSelection(0)
            MatchData.selectedMatchId = "AUTO"
        }
    }

    private fun showSequentialPermissionPopups() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED && !hasPromptedPostNotification) {
                hasPromptedPostNotification = true
                showPostNotificationDialog()
                return
            }
        }

        if (!isNotificationServiceEnabled() && !hasPromptedListenerPermission) {
            hasPromptedListenerPermission = true
            showNotificationListenerDialog()
        }
    }

    private fun showPostNotificationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Allow Live Highlight Alerts")
            .setMessage("Would you like to receive system notifications for match events, goals, and half-time/full-time highlight summaries?")
            .setIcon(R.drawable.soccer)
            .setCancelable(false)
            .setPositiveButton("Allow") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_CODE_POST_NOTIFICATIONS
                    )
                }
            }
            .setNegativeButton("Maybe Later") { dialog, _ ->
                dialog.dismiss()
                if (!isNotificationServiceEnabled() && !hasPromptedListenerPermission) {
                    hasPromptedListenerPermission = true
                    showNotificationListenerDialog()
                }
            }
            .show()
    }

    private fun showNotificationListenerDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enable FlashScore Interceptor")
            .setMessage("To automatically capture live score feeds, goal events, and scorer details from FlashScore notifications, this app requires Notification Access.\n\nAll notification data is processed strictly on-device and is never shared.")
            .setIcon(R.drawable.soccer)
            .setCancelable(false)
            .setPositiveButton("Configure Settings") { _, _ ->
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)
            }
            .setNegativeButton("Maybe Later") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (!isNotificationServiceEnabled() && !hasPromptedListenerPermission) {
                hasPromptedListenerPermission = true
                showNotificationListenerDialog()
            }
        }
    }

    private fun checkNotificationPermission() {
        val isPermissionGranted = isNotificationServiceEnabled()
        if (isPermissionGranted) {
            textPermissionStatus.text = "ACTIVE"
            textPermissionStatus.setTextColor(Color.parseColor("#4CD137"))
            textPermissionDesc.text = "Successfully intercepting live score feeds from FlashScore."
            btnGrantPermission.visibility = View.GONE
        } else {
            textPermissionStatus.text = "ACTION REQUIRED"
            textPermissionStatus.setTextColor(Color.parseColor("#FF9F43"))
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
        val match = MatchData.getSelectedMatch()
        
        if (match != null) {
            textHomeTeam.text = match.homeTeam
            textAwayTeam.text = match.awayTeam
            textHomeScore.text = match.homeScore
            textAwayScore.text = match.awayScore
            textScorers.text = match.scorers

            if (match.target_visibility) {
                textMatchStatus.text = match.extras.uppercase()
                textMatchStatus.setBackgroundColor(Color.parseColor("#2E3558"))
                textMatchStatus.setTextColor(Color.parseColor("#FFFFFF"))
            } else {
                if (match.extras == "Finished") {
                    textMatchStatus.text = "FINISHED"
                    textMatchStatus.setBackgroundColor(Color.parseColor("#E84118"))
                    textMatchStatus.setTextColor(Color.parseColor("#FFFFFF"))
                } else {
                    textMatchStatus.text = "INACTIVE"
                    textMatchStatus.setBackgroundColor(Color.parseColor("#1B1E36"))
                    textMatchStatus.setTextColor(Color.parseColor("#A0A5B5"))
                }
            }
        } else {
            // Fallback for default state when list is empty
            textHomeTeam.text = "Home Team"
            textAwayTeam.text = "Away Team"
            textHomeScore.text = "0"
            textAwayScore.text = "0"
            textScorers.text = "No goal scorers yet"
            
            textMatchStatus.text = "NO ACTIVE MATCH"
            textMatchStatus.setBackgroundColor(Color.parseColor("#1B1E36"))
            textMatchStatus.setTextColor(Color.parseColor("#A0A5B5"))
        }
    }

    private fun notifyTargetChanges() {
        try {
            com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider.notifyChange(
                applicationContext, Target::class.java, "smartspacer_falshscore"
            )
        } catch (e: Exception) {
            // Safe fallback inside environments lacking active SDK bindings
        }
    }

    private fun setupSimulatorListeners() {
        // SIMULATE MATCH A (Real Madrid vs Barcelona)
        btnSimStart.setOnClickListener {
            FlashScoreNotificationParser.parse(
                this,
                "Real Madrid - Barcelona",
                "Match starts soon\nLineups are available"
            )
        }

        btnSimHomeGoal.setOnClickListener {
            val match = MatchData.activeMatches["Real Madrid - Barcelona"]
            val currentHome = match?.homeScore?.toIntOrNull() ?: 0
            val currentAway = match?.awayScore?.toIntOrNull() ?: 0
            val nextHome = currentHome + 1
            val minute = random.nextInt(89) + 1
            val scorers = listOf("Benzema", "Vinicius Jr", "Bellingham", "Rodrygo")
            val scorer = scorers[random.nextInt(scorers.size)]

            FlashScoreNotificationParser.parse(
                this,
                "Real Madrid - Barcelona",
                "⚽ $minute' Goal! [$nextHome] - $currentAway ($scorer)"
            )
        }

        btnSimAwayGoal.setOnClickListener {
            val match = MatchData.activeMatches["Real Madrid - Barcelona"]
            val currentHome = match?.homeScore?.toIntOrNull() ?: 0
            val currentAway = match?.awayScore?.toIntOrNull() ?: 0
            val nextAway = currentAway + 1
            val minute = random.nextInt(89) + 1
            val scorers = listOf("Messi", "Lewandowski", "Pedri", "Raphinha")
            val scorer = scorers[random.nextInt(scorers.size)]

            FlashScoreNotificationParser.parse(
                this,
                "Real Madrid - Barcelona",
                "⚽ $minute' Goal! $currentHome - [$nextAway] ($scorer)"
            )
        }

        // SIMULATE MATCH B (Arsenal vs Chelsea)
        btnSimHalf.setOnClickListener {
            // Automatically kickoffs Arsenal - Chelsea
            val match = MatchData.activeMatches["Arsenal - Chelsea"]
            val currentHome = match?.homeScore?.toIntOrNull() ?: 0
            val currentAway = match?.awayScore?.toIntOrNull() ?: 0
            val nextHome = currentHome + 1
            val minute = random.nextInt(40) + 1
            
            FlashScoreNotificationParser.parse(
                this,
                "Arsenal - Chelsea",
                "⚽ $minute' Goal! [$nextHome] - $currentAway (Saka)"
            )
        }

        btnSimFull.setOnClickListener {
            // Simulates an equalizer and finished state for Arsenal - Chelsea
            val match = MatchData.activeMatches["Arsenal - Chelsea"]
            val currentHome = match?.homeScore?.toIntOrNull() ?: 1
            val nextAway = currentHome // Makes it a draw e.g. 1 - 1
            
            FlashScoreNotificationParser.parse(
                this,
                "Arsenal - Chelsea",
                "⚽ 88' Goal! $currentHome - [$nextAway] (Palmer)\nFinished\nFinal Score: $currentHome - $nextAway"
            )
        }

        btnSimReset.setOnClickListener {
            MatchData.reset()
            updateSpinnerOptions()
            updateMatchUI()
            notifyTargetChanges()
        }
    }
}
