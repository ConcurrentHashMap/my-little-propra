package propra.mci;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

class SendHandler implements ChunkFlags {
	/**
	 * Send wrapper for sending a HELO message
	 */
	void sendHELO() {
        // create and send initial HELO message
		byte[] HELO = ChunkParser.buildMessage("HELO", null);
		AppActivity.mNetworkThread.sendData(HELO);
	}
	
	/**
	 * Send wrapper for sending an AUTH message
	 */
	void sendAUTH() {
		byte[] usernameByte = Settings.username.getBytes();
		byte[] passwordByte = Settings.password.getBytes();
		byte[] usernameLengthByte = new byte[4];
		byte[] passwordLengthByte = new byte[4];
		
		ChunkParser.addToByteArray(usernameByte.length, usernameLengthByte, 0);
		ChunkParser.addToByteArray(passwordByte.length, passwordLengthByte, 0);

		byte[] authChunkData = ChunkParser.concatByteArrays(usernameLengthByte, usernameByte, passwordLengthByte, passwordByte);
		
		// create and send AUTH message
		byte[] AUTH = ChunkParser.buildMessage("AUTH", authChunkData);
		AppActivity.mNetworkThread.sendData(AUTH);
	}
	
	/**
	 * Send wrapper for sending local created OBJects to the server
	 * 
	 * @param obj a reference to a local created OBJect
	 */
	void sendCOBJ(OBJect obj) {
		
		// ignore if object is not created locally
		if(obj.isLocal()) {
			
			// create the cobjChunkData
			byte[] cobjChunkData = ChunkParser.concatByteArrays(
				obj.getLocalID().getBytes(), 
				ChunkParser.intToByteArray(obj.type), 
				ChunkParser.intToByteArray(obj.xPos), 
				ChunkParser.intToByteArray(obj.yPos), 
				ChunkParser.intToByteArray(obj.zPos),
				ChunkParser.intToByteArray(obj.width),
				ChunkParser.intToByteArray(obj.height),
				ChunkParser.intToByteArray(obj.xFP),
				ChunkParser.intToByteArray(obj.yFP),
				obj.getLocalImageID().getBytes(),
				ChunkParser.intToByteArray(obj.state),
				ChunkParser.intToByteArray(obj.stateLoopSize),
				ChunkParser.intMultidimensionalArrayToByteArray(obj.stateLoop),
				ChunkParser.intToByteArray(obj.cBoxLeft),
				ChunkParser.intToByteArray(obj.cBoxRight),
				ChunkParser.intToByteArray(obj.cBoxTop),
				ChunkParser.intToByteArray(obj.cBoxBottom),
				ChunkParser.doubleToByteArray(obj.cFactor)
			);

			// create and send COBJ message
			byte[] COBJ = ChunkParser.buildMessage("COBJ", cobjChunkData);
			AppActivity.mNetworkThread.sendData(COBJ);
		}
	}
	
