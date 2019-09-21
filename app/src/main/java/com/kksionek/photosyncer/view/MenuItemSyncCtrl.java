package com.kksionek.photosyncer.view;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.kksionek.photosyncer.R;

class MenuItemSyncCtrl {
    private final Context mCtx;
    private final MenuItem mMenuItem;
    private Animation mRotationAnimation = null;
    private ImageView mRefreshImage = null;
    private boolean mAnimating = false;

    MenuItemSyncCtrl(@NonNull Context ctx, @NonNull MenuItem menuItem) {
        mCtx = ctx;
        mMenuItem = menuItem;
    }

    void startAnimation() {
        if (mAnimating)
            return;
        mAnimating = true;
        if (mRefreshImage == null)
            mRefreshImage = (ImageView) LayoutInflater.from(mCtx).inflate(
                    R.layout.refresh_action_view,
                    null);
        if (mRotationAnimation == null)
            mRotationAnimation = AnimationUtils.loadAnimation(mCtx, R.anim.anim_rotate);
        if (mMenuItem.getActionView() == null) {
            mRefreshImage.startAnimation(mRotationAnimation);
            mMenuItem.setActionView(mRefreshImage);
            mMenuItem.setEnabled(false);
        }
    }

    void endAnimation() {
        if (!mAnimating)
            return;
        mAnimating = false;
        if (mMenuItem.getActionView() != null) {
            mMenuItem.getActionView().clearAnimation();
            mMenuItem.setActionView(null);
        }
        mMenuItem.setEnabled(true);
    }
}
