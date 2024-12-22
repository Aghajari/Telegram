package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

import java.io.File;

public class AttachPickerCameraView extends FrameLayout {

    public CollageLayoutView2 collageLayoutView;
    public DualCameraView cameraView;
    private final ImageView cameraThumb;
    private DualCameraLayoutDelegate delegate;
    private float openProgress;
    public boolean isClosing;
    public float zoom = 0f;

    private int dualW = -1;
    private int dualH = -1;

    private int cameraViewTargetTop = 0;
    private int cameraViewTargetWidth = -1;
    private int cameraViewTargetHeight = -1;
    private float cameraViewTargetProgress = 0f;

    private ValueAnimator animator = null;

    public AttachPickerCameraView(@NonNull Context context, boolean frontFace, boolean lazy) {
        super(context);
        collageLayoutView = new CollageLayoutView2(context, new DarkThemeResourceProvider());
        collageLayoutView.setBackgroundColor(Color.TRANSPARENT);

        cameraThumb = new ImageView(context);
        cameraThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cameraThumb.setImageDrawable(getCameraThumb());
        addView(cameraThumb);
        createNewCamera(context, frontFace, lazy);
        cameraView.setThumbDrawable(getCameraThumb());
        addView(collageLayoutView);
        resetOpenedUI();

        if (Build.VERSION.SDK_INT >= 21) {
            collageLayoutView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    if (!isMaster() || animator == null) {
                        outline.setRect(0, 0, view.getWidth(), view.getHeight());
                    } else {
                        int rad = (int) (dp(12) * cameraViewTargetProgress);
                        outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), rad);
                    }
                }
            });
            collageLayoutView.setClipToOutline(true);
        }
    }

    public void removeCamera(boolean showThumb) {
        cameraView = null;
        zoom = 0f;
        if (showThumb) {
            cameraThumb.setImageDrawable(getCameraThumb());
            cameraThumb.setVisibility(VISIBLE);
        }
    }

    public void createNewCamera(Context context, boolean frontFace, boolean lazy) {
        if (cameraView != null) {
            return;
        }
        zoom = 0f;
        cameraView = new DualCameraView(context, frontFace, lazy) {

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                return false;
            }

            @Override
            public void onEntityDraggedTop(boolean value) {
                if (delegate != null) delegate.onEntityDraggedTop(value);
            }

            @Override
            public void onEntityDraggedBottom(boolean value) {
                if (delegate != null) delegate.onEntityDraggedBottom(value);
            }

            @Override
            public void toggleDual() {
                super.toggleDual();
                if (delegate != null) delegate.onToggleDual(cameraView.isDual());
            }

            @Override
            protected void onSavedDualCameraSuccess() {
                if (delegate != null) {
                    delegate.onSavedDualCameraSuccess(
                            cameraView.isDual(),
                            cameraView.isFrontface()
                    );
                }
            }

            @Override
            protected void receivedAmplitude(double amplitude) {
                if (delegate != null) delegate.receivedAmplitude(amplitude);
            }

            @Override
            protected int getDualMeasuredWidth() {
                if (dualW > 0) return dualW;
                return getTargetWidth();
            }

            @Override
            protected int getDualMeasuredHeight() {
                if (dualH > 0) return dualH;
                return getTargetHeight();
            }

            @Override
            public boolean isSavedDual() {
                return false;
            }

            @Override
            protected void onCameraInitialized() {
                if (delegate != null) delegate.onCameraInitialized();
            }
        };
        collageLayoutView.setCameraView(cameraView);
    }

    public void resetOpenedUI() {
        delegate = null;
        collageLayoutView.updateContainer(null, null, null, null);
        collageLayoutView.setCancelGestures(null);
        collageLayoutView.setResetState(null);

        collageLayoutView.setLayout(null, true);
        collageLayoutView.clear(true);
        if (cameraView != null) {
            cameraView.recordHevc = !collageLayoutView.hasLayout();
        }
    }

    public void setDelegate(DualCameraLayoutDelegate delegate) {
        this.delegate = delegate;
    }

    public Object getCameraSessionObject() {
        return cameraView.getCameraSessionObject();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        cameraView.setEnabled(enabled);
    }

    public void switchMasterToOwner() {
        if (animator != null) {
            animator.cancel();
            animator = null;

            cameraViewTargetHeight = cameraViewTargetWidth = -1;
            cameraViewTargetProgress = 0f;
        }

        if (collageLayoutView.getParent() != null) {
            ((ViewGroup) collageLayoutView.getParent()).removeView(collageLayoutView);
        }

        cameraThumb.setImageDrawable(getCameraThumb());
        cameraThumb.setVisibility(VISIBLE);
        addView(collageLayoutView);
    }

    public void switchMasterTo(ViewGroup target) {
        cameraThumb.setVisibility(GONE);
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (!collageLayoutView.hasLayout()) {
            cameraViewTargetHeight = cameraViewTargetWidth = -1;
            cameraViewTargetProgress = 0f;

            if (collageLayoutView.getParent() != null) {
                ((ViewGroup) collageLayoutView.getParent()).removeView(collageLayoutView);
            }
            target.addView(collageLayoutView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            return;
        }

        int[] loc = new int[2];
        target.getLocationOnScreen(loc);

        cameraViewTargetHeight = target.getMeasuredHeight();
        cameraViewTargetWidth = target.getMeasuredWidth();
        cameraViewTargetTop = loc[1];

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(animation -> {
            cameraViewTargetProgress = (float) animation.getAnimatedValue();
            requestLayout();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                if (collageLayoutView.getParent() != null) {
                    ((ViewGroup) collageLayoutView.getParent()).removeView(collageLayoutView);
                }
                target.addView(collageLayoutView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
                animator = null;
                cameraViewTargetHeight = cameraViewTargetWidth = -1;
                cameraViewTargetProgress = 0f;
            }
        });
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.setDuration(200);
        animator.start();
    }

    public boolean isMaster() {
        return collageLayoutView.getParent() == this;
    }

    protected int getTargetWidth() {
        return getMeasuredWidth();
    }

    protected int getTargetHeight() {
        return getMeasuredHeight();
    }

    public void setOpenProgress(float openProgress) {
        this.openProgress = openProgress;
        invalidate();
    }

    public void updateDualCameraSize(int width, int height) {
        if (dualW == width && dualH == height) {
            return;
        }

        this.dualW = width;
        this.dualH = height;
        if (cameraView != null) {
            cameraView.updateDualCameraSize(width, height);
        }
    }

    public void close() {
        isClosing = true;
        cameraThumb.setVisibility(GONE);
        if (cameraView != null) {
            cameraView.clearThumb();
        }
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void resume() {
        isClosing = false;
        cameraThumb.setVisibility(VISIBLE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int W = MeasureSpec.getSize(widthMeasureSpec);
        final int H = MeasureSpec.getSize(heightMeasureSpec);

        if (cameraThumb != null) {
            cameraThumb.measure(
                    MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY)
            );
        }
        if (collageLayoutView != null) {
            if (cameraViewTargetWidth == -1 || cameraViewTargetHeight == -1 || cameraViewTargetProgress <= 0f) {
                collageLayoutView.measure(
                        MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY)
                );
            } else {
                collageLayoutView.measure(
                        MeasureSpec.makeMeasureSpec((int) (W + (cameraViewTargetWidth - W) * cameraViewTargetProgress), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec((int) (H + (cameraViewTargetHeight - H) * cameraViewTargetProgress), MeasureSpec.EXACTLY)
                );
            }
        }
        setMeasuredDimension(W, H);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int W = right - left;
        final int H = bottom - top;

        if (cameraThumb != null) {
            cameraThumb.layout(0, 0, W, H);
        }
        if (collageLayoutView != null) {
            if (cameraViewTargetWidth == -1 || cameraViewTargetHeight == -1 || cameraViewTargetProgress <= 0f) {
                collageLayoutView.layout(0, 0, W, H);
            } else {
                int targetWidth = (int) (W + (cameraViewTargetWidth - W) * cameraViewTargetProgress);
                int targetHeight = (int) (H + (cameraViewTargetHeight - H) * cameraViewTargetProgress);
                int targetY = (int) (cameraViewTargetTop * cameraViewTargetProgress);
                collageLayoutView.layout(0, targetY, targetWidth, targetY + targetHeight);
            }
        }
    }

    private final Paint topGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient topGradient;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (isClosing) return;
        final float top = 0;
        final float height = dp(144);
        if (topGradient == null) {
            topGradient = new LinearGradient(0, top, 0, top + height, new int[]{0x40000000, 0x00000000}, new float[]{top / (top + height), 1}, Shader.TileMode.CLAMP);
            topGradientPaint.setShader(topGradient);
        }
        topGradientPaint.setAlpha((int) (0xFF * openProgress));
        AndroidUtilities.rectTmp.set(0, 0, getTargetWidth(), height + dp(12) + top);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), topGradientPaint);
    }

    private Drawable getCameraThumb() {
        Bitmap bitmap = null;
        try {
            File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignore) {
        }
        if (bitmap != null) {
            return new BitmapDrawable(bitmap);
        } else {
            return getContext().getResources().getDrawable(R.drawable.icplaceholder);
        }
    }

    public void onDone(File file, StoryEntry entry) {
    }

    public void onDone(MediaController.PhotoEntry entry) {
    }

    public void onCounterClicked() {
    }

    public boolean shouldCounterBeVisible() {
        return false;
    }

    public boolean isPhotoOnly() {
        return false;
    }

    public GalleryListView.GalleryListSelector getGalleryListSelector() {
        return null;
    }

    public CharSequence getTitle() {
        return "";
    }

    public interface DualCameraLayoutDelegate {
        void onEntityDraggedTop(boolean value);

        void onEntityDraggedBottom(boolean value);

        void onToggleDual(boolean isDual);

        void onSavedDualCameraSuccess(boolean isDual, boolean isFrontFace);

        void receivedAmplitude(double amplitude);

        void onCameraInitialized();
    }
}
