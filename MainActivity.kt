package com.nmobile.luckytickets

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.nmobile.luckytickets.databinding.ActivityMainBinding
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("lucky_tickets", MODE_PRIVATE) }

    private var tickets: Long = 0
    private var plays: Int = 20
    private var nextAdAllowedAt: Long = 0L
    private var rewardedAd: RewardedAd? = null
    private var adLoading = false
    private var spinning = false

    private val symbols = listOf("🍒", "🍋", "🔔", "💎", "⭐", "7️⃣")
    private val jackpotTickets = 50_000L

    private lateinit var cells: List<List<TextView>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tickets = prefs.getLong(KEY_TICKETS, 0L)
        plays = prefs.getInt(KEY_PLAYS, 20)
        nextAdAllowedAt = prefs.getLong(KEY_NEXT_AD, 0L)

        cells = listOf(
            listOf(binding.r00, binding.r01, binding.r02, binding.r03, binding.r04),
            listOf(binding.r10, binding.r11, binding.r12, binding.r13, binding.r14),
            listOf(binding.r20, binding.r21, binding.r22, binding.r23, binding.r24)
        )

        binding.btnSpin.setOnClickListener { spin() }
        binding.btnGetPlays.setOnClickListener { requestRewardedPlayAd() }

        updateBalances()
        updateAdUi()

        MobileAds.initialize(this) {
            loadRewardedAd()
        }

        handler.post(cooldownTicker)
    }

    private fun spin() {
        if (spinning) return
        if (plays <= 0) {
            Toast.makeText(this, "You're out of plays. Watch an ad for 10 more.", Toast.LENGTH_SHORT).show()
            return
        }

        spinning = true
        plays -= 1
        saveState()
        updateBalances()
        binding.btnSpin.isEnabled = false
        binding.tvResult.text = "Spinning…"

        val finalGrid = List(3) { List(5) { symbols.random() } }

        var frame = 0
        val animation = object : Runnable {
            override fun run() {
                frame++
                for (row in 0..2) {
                    for (col in 0..4) {
                        cells[row][col].text = symbols.random()
                    }
                }

                if (frame < 14) {
                    handler.postDelayed(this, 70L)
                } else {
                    for (row in 0..2) {
                        for (col in 0..4) {
                            cells[row][col].text = finalGrid[row][col]
                        }
                    }
                    settleSpin(finalGrid[1])
                }
            }
        }
        handler.post(animation)
    }

    private fun settleSpin(centerLine: List<String>) {
        val payout = calculatePayout(centerLine)
        if (payout > 0) {
            tickets += payout
            binding.tvResult.text = if (payout == jackpotTickets) {
                "JACKPOT! +${formatNumber(payout)} tickets"
            } else {
                "WIN! +${formatNumber(payout)} tickets"
            }
        } else {
            binding.tvResult.text = "No win — spin again"
        }

        spinning = false
        saveState()
        updateBalances()
        binding.btnSpin.isEnabled = plays > 0
    }

    private fun calculatePayout(line: List<String>): Long {
        if (line.all { it == "7️⃣" }) return jackpotTickets

        val groups = line.groupingBy { it }.eachCount()
        val maxMatch = groups.values.maxOrNull() ?: 1
        return when (maxMatch) {
            5 -> 5_000L
            4 -> 1_500L
            3 -> 500L
            else -> 0L
        }
    }

    private fun requestRewardedPlayAd() {
        val remaining = nextAdAllowedAt - System.currentTimeMillis()
        if (remaining > 0) {
            Toast.makeText(this, "Another rewarded ad is available when the timer reaches zero.", Toast.LENGTH_SHORT).show()
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            loadRewardedAd()
            Toast.makeText(this, "Ad is loading. Try again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }

        rewardedAd = null
        var rewardGranted = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewardedAd()
                updateAdUi()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                loadRewardedAd()
                updateAdUi()
            }
        }

        ad.show(this) {
            if (!rewardGranted) {
                rewardGranted = true
                plays += 10
                nextAdAllowedAt = System.currentTimeMillis() + AD_COOLDOWN_MS
                saveState()
                updateBalances()
                updateAdUi()
                Toast.makeText(this, "+10 plays added!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadRewardedAd() {
        if (adLoading || rewardedAd != null) return
        adLoading = true
        updateAdUi()

        val request = AdRequest.Builder().build()
        RewardedAd.load(
            this,
            TEST_REWARDED_AD_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    adLoading = false
                    updateAdUi()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    adLoading = false
                    updateAdUi()
                    handler.postDelayed({ loadRewardedAd() }, 15_000L)
                }
            }
        )
    }

    private fun updateBalances() {
        binding.tvTickets.text = formatNumber(tickets)
        binding.tvPlays.text = plays.toString()
        val bonusPoints = tickets / 100_000.0
        binding.tvTicketValue.text = String.format(Locale.US, "%.2f bonus points", bonusPoints)
        binding.btnSpin.isEnabled = plays > 0 && !spinning
    }

    private fun updateAdUi() {
        val remaining = max(0L, nextAdAllowedAt - System.currentTimeMillis())
        if (remaining > 0) {
            val totalSeconds = (remaining + 999L) / 1000L
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            binding.btnGetPlays.isEnabled = false
            binding.btnGetPlays.text = "NEXT AD IN %02d:%02d".format(minutes, seconds)
            binding.tvCooldown.text = "Watch another rewarded ad when the timer reaches zero"
        } else {
            when {
                rewardedAd != null -> {
                    binding.btnGetPlays.isEnabled = true
                    binding.btnGetPlays.text = "WATCH AD • +10 PLAYS"
                    binding.tvCooldown.text = "Available now • one rewarded ad every 5 minutes"
                }
                adLoading -> {
                    binding.btnGetPlays.isEnabled = false
                    binding.btnGetPlays.text = "LOADING AD…"
                    binding.tvCooldown.text = "Preparing your rewarded ad"
                }
                else -> {
                    binding.btnGetPlays.isEnabled = true
                    binding.btnGetPlays.text = "RETRY AD"
                    binding.tvCooldown.text = "Ad unavailable right now — tap to retry"
                }
            }
        }
    }

    private val cooldownTicker = object : Runnable {
        override fun run() {
            updateAdUi()
            handler.postDelayed(this, 1_000L)
        }
    }

    private fun saveState() {
        prefs.edit()
            .putLong(KEY_TICKETS, tickets)
            .putInt(KEY_PLAYS, plays)
            .putLong(KEY_NEXT_AD, nextAdAllowedAt)
            .apply()
    }

    private fun formatNumber(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val KEY_TICKETS = "tickets"
        private const val KEY_PLAYS = "plays"
        private const val KEY_NEXT_AD = "next_ad_allowed_at"
        private const val AD_COOLDOWN_MS = 5 * 60 * 1000L

        // Official Google test rewarded-ad unit ID. Replace before publishing.
        private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}
