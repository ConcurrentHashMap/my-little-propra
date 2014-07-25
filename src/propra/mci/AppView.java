package propra.mci;

import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.EditText;

public class AppView extends View implements OnLongClickListener {

	// DEBUG
	private boolean debug = Settings.debug;
	
	private int screenWidth;
	private int screenHeight;
	
	private int viewXPos;
	private int viewYPos;
	private int clickX;
	private int clickY;

	private int mYPos;
	private int mXPos;

	private int calculatedXPos;
	private int calculatedYPos;

	private Paint paint;
	private Paint rectPaint;
	private TextPaint textPaint;

	private OBJect currentClickedUser; 	// the last clicked user

	public volatile int stateLoopEndState;

	// HAPPY X-MAS!
	private Bitmap snowFlakes;
	private Bitmap[] snowFlake;
	private int[] snowFlakeX;
	private int[] snowFlakeY;

	public AppView(Context context) {
		super(context);
		
		stateLoopEndState = 1; // initialize the stateLoopEndState
		
		viewXPos = Settings.viewXPos;
		viewYPos = Settings.viewYPos;
				
		screenWidth = Settings.fallBackWidth;
		screenHeight = Settings.fallBackHeight;
		
		// Paint needed for drawing borders around OBJects with type 1 for debugging
		paint = new Paint();
		paint.setStyle(Style.STROKE);
		
		// a Paint for our speech bubble
		rectPaint = new Paint();
		rectPaint.setStyle(Style.FILL_AND_STROKE);
		rectPaint.setColor(Color.argb(200, 255, 255, 255));
		
		// initialize the TextPaint for messages
		textPaint = new TextPaint();
		textPaint.setColor(Color.BLACK);
		textPaint.setTextSize(16);
		textPaint.setShadowLayer(1, 1, 1, Color.WHITE);
		textPaint.setAntiAlias(true);
		
		// set OnLongClickListener to this, so that the View handles the onLongClick event itself
		setOnLongClickListener(this);
		
		// HAPPY X-MAS!
		// this will need resources, but needs to be initialized... no risk, no fun!
		snowFlakes = BitmapFactory.decodeResource(AppActivity.mContext.getResources(), R.drawable.snow);
		snowFlake = new Bitmap[Settings.snowFlakeCount];
		snowFlakeX = new int[Settings.snowFlakeCount];
		snowFlakeY = new int[Settings.snowFlakeCount];

		for(int i = 0; i < Settings.snowFlakeCount; i++) {
			int scale = (int) (Math.random()*11)+5;
			snowFlake[i] = Bitmap.createScaledBitmap(snowFlakes, scale, scale, false);
			snowFlakeX[i] = (int) (Math.random()*Settings.fallBackWidth);
			snowFlakeY[i] = (int) -(Math.random()*1.5*Settings.fallBackHeight);
		}
	}
	
