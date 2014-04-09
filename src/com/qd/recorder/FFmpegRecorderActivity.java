package com.qd.recorder;

import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.MediaStore.Video;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.googlecode.javacv.FrameRecorder;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_videostab.LogToStdout;
import com.qd.recorder.ProgressView.State;
import com.qd.videorecorder.R;

public class FFmpegRecorderActivity extends Activity implements OnClickListener, OnTouchListener {

	private final static String CLASS_LABEL = "RecordActivity";
	private final static String LOG_TAG = CLASS_LABEL;

	private PowerManager.WakeLock mWakeLock;
	// ”∆µŒƒº˛µƒ¥Ê∑≈µÿ÷∑
	private String strVideoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_video.mp4";
	// ”∆µŒƒº˛∂‘œÛ
	private File fileVideoPath = null;
	// ”∆µŒƒº˛‘⁄œµÕ≥÷–¥Ê∑≈µƒurl
	private Uri uriVideoPath = null;
	//≈–∂œ «∑Ò–Ë“™¬º÷∆£¨µ„ª˜œ¬“ª≤Ω ±‘›Õ£¬º÷∆
	private boolean rec = false;
	//≈–∂œ «∑Ò–Ë“™¬º÷∆£¨ ÷÷∏∞¥œ¬ºÃ–¯£¨Ãß∆ ±‘›Õ£
	boolean recording = false;
	//≈–∂œ «∑Òø™ º¡À¬º÷∆£¨µ⁄“ª¥Œ∞¥œ¬∆¡ƒª ±…Ë÷√Œ™true
	boolean	isRecordingStarted = false;
	// «∑Òø™∆Ù…¡π‚µ∆
	boolean isFlashOn = false;
	//∑÷±Œ™…¡π‚µ∆∞¥≈•°¢◊™÷√…„œÒÕ∑∞¥≈•°¢»°œ˚∞¥≈•°¢œ¬“ª≤Ω∞¥≈•
	Button flashIcon = null, switchCameraIcon = null, cancelBtn, nextBtn;
	boolean nextEnabled = false;
	
	//¬º÷∆ ”∆µ∫Õ±£¥Ê“Ù∆µµƒ¿‡
	private volatile NewFFmpegFrameRecorder videoRecorder;
	
	//≈–∂œ «∑Ò ««∞÷√…„œÒÕ∑
	private boolean isPreviewOn = false;
	//µ±«∞¬º÷∆µƒ÷ ¡ø£¨ª·”∞œÏ ”∆µ«ÂŒ˙∂»∫ÕŒƒº˛¥Û–°
	private int currentResolution = CONSTANTS.RESOLUTION_MEDIUM_VALUE;
	private Camera mCamera;

	//‘§¿¿µƒøÌ∏ﬂ∫Õ∆¡ƒªøÌ∏ﬂ
	private int previewWidth = 480, screenWidth = 480;
	private int previewHeight = 480, screenHeight = 480;
	
	//“Ù∆µµƒ≤…—˘¬ £¨recorderParameters÷–ª·”–ƒ¨»œ÷µ
	private int sampleRate = 44100;
	//µ˜”√œµÕ≥µƒ¬º÷∆“Ù∆µ¿‡
	private AudioRecord audioRecord; 
	//¬º÷∆“Ù∆µµƒœﬂ≥Ã
	private AudioRecordRunnable audioRecordRunnable;
	private Thread audioThread;
	//ø™∆Ù∫ÕÕ£÷π¬º÷∆“Ù∆µµƒ±Íº«
	volatile boolean runAudioThread = true;

	//…„œÒÕ∑“‘º∞À¸µƒ≤Œ ˝
	private Camera cameraDevice;
	private CameraView cameraView;
	Parameters cameraParameters = null;
	//IplImage∂‘œÛ,”√”⁄¥Ê¥¢…„œÒÕ∑∑µªÿµƒbyte[]£¨“‘º∞Õº∆¨µƒøÌ∏ﬂ£¨depth£¨channelµ»
	private IplImage yuvIplImage = null;
	//∑÷±Œ™ ƒ¨»œ…„œÒÕ∑£®∫Û÷√£©°¢ƒ¨»œµ˜”√…„œÒÕ∑µƒ∑÷±Ê¬ °¢±ª—°‘Òµƒ…„œÒÕ∑£®«∞÷√ªÚ’ﬂ∫Û÷√£©
	int defaultCameraId = -1, defaultScreenResolution = 10 , cameraSelection = 0;

	private Dialog dialog = null;
	//∞¸∫¨œ‘ æ…„œÒÕ∑ ˝æ›µƒsurfaceView
	RelativeLayout topLayout = null;

	//µ⁄“ª¥Œ∞¥œ¬∆¡ƒª ±º«¬ºµƒ ±º‰
	long firstTime = 0;
	// ÷÷∏Ãß∆ «µƒ ±º‰
	long startPauseTime = 0;
	//√ø¥Œ∞¥œ¬ ÷÷∏∫ÕÃß∆÷Æº‰µƒ‘›Õ£ ±º‰
	long totalPauseTime = 0;
	// ÷÷∏Ãß∆ «µƒ ±º‰
	long pausedTime = 0;
	//◊‹µƒ‘›Õ£ ±º‰
	long stopPauseTime = 0;
	//¬º÷∆µƒ”––ß◊‹ ±º‰
	long totalTime = 0;
	// ”∆µ÷°¬ 
	private int frameRate = 24;
	//¬º÷∆µƒ◊Ó≥§ ±º‰
	public static int recordingTime = 30000;
	//¬º÷∆µƒ◊Ó∂Ã ±º‰
	private int recordingMinimumTime = 6000;
	//Ã· æªª∏ˆ≥°æ∞
	private int recordingChangeTime = 3000;
	