	void sendUOBJ(OBJect obj, int flags) {
		
		// ignore if object is not created locally
		if(obj.isLocal()) {
						
			// create the uobjChunkData
			byte[] uobjChunkData = ChunkParser.concatByteArrays(obj.getLocalID().getBytes(), ChunkParser.intToByteArray(flags));
		
			byte[] flags1 = new byte[12];
			if((flags & ChunkFlags.Position) > 0) {
				// Position flag
				ChunkParser.addToByteArray(obj.xPos, flags1, 0);
				ChunkParser.addToByteArray(obj.yPos, flags1, 4);
				ChunkParser.addToByteArray(obj.zPos, flags1, 8);
				
				uobjChunkData = ChunkParser.concatByteArrays(uobjChunkData, flags1);
			}
			
			byte[] flags2 = new byte[8];
			if((flags & ChunkFlags.BasePoint) > 0) {
				// BasePoint flag
				ChunkParser.addToByteArray(obj.xFP, flags2, 0);
				ChunkParser.addToByteArray(obj.yFP, flags2, 4);
				
				uobjChunkData = ChunkParser.concatByteArrays(uobjChunkData, flags2);
			}
			
			byte[] flags4 = new byte[4];
			if((flags & ChunkFlags.ImageID) > 0) {
				// ImageID flag
				flags4 = obj.getLocalImageID().getBytes();
				
				uobjChunkData = ChunkParser.concatByteArrays(uobjChunkData, flags4);
			}
			
			byte[] flags8 = new byte[4];
			if((flags & ChunkFlags.State) > 0) {
				// State Flag
				ChunkParser.addToByteArray(obj.state, flags8, 0);
				
				uobjChunkData = ChunkParser.concatByteArrays(uobjChunkData, flags8);
			}
			
			byte[] flags16 = new byte[obj.stateLoopSize*8 + 4];
			if((flags & ChunkFlags.StateLoop) > 0) {
				// StateLoop Flag
				byte[] stateLoopSize = new byte[4];
				ChunkParser.addToByteArray(obj.stateLoopSize, stateLoopSize, 0);
				
				// now create the ByteArray
				flags16 = ChunkParser.concatByteArrays(stateLoopSize, ChunkParser.intMultidimensionalArrayToByteArray(obj.stateLoop));
				
				uobjChunkData = ChunkParser.concatByteArrays(uobjChunkData, flags16);
			}
			
			byte[] flags32 = new byte[16];
			if((flags & ChunkFlags.Speed) > 0) {
				// Speed Flag
				flags32 = ChunkParser.concatByteArrays(ChunkParser.doubleToByteArray(obj.speedX), ChunkParser.doubleToByteArray(obj.speedY));
			
				uobjChunkData = ChunkParser.concatByteArrays(uobjChunkData, flags32);
			}
			
			byte[] flags64 = new byte[16];
			if((flags & ChunkFlags.ColBox) > 0) {
				// C-Box Flag
				ChunkParser.addToByteArray(obj.cBoxLeft, flags64, 0);
				ChunkParser.addToByteArray(obj.cBoxRight, flags64, 4);
				ChunkParser.addToByteArray(obj.cBoxTop, flags64, 8);
				ChunkParser.addToByteArray(obj.cBoxBottom, flags64, 12);
				
				uobjChunkData = ChunkParser.concatByteArrays(uobjChunkData, flags64);
			}
			
			byte[] flags128 = new byte[8];
			if((flags & ChunkFlags.ColFactor) > 0) {
				// C-Factor Flag	
				flags128 = ChunkParser.doubleToByteArray(obj.cFactor);
				
				uobjChunkData = ChunkParser.concatByteArrays(uobjChunkData, flags128);
			}
			
			// create and send UOBJ message
			byte[] UOBJ = ChunkParser.buildMessage("UOBJ", uobjChunkData);
			AppActivity.mNetworkThread.sendData(UOBJ);
		}
	}
	
	/**
	 * Send wrapper for ROBJ message (to request OBJI messages for known object IDs)
	 * 
	 * @param objID - a 8 Byte long containing the global object ID
	 */
	void sendROBJ(long objID) {
		// create and send ROBJ message
		byte[] ROBJ = ChunkParser.buildMessage("ROBJ", ChunkParser.longToByteArray(objID));
		AppActivity.mNetworkThread.sendData(ROBJ);
	}
	
	/**
	 * Send wrapper for sending DOBJ messages
	 * 
	 * @param objID - the local object ID as String representation
	 */
	void sendDOBJ(String objID) {
		// create and send DOBJ message
		byte[] DOBJ = ChunkParser.buildMessage("DOBJ", objID.getBytes());
		AppActivity.mNetworkThread.sendData(DOBJ);
	}
	
	/**
	 * Send wrapper for sending CIMG messages
	 * 
	 * @param imageID - a 4 Byte long String as local image ID
	 * @param imageRes - the image resource
	 * @param imageXCount - how many states in X direction
	 * @param imageYCount - how many states in Y direction
	 */
	void sendCIMG(String imageID, int imageRes, int imageXCount, int imageYCount) {
		
		// let's upload our user avatar
		// get the Bitmap from resources
	    Bitmap image = BitmapFactory.decodeResource(AppActivity.mContext.getResources(), imageRes);
	    ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
	    image.compress(Bitmap.CompressFormat.PNG, 0, imageStream);
	    byte[] imageByte = imageStream.toByteArray();
	    
	    // DEBUG
	    //Bitmap imageData = BitmapFactory.decodeByteArray(imageByte, 0, imageByte.length);
	    //AppActivity.imageCache.put(String.valueOf(imageID), imageData);
	    	    
		// create the cimgChunkData
	    byte[] cimgChunkData = ChunkParser.concatByteArrays(imageID.getBytes(), ChunkParser.intToByteArray(imageByte.length), imageByte, ChunkParser.intToByteArray(imageXCount), ChunkParser.intToByteArray(imageYCount));

		// create and send CIMG message
		byte[] CIMG = ChunkParser.buildMessage("CIMG", cimgChunkData);
		AppActivity.mNetworkThread.sendData(CIMG);
	}
	