	/**
	 * @see android.view.View#onDraw(android.graphics.Canvas)
	 */
	@SuppressLint("DrawAllocation")
	@Override
	protected void onDraw(Canvas canvas) {
	    super.onDraw(canvas);
		canvas.drawRGB(0, 0, 0);
		
		for(OBJect obj : AppActivity.objOrderSet) {
			if(obj.isLocal()) {

				// let's check if the avatar has reached his position
				// the border around the position should be as fat as a touch finger. The bigger, the better ;-)
				// 16 seems to big (avatar stops before reaching the click position)
				// 8 seems to small (avatar not stopping at position)
				if((Math.abs(obj.xPos-calculatedXPos) < 12) && (Math.abs(obj.yPos-calculatedYPos) < 12) && obj.speedX != 0 && obj.speedY != 0) {
					obj.speedX = 0;
					obj.speedY = 0;
					
					obj.state = stateLoopEndState;
					obj.stateLoopSize = 0;

					// send everything
					// 32 = speed update
					AppActivity.mSendHandler.sendUOBJ(AppActivity.mAvatar, 56);
				}
			}
			
			// check if bitmap is referenced
			if(obj.bitmap == null)
					continue;
			
			// if type = 1 and messageQueue is not null, let's display messages
			// handled before checking if inside view size, because messages could exceed display size
			if(obj.type == 1 && obj.messageQueue != null) {
				
				String message = obj.messageQueue.peekFirst();
				if(message != null) {				
					// create a StaticLayout for displaying the message
					StaticLayout mLayout = new StaticLayout(message, textPaint, 200, android.text.Layout.Alignment.ALIGN_NORMAL, (float) 1.0, (float) 0.0, true);
								
					// get the estimated width
					float width = textPaint.measureText(message);
					
					// set the width for the speech bubble to the maximum size of our mLayout if it's greater than this
					if(width > 200)
						width = 200;
					
					int translateX = obj.xPos-viewXPos+10;
					int translateY = obj.yPos-viewYPos-((mLayout.getLineCount()+1)*20);
					
					// change ">" to "<" if messages should be displayed at each avatar's side
					if(translateX > Settings.fallBackWidth/2)
						translateX -= width+20-obj.width;
					
					if(translateY < 0)
						translateY += ((mLayout.getLineCount()+2)*20)+obj.height;
					
					// translate() will transponate the canvas matrix for easier handling xPos and yPos
					// save the count of canvas, so there can't be any inconsistence
					int saveCount = canvas.save();
					canvas.translate(translateX, translateY);
					
					// draw the RoundRect for speech bubble (and borders)
					RectF mRect = new RectF(-10, -10, width+10, ((mLayout.getLineCount()+1)*20)-10);
					canvas.drawRoundRect(mRect, 5, 5, rectPaint);

					// finally draw our mLayout to the canvas and put the canvas back to its origin
					mLayout.draw(canvas);
					canvas.restoreToCount(saveCount);
				}
			}
			
			// check if OBJect is inside the view
			// don't just check obj.xPos here, take obj.xPos + obj.xFP, because object basepoints matter for displaying (think of trees!) 
			if((obj.xPos + obj.xFP > viewXPos + screenWidth + 64) || (obj.xPos + obj.xFP < viewXPos - 64) || (obj.yPos + obj.yFP > viewYPos + screenHeight + 64) || (obj.yPos + obj.yFP < viewYPos - 64))
				continue;
			
			// if recycled, call it again
			if(obj.bitmap.isRecycled()) {
				// when image was recycled, get it from cache again
				obj.bitmap = AppActivity.imageCache.get(String.valueOf(obj.imageID) + "_" + String.valueOf(obj.state));
				continue; // will be drawn next time, so there should be enough time to fetch the image
			}
			
			// DEBUG
			if(obj.type == 1 && !obj.isLocal() && AppActivity.settings.getBoolean("displayDebugHelpers", false)) {
			
				// set the color for normal objects (not reachabel for chat) to Color.WHITE
				paint.setColor(Color.RED);
				
				// check if the distance between avatar and the other avatar is < 256
				// a² + b² = c²
				// if reachable, set to Color.RED
				if((AppActivity.mAvatar != null) && (AppActivity.mAvatar.xPos - obj.xPos)*(AppActivity.mAvatar.xPos - obj.xPos) + (AppActivity.mAvatar.yPos - obj.yPos)*(AppActivity.mAvatar.yPos - obj.yPos) <= 256*256)
					paint.setColor(Color.GREEN);
				
				// draw circle around FP coordinates
				canvas.drawRect(obj.xPos-viewXPos, obj.yPos-viewYPos, obj.xPos+obj.width-viewXPos, obj.yPos+obj.height-viewYPos, paint);
				
				// DEBUG
				if(debug)
					Log.i("VIEW", "User-created OBJect at: " + String.valueOf(obj.xPos) + ", " + String.valueOf(obj.yPos));
			}
			
			// draw bitmap to Canvas
			canvas.drawBitmap(obj.bitmap, obj.xPos-viewXPos, obj.yPos-viewYPos, null);
			
			// DEBUG
			if(debug)
				Log.i("VIEW", "OBJect: " + String.valueOf(obj.objectID) + " drawn");
		}
		
		// HAPPY X-MAS!
		if(Settings.xmasMode) {

			for(int i = 0; i < Settings.snowFlakeCount; i++) {
				canvas.drawBitmap(snowFlake[i], snowFlakeX[i], snowFlakeY[i], null);
				
				if(Math.round(Math.random()) > 0)
					snowFlakeX[i] -= Math.random()*8;
				else
					snowFlakeX[i] += Math.random()*8;
	
				if(snowFlakeX[i] > Settings.fallBackWidth+32 || snowFlakeX[i] < -32 || snowFlakeY[i] > Settings.fallBackHeight+32) {
					snowFlakeY[i] = -32;
					snowFlakeX[i] = (int) (Math.random()*Settings.fallBackWidth);
				}
	
				snowFlakeY[i] += Math.random()*8 + 4; // minimal will be 4, maximum 12, that really looks nice!
			}
		}
	}

