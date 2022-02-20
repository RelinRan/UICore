/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.core.refrsh;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.util.Preconditions;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 以 Material design 风格呈现动画的不确定进度指示器的 Drawable不依赖于 API 级别 11。
 * <p>虽然这可用于使用 {@link #start()} 和  {@link #stop()}  方法，这也可用于使用  {@link #setStartEndTrim(float, float)} 方法。
 * SwipeRefreshDrawable 还支持添加箭头通过 {@link #setArrowEnabled(boolean)} 和 {@link #setArrowDimensions(float, float)} 方法。
 * <p>要使用预定义的尺寸之一而不是使用您自己的尺寸，{@link #setStyle(int)} 应该使用 {@link #DEFAULT} 或 {@link #LARGE} 样式之一作为其参数调用。正在做因此将更新箭头尺寸、环尺寸和笔划宽度以适合指定的尺寸。
 * <p>如果没有通过 {@link #setCenterRadius(float)} 或 {@link #setStyle(int)} 设置中心半径方法，CircularProgressDrawable 将填充通过 {@link #setBounds(Rect)} 设置的边界。
 */
public class SwipeRefreshDrawable extends Drawable implements Animatable {
    private static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
    private static final Interpolator MATERIAL_INTERPOLATOR = new FastOutSlowInInterpolator();

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({LARGE, DEFAULT})
    public @interface ProgressDrawableSize {
    }

    /**
     * 映射到 ProgressBar.Large 样式。
     */
    public static final int LARGE = 0;

    private static final float CENTER_RADIUS_LARGE = 11f;
    private static final float STROKE_WIDTH_LARGE = 3f;
    private static final int ARROW_WIDTH_LARGE = 12;
    private static final int ARROW_HEIGHT_LARGE = 6;

    /**
     * 映射到 ProgressBar 默认样式。
     */
    public static final int DEFAULT = 1;

    private static final float CENTER_RADIUS = 7.5f;
    private static final float STROKE_WIDTH = 2.5f;
    private static final int ARROW_WIDTH = 8;
    private static final int ARROW_HEIGHT = 4;

    /**
     * 这是微调器中使用的默认颜色集。 {@关联 #setColorSchemeColors(int...)} 允许修改颜色。
     */
    private static final int[] COLORS = new int[]{
            Color.CYAN
    };

    /**
     * 线性插值器中用于动画绘制的值颜色过渡应该开始
     */
    private static final float COLOR_CHANGE_OFFSET = 0.75f;
    private static final float SHRINK_OFFSET = 0.5f;

    /**
     * 单个进度旋转的持续时间（以毫秒为单位）。
     */
    private static final int ANIMATION_DURATION = 1332;

    /**
     * 在动画持续时间内完成的完全旋转（以度为单位）
     */
    private static final float GROUP_FULL_ROTATION = 1080f / 5f;

    /**
     * 指示环，用于管理动画状态。
     */
    private final Ring mRing;

    /**
     * 画布旋转度数。
     */
    private float mRotation;

    /**
     * 动画期间进度弧的最大长度。
     */
    private static final float MAX_PROGRESS_ARC = .8f;
    /**
     * 动画期间进度弧的最小长度。
     */
    private static final float MIN_PROGRESS_ARC = .01f;

    /**
     * 在动画期间应用到环的旋转，以将其完成一个完整的圆圈。
     */
    private static final float RING_ROTATION = 1f - (MAX_PROGRESS_ARC - MIN_PROGRESS_ARC);

    private Resources mResources;
    private Animator mAnimator;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            float mRotationCount;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            boolean mFinishing;

    /**
     * @param context 应用上下文
     */
    @SuppressLint("RestrictedApi")
    public SwipeRefreshDrawable(@NonNull Context context) {
        mResources = Preconditions.checkNotNull(context).getResources();

        mRing = new Ring();
        mRing.setColors(COLORS);

        setStrokeWidth(STROKE_WIDTH);
        setupAnimators();
    }

    /**
     * 在 dp 中一次设置所有参数。
     */
    private void setSizeParameters(float centerRadius, float strokeWidth, float arrowWidth,
                                   float arrowHeight) {
        final Ring ring = mRing;
        final DisplayMetrics metrics = mResources.getDisplayMetrics();
        final float screenDensity = metrics.density;

        ring.setStrokeWidth(strokeWidth * screenDensity);
        ring.setCenterRadius(centerRadius * screenDensity);
        ring.setColorIndex(0);
        ring.setArrowDimensions(arrowWidth * screenDensity, arrowHeight * screenDensity);
    }

    /**
     * 设置进度微调器的整体大小。 这会更新半径和环的行程宽度，以及箭头尺寸。
     *
     * @param size one of {@link #LARGE} or {@link #DEFAULT}
     */
    public void setStyle(@ProgressDrawableSize int size) {
        if (size == LARGE) {
            setSizeParameters(CENTER_RADIUS_LARGE, STROKE_WIDTH_LARGE, ARROW_WIDTH_LARGE,
                    ARROW_HEIGHT_LARGE);
        } else {
            setSizeParameters(CENTER_RADIUS, STROKE_WIDTH, ARROW_WIDTH, ARROW_HEIGHT);
        }
        invalidateSelf();
    }

    /**
     * 返回进度微调器的笔画宽度（以像素为单位）。
     *
     * @return 笔画宽度（以像素为单位）
     */
    public float getStrokeWidth() {
        return mRing.getStrokeWidth();
    }

    /**
     * 设置进度微调器的笔触宽度（以像素为单位）。
     *
     * @param strokeWidth 笔画宽度（以像素为单位）
     */
    public void setStrokeWidth(float strokeWidth) {
        mRing.setStrokeWidth(strokeWidth);
        invalidateSelf();
    }

    /**
     * Returns the center radius for the progress spinner in pixels.
     *
     * @return center radius in pixels
     */
    public float getCenterRadius() {
        return mRing.getCenterRadius();
    }

    /**
     * Sets the center radius for the progress spinner in pixels. If set to 0, this drawable will
     * fill the bounds when drawn.
     *
     * @param centerRadius center radius in pixels
     */
    public void setCenterRadius(float centerRadius) {
        mRing.setCenterRadius(centerRadius);
        invalidateSelf();
    }

    /**
     * Sets the stroke cap of the progress spinner. Default stroke cap is {@link Paint.Cap#SQUARE}.
     *
     * @param strokeCap stroke cap
     */
    public void setStrokeCap(@NonNull Paint.Cap strokeCap) {
        mRing.setStrokeCap(strokeCap);
        invalidateSelf();
    }

    /**
     * Returns the stroke cap of the progress spinner.
     *
     * @return stroke cap
     */
    @NonNull
    public Paint.Cap getStrokeCap() {
        return mRing.getStrokeCap();
    }

    /**
     * Returns the arrow width in pixels.
     *
     * @return arrow width in pixels
     */
    public float getArrowWidth() {
        return mRing.getArrowWidth();
    }

    /**
     * Returns the arrow height in pixels.
     *
     * @return arrow height in pixels
     */
    public float getArrowHeight() {
        return mRing.getArrowHeight();
    }

    /**
     * Sets the dimensions of the arrow at the end of the spinner in pixels.
     *
     * @param width  width of the baseline of the arrow in pixels
     * @param height distance from tip of the arrow to its baseline in pixels
     */
    public void setArrowDimensions(float width, float height) {
        mRing.setArrowDimensions(width, height);
        invalidateSelf();
    }

    /**
     * Returns {@code true} if the arrow at the end of the spinner is shown.
     *
     * @return {@code true} if the arrow is shown, {@code false} otherwise.
     */
    public boolean getArrowEnabled() {
        return mRing.getShowArrow();
    }

    /**
     * Sets if the arrow at the end of the spinner should be shown.
     *
     * @param show {@code true} if the arrow should be drawn, {@code false} otherwise
     */
    public void setArrowEnabled(boolean show) {
        mRing.setShowArrow(show);
        invalidateSelf();
    }

    /**
     * Returns the scale of the arrow at the end of the spinner.
     *
     * @return scale of the arrow
     */
    public float getArrowScale() {
        return mRing.getArrowScale();
    }

    /**
     * Sets the scale of the arrow at the end of the spinner.
     *
     * @param scale scaling that will be applied to the arrow's both width and height when drawing.
     */
    public void setArrowScale(float scale) {
        mRing.setArrowScale(scale);
        invalidateSelf();
    }

    /**
     * Returns the start trim for the progress spinner arc
     *
     * @return start trim from [0..1]
     */
    public float getStartTrim() {
        return mRing.getStartTrim();
    }

    /**
     * Returns the end trim for the progress spinner arc
     *
     * @return end trim from [0..1]
     */
    public float getEndTrim() {
        return mRing.getEndTrim();
    }

    /**
     * Sets the start and end trim for the progress spinner arc. 0 corresponds to the geometric
     * angle of 0 degrees (3 o'clock on a watch) and it increases clockwise, coming to a full circle
     * at 1.
     *
     * @param start starting position of the arc from [0..1]
     * @param end   ending position of the arc from [0..1]
     */
    public void setStartEndTrim(float start, float end) {
        mRing.setStartTrim(start);
        mRing.setEndTrim(end);
        invalidateSelf();
    }

    /**
     * Returns the amount of rotation applied to the progress spinner.
     *
     * @return amount of rotation from [0..1]
     */
    public float getProgressRotation() {
        return mRing.getRotation();
    }

    /**
     * Sets the amount of rotation to apply to the progress spinner.
     *
     * @param rotation rotation from [0..1]
     */
    public void setProgressRotation(float rotation) {
        mRing.setRotation(rotation);
        invalidateSelf();
    }

    /**
     * Returns the background color of the circle drawn inside the drawable.
     *
     * @return an ARGB color
     */
    public int getBackgroundColor() {
        return mRing.getBackgroundColor();
    }

    /**
     * Sets the background color of the circle inside the drawable. Calling {@link
     * #setAlpha(int)} does not affect the visibility background color, so it should be set
     * separately if it needs to be hidden or visible.
     *
     * @param color an ARGB color
     */
    public void setBackgroundColor(int color) {
        mRing.setBackgroundColor(color);
        invalidateSelf();
    }

    /**
     * Returns the colors used in the progress animation
     *
     * @return list of ARGB colors
     */
    @NonNull
    public int[] getColorSchemeColors() {
        return mRing.getColors();
    }

    /**
     * Sets the colors used in the progress animation from a color list. The first color will also
     * be the color to be used if animation is not started yet.
     *
     * @param colors list of ARGB colors to be used in the spinner
     */
    public void setColorSchemeColors(@NonNull int... colors) {
        mRing.setColors(colors);
        mRing.setColorIndex(0);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        canvas.save();
        canvas.rotate(mRotation, bounds.exactCenterX(), bounds.exactCenterY());
        mRing.draw(canvas, bounds);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        mRing.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return mRing.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mRing.setColorFilter(colorFilter);
        invalidateSelf();
    }

    private void setRotation(float rotation) {
        mRotation = rotation;
    }

    @SuppressWarnings("UnusedMethod") // TODO(b/141954576): Suppressed during upgrade to AGP 3.6.
    private float getRotation() {
        return mRotation;
    }

    @Override
    @SuppressWarnings("deprecation")
    // Remove suppression was b/120985527 is addressed.
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public boolean isRunning() {
        return mAnimator.isRunning();
    }

    /**
     * Starts the animation for the spinner.
     */
    @Override
    public void start() {
        mAnimator.cancel();
        mRing.storeOriginals();
        // Already showing some part of the ring
        if (mRing.getEndTrim() != mRing.getStartTrim()) {
            mFinishing = true;
            mAnimator.setDuration(ANIMATION_DURATION / 2);
            mAnimator.start();
        } else {
            mRing.setColorIndex(0);
            mRing.resetOriginals();
            mAnimator.setDuration(ANIMATION_DURATION);
            mAnimator.start();
        }
    }

    /**
     * Stops the animation for the spinner.
     */
    @Override
    public void stop() {
        mAnimator.cancel();
        setRotation(0);
        mRing.setShowArrow(false);
        mRing.setColorIndex(0);
        mRing.resetOriginals();
        invalidateSelf();
    }

    // Adapted from ArgbEvaluator.java
    private int evaluateColorChange(float fraction, int startValue, int endValue) {
        int startA = (startValue >> 24) & 0xff;
        int startR = (startValue >> 16) & 0xff;
        int startG = (startValue >> 8) & 0xff;
        int startB = startValue & 0xff;

        int endA = (endValue >> 24) & 0xff;
        int endR = (endValue >> 16) & 0xff;
        int endG = (endValue >> 8) & 0xff;
        int endB = endValue & 0xff;

        return (startA + (int) (fraction * (endA - startA))) << 24
                | (startR + (int) (fraction * (endR - startR))) << 16
                | (startG + (int) (fraction * (endG - startG))) << 8
                | (startB + (int) (fraction * (endB - startB)));
    }

    /**
     * Update the ring color if this is within the last 25% of the animation.
     * The new ring color will be a translation from the starting ring color to
     * the next color.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void updateRingColor(float interpolatedTime, Ring ring) {
        if (interpolatedTime > COLOR_CHANGE_OFFSET) {
            ring.setColor(evaluateColorChange((interpolatedTime - COLOR_CHANGE_OFFSET)
                            / (1f - COLOR_CHANGE_OFFSET), ring.getStartingColor(),
                    ring.getNextColor()));
        } else {
            ring.setColor(ring.getStartingColor());
        }
    }

    /**
     * Update the ring start and end trim if the animation is finishing (i.e. it started with
     * already visible progress, so needs to shrink back down before starting the spinner).
     */
    private void applyFinishTranslation(float interpolatedTime, Ring ring) {
        // shrink back down and complete a full rotation before
        // starting other circles
        // Rotation goes between [0..1].
        updateRingColor(interpolatedTime, ring);
        float targetRotation = (float) (Math.floor(ring.getStartingRotation() / MAX_PROGRESS_ARC)
                + 1f);
        final float startTrim = ring.getStartingStartTrim()
                + (ring.getStartingEndTrim() - MIN_PROGRESS_ARC - ring.getStartingStartTrim())
                * interpolatedTime;
        ring.setStartTrim(startTrim);
        ring.setEndTrim(ring.getStartingEndTrim());
        final float rotation = ring.getStartingRotation()
                + ((targetRotation - ring.getStartingRotation()) * interpolatedTime);
        ring.setRotation(rotation);
    }

    /**
     * Update the ring start and end trim according to current time of the animation.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void applyTransformation(float interpolatedTime, Ring ring, boolean lastFrame) {
        if (mFinishing) {
            applyFinishTranslation(interpolatedTime, ring);
            // Below condition is to work around a ValueAnimator issue where onAnimationRepeat is
            // called before last frame (1f).
        } else if (interpolatedTime != 1f || lastFrame) {
            final float startingRotation = ring.getStartingRotation();
            float startTrim, endTrim;

            if (interpolatedTime < SHRINK_OFFSET) { // Expansion occurs on first half of animation
                final float scaledTime = interpolatedTime / SHRINK_OFFSET;
                startTrim = ring.getStartingStartTrim();
                endTrim = startTrim + ((MAX_PROGRESS_ARC - MIN_PROGRESS_ARC)
                        * MATERIAL_INTERPOLATOR.getInterpolation(scaledTime) + MIN_PROGRESS_ARC);
            } else { // Shrinking occurs on second half of animation
                float scaledTime = (interpolatedTime - SHRINK_OFFSET) / (1f - SHRINK_OFFSET);
                endTrim = ring.getStartingStartTrim() + (MAX_PROGRESS_ARC - MIN_PROGRESS_ARC);
                startTrim = endTrim - ((MAX_PROGRESS_ARC - MIN_PROGRESS_ARC)
                        * (1f - MATERIAL_INTERPOLATOR.getInterpolation(scaledTime))
                        + MIN_PROGRESS_ARC);
            }

            final float rotation = startingRotation + (RING_ROTATION * interpolatedTime);
            float groupRotation = GROUP_FULL_ROTATION * (interpolatedTime + mRotationCount);

            ring.setStartTrim(startTrim);
            ring.setEndTrim(endTrim);
            ring.setRotation(rotation);
            setRotation(groupRotation);
        }
    }

    private void setupAnimators() {
        final Ring ring = mRing;
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float interpolatedTime = (float) animation.getAnimatedValue();
                updateRingColor(interpolatedTime, ring);
                applyTransformation(interpolatedTime, ring, false);
                invalidateSelf();
            }
        });
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(LINEAR_INTERPOLATOR);
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animator) {
                mRotationCount = 0;
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                // do nothing
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // do nothing
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
                applyTransformation(1f, ring, true);
                ring.storeOriginals();
                ring.goToNextColor();
                if (mFinishing) {
                    // finished closing the last ring from the swipe gesture; go
                    // into progress mode
                    mFinishing = false;
                    animator.cancel();
                    animator.setDuration(ANIMATION_DURATION);
                    animator.start();
                    ring.setShowArrow(false);
                } else {
                    mRotationCount = mRotationCount + 1;
                }
            }
        });
        mAnimator = animator;
    }

    /**
     * A private class to do all the drawing of CircularProgressDrawable, which includes background,
     * progress spinner and the arrow. This class is to separate drawing from animation.
     */
    private static class Ring {
        final RectF mTempBounds = new RectF();
        final Paint mPaint = new Paint();
        final Paint mArrowPaint = new Paint();
        final Paint mCirclePaint = new Paint();

        float mStartTrim = 0f;
        float mEndTrim = 0f;
        float mRotation = 0f;
        float mStrokeWidth = 5f;

        int[] mColors;
        // mColorIndex represents the offset into the available mColors that the
        // progress circle should currently display. As the progress circle is
        // animating, the mColorIndex moves by one to the next available color.
        int mColorIndex;
        float mStartingStartTrim;
        float mStartingEndTrim;
        float mStartingRotation;
        boolean mShowArrow;
        Path mArrow;
        float mArrowScale = 1;
        float mRingCenterRadius;
        int mArrowWidth;
        int mArrowHeight;
        int mAlpha = 255;
        int mCurrentColor;

        Ring() {
            mPaint.setStrokeCap(Paint.Cap.SQUARE);
            mPaint.setAntiAlias(true);
            mPaint.setStyle(Style.STROKE);

            mArrowPaint.setStyle(Paint.Style.FILL);
            mArrowPaint.setAntiAlias(true);

            mCirclePaint.setColor(Color.TRANSPARENT);
        }

        /**
         * Sets the dimensions of the arrowhead.
         *
         * @param width  width of the hypotenuse of the arrow head
         * @param height height of the arrow point
         */
        void setArrowDimensions(float width, float height) {
            mArrowWidth = (int) width;
            mArrowHeight = (int) height;
        }

        void setStrokeCap(Paint.Cap strokeCap) {
            mPaint.setStrokeCap(strokeCap);
        }

        Paint.Cap getStrokeCap() {
            return mPaint.getStrokeCap();
        }

        float getArrowWidth() {
            return mArrowWidth;
        }

        float getArrowHeight() {
            return mArrowHeight;
        }

        /**
         * Draw the progress spinner
         */
        void draw(Canvas c, Rect bounds) {
            final RectF arcBounds = mTempBounds;
            float arcRadius = mRingCenterRadius + mStrokeWidth / 2f;
            if (mRingCenterRadius <= 0) {
                // If center radius is not set, fill the bounds
                arcRadius = Math.min(bounds.width(), bounds.height()) / 2f - Math.max(
                        (mArrowWidth * mArrowScale) / 2f, mStrokeWidth / 2f);
            }
            arcBounds.set(bounds.centerX() - arcRadius,
                    bounds.centerY() - arcRadius,
                    bounds.centerX() + arcRadius,
                    bounds.centerY() + arcRadius);

            final float startAngle = (mStartTrim + mRotation) * 360;
            final float endAngle = (mEndTrim + mRotation) * 360;
            float sweepAngle = endAngle - startAngle;

            mPaint.setColor(mCurrentColor);
            mPaint.setAlpha(mAlpha);

            // Draw the background first
            float inset = mStrokeWidth / 2f; // Calculate inset to draw inside the arc
            arcBounds.inset(inset, inset); // Apply inset
            c.drawCircle(arcBounds.centerX(), arcBounds.centerY(), arcBounds.width() / 2f,
                    mCirclePaint);
            arcBounds.inset(-inset, -inset); // Revert the inset

            c.drawArc(arcBounds, startAngle, sweepAngle, false, mPaint);

            drawTriangle(c, startAngle, sweepAngle, arcBounds);
        }

        void drawTriangle(Canvas c, float startAngle, float sweepAngle, RectF bounds) {
            if (mShowArrow) {
                if (mArrow == null) {
                    mArrow = new android.graphics.Path();
                    mArrow.setFillType(android.graphics.Path.FillType.EVEN_ODD);
                } else {
                    mArrow.reset();
                }
                float centerRadius = Math.min(bounds.width(), bounds.height()) / 2f;
                float inset = mArrowWidth * mArrowScale / 2f;
                // Update the path each time. This works around an issue in SKIA
                // where concatenating a rotation matrix to a scale matrix
                // ignored a starting negative rotation. This appears to have
                // been fixed as of API 21.
                mArrow.moveTo(0, 0);
                mArrow.lineTo(mArrowWidth * mArrowScale, 0);
                mArrow.lineTo((mArrowWidth * mArrowScale / 2), (mArrowHeight
                        * mArrowScale));
                mArrow.offset(centerRadius + bounds.centerX() - inset,
                        bounds.centerY() + mStrokeWidth / 2f);
                mArrow.close();
                // draw a triangle
                mArrowPaint.setColor(mCurrentColor);
                mArrowPaint.setAlpha(mAlpha);
                c.save();
                c.rotate(startAngle + sweepAngle, bounds.centerX(),
                        bounds.centerY());
                c.drawPath(mArrow, mArrowPaint);
                c.restore();
            }
        }

        /**
         * Sets the colors the progress spinner alternates between.
         *
         * @param colors array of ARGB colors. Must be non-{@code null}.
         */
        void setColors(@NonNull int[] colors) {
            mColors = colors;
            // if colors are reset, make sure to reset the color index as well
            setColorIndex(0);
        }

        int[] getColors() {
            return mColors;
        }

        /**
         * Sets the absolute color of the progress spinner. This is should only
         * be used when animating between current and next color when the
         * spinner is rotating.
         *
         * @param color an ARGB color
         */
        void setColor(int color) {
            mCurrentColor = color;
        }

        /**
         * Sets the background color of the circle inside the spinner.
         */
        void setBackgroundColor(int color) {
            mCirclePaint.setColor(color);
        }

        int getBackgroundColor() {
            return mCirclePaint.getColor();
        }

        /**
         * @param index index into the color array of the color to display in
         *              the progress spinner.
         */
        void setColorIndex(int index) {
            mColorIndex = index;
            mCurrentColor = mColors[mColorIndex];
        }

        /**
         * @return int describing the next color the progress spinner should use when drawing.
         */
        int getNextColor() {
            return mColors[getNextColorIndex()];
        }

        int getNextColorIndex() {
            return (mColorIndex + 1) % mColors.length;
        }

        /**
         * Proceed to the next available ring color. This will automatically
         * wrap back to the beginning of colors.
         */
        void goToNextColor() {
            setColorIndex(getNextColorIndex());
        }

        void setColorFilter(ColorFilter filter) {
            mPaint.setColorFilter(filter);
        }

        /**
         * @param alpha alpha of the progress spinner and associated arrowhead.
         */
        void setAlpha(int alpha) {
            mAlpha = alpha;
        }

        /**
         * @return current alpha of the progress spinner and arrowhead
         */
        int getAlpha() {
            return mAlpha;
        }

        /**
         * @param strokeWidth set the stroke width of the progress spinner in pixels.
         */
        void setStrokeWidth(float strokeWidth) {
            mStrokeWidth = strokeWidth;
            mPaint.setStrokeWidth(strokeWidth);
        }

        float getStrokeWidth() {
            return mStrokeWidth;
        }

        void setStartTrim(float startTrim) {
            mStartTrim = startTrim;
        }

        float getStartTrim() {
            return mStartTrim;
        }

        float getStartingStartTrim() {
            return mStartingStartTrim;
        }

        float getStartingEndTrim() {
            return mStartingEndTrim;
        }

        int getStartingColor() {
            return mColors[mColorIndex];
        }

        void setEndTrim(float endTrim) {
            mEndTrim = endTrim;
        }

        float getEndTrim() {
            return mEndTrim;
        }

        void setRotation(float rotation) {
            mRotation = rotation;
        }

        float getRotation() {
            return mRotation;
        }

        /**
         * @param centerRadius inner radius in px of the circle the progress spinner arc traces
         */
        void setCenterRadius(float centerRadius) {
            mRingCenterRadius = centerRadius;
        }

        float getCenterRadius() {
            return mRingCenterRadius;
        }

        /**
         * @param show {@code true} if should show the arrow head on the progress spinner
         */
        void setShowArrow(boolean show) {
            if (mShowArrow != show) {
                mShowArrow = show;
            }
        }

        boolean getShowArrow() {
            return mShowArrow;
        }

        /**
         * @param scale scale of the arrowhead for the spinner
         */
        void setArrowScale(float scale) {
            if (scale != mArrowScale) {
                mArrowScale = scale;
            }
        }

        float getArrowScale() {
            return mArrowScale;
        }

        /**
         * @return The amount the progress spinner is currently rotated, between [0..1].
         */
        float getStartingRotation() {
            return mStartingRotation;
        }

        /**
         * If the start / end trim are offset to begin with, store them so that animation starts
         * from that offset.
         */
        void storeOriginals() {
            mStartingStartTrim = mStartTrim;
            mStartingEndTrim = mEndTrim;
            mStartingRotation = mRotation;
        }

        /**
         * Reset the progress spinner to default rotation, start and end angles.
         */
        void resetOriginals() {
            mStartingStartTrim = 0;
            mStartingEndTrim = 0;
            mStartingRotation = 0;
            setStartTrim(0);
            setEndTrim(0);
            setRotation(0);
        }
    }
}
