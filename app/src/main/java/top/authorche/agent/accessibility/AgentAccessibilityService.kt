package top.authorche.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class AgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance.clear()
        super.onDestroy()
    }

    companion object {
        private var instance = WeakReference<AgentAccessibilityService>(null)
        private const val MAX_NODES = 180

        fun isConnected(): Boolean = instance.get() != null

        fun snapshot(): Result<String> = runCatching {
            val service = requireService()
            val root = service.rootInActiveWindow
                ?: error("Активне вікно недоступне")
            breadthFirst(root).mapIndexedNotNull { index, node -> describeNode(index, node) }
                .joinToString("\n")
                .ifBlank { "Елементи інтерфейсу не знайдено" }
        }

        fun clickText(query: String): Result<String> = runCatching {
            require(query.isNotBlank()) { "Текст для пошуку порожній" }
            val root = requireService().rootInActiveWindow
                ?: error("Активне вікно недоступне")
            val candidate = breadthFirst(root).firstOrNull { node ->
                val visibleText = listOf(node.text, node.contentDescription)
                    .filterNotNull()
                    .joinToString(" ")
                visibleText.contains(query, ignoreCase = true)
            } ?: error("Елемент із текстом «$query» не знайдено")

            val clickable = generateSequence(candidate) { it.parent }
                .take(6)
                .firstOrNull { it.isClickable }
                ?: error("Знайдений елемент не підтримує натискання")
            check(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                "Система відхилила натискання"
            }
            "Натиснуто елемент «$query»"
        }

        fun clickId(id: Int): Result<String> = runCatching {
            val root = requireService().rootInActiveWindow ?: error("Активне вікно недоступне")
            val node = breadthFirst(root).getOrNull(id) ?: error("Елемент #$id більше не існує")
            val clickable = generateSequence(node) { it.parent }
                .take(6)
                .firstOrNull { it.isClickable }
                ?: error("Елемент #$id не підтримує натискання")
            check(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                "Система відхилила натискання"
            }
            "Натиснуто елемент #$id"
        }

        fun longClickText(query: String): Result<String> = runCatching {
            require(query.isNotBlank()) { "Текст для пошуку порожній" }
            val root = requireService().rootInActiveWindow ?: error("Активне вікно недоступне")
            val node = breadthFirst(root).firstOrNull {
                listOf(it.text, it.contentDescription).filterNotNull().joinToString(" ")
                    .contains(query, ignoreCase = true)
            } ?: error("Елемент із текстом «$query» не знайдено")
            val target = generateSequence(node) { it.parent }
                .take(6)
                .firstOrNull { it.isLongClickable }
                ?: error("Елемент не підтримує довге натискання")
            check(target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                "Система відхилила довге натискання"
            }
            "Виконано довге натискання «$query»"
        }

        fun setText(text: String, target: String?): Result<String> = runCatching {
            require(text.length <= 4_000) { "Текст задовгий" }
            val root = requireService().rootInActiveWindow
                ?: error("Активне вікно недоступне")
            val nodes = breadthFirst(root)
            val editable = if (target.isNullOrBlank()) {
                nodes.firstOrNull { it.isFocused && it.isEditable }
                    ?: nodes.firstOrNull { it.isEditable }
            } else {
                nodes.firstOrNull { node ->
                    node.isEditable && listOf(node.text, node.hintText, node.contentDescription)
                        .filterNotNull()
                        .joinToString(" ")
                        .contains(target, ignoreCase = true)
                }
            } ?: error("Поле введення не знайдено")

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            check(editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                "Система відхилила введення тексту"
            }
            "Текст введено"
        }

        fun globalAction(action: Int, label: String): Result<String> = runCatching {
            check(requireService().performGlobalAction(action)) {
                "Глобальна дія «$label» недоступна"
            }
            "Виконано: $label"
        }

        fun scrollForward(): Result<String> = runCatching {
            scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, "вперед")
        }

        fun scrollBackward(): Result<String> = runCatching {
            scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, "назад")
        }

        suspend fun tap(x: Float, y: Float): Result<String> = gesture(
            Path().apply { moveTo(x, y) },
            duration = 80,
            success = "Виконано натискання в точці ${x.toInt()}, ${y.toInt()}",
        )

        suspend fun swipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            duration: Long,
        ): Result<String> = gesture(
            Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            },
            duration.coerceIn(100, 2_000),
            "Виконано жест від ${startX.toInt()},${startY.toInt()} до ${endX.toInt()},${endY.toInt()}",
        )

        private fun scroll(action: Int, direction: String): String {
            val root = requireService().rootInActiveWindow
                ?: error("Активне вікно недоступне")
            val scrollable = breadthFirst(root).firstOrNull { it.isScrollable }
                ?: error("Область прокручування не знайдена")
            check(scrollable.performAction(action)) {
                "Прокручування не виконано"
            }
            return "Екран прокручено $direction"
        }

        private suspend fun gesture(path: Path, duration: Long, success: String): Result<String> = runCatching {
            val service = requireService()
            val metrics = service.resources.displayMetrics
            val bounds = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
            val pathBounds = android.graphics.RectF().also { path.computeBounds(it, true) }
            require(
                pathBounds.left >= bounds.left && pathBounds.top >= bounds.top &&
                    pathBounds.right <= bounds.right && pathBounds.bottom <= bounds.bottom
            ) { "Координати жесту поза екраном" }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            val completed = withTimeout(5_000) {
                suspendCancellableCoroutine { continuation ->
                    val accepted = service.dispatchGesture(
                        gesture,
                        object : AccessibilityService.GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                if (continuation.isActive) continuation.resume(true)
                            }

                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                if (continuation.isActive) continuation.resume(false)
                            }
                        },
                        null,
                    )
                    if (!accepted && continuation.isActive) continuation.resume(false)
                }
            }
            check(completed) { "Android відхилив жест" }
            success
        }

        private fun requireService(): AgentAccessibilityService = instance.get()
            ?: error("Увімкніть службу спеціальних можливостей Jarvis | AuthorAi")

        private fun breadthFirst(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val result = mutableListOf<AccessibilityNodeInfo>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty() && result.size < MAX_NODES) {
                val node = queue.removeFirst()
                result += node
                repeat(node.childCount) { index -> node.getChild(index)?.let(queue::addLast) }
            }
            return result
        }

        private fun describeNode(index: Int, node: AccessibilityNodeInfo): String? {
            val text = when {
                node.isPassword -> "••••••"
                !node.text.isNullOrBlank() -> node.text.toString()
                !node.contentDescription.isNullOrBlank() -> node.contentDescription.toString()
                !node.hintText.isNullOrBlank() -> "підказка=${node.hintText}"
                else -> ""
            }.replace("\n", " ").take(160)
            val flags = buildList {
                if (node.isClickable) add("click")
                if (node.isLongClickable) add("long_click")
                if (node.isEditable) add("edit")
                if (node.isScrollable) add("scroll")
                if (node.isChecked) add("checked")
            }.joinToString(",")
            if (text.isBlank() && flags.isBlank()) return null
            val bounds = Rect().also(node::getBoundsInScreen)
            val className = node.className?.toString()?.substringAfterLast('.').orEmpty()
            return "#$index $className text=\"$text\" [$flags] bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        }
    }
}