	boolean recordFinish = false;
	private  Dialog creatingProgress;
	
	//“Ù∆µ ±º‰¥¡
	private volatile long mAudioTimestamp = 0L;
	//“‘œ¬¡Ω∏ˆ÷ª◊ˆÕ¨≤Ω±Í÷æ£¨√ª”– µº “‚“Â
	private final int[] mVideoRecordLock = new int[0];
	private final int[] mAudioRecordLock = new int[0];
	private long mLastAudioTimestamp = 0L;
	private volatile long mAudioTimeRecorded;
	private long frameTime = 0L;
	//√ø“ªé¨µƒ ˝æ›Ω·ππ
	private SavedFrames lastSavedframe = new SavedFrames(null,0L);
	// ”∆µ ±º‰¥¡
	private long mVideoTimestamp = 0L;
	// «∑Ò±£¥Êπ˝ ”∆µŒƒº˛
	private boolean isRecordingSaved = false;
	private boolean isFinalizing = false;
	
	//Ω¯∂»Ãı
	private ProgressView progressView;
	//≤∂ªÒµƒµ⁄“ªé¨µƒÕº∆¨
	private String imagePath = null;
	private RecorderState currentRecorderState = RecorderState.PRESS;
	private ImageView stateImageView;
	
	private byte[] firstData = null;
	
	private Handler mHandler;
	private void initHandler(){
		mHandler = new Handler(){
			@Override
			public void dispatchMessage(Message msg) {
				switch (msg.what) {
				/*case 1:
					final byte[] data = (byte[]) msg.obj;
					ThreadPoolUtils.execute(new Runnable() {
						
						@Override
						public void run() {
							getFirstCapture(data);
						}
					});
					break;*/
				case 2:
					int resId = 0;
					if(currentRecorderState == RecorderState.PRESS){
						resId = R.drawable.video_text01;
					}else if(currentRecorderState == RecorderState.LOOSEN){
						resId = R.drawable.video_text02;
					}else if(currentRecorderState == RecorderState.CHANGE){
						resId = R.drawable.video_text03;
					}else if(currentRecorderState == RecorderState.SUCCESS){
						resId = R.drawable.video_text04;
					}
					stateImageView.setImageResource(resId);
					break;
				case 3:
					if(!recording)
					{
						initiateRecording(true);
					}else{
						//∏¸–¬‘›Õ£µƒ ±º‰
						stopPauseTime = System.currentTimeMillis();
						totalPauseTime = stopPauseTime - startPauseTime - ((long) (1.0/(double)frameRate)*1000);
						pausedTime += totalPauseTime;
					}
					
					rec = true;
					//ø™ ºΩ¯∂»Ãı‘ˆ≥§
					progressView.setCurrentState(State.START);
				break;
				case 4:
					//…Ë÷√Ω¯∂»Ãı‘›Õ£◊¥Ã¨
					progressView.setCurrentState(State.PAUSE);
					//Ω´‘›Õ£µƒ ±º‰¥¡ÃÌº”µΩΩ¯∂»Ãıµƒ∂”¡–÷–
					progressView.putProgressList((int) totalTime);
					rec = false;
					startPauseTime = System.currentTimeMillis();
					if(totalTime >= recordingMinimumTime){
						currentRecorderState = RecorderState.SUCCESS;
						mHandler.sendEmptyMessage(2);
					}else if(totalTime >= recordingChangeTime){
						currentRecorderState = RecorderState.CHANGE;
						mHandler.sendEmptyMessage(2);
					}
					break;
				case 5:
					currentRecorderState = RecorderState.SUCCESS;
					mHandler.sendEmptyMessage(2);
					break;
				default:
					break;
				}
			}
		};
	}
	
	//neonø‚∂‘opencv◊ˆ¡À”≈ªØ
	static {
		System.loadLibrary("checkneon");
	}

	private boolean initSuccess = false;
	public native static int  checkNeonFromJNI();
	private boolean initSuccess = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_recorder);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); 
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL); 
		mWakeLock.acquire(); 

		DisplayMetrics displaymetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
		//Find screen dimensions
		screenWidth = displaymetrics.widthPixels;
		screenHeight = displaymetrics.heightPixels;
		
		initHandler();
		
		initLayout();
<<<<<<< HEAD
	}
	
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if(!initSuccess)
		{
			return false;
		}
		return super.dispatchTouchEvent(ev);
=======
		
