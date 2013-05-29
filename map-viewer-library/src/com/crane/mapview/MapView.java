package com.crane.mapview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Scroller;
import android.widget.Toast;

import com.crane.mapview.ZoomDetector.OnZoomListener;

public class MapView extends ViewGroup implements TilesProvider.OnTileListener {

	public interface OnBalloonUpdateListener {
		void onUpdate(MapViewBalloon balloon, Overlay overlay);
	}

	public static class RoutePoint {
		private double lat;

		private double lng;

		public RoutePoint(double lat, double lng) {
			this.lat = lat;
			this.lng = lng;
		}
	}

	public static class Overlay implements Comparable<Overlay> {

		private double lat;

		private double lng;

		Bitmap bitmap;

		NinePatchDrawable circle;

		int x;

		int y;

		int count;

		Rect rect = new Rect();

		List<Overlay> items;

		public Object data;

		private double zoomLevel;

		private String text;

		Overlay() {
		}

		public Overlay(double lat, double lng, Bitmap bitmap, NinePatchDrawable circle) {
			this.lat = lat;
			this.lng = lng;
			this.bitmap = bitmap;
			this.circle = circle;
		}

		public Overlay(double lat, double lng, Bitmap bitmap, String text) {
			this.lat = lat;
			this.lng = lng;
			this.bitmap = bitmap;
			this.text = text;
		}

		private Overlay(Overlay overlay) {
			this.lat = overlay.lat;
			this.lng = overlay.lng;
			this.bitmap = overlay.bitmap;
			this.circle = overlay.circle;
			this.data = overlay.data;
			this.text = overlay.text;
		}

		private Overlay copy() {
			return new Overlay(this);
		}

		private void ensureZoomLevel(double zoomLevel, int tileSize) {
			if (this.zoomLevel != zoomLevel) {
				x = MapUtils.longitudeToPixelX(lng, zoomLevel, tileSize);
				y = MapUtils.latitudeToPixelY(lat, zoomLevel, tileSize);
				this.zoomLevel = zoomLevel;
			}
		}

		@Override
		public int compareTo(Overlay another) {
			return another.lat < lat ? -1 : another.lat > lat ? 1 : 0;
		}
	}

	private final static String TAG = MapView.class.getName();

	public static final double MAX_ZOOM_LEVEL = 18;

	private OnBalloonUpdateListener onBalloonUpdateListener;

	private LocationManager locationManager;

	private Paint paint;

	private double minZoomLevel = 0;

	private double maxZoomLevel = 16;

	private double zoomLevel = 16;

	private int mapSize;

	private int maxScrollX;

	private int maxScrollY;

	private int currentTileSize;

	private int tileSize;

	private Scroller scroller;

	private ZoomDetector zoomDetector;

	private TilesCache tilesCache;

	private TilesProvider tilesProvider;

	private ScaleAnimation scaleAnimation;

	private ZoomAnimation zoomAnimation;

	private List<Overlay> overlays = new ArrayList<MapView.Overlay>();

	private SparseArray<List<Overlay>> overlaysMap = new SparseArray<List<Overlay>>();

	private MapViewBalloon balloon;

	private List<RoutePoint> routePoints;

	private Location location;

	private Bitmap myLocationBitmap;

	private OnZoomListener onZoomListener = new ZoomDetector.OnZoomListener() {
		@Override
		public void onZoom(double scale, double dx, double dy) {
			zoom(scale, dx, dy);
		}

		@Override
		public void onPan(double dx, double dy) {
			scrollBy((int) dx, (int) dy);
		}

		@Override
		public void onTap(double dx, double dy) {
			Overlay overlay = findOverlay((int) (dx * getWidth()), (int) (dy * getHeight()));
			if (overlay != null) {
				if (overlay.count > 1)
					zoomIn(dx, dy);
				else
					showBalloon(overlay);
			} else
				hideBalloon();
		}

		@Override
		public void onZoomIn(double dx, double dy) {
			zoomIn(dx, dx);
		}

		@Override
		public void onZoomOut(double dx, double dy) {
			zoomOut(dx, dx);
		}

		@Override
		public void onFling(int velX, int velY) {
			scroller.fling(getScrollX(), getScrollY(), velX / 4, velY / 4, 0, maxScrollX, 0, maxScrollY);
			invalidate();
		}

		@Override
		public void onDown() {
			scroller.forceFinished(true);
		}
	};

