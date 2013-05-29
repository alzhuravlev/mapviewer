package com.crane.mapview.example;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.ZoomControls;

import com.crane.mapview.MapView;
import com.crane.mapview.MapView.OnBalloonUpdateListener;
import com.crane.mapview.MapView.Overlay;
import com.crane.mapview.MapViewBalloon;
import com.crane.mapview.TilesProvider;

public class MainActivity extends Activity {

	private TilesProvider tilesProvider;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		getWindow().setFormat(PixelFormat.RGB_565);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		tilesProvider = new SqliteTilesProvider();
		tilesProvider.init();

		final MapView mapView = (MapView) findViewById(R.id.map_view);
		mapView.setTilesProvider(tilesProvider);

		ZoomControls zoomControls = (ZoomControls) findViewById(R.id.zoomControls);
		zoomControls.setOnZoomInClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mapView.zoomIn();
			}
		});
		zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mapView.zoomOut();
			}
		});

		MapViewBalloon balloon = (MapViewBalloon) LayoutInflater.from(this).inflate(R.layout.balloon, null);
		mapView.setBalloon(balloon);

		mapView.setOnBalloonUpdateListener(new OnBalloonUpdateListener() {
			@Override
			public void onUpdate(MapViewBalloon balloon, Overlay overlay) {
				TextView title = (TextView) balloon.findViewById(R.id.title);
				TextView subTitle = (TextView) balloon.findViewById(R.id.sub_title);
				title.setText("Привет!");
				subTitle.setText(overlay.data.toString());
			}
		});

		mapView.moveTo(47.458737, 19.090118);

		Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bubble);
		NinePatchDrawable circle = (NinePatchDrawable) getResources().getDrawable(R.drawable.circle);

		for (int i = 0; i < 10000; i++) {
			double lat = Math.random() * .1 + 47.5;
			double lng = Math.random() * .1 + 19;
			Overlay overlay = new Overlay(lat, lng, bitmap, circle);
			overlay.data = String.valueOf(lat);
			mapView.addOverlay(overlay);
		}
		mapView.prepareOverlays(true);
	}
}