	/**
	 * @see android.view.View#onTouchEvent(android.view.MotionEvent)
	 */
	public boolean onTouchEvent(MotionEvent event) {
			
		if(event.getAction() == MotionEvent.ACTION_DOWN) {
			
			// get the click position coordinates
			clickX = (int) event.getX();
			clickY = (int) event.getY();

			// save the current view position
			mXPos = viewXPos;
			mYPos = viewYPos;
			
			if(AppActivity.initialAvatarPositioned) {
				// reset currentClickedUser
				currentClickedUser = null;
				
				// check if click was onto a user, then pass event to onLongClickListener
				for(OBJect user : AppActivity.userSet) {
					
					// let's check if click position was on top of an avatar
					if((clickX + viewXPos >= user.xPos) && (clickX + viewXPos <= user.xPos + user.width) && (clickY + viewYPos >= user.yPos) && (clickY + viewYPos <= user.yPos + user.height)) {
						currentClickedUser = user;

						// check if the distance between avatar and the other avatar is < 256 (a² + b² = c²)
						// then pass the event to it's default callback, so the onLongClick will be entered
						if((user.isLocal()) || ((AppActivity.mAvatar.xPos - user.xPos)*(AppActivity.mAvatar.xPos - user.xPos) + (AppActivity.mAvatar.yPos - user.yPos)*(AppActivity.mAvatar.yPos - user.yPos) <= 256*256))
							return super.onTouchEvent(event);
						
						// if not another user or not inside the chat distance, let's stop the for-loop
						break;
					}
				}
			
				// if the click was outside of an avatar
				// set new position
				calculatedXPos = mXPos + clickX;
				calculatedYPos = mYPos + clickY;
	
				// let's calculate (perfect) speed settings
				// beginning with calculating the distance between current position and clicked position
								
				double xDistance = calculatedXPos - AppActivity.mAvatar.xPos;
				double yDistance = calculatedYPos - AppActivity.mAvatar.yPos;
				
				// Pro tip for using Math functions instead of Pythagorean theorem
				double alpha = Math.atan(Math.abs(yDistance)/Math.abs(xDistance));
				
				// calculate speedX
				double speedX = Math.cos(alpha)*Settings.avatarSpeed;
				if(xDistance < 0)
					speedX = -speedX;
				
				// calculate speedY
				double speedY = Math.sin(alpha)*Settings.avatarSpeed;
				if(yDistance < 0)
					speedY = -speedY;
				
				// let's set the state according to the highest distance vector
				if(Math.abs(yDistance) > Math.abs(xDistance)) {
					// yDistance is higher
					if(yDistance < 0) {
						stateLoopEndState = 10;
						AppActivity.mAvatar.state = 10; // pony is walking up
						AppActivity.mAvatar.stateLoop = Settings.avatarStateLoop[3];
						AppActivity.mAvatar.stateLoopSize = AppActivity.mAvatar.stateLoop.length;
					} else {
						stateLoopEndState = 1;
						AppActivity.mAvatar.state = 1; // pony is walking down
						AppActivity.mAvatar.stateLoop = Settings.avatarStateLoop[0];
						AppActivity.mAvatar.stateLoopSize = AppActivity.mAvatar.stateLoop.length;
					}
				} else {
					// xDistance is higher than yDistance
					if(xDistance < 0) {
						stateLoopEndState = 4;
						AppActivity.mAvatar.state = 4; // pony is walking left
						AppActivity.mAvatar.stateLoop = Settings.avatarStateLoop[1];
						AppActivity.mAvatar.stateLoopSize = AppActivity.mAvatar.stateLoop.length;
					} else {
						stateLoopEndState = 7;
						AppActivity.mAvatar.state = 7; // pony is walking right
						AppActivity.mAvatar.stateLoop = Settings.avatarStateLoop[2];
						AppActivity.mAvatar.stateLoopSize = AppActivity.mAvatar.stateLoop.length;
					}
				}
				
				// let's set the speed double values							
				AppActivity.mAvatar.speedX = speedX;
				AppActivity.mAvatar.speedY = speedY;
	
				// and finally send the update to server
				// 56 = 32 (speed update) + 16 (stateLoop update) + 8 (state update)
				AppActivity.mSendHandler.sendUOBJ(AppActivity.mAvatar, 56);
				
				// DEBUG
				if(debug)
					Log.i("EVENT", "Avatar set to: " + String.valueOf(AppActivity.mAvatar.xPos) + ", " + String.valueOf(AppActivity.mAvatar.yPos));			
			}
						
			// get new view position
			viewXPos = mXPos - screenWidth/2 + clickX;
			viewYPos = mYPos - screenHeight/2 + clickY;
			
			// Check for left and upper map borders
			if(viewXPos < 32)
				viewXPos = 32;
			if(viewYPos < 32)
				viewYPos = 32;
			
			// Check for right and bottom map borders
			if(viewXPos + screenWidth >= Settings.maxXPos)
				viewXPos = Settings.maxXPos - screenWidth;
			if(viewYPos + screenHeight >= Settings.maxYPos)
				viewYPos = Settings.maxYPos - screenHeight;
		
			// DEBUG
			if(debug)
				Log.i("EVENT", "View set to: " + String.valueOf(viewXPos) + ", " + String.valueOf(viewYPos));

			// send UVIE
			AppActivity.mSendHandler.sendUVIE(Settings.viewID, viewXPos, viewYPos, screenWidth, screenHeight);

			return super.onTouchEvent(event);
		}
		
		// if nothing happened, pass the event to it's default callback
		return super.onTouchEvent(event);
	}

