package dev.workshopvideo;

import android.content.Context;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.ImageView;

/** Stop animation when a recycled card, tab or activity is no longer visible. */
public final class PreviewImageView extends ImageView {
    public PreviewImageView(Context context){super(context);}
    @Override public void setImageDrawable(Drawable drawable){Drawable old=getDrawable();if(old instanceof Animatable)((Animatable)old).stop();super.setImageDrawable(drawable);update();}
    private void update(){Drawable drawable=getDrawable();if(drawable instanceof Animatable){if(isAttachedToWindow()&&isShown()&&getWindowVisibility()==View.VISIBLE)((Animatable)drawable).start();else ((Animatable)drawable).stop();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();update();}
    @Override protected void onDetachedFromWindow(){Drawable drawable=getDrawable();if(drawable instanceof Animatable)((Animatable)drawable).stop();super.onDetachedFromWindow();}
    @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);update();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);update();}
}
