package propra.mci;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class BufferedLRUCache {
	
	// DEBUG
	// boolean debug = Settings.debug;
	boolean debug = false;
	
	int currentSize;
	int maxSize;
	int imageSize;
	
	String currentlyRemovedImage;
	int currentlyRemovedImageSize;
	
	private Bitmap imageData;
	private Bitmap currentlyRemovedImageData;
	
	ConcurrentHashMap<String, Bitmap> cacheMap;
	LinkedBlockingDeque<String> cacheOrderSet;

	/**
	 * @param cacheSize - the size for our cache (about 8-12 MB should be fine ;-))
	 */

	public BufferedLRUCache(int cacheSize) {
		
		currentSize = 0;
		maxSize = cacheSize;

		// initialize an empty ConcurrentHashMap as cache table
		cacheMap = new ConcurrentHashMap<String, Bitmap>();
		
		// initialize a Deque for handling the last-recently-used cache
		cacheOrderSet = new LinkedBlockingDeque<String>();
	}
	
	/**
	 * Fetches a Bitmap from cache if exits
	 * 
	 * @param imageID - the image ID (as key)
	 * @return the Bitmap reference if it's inside the cache, otherwise null
	 */
	public synchronized Bitmap get(String imageID) {
		
		// check if image ID is already in our OrderSet or put the image into
		// if already inside, first remove to sort again
		cacheOrderSet.remove(imageID);
		cacheOrderSet.add(imageID);
		
		imageData = cacheMap.get(imageID);
		
		// DEBUG
		if(debug)
			Log.i("BufferedLRUCache", imageID + " requested from cache");
		
		if(imageData != null) {
			return imageData;
		}
		
		// if image isn't present inside the first level cache, get it from /cache
		imageData = readFromDiskCache(imageID);
		if(imageData == null) {
			// image not inside the cache, remove it from the OrderSet
			cacheOrderSet.remove(imageID);
			
			// DEBUG
			if(debug)
				Log.i("BufferedLRUCache", imageID + " not inside the cache");
			
			return null;
		}
		
		imageSize = imageData.getHeight() * imageData.getRowBytes();
			
		// now let's walk through our SortedSet and remove the latest entries
		while(currentSize + imageSize >= maxSize) {
			currentlyRemovedImage = cacheOrderSet.poll();
			currentlyRemovedImageData = cacheMap.remove(currentlyRemovedImage);
			currentlyRemovedImageSize = currentlyRemovedImageData.getHeight() * currentlyRemovedImageData.getRowBytes();
			currentSize -= currentlyRemovedImageSize;
			
			// recyle the bitmap
			if(!currentlyRemovedImageData.isRecycled())
				currentlyRemovedImageData.recycle();

			// DEBUG
			if(debug)
				Log.i("BufferedLRUCache", currentlyRemovedImage + " removed from cache");
		}
			
		// ok, there seem's to be enough space for the new image now
		cacheMap.put(imageID, imageData);
		currentSize += imageSize;
		
		return imageData;
	}

	/**
	 * Puts a new pair of data into the key-value storage
	 * 
	 * @param imageID - the image ID as key
	 * @param bitmap - the Bitmap as value
	 * @return true if it the image was written
	 */
	public synchronized boolean put(String imageID, Bitmap bitmap) {
		Bitmap currentCacheImage;
		
		// try to receive image from cache
		currentCacheImage = get(imageID);
		if(currentCacheImage != null) {
			// ouh, image is already inside our cache. let's compare this (sameAs is only available up from API level 12...)
			// create a buffer for the already known image
			ByteBuffer currentCacheImageBuffer = ByteBuffer.allocate(currentCacheImage.getHeight() * currentCacheImage.getRowBytes());
			currentCacheImage.copyPixelsToBuffer(currentCacheImageBuffer);
			
			// create a buffer for the new image
			ByteBuffer newImageBuffer = ByteBuffer.allocate(bitmap.getHeight() * bitmap.getRowBytes());
			bitmap.copyPixelsToBuffer(newImageBuffer);
			
			// DEBUG
			if(debug)
				Log.i("BufferedLRUCache", imageID + " already inside cache");
			
			// if someone changes his avatar inside the game play, we need to update it
			if(!Arrays.equals(currentCacheImageBuffer.array(), newImageBuffer.array())) {
				// new image is different from already known one, we need to update both cache levels now!
				writeToDiskCache(imageID, bitmap, true);
				
				int currentCacheImageSize = currentCacheImage.getHeight() * currentCacheImage.getRowBytes();

				// two ways to do it here: check the image size, clear up the cache until the image fits, or:
				// simply delete it from the cache to prevent cache inconsistencies
				cacheOrderSet.remove(imageID);
				cacheMap.remove(imageID);
				currentSize -= currentCacheImageSize;
				
				// DEBUG
				if(debug)
					Log.i("BufferedLRUCache", imageID + " overwritten");
				
				return true;
			}
			
			return false;
		}
				
		// write to second level cache (/cache directory)
		if(writeToDiskCache(imageID, bitmap, false))
			return true;
					
		return false;
	}
	
	/**
	 * Removes all files from /cache for having a clean cache each start
	 * 
	 * @return true if /cache directory was successfully cleaned up
	 */
	public synchronized boolean clearDiskCache() {
		File[] files = AppActivity.mContext.getCacheDir().listFiles();

		// let's iterate through our app's /cache directory
		if(files != null) {
		    for(File file : files)
		    	file.delete();
		    return true;
		}

		return false;
	}
	
	/**
	 * Writes Bitmaps to the application's caching directory
	 * 
	 * @param imageID - the image ID (will be the filename of the saved .png)
	 * @param bitmap - the Bitmap that should be saved to disk
	 * @param overwrite - true, to force the image to be overwritten
	 */
	private boolean writeToDiskCache(String imageID, Bitmap bitmap, boolean overwrite) {
		// create a file name inside the application's caching directory
		File file = new File(AppActivity.mContext.getCacheDir().getPath(), imageID + ".png");
		
		// don't overwrite
 	    if (!file.exists() || overwrite == true) {
 	    	FileOutputStream fileOutputStream = null;
			try {
				fileOutputStream = new FileOutputStream(file);
				
				// DEBUG
				if(debug)
					Log.i("BufferedLRUCache", imageID + " written to disk cache");
				
			} catch (FileNotFoundException e) {
				Log.w("BufferedLRUCache", "FileNotFoundException during writeToDiskCache", e);
			}
			
			// compress the image for output stream
 	    	bitmap.compress(Bitmap.CompressFormat.PNG, 0, fileOutputStream);
 	    	
	        try {
	        	fileOutputStream.flush();
	        	fileOutputStream.close();
			} catch (IOException e) {
				Log.w("BufferedLRUCache", "IOException during writeToDiskCache", e);
			}
	        return true;
 	    }
 	    return false;
	}
	
	/**
	 * @param imageID - the imageID resource that should be fetched
	 * @return - a Bitmap if the resource is available inside the /cache directory, null otherwise
	 */
	private Bitmap readFromDiskCache(String imageID) {
		// create a file resource
		File file = new File(AppActivity.mContext.getCacheDir().getPath(), imageID + ".png");
		
		if (file.exists()) {
			FileInputStream fileInputStream = null;
			try {
				fileInputStream = new FileInputStream(file);
			} catch (FileNotFoundException e) {
				Log.w("BufferedLRUCache", "FileNotFoundException during readFromDiskCache", e);
			}
			
			Bitmap bitmap = BitmapFactory.decodeStream(fileInputStream);
			
			// DEBUG
			if(debug)
				Log.i("BufferedLRUCache", imageID + " successfully read from disk cache");
			
 	    	return bitmap;
		}

		return null;
	}
}
