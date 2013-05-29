package com.crane.mapview.example;

import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.util.Log;

import com.crane.mapview.R;
import com.crane.mapview.Tile;
import com.crane.mapview.TilesProvider;

public class OnlineMapTilesProvider extends TilesProvider {

	private Options options = new Options();

	private Bitmap defaultBitmap;

	public OnlineMapTilesProvider() {
		options.inPreferredConfig = Bitmap.Config.RGB_565;
		options.inDither = false;
		options.inScaled = false;
		options.inSampleSize = 1;
	}

	@Override
	protected Tile doFetchTile(int zoomLevel, int tileX, int tileY) {

		// String url =
		// String.format("http://otile1.mqcdn.com/tiles/1.0.0/osm/%d/%d/%d.jpg",
		// zoomLevel, tileX, tileY);
		String url = String.format("http://vec01.maps.yandex.net/tiles?l=map&v=2.32.0&x=%d&y=%d&z=%d", tileX, tileY, zoomLevel);
		// String url =
		// String.format("http://oatile1.mqcdn.com/naip/%d/%d/%d.jpg",
		// zoomLevel, x, y);
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setReadTimeout(5000);
			connection.setConnectTimeout(10000);
			try {
				int rc = connection.getResponseCode();
				if (rc != 200) {
					Log.e(getClass().getSimpleName(), "Unable load tile: x=" + tileX + "; y=" + tileY + "; zoom=" + zoomLevel + ". Server return " + rc);
					return null;
				}

				Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream(), null, options);
				Tile tile = new Tile(bitmap);
				return tile;
			} finally {
				connection.disconnect();
			}
		} catch (Throwable e) {
			Log.e(getClass().getSimpleName(), "Unable load tile: x=" + tileX + "; y=" + tileY + "; zoom=" + zoomLevel, e);
		}
		return null;
	}

	@Override
	protected void doInit() {
	}

	@Override
	protected void doRelease() {
	}

	@Override
	protected double getMinZoomLevel() {
		return 0d;
	}

	@Override
	protected double getMaxZoomLevel() {
		return 16d;
	}

	@Override
	protected int getTileSize() {
		return 256;
	}

	@Override
	protected Bitmap getDefaultBitmap(Context context) {
		if (defaultBitmap == null)
			defaultBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_tile, options);
		return defaultBitmap;
	}
}
