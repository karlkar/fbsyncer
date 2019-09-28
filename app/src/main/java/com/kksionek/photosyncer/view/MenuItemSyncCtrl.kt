package com.kksionek.photosyncer.view

import android.content.Context
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView

import com.kksionek.photosyncer.R

internal class MenuItemSyncCtrl(private val context: Context, private val menuItem: MenuItem) {

    private lateinit var rotationAnimation: Animation
    private lateinit var refreshImage: ImageView
    private var animating = false

    fun startAnimation() {
        if (animating) return
        animating = true
        if (!::refreshImage.isInitialized) {
            refreshImage = LayoutInflater.from(context).inflate(
                R.layout.refresh_action_view,
                null
            ) as ImageView
        }
        if (!::rotationAnimation.isInitialized) {
            rotationAnimation = AnimationUtils.loadAnimation(context, R.anim.anim_rotate)
        }
        if (menuItem.actionView == null) {
            refreshImage.startAnimation(rotationAnimation)
            menuItem.actionView = refreshImage
            menuItem.isEnabled = false
        }
    }

    fun endAnimation() {
        if (!animating) return
        animating = false
        if (menuItem.actionView != null) {
            menuItem.actionView.clearAnimation()
            menuItem.actionView = null
        }
        menuItem.isEnabled = true
    }
}
