package propra.mci;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import android.util.Log;

/**
 * ChunkParser for handling incoming messages
 */
public class ChunkParser implements NetworkHandler {
	
	// DEBUG (was too lazy to change every debug to Settings.debug ;-))
	//private boolean debug = Settings.debug;
	private boolean debug = false;
	
	private final static int PPCH = 0x50504348;
	private final static int EMPTY_DATA_CRC = 0x00000000;
	
	enum Chunks { StartID, ChunkID, ChunkSize, HeaderCRC, ChunkData, DataCRC }
	private Chunks state;

	private int currentStartID;
	private int currentChunkID;
	private int currentChunkSize;
	private int currentHeaderCRC;
	private int currentDataCRC;
	
	private int currentStartIDByteCount;
	private int currentChunkIDByteCount;
	private int currentChunkSizeByteCount;
	private int currentHeaderCRCByteCount;
	private int currentChunkDataByteCount;
	private int currentDataCRCByteCount;
	
	private ChunkID currentChunkIDHandler;
	private byte[] currentChunkData;

	public ChunkParser() {
		state = Chunks.StartID;
		
		currentStartID = 0;
		currentChunkID = 0;
		currentChunkSize = 0;
		currentHeaderCRC = 0;
		currentDataCRC = 0;	
		
		currentStartIDByteCount = 0;
		currentChunkIDByteCount = 0;
		currentChunkSizeByteCount = 0;
		currentHeaderCRCByteCount = 0;
		currentChunkDataByteCount = 0;
		currentDataCRCByteCount = 0;
	}
	
