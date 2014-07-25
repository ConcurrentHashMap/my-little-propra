package propra.mci;

import java.util.LinkedList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The main activity that will be called when the application starts
 */
public class AppActivity extends Activity {
	
	public static NetworkThread mNetworkThread;
	public static HandleThread mHandleThread;
	
	// we'll need the Context to handle the path to our /cache directory
	public static Context mContext;
	
	// some self-made data structures we need to create
	public static BufferedLRUCache imageCache; // this is needed for caching images in a performant way
	public static ConcurrentHashMap<Long, OBJect> objMap; // ConcurrentHashMap for all view Objects
	public static ConcurrentSkipListSet<OBJect> objOrderSet; // a Set for the view Objects for ordering the view
	public static ConcurrentHashMap<Long, Set<OBJect>> rimgMessageMap; // a ConcurrentHashMap for already sent RIMG messages
	
	// handle chatable usersr
	public static ConcurrentSkipListSet<OBJect> userSet; // this is needed for adding users that are available for chat
	
	// and handle those, who should be animated
	public static ConcurrentSkipListSet<OBJect> animationSet;
	
	public static AppView mAppView;
	public static SendHandler mSendHandler;
	
	private ChunkParser mChunkParser;

	// create a TimerTask for recurringly redrawing the View
	private Timer mPostInvalidateTimer;
	public static Timer mMessageTimeoutTimer;
	
	// the user's avatar
	public static OBJect mAvatar;
	
	// used for notification sounds
	public static volatile MediaPlayer mMediaPlayer;
	
	// some boolean things to check for
	public static boolean initialAvatarPositioned;
	private static boolean errors;
	
	// SharedPreferences
	public static SharedPreferences settings;	
	
	// needs volatile!
	public static volatile boolean viewUpdateNeeded;
	public static volatile boolean viewStarted;
	
