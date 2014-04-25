package com.sleep.apnea;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

class BluetoothApneaService //extends Service
{
    // Debugging
    private static final String TAG = "BluetoothApneaService";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothApneaSecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //ffdd6650-b00d-11e3-a5e2-0800200c9a66
    //private static final UUID SerialPortServiceClass_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter; //static
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private BluetoothDevice device; //do we need this?
    private int mState;
	private Context mContext; // reqd?

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new BluetoothApnea session.
     * @param handler  A Handler to send messages back to the UI Activity
     * @param context Application Context
     */
    public BluetoothApneaService(Handler handler, Context ctx) { 
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        mContext = ctx;
    }

    /**
     * Set the current state of the Bluetooth connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothMessageConstant.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the Apnea service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread();
            mSecureAcceptThread.start();
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected, Socket");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }/**/

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothMessageConstant.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothMessageConstant.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop, cancelling all threads");

        //loop through devices and stop?
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte out) { //byte[] out
    	Log.d(TAG,"write data to bt");
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED){
            	Log.d(TAG,"trying to write when not connected");
            	return;
            }
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    protected void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothMessageConstant.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothMessageConstant.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        //if(device == null)
        	BluetoothApneaService.this.start();
        //else
        //	BluetoothApneaService.this.connect(device);
        //didnt make sense since connfailed is called in connect
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    protected void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothMessageConstant.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothMessageConstant.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        //BluetoothApneaService.this.start();
        BluetoothApneaService.this.connect(device);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                        MY_UUID_SECURE);
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "Socket Type: " + "BEGIN mAcceptThread" + this);
            setName("AcceptThread");

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (true) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                	device = socket.getRemoteDevice();
                	connected(socket, device);
                	try {
						mmServerSocket.close();
					} catch (IOException e) {
						Log.e(TAG, "mmServerSocket close() failed", e);
						e.printStackTrace();
					}
                	break;
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread ");

        }

        public void cancel() {
            if (D) Log.d(TAG, "cancel of AcceptThread" + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
        	Log.d(TAG,"in connect thread");
            mmDevice = device;
            BluetoothSocket tmp = null;
            BluetoothApneaService.this.device = device;
            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
            	//Method m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
            	//tmp = (BluetoothSocket) m.invoke(device,1);
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread Socket");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
            	Log.e(TAG, "connection failure", e);
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothApneaService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
        	Log.d(TAG,"in cancel of connect thread");
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private Object sync = new Object();
        private boolean cancelled;
        private volatile boolean waitForAck;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread: ");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            cancelled = false;
            waitForAck = false;
            //sync = this;
            
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
         
            // Send register receiver msg to the UI Activity
            mHandler.obtainMessage(BluetoothMessageConstant.MESSAGE_REG_RECEIVER).sendToTarget();
            //get reference to tds
            testDataSource tds = testDataSource.getInstance();
            //create scanner using stream
          	Scanner in = new Scanner(mmInStream);
          	boolean testing;
          	while(true) {
	            try {
            		// need to read waitForAck in synchronized manner? - dbt
	            	synchronized (sync) {
	            		testing = waitForAck;
	            		Log.d(TAG, "read wfa "+testing);
	            	}
            		Log.d(TAG,sync+" "+sync.hashCode());
	            	if(!testing) {
	            		Log.d(TAG,"no need to wait for ack");
	            		double value = in.nextDouble();
	            		tds.appendToBuffer(value);
	            		Thread.sleep(1000-System.currentTimeMillis()%1000);
	            	}
	            	else {
	            		handleAck(in);
	            	}
	            	//}
	            } catch (Exception e) {
	                Log.e(TAG, "disconnected : connectionLost", e);
	                in.close();
	                // Send unregister receiver msg to the UI Activity            
	                mHandler.obtainMessage(BluetoothMessageConstant.MESSAGE_UNREG_RECEIVER).sendToTarget();
	                // Start the service over to restart connecting mode
	            	if(!cancelled) {
	            		connectionLost();
	            	}
	            	break;
	            }
          	}
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte buffer) { //byte[] buffer
            try {
                mmOutStream.write(buffer);
                // if apnea event then need to wait for acknowledgement
                if(buffer == 1) {
                	Log.d(TAG,sync+" "+sync.hashCode());
                	Log.d(TAG,"waitForAck being set to true");
	                synchronized (sync) {
	                    waitForAck = true;
					}
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
        	Log.d(TAG,"in cancel of connected thread");
            try {
            	cancelled = true;
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
        
        private void handleAck(Scanner in){
        	try {
        		Log.d(TAG,"waiting for ack");
        		long before = System.currentTimeMillis();
        		//read ack
        		byte value = in.nextByte();
        		long after = System.currentTimeMillis();
        		Log.d(TAG,"after - before: "+(after-before));
        		//if pillow has reached max height
        		if(value == -1){
        			Log.d(TAG,"max height reached");
        			//get phone no to call
        			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        			String phnoPref = sharedPref.getString(mContext.getString(R.string.pref_phno), "");
        			//start intent to call caretaker
    				Intent phint = new Intent(Intent.ACTION_CALL);
    				phint.setData(Uri.parse("tel:"+phnoPref));
    				//Context context = new ContextWrapper(null);
    				mContext.startActivity(phint); //need context
    				//should device be stopped here?
    				//stop(); ??
        		}
        		// successfully raised pillow. ack = 1
        		else if(value == 1) {
        			Log.d(TAG,"ack=1, successfully raised pillow");
        			waitForAck = false;
        			//++height;
            		Thread.sleep(5000); //allow sometime for recovering.  reqd here?
        		}
        		else {
        			//ack not recd, not reached max height, try again
    				Log.d(TAG,"not reached max height, try again");
    				//write((byte)1);	//sync block in this too - reentrant X, blocked?
        		}
                // Send a message back to the Activity
                Message msg = mHandler.obtainMessage(BluetoothMessageConstant.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(BluetoothMessageConstant.TOAST, "Received ack value : "+value);
                msg.setData(bundle);
                mHandler.sendMessage(msg);
                
        	} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
    }
}

//ffdd6650-b00d-11e3-a5e2-0800200c9a66
//changed start to connect when connection is lost in connected thread - for this one needs to remember device
/*
changed boolean waitforack to volatile - still no difference
read online that The Java memory model guarantees that each thread entering a synchronized block 
of code sees the effects of all previous modifications that were guarded by the same lock - still no diff 
okay so, classification which is done in the intent thread takes some time and after that the message is sent to 
the arduino, in the meantime read has already been called in connect thread  
can do - thread sleep for till next sec
now it is read at the right time : after being set, but still not working
created local var which is set to waitforack
works! but failure still possible if threads interleave in a certain manner
failed! that sort of interleaving did happen see log4.txt
 */
/*
//pillow values
private int height = 0;
private final int maxheight = 3;

int count = 4; //wait for
int value = 0; //assume failed
//waiting for ack
while(!in.hasNext() && count!=0){
	Thread.sleep(1500);
	--count;
}

*/
