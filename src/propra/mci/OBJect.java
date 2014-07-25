package propra.mci;

import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.zip.CRC32;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * The class OBJect is used for Objects of the game map (user avatars and tiles), so that they can be updated threadsafe 
 */
public class OBJect implements Comparable<OBJect> {
	
	// DEBUG
	private boolean debug = false;

	private boolean isLocal;
	
	public final long objectID;
	public int type = 0;
	public int viewID = 0;
	public int xPos = 0;
	public int yPos = 0;
	public int zPos = 0;
	public int height = 0;
	public int width = 0;
	public int xFP = 0;
	public int yFP = 0;
	public volatile long imageID = 0;
	public volatile int state = 0;
	public volatile int stateLoopSize = 0;
	public volatile int[][] stateLoop = null;
	
	private String localID;
	private String localImageID;
	
	public double speedX = 0;
	public double speedY = 0;
	
	// 1px border as C-Box?
	public int cBoxLeft = 1;
	public int cBoxRight = 1;
	public int cBoxTop = 1;
	public int cBoxBottom = 1;
	public double cFactor = 0.10; // 0.10 or 0.05 or even 0?
	
	public volatile Bitmap bitmap = null;
	
	// needed for ROBJ timeout messages
	public volatile int timeout = 0;
	public volatile int countROBJtries = 0;

	// let's set this to save comparison costs
	public volatile boolean nextStateTimed = false;
	public volatile int nextStateLoop = 0;

	public volatile TimerTask animationTimerTask;
	
	// a Deque for handling messages from each user OBJect
	public volatile LinkedBlockingDeque<String> messageQueue = null;

	/**
	 * @param objectID - the object ID of the new created OBJect
	 */
	OBJect(long objectID) {
		this.objectID = objectID;
		this.isLocal = false;
	}
	
	/**
	 * Constructor overloaded to create local OBJects too
	 * 
	 * @param objID - a local object ID as String representation
	 */
	OBJect(String objID, String username, String imageID) {
		
		// create CRC checksum of the username
		CRC32 mCRC32 = new CRC32();
		mCRC32.update(username.getBytes());
		int usernameCRC = (int) mCRC32.getValue();
		
		// create global ID for local OBJect
		this.objectID = ChunkParser.byteToLong(ChunkParser.concatByteArrays(objID.getBytes(), ChunkParser.intToByteArray(usernameCRC)), 8, 0);
		this.isLocal = true;
		this.localID = objID;
		this.localImageID = imageID;
	}
	
	/**
	 * Overwrites compareTo, so that OBJects will be sorted in a proper way (by x, y and z coordinate)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(OBJect mOBJect) {
		if(zPos == mOBJect.zPos) {
			if((yPos + yFP) == (mOBJect.yPos + mOBJect.yFP)) {
				if((xPos + xFP) == (mOBJect.xPos + mOBJect.xFP)) {
					if(((int) objectID) == ((int) mOBJect.objectID)) {
						return ((int) objectID >>> 32) - ((int) mOBJect.objectID >>> 32);
					}
					return ((int) objectID) - ((int) mOBJect.objectID);
				}
				return (xPos + xFP) - (mOBJect.xPos + mOBJect.xFP);
				}
			return (yPos + yFP) - (mOBJect.yPos + mOBJect.yFP);
		}
		return (zPos - mOBJect.zPos);
	}
	
	/**
	 * Need to overwrite equals() for finding and removing from Sets
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object mOBJect) {
		if(!(mOBJect instanceof OBJect)) 
			return false;
		return objectID == ((OBJect) mOBJect).objectID;
	}
	
	/**
	 * Fix for bug: sometimes avatars will stay inside HashMap even when they are not on the server anymore
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
    public int hashCode() {
		// create CRC checksum of the objectID and return it
		CRC32 mCRC32 = new CRC32();
		mCRC32.update(ChunkParser.longToByteArray(objectID));
		return (int) mCRC32.getValue();
	}
	
	/**
	 * @return true, if OBJect is created locally
	 */
	public boolean isLocal() {
		return isLocal;
	}
	
	/**
	 * @return the 4 Byte String representation of the local object ID
	 */
	public String getLocalID() {
		return localID;
	}
	
	/**
	 * @return the 4 Byte String representation of the local image ID
	 */
	public String getLocalImageID() {
		return localImageID;
	}
		
	/**
	 * Invalidates OBJects, e.g. when a user's avatar timed out
	 */
	public void invalidate() {
		
		// delete the OBJect from userSet
		// Note: will be type == 0, even if it's a user-created Object, but doesn't matter because remove() will not throw any Exceptions
		AppActivity.userSet.remove(this);
		
		// remove from animation set
		AppActivity.animationSet.remove(this);
		
		// let's delete it from our OBJect Set
		AppActivity.objOrderSet.remove(this);
				
		// DEBUG
		if(debug)
			Log.i("OBJect", String.valueOf(objectID) + " invalidated");
	}
	
	/**
	 * Validates an OBJect (done, when the image data is completed)
	 */
	public void validate() {

		// let's add the OBJect to our OBJect Set
		AppActivity.objOrderSet.remove(this); // if already inside, first remove to sort in at the right position again
		AppActivity.objOrderSet.add(this);
		
		// let's handle a Set with chatable user OBJects (and Dr. Who's TARDIS)
		AppActivity.userSet.remove(this);
		if(type == 1) {
			AppActivity.userSet.add(this);
			
			if(messageQueue == null)
				messageQueue = new LinkedBlockingDeque<String>();
		}
		
		AppActivity.animationSet.remove(this);
		if(stateLoopSize != 0 && stateLoop != null)
			AppActivity.animationSet.add(this);
				
		// reset timer
		timeout = 0;
		countROBJtries = 0;
		
		// DEBUG
		if(debug)
			Log.i("OBJect", String.valueOf(objectID) + " validated");
	}
}
