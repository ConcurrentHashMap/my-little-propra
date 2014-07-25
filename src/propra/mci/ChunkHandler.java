package propra.mci;

import java.util.HashSet;
import java.util.Set;
import java.util.TimerTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.util.Log;

/**
 * Hard-coded flags for handling OBJI and UOBJ messages
 */
interface ChunkFlags {

	static final int Position  =   1;
	static final int BasePoint =   2;
	static final int ImageID   =   4;
	static final int State     =   8;
	static final int StateLoop =  16;
	static final int Speed     =  32;
	static final int ColBox    =  64;
	static final int ColFactor = 128;	
}

class ChunkHandler extends SendHandler implements ChunkFlags {
	
	// DEBUG (was too lazy to change every debug to Settings.debug ;-))
	//private boolean debug = Settings.debug;
	private boolean debug = false;
	
	// this is our OBJect
	private OBJect currentOBJect;
	private Set<OBJect> objSet;
	
	private boolean statechanged;
	private boolean imagechanged;
	
	private long objID;
	private int type;
	private int viewID;
	private int flags;
	private int xPos = 0;
	private int yPos = 0;
	private int zPos = 0;
	private int height = 0;
	private int width = 0;
	private int xFP = 0;
	private int yFP = 0;
	private long imageID = 0;
	private int state = 0;
	private int stateLoopSize = 0;
	private int[][] stateLoop = null;

	private volatile MediaPlayer mMediaPlayer;
			