	/**
	 * @see android.view.View.OnLongClickListener#onLongClick(android.view.View)
	 */
	@Override
	public boolean onLongClick(View v) {		
		// native Android power, so the next line can be left commented out
		// performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
		
		// check if avatar wasn't positioned yet
		if(!AppActivity.initialAvatarPositioned) {

			// let's create our local OBJect
        	AppActivity.mAvatar = new OBJect(Settings.objID, Settings.username, Settings.avatarID);
        	
        	AppActivity.mAvatar.zPos = Settings.avatarZPos;
        	
        	// get other properties from Settings
        	AppActivity.mAvatar.width = Settings.avatarWidth;
        	AppActivity.mAvatar.height = Settings.avatarHeight;
        	AppActivity.mAvatar.xFP = Settings.avatarXFP;
        	AppActivity.mAvatar.yFP = Settings.avatarYFP;
        	AppActivity.mAvatar.type = Settings.avatarType;
        	AppActivity.mAvatar.state = Settings.avatarState;
        	AppActivity.mAvatar.cFactor = Settings.avatarCFactor;

        	// set it to the last clicked position
        	AppActivity.mAvatar.xPos = mXPos + clickX - AppActivity.mAvatar.xFP;
        	AppActivity.mAvatar.yPos = mYPos + clickY - AppActivity.mAvatar.yFP;
        	
        	AppActivity.objMap.put(AppActivity.mAvatar.objectID, AppActivity.mAvatar);
        	
        	// send the COBJ message
        	AppActivity.mSendHandler.sendCOBJ(AppActivity.mAvatar);
        	
			// DEBUG
			if(debug)
				Log.i("EVENT", "Received onLongClick event to set the avatar");
        	
        	// return true to end the onLongClick event now
        	return true;

		} else if(currentClickedUser != null) {
			// the pony should now be positioned and we're clicking onto users
						
			// let's react to
			// 1) avatar actions (if onLongClick onto own avatar)
			// 2) chat messages (when onLongClick onto other users)
		
			// let's get a reference to the currentClickedUser
			OBJect user = currentClickedUser;
			
			// let's stop movement
			AppActivity.mAvatar.speedX = 0;
			AppActivity.mAvatar.speedY = 0;
			
			AppActivity.mAvatar.state = stateLoopEndState;
			AppActivity.mAvatar.stateLoopSize = 0;
			
			// send everything
			// 56 = speed update + state update + delete stateLoop
			AppActivity.mSendHandler.sendUOBJ(AppActivity.mAvatar, 56);
			
			// CASE 1) avatar actions (if onLongClick onto own avatar)
			if(user.isLocal()) {

				// we need to give the callback to our Activity
				// (wrapper needed to call a static reference inside a static method)
				AppActivity.OnLongClickOpenContextMenu(AppActivity.mAppView);
								
				// DEBUG
				if(debug)
					Log.i("EVENT", "Received onLongClick event onto own avatar");

				return true;
			}
			
			// CASE 2) chat messages (when onLongClick onto other users)
			// check if the distance between avatar and the other avatar is < 256 (a² + b² = c²)
			if((AppActivity.mAvatar.xPos - user.xPos)*(AppActivity.mAvatar.xPos - user.xPos) + (AppActivity.mAvatar.yPos - user.yPos)*(AppActivity.mAvatar.yPos - user.yPos) <= 256*256) {
					
				// DEBUG
				if(debug)
					Log.i("CHAT", "Started Chat for object ID " + String.valueOf(user.objectID));
				
				// HAPPY X-MAS!
				if(Settings.xmasMode) {
					final String message = "Merry Christmas!";
					
					// get user input text and send SMSG
					AppActivity.mSendHandler.sendSMSG(user.objectID, message);
					
					// and add to messageQueue for our mAvatar to display the sent message, too
					AppActivity.mAvatar.messageQueue.add(message);
					AppActivity.mMessageTimeoutTimer.schedule(new TimerTask() {
						@Override
						public void run() {
							AppActivity.mAvatar.messageQueue.remove(message);
						       AppActivity.viewUpdateNeeded = true;
						}
					}, Settings.messageDisplayLength*AppActivity.mAvatar.messageQueue.size());
					
					return true;
				}

				// let's build the layout for our prompt view
				LayoutInflater mLayoutInflater = LayoutInflater.from(AppActivity.mContext);
				View chatDialog = mLayoutInflater.inflate(R.layout.chat_dialog, null);
		 
				// and build a new DialogBuilder with the currently created view
				AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(AppActivity.mContext);
				alertDialogBuilder.setView(chatDialog);

				// why final? because Eclipse wanted so ;-) (seriously: http://stackoverflow.com/questions/4732544/why-are-only-final-variables-accessible-in-anonymous-class)
				final EditText userInput = (EditText) chatDialog.findViewById(R.id.editTextDialogUserInput);
				final long destination = user.objectID;
		 
				// define the action when clicking onto the "Cancel" or back button
				alertDialogBuilder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						// do nothing here...
						dialog.cancel();
						
						// DEBUG
						if(debug)
							Log.i("CHAT", "Aborted Chat for object ID " + String.valueOf(destination));
					}
				});
				