>>>>>>> ÂêØÂä®ÈÄüÂ∫¶ÂÅö‰∫Ü‰∏Ä‰∫õ‰ºòÂåñ
	}

	
	@Override
	protected void onResume() {
		super.onResume();
		mHandler.sendEmptyMessage(2);
		
		if (mWakeLock == null) {
			//ªÒ»°ªΩ–—À¯,±£≥÷∆¡ƒª≥£¡¡
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
			mWakeLock.acquire();
		}
	}
	
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if(!initSuccess)
			return false;
		return super.dispatchTouchEvent(ev);
	}


	@Override
	protected void onPause() {
		super.onPause();
		if(!isFinalizing)
			finish();
		
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		//Log.i("video", this.getLocalClassName()+"°™destory");
		recording = false;
		runAudioThread = false;
		
		releaseResources();
			
		if (cameraView != null) {
			cameraView.stopPreview();
			if(cameraDevice != null){
				cameraDevice.setPreviewCallback(null);
				cameraDevice.release();
			}
			cameraDevice = null;
		}
		firstData = null;
		mCamera = null;
		cameraView = null;
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}
	private void initLayout()
	{
		stateImageView = (ImageView) findViewById(R.id.recorder_surface_state);
		
		progressView = (ProgressView) findViewById(R.id.recorder_progress);
		cancelBtn = (Button) findViewById(R.id.recorder_cancel);
		cancelBtn.setOnClickListener(this);
		nextBtn = (Button) findViewById(R.id.recorder_next);
		nextBtn.setOnClickListener(this);
		flashIcon = (Button)findViewById(R.id.recorder_flashlight);
		switchCameraIcon = (Button)findViewById(R.id.recorder_frontcamera);
		flashIcon.setOnClickListener(this);
		
		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
			switchCameraIcon.setVisibility(View.VISIBLE);
		}
		initCameraLayout();
	}

	private void initCameraLayout() {
		new AsyncTask<String, Integer, Boolean>(){
<<<<<<< HEAD

			@Override
			protected Boolean doInBackground(String... params) {
				boolean result = setCamera();
				
				if(!initSuccess){
					
					initVideoRecorder();
					startRecording();
					
					initSuccess = true;
				}
				
				return result;
			}
			
			@Override
			protected void onPostExecute(Boolean result) {
				if(!result || cameraDevice == null){
					//FuncCore.showToast(FFmpegRecorderActivity.this, "Œﬁ∑®¡¨Ω”µΩœ‡ª˙");
					finish();
					return;
				}
				
				topLayout = (RelativeLayout) findViewById(R.id.recorder_surface_parent);
				if(topLayout != null && topLayout.getChildCount() > 0)
					topLayout.removeAllViews();
				
				cameraView = new CameraView(FFmpegRecorderActivity.this, cameraDevice);
				
				handleSurfaceChanged();
				//…Ë÷√surfaceµƒøÌ∏ﬂ
				RelativeLayout.LayoutParams layoutParam1 = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT);
				topLayout.addView(cameraView, layoutParam1);
				
				topLayout.setOnTouchListener(FFmpegRecorderActivity.this);
				
				switchCameraIcon.setOnClickListener(FFmpegRecorderActivity.this);
				if(cameraSelection == CameraInfo.CAMERA_FACING_FRONT)
				{
					flashIcon.setVisibility(View.GONE);
				}else{
					flashIcon.setVisibility(View.VISIBLE);
				}
=======

			@Override
			protected Boolean doInBackground(String... params) {
				boolean result = setCamera();
				
				if(!initSuccess){
					
					initVideoRecorder();
					startRecording();
					
					initSuccess = true;
				}
				
				return result;
			}
			
			@Override
			protected void onPostExecute(Boolean result) {
				if(!result || cameraDevice == null){
					//showToast(FFmpegRecorderActivity.this, "Œﬁ∑®¡¨Ω”µΩœ‡ª˙");
					finish();
					return;
				}
				
				topLayout = (RelativeLayout) findViewById(R.id.recorder_surface_parent);
				if(topLayout != null && topLayout.getChildCount() > 0)
					topLayout.removeAllViews();
				
				cameraView = new CameraView(FFmpegRecorderActivity.this, cameraDevice);
				
				handleSurfaceChanged();
				//…Ë÷√surfaceµƒøÌ∏ﬂ
				RelativeLayout.LayoutParams layoutParam1 = new RelativeLayout.LayoutParams(screenWidth,(int) (screenWidth*(previewWidth/(previewHeight*1f))));
				layoutParam1.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
				//int margin = Util.calculateMargin(previewWidth, screenWidth);
				//layoutParam1.setMargins(0,margin,0,margin);

				RelativeLayout.LayoutParams layoutParam2 = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT);
				layoutParam2.topMargin = screenWidth;
				
				View view = new View(FFmpegRecorderActivity.this);
				view.setFocusable(false);
				view.setBackgroundColor(Color.BLACK);
				view.setFocusableInTouchMode(false);
				
				topLayout.addView(cameraView, layoutParam1);
				topLayout.addView(view,layoutParam2);
				
				topLayout.setOnTouchListener(FFmpegRecorderActivity.this);
				
				flashIcon.setVisibility(View.VISIBLE);
>>>>>>> ÂêØÂä®ÈÄüÂ∫¶ÂÅö‰∫Ü‰∏Ä‰∫õ‰ºòÂåñ
			}
			
		}.execute("start");
	}

	private boolean setCamera()
	{
		try
		{
			if(Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO)
			{
				int numberOfCameras = Camera.getNumberOfCameras();
				
				CameraInfo cameraInfo = new CameraInfo();
				for (int i = 0; i < numberOfCameras; i++) {
					Camera.getCameraInfo(i, cameraInfo);
					if (cameraInfo.facing == cameraSelection) {
						defaultCameraId = i;
					}
				}
			}
			stopPreview();
			if(mCamera != null)
			{
				mCamera.release();
<<<<<<< HEAD
			}
=======
>>>>>>> ÂêØÂä®ÈÄüÂ∫¶ÂÅö‰∫Ü‰∏Ä‰∫õ‰ºòÂåñ
			
			if(defaultCameraId >= 0)
			{
				cameraDevice = Camera.open(defaultCameraId);
			}else{
				cameraDevice = Camera.open();
<<<<<<< HEAD
			}
=======

>>>>>>> ÂêØÂä®ÈÄüÂ∫¶ÂÅö‰∫Ü‰∏Ä‰∫õ‰ºòÂåñ
		}
		catch(Exception e)
		{	
			return false;
		}
		return true;
	}


	private void initVideoRecorder() {
		strVideoPath = Util.createFinalPath(this);//Util.createTempPath(tempFolderPath);
		
		RecorderParameters recorderParameters = Util.getRecorderParameter(currentResolution);
		sampleRate = recorderParameters.getAudioSamplingRate();
		frameRate = recorderParameters.getVideoFrameRate();
		frameTime = (1000000L / frameRate);
		
		fileVideoPath = new File(strVideoPath); 
//		videoRecorder = new NewFFmpegFrameRecorder(strVideoPath, 480, 480, 1);
		videoRecorder = new NewFFmpegFrameRecorder(strVideoPath, previewWidth, previewHeight, 1);
		videoRecorder.setFormat(recorderParameters.getVideoOutputFormat());
		videoRecorder.setSampleRate(recorderParameters.getAudioSamplingRate());
		videoRecorder.setFrameRate(recorderParameters.getVideoFrameRate());
		videoRecorder.setVideoCodec(recorderParameters.getVideoCodec());
		videoRecorder.setVideoQuality(recorderParameters.getVideoQuality()); 
		videoRecorder.setAudioQuality(recorderParameters.getVideoQuality());
		videoRecorder.setAudioCodec(recorderParameters.getAudioCodec());
		videoRecorder.setVideoBitrate(recorderParameters.getVideoBitrate());
		videoRecorder.setAudioBitrate(recorderParameters.getAudioBitrate());
		
		audioRecordRunnable = new AudioRecordRunnable();
		audioThread = new Thread(audioRecordRunnable);
	}

	public void startRecording() {

		try {
			videoRecorder.start();
			audioThread.start();

		} catch (NewFFmpegFrameRecorder.Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Õ£÷π¬º÷∆
	 * @author QD
	 *
	 */
	public class AsyncStopRecording extends AsyncTask<Void,Integer,Void>{
		
		private ProgressBar bar;
		private TextView progress;
		@Override
		protected void onPreExecute() {
			isFinalizing = true;
			recordFinish = true;
			runAudioThread = false;
			
			//¥¥Ω®¥¶¿ÌΩ¯∂»Ãı
			creatingProgress= new Dialog(FFmpegRecorderActivity.this,R.style.Dialog_loading_noDim);
			Window dialogWindow = creatingProgress.getWindow();
			WindowManager.LayoutParams lp = dialogWindow.getAttributes();
			lp.width = (int) (getResources().getDisplayMetrics().density*240);
			lp.height = (int) (getResources().getDisplayMetrics().density*80);
			lp.gravity = Gravity.CENTER;
			dialogWindow.setAttributes(lp);
			creatingProgress.setCanceledOnTouchOutside(false);
			creatingProgress.setContentView(R.layout.activity_recorder_progress);
			
			progress = (TextView) creatingProgress.findViewById(R.id.recorder_progress_progresstext);
			bar = (ProgressBar) creatingProgress.findViewById(R.id.recorder_progress_progressbar);
			creatingProgress.show();
			
			super.onPreExecute();
		}
		
		@Override
		protected void onProgressUpdate(Integer... values) {
			progress.setText(values[0]+"%");
			bar.setProgress(values[0]);
		}
		
		/**
		 * “¿æ›byte[]¿Ôµƒ ˝æ›∫œ≥…“ª’≈bitmap£¨
		 * Ωÿ≥…480*480£¨≤¢«“–˝◊™90∂»∫Û£¨±£¥ÊµΩŒƒº˛
		 * @param data
		 */
		private void getFirstCapture(byte[] data){
			
			publishProgress(10);
			
			String captureBitmapPath = CONSTANTS.CAMERA_FOLDER_PATH;
			captureBitmapPath = Util.createImagePath(FFmpegRecorderActivity.this);
			YuvImage localYuvImage = new YuvImage(data, 17, previewWidth, previewHeight, null);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			FileOutputStream outStream = null;
			
			publishProgress(50);
			
			try {
				File file = new File(captureBitmapPath);
				if(!file.exists())
					file.createNewFile();
				localYuvImage.compressToJpeg(new Rect(0, 0, previewWidth, previewHeight), 100, bos);
				Bitmap localBitmap1 = BitmapFactory.decodeByteArray(bos.toByteArray(),
						0,bos.toByteArray().length);
				
				bos.close();
				
				Matrix localMatrix = new Matrix();
				if (cameraSelection == 0)
					localMatrix.setRotate(90.0F);
				else
					localMatrix.setRotate(270.0F);
				
				Bitmap	localBitmap2 = Bitmap.createBitmap(localBitmap1, 0, 0,
									localBitmap1.getHeight(),
									localBitmap1.getHeight(),
									localMatrix, true);
				
				publishProgress(70);
				
				ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
				localBitmap2.compress(Bitmap.CompressFormat.JPEG, 100, bos2);
					 
				outStream = new FileOutputStream(captureBitmapPath);
				outStream.write(bos2.toByteArray());
				outStream.close();
				
				localBitmap1.recycle();
				localBitmap2.recycle();
				
				publishProgress(90);
				
				isFirstFrame = false;
				imagePath = captureBitmapPath;
			} catch (FileNotFoundException e) {
				isFirstFrame = true;
				e.printStackTrace();
			} catch (IOException e) {
				isFirstFrame = true;
				e.printStackTrace();
			}        
		}
			
		
		@Override
		protected Void doInBackground(Void... params) {
			if(firstData != null)
			{
				getFirstCapture(firstData);
			}
			isFinalizing = false;
			if (videoRecorder != null && recording) {
				recording = false;
				releaseResources();
			}
			publishProgress(100);
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			creatingProgress.dismiss();
			registerVideo();
			returnToCaller(true);
			videoRecorder = null;
		}
		
	}
	
	/**
	 * ∑≈∆˙ ”∆µ ±µØ≥ˆøÚ
	 */
	private void showCancellDialog(){
		Util.showDialog(FFmpegRecorderActivity.this, "Ã· æ", "»∑∂®“™∑≈∆˙±æ ”∆µ¬£ø", 2, new Handler(){
			@Override
			public void dispatchMessage(Message msg) {
				if(msg.what == 1)
				{
					videoTheEnd(false);
				}
			}
		});
	}
	
	@Override
	public void onBackPressed() {
		if (recording) 
		{
			showCancellDialog();
		}else{
			videoTheEnd(false);
		}
	}

	/**
	 * ¬º÷∆“Ù∆µµƒœﬂ≥Ã
	 * @author QD
	 *
	 */
	class AudioRecordRunnable implements Runnable {
		
		int bufferSize;
		short[] audioData;
		int bufferReadResult;
		private final AudioRecord audioRecord;
		public volatile boolean isInitialized;
		private int mCount =0;
		private AudioRecordRunnable()
		{
			bufferSize = AudioRecord.getMinBufferSize(sampleRate, 
					AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
			audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, 
					AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,bufferSize);
			audioData = new short[bufferSize];
		}

		/**
		 * shortBuffer∞¸∫¨¡À“Ù∆µµƒ ˝æ›∫Õ∆ ºŒª÷√
		 * @param shortBuffer
		 */
		private void record(ShortBuffer shortBuffer)
		{
			try
			{
				synchronized (mAudioRecordLock)
				{
					if (videoRecorder != null)
					{
						this.mCount += shortBuffer.limit();
						videoRecorder.record(0,new Buffer[] {shortBuffer});
					}
					return;
				}
			}
			catch (FrameRecorder.Exception localException){}
		}
		
		/**
		 * ∏¸–¬“Ù∆µµƒ ±º‰¥¡
		 */
		private void updateTimestamp()
		{
			if (videoRecorder != null)
			{
				int i = Util.getTimeStampInNsFromSampleCounted(this.mCount);
				if (mAudioTimestamp != i)
				{
					mAudioTimestamp = i;
					mAudioTimeRecorded =  System.nanoTime();
				}
			}
		}

		public void run()
		{
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
			this.isInitialized = false;
			if(audioRecord != null)
			{
				//≈–∂œ“Ù∆µ¬º÷∆ «∑Ò±ª≥ı ºªØ
				while (this.audioRecord.getState() == 0)
				{
					try
					{
						Thread.sleep(100L);
					}
					catch (InterruptedException localInterruptedException)
					{
					}
				}
				this.isInitialized = true;
				this.audioRecord.startRecording();
				while (((runAudioThread) || (mVideoTimestamp > mAudioTimestamp)) && (mAudioTimestamp < (1000 * recordingTime)))
				{
					updateTimestamp();
					bufferReadResult = this.audioRecord.read(audioData, 0, audioData.length);
					if ((bufferReadResult > 0) && ((recording && rec) || (mVideoTimestamp > mAudioTimestamp)))
					{
						record(ShortBuffer.wrap(audioData, 0, bufferReadResult));
					}
				}
				this.audioRecord.stop();
				this.audioRecord.release();
			}
		}
	}
	
	//ªÒ»°µ⁄“ªé¨µƒÕº∆¨
	private boolean isFirstFrame = true;
	
		
	/**
	 * œ‘ æ…„œÒÕ∑µƒƒ⁄»›£¨“‘º∞∑µªÿ…„œÒÕ∑µƒ√ø“ª÷° ˝æ›
	 * @author QD
	 *
	 */
	class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {

		private SurfaceHolder mHolder;


		public CameraView(Context context, Camera camera) {
			super(context);
			mCamera = camera;
			cameraParameters = mCamera.getParameters();
			mHolder = getHolder();
			mHolder.addCallback(CameraView.this);
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
			mCamera.setPreviewCallback(CameraView.this);
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			try {
				stopPreview();
				mCamera.setPreviewDisplay(holder);
			} catch (IOException exception) {
				mCamera.release();
				mCamera = null;
			}
		}

		public void surfaceChanged(SurfaceHolder  holder, int format, int width, int height) {
			if (isPreviewOn)
			{
				mCamera.stopPreview();
			}
			handleSurfaceChanged();
			startPreview();  
			mCamera.autoFocus(null);
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			try {
				mHolder.addCallback(null);
				mCamera.setPreviewCallback(null);
				
			} catch (RuntimeException e) {
			}
		}

		public void startPreview() {
			if (!isPreviewOn && mCamera != null) {
				isPreviewOn = true;
				mCamera.startPreview();
			}
		}

		public void stopPreview() {
			if (isPreviewOn && mCamera != null) {
				isPreviewOn = false;
				mCamera.stopPreview();
			}
		}
	private byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) 
	{
		
		byte [] yuv = new byte[imageWidth*imageHeight*3/2];
	    // Rotate the Y luma
	    int i = 0;
	    for(int x = 0;x < imageWidth;x++)
	    {
	        for(int y = imageHeight-1;y >= 0;y--)                               
	        {
	            yuv[i] = data[y*imageWidth+x];
	            i++;
	        }

	    }
	    // Rotate the U and V color components 
	    i = imageWidth*imageHeight*3/2-1;
	    for(int x = imageWidth-1;x > 0;x=x-2)
	    {
	        for(int y = 0;y < imageHeight/2;y++)                                
	        {
	            yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+x];
	            i--;
	            yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+(x-1)];
	            i--;
	        }
	    }
	    return yuv;
	}
	
	private byte[] rotateYUV420Degree180(byte[] data, int imageWidth, int imageHeight) 
	{
		byte [] yuv = new byte[imageWidth*imageHeight*3/2];
		int i = 0;
		int count = 0;

		for (i = imageWidth * imageHeight - 1; i >= 0; i--) {
			yuv[count] = data[i];
			count++;
		}

		i = imageWidth * imageHeight * 3 / 2 - 1;
		for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth
				* imageHeight; i -= 2) {
			yuv[count++] = data[i - 1];
			yuv[count++] = data[i];
		}
		return yuv;
	}
	
	private byte[] rotateYUV420Degree270(byte[] data, int imageWidth, int imageHeight) 
	{
	    byte [] yuv = new byte[imageWidth*imageHeight*3/2];
	    int nWidth = 0, nHeight = 0;
	    int wh = 0;
	    int uvHeight = 0;
	    if(imageWidth != nWidth || imageHeight != nHeight)
	    {
	        nWidth = imageWidth;
	        nHeight = imageHeight;
	        wh = imageWidth * imageHeight;
	        uvHeight = imageHeight >> 1;//uvHeight = height / 2
	    }

	    //–˝◊™Y
	    int k = 0;
	    for(int i = 0; i < imageWidth; i++) {
	        int nPos = 0;
	        for(int j = 0; j < imageHeight; j++) {
	        	yuv[k] = data[nPos + i];
	            k++;
	            nPos += imageWidth;
	        }
	    }

	    for(int i = 0; i < imageWidth; i+=2){
	        int nPos = wh;
	        for(int j = 0; j < uvHeight; j++) {
	        	yuv[k] = data[nPos + i];
	        	yuv[k + 1] = data[nPos + i + 1];
	            k += 2;
	            nPos += imageWidth;
	        }
	    }
	    //’‚“ª≤ø∑÷ø…“‘÷±Ω”–˝◊™270∂»£¨µ´ «ÕºœÒ—’…´≤ª∂‘
//	    // Rotate the Y luma
//	    int i = 0;
//	    for(int x = imageWidth-1;x >= 0;x--)
//	    {
//	        for(int y = 0;y < imageHeight;y++)                                 
//	        {
//	            yuv[i] = data[y*imageWidth+x];
//	            i++;
//	        }
//
//	    }
//	    // Rotate the U and V color components 
//		i = imageWidth*imageHeight;
//	    for(int x = imageWidth-1;x > 0;x=x-2)
//	    {
//	        for(int y = 0;y < imageHeight/2;y++)                                
//	        {
//	            yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+x];
//	            i++;
//	            yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+(x-1)];
//	            i++;
//	        }
//	    }
	    return rotateYUV420Degree180(yuv,imageWidth,imageHeight);
	}
	
	public byte[] cropYUV420(byte[] data,int imageW,int imageH,int newImageH){
		int cropH;
		int i,j,count,tmp;
		byte[] yuv = new byte[imageW*newImageH*3/2];
 
		cropH = (imageH - newImageH)/2;
 
		count = 0;
		for(j=cropH;j<cropH+newImageH;j++){
			for(i=0;i<imageW;i++){
				yuv[count++] = data[j*imageW+i];
			}
		}
 
		//Cr Cb
		tmp = imageH+cropH/2;
		for(j=tmp;j<tmp + newImageH/2;j++){
			for(i=0;i<imageW;i++){
				yuv[count++] = data[j*imageW+i];
			}
		}
 
		return yuv;
	}
									 
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		//º∆À„ ±º‰¥¡
		long frameTimeStamp = 0L;
		if(mAudioTimestamp == 0L && firstTime > 0L)
		{
			frameTimeStamp = 1000L * (System.currentTimeMillis() - firstTime);
		}else if (mLastAudioTimestamp == mAudioTimestamp){
			frameTimeStamp = mAudioTimestamp + frameTime;
		}else{
			long l2 = (System.nanoTime() - mAudioTimeRecorded) / 1000L;
			frameTimeStamp = l2 + mAudioTimestamp;
			mLastAudioTimestamp = mAudioTimestamp;
		}
		
		//¬º÷∆ ”∆µ
		synchronized (mVideoRecordLock) {
				if (recording && rec && lastSavedframe!=null && lastSavedframe.getFrameBytesData()!=null && yuvIplImage!=null) 
				{
					//±£¥Êƒ≥“ªé¨µƒÕº∆¨
					if(isFirstFrame){
						isFirstFrame = false;
						firstData = data;
						/*Message msg = mHandler.obtainMessage(1);
						msg.obj = data;
						msg.what = 1;
						mHandler.sendMessage(msg);*/
						
					}
					//≥¨π˝◊ÓµÕ ±º‰ ±£¨œ¬“ª≤Ω∞¥≈•ø…µ„ª˜
					totalTime = System.currentTimeMillis() - firstTime - pausedTime - ((long) (1.0/(double)frameRate)*1000);
					if(!nextEnabled && totalTime >= recordingChangeTime){
						nextEnabled = true;
						nextBtn.setEnabled(true);
					}
					
					if(nextEnabled && totalTime >= recordingMinimumTime){
						mHandler.sendEmptyMessage(5);
					}
					
					if(currentRecorderState == RecorderState.PRESS && totalTime >= recordingChangeTime){
						currentRecorderState = RecorderState.LOOSEN;
						mHandler.sendEmptyMessage(2);
					}
					
					mVideoTimestamp += frameTime;
					if(lastSavedframe.getTimeStamp() > mVideoTimestamp)
					{
						mVideoTimestamp = lastSavedframe.getTimeStamp();
					}
					try {
						Log.e("recorde", "width:"+previewWidth);
						Log.e("recorde", "height:"+previewHeight);
						yuvIplImage = IplImage.create(previewHeight, previewWidth, IPL_DEPTH_8U, 2);
						yuvIplImage.getByteBuffer().put(lastSavedframe.getFrameBytesData());
						videoRecorder.setTimestamp(lastSavedframe.getTimeStamp());
						videoRecorder.record(yuvIplImage);
					} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
						Log.i("recorder", "¬º÷∆¥ÌŒÛ"+e.getMessage());
						e.printStackTrace();
					}
				}
			
				byte[] tempData = rotateYUV420Degree90(data, previewWidth, previewHeight);
				if(cameraSelection == 1)
				{
					tempData = rotateYUV420Degree270(data, previewWidth, previewHeight);
				}
				lastSavedframe = new SavedFrames(tempData, frameTimeStamp);
			}
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {

		if(!recordFinish){
			if(totalTime< recordingTime){
				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					//»Áπ˚MediaRecorder√ª”–±ª≥ı ºªØ
					//÷¥––≥ı ºªØ
					mHandler.removeMessages(3);
					mHandler.removeMessages(4);
					mHandler.sendEmptyMessageDelayed(3,300);
					break;
				case MotionEvent.ACTION_UP:
					mHandler.removeMessages(3);
					mHandler.removeMessages(4);
					if(rec)
					{
						mHandler.sendEmptyMessage(4);
					}
					break;
				}
			}else{
				//»Áπ˚¬º÷∆ ±º‰≥¨π˝◊Ó¥Û ±º‰£¨±£¥Ê ”∆µ
				rec = false;
				saveRecording();
			}
		}
		return true;
	}
	/**
	 * πÿ±’…„œÒÕ∑µƒ‘§¿¿
	 */
	public void stopPreview() {
		if (isPreviewOn && mCamera != null) {
			isPreviewOn = false;
			mCamera.stopPreview();

		}
	}

	private void handleSurfaceChanged()
	{
		if(mCamera == null){
			//showToast(this, "Œﬁ∑®¡¨Ω”µΩœ‡ª˙");
			finish();
			return;
		}
		//ªÒ»°…„œÒÕ∑µƒÀ˘”–÷ß≥÷µƒ∑÷±Ê¬ 
		List<Camera.Size> resolutionList = Util.getResolutionList(mCamera);
		if(resolutionList != null && resolutionList.size() > 0){
			Collections.sort(resolutionList, new Util.ResolutionComparator());
			
			for (Size size : resolutionList) {
				Log.e("test", "width:"+size.width+";height:"+size.height);
			}
			
			Camera.Size previewSize =  null;	
//			if(defaultScreenResolution == -1){
//				boolean hasSize = false;
//				//»Áπ˚…„œÒÕ∑÷ß≥÷640*480£¨ƒ«√¥«ø÷∆…ËŒ™640*480
//				for(int i = 0;i<resolutionList.size();i++){
//					Size size = resolutionList.get(i);
//					if(size != null && size.width==640 && size.height==480){
//						previewSize = size;
//						hasSize = true;
//						break;
//					}
//				}
//				//»Áπ˚≤ª÷ß≥÷…ËŒ™÷–º‰µƒƒ«∏ˆ
//				if(!hasSize){
//					int mediumResolution = resolutionList.size()/2;
//					if(mediumResolution >= resolutionList.size())
//						mediumResolution = resolutionList.size() - 1;
//					previewSize = resolutionList.get(mediumResolution);
//				}
//			}else{
//				if(defaultScreenResolution >= resolutionList.size())
//					defaultScreenResolution = resolutionList.size() - 1;
//				previewSize = resolutionList.get(defaultScreenResolution);
//			}
			
			boolean hasSize = false;
			//»Áπ˚…„œÒÕ∑÷ß≥÷640*480£¨ƒ«√¥«ø÷∆…ËŒ™640*480
			for(int i = 0;i<resolutionList.size();i++){
				Size size = resolutionList.get(i);
				if(size != null && size.width==640 || size.height==640){
					previewSize = size;
					hasSize = true;
					break;
				}
			}
			//»Áπ˚≤ª÷ß≥÷…ËŒ™÷–º‰µƒƒ«∏ˆ
			if(!hasSize){
				int mediumResolution = resolutionList.size()/2;
				if(mediumResolution >= resolutionList.size())
					mediumResolution = resolutionList.size() - 1;
				previewSize = resolutionList.get(mediumResolution);
			}
			
			//ªÒ»°º∆À„π˝µƒ…„œÒÕ∑∑÷±Ê¬ 
			if(previewSize != null ){
				previewWidth = previewSize.width;
				previewHeight = previewSize.height;
				
				cameraParameters.setPreviewSize(previewWidth, previewHeight);
				if(videoRecorder != null)
				{
					videoRecorder.setImageWidth(previewWidth);
					videoRecorder.setImageHeight(previewHeight);
				}
				
				Log.e("test", "cameraWidth:"+previewWidth);
				Log.e("test", "cameraHeight:"+previewHeight);
				Log.e("test", "recorderWidth:"+videoRecorder.getImageWidth());
				Log.e("test", "recorderHeight:"+videoRecorder.getImageHeight());
			}
		}
		//…Ë÷√‘§¿¿÷°¬ 
		cameraParameters.setPreviewFrameRate(frameRate);
		//ππΩ®“ª∏ˆIplImage∂‘œÛ£¨”√”⁄¬º÷∆ ”∆µ
		//∫Õopencv÷–µƒcvCreateImage∑Ω∑®“ª—˘
		yuvIplImage = IplImage.create(previewHeight, previewWidth, IPL_DEPTH_8U, 2);

		//œµÕ≥∞Ê±æŒ™8“ªœ¬µƒ≤ª÷ß≥÷’‚÷÷∂‘Ωπ
		if(Build.VERSION.SDK_INT >  Build.VERSION_CODES.FROYO)
		{
			mCamera.setDisplayOrientation(Util.determineDisplayOrientation(FFmpegRecorderActivity.this, defaultCameraId));
			List<String> focusModes = cameraParameters.getSupportedFocusModes();
			if(focusModes != null){
				Log.i("video", Build.MODEL);
				 if (((Build.MODEL.startsWith("GT-I950"))
						 || (Build.MODEL.endsWith("SCH-I959"))
						 || (Build.MODEL.endsWith("MEIZU MX3")))&&focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)){
						
					 cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
				 }else if(focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)){
					cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
				}else{
					cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
				}
			}
		}
		else{
			mCamera.setDisplayOrientation(90);
		}
			
		mCamera.setParameters(cameraParameters);
	}
	
	@Override
	public void onClick(View v) {
		//œ¬“ª≤Ω
		if(v.getId() == R.id.recorder_next){
			if (isRecordingStarted) {
				rec = false;
				saveRecording();
			}else
			{
				initiateRecording(false);
			}
		}else if(v.getId() == R.id.recorder_flashlight){
			if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)){
				//showToast(this, "≤ªƒ‹ø™∆Ù…¡π‚µ∆");
				return;
			}
			//…¡π‚µ∆
			if(isFlashOn){
				isFlashOn = false;
				flashIcon.setSelected(false);
				cameraParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
			}
			else{
				isFlashOn = true;
				flashIcon.setSelected(true);
				cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
			}
			mCamera.setParameters(cameraParameters);
		}else if(v.getId() == R.id.recorder_frontcamera){
			//◊™ªª…„œÒÕ∑
			cameraSelection = ((cameraSelection == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT:CameraInfo.CAMERA_FACING_BACK);
			initCameraLayout();

			if(cameraSelection == CameraInfo.CAMERA_FACING_FRONT)
				flashIcon.setVisibility(View.GONE);
			else{
				flashIcon.setVisibility(View.VISIBLE);
				if(isFlashOn){
					cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
					mCamera.setParameters(cameraParameters);
				}
			}
		}else if(v.getId() == R.id.recorder_cancel){
			if (recording) 
			{
				showCancellDialog();
			}else{
				videoTheEnd(false);
			}
		}
	}

	/**
	 * Ω· ¯¬º÷∆
	 * @param isSuccess
	 */
	public void videoTheEnd(boolean isSuccess)
	{
		releaseResources();
		if(fileVideoPath != null && fileVideoPath.exists() && !isSuccess)
		{
			fileVideoPath.delete();
		}
		
		returnToCaller(isSuccess);
	}
	
	/**
	 * …Ë÷√∑µªÿΩ·π˚
	 * @param valid
	 */
	private void returnToCaller(boolean valid)
	{
		try{
			setActivityResult(valid);
			if(valid){
				Intent intent = new Intent(this,FFmpegPreviewActivity.class);
				intent.putExtra("path", strVideoPath);
				intent.putExtra("imagePath", imagePath);
				startActivity(intent);
			}
		} catch (Throwable e)
		{
		}finally{
			finish();
		}
	}
	
	private void setActivityResult(boolean valid)
	{
		Intent resultIntent = new Intent();
		int resultCode;
		if (valid)
		{
			resultCode = RESULT_OK;
			resultIntent.setData(uriVideoPath);
		} else
			resultCode = RESULT_CANCELED;
		
		setResult(resultCode, resultIntent);
	}

	/**
	 * œÚœµÕ≥◊¢≤·Œ“√«¬º÷∆µƒ ”∆µŒƒº˛£¨’‚—˘Œƒº˛≤≈ª·‘⁄sdø®÷–œ‘ æ
	 */
	private void registerVideo()
	{
		Uri videoTable = Uri.parse(CONSTANTS.VIDEO_CONTENT_URI);
		
		Util.videoContentValues.put(Video.Media.SIZE, new File(strVideoPath).length());
		try{
			uriVideoPath = getContentResolver().insert(videoTable, Util.videoContentValues);
		} catch (Throwable e){
			uriVideoPath = null;
			strVideoPath = null;
			e.printStackTrace();
		} finally{}
		Util.videoContentValues = null;
	}

	/**
	 * ±£¥Ê¬º÷∆µƒ ”∆µŒƒº˛
	 */
	private void saveRecording()
	{
		if(isRecordingStarted){
			runAudioThread = false;
			if(!isRecordingSaved){
				isRecordingSaved = true;
				new AsyncStopRecording().execute();
			}
		}else{
			videoTheEnd(false);
		}
	}

	/**
	 *  Õ∑≈◊ ‘¥£¨Õ£÷π¬º÷∆ ”∆µ∫Õ“Ù∆µ
	 */
	private void releaseResources(){
		isRecordingSaved = true;
		try {
			if(videoRecorder != null)
			{
				videoRecorder.stop();
				videoRecorder.release();
			}
		} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
			e.printStackTrace();
		}
		
		yuvIplImage = null;
		videoRecorder = null;
		lastSavedframe = null;
		
