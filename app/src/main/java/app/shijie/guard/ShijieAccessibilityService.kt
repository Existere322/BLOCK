package app.shijie.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import app.shijie.MainActivity
import app.shijie.R
import app.shijie.ShijieApp
import app.shijie.domain.BlockReason
import app.shijie.domain.EmergencyRelease
import app.shijie.system.SettingsNavigator

class ShijieAccessibilityService : AccessibilityService(), GuardHost {
    private val handler = Handler(Looper.getMainLooper())
    private var enforcement: Runnable? = null
    private lateinit var overlay: OverlayController
    private lateinit var engine: GuardEngine
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> engine.onScreenOff()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> engine.onScreenOn()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        engine = (application as ShijieApp).graph.engine
        overlay = OverlayController(this, engine)
        engine.attach(this)
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        engine.onConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (::engine.isInitialized) {
        engine.noteEvent()
        if (packageName == "com.android.systemui") return
        if (engine.shouldIgnoreForeground(packageName)) return
        engine.onForeground(packageName)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::engine.isInitialized) engine.detach(this)
        if (::overlay.isInitialized) overlay.hide()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun showBlock(model: BlockOverlayModel) {
        overlay.show(model)
    }

    override fun hideBlock() {
        overlay.hide()
    }

    override fun schedule(delayMs: Long, action: () -> Unit) {
        cancelSchedule()
        val runnable = Runnable { action() }
        enforcement = runnable
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancelSchedule() {
        enforcement?.let { handler.removeCallbacks(it) }
        enforcement = null
    }

    override fun screenInteractive(): Boolean {
        val power = getSystemService(POWER_SERVICE) as android.os.PowerManager
        return power.isInteractive
    }

    companion object {
        var instance: ShijieAccessibilityService? = null
    }
}

class OverlayController(
    private val service: ShijieAccessibilityService,
    private val engine: GuardEngine,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var model: BlockOverlayModel? = null
    private var waitStartedAt = 0L
    private var ticker: Runnable? = null

    fun show(model: BlockOverlayModel) {
        if (this.model?.packageName != model.packageName || root == null) {
            waitStartedAt = System.currentTimeMillis()
        }
        this.model = model
        if (root == null) {
            root = LayoutInflater.from(service).inflate(R.layout.overlay_block, null)
            wire(root!!)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            try {
                windowManager.addView(root, params)
                Log.i("Shijie", "overlay shown")
            } catch (error: Exception) {
                Log.e("Shijie", "overlay add failed: ${error.message}")
                root = null
                return
            }
            startTicker()
        }
        render()
    }

    fun hide() {
        stopTicker()
        val view = root ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        root = null
        model = null
    }

    private fun wire(view: View) {
        view.findViewById<Button>(R.id.overlay_dismiss).setOnClickListener { engine.dismiss() }
        view.findViewById<Button>(R.id.overlay_open_app).setOnClickListener {
            val intent = Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                service.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(service, "请从桌面打开时界", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.overlay_open_settings).setOnClickListener {
            if (!SettingsNavigator.openSettingsRoot(service)) {
                service.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        view.findViewById<Button>(R.id.overlay_release).setOnClickListener {
            val current = model ?: return@setOnClickListener
            val reason = view.findViewById<EditText>(R.id.overlay_reason_input).text?.toString().orEmpty()
            engine.requestRelease(current.packageName, current.groupId, reason, waitStartedAt) { error ->
                if (error != null) {
                    Toast.makeText(service, error, Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }
        view.findViewById<EditText>(R.id.overlay_reason_input).addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) = render()
            },
        )
    }

    private fun render() {
        val view = root ?: return
        val current = model ?: return
        val title = view.findViewById<TextView>(R.id.overlay_title)
        val reason = view.findViewById<TextView>(R.id.overlay_reason)
        val detail = view.findViewById<TextView>(R.id.overlay_detail)
        val hint = view.findViewById<TextView>(R.id.overlay_release_hint)
        val input = view.findViewById<EditText>(R.id.overlay_reason_input)
        val release = view.findViewById<Button>(R.id.overlay_release)
        title.text = if (current.test) "拦截测试" else "已暂停"
        reason.text = current.appLabel
        detail.text = current.detail
        val showRelease = current.releaseAvailable && !current.test
        input.visibility = if (showRelease) View.VISIBLE else View.GONE
        release.visibility = if (showRelease) View.VISIBLE else View.GONE
        if (!showRelease) {
            hint.text = if (current.test) "确认遮罩可见后，点“我知道了”回到桌面。" else "本组今日已使用应急放行。点“我知道了”后可以继续使用其他应用。"
            return
        }
        val elapsed = System.currentTimeMillis() - waitStartedAt
        val remain = (EmergencyRelease.wait.toMillis() - elapsed).coerceAtLeast(0L)
        val reasonText = input.text?.toString().orEmpty()
        val ready = remain == 0L && reasonText.isNotBlank()
        release.isEnabled = ready
        release.text = if (remain == 0L) "应急放行 5 分钟" else "应急放行（${(remain + 999) / 1000} 秒）"
        hint.text = "每组每天只能放行一次。放行 5 分钟，期间仍会计入额度；重启手机会立即取消。"
        if (current.reason == BlockReason.QUOTA_EXHAUSTED) {
            detail.text = current.detail
        }
    }

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                if (root == null) return
                render()
                handler.postDelayed(this, 1000)
            }
        }
        ticker = runnable
        handler.postDelayed(runnable, 1000)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }
}