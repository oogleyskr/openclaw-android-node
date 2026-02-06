package com.openclaw.node.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.openclaw.node.models.UINode
import com.openclaw.node.models.Bounds
import kotlinx.coroutines.CompletableDeferred

class OpenClawAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: OpenClawAccessibilityService? = null
        
        fun getInstance(): OpenClawAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
    }
    
    override fun onInterrupt() {
        // Required override
    }
    
    // UI Tree Functions
    fun getUITree(): List<UINode> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        return listOf(convertAccessibilityNodeToUINode(rootNode))
    }
    
    private fun convertAccessibilityNodeToUINode(node: AccessibilityNodeInfo): UINode {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        val children = mutableListOf<UINode>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                children.add(convertAccessibilityNodeToUINode(child))
                child.recycle()
            }
        }
        
        val uiNode = UINode(
            id = node.viewIdResourceName,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            bounds = Bounds(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            ),
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            editable = node.isEditable,
            checkable = node.isCheckable,
            checked = node.isChecked,
            enabled = node.isEnabled,
            focused = node.isFocused,
            selected = node.isSelected,
            children = children
        )
        
        return uiNode
    }
    
    // Touch/Gesture Functions
    suspend fun performTap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        val result = CompletableDeferred<Boolean>()
        
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                result.complete(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                result.complete(false)
            }
        }, null)
        
        return result.await()
    }
    
    suspend fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        val result = CompletableDeferred<Boolean>()
        
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                result.complete(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                result.complete(false)
            }
        }, null)
        
        return result.await()
    }
    
    // Text Input Functions
    fun performType(text: String): Boolean {
        val focusedNode = findFocusedEditableNode()
        if (focusedNode != null) {
            val arguments = android.os.Bundle().apply {
                putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusedNode.recycle()
            return result
        }
        return false
    }
    
    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findFocusedEditableNodeRecursive(rootNode)
    }
    
    private fun findFocusedEditableNodeRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findFocusedEditableNodeRecursive(child)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
        }
        
        return null
    }
    
    // System Key Functions
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
    
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    fun performRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
}