package com.crane.mapview;

import android.graphics.Bitmap;

public class Tile {

	Bitmap bitmap;

	public static long getKey(int tileX, int tileY, int zoomLevel) {
		return tileX | ((long) (tileY) << 20) | (((long) (zoomLevel)) << 40);
	}

	public static int getTileXFromKey(long key) {
		return (int) (key & (-1L >> 44));
	}

	public static int getTileYFromKey(long key) {
		return (int) ((key >> 20) & (-1 >> 44));
	}

	public static int getZoomLevelFromKey(long key) {
		return (int) ((key >> 40) & (-1 >> 44));
	}

	public Tile(Bitmap bitmap) {
		this.bitmap = bitmap;
	}
}
