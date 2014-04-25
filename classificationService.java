package com.sleep.apnea;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.sleep.apnea.R;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class classificationService extends IntentService
{
	private static final String TAG = "classificationService";
	private static SpO2desaturationEventBlock[] buffer;
	private static int nevents;
	//private int prevtype;
	
	//for uploading
	//private static FileOutputStream outputStream;
	//private static DataOutputStream dos;
	private static PrintWriter pw;
	private static DateFormat df;
	private static String filename;
	//private static  Date dt;
	private static final int waitup = 1;
	private static int countup;
	//private static Intent upint;
	
	static {
		buffer = testDataSource.getInstance().getBuffer();
		nevents = 0;
		//prevtype = SpO2desaturationEventBlock.NONE;
		countup = waitup; // 60*60/150
		df = new SimpleDateFormat("dd-MM[HH-mm]"); //"EEE MMM dd [HH mm ss] zzz yyyy"
		Log.d(TAG,"init static stuff");
	}
	
	//this is called many times, not sure why, but I want to retain vals of my vars
	public classificationService() {
		super(TAG);
		Log.d("IntentService","constructor");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d("IntentService","onHandleIntent");
		int curindex = intent.getIntExtra("index",0);
		int curtype = buffer[curindex].doAll();
		if(curtype == SpO2desaturationEventBlock.CONTINUOUS){
			//start giving the pillow command
			sendMessage((byte)1);
			//add to db?
			writeInt(curindex);
			++nevents; //+=N
	    	if(nevents>=1)
	    	{
	    	}
	    }
		else{
			nevents = 0;
			sendMessage((byte)0);
	    }
		//return classification results in doAll? and remember count here instead of SpO2desaturationEventBlock and activate pillow as reqd?
		writeFile(curindex,curtype);
	}

	// Send an Intent with an action named "apnea-event". 
	private void sendMessage(byte val) {
		Log.d(TAG,"sendMessage to arduino, cur in intent");
		Intent intent = new Intent(BluetoothMessageConstant.APNEA_EVENT);
		// add data
		intent.putExtra("message", val);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	} 
	
	private void writeFile(int curindex,int curtype) {
		Log.d(TAG,"writeFile");
		if(countup == waitup) {
			//create a new file to put SpO2 values into
			filename = "data"+df.format(new Date(System.currentTimeMillis()))+".txt";
			Log.d(TAG,"creating file"+filename);
			try {
				pw = new PrintWriter(openFileOutput(filename, Context.MODE_PRIVATE));//WORLD_READABLE - deprecated
			} catch (FileNotFoundException e) {
				Log.e(TAG,"Filenotfoundexception");
				e.printStackTrace();
			}
			Log.d(TAG,pw.toString());
		}
		pw.println(curtype);
		writeArray(curindex);
		if(--countup == 0){
			countup = waitup;
			try {
				pw.flush(); //flush and close streams
				pw.close();
			}
			catch(Exception e) {//IO
				Log.e(TAG,"ioexception during close or flush");
			}
			//upload this file to the server
			Intent upint = new Intent(this,uploadDataService.class);
			upint.putExtra("filename",filename);
			startService(upint);
		}
	}
	
	private void writeArray(int curindex){
		Log.d(TAG,"writing array to file");
		//try {
			for(int i=0; i<150; ++i) //
			{
				Log.d(TAG,i+"");
				pw.println(buffer[curindex].arr[i]);
				//dos.writeDouble(buffer[cur][i]); //write in bigendian
				//dos.writeChar('\n');
			}
		/*} catch (IOException e) {
			Log.e(TAG,"write double array ioexception");
			e.printStackTrace();
		}*/
	}
	
	//for keeping a list internally
	private void writeInt(int curindex){
		Log.d(TAG,"apneaeventlistfile");
		try {
			PrintWriter ipw = new PrintWriter(openFileOutput(getString(R.string.eventlistfile),Context.MODE_APPEND));
			ipw.println(new Date());
			ipw.println(buffer[curindex]);
			ipw.println("---------------");
			ipw.flush();
			ipw.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG,"Filenotfoundexception");
			e.printStackTrace();
		}
	}
	
}

//need some way of pausing reading spo2
//maybe have a func in the arduino code to delay or pause till 