	/**
	 * Handles OBJI messages
	 * 
	 * @param chunkData - the chunk data ByteArray
	 */
	public void handleOBJI(byte[] chunkData) {

		// initialize the current position inside the ByteArray
		int idx = 0;
		statechanged = false;
		imagechanged = false;
		
		/**
		 * Setting the values directly, isn't good OOP, but it's for performance
		 */
		
		// split the ByteArray to the specified contents
		objID = ChunkParser.byteToLong(chunkData, 8, idx);
		idx += 8;
		type = ChunkParser.byteToInteger(chunkData, 4, idx);
		idx += 4;
		viewID = ChunkParser.byteToInteger(chunkData, 4, idx);
		idx += 4;
		flags = ChunkParser.byteToInteger(chunkData, 4, idx);
		idx += 4;
		
		// Now initialize the object with the object ID or reference it, if it's already there
		currentOBJect = AppActivity.objMap.get(objID);
		
		if(currentOBJect == null) {
			currentOBJect = new OBJect(objID);
			AppActivity.objMap.put(objID, currentOBJect);
					
			// DEBUG
			if(debug)
				Log.i("HANDLER", "OBJect: created " + objID);
		}
		
		if(debug)
			Log.i("HANDLER", "OBJect: " + objID);
		
		currentOBJect.type = type;
		currentOBJect.viewID = viewID;
						
		// now let's handle the flags
		// each flag position will update the Object we just created (or referenced)
		
		// DEBUG
		if(debug)
			Log.i("HANDLER", "Flags: " + String.valueOf(flags));
		
		if((flags & ChunkFlags.Position) > 0) {
			// Position flag
			
			xPos = ChunkParser.byteToInteger(chunkData, 4, idx);
			idx += 4;
			yPos = ChunkParser.byteToInteger(chunkData, 4, idx);
			idx += 4;
			zPos = ChunkParser.byteToInteger(chunkData, 4, idx);
			idx += 4;
			
			width = ChunkParser.byteToInteger(chunkData, 4, idx);
			idx += 4;
			height = ChunkParser.byteToInteger(chunkData, 4, idx);
			idx += 4;
			
			// set the properties
			currentOBJect.xPos = xPos;
			currentOBJect.yPos = yPos;
			currentOBJect.zPos = zPos;
			currentOBJect.width = width;
			currentOBJect.height = height;
		}
		
		if((flags & ChunkFlags.BasePoint) > 0) {
			// BasePoint flag
			
			xFP = ChunkParser.byteToInteger(chunkData, 4, idx);
			idx += 4;
			yFP = ChunkParser.byteToInteger(chunkData, 4, idx);
			idx += 4;
			
			// set the properties
			currentOBJect.xFP = xFP;
			currentOBJect.yFP = yFP;
		}
		
		if((flags & ChunkFlags.ImageID) > 0) {
			// ImageID flag
			
			imageID = ChunkParser.byteToLong(chunkData, 8, idx);
			idx += 8;
			
			// some boolean value to check for need to get new cache values
			imagechanged = true;
			
			// set the properties
			currentOBJect.imageID = imageID;
		}
		
		if((flags & ChunkFlags.State) > 0) {
			// State Flag
			
			state = ChunkParser.byteToInteger(chunkData, 4, idx);
			idx += 4;
			
			// some boolean value to check for need to get new cache values
			statechanged = true;
			
			// set the properties
			currentOBJect.state = state;
		}
		
		if((flags & ChunkFlags.StateLoop) > 0) {
			// StateLoop Flag

			stateLoopSize = ChunkParser.byteToInteger(chunkData, 4, idx);
			idx += 4;
			
			// set the properties
			currentOBJect.stateLoopSize = stateLoopSize;
			
			// only create stateLoop if the stateLoopSize is > 0
			if(stateLoopSize != 0) {
				stateLoop = new int[stateLoopSize][2];
						
				for(int i = 0; i < stateLoopSize; i++) {
					stateLoop[i][0] = ChunkParser.byteToInteger(chunkData, 4, idx);
					idx += 4;
					stateLoop[i][1] = ChunkParser.byteToInteger(chunkData, 4, idx);
					idx += 4;
				}
				
				// set the properties
				currentOBJect.stateLoop = stateLoop;
				
				// and if is a new stateLoop, then begin new animation
				// okay, that's no good programming style, but isLocal() is needed as stateLoop will not differ after setting it locally before receiving OBJI
				if(!stateLoop.equals(currentOBJect.stateLoop) || currentOBJect.isLocal()) {
					currentOBJect.nextStateTimed = false;
					currentOBJect.nextStateLoop = 0;
				}
			} else {
				// remove TimerTasks
				if(currentOBJect.animationTimerTask != null)
					currentOBJect.animationTimerTask.cancel();
				
				AppActivity.animationSet.remove(currentOBJect);
				
				// and set nextStateTimed to true, so there won't be any more animation
				currentOBJect.nextStateTimed = true;
				
				// and set the state to the estimated stateLoopEndState
				if(currentOBJect.isLocal())
					currentOBJect.state = AppActivity.mAppView.stateLoopEndState;
			}
		}
		
		// now all properties are set, we should validate the OBJect
		
		if(statechanged || imagechanged) {
			// let's check if the image is inside the imageCache
			currentOBJect.bitmap = AppActivity.imageCache.get(String.valueOf(currentOBJect.imageID) + "_" + String.valueOf(currentOBJect.state));
			
			// if the Bitmap is not available, put the current object ID into the Set of OBJects waiting for a RIMG response
			if(currentOBJect.bitmap == null) {
				// check if there is already sent out a RIMG message for this image
				objSet = AppActivity.rimgMessageMap.get(currentOBJect.imageID);
				
				if(objSet == null) {
					objSet = new HashSet<OBJect>();
					AppActivity.rimgMessageMap.put(currentOBJect.imageID, objSet);
					sendRIMG(currentOBJect.imageID);
										
					// DEBUG
					if(debug)
						Log.i("HANDLER", "RIMG sent for image ID " + String.valueOf(currentOBJect.imageID));
				}
				
				// add the current object ID to the Set
				objSet.add(currentOBJect);
	
				// invalide the OBJect
				currentOBJect.invalidate();
			} else {
				// validate the OBJect
				currentOBJect.validate();
			}
		} else if(flags == 0) {
			// remove the OBJect when flags = 0 and invalidate it for next draw
			currentOBJect.invalidate();
			AppActivity.objMap.remove(currentOBJect);
		} else {	
			// validate the OBJect
			currentOBJect.validate();
		}
		
		// let's tell our Activity, there's something new to draw
		AppActivity.viewUpdateNeeded = true;
	}
	
