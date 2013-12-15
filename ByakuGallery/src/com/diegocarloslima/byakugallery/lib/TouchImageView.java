package com.diegocarloslima.byakugallery.lib;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;
import android.widget.ImageView;

public class TouchImageView extends ImageView {

	private static final int DOUBLE_TAP_ANIMATION_DURATION = 300;
	private static final int SCALE_END_ANIMATION_DURATION = 200;

	private Drawable mDrawable;
	private int mDrawableIntrinsicWidth;
	private int mDrawableIntrinsicHeight;

	private final TouchGestureDetector mTouchGestureDetector;
	private final Matrix mMatrix = new Matrix();
	private final float[] mMatrixValues = new float[9];
	
	private float mScale;
	private float mTranslationX;
	private float mTranslationY;
	private Float mLastFocusX;
	private Float mLastFocusY;
	private final FlingScroller mFlingScroller = new FlingScroller();
	private boolean mRepositioningAnimation;

	public TouchImageView(Context context) {
		this(context, null);
	}

	public TouchImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TouchImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		final TouchGestureDetector.OnTouchGestureListener listener = new TouchGestureDetector.OnTouchGestureListener() {

			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				return performClick();
			}

			@Override
			public void onLongPress(MotionEvent e) {
				performLongClick();
			}

			@Override
			public boolean onDoubleTap(MotionEvent e) {
				loadMatrixValues();

				final float minScale = getMinScale();
				final float targetScale = mScale > minScale ? minScale : 1;

				final float desiredTranslationX = e.getX() - (e.getX() - mTranslationX) * (targetScale / mScale);
				final float desiredTranslationY = e.getY() - (e.getY() - mTranslationY) * (targetScale / mScale);

				final float targetTranslationX = desiredTranslationX + computeTranslation(getMeasuredWidth(), mDrawableIntrinsicWidth * targetScale, desiredTranslationX, 0);
				final float targetTranslationY = desiredTranslationY + computeTranslation(getMeasuredHeight(), mDrawableIntrinsicHeight * targetScale, desiredTranslationY, 0);

				clearAnimation();
				final Animation animation = new TouchAnimation(targetScale, targetTranslationX, targetTranslationY);
				animation.setDuration(DOUBLE_TAP_ANIMATION_DURATION);
				startAnimation(animation);

				return true;
			}

			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
				if(mRepositioningAnimation) {
					return false;
				}
				loadMatrixValues();

				final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
				final float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

				final float dx = computeTranslation(getMeasuredWidth(), currentDrawableWidth, mTranslationX, -distanceX);
				final float dy = computeTranslation(getMeasuredHeight(), currentDrawableHeight, mTranslationY, -distanceY);

				if(Math.abs(dx) < 1 && Math.abs(dy) < 1) {
					return false;
				}
				mMatrix.postTranslate(dx, dy);

				clearAnimation();
				invalidate();