				// define the action when clicking the "OK" button					
				alertDialogBuilder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						
						final String message = userInput.getText().toString();
						
						// get user input text and send SMSG
						AppActivity.mSendHandler.sendSMSG(destination, message);
						
						// and add to messageQueue for our mAvatar to display the sent message, too
						if(message.length() > 0)
							AppActivity.mAvatar.messageQueue.add(message);
							AppActivity.mMessageTimeoutTimer.schedule(new TimerTask() {
								@Override
								public void run() {
									AppActivity.mAvatar.messageQueue.remove(message);
							        AppActivity.viewUpdateNeeded = true;
								}
							}, Settings.messageDisplayLength*AppActivity.mAvatar.messageQueue.size());
						
						// DEBUG
						if(debug)
							Log.i("CHAT", "Message \"" + userInput.getText().toString() + "\" sent to object ID " + String.valueOf(destination));
					}
				});
		 
				// now create the dialog and show it
				AlertDialog alertDialog = alertDialogBuilder.create();
				alertDialog.show();	

				// return true, so that the rest of the onLongClick event will be ignored
				return true;
			}			
		}
		// nothing was done here, so "false" would be right here...
		// though, true has to be the return value, so that onCreateContextMenu will not be called to finally handle the callback
		return true;
	}

	/**
	 * Overwritten to change View dimensions after initialization with default fallback values
	 * 
	 * @see android.view.View#onSizeChanged(int, int, int, int)
	 */
	protected void onSizeChanged (int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		// set new dimensions
		screenWidth = w;
		screenHeight = h;
		
		// send UVIE
		AppActivity.mSendHandler.sendUVIE(Settings.viewID, viewXPos, viewYPos, screenWidth, screenHeight);
		
		// DEBUG
		if(debug)
			Log.i("VIEW", "Screen size changed to: " + w + "x" + h + " px");
	}
}