	/**
	 * Implementation of processData
	 * 
	 * @see propra.mci.NetworkHandler#processData(byte[], int)
	 */
	@Override
	public void processData(byte[] data, int size) {
		if (size <= 0)
			return;

		int idx = 0;
		int prev_idx = 0;
		
		boolean needMoreData = false;
		
		while (needMoreData == false) {
			switch(state) {
				case StartID : {
					// check if the StartID is valid
					
					while((idx < size) && ((currentStartIDByteCount++ < 4) || (currentStartID != PPCH)))  {		
						int value = data[idx++];
						currentStartID = (currentStartID << 8) | (value & 0xFF);
					}
					
					if (currentStartID == PPCH) {
						state = Chunks.ChunkID;
						currentChunkID = 0;
						currentChunkIDByteCount = 0;
						prev_idx = idx;
					} else {
						needMoreData = true;
					}
				} break;
				
				case ChunkID : {
					// now parse the ChunkID
					
					while((idx < size) && (currentChunkIDByteCount++ < 4))  {		
						int value = data[idx++];
						currentChunkID = (currentChunkID << 8) | (value & 0xFF);
					}
					
					if (currentChunkIDByteCount < 4) {
						needMoreData = true;
						continue;
					}
					
					// let's convert the currentChunkID to the strings representation, so we don't have to hex hex.
					ByteBuffer tmp = ByteBuffer.allocate(4);
					tmp.putInt(currentChunkID);
					String chunkIDString = new String(tmp.array());
					
					try {
						currentChunkIDHandler = ChunkFactory.getChunkID(chunkIDString);
					}
					catch (UnknownChunkIDException e) {
						// DEBUG
						/*
						if(debug)
							Log.i("PARSER", "ChunkID: UNKNOWN");
						*/
						state = Chunks.StartID;
						currentStartID = 0;
						currentStartIDByteCount = 0;
						idx = prev_idx;
						continue;
					}
					
					// DEBUG
					if(debug)
						Log.i("PARSER", "ChunkID: " + currentChunkIDHandler.getChunkIDString());
					
					state = Chunks.ChunkSize;
					currentChunkSize = 0;
					currentChunkSizeByteCount = 0;
				} break;
				
				case ChunkSize : {
					
					while((idx < size) && (currentChunkSizeByteCount++ < 4))  {		
						int value = data[idx++];
						currentChunkSize = (currentChunkSize << 8) | (value & 0xFF);
					}
					
					if (currentChunkSizeByteCount < 4) {
						needMoreData = true;
						continue;
					}
					
					// DEBUG
					if(debug)
						Log.i("PARSER", "ChunkSize: " + String.valueOf(currentChunkSize));

					state = Chunks.HeaderCRC;
					currentHeaderCRC = 0;
					currentHeaderCRCByteCount = 0;
				} break;
				
				case HeaderCRC : {
				
					while((idx < size) && (currentHeaderCRCByteCount++ < 4))  {		
						int value = data[idx++];
						currentHeaderCRC = (currentHeaderCRC << 8) | (value & 0xFF);
					}
					
					if (currentHeaderCRCByteCount < 4) {
						needMoreData = true;
						continue;
					}
					
					// convert to Integer representation for better comparison
					Integer receivedHeaderCRC = Integer.valueOf(currentHeaderCRC);

					// now calculate the header CRC
					byte[] header = new byte[12];
					addToByteArray(currentStartID, header, 0);
					addToByteArray(currentChunkID, header, 4);
					addToByteArray(currentChunkSize, header, 8);
					CRC32 mCRC32 = new CRC32();
					mCRC32.update(header, 0, header.length);
					
					int calculatedHeaderCRC = (int) mCRC32.getValue();

					// check if the received checksum is equals calculated checksum
					if(receivedHeaderCRC.equals(calculatedHeaderCRC)) {
						// DEBUG
						if(debug)
							Log.i("PARSER", "HeaderCRC: OK");
					} else {
						// DEBUG
						if(debug)
							Log.i("PARSER", "HeaderCRC: INVALID");
						needMoreData = true;
						continue;
					}
					
					state = Chunks.ChunkData;
					currentChunkData = new byte[currentChunkSize];
					currentChunkDataByteCount = 0;
				} break;
				
				case ChunkData : {
						
					while((idx < size) && (currentChunkDataByteCount < currentChunkSize))  {		
						currentChunkData[currentChunkDataByteCount++] = data[idx++];
					}
					
					if (currentChunkDataByteCount < currentChunkSize) {
						needMoreData = true;
						continue;
					}
					
					// DEBUG
					if(debug)
						Log.i("PARSER", "ChunkData: " + ChunkParser.byteToString(currentChunkData, currentChunkSize, 0));
					
					state = Chunks.DataCRC;
					currentDataCRC = 0;
					currentDataCRCByteCount = 0;					
				} break;
				
				case DataCRC : {
					
					while((idx < size) && (currentDataCRCByteCount++ < 4))  {		
						int value = data[idx++];
						currentDataCRC = (currentDataCRC << 8) | (value & 0xFF);
					}
					
					if (currentDataCRCByteCount < 4) {
						needMoreData = true;
						continue;
					}
					
					// convert to Integer representation for better comparison
					Integer receivedDataCRC = Integer.valueOf(currentDataCRC);

					// now calculate the data CRC
					CRC32 mCRC32 = new CRC32();
					mCRC32.update(currentChunkData, 0, currentChunkData.length);
					
					int calculatedDataCRC = (int) mCRC32.getValue();

					// check if the received checksum is equals calculated checksum
					if(receivedDataCRC.equals(calculatedDataCRC)) {
						// DEBUG
						if(debug)
							Log.i("PARSER", "DataCRC: OK");
						
						// the message is valid and complete, so lets put the chunkData in
						currentChunkIDHandler.chunkData = currentChunkData;
						putIntoHandleQueue(currentChunkIDHandler);
					} else {
						// DEBUG
						if(debug)
							Log.i("PARSER", "DataCRC: INVALID");
					}

					state = Chunks.ChunkID;
					currentStartID = 0;
					currentChunkID = 0;
					currentChunkSize = 0;
					currentHeaderCRC = 0;
					currentDataCRC = 0;	
					
					currentStartIDByteCount = 0;
					currentChunkIDByteCount = 0;
					currentChunkSizeByteCount = 0;
					currentHeaderCRCByteCount = 0;
					currentChunkDataByteCount = 0;
					currentDataCRCByteCount = 0;
										
				} break;
				
				default: {
					Log.w("ChunkParser","Unknown state during switch");
				} break;
			}
		}
	}
	
	private void putIntoHandleQueue(ChunkID chunkID) {
		AppActivity.mHandleThread.mQueue.add(chunkID);
	}

