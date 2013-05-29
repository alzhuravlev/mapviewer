package com.crane.mapview;

import android.app.ActivityManager;
import android.content.Context;
import android.support.v4.util.LruCache;

public class TilesCache {

	private LruCache<Long, Tile> cache;

	public TilesCache(Context context) {
		final int memClass = ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
		final int cacheSize = 1024 * 1024 * memClass / 5;
		cache = new LruCache<Long, Tile>(cacheSize) {
			@Override
			protected int sizeOf(Long key, Tile value) {
				return value.bitmap.getRowBytes() * value.bitmap.getHeight();
			}

			@Override
			protected void entryRemoved(boolean evicted, Long key, Tile oldValue, Tile newValue) {
				if (newValue == null && oldValue != null) {
					oldValue.bitmap.recycle();
					oldValue.bitmap = null;
				}
			}
		};
	}

	public int size() {
		return cache.size();
	}

	public void clear() {
		cache.evictAll();
	}

	public int hitCount() {
		return cache.hitCount();
	}

	public int missCount() {
		return cache.missCount();
	}

	public Tile get(int tileX, int tileY, int zoomLevel) {
		return cache.get(Tile.getKey(tileX, tileY, zoomLevel));
	}

	public void put(int tileX, int tileY, int zoomLevel, Tile tile) {
		cache.put(Tile.getKey(tileX, tileY, zoomLevel), tile);
	}
}
