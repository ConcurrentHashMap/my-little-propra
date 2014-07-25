package propra.mci;

/**
 * Hard-coded settings for our application
 * (let's do it here, so it's easier and faster to implement)
 */
class Settings {
	// changed from interface to class, so the values needn't be final and get accessable
	
	// DEBUG
	static volatile boolean debug = false;
	
	// the following Settings can be changed from outside and needed to be volatile
	static volatile boolean dontShowTutorialAgain = false;	// Don't show tutorial again
	static volatile int avatarRes = R.drawable.spike;
	
	// HAPPY X-MAS!
	static volatile boolean xmasMode = false;
	
	// fit all screen sizes
	static volatile int fallBackWidth = 480; // WVGA800 = 800x480px
	static volatile int fallBackHeight = 800; // WVGA800 = 800x480px
	//static final int fallBackWidth = 480; // WVGA800 = 800x480px
	//static final int fallBackHeight = 800; // WVGA800 = 800x480px
	
	// user settings
	static final String username = "demo"; // removed for GitHub publishing
	static final String password = "demo"; // removed for GitHub publishing
	
	// avatar settings
	static final String objID = "QWER";  // removed for GitHub publishing
	static final String avatarID = "TZUI"; // removed for GitHub publishing 
	static final int avatarXCount = 3;
	static final int avatarYCount = 4;	
	static final int avatarZPos = 3;
	static final int avatarType = 1;
	static final int avatarWidth = 32;
	static final int avatarHeight = 32;
	static final int avatarXFP = 16;
	static final int avatarYFP = 32;
	static final int avatarState = 1;
	static final double avatarCFactor = 0.1;
	static final double avatarSpeed = 2.0;
	
	// the stateLoops for the avatar
	static final int[][][] avatarStateLoop = new int[][][] {
		{ // 0: avatar walking down
			{ 0, 500 },
	        { 2, 500 },
		},
		{ // 1: avatar walking left
	        { 3, 500 },
	        { 5, 500 },
		},
		{ // 2: avatar walking right
			{ 6, 500 },
	        { 8, 500 },
		},
		{ // 3: avatar walking up
	        { 9, 500 },
	        { 11, 500 },
		}
    };
	
	// view settings
	// set the initial view position to the upper left corner (so preloading will be easier ;-))
	static final String viewID = "UIOP"; // removed for GitHub publishing
	static final int viewXPos = 32;
	static final int viewYPos = 32;
		
	// some more random settings
	// "Der reale Bereich der Welt endet bei 5120,2560."
	static final int maxXPos = 5120;
	static final int maxYPos = 2560;
		
	// the cache size for our BufferedLRUCache
	static final int cacheSize = 1024 * 1024 * 8;
	
	// the maximum time for a user in mUserSet to get a new request
	// be careful: real length in seconds depends on how often the TimerTask in our AppActivity will be triggered
	// if the Timer will be called each 100ms, a value of 50 here will equal a 5s timeout length
	static final int maxUserSetTimeout = 100;
	static final int maxROBJtries = 3;
	
	// length for displaying messages inside speech bubbles
	static final int messageDisplayLength = 20000; // 20 seconds
	
	// HAPPY X-MAS!
	static final int snowFlakeCount = 150;
}