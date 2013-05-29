package com.crane.mapview;

public final class MapUtils {

	public static final double LONGITUDE_MAX = 180;

	public static final double LONGITUDE_MIN = -LONGITUDE_MAX;

	public static final double LATITUDE_MAX = 85.05112877980659;

	public static final double LATITUDE_MIN = -LATITUDE_MAX;

	public static int getMapSize(double zoomLevel, int tileSize) {
		return (int) Math.pow(2d, getZoomLevelAsInt(zoomLevel)) * getTileSize(zoomLevel, tileSize);
	}

	public static int getTileSize(double zoomLevel, int tileSize) {
		double f = zoomLevel - Math.floor(zoomLevel);
		f = f < .5f ? f + 1f : .5f * (f + 1f);
		return (int) (tileSize * f);
	}

	public static int getZoomLevelAsInt(double zoomLevel) {
		return (int) Math.round(zoomLevel);
	}

	public static int latitudeToPixelY(double latitude, double zoomLevel, int tileSize) {
		double sinLatitude = Math.sin(latitude * (Math.PI / 180));
		int mapSize = getMapSize(zoomLevel, tileSize);
		return (int) Math.round((0.5d - Math.log((1d + sinLatitude) / (1d - sinLatitude)) / (4d * Math.PI)) * mapSize);
	}

	public static int longitudeToPixelX(double longitude, double zoomLevel, int tileSize) {
		int mapSize = getMapSize(zoomLevel, tileSize);
		return (int) Math.round((longitude + 180d) / 360d * mapSize);
	}

	public static double pixelXToLongitude(int pixelX, double zoomLevel, int tileSize) {
		int mapSize = getMapSize(zoomLevel, tileSize);
		if (pixelX < 0 || pixelX > mapSize) {
			throw new IllegalArgumentException("invalid pixelX coordinate at zoom level " + zoomLevel + ": " + pixelX);
		}
		return 360d * pixelX / mapSize - 180d;
	}

	public static int pixelXToTileX(int pixelX, int tileSize) {
		return pixelX / tileSize;
	}

	public static double pixelYToLatitude(int pixelY, double zoomLevel, int tileSize) {
		int mapSize = getMapSize(zoomLevel, tileSize);
		if (pixelY < 0 || pixelY > mapSize) {
			throw new IllegalArgumentException("invalid pixelY coordinate at zoom level " + zoomLevel + ": " + pixelY);
		}
		double y = .5d - ((double) pixelY / mapSize);
		return 90d - 360d * Math.atan(Math.exp(-y * (2d * Math.PI))) / Math.PI;
	}

	public static int pixelYToTileY(int pixelY, int tileSize) {
		return pixelY / tileSize;
	}

	public static double calculateMinZoomLevel(int width, int height, int tileSize) {
		int max = Math.max(width, height);
		int tilesCount = (int) Math.ceil(1d * max / tileSize);
		return Math.ceil(Math.log(tilesCount) / Math.log(2d));
	}
}
