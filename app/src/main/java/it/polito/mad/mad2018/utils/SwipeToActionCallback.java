package it.polito.mad.mad2018.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;

public abstract class SwipeToActionCallback extends ItemTouchHelper.SimpleCallback {

    private final Drawable icon, background;
    private final int iconWidth, iconHeight;
    private final Paint clearPaint;

    protected SwipeToActionCallback(@NonNull Context context, @DrawableRes int icon, @ColorRes int background) {
        super(0, ItemTouchHelper.LEFT);

        this.icon = context.getResources().getDrawable(icon);
        assert this.icon != null;

        this.iconWidth = this.icon.getIntrinsicWidth();
        this.iconHeight = this.icon.getIntrinsicHeight();

        this.background = new ColorDrawable(context.getResources().getColor(background));

        this.clearPaint = new Paint();
        this.clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onChildDraw(Canvas canvas, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {

        View itemView = viewHolder.itemView;
        int itemWidth = itemView.getLeft() - itemView.getRight();
        int itemHeight = itemView.getBottom() - itemView.getTop();
        boolean isCanceled = dX == 0f && !isCurrentlyActive;

        if (isCanceled) {
            clearCanvas(canvas, itemView.getRight() + dX, itemView.getTop(),
                    itemView.getRight(), itemView.getBottom());
            super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, true);
            return;
        }

        dX = Math.max(dX, itemWidth);

        // Draw the background
        background.setBounds((int) (itemView.getRight() + dX), itemView.getTop(),
                itemView.getRight(), itemView.getBottom());
        background.draw(canvas);

        // Calculate position of delete icon
        int deleteIconTop = itemView.getTop() + (itemHeight - iconHeight) / 2;
        int deleteIconMargin = (itemHeight - iconHeight) / 2;
        int deleteIconLeft = itemView.getRight() - deleteIconMargin - iconWidth;
        int deleteIconRight = itemView.getRight() - deleteIconMargin;
        int deleteIconBottom = deleteIconTop + iconHeight;

        // Draw the delete icon
        icon.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom);
        icon.draw(canvas);

        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    private void clearCanvas(Canvas canvas, float left, float top, float right, float bottom) {
        canvas.drawRect(left, top, right, bottom, clearPaint);
    }
}
