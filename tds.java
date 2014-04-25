package com.sleep.apnea;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
//import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Observable;
import java.util.Observer;
//import java.util.Random;
import java.util.Scanner;

import com.androidplot.xy.XYSeries;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

//make this singleton?
public class testDataSource //implements BluetoothApneaService.doWork, XYSeries
{
	//public Handler dsHandler;
   	private SpO2desaturationEventBlock[] buffer;
   	private int cur;
   	private double val; //prob of sync
   	private long time;
   	private Context ctx; //Resources res;
   	static private final int blocksize = 150;
   	//int ub; //i think this is the culprit
   	
   	//debugging
   	String TAG = "testdatasource2";
   	
   	//Singleton non lazy
   	private static testDataSource tds2instance = new testDataSource();
   	
   	private testDataSource(){
   		Log.d(TAG,"singleton pvt constructor called");
		buffer = new SpO2desaturationEventBlock[2]; //2?
		for(int i=0; i<buffer.length; ++i)
			buffer[i] = new SpO2desaturationEventBlock();
    }
   	
   	public void init(Context ctx){
   		Log.d(TAG,"context initialized");
   		this.ctx = ctx.getApplicationContext();
   		SpO2desaturationEventBlock.initmodel(ctx.getResources());
   	}
   	
	public static testDataSource getInstance() {
   		return tds2instance;
   	}
   	
	//get spo2buffer
   	public SpO2desaturationEventBlock[] getBuffer() {
		return buffer;
	}
   	
   	public void reset(){
   		time = 0;
   		buffer[0].reset();
   		buffer[1].reset();
   		cur = 0;
   	}
	
	//bluetooth thingy
   	/*@Override
    public void work(InputStream in, OutputStream out) throws IOException {
        Log.i(TAG, "BEGIN mConnectedThread");
        Scanner ins = new Scanner(in);
        //DataInputStream dis = new DataInputStream(in);
        //ub = 0;// reset to arrUb then okay?
        // Keep listening to the InputStream while connected
        while (true) {
            time++;// Read from the InputStream; .read(btbuffer)
            val = ins.nextDouble();//dis.readDouble();// 
            Log.d(TAG, val+"");
			if(buffer[cur].arrUb < 150) {
   				buffer[cur].arr[buffer[cur].arrUb++] = val;
			}
   			else
   			{
   				/*new Thread(new Runnable(){
   					int now;
   					{
   						now = cur;
   					}
					@Override
					public void run() {
						buffer[now].doAll();
					}
   				}).start();
   				Intent intent = new Intent(ctx, classificationService.class);
	            intent.putExtra("index",cur);
	            ctx.startService(intent);
	            //
   				cur = cur==0?1:0;
   				buffer[cur].reset();
   				buffer[cur].arr[buffer[cur].arrUb++] = val;
   				//ub = 1;
   			}
   			notifier.notifyObservers();
   			//remove observer and add data points to line graph here only?
    	}
    }*/
   	
   	protected void appendToBuffer(double dval)
   	{
   		val = dval;
   		Log.d(TAG, "added to buffer : "+val+" at "+time);
   		time++;
   		// check if buffer being used is full
        if(buffer[cur].arrUb < 150) {
				buffer[cur].arr[buffer[cur].arrUb++] = val;
		}
		else
		{
			//perform classification using service
			Intent intent = new Intent(ctx, classificationService.class);
			intent.putExtra("index",cur);
			ctx.startService(intent);
			//use the other buffer, reset it first
			cur = cur==0?1:0;
			buffer[cur].reset();
			buffer[cur].arr[buffer[cur].arrUb++] = val;
		}
		notifier.notifyObservers();
		//remove observer and add data points to line graph here only?
   	}
   	
   	/*
   	//XYSeries stuff of androidplot
   	@Override
	public String getTitle() {
		return "tds2";
	}
   	
   	@Override
	public int size() {
		return buffer[cur].arrUb; //ub; //1 //blocksize
	}
	
	@Override
	public Number getX(int index) {
		if (index >= blocksize) {
			throw new IllegalArgumentException();
	    }
		//System.out.println("getX - index : "+index +" time : "+time);
		return  time+index-buffer[cur].arrUb; //-ub //blockno*150+index;
	}
	    
	@Override
	public Number getY(int index) {
		if (index >= blocksize){
	   		throw new IllegalArgumentException();
	     }
	   	 return buffer[cur].arr[index];
	}
	*/
   	
	//observer pattern
    class dsObservable extends Observable{
		@Override
		public void notifyObservers() {
			setChanged();
			super.notifyObservers(new float[]{(float) time-1, (float)buffer[cur].arr[buffer[cur].arrUb-1]});
		}
	}

    private dsObservable notifier = new dsObservable();
 
    public void addObserver(Observer observer) {
        notifier.addObserver(observer);
    }
 
    public void removeObserver(Observer observer) {
        notifier.deleteObserver(observer);
    }

    public long getTime() {
		return time;
	}

}

//Context is not parcelable or serializable
