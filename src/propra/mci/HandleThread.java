package propra.mci;

import java.util.concurrent.LinkedBlockingDeque;

public class HandleThread extends Thread {
	
	private ChunkID chunksToProcess;
	public LinkedBlockingDeque<ChunkID> mQueue;
	
	public HandleThread() {
		setDaemon(true); // run as daemon thread, so we don't have to care about destroying it later
		mQueue = new LinkedBlockingDeque<ChunkID>();
	}
	
	/**
	 * The run-Method for the HandleThread
	 */
	public void run() {
		while(true) {
			chunksToProcess = mQueue.pollFirst();
			if(chunksToProcess != null)
				chunksToProcess.action();
		}
	}
}