				return true;
			}

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				if(mRepositioningAnimation) {
					return false;
				}
				loadMatrixValues();

				final float horizontalFreeSpace = (getMeasuredWidth() - mDrawableIntrinsicWidth * mScale) / 2;
				final float minX = horizontalFreeSpace > 0 ? horizontalFreeSpace : getMeasuredWidth() - mDrawableIntrinsicWidth * mScale;
				final float maxX = horizontalFreeSpace > 0 ? horizontalFreeSpace : 0;

				final float verticalFreeSpace = (getMeasuredHeight() - mDrawableIntrinsicHeight * mScale) / 2;
				final float minY = verticalFreeSpace > 0 ? verticalFreeSpace : getMeasuredHeight() - mDrawableIntrinsicHeight * mScale;
				final float maxY = verticalFreeSpace > 0 ? verticalFreeSpace : 0;

				mFlingScroller.fling(Math.round(mTranslationX), Math.round(mTranslationY), Math.round(velocityX), Math.round(velocityY), Math.round(minX), Math.round(maxX), Math.round(minY), Math.round(maxY));

				final float dx = mFlingScroller.getFinalX() - mTranslationX;
				final float dy = mFlingScroller.getFinalY() - mTranslationY;

				if(Math.abs(dx) < 1 && Math.abs(dy) < 1) {
					return false;
				}

				clearAnimation();
				final Animation animation = new FlingAnimation();
				animation.setDuration(mFlingScroller.getDuration());
				animation.setInterpolator(new LinearInterpolator());
				startAnimation(animation);

				return true;
			}

			@Override
			public boolean onScaleBegin(ScaleGestureDetector detector) {
				mLastFocusX = null;
				mLastFocusY = null;

				return true;
			}

			@Override
			public boolean onScale(ScaleGestureDetector detector) {

				final float scaleFactor = detector.getScaleFactor();
				float focusX = detector.getFocusX();
				float focusY = detector.getFocusY();

				loadMatrixValues();

				final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
				final float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

				if(mTranslationX > 0 && focusX < mTranslationX) {
					focusX = mTranslationX;
				} else if(currentDrawableWidth + mTranslationX < getMeasuredWidth() && focusX > currentDrawableWidth + mTranslationX) {
					focusX = currentDrawableWidth + mTranslationX;
				}

				if(mTranslationY > 0 && focusY < mTranslationY) {
					focusY = mTranslationY;
				} else if(currentDrawableHeight + mTranslationY < getMeasuredHeight() && focusY > currentDrawableHeight + mTranslationY) {
					focusY = currentDrawableHeight + mTranslationY;
				}

				if(mLastFocusX != null && mLastFocusY != null) {
					final float dx = focusX - mLastFocusX;
					final float dy = focusY - mLastFocusY;
					mMatrix.postTranslate(dx, dy);
				}

				final float scale = computeScale(getMinScale(), mScale, scaleFactor);
				mMatrix.postScale(scale, scale, focusX, focusY);

				clearAnimation();
				invalidate();

				mLastFocusX = focusX;
				mLastFocusY = focusY;

				return true;
			}

			@Override
			public void onScaleEnd(ScaleGestureDetector detector) {
				loadMatrixValues();

				final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
				final float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

				final float dx = computeTranslation(getMeasuredWidth(), currentDrawableWidth, mTranslationX, 0);
				final float dy = computeTranslation(getMeasuredHeight(), currentDrawableHeight, mTranslationY, 0);

				if(Math.abs(dx) < 1 && Math.abs(dy) < 1) {
					return;
				}

				final float targetTranslationX = mTranslationX + dx;
				final float targetTranslationY = mTranslationY + dy;

				clearAnimation();
				final Animation animation = new TouchAnimation(mScale, targetTranslationX, targetTranslationY);
				animation.setDuration(SCALE_END_ANIMATION_DURATION);
				startAnimation(animation);

				mRepositioningAnimation = true;
			}
		};

		mTouchGestureDetector = new TouchGestureDetector(context, listener);

		super.setScaleType(ScaleType.MATRIX);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int oldMeasuredWidth = getMeasuredWidth();
		final int oldMeasuredHeight = getMeasuredHeight();

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		if(oldMeasuredWidth != getMeasuredWidth() || oldMeasuredHeight != getMeasuredHeight()) {
			resetToInitialState();
		}
	}
	
	@Override
	public void setImageMatrix(Matrix matrix) {
	}
	
	@Override
	public Matrix getImageMatrix() {
		return mMatrix;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		mTouchGestureDetector.onTouchEvent(event);

		return true;
	}

	@Override
	public void clearAnimation() {
		super.clearAnimation();
		mRepositioningAnimation = false;
	}

	@Override
	public void setImageDrawable(Drawable drawable) {
		super.setImageDrawable(drawable);
		if(mDrawable != drawable) {
			mDrawable = drawable;
			if(drawable != null) {
				mDrawableIntrinsicWidth = drawable.getIntrinsicWidth();
				mDrawableIntrinsicHeight = drawable.getIntrinsicHeight();
				resetToInitialState();
			} else {
				mDrawableIntrinsicWidth = 0;
				mDrawableIntrinsicHeight = 0;
			}
		}
	}

	@Override
	public void setScaleType(ScaleType scaleType) {
		if(scaleType != ScaleType.MATRIX) {
			throw new IllegalArgumentException("Unsupported scaleType. Only ScaleType.MATRIX is allowed.");
		}
	}

	@Override
	public boolean canScrollHorizontally(int direction) {
		boolean ret = false;
		float currentDrawableWidth = -1;

		loadMatrixValues();

		if(direction > 0) {
			ret = Math.round(mTranslationX) < 0;
		} else if(direction < 0) {
			currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
			ret = Math.round(mTranslationX) > getMeasuredWidth() - Math.round(currentDrawableWidth);
		}
		return ret;
	}

	private void resetToInitialState() {
		mMatrix.reset();
		final float minScale = getMinScale();
		mMatrix.postScale(minScale, minScale);

		final float freeSpaceHorizontal = (getMeasuredWidth() - (mDrawableIntrinsicWidth * minScale)) / 2;
		final float freeSpaceVertical = (getMeasuredHeight() - (mDrawableIntrinsicHeight * minScale)) / 2;
		mMatrix.postTranslate(freeSpaceHorizontal, freeSpaceVertical);

		invalidate();
	}

	private void loadMatrixValues() {
		mMatrix.getValues(mMatrixValues);
		mScale = mMatrixValues[Matrix.MSCALE_X];
		mTranslationX = mMatrixValues[Matrix.MTRANS_X];
		mTranslationY = mMatrixValues[Matrix.MTRANS_Y];
	}

	private float getMinScale() {
		float minScale = Math.min(getMeasuredWidth() / (float) mDrawableIntrinsicWidth, getMeasuredHeight() / (float) mDrawableIntrinsicHeight);
		if(minScale > 1) {
			minScale = 1;
		}
		return minScale;
	}

	private static float computeTranslation(float viewSize, float drawableSize, float currentTranslation, float delta) {
		final float sideFreeSpace = (viewSize - drawableSize) / 2;

		if(sideFreeSpace > 0) {
			return sideFreeSpace - currentTranslation;
		} else if(currentTranslation + delta > 0) {
			return -currentTranslation;
		} else if(currentTranslation + delta < viewSize - drawableSize) {
			return viewSize - drawableSize - currentTranslation;
		}

		return delta;
	}

	private static float computeScale(float minScale, float currentScale, float delta) {
		if(currentScale * delta < minScale) {
			return minScale / currentScale;
		} else if(currentScale * delta > 1) {
			return 1 / currentScale;
		}

		return delta;
	}

	private class FlingAnimation extends Animation {

		@Override
		protected void applyTransformation(float interpolatedTime, Transformation t) {
			mFlingScroller.computeScrollOffset(interpolatedTime);

			loadMatrixValues();

			final float dx = mFlingScroller.getCurrX() - mTranslationX;
			final float dy = mFlingScroller.getCurrY() - mTranslationY;
			mMatrix.postTranslate(dx, dy);

			invalidate();
		}
	}

	private class TouchAnimation extends Animation {

		private float initialScale;
		private float initialTranslationX;
		private float initialTranslationY;

		private float targetScale;
		private float targetTranslationX;
		private float targetTranslationY;

		TouchAnimation(float targetScale, float targetTranslationX, float targetTranslationY) {
			loadMatrixValues();
			
			this.initialScale =  mScale;
			this.initialTranslationX = mTranslationX;
			this.initialTranslationY = mTranslationY;

			this.targetScale = targetScale;
			this.targetTranslationX = targetTranslationX;
			this.targetTranslationY = targetTranslationY;
		}

		@Override
		protected void applyTransformation(float interpolatedTime, Transformation t) {
			loadMatrixValues();

			if(interpolatedTime > 1) {
				interpolatedTime = 1;
			}

			final float scaleFactor = (this.initialScale + interpolatedTime * (this.targetScale - this.initialScale)) / mScale;
			mMatrix.postScale(scaleFactor, scaleFactor);

			mMatrix.getValues(mMatrixValues);
			final float currentTranslationX = mMatrixValues[Matrix.MTRANS_X];
			final float currentTranslationY = mMatrixValues[Matrix.MTRANS_Y];

			final float dx = this.initialTranslationX + interpolatedTime * (this.targetTranslationX - this.initialTranslationX) - currentTranslationX;
			final float dy = this.initialTranslationY + interpolatedTime * (this.targetTranslationY - this.initialTranslationY) - currentTranslationY;
			mMatrix.postTranslate(dx, dy);

			invalidate();
		}
	}
}