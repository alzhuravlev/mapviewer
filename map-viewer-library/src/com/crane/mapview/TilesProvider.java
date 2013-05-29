package com.crane.mapview;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

@SuppressLint("NewApi")
public abstract class TilesProvider {

	public interface OnTileListener {
		void onFetch(Tile tile, int zoomLevel, int tileX, int tileY);
	}

	private OnTileListener onTileListener;

	private Handler handler;

	private TilesThread tilesThread;

	private static final int CORE_POOL_SIZE = 1;

	private static final int MAXIMUM_POOL_SIZE = 2;

	private static final int KEEP_ALIVE = 1;

	private static final AtomicLong TASK_COUNTER = new AtomicLong();

	private static final Set<Long> runningTasks = Collections.synchronizedSet(new HashSet<Long>());

	private static final ThreadFactory sThreadFactory = new ThreadFactory() {
		private final AtomicInteger mCount = new AtomicInteger(1);

		public Thread newThread(Runnable r) {
			return new Thread(r, "MapTask #" + mCount.getAndIncrement());
		}
	};

	private static final BlockingQueue<Runnable> sPoolWorkQueue = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD ? new LinkedBlockingDeque<Runnable>(20) : new LinkedBlockingQueue<Runnable>(20);

	private static final RejectedExecutionHandler REJECTED_EXECUTION_HANDLER = new RejectedExecutionHandler() {

		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			if (!e.isShutdown()) {
				BlockingDeque<Runnable> deque = (BlockingDeque<Runnable>) e.getQueue();
				Task task = (Task) deque.pollLast();
				if (task != null)
					runningTasks.remove(task.key);
				e.execute(r);
			}
		}
	};

	public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory, REJECTED_EXECUTION_HANDLER);

	private class Task implements Runnable, Comparable<Task> {

		long order = TASK_COUNTER.getAndIncrement();

		int zoomLevel;

		int tileX;

		int tileY;

		long key;

		Task(int zoomLevel, int tileX, int tileY) {
			this.zoomLevel = zoomLevel;
			this.tileX = tileX;
			this.tileY = tileY;
			this.key = Tile.getKey(tileX, tileY, zoomLevel);
		}

		@Override
		public void run() {
			Tile tile = doFetchTile(zoomLevel, tileX, tileY);
			handler.obtainMessage(0, new Result(TilesProvider.this, tile, tileX, tileY, zoomLevel)).sendToTarget();
		}

		@Override
		public int compareTo(Task another) {
			return order < another.order ? 1 : -1;
		}

		@Override
		public boolean equals(Object o) {
			Task other = (Task) o;
			return other.key == key;
		}

		@Override
		public int hashCode() {
			return (int) key;
		}
	}

	private class TilesThread extends Thread {

		private Handler handler;

		@Override
		public void run() {
			Looper.prepare();
			handler = new Handler() {
				@Override
				public void dispatchMessage(Message msg) {

					if (msg.what < 0) {
						Looper.myLooper().quit();
						return;
					}

					int tileX = msg.arg1;
					int tileY = msg.arg2;
					int zoomLevel = msg.what;

					long key = Tile.getKey(tileX, tileY, zoomLevel);
					if (runningTasks.contains(key))
						return;

					runningTasks.add(key);
					THREAD_POOL_EXECUTOR.execute(new Task(zoomLevel, tileX, tileY));
				}
			};
			Looper.loop();
		}
	}

	public TilesProvider() {
		this.handler = new InternalHandler();
		this.tilesThread = new TilesThread();
	}

	protected abstract double getMinZoomLevel();

	protected abstract double getMaxZoomLevel();

	protected abstract int getTileSize();

	protected abstract void doInit();

	protected abstract void doRelease();

	protected abstract Bitmap getDefaultBitmap(Context context);

	public final void init() {
		doInit();
		tilesThread.start();
	}

	public final void release() {
		if (tilesThread.handler != null) {
			tilesThread.handler.obtainMessage(-1).sendToTarget();
			try {
				tilesThread.join();
			} catch (InterruptedException e) {
			}
		}
		doRelease();
	}

	protected abstract Tile doFetchTile(int zoomLevel, int tileX, int tileY);

	public final void fetchTileAsync(final int zoomLevel, final int tileX, final int tileY) {
		tilesThread.handler.obtainMessage(zoomLevel, tileX, tileY).sendToTarget();
	}

	private void onResult(Tile tile, int tileX, int tileY, int zoomLevel) {
		runningTasks.remove(Tile.getKey(tileX, tileY, zoomLevel));
		if (tile != null) {
			if (onTileListener != null)
				onTileListener.onFetch(tile, zoomLevel, tileX, tileY);
		}
	}

	public OnTileListener getOnTileListener() {
		return onTileListener;
	}

	public void setOnTileListener(OnTileListener onTileListener) {
		this.onTileListener = onTileListener;
	}

	private static class InternalHandler extends Handler {
		public void handleMessage(Message msg) {
			Result result = (Result) msg.obj;
			result.tilesProvider.onResult(result.tile, result.tileX, result.tileY, result.zoomLevel);
		}
	}

	private static class Result {
		TilesProvider tilesProvider;

		Tile tile;

		int tileX;

		int tileY;

		int zoomLevel;

		public Result(TilesProvider tilesProvider, Tile tile, int tileX, int tileY, int zoomLevel) {
			this.tilesProvider = tilesProvider;
			this.tile = tile;
			this.tileX = tileX;
			this.tileY = tileY;
			this.zoomLevel = zoomLevel;
		}
	}
}