	/**
	 * Converts a ByteArray to its ASCII String representation
	 * 
	 * @param data - a ByteArray
	 * @return a (readable) String representation of a given ByteArray
	 */
	public static String byteToString(byte[] data) {
		return hexStringToString(byteToHexString(data));
	}
	
	/**
	 * Converts a ByteArray to its ASCII String representation
	 * 
	 * @param data - a ByteArray
	 * @param size - the size of the ByteArray where to convert the String
	 * @param offset - offset for reading particular Strings
	 * @return a String representation of the given ByteArray
	 */
	public static String byteToString(byte[] data, int size, int offset) {
		char[] retChar = new char[size];
		
		for(int i = 0; i < size; i++) {
			retChar[i] = (char) new Byte(data[i + offset]).intValue();
		}
		return new String(retChar);
	}
	
	/**
	 * Converts a Hex String to a ASCII String
	 * 
	 * @param data - a Hex String
	 * @return a (readable) String representation of a given Hex String
	 */
	public static String hexStringToString(String data) {
		StringBuilder retStringBuilder = new StringBuilder(data.length() / 2);
	    for (int i = 0; i < data.length(); i += 2) {
	        String str = data.substring(i, i + 2);
	        retStringBuilder.append((char)Integer.parseInt(str, 16));
	    }
		return retStringBuilder.toString();
	}
	
	/**
	 * Converts a ByteArray to a Hex String
	 * 
	 * @param data - a ByteArray
	 * @return a Hex String representation of a given ByteArray
	 */
	public static String byteToHexString(byte[] data) {
		return Integer.toHexString(ChunkParser.byteToInteger(data));
	}
	
	/**
	 * Converts a ByteArray to an Integer
	 * 
	 * @param data - a ByteArray
	 * @return the Integer representation of the given ByteArray
	 */
	public static int byteToInteger(byte[] data) {
		int retInt = 0;
		for (int i = 0; i < data.length; i++)
		{
			retInt = (retInt << 8) + (data[i] & 0xff);
		}
		return retInt;
	}

	/**
	 * Converts a ByteArray to an Integer
	 * 
	 * @param data - a ByteArray
	 * @param size - the size of the ByteArray where to convert the Integer
	 * @param offset - offset for reading particular Integers
	 * @return the Integer representation of a particular ByteArray
	 */
	public static int byteToInteger(byte[] data, int size, int offset) {
		int retInt = 0;
		for (int i = 0; i < size; i++)
		{
			retInt = (retInt << 8) + (data[i + offset] & 0xff);
		}
		return retInt;
	}
	
	/**
	 * Converts a ByteArray to a Long Integer
	 * 
	 * @param data - a ByteArray
	 * @param size - the size of the ByteArray where to convert the Long Integer
	 * @param offset - offset for reading particular Integers
	 * @return the Integer representation of a particular ByteArray
	 */
	public static long byteToLong(byte[] data, int size, int offset) {
		long retLong = 0;
		for (int i = 0; i < size; i++)
		{
			retLong = (retLong << 8) + (data[i + offset] & 0xff);
		}
		return retLong;
	}
	
	/**
	 * Converts a Long Integer to its ByteArray representation
	 * 
	 * @param longinteger - the inverse function of byteToLong
	 * @return a 8 Byte long ByteArray
	 */
	public static byte[] longToByteArray(long longInteger) {  
        ByteBuffer bb = ByteBuffer.allocate(8);  
        return bb.putLong(longInteger).array(); 
    }

	/**
	 * Returns a concatenation of a given set of ByteArrays
	 * 
	 * @param arrays - Multiple ByteArrays that should be concatenated
	 * @return a new concatenated ByteArray
	 */
	public static byte[] concatByteArrays(byte[]...arrays) {
	    
		// calculate the whole length of our new ByteArray
	    int totalLength = 0;
	    for (int i = 0; i < arrays.length; i++)
	        totalLength += arrays[i].length;
	    
	    byte[] result = new byte[totalLength];

	    // copy source arrays to result array
	    int currentIndex = 0;
	    for (int i = 0; i < arrays.length; i++) {
	        System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
	        currentIndex += arrays[i].length;
	    }
	    return result;
	}

