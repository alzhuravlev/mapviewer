package com.crane.mapview;

import android.view.animation.Animation;
import android.view.animation.Transformation;

public class ZoomAnimation extends Animation {

	private double zoomLevel;

	private double targetZoomLevel;

	private MapView mapView;

	private double dx;

	private double dy;

	public ZoomAnimation(MapView mapView) {
		this.mapView = mapView;
	}

	public void reset(double zoomLevel, double targetZoomLevel, double dx, double dy) {
		this.zoomLevel = zoomLevel;
		this.targetZoomLevel = targetZoomLevel;
		this.dx = dx;
		this.dy = dy;
		reset();
	}

	@Override
	protected void applyTransformation(float interpolatedTime, Transformation t) {
		double zoom = zoomLevel + (targetZoomLevel - zoomLevel) * interpolatedTime;
		mapView.zoomTo(zoom, dx, dy);
	}
}