<<<<<<< HEAD
=======
		//progressView.putProgressList((int) totalTime);
>>>>>>> ÂêØÂä®ÈÄüÂ∫¶ÂÅö‰∫Ü‰∏Ä‰∫õ‰ºòÂåñ
		//Õ£÷πÀ¢–¬Ω¯∂»
		progressView.setCurrentState(State.PAUSE);
	}
	
	/**
	 * µ⁄“ª¥Œ∞¥œ¬ ±£¨≥ı ºªØ¬º÷∆ ˝æ›
	 * @param isActionDown
	 */
	private void initiateRecording(boolean isActionDown)
	{
		isRecordingStarted = true;
		firstTime = System.currentTimeMillis();
	
		recording = true;
		totalPauseTime = 0;
		pausedTime = 0;
	}
	
	public static enum RecorderState {
		PRESS(1),LOOSEN(2),CHANGE(3),SUCCESS(4);
		
		static RecorderState mapIntToValue(final int stateInt) {
			for (RecorderState value : RecorderState.values()) {
				if (stateInt == value.getIntValue()) {
					return value;
				}
			}
			return PRESS;
		}

		private int mIntValue;

		RecorderState(int intValue) {
			mIntValue = intValue;
		}

		int getIntValue() {
			return mIntValue;
		}
	}
}