	/**
	 * Builds a message that can be send to the server
	 * 
	 * Usage:
	 * byte[] HELO = buildMessage("HELO", null);
	 * mNetworkThread.sendData(HELO);
	 * 
	 * @param chunkID - String representation of the chunk ID of the message
	 * @param chunkData - ByteArray of chunk data to send
	 * 
	 * @return A ByteArray with built-in CRC checksums and chunk data
	 */
	public static byte[] buildMessage(String chunkID, byte[] chunkData)	{

		int length = 20; // 5*4 Bytes for standard protocol without chunk data length
		int chunkSize = EMPTY_DATA_CRC;
		
		if (chunkData != null) {
				chunkSize = chunkData.length;
				length += chunkSize;
		}
		
		ByteBuffer buffer = ByteBuffer.allocate(length);
		buffer.put("PPCH".getBytes());
		buffer.put(chunkID.getBytes());
		buffer.putInt(chunkSize);
		buffer.putInt(ChunkParser.createCRC(buffer, 12, 0)); // 3*4 Bytes length for standard protocol header
		if (chunkData != null) {
			buffer.put(chunkData);
			buffer.putInt(ChunkParser.createCRC(buffer, chunkData.length, 16)); // 16 Bytes offset for header (12 Bytes, see above) and 4 Bytes for header CRC checksum
		} else {
			buffer.putInt(EMPTY_DATA_CRC);
		}
		
		return buffer.array();
	}
	
	/**
	 * Creates a CRC checksum of a given ByteBuffer
	 * 
	 * @param buffer - the input ByteBuffer
	 * @param size - size of the ByteBuffer array representation in Bytes
	 * @param offset - offset for the CRC checksum calculation in Bytes
	 * @return
	 */
	public static int createCRC(ByteBuffer buffer, int size, int offset) {
		Checksum checksum = new CRC32();
		checksum.update(buffer.array(), offset, size);
		return (int) checksum.getValue();
	}
	
    /**
     * Converts an Integer to a ByteArray
     * 
     * @param integer - an Integer that should be converted to a single ByteArray
     * @return a 4 Byte long ByteArray representation of a given Integer value
     */
    public static byte[] intToByteArray(int integer) {
        byte[] retByte = new byte[4];
   
        retByte[0] = (byte) (integer >> 24);
        retByte[1] = (byte) (integer >> 16);
        retByte[2] = (byte) (integer >> 8);
        retByte[3] = (byte) integer;
        
        return retByte;
    }
    
    /**
     * Converts a Double to a ByteArray
     *  
     * @param doubleinteger - a 8 Byte Double Integer that should be converted to a ByteArray
     * @return a 8 Byte long ByteArray
     */
    public static byte[] doubleToByteArray(double doubleInteger) {
    	ByteBuffer bb = ByteBuffer.allocate(8);  
        return bb.putDouble(doubleInteger).array();
    }

	/**
	 * Adds an Integer to a given ByteArray
	 * 
	 * @param integer - the Integer (4 Byte) that should be added to our ByteArray
	 * @param byteArray - the ByteArray to put the Integer in
	 * @param offset - define an offset to put multiple 4 Byte Integers into one ByteArray
	 */
	public static void addToByteArray(int integer, byte[] byteArray, int offset) {
	    byteArray[offset] = (byte) ((integer >> 24) & 0xFF);
	    byteArray[offset+1] = (byte) ((integer >> 16) & 0xFF);
	    byteArray[offset+2] = (byte) ((integer >> 8) & 0xFF);
	    byteArray[offset+3] = (byte) (integer & 0xFF);
	}

	/**
	 * Converts a multidimensional Integer Array to its ByteArray representation
	 * (needed for StateLoop Handling)
	 * 
	 * @param stateLoop - a int[][] Array as representation for the StateLoop
	 * @return a ByteArray representation
	 */
	public static byte[] intMultidimensionalArrayToByteArray(int[][] stateLoop) {
		if(stateLoop != null && stateLoop.length != 0) {
			byte[] retByte = new byte[stateLoop.length*8];
	
			for(int i = 0; i < stateLoop.length; i++) {
				addToByteArray(stateLoop[i][0], retByte, (i*8));
				addToByteArray(stateLoop[i][1], retByte, (i*8)+4);
			}
	
			return retByte;
		}
		return String.valueOf("").getBytes(); // crappy, but works. if length is 0, we need to return an empty byte[]
	}
}
