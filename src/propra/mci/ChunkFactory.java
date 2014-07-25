package propra.mci;

abstract class ChunkID extends ChunkHandler {
	
	public byte[] chunkData;
	
	/**
	 * @return a String representation of the chunk ID
	 */
	abstract String getChunkIDString();
	
	/**
	 * The default action handler for incoming messages
	 * 
	 * @param chunkData - a ByteArray of received chunk data
	 */
	abstract void action();

}

class GACK extends ChunkID {
	@Override
	public String getChunkIDString() {
    	return "GACK";
	}

	@Override
	public void action() {
		// the chunkData for a message of type GACK is the chunk ID of the corresponding message
		// so we can simply convert the whole 4 Bytes to a single String
		String chunkString = ChunkParser.byteToString(chunkData);
	
		// now let's check for common used GACK messages
		if(chunkString.equals("HELO")) {
			// handle GACK messages for HELO
			// if server responds with a GACK with chunkString HELO, we need to authorize first
			
			sendAUTH();
			
		} else if(chunkString.equals("AUTH")) {
			// handle GACK messages sent for AUTH
			// if server responds with a GACK with chunkString AUTH, the authorization was successful
		
		    // delete old avatar first (will not result in GERR!)
			sendRSAR();

			// let's create a view
			// initial CVIE will be sent with some default fallback values, because intialization will always fail if not inside the View
			sendCVIE(Settings.viewID, Settings.viewXPos, Settings.viewYPos, Settings.fallBackWidth, Settings.fallBackHeight);

		} else if(chunkString.equals("CIMG")) {
			// handle GACK messages sent for CIMG
			
			// let's prefetch our avatar
			sendRIMG(Settings.avatarID, Settings.username);
						
		} else if(chunkString.equals("RIMG")) {
			// handle GACK messages sent for RIMG

			/** Do anything useful here ;-) */
					
		} else if(chunkString.equals("DIMG")) {
			// handle GACK messages sent for DIMG

			/** TODO: Do anything useful here ;-) */
					
		} else if(chunkString.equals("CVIE")) {
			// handle GACK messages sent for CVIE
			
			/** TODO: Do anything useful here ;-) */

		} else if(chunkString.equals("COBJ")) {
			// handle GACK messages sent for COBJ
			
			// now the avatar was set successfully
			AppActivity.initialAvatarPositioned = true;
						
			// let's make a Toast
			// DEBUG
			if(Settings.debug)
				AppActivity.toast("Avatar successfully created.");
			
		} else if(chunkString.equals("UOBJ")) {
			// handle GACK messages sent for UOBJ
			
			/** TODO: Do anything useful here ;-) */
			
		} else if(chunkString.equals("DOBJ")) {
			// handle GACK messages sent for DOBJ
			
			// avatar deleted, now let's set enable setting the avatar again
			AppActivity.initialAvatarPositioned = false;
			
			// let's make a Toast
			// DEBUG
			if(Settings.debug)
				AppActivity.toast("Avatar successfully deleted.");
			
		} else if(chunkString.equals("SMSG")) {
			// handle GACK messages sent for SMSG
			
			/** TODO: Do anything useful here ;-) */
			
			// let's make a Toast
			// DEBUG
			if(Settings.debug)
				AppActivity.toast("Message successfully delivered.");
		}
	}
}

class GERR extends ChunkID {
	@Override
	public String getChunkIDString() {
    	return "GERR";
	}

	@Override
	public void action() {	
		// let's handle GERR data in an own method
		handleGERR(chunkData);
	}
}

class OBJI extends ChunkID {
	@Override
	public String getChunkIDString() {
    	return "OBJI";
	}

	@Override
	public void action() {		
		// let's handle OBJI data in an own method
		handleOBJI(chunkData);
	}
}

class IMGD extends ChunkID {
	@Override
	public String getChunkIDString() {
    	return "IMGD";
	}

	@Override
	public void action() {
		// let's handle CIMG data in an own method
		handleCIMG(chunkData);	
	}
}

class MSGD extends ChunkID {
	@Override
	public String getChunkIDString() {
    	return "MSGD";
	}

	@Override
	public void action() {
		// let's handle MSGD data in an own method
		handleMSGD(chunkData);
	}
}

/**
 * ChunkFactory (an implementation of the Factory method pattern)
 */
class ChunkFactory {

	/**
	 * @param chunkID - the String representation of the chunk ID
	 * @return a new Object of the incoming message type
	 * @throws UnknownChunkIDException if the incoming chunk ID is not known to our ChunkFactory
	 */
	public static ChunkID getChunkID(String chunkID) throws UnknownChunkIDException {
		if (chunkID.equals("GACK")) {
			return new GACK();
		}
		else if(chunkID.equals("GERR")) {
			return new GERR(); 	
		}
		else if(chunkID.equals("OBJI")) {
			return new OBJI(); 	
		}
		else if(chunkID.equals("IMGD")) {
			return new IMGD(); 	
		}
		else if(chunkID.equals("MSGD")) {
			return new MSGD(); 	
		}

		// nothing matched to return, let's throw an exception
		throw new UnknownChunkIDException();
    }
}
