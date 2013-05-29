package com.crane.mapview;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.crane.mapview.MapView.Overlay;

public class MapViewBalloon extends LinearLayout {

	public Overlay overlay;

	public MapViewBalloon(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public MapViewBalloon(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public MapViewBalloon(Context context) {
		super(context);
	}
}
