package propra.mci;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import android.util.Log;

/**
 * NetworkHandler interface needs to be implemented in all Activities that will use network threads
 */
interface NetworkHandler {
	/**
	 * Interface for processing incoming Byte data
	 * 
	 * @param data - the incoming Bytes
	 * @param size - size of incoming data (must be > 0)
	 */
	public void processData(byte[] data, int size);
}

/**
 * NetworkThread handles all traffic of our application so that network can't block the application view
 */
public class NetworkThread extends Thread {
	
	/**
	 * SendThread class for enqueue and send data to our server
	 */
	class SendThread extends Thread {
		private OutputStream mOutputStream;
		private volatile boolean stopRunning;
		private LinkedBlockingDeque<byte[]> dataToSend;
		
		public SendThread(OutputStream _mOutputStream) {
			mOutputStream = _mOutputStream;
			dataToSend = new LinkedBlockingDeque<byte[]>();
		}
		
		/**
		 * The run-Method for the SendThread
		 */
		@Override
		public void run() {
			stopRunning = false;
			
			while (stopRunning == false) {
				byte[] strToSend = null;
				try {
					strToSend = dataToSend.pollFirst(250L,TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					Log.w("SendThread", "InterruptedException during dataToSend.pollFirst", e);
				}
				
				if (strToSend == null)
					continue;
				
				try {
					mOutputStream.write(strToSend);
				} catch (IOException e) {
					Log.w("SendThread", "IOException during mOutputStream.write", e);
				}
			}
		}
		
		/**
		 * Stops the thread, so that closing our application will terminate all threads
		 */
		public void stopThread() {
			stopRunning = true;
			
			try {
				join();
			} catch (InterruptedException e) {
				Log.w("SendThread", "InterruptedException during join", e);
			}
		}
		
		/**
		 * Appends data to send into the dataToSend queue
		 * @param bytes - ByteArray to send
		 */
		public void sendData(byte[] bytes) {
			
			try {
				dataToSend.putLast(bytes);
			} catch (InterruptedException e) {
				Log.w("SendThread", "InterruptedException during dataToSend.putLast", e);
			}
		}
	}

	private volatile boolean stopRunning;
	
	private String mHostName;
	private int	   mPortNr;
	
	private NetworkHandler mNetworkHandler;
	private SendThread mSendThread;

	private Socket mSocket;
	private InputStream iStream;
	private OutputStream oStream;
	
	public NetworkThread(String _mHostName, int _mPortNr, NetworkHandler _mNetworkHandler) {
		mHostName = _mHostName;
		mPortNr = _mPortNr;
		mNetworkHandler = _mNetworkHandler;
		mSendThread = null;
	}
	
	public void run() {
		stopRunning = false;
		
		mSocket = null;
		iStream = null;
		oStream = null;
		
		try {
			mSocket = new Socket(mHostName,mPortNr);
			iStream = mSocket.getInputStream();
			oStream = mSocket.getOutputStream();
			mSocket.setSoTimeout(500);
		} catch (UnknownHostException e) {
			Log.w("NetworkThread", "UnknownHostException during mSocket Handling", e);
		} catch (IOException e) {
			Log.w("NetworkThread", "IOException during mSocket Handling", e);
		}
		
		if (oStream != null) {
			mSendThread = new SendThread(oStream);
			mSendThread.start();
		}

		byte[] buffer = new byte[4096];
		
		while((stopRunning == false) && (mSocket != null) && (mSocket.isConnected())) {
			try {
				int bytesWritten = iStream.read(buffer);
				if (bytesWritten > 0) {
					mNetworkHandler.processData(buffer, bytesWritten);
				}
				if (bytesWritten <= 0) {
					break;
				}
			} catch (SocketTimeoutException e) {
				// if socket times out, just do nothing than continue the while loop...
			} catch (IOException e) {
				Log.w("NetworkThread", "IOException during mNetworkHandler.processData", e);
				break;
			}
		}
		
		// now stop the thread and destroy the socket connection
		if(mSendThread != null)
			mSendThread.stopThread();
		
		if ((mSocket != null) && (mSocket.isConnected())) {
			try {
				mSocket.close();
			} catch (IOException e) {
				Log.w("NetworkThread", "IOException during mSocket.close", e);
			}
		}
	}
	
	/**
	 * Stops the thread, so that closing our application will terminate all threads
	 */
	public void stopThread() {
		stopRunning = true;
		
		try {
			join();
		} catch (InterruptedException e) {
			Log.w("NetworkThread", "InterruptedException during join", e);
		}
	}
	
	/**
	 * Is used to check if the socket connection is yet alive
	 * @return true, if socket is connected
	 */
	public boolean isConnected() {
		if (mSocket.isConnected())
			return true;

		return false;
	}
	
	/**
	 * Uses sendData method of current SendThread to send
	 * @param bytes - ByteArray with bytes to send
	 */
	public void sendData(byte[] bytes) {
		if (mSendThread != null) {
			mSendThread.sendData(bytes);
		}
	}
}
