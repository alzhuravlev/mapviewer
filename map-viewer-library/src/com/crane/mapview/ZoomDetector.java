package com.crane.mapview;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

public class ZoomDetector implements View.OnTouchListener {

	public interface OnZoomListener {
		void onTap(double dx, double dy);

		void onZoomIn(double dx, double dy);

		void onZoomOut(double dx, double dy);

		void onZoom(double scale, double dx, double dy);

		void onPan(double dx, double dy);

		void onFling(int velX, int velY);

		void onDown();
	}

	private enum Mode {
		ZOOM, PAN
	}

	private VelocityTracker velocityTracker;

	private double lastScale;

	private double lastPanX;

	private double lastPanY;

	private double savePanX;

	private double savePanY;

	private Mode mMode;

	private float mX;

	private float mX2;

	private float mY;

	private float mY2;

	private boolean movingStarted;

	private boolean zoomingStarted;

	private boolean wasTap;

	private boolean wasDoubleTap;

	private int touchSlop;

	private int minimumFlingVelocity;

	private boolean secondPointerDown;

	private long lastUp;

	private OnZoomListener onZoomListener;

	private Handler handler;

	private static final int TAP_MESSAGE = 0;

	private class InternalHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case TAP_MESSAGE:
				double dx = Float.intBitsToFloat(msg.arg1);
				double dy = Float.intBitsToFloat(msg.arg2);
				onZoomListener.onTap(dx, dy);
				break;
			}
		}
	}

	public ZoomDetector(Context context) {
		handler = new InternalHandler();

		final ViewConfiguration configuration = ViewConfiguration.get(context);
		touchSlop = configuration.getScaledTouchSlop();
		minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
	}

	public boolean onTouch(View v, MotionEvent event) {

		if (velocityTracker == null)
			velocityTracker = VelocityTracker.obtain();
		velocityTracker.addMovement(event);

		final int action = event.getAction();
		switch (action & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_UP:

			if (onZoomListener != null) {
				if (wasTap && secondPointerDown) {
					double dx = (Math.min(mX, mX2) + Math.abs(mX - mX2) / 2d) / v.getWidth();
					double dy = (Math.min(mY, mY2) + Math.abs(mY - mY2) / 2d) / v.getHeight();
					onZoomListener.onZoomOut(dx, dy);
				} else if (wasDoubleTap && !secondPointerDown) {
					double dx = event.getX() / v.getWidth();
					double dy = event.getY() / v.getHeight();
					onZoomListener.onZoomIn(dx, dy);
				} else if (wasTap && !secondPointerDown) {
					handler.sendMessageDelayed(handler.obtainMessage(TAP_MESSAGE, Float.floatToIntBits(event.getX() / v.getWidth()), Float.floatToIntBits(event.getY() / v.getHeight())), ViewConfiguration.getDoubleTapTimeout());
				}

				if (wasTap)
					lastUp = System.currentTimeMillis();

				if (!wasTap && !secondPointerDown) {
					velocityTracker.addMovement(event);
					velocityTracker.computeCurrentVelocity(1000);
					int velX = -(int) velocityTracker.getXVelocity();
					int velY = -(int) velocityTracker.getYVelocity();
					if (Math.abs(velY) >= minimumFlingVelocity || Math.abs(velY) >= minimumFlingVelocity)
						onZoomListener.onFling(velX, velY);
				}

				secondPointerDown = false;
			}

			if (velocityTracker != null) {
				velocityTracker.recycle();
				velocityTracker = null;
			}

			break;

		case MotionEvent.ACTION_DOWN:

			if (onZoomListener != null)
				onZoomListener.onDown();

			wasDoubleTap = System.currentTimeMillis() - lastUp < ViewConfiguration.getDoubleTapTimeout();
			if (wasDoubleTap)
				handler.removeMessages(TAP_MESSAGE);

			wasTap = true;

			movingStarted = false;

			mX = event.getX();
			mY = event.getY();
			mMode = Mode.PAN;
			lastPanX = 0d;
			lastPanY = 0d;
			savePanX = event.getX();
			savePanY = event.getY();

			break;

		case MotionEvent.ACTION_POINTER_DOWN:
			secondPointerDown = true;

			movingStarted = false;
			zoomingStarted = false;

			mX = event.getX(0);
			mY = event.getY(0);
			mX2 = event.getX(1);
			mY2 = event.getY(1);
			mMode = Mode.ZOOM;
			lastScale = 0d;
			lastPanX = 0d;
			lastPanY = 0d;
			savePanX = getDX(event);
			savePanY = getDY(event);
			break;

		case MotionEvent.ACTION_POINTER_UP:
			savePanX = event.getX(MotionEventCompat.getActionIndex(event) == 1 ? 0 : 1);
			savePanY = event.getY(MotionEventCompat.getActionIndex(event) == 1 ? 0 : 1);
			mMode = Mode.PAN;
			break;

		case MotionEvent.ACTION_MOVE: {

			if (onZoomListener != null)
				if (mMode == Mode.ZOOM) {

					final double panX1 = event.getX() - mX;
					final double panY1 = event.getY() - mY;
					final double pan1 = panX1 * panX1 + panY1 * panY1;

					final double panX2 = event.getX(1) - mX2;
					final double panY2 = event.getY(1) - mY2;
					final double pan2 = panX2 * panX2 + panY2 * panY2;

					if (!zoomingStarted)
						zoomingStarted = touchSlop < pan1 || touchSlop < pan2;

					if (zoomingStarted) {

						handler.removeMessages(TAP_MESSAGE);
						wasDoubleTap = false;
						wasTap = false;

						final double x1 = (event.getX(1) - event.getX(0)) / v.getWidth();
						final double y1 = (event.getY(1) - event.getY(0)) / v.getHeight();
						final double d1 = x1 * x1 + y1 * y1;

						final double x2 = (mX - mX2) / v.getWidth();
						final double y2 = (mY - mY2) / v.getHeight();
						final double d2 = x2 * x2 + y2 * y2;

						final double d = d1 - d2;
						final double scale = Math.pow(1.6d, d);

						final double dx = getDX(event) / v.getWidth();
						final double dy = getDY(event) / v.getHeight();

						onZoomListener.onZoom(smoothedScale(scale), dx, dy);
					}

					final double panX = getDX(event);
					final double panY = getDY(event);

					final double deltaX = savePanX - panX;
					final double deltaY = savePanY - panY;

					if (!movingStarted)
						movingStarted = touchSlop < deltaX * deltaX + deltaY * deltaY;

					if (movingStarted) {
						handler.removeMessages(TAP_MESSAGE);
						wasDoubleTap = false;
						wasTap = false;

						onZoomListener.onPan(smoothedPanX(deltaX), smoothedPanY(deltaY));
					}

					savePanX = panX;
					savePanY = panY;

					mX = event.getX(0);
					mY = event.getY(0);
					mX2 = event.getX(1);
					mY2 = event.getY(1);

				} else if (mMode == Mode.PAN) {

					final double panX = event.getX();
					final double panY = event.getY();

					final double deltaX = savePanX - panX;
					final double deltaY = savePanY - panY;

					if (!movingStarted)
						movingStarted = touchSlop < deltaX * deltaX + deltaY * deltaY;

					if (movingStarted) {
						handler.removeMessages(TAP_MESSAGE);
						wasDoubleTap = false;
						wasTap = false;

						double dx = smoothedPanX(deltaX);
						double dy = smoothedPanY(deltaY);

						onZoomListener.onPan(dx, dy);

					}

					savePanX = panX;
					savePanY = panY;

				}
			break;
		}

		}

		return true;
	}

	private double smoothedScale(double scale) {
		if (lastScale == 0d)
			lastScale = scale;
		lastScale = lastScale + (scale - lastScale) / 5d;
		return lastScale;
	}

	private double smoothedPanX(double panX) {
		if (lastPanX == 0d)
			lastPanX = panX;
		lastPanX = lastPanX + (panX - lastPanX) / 5d;
		return lastPanX;
	}

	private double smoothedPanY(double panY) {
		if (lastPanY == 0d)
			lastPanY = panY;
		lastPanY = lastPanY + (panY - lastPanY) / 5d;
		return lastPanY;
	}

	private double getDX(MotionEvent event) {
		double min = Math.min(event.getX(0), event.getX(1));
		double max = Math.max(event.getX(0), event.getX(1));
		return min + (max - min) / 2d;
	}

	private double getDY(MotionEvent event) {
		double min = Math.min(event.getY(0), event.getY(1));
		double max = Math.max(event.getY(0), event.getY(1));
		return min + (max - min) / 2d;
	}

	public OnZoomListener getOnZoomListener() {
		return onZoomListener;
	}

	public void setOnZoomListener(OnZoomListener onZoomListener) {
		this.onZoomListener = onZoomListener;
	}
}
