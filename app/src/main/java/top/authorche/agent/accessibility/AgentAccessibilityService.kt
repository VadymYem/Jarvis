package top.authorche.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

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
            val lines = mutableListOf<String>()
            collectNodes(root, lines, 0)
            lines.joinToString("\n").ifBlank { "Елементи інтерфейсу не знайдено" }
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
            val root = requireService().rootInActiveWindow
                ?: error("Активне вікно недоступне")
            val scrollable = breadthFirst(root).firstOrNull { it.isScrollable }
                ?: error("Область прокручування не знайдена")
            check(scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                "Прокручування не виконано"
            }
            "Екран прокручено вперед"
        }

        private fun requireService(): AgentAccessibilityService = instance.get()
            ?: error("Увімкніть службу спеціальних можливостей AuthorAI")

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

        private fun collectNodes(
            node: AccessibilityNodeInfo,
            lines: MutableList<String>,
            depth: Int,
        ) {
            if (lines.size >= MAX_NODES) return
            val text = when {
                node.isPassword -> "••••••"
                !node.text.isNullOrBlank() -> node.text.toString()
                !node.contentDescription.isNullOrBlank() -> node.contentDescription.toString()
                !node.hintText.isNullOrBlank() -> "підказка=${node.hintText}"
                else -> ""
            }.replace("\n", " ").take(160)
            val flags = buildList {
                if (node.isClickable) add("click")
                if (node.isEditable) add("edit")
                if (node.isScrollable) add("scroll")
                if (node.isChecked) add("checked")
            }.joinToString(",")
            if (text.isNotBlank() || flags.isNotBlank()) {
                lines += "${"  ".repeat(depth.coerceAtMost(8))}${node.className?.substringAfterLast('.')} text=\"$text\" [$flags]"
            }
            repeat(node.childCount) { index ->
                node.getChild(index)?.let { child -> collectNodes(child, lines, depth + 1) }
            }
        }
    }
}
