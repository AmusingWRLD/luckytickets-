# Lucky Tickets Casino - Android starter

This is a native Android/Kotlin casino-style ticket game starter.

## Implemented
- 5x3 slot-style board with a center payline.
- 1 play consumed per spin.
- Ticket payouts for 3/4/5 matching symbols.
- 7-7-7-7-7 jackpot.
- Ticket conversion display: **1,000 tickets = 0.01 bonus points** (points have no cash value).
- "Get more plays" rewarded ad flow: **+10 plays per completed rewarded ad**.
- **5-minute cooldown** between rewarded-ad rewards.
- Tickets, plays, and cooldown persist after app restart.
- Google AdMob official TEST app/ad IDs are included so development does not generate live ad traffic.

## Open / run
1. Open the `LuckyTicketsCasino` folder in Android Studio.
2. Let Android Studio sync/download Gradle dependencies.
3. Run on an Android device or emulator (Android 7.0 / API 24+).
4. The rewarded ad uses Google's official test rewarded-ad unit.

## Before publishing
Replace:
- AdMob sample app ID in `AndroidManifest.xml`.
- `TEST_REWARDED_AD_UNIT_ID` in `MainActivity.kt` with your real rewarded-ad unit ID.

Do not test your production ad IDs by repeatedly watching/clicking your own live ads. Keep test ads enabled during development.

## Easy values to change
In `MainActivity.kt`:
- `AD_COOLDOWN_MS` = 5 minutes.
- `plays += 10` = reward per watched ad.
- `jackpotTickets = 50_000L` = jackpot payout.
- `calculatePayout()` = payout table.

The current build has no purchase flow and no cash-withdrawal flow.

## One-click cloud APK build
This project includes `.github/workflows/build-apk.yml`.
After pushing it to GitHub, open **Actions → Build Android APK → Run workflow**. When the job finishes, download the `LuckyTickets-debug-apk` artifact; it contains `app-debug.apk`.