	/**
	 * Handles CIMG messages and caching for received images
	 * 
	 * @param chunkData - the chunk data ByteArray
	 */
	public void handleCIMG(byte[] chunkData) {
		
		// initialize the current position inside the ByteArray
		int idx = 0;
		
		// split the ByteArray to the specified data fields
		long imageID = ChunkParser.byteToLong(chunkData, 8, idx);
		idx += 8;
		
		if(debug)
			Log.i("HANDLER", "IMGD received for image ID " + String.valueOf(imageID));
				
		int imageSize = ChunkParser.byteToInteger(chunkData, 4, idx);
		idx += 4;
		
		// based on http://www.droidnova.com/2d-sprite-animation-in-android-addendum,505.html
		BitmapFactory.Options imageDataOptions = new BitmapFactory.Options(); 
		imageDataOptions.inPurgeable = true;
		imageDataOptions.inInputShareable = true;
		
		Bitmap imageData = BitmapFactory.decodeByteArray(chunkData, idx, imageSize, imageDataOptions);
		idx += imageSize;
		
		int stateXCount = ChunkParser.byteToInteger(chunkData, 4, idx);
		idx += 4;
		int stateYCount = ChunkParser.byteToInteger(chunkData, 4, idx);
		idx += 4;
		
		// handle NullPointerException when images data is corrupted (first occurence noticed on 11.12.2012)
		if(imageData == null)
			return;
		
		// DEBUG
		//AppActivity.imageCache.put(String.valueOf(imageID), imageData);

		// get the dimensions of the Bitmap
		int imageWidth = imageData.getWidth();
		int imageHeight = imageData.getHeight();
				
		// initialize the current state and a BitmapArray
		int currentState = 0;
		Bitmap[] imageDataProcessed = new Bitmap[stateXCount*stateYCount];
		
		// now let's cut the image inside it's states
		for(int i = 0; i < stateYCount; i++){
			for(int j = 0 ; j < stateXCount; j++) {
				// create a Bitmap
				imageDataProcessed[currentState] = Bitmap.createBitmap(imageData, (imageWidth*j)/stateXCount, (imageHeight*i)/stateYCount, imageWidth/stateXCount, imageHeight/stateYCount);

				// let's put this into the image cache now!
				// caching algorithms handles that the image will not be inserted, if it's already inside (or updated, if the image ID is equal but image data differs)
				AppActivity.imageCache.put(String.valueOf(imageID) + "_" + String.valueOf(currentState), imageDataProcessed[currentState]);

				// recycle current state's bitmap
				imageDataProcessed[currentState].recycle();
				
		 	    // count up the state, so the images will be counted line by line, left to right
				currentState++;
			}
		}
		
		// recycle the imageData
		imageData.recycle();
		
		// we need to delete it from the rimgMessageList now
		// and check if there are OBJects that were waiting for the image
		objSet = AppActivity.rimgMessageMap.remove(imageID);
		if(objSet != null) {
			// there seems to be a Set, so let's iterate over it and validate all referenced OBJects
			for(OBJect obj : objSet) {
				obj.bitmap = AppActivity.imageCache.get(String.valueOf(obj.imageID) + "_" + String.valueOf(obj.state));
				obj.validate();
			}
		}
	}
	
	/**
	 * Handles GERR messages (only raw output of the error message)
	 * 
	 * @param chunkData - the chunk data ByteArray
	 */
	public void handleGERR(byte[] chunkData) {
		// decode bytes as specified inside the protocol
		int errorCode = ChunkParser.byteToInteger(chunkData, 4, 0);
		int errorMsgLength =  ChunkParser.byteToInteger(chunkData, 4, 4);

		// displayDebugHelpers will be true, if activated inside Credits
		if(AppActivity.settings.getBoolean("displayDebugHelpers", false))
			AppActivity.toast("Error " + String.valueOf(errorCode) + ": " + ChunkParser.byteToString(chunkData, errorMsgLength, 8));
		
		// DEBUG
		// let's log GERR messages by default for speeding up debugging
		// if(debug)
			Log.i("HANDLER", "Error " + String.valueOf(errorCode) + ": " + ChunkParser.byteToString(chunkData, errorMsgLength, 8));
	}
	
	/**
	 * Handles MSGD messages received from other users
	 * 
	 * @param chunkData - the chunk data ByteArray
	 */
	public void handleMSGD(byte[] chunkData) {
		// handle incoming chat messages
		
		int idx = 0;
		long objID = ChunkParser.byteToLong(chunkData, 8, idx);
		idx += 8;
		int messageLength = ChunkParser.byteToInteger(chunkData, 4, idx);
		idx += 4;
		final String message = ChunkParser.byteToString(chunkData, messageLength, idx);
			
		// Play "Pop" sound with new incoming messages
		mMediaPlayer = MediaPlayer.create(AppActivity.mContext, R.raw.chat_pop_sound);
		mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {

			@Override
			public void onCompletion(MediaPlayer mMediaPlayer) {
				// if sound was played, free resources
				mMediaPlayer.release();
			}
		});   
		mMediaPlayer.start();
		
		final OBJect currentOBJect = AppActivity.objMap.get(objID);
		currentOBJect.messageQueue.add(message);
		
		AppActivity.mMessageTimeoutTimer.schedule(new TimerTask() {
			@Override
			public void run() {
		        currentOBJect.messageQueue.remove(message);
		        AppActivity.viewUpdateNeeded = true;
			}
		}, Settings.messageDisplayLength*currentOBJect.messageQueue.size()); // messag will last for 20s on the screen, then will be deleted from Queue
		// and for each additional message inside the queue, the timeout needs to be even 20s longer!

		// Toast is nice, but not needed any more as we have speech bubbles now
		//AppActivity.toast("Message received: \"" + message + "\"", Toast.LENGTH_LONG);
		
		// DEBUG
		// the LogCat output will be displayed additionally to react for messages received when the emulator is not opened up all the time
		// if(debug)
			Log.i("CHAT", "Message from " + String.valueOf(objID) + " received: \"" + message + "\"");
	}
}
