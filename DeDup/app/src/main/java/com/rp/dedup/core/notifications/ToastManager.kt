package com.rp.dedup.core.notifications

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.graphics.toColorInt

/**
 * Enhanced manager for showing Toast messages, including custom styled toasts.
 * Note: On Android 11 (API 30) and above, custom toast views are only shown 
 * while the app is in the foreground.
 */
class ToastManager(private val context: Context) {

    /**
     * Shows a standard short duration toast.
     */
    fun showShort(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Shows a standard long duration toast.
     */
    fun showLong(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Shows a standard short duration toast using a string resource.
     */
    fun showShort(@StringRes resId: Int) {
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
    }

    /**
     * Shows a standard long duration toast using a string resource.
     */
    fun showLong(@StringRes resId: Int) {
        Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
    }

    /**
     * Shows a customized toast with positioning, background, and an optional icon.
     *
     * @param message The text to display
     * @param iconRes Optional drawable resource for the icon
     * @param backgroundColor Hex string color (e.g., "#323232")
     * @param textColor Hex string color (e.g., "#FFFFFF")
     * @param cornerRadius Corner radius for the background in pixels
     * @param gravity Positioning of the toast (e.g., Gravity.TOP, Gravity.CENTER)
     * @param yOffset Vertical offset from the gravity position
     * @param toastDuration Toast.LENGTH_SHORT or Toast.LENGTH_LONG
     */
    @Suppress("DEPRECATION")
    fun showCustom(
        message: String,
        @DrawableRes iconRes: Int? = null,
        backgroundColor: String = "#323232",
        textColor: String = "#FFFFFF",
        cornerRadius: Float = 24f,
        gravity: Int = Gravity.BOTTOM,
        yOffset: Int = 100,
        toastDuration: Int = Toast.LENGTH_SHORT
    ) {
        val toast = Toast(context)
        
        // Create the root container programmatically
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 20, 40, 20)
            this.gravity = Gravity.CENTER_VERTICAL
            
            // Set background with corner radius
            background = GradientDrawable().apply {
                setColor(backgroundColor.toColorInt())
                setCornerRadius(cornerRadius)
            }
        }

        // Add optional icon
        if (iconRes != null) {
            val iconView = ImageView(context).apply {
                setImageResource(iconRes)
                val size = 48 
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = 20
                }
                setColorFilter(textColor.toColorInt()) 
            }
            layout.addView(iconView)
        }

        // Add text message
        val textView = TextView(context).apply {
            text = message
            setTextColor(textColor.toColorInt())
            textSize = 14f
        }
        layout.addView(textView)

        toast.duration = toastDuration
        toast.view = layout
        toast.setGravity(gravity, 0, yOffset)
        toast.show()
    }
}
