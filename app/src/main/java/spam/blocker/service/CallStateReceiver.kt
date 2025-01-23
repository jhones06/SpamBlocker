package spam.blocker.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.TELECOM_SERVICE
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import spam.blocker.util.Util
import spam.blocker.util.logi
import spam.blocker.util.spf

class CallStateReceiver : BroadcastReceiver() {

    // Don't block calls that are not marked to block in CallScreenService.
    private fun shouldBlock(ctx: Context, currNumber: String): Pair<Boolean, Int> {
        // `numToBlock` and `lastCalledTime` are set in CallScreeningService,
        //   they are only set when the call should be blocked by "answer + hang up"
        val (numToBlock, lastCalledTime, delay) = spf.Temporary(ctx).getLastCallToBlock()

        // if the time since the `lastCalledTime` is less than 5 seconds,
        //   answer the call and hang up
        val now = System.currentTimeMillis()
        val tolerance = 5000 // 5 seconds
        return Pair(
            (now - lastCalledTime) < tolerance && numToBlock == Util.clearNumber(currNumber),
            delay
        )
    }

    private fun extractNumber(intent: Intent) : String? {
        return intent.extras?.getString("incoming_number")
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == "android.intent.action.PHONE_STATE") {

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

            when (state) {
                // ringing
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val currNumber = extractNumber(intent) ?: return
                    logi("RINGING, num: $currNumber")

                    val (block, _) = shouldBlock(ctx, currNumber)
                    if (block)
                        answerCall(ctx)
                }

                // call is active(in call)
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    val currNumber = extractNumber(intent) ?: return
                    logi("IN CALL, num: $currNumber")

                    val (block, delay) = shouldBlock(ctx, currNumber)

                    if (block) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            endCall(ctx)
                        }, delay.toLong() * 1000)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun answerCall(ctx: Context) {
        val telMgr = ctx.getSystemService(TELECOM_SERVICE) as TelecomManager
        telMgr.acceptRingingCall()
        logi("answer call")
    }
    @SuppressLint("MissingPermission")
    private fun endCall(ctx: Context) {
        val telMgr = ctx.getSystemService(TELECOM_SERVICE) as TelecomManager
        telMgr.endCall()
        logi("end call")
    }
}