	// handle onBackPressed with a smart List
	private LinkedList<Object> backButtonPressedList;
	
    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
	@Override
    public void onCreate(Bundle savedInstanceState) {	
        super.onCreate(savedInstanceState);
       
        // get SharedPreferences
        settings = getSharedPreferences("MyLittleProPra", MODE_PRIVATE);
        Settings.dontShowTutorialAgain = settings.getBoolean("dontShowTutorialAgain", false);
   
        viewStarted = false;
        initialAvatarPositioned = false;
        errors = false;
            
        // we'll need the Context to handle the path to our /cache directory
        // Strange bug: mContext = this.getApplicationContext();
        // should be the correct solution, but leads to a crash each time creating a dialog box!
        // Although "this" shouldn't be a Context but an Activity, it works...
        // Pro tip found here: @link http://tech.shantanugoel.com/2010/07/08/badtokenexception-android-dialog-getapplicationcontext.html
        mContext = this;
        
        backButtonPressedList = new LinkedList<Object>();
        
    	// initialize our Maps and lists to handle OBJects and RIMG messages
    	imageCache = new BufferedLRUCache(Settings.cacheSize);
    	objMap = new ConcurrentHashMap<Long, OBJect>();
    	objOrderSet = new ConcurrentSkipListSet<OBJect>();
       	rimgMessageMap = new ConcurrentHashMap<Long, Set<OBJect>>();
       	userSet = new ConcurrentSkipListSet<OBJect>();
       	animationSet = new ConcurrentSkipListSet<OBJect>();
    	
        // initialize the ChunkParser
        mChunkParser = new ChunkParser();
        
        // initialize the ChunkHandler thread
        mHandleThread = new HandleThread();
        mHandleThread.start();
        
		// let's do some loading splash screen while the app is fetching and loading images
		setContentView(R.layout.loading_screen);
		
        // initialize the network thread
        mNetworkThread = new NetworkThread("server.example.com", 2421, mChunkParser); // removed for GitHub publishing
        mNetworkThread.start();
        
        // lets wait for the socket to get successfully connected
        int i = 0;
        
        try {
	        do {
	        	try {
	        		Thread.sleep(1000);
	        	} catch (InterruptedException e) {
	        		// nothing to do here
	        	}
	        } while ((mNetworkThread.isConnected() == false) && (i++ < 5));
        } catch(Exception e) {
        	
        	// write "No network connection" onto loading screen and stop execution!
    		TextView splashText = (TextView) findViewById(R.id.splashText);
           	splashText.setText(R.string.error_no_network_connection);
           	errors = true;
           	
        	Log.w("AppActivity", "NullPointerException during isConnected", e);
        }
        
        // create and send initial HELO message
        mSendHandler = new SendHandler();
        mSendHandler.sendHELO();

        // set the ContentView to our AppView
		mAppView = new AppView(this);
		mAppView.setHapticFeedbackEnabled(true); // let's do it like native Android apps!
		registerForContextMenu(mAppView); // Enable ability for Context menus
        
		// time out the splash screen (if there are no errors) and then switch to mAppView
		if(!errors)
	        new Handler().postDelayed(new Runnable(){
	        	
				@Override
	        	public void run() {
	        		if(Settings.dontShowTutorialAgain) {
	        			selectAvatar();
	        		} else {
		        		// set the View to our tutorial
		        		setContentView(R.layout.tutorial);
	        		}
	        	}
	        }, 5000); // = 5 seconds

		// let's create a TimerTask for updating our View
		// true for daemon thread
		mPostInvalidateTimer = new Timer(true);
		mMessageTimeoutTimer = new Timer(true);

		// and schedule the periodical postInvalidate() call
		mPostInvalidateTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				// let's get an own scheduled TimerTask to handle postInvalidate()

				// let's iterate through our userSet
				// it an object reaches the timeout limit, let's send out a ROBJ message
				for(OBJect obj : userSet) {
					if(obj.timeout >= Settings.maxUserSetTimeout) {
						// timeout reached, let's send out a ROBJ message
						if(obj.countROBJtries < Settings.maxROBJtries) {
							// and count up the ROBJtries
							mSendHandler.sendROBJ(obj.objectID);
							obj.countROBJtries++;
						} else {
							obj.invalidate();
						}

						// and reset timer here (if OBJI messages takes more than 100ms it would be resend)
						obj.timeout = 0;

						// DEBUG
						if(Settings.debug)
							Log.i("VIEW", "User " + String.valueOf(obj.objectID) + " timed out");
					} else {
						obj.timeout++;
					}
				}

				// check if there is something new to draw
				if(viewUpdateNeeded) {
					mAppView.postInvalidate();
					viewUpdateNeeded = false; // reset our boolean for the next drawing period
				}
			}
		}, 0, 100); // best effort should be 50 fps, equals a timeout of 20 ms, but 50fps takes all my CPU power, so let's try 10 fps = each 100ms, that seems slightly enough (would be different if emulator runs on Intel XEON ;-))

		// let's add a second TimerTask for our mPostInvalidateTimer
		// to handle the StateLoop animation
		mPostInvalidateTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				// let's check our animationSet
				for(OBJect obj : animationSet) {
					if(!obj.nextStateTimed) {
						// the Timer is not yet set for this object
						
						final int nextState;
						final OBJect currentOBJect = obj; // final needed to for run() method

						// if the next state is greater than the stateLoopSize, begin at 0 again
						if(currentOBJect.nextStateLoop + 1 >= currentOBJect.stateLoopSize) {
							nextState = 0;
						} else {
							nextState = currentOBJect.nextStateLoop + 1;
						}

						// create a new TimerTask for this OBJect						
						currentOBJect.animationTimerTask = new TimerTask() {
							@Override
							public void run() {
								currentOBJect.state = currentOBJect.stateLoop[nextState][0];
								currentOBJect.bitmap = imageCache.get(String.valueOf(currentOBJect.imageID) + "_" + String.valueOf(currentOBJect.state));
								currentOBJect.nextStateTimed = false;

								if(currentOBJect.bitmap != null)
									AppActivity.mAppView.postInvalidate();
							}

						};
						// and schedule again (puh, this is kind of hack...)
						mPostInvalidateTimer.schedule(currentOBJect.animationTimerTask , currentOBJect.stateLoop[currentOBJect.nextStateLoop][1]);				
						
						currentOBJect.nextStateLoop = nextState;
						currentOBJect.nextStateTimed = true;
					}
					
					// copied from above: let's set a timeout here, too
					if(!AppActivity.userSet.contains(obj)) {
						if(obj.timeout >= Settings.maxUserSetTimeout*10) {
							// timeout reached, let's send out a ROBJ message
							if(obj.countROBJtries < Settings.maxROBJtries) {
								// and count up the ROBJtries
								mSendHandler.sendROBJ(obj.objectID);
								obj.countROBJtries++;
							} else {
								obj.invalidate();
							}
							
							// and reset timer here (if OBJI messages takes more than 100ms it would be resend)
							obj.timeout = 0;
							
							// DEBUG
							if(Settings.debug)
								Log.i("VIEW", "Animated OBJect " + String.valueOf(obj.objectID) + " timed out");
						} else {
							obj.timeout++;
						}
					}
				}	
			}
		}, 0, 10); // 10ms... there won't be any chance to get better than this...
	}

	/**
	 * Destroys the current Activity and stops the network thread
	 * 
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
    protected void onDestroy() {

		// DEBUG
		if(Settings.debug)
			Log.i("AppActivity", "AppActivity destroyed.");
		
		// let's send a DVIE, DOBJ and RSAR for cleaning up the server
		mSendHandler.sendDVIE(Settings.viewID);
		mSendHandler.sendDOBJ(Settings.objID);
		mSendHandler.sendRSAR();
		
		// clean up the /cache directory
		//imageCache.clearDiskCache();
		
		// stop all media playing
		try {
			if(mMediaPlayer != null && mMediaPlayer.isPlaying()) {
				mMediaPlayer.stop();
				mMediaPlayer.release();
			}
		} catch(IllegalStateException e) {
			Log.i("MEDIA", "IllegalStateException during MediaPlayer stop.");
		}

		// stop all Threads and TimerTasks
		mPostInvalidateTimer.cancel();
		mNetworkThread.stopThread();
		
		// finally give the callback to super
		super.onDestroy();
    }
	    
    /**
     * Creates the standard options menu for Android
     * 
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }
    
    /**
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    	case R.id.menu_tutorial : {
    		// selected Tutorial 

    		// set the View to our tutorial.xml
    		setContentView(R.layout.tutorial);

    		return true;
    	}
    	case R.id.menu_credits : {
    		// selected Credits
    		
    		// set View to our credits.xml
    		setContentView(R.layout.credits);
    		
    		return true;
    	}
    	}
		return false;
    }
    
    /**
     * Overwritten for handling backButtonPressedList
     * 
     * @see android.app.Activity#setContentView(android.view.View)
     */
    public void setContentView(View v) {
    	super.setContentView(v);
    
    	// add the current View to our backButtonPressedList
    	backButtonPressedList.remove(v);
    	backButtonPressedList.add(v);
    }
    
    /**
     * Overwritten for handling backButtonPressedList
     *      
     * @see android.app.Activity#setContentView(int)
     */
    public void setContentView(int i) {
    	super.setContentView(i);
    	
    	if(i == R.layout.credits) {
    		
    		// handle CheckBox
    		CheckBox checkbox = (CheckBox) findViewById(R.id.credits_checkbox);
    		checkbox.setChecked(settings.getBoolean("displayDebugHelpers", false));
    		checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
    			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    				// save to SharedPreferences
    				SharedPreferences settings = getSharedPreferences("MyLittleProPra", MODE_PRIVATE);
    				SharedPreferences.Editor editor = settings.edit();
    				editor.putBoolean("displayDebugHelpers", isChecked);
    				editor.commit();
    			}
    		});

    	} else if(i == R.layout.tutorial) {
    		
    		// handle CheckBox
    		CheckBox checkbox = (CheckBox) findViewById(R.id.tutorial_checkbox);
    		checkbox.setChecked(settings.getBoolean("dontShowTutorialAgain", false));
    		checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
    			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    				// save to SharedPreferences
    				SharedPreferences settings = getSharedPreferences("MyLittleProPra", MODE_PRIVATE);
    				SharedPreferences.Editor editor = settings.edit();
    				editor.putBoolean("dontShowTutorialAgain", isChecked);
    				editor.commit();
    			}
    		});

    		// and when the "Continue" button will be pressed, continue to selecting a pony
    		final Button button = (Button) findViewById(R.id.button_next);
    		button.setOnClickListener(new View.OnClickListener() {
    			public void onClick(View v) {    				
    				// and begin new selection
    				selectAvatar();
    			}
    		});
    	}
    
    	// add the current View to our backButtonPressedList
    	// (but only if not the loading screen!)
    	if(i != R.layout.loading_screen) {
        	backButtonPressedList.remove((Object) i);
    		backButtonPressedList.add(i);
    	}
    }
    
    /**
     * Used to overwrite onBackPressed(), because back will normally switch between Activities,
     * but we have only View changes, so we will need to handle our own onBackPressed.
     * 
     * @see android.app.Activity#onBackPressed()
     */
    public void onBackPressed() {
    	// first delete the current View entry
       	backButtonPressedList.pollLast();
    	
       	// then check if the last entry is a View or an integer
    	if(backButtonPressedList.peekLast() == null) {
    		super.onBackPressed();
    	} else if(backButtonPressedList.peekLast() instanceof View) {
    		setContentView((View) backButtonPressedList.pollLast());
    	} else {
    		// OMG, this casting is even more horrible than DSDS!!1!
    		setContentView((Integer) backButtonPressedList.pollLast());
    	}
    }
    
    /**
     * Creates the Context menu for onLongClick actions at the avatar
     * 
     * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	getMenuInflater().inflate(R.menu.avatar_action, menu);
    	menu.setHeaderTitle("Select an Option"); 
    	menu.setHeaderIcon(R.drawable.ic_launcher); // set the icon :)
    }

	/**
	 * Wrapper method for calling the onCreateContextMenu method from the AppView
	 * 
	 * @param view - the View that requested the Context menu
	 */
	public static void OnLongClickOpenContextMenu(View view) {
		// we can't access any non-static reference here, so let's take the mContext
		// which is actually a reference onto "this", but needs to be casted to type Activity
		((Activity) mContext).openContextMenu(view);
	}
	
	/**
	 * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.avatar_action_delete : {
				// now let's send a DOBJ message
				
				// launch a simple context menu for avatar actions like sending DOBJ
				AppActivity.mSendHandler.sendDOBJ(Settings.objID);
				
				// finally, return true because we have handled the event
				return true;
			}
		}
		return super.onContextItemSelected(item);
	}

	/**
	 * Wrapper method for displaying Toast messages after successful operations
	 * (currently overloaded because most Toasts will be displayed with Toast.LENGTH_SHORT)
	 * 
	 * @param text - a String that should be displayed
	 */
	public static void toast(final String text) {
		toast(text, Toast.LENGTH_SHORT);
	}
	
	/**
	 * Wrapper method for displaying Toast messages after successful operations
	 * (currently only enabled when Settings.debug = true)
	 * 
	 * @param text - a String that should be displayed
	 * @param toastLength - a constant int for choosing the Toast display length
	 */
	public static void toast(final String text, final int toastLength) {	
		// this is a little workaround for Exception "Can't create handler inside thread that has not called Looper.prepare()"
		// Toast.makeText needs to be run from UI thread
		// mContext needs to be casted to Activity before
		((Activity) mContext).runOnUiThread(new Runnable() {
			  public void run() {
				  Toast.makeText(mContext, text, toastLength).show();
			  }
		});
	}
	
	private void selectAvatar() {
		// and build a new DialogBuilder with the currently created view
		final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(AppActivity.mContext);
		//alertDialogBuilder.setCancelable(false);
		alertDialogBuilder.setTitle("Select your Pony"); 
		alertDialogBuilder.setIcon(R.drawable.ic_launcher); // set the icon :)
		
		alertDialogBuilder.setItems(R.array.ponies, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				
				// first clear all recent OBJects and user images ;-)
				mSendHandler.sendDOBJ(Settings.objID);
				mSendHandler.sendDIMG(Settings.avatarID);
				
				// X-MAS is over :(
				Settings.xmasMode = false;

				// stop all media playing
				try {
					if(mMediaPlayer != null && mMediaPlayer.isPlaying()) {
						mMediaPlayer.stop();
						mMediaPlayer.release();
					}
				} catch(IllegalStateException e) {
					Log.i("MEDIA", "IllegalStateException during MediaPlayer stop.");
				}

				// DEBUG
				if(Settings.debug)
					Log.i("START", "Pony selection: " + String.valueOf(which));

				// now upload the selected avatar
				switch(which) {
				case 0 : {
					// Applejack
					Settings.avatarRes = R.drawable.applejack;
				} break;
				case 1 : {
					// Fluttershy
					Settings.avatarRes = R.drawable.fluttershy;
				} break;
				case 2 : {
					// Pinkie Pie
					Settings.avatarRes = R.drawable.pinkie_pie;
				} break;
				case 3 : {
					// Rainbow Dash
					Settings.avatarRes = R.drawable.rainbow_dash;
				} break;
				case 4 : {
					// Rarity
					Settings.avatarRes = R.drawable.rarity;
				} break;
				case 5 : {
					// Spike
					Settings.avatarRes = R.drawable.spike;
				} break;
				case 6 : {
					// Trixie
					Settings.avatarRes = R.drawable.trixie;
				} break;
				case 7 : {
					// Twilight Sparkle
					Settings.avatarRes = R.drawable.twilight_sparkle;
				} break;
				case 8 : {
					// HAPPY X-MAS!
					Settings.avatarRes = R.drawable.santa_claus;
					Settings.xmasMode = true;

					/** TODO: this needed to be removed because otherwise the package size would exceed 10MB...
					AppActivity.mMediaPlayer = MediaPlayer.create(AppActivity.mContext, R.raw.christmas_song);
					AppActivity.mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {

						@Override
						public void onCompletion(MediaPlayer mMediaPlayer) {
							// if sound was played, free resources
							mMediaPlayer.release();
						}
					});   
					AppActivity.mMediaPlayer.start();
					*/
				} break;
				default: {
					// there won't be any default, but who knows...
					Log.i("ACTIVITY", "Pony selection missed!");
				} break;
				}

				// now upload the selected avatar
				mSendHandler.sendCIMG(Settings.avatarID, Settings.avatarRes, Settings.avatarXCount, Settings.avatarYCount);
				setContentView(mAppView);
				
        		// okay, that's difficult to explain: I need some boolean to check inside other views if pressing back will return to Activity or finish Activity
        		viewStarted = true;
			}
		});
		
		// now create the dialog and show it
		AlertDialog alertDialog = alertDialogBuilder.create();
		
		// add a onDismissListener
		// if Dialog will be dismissed, finish Activity or return to tutorial View
		alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				if(Settings.dontShowTutorialAgain && !viewStarted) {
					AppActivity.this.finish();
				}
			}
		});
		
		alertDialog.show();	
	}
}