package com.rsa.autobooker

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var switchAutoClick: SwitchCompat
    private lateinit var switchAutoBook: SwitchCompat
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibrate: SwitchCompat

    private lateinit var modeStealth: CardView
    private lateinit var modeNormal: CardView
    private lateinit var modeAggressive: CardView
    private lateinit var modeStealthCheck: View
    private lateinit var modeNormalCheck: View
    private lateinit var modeAggressiveCheck: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = AppSettings(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        switchAutoClick = findViewById(R.id.switchAutoClick)
        switchAutoBook = findViewById(R.id.switchAutoBook)
        switchSound = findViewById(R.id.switchSound)
        switchVibrate = findViewById(R.id.switchVibrate)

        modeStealth = findViewById(R.id.modeStealth)
        modeNormal = findViewById(R.id.modeNormal)
        modeAggressive = findViewById(R.id.modeAggressive)
        modeStealthCheck = findViewById(R.id.modeStealthCheck)
        modeNormalCheck = findViewById(R.id.modeNormalCheck)
        modeAggressiveCheck = findViewById(R.id.modeAggressiveCheck)

        // Load saved settings
        switchAutoClick.isChecked = settings.autoClick
        switchAutoBook.isChecked = settings.autoBook
        switchSound.isChecked = settings.soundEnabled
        switchVibrate.isChecked = settings.vibrateEnabled
        updateModeSelection(settings.scanMode)

        // Listeners
        switchAutoClick.setOnCheckedChangeListener { _, isChecked ->
            settings.autoClick = isChecked
        }

        switchAutoBook.setOnCheckedChangeListener { _, isChecked ->
            settings.autoBook = isChecked
            if (isChecked) {
                switchAutoClick.isChecked = true
                settings.autoClick = true
            }
        }

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            settings.soundEnabled = isChecked
        }

        switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            settings.vibrateEnabled = isChecked
        }

        // Mode selectors
        modeStealth.setOnClickListener {
            settings.scanMode = ScanMode.STEALTH
            updateModeSelection(ScanMode.STEALTH)
        }
        modeNormal.setOnClickListener {
            settings.scanMode = ScanMode.NORMAL
            updateModeSelection(ScanMode.NORMAL)
        }
        modeAggressive.setOnClickListener {
            settings.scanMode = ScanMode.AGGRESSIVE
            updateModeSelection(ScanMode.AGGRESSIVE)
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun updateModeSelection(mode: ScanMode) {
        val activeColor = ContextCompat.getColor(this, R.color.irish_green)
        val inactiveColor = ContextCompat.getColor(this, android.R.color.transparent)

        modeStealthCheck.setBackgroundColor(if (mode == ScanMode.STEALTH) activeColor else inactiveColor)
        modeNormalCheck.setBackgroundColor(if (mode == ScanMode.NORMAL) activeColor else inactiveColor)
        modeAggressiveCheck.setBackgroundColor(if (mode == ScanMode.AGGRESSIVE) activeColor else inactiveColor)

        val activeBorder = ContextCompat.getColor(this, R.color.glass_border)
        val selectedBorder = ContextCompat.getColor(this, R.color.irish_gold)

        modeStealth.strokeColor = if (mode == ScanMode.STEALTH) selectedBorder else activeBorder
        modeNormal.strokeColor = if (mode == ScanMode.NORMAL) selectedBorder else activeBorder
        modeAggressive.strokeColor = if (mode == ScanMode.AGGRESSIVE) selectedBorder else activeBorder
    }
}