	/**
	 * Alternative send wrapper for RIMG, if global image ID is unknown
	 * 
	 * @param imageID - the image ID that should be fetched from server
	 * @param username - the username of the user processing the image
	 */
	void sendRIMG(String imageID, String username) {
		// create CRC checksum of the username
		CRC32 mCRC32 = new CRC32();
		mCRC32.update(username.getBytes());
		int usernameCRC = (int) mCRC32.getValue();
		
		// create the rimgChunkData and wrap it over to sendRIMG(long imageID)
		byte[] rimgChunkData = ChunkParser.concatByteArrays(imageID.getBytes(), ChunkParser.intToByteArray(usernameCRC));
		sendRIMG(ChunkParser.byteToLong(rimgChunkData, 8, 0));
	}
	
	/**
	 * Sends an RIMG message
	 * 
	 * @param imageID - the image ID that should be fetched from server
	 */
	void sendRIMG(long imageID) {
		// create and send RIMG message
		byte[] RIMG = ChunkParser.buildMessage("RIMG", ChunkParser.longToByteArray(imageID));
		AppActivity.mNetworkThread.sendData(RIMG);
	}
	
	/**
	 * Send wrapper for sending DIMG message
	 * 
	 * @param imageID - the local ID of the image that should be deleted
	 */
	void sendDIMG(String imageID) {
		// create and send DIMG message
		byte[] DIMG = ChunkParser.buildMessage("DIMG", imageID.getBytes());
		AppActivity.mNetworkThread.sendData(DIMG);
	}
	
	/**
	 * Send wrapper to send a CVIE message
	 * 
	 * @param viewID - the local ID of the view
	 * @param xPos - X position of the view
	 * @param yPos - Y position of the view
	 * @param width - width of view
	 * @param height - height of view
	 */
	void sendCVIE(String viewID, int xPos, int yPos, int width, int height) {
		// create the cvieChunkData
		byte[] cvieChunkData = ChunkParser.concatByteArrays(viewID.getBytes(), ChunkParser.intToByteArray(xPos), ChunkParser.intToByteArray(yPos), ChunkParser.intToByteArray(width), ChunkParser.intToByteArray(height));

		// create and send CVIE message
		byte[] CVIE = ChunkParser.buildMessage("CVIE", cvieChunkData);
		AppActivity.mNetworkThread.sendData(CVIE);
	}
	
	/**
	 * Send wrapper to send a UVIE message
	 * 
	 * @param viewID - the local ID of the view
	 * @param xPos - new X position of the view
	 * @param yPos - new Y position of the view
	 * @param width - new width of view
	 * @param height - new height of view
	 */
	void sendUVIE(String viewID, int xPos, int yPos, int width, int height) {
		// create the uvieChunkData
		byte[] uvieChunkData = ChunkParser.concatByteArrays(viewID.getBytes(), ChunkParser.intToByteArray(xPos), ChunkParser.intToByteArray(yPos), ChunkParser.intToByteArray(width), ChunkParser.intToByteArray(height));

		// create and send UVIE message
		byte[] UVIE = ChunkParser.buildMessage("UVIE", uvieChunkData);
		AppActivity.mNetworkThread.sendData(UVIE);
	}
	
	/**
	 * Send wrapper for sending DVIE messages
	 * 
	 * @param viewID - ID of the local view that should be deleted
	 */
	void sendDVIE(String viewID) {
		// create and send DVIE message
		byte[] DVIE = ChunkParser.buildMessage("DVIE", viewID.getBytes());
		AppActivity.mNetworkThread.sendData(DVIE);
	}
	
	/**
	 * Send wrapper for SMSG messages
	 * 
	 * @param objDestinationID - the 8 Byte long as destination object ID 
	 * @param messageString - the string that should be sent to the destination
	 */
	void sendSMSG(long objDestinationID, String messageString) {
		// decode the String to a ByteArray
		// UTF-8 seems to work as well, if the chinese emulator IME would do it too ;-)
		byte[] message = messageString.getBytes();
		
		// create the smsgChunkData
		byte[] smsgChunkData = ChunkParser.concatByteArrays(Settings.objID.getBytes(), ChunkParser.longToByteArray(objDestinationID), ChunkParser.intToByteArray(message.length), message);

		// create and send SMSG message
		byte[] SMSG = ChunkParser.buildMessage("SMSG", smsgChunkData);
		AppActivity.mNetworkThread.sendData(SMSG);
	}
	
	/**
	 * Send wrapper for clearing up all user resources
	 */
	void sendRSAR() {
		// create and send a RSAR (Reset All Resources) message
		byte[] RSAR = ChunkParser.buildMessage("RSAR", null);
		AppActivity.mNetworkThread.sendData(RSAR);
	}
}