package com.crane.mapview.example;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.util.Log;

import com.crane.mapview.Tile;
import com.crane.mapview.TilesProvider;

public class SqliteTilesProvider extends TilesProvider {
	private Options options = new Options();

	private Bitmap defaultBitmap;

	private SQLiteDatabase database;

	public SqliteTilesProvider() {
		options.inPreferredConfig = Bitmap.Config.RGB_565;
		options.inDither = false;
		options.inScaled = false;
		options.inSampleSize = 1;
	}

	@Override
	protected Tile doFetchTile(int zoomLevel, int tileX, int tileY) {
		try {
			Cursor cursor = database.rawQuery("select tile_data from tiles where tile_row = ? and tile_column = ? and zoom_level = ?", new String[] { "" + tileY, "" + tileX, "" + zoomLevel });
			try {
				if (cursor.moveToFirst()) {
					byte[] data = cursor.getBlob(0);

					Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
					Tile tile = new Tile(bitmap);
					return tile;
				}
			} finally {
				cursor.close();
			}
		} catch (Throwable e) {
			Log.e(getClass().getSimpleName(), "Unable load tile: x=" + tileX + "; y=" + tileY + "; zoom=" + zoomLevel, e);
		}
		return null;
	}

	@Override
	protected void doInit() {
		database = SQLiteDatabase.openDatabase("/sdcard/Europe_Russia_Moscow@2x.mbtiles", null, SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
	}

	@Override
	protected void doRelease() {
		if (database != null) {
			database.close();
			database = null;
		}
	}

	@Override
	protected double getMinZoomLevel() {
		try {
			Cursor cursor = database.rawQuery("select min(zoom_level) from tiles", null);
			try {
				if (cursor.moveToFirst()) {
					return cursor.getDouble(0);
				}
			} finally {
				cursor.close();
			}
		} catch (Throwable e) {
			Log.e(getClass().getSimpleName(), "Unable to load min zoom", e);
		}
		return 0d;
	}

	@Override
	protected double getMaxZoomLevel() {
		try {
			Cursor cursor = database.rawQuery("select max(zoom_level) from tiles", null);
			try {
				if (cursor.moveToFirst()) {
					return cursor.getDouble(0);
				}
			} finally {
				cursor.close();
			}
		} catch (Throwable e) {
			Log.e(getClass().getSimpleName(), "Unable to load max zoom", e);
		}
		return 0d;
	}

	@Override
	protected int getTileSize() {
		try {
			Cursor cursor = database.rawQuery("select tile_data from tiles limit 1", null);
			try {
				if (cursor.moveToFirst()) {
					byte[] data = cursor.getBlob(0);

					Options options = new Options();
					options.inJustDecodeBounds = true;
					BitmapFactory.decodeByteArray(data, 0, data.length, options);
					return options.outWidth;
				}
			} finally {
				cursor.close();
			}
		} catch (Throwable e) {
			Log.e(getClass().getSimpleName(), "Unable to load max zoom", e);
		}
		return 256;
	}

	@Override
	protected Bitmap getDefaultBitmap(Context context) {
		if (defaultBitmap == null)
			defaultBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_tile, options);
		return defaultBitmap;
	}
}