	private Runnable unableToDetectMyLocation = new Runnable() {

		@Override
		public void run() {
			Toast.makeText(getContext(), getResources().getString(R.string.unable_detect_my_location), Toast.LENGTH_LONG).show();
		}
	};

	private LocationListener locationListener = new LocationListener() {

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onProviderDisabled(String provider) {
		}

		@Override
		public void onLocationChanged(Location location) {
			MapView.this.getHandler().removeCallbacks(unableToDetectMyLocation);
			MapView.this.location = location;
			MapView.this.invalidate();
		}
	};

	public MapView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		setBackgroundResource(0);

		this.myLocationBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.icon_my_location);
		this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

		this.zoomDetector = new ZoomDetector(context);
		this.zoomDetector.setOnZoomListener(onZoomListener);

		this.scroller = new Scroller(context, new LinearInterpolator());

		this.tilesCache = new TilesCache(context);

		this.zoomAnimation = new ZoomAnimation(this);
		this.zoomAnimation.setInterpolator(new LinearInterpolator());
		this.zoomAnimation.setDuration(200);

		this.paint = new Paint();
		this.paint.setDither(false);
		this.paint.setAntiAlias(true);
		this.paint.setFilterBitmap(true);
		this.paint.setStyle(Style.FILL);

		scaleAnimation = new ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, 1f);
		scaleAnimation.setDuration(250);
		scaleAnimation.setInterpolator(new OvershootInterpolator(1.5f));
		scaleAnimation.setAnimationListener(new AnimationListener() {

			@Override
			public void onAnimationStart(Animation animation) {
				balloon.setVisibility(View.VISIBLE);
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
			}
		});

		setWillNotDraw(false);
	}

	public MapView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public MapView(Context context) {
		this(context, null);
	}

	private void log(String msg) {
		Log.i(TAG, msg);
	}

	private void adjustZoomLevel(int w, int h) {
		if (zoomLevel < minZoomLevel) {
			zoomLevel = minZoomLevel;
		} else if (zoomLevel > maxZoomLevel) {
			zoomLevel = maxZoomLevel;
		}
	}

	Rect rect2 = new Rect();

	private boolean drawNearestPrevZoom(Canvas canvas, int tileX, int tileY, int zoomLevel, int step) {

		int pow2 = 1 << step;

		int prevZoomLevel = zoomLevel - step;
		int prevTileX = tileX / pow2;
		int prevTileY = tileY / pow2;
		Tile tile = tilesCache.get(prevTileX, prevTileY, prevZoomLevel);
		if (tile != null && tile.bitmap != null && !tile.bitmap.isRecycled()) {

			int x = tileX % pow2;
			int y = tileY % pow2;

			int snippetSize = tileSize / pow2;

			rect2.left = x * snippetSize;
			rect2.top = y * snippetSize;
			rect2.right = rect2.left + snippetSize;
			rect2.bottom = rect2.top + snippetSize;

			canvas.drawBitmap(tile.bitmap, rect2, rect, null);
			return true;
		}
		return false;
	}

	Rect rect = new Rect();

	Paint routePaint = new Paint();
	{
		routePaint.setARGB(90, 255, 0, 0);
		routePaint.setStrokeWidth(10);
		routePaint.setDither(true);
		routePaint.setAntiAlias(true);
		routePaint.setStrokeJoin(Join.ROUND);
		routePaint.setStrokeCap(Cap.ROUND);
		routePaint.setStyle(Style.STROKE);
	}

	Path path = new Path();

	private void drawMyLocation(Canvas canvas) {
		if (location == null)
			return;

		int w = myLocationBitmap.getWidth();
		int h = myLocationBitmap.getHeight();

		int x = MapUtils.longitudeToPixelX(location.getLongitude(), zoomLevel, tileSize) - w / 2;
		int y = MapUtils.latitudeToPixelY(location.getLatitude(), zoomLevel, tileSize) - h / 2;
		canvas.drawBitmap(myLocationBitmap, x, y, null);
	}

	private void drawRoute(Canvas canvas) {
		if (routePoints == null || routePoints.size() == 0)
			return;

		RoutePoint last = null;
		path.reset();
		for (RoutePoint routePoint : routePoints) {
			if (last == null) {
				last = routePoint;
				int x = MapUtils.longitudeToPixelX(routePoint.lng, zoomLevel, tileSize);
				int y = MapUtils.latitudeToPixelY(routePoint.lat, zoomLevel, tileSize);
				path.moveTo(x, y);
				continue;
			}

			int x = MapUtils.longitudeToPixelX(routePoint.lng, zoomLevel, tileSize);
			int y = MapUtils.latitudeToPixelY(routePoint.lat, zoomLevel, tileSize);
			path.lineTo(x, y);
		}
		canvas.drawPath(path, routePaint);
	}

	private void drawOverlay(Canvas canvas, Overlay overlay) {
		if (overlay.count > 1 && overlay.circle != null) {

			final String text = String.valueOf(overlay.items.size());
			overlay.circle.getPadding(rect2);

			float tw = paint.measureText(text) + rect2.left + rect2.right;
			float th = paint.getTextSize() + rect2.top + rect2.bottom;

			float x = overlay.x - tw / 2;
			float y = overlay.y - th / 2;

			rect.left = (int) x - 3;
			rect.top = (int) y - 3;
			rect.right = rect.left + (int) tw + 3;
			rect.bottom = rect.top + (int) th + 3;

			overlay.circle.setBounds(rect);
			overlay.circle.draw(canvas);
			overlay.rect.set(rect);

			paint.setTextSize(28);
			paint.setColor(Color.WHITE);
			canvas.drawText(text, x + rect2.left, y + th - rect2.bottom - 4, paint);
		} else {
			float x = overlay.x - overlay.bitmap.getWidth() / 2;
			float y = overlay.y - (overlay.text == null ? overlay.bitmap.getHeight() : overlay.bitmap.getHeight() / 2);
			overlay.rect.set((int) x - 3, (int) y - 3, (int) x + overlay.bitmap.getWidth() + 3, (int) y + overlay.bitmap.getHeight() + 3);
			canvas.drawBitmap(overlay.bitmap, x, y, null);

			if (overlay.text != null) {
				paint.setTextSize(overlay.bitmap.getHeight() - 20);

				int tw = (int) paint.measureText(overlay.text);
				int th = (int) paint.getTextSize();

				x = overlay.x - tw / 2;
				y = overlay.y + th / 2 - 3;

				paint.setColor(Color.BLACK);
				canvas.drawText(overlay.text, x, y, paint);
			}
		}

		// paint.setStyle(Style.FILL);
		// paint.setColor(Color.YELLOW);
		// canvas.drawCircle(overlay.x, overlay.y, 5, paint);
	}

	public void drawTile(Canvas canvas, int tileX, int tileY, int zoomLevel, int x1, int y1, int x2, int y2, int sx1, int sy1, int sx2, int sy2) {

		rect.left = x1;
		rect.top = y1;
		rect.right = x2;
		rect.bottom = y2;

		boolean drawPrev = false;

		Tile tile = tilesCache.get(tileX, tileY, zoomLevel);
		if (tile == null || tile.bitmap == null || tile.bitmap.isRecycled()) {
			drawPrev = drawNearestPrevZoom(canvas, tileX, tileY, zoomLevel, 1);
			if (!drawPrev)
				drawPrev = drawNearestPrevZoom(canvas, tileX, tileY, zoomLevel, 2);
			if (tilesProvider != null)
				tilesProvider.fetchTileAsync(zoomLevel, tileX, tileY);
		}

		if (!drawPrev) {
			Bitmap bitmap = tile != null ? tile.bitmap : tilesProvider != null ? tilesProvider.getDefaultBitmap(getContext()) : null;
			if (bitmap != null)
				canvas.drawBitmap(bitmap, null, rect, paint);
		}

		// canvas.drawRect(rect, paint);
		// canvas.drawText(tileX + " : " + tileY, rect.left + 10, rect.top + 40,
		// paint);
	}

	private Overlay dummyOverlay = new Overlay();

	@SuppressLint("NewApi")
	@Override
	public void onDraw(Canvas canvas) {

		if (currentTileSize == 0)
			return;

		long t1 = System.nanoTime();

		int w = getWidth();
		int h = getHeight();

		int sx1 = getScrollX();
		int sy1 = getScrollY();

		int sx2 = sx1 + w;
		int sy2 = sy1 + h;

		int zoom = MapUtils.getZoomLevelAsInt(zoomLevel);

		int tileX = MapUtils.pixelXToTileX(sx1, currentTileSize);
		int x1 = tileX * currentTileSize;
		int x2 = x1 + currentTileSize;

		int _tileY = MapUtils.pixelYToTileY(sy1, currentTileSize);
		int _y1 = _tileY * currentTileSize;
		int _y2 = _y1 + currentTileSize;

		while (x1 <= sx2 && x2 >= sx1) {

			int tileY = _tileY;
			int y1 = _y1;
			int y2 = _y2;

			while (y1 <= sy2 && y2 >= sy1) {
				drawTile(canvas, tileX, tileY, zoom, x1, y1, x2, y2, sx1, sy1, sx2, sy2);

				tileY++;
				y1 += currentTileSize;
				y2 += currentTileSize;
			}

			tileX++;
			x1 += currentTileSize;
			x2 += currentTileSize;
		}

		if (routePoints != null)
			drawRoute(canvas);

		List<Overlay> overlays = overlaysMap.get(zoom);

		if (overlays == null)
			overlays = this.overlays;

		if (overlays != null) {
			dummyOverlay.lat = MapUtils.pixelYToLatitude(sy1, zoomLevel, tileSize);
			int ind1 = Collections.binarySearch(overlays, dummyOverlay);
			ind1 = ind1 < 0 ? -ind1 - 1 : ind1;

			dummyOverlay.lat = MapUtils.pixelYToLatitude(sy2, zoomLevel, tileSize);
			int ind2 = Collections.binarySearch(overlays, dummyOverlay);
			ind2 = ind2 < 0 ? -ind2 - 1 : ind2;

			if (ind1 >= 0 && ind1 <= overlays.size() && ind2 >= 0 && ind2 <= overlays.size() && ind1 <= ind2)
				for (int i = ind1; i < ind2; i++) {
					Overlay overlay = overlays.get(i);
					overlay.ensureZoomLevel(zoomLevel, tileSize);
					if (overlay.x <= sx2 && overlay.x + overlay.bitmap.getWidth() >= sx1)
						if (overlay.y <= sy2 && overlay.y + overlay.bitmap.getHeight() >= sy1)
							drawOverlay(canvas, overlay);
				}
		}

		drawMyLocation(canvas);

		if (scroller.computeScrollOffset()) {
			scrollTo(scroller.getCurrX(), scroller.getCurrY());
		}

		long t2 = System.nanoTime();
		// log(canvas.isHardwareAccelerated() + " onDraw: " + ((t2 - t1) / 1e6)
		// + " ms");
	}

	private Overlay findOverlay(int x, int y) {
		int w = getWidth();
		int h = getHeight();

		int sx1 = getScrollX();
		int sy1 = getScrollY();

		int sx2 = sx1 + w;
		int sy2 = sy1 + h;

		x += sx1;
		y += sy1;

		List<Overlay> overlays = overlaysMap.get(MapUtils.getZoomLevelAsInt(zoomLevel));
		if (overlays == null)
			overlays = this.overlays;

		if (overlays != null) {
			dummyOverlay.lat = MapUtils.pixelYToLatitude(sy1, zoomLevel, tileSize);
			int ind1 = Collections.binarySearch(overlays, dummyOverlay);
			ind1 = ind1 < 0 ? -ind1 - 1 : ind1;

			dummyOverlay.lat = MapUtils.pixelYToLatitude(sy2, zoomLevel, tileSize);
			int ind2 = Collections.binarySearch(overlays, dummyOverlay);
			ind2 = ind2 < 0 ? -ind2 - 1 : ind2;

			if (ind1 >= 0 && ind1 <= overlays.size() && ind2 >= 0 && ind2 <= overlays.size() && ind1 <= ind2) {
				Overlay result = null;
				int min = Integer.MAX_VALUE;
				for (int i = ind1; i < ind2; i++) {
					Overlay overlay = overlays.get(i);
					if (overlay.rect.contains(x, y)) {
						int dx = x - (int) overlay.rect.exactCenterX();
						int dy = y - (int) overlay.rect.exactCenterY();
						int d = dx * dx + dy * dy;
						if (min > d) {
							result = overlay;
							min = d;
						}
					}
				}
				return result;
			}
		}
		return null;
	}

	public void showBalloon(Overlay overlay) {
		if (balloon == null)
			return;

		balloon.overlay = overlay;

		if (onBalloonUpdateListener != null)
			onBalloonUpdateListener.onUpdate(balloon, overlay);

		balloon.setVisibility(View.VISIBLE);
		balloon.startAnimation(scaleAnimation);
		requestLayout();
		moveTo(overlay.lat, overlay.lng);
	}

	private void hideBalloon() {
		if (balloon != null) {
			balloon.setVisibility(View.GONE);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		double minZoomLevel = MapUtils.calculateMinZoomLevel(w, h, tileSize);
		this.minZoomLevel = Math.max(minZoomLevel, this.minZoomLevel);

		updateSizes(w, h);

		log("zoomLevel = " + zoomLevel);
		log("mapSize = " + mapSize);
		log("currentTileSize = " + currentTileSize);
		log("maxScrollX = " + maxScrollX);
		log("maxScrollY = " + maxScrollY);
	}

	private void updateSizes(int w, int h) {
		adjustZoomLevel(w, h);
		this.mapSize = MapUtils.getMapSize(zoomLevel, tileSize);
		this.currentTileSize = MapUtils.getTileSize(zoomLevel, tileSize);
		this.maxScrollX = mapSize - w;
		this.maxScrollY = mapSize - h;
	}

	private void zoom(double scale, double dx, double dy) {
		if (zoomAnimation != null)
			zoomAnimation.cancel();
		zoomTo(zoomLevel * scale, dx, dy);
	}

	public void zoomToSpan(double lat, double lng) {
		int w = getWidth();
		int h = getHeight();

		double b;

		b = (360d * w / tileSize / lng);
		double zoomW = Math.log(b) / Math.log(2);

		b = ((MapUtils.LATITUDE_MAX - MapUtils.LATITUDE_MIN) * h / tileSize / lat);
		double zoomH = Math.log(b) / Math.log(2);

		double zoom = Math.min(zoomW, zoomH);

		zoomTo(zoom, .5d, .5d);
	}

	private void zoomToAnim(double zoom, double dx, double dy) {
		zoomAnimation.reset(zoomLevel, zoom, dx, dy);
		this.startAnimation(zoomAnimation);
	}

	private float calcNextZoom() {
		return Math.round(zoomLevel + 1.25d);
	}

	private float calcPrevZoom() {
		return Math.round(zoomLevel - 1.25d);
	}

	public void zoomIn(double dx, double dy) {
		zoomToAnim(calcNextZoom(), dx, dy);
	}

	public void zoomIn() {
		zoomIn(.5d, .5d);
	}

	public void zoomOut(double dx, double dy) {
		zoomToAnim(calcPrevZoom(), dx, dy);
	}

	public void zoomOut() {
		zoomOut(.5d, .5d);
	}

	public void zoomTo(double zoom, double dx, double dy) {

		zoomLevel = zoom;

		int oldMapSize = mapSize;

		int sx = getScrollX();
		int sy = getScrollY();

		int w = getWidth();
		int h = getHeight();

		double left = dx * w;
		double top = dy * h;

		updateSizes(w, h);

		double mapScale = (double) mapSize / oldMapSize;

		int scrollX = (int) Math.round((sx + left) * mapScale - left);
		int scrollY = (int) Math.round((sy + top) * mapScale - top);
		scrollTo(scrollX, scrollY);

		changeBalloonPosition();

		invalidate();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		zoomDetector.onTouch(this, event);
		return true;
	}

	@Override
	public void scrollTo(int x, int y) {
		x = Math.max(0, Math.min(x, maxScrollX));
		y = Math.max(0, Math.min(y, maxScrollY));
		super.scrollTo(x, y);
	}

	@Override
	public void onFetch(Tile tile, int zoomLevel, int tileX, int tileY) {

		if (tile == null)
			return;

		int tx1 = tileX * currentTileSize;
		int tx2 = tx1 + currentTileSize;
		int ty1 = tileY * currentTileSize;
		int ty2 = ty1 + currentTileSize;

		int sx1 = getScrollX();
		int sy1 = getScrollY();
		int sx2 = sx1 + getWidth();
		int sy2 = sy1 + getHeight();

		if (tx1 <= sx2 && tx2 >= sx1 && ty1 <= sy2 && ty2 >= sy1) {
			invalidate();
		}

		tilesCache.put(tileX, tileY, zoomLevel, tile);
	}

	public void moveTo(final double lat, final double lng) {
		post(new Runnable() {
			@Override
			public void run() {
				int w = getWidth();
				int h = getHeight();
				int x = MapUtils.longitudeToPixelX(lng, zoomLevel, tileSize) - w / 2;
				int y = MapUtils.latitudeToPixelY(lat, zoomLevel, tileSize) - h / 2;
				int sx = getScrollX();
				int sy = getScrollY();
				scroller.startScroll(sx, sy, x - sx, y - sy, 300);
				invalidate();
			}
		});
	}

	public void moveToNoAnim(final double lat, final double lng) {
		post(new Runnable() {
			@Override
			public void run() {
				int w = getWidth();
				int h = getHeight();
				int x = MapUtils.longitudeToPixelX(lng, zoomLevel, tileSize) - w / 2;
				int y = MapUtils.latitudeToPixelY(lat, zoomLevel, tileSize) - h / 2;
				scrollTo(x, y);
			}
		});
	}

	public void prepareOverlays(final boolean collapse) {
		post(new Runnable() {
			@Override
			public void run() {
				doPrepareOverlays(collapse);
			}
		});

	}

	private void doPrepareOverlays(boolean collapse) {

		overlaysMap.clear();

		if (collapse) {

			int min = (int) minZoomLevel;
			int max = (int) maxZoomLevel;

			int step = Math.min(getWidth(), getHeight()) / 4; // in pixels
			SparseArray<Overlay> map = new SparseArray<Overlay>();
			for (int zoom = min; zoom <= max - 1; zoom++) {
				map.clear();

				List<Overlay> list = overlaysMap.get(zoom);
				int mapSize = MapUtils.getMapSize(zoom, tileSize);
				int size = mapSize / step;

				for (Overlay overlay : overlays) {

					overlay.ensureZoomLevel(zoom, tileSize);
					int x = overlay.x;
					int y = overlay.y;

					int index = (x / step) * size + y / step;

					Overlay overlayCollapsed = map.get(index);

					if (overlayCollapsed == null) {
						if (list == null) {
							list = new ArrayList<MapView.Overlay>();
							overlaysMap.put(zoom, list);
						}

						overlayCollapsed = overlay.copy();
						overlayCollapsed.items = new ArrayList<MapView.Overlay>();
						list.add(overlayCollapsed);

						map.put(index, overlayCollapsed);
					} else {
						overlayCollapsed.lat = (overlayCollapsed.lat + overlay.lat) / 2;
						overlayCollapsed.lng = (overlayCollapsed.lng + overlay.lng) / 2;
					}
					overlayCollapsed.count++;
					overlayCollapsed.items.add(overlay);
				}

				if (list != null) {
					removeOverCollapsedOverlays(list);
					Collections.sort(list);
				}
			}
			Collections.sort(overlays);
			overlaysMap.put(max, overlays);
		} else {
			Collections.sort(overlays);
		}
	}

	private void removeOverCollapsedOverlays(List<Overlay> overlays) {
		List<Overlay> toAdd = new ArrayList<MapView.Overlay>();
		Iterator<Overlay> i = overlays.iterator();
		while (i.hasNext()) {
			Overlay overlay = i.next();
			if (overlay.items != null && overlay.items.size() < 6) {
				toAdd.addAll(overlay.items);
				i.remove();
			}
		}
		overlays.addAll(toAdd);
	}

	public void addOverlay(Overlay overlay) {
		overlays.add(overlay);
	}

	public void addRoutePoint(RoutePoint routePoint) {
		if (routePoints == null)
			routePoints = new ArrayList<MapView.RoutePoint>();
		routePoints.add(routePoint);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (balloon != null && balloon.getVisibility() == View.VISIBLE) {
			measureChild(balloon, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST));
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		changeBalloonPosition();
	}

	private void changeBalloonPosition() {
		if (balloon != null && balloon.getVisibility() == View.VISIBLE) {
			Overlay overlay = balloon.overlay;
			overlay.ensureZoomLevel(zoomLevel, tileSize);
			int l1 = overlay.x - balloon.getMeasuredWidth() / 2;
			int t1 = overlay.y - balloon.getMeasuredHeight() - (overlay.text == null ? overlay.bitmap.getHeight() : overlay.bitmap.getHeight() / 2);
			int r1 = l1 + balloon.getMeasuredWidth();
			int b1 = t1 + balloon.getMeasuredHeight();
			balloon.layout(l1, t1, r1, b1);
		}
	}

	public TilesProvider getTilesProvider() {
		return tilesProvider;
	}

	public void setTilesProvider(TilesProvider tilesProvider) {
		this.tilesProvider = tilesProvider;
		tileSize = tilesProvider.getTileSize();
		minZoomLevel = tilesProvider.getMinZoomLevel();
		maxZoomLevel = Math.min(tilesProvider.getMaxZoomLevel(), MAX_ZOOM_LEVEL);
		this.tilesProvider.setOnTileListener(this);
		tilesCache.clear();
	}

	public MapViewBalloon getBalloon() {
		return balloon;
	}

	public void setBalloon(MapViewBalloon balloon) {
		if (this.balloon != null)
			removeView(this.balloon);

		this.balloon = balloon;

		if (balloon != null) {
			balloon.setVisibility(View.GONE);
			addView(balloon);
		}
	}

	public OnBalloonUpdateListener getOnBalloonUpdateListener() {
		return onBalloonUpdateListener;
	}

	public void setOnBalloonUpdateListener(OnBalloonUpdateListener onBalloonUpdateListener) {
		this.onBalloonUpdateListener = onBalloonUpdateListener;
	}

	public List<Overlay> getOverlays() {
		return overlays;
	}

	public void enableMyLocation() {
		post(new Runnable() {
			@Override
			public void run() {
				MapView.this.getHandler().postDelayed(unableToDetectMyLocation, 15000);
			}
		});
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 30, locationListener);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 30, locationListener);
	}

	public void disableMyLocation() {
		locationManager.removeUpdates(locationListener);
	}

	public void moveToMyLocation() {
		if (location != null) {
			moveTo(location.getLatitude(), location.getLongitude());
		}
	}
}
