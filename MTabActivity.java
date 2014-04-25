package com.sleep.apnea;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.app.ActionBar.Tab;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MTabActivity extends FragmentActivity implements ActionBar.OnNavigationListener
{	
	//debugging
	private final static String TAG = "MTabActivity debug";
	 
    //datasource
    private testDataSource tds2 = null;
    private GraphFragment gf = null;
    
    //for navigation
	private ActionBar actionBar;
	private String[] dropdownValues;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mtab);
 
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        //navigating the app
        setupNavigation();
        
        //textview
        statetv = (TextView)findViewById(R.id.devstatxt);
        
        //initialize datasource
        tds2 = testDataSource.getInstance();
        tds2.init(this);
        gf = new GraphFragment();
    } 
    
    //bluetooth stuff 
    
    //Layout Views
    private TextView statetv = null;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the Apnea services
    private static BluetoothApneaService mApneaService = null;//[]    

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "++ ON START ++"+mApneaService);

        // If BT is not on, request that it be enabled.
        // setupApnea() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, BluetoothMessageConstant.REQUEST_ENABLE_BT);
        // Otherwise, setup the Apnea session
        } else {
            if (mApneaService == null) setupApnea();
        }
    }
    
    private void setupApnea() {
        Log.d(TAG, "setupApnea()");
        
        // Initialize the BluetoothApneaService to perform bluetooth connections
        //if(mApneaService is null)
        mApneaService = new BluetoothApneaService(mHandler, getApplicationContext()); //, tds2,this  
    }
    
    @Override
    public synchronized void onResume() {
        super.onResume();
        Log.d(TAG, "+ ON RESUME +");// broadcastmgr registered
        if( mApneaService!=null ){
	        switch (mApneaService.getState()) {
	        case BluetoothApneaService.STATE_CONNECTED:
	        	statetv.setText(getString(R.string.title_connected_to, mConnectedDeviceName));
	            
	            break;
	        case BluetoothApneaService.STATE_CONNECTING:
	        	statetv.setText(R.string.title_connecting);
	            break;
	        case BluetoothApneaService.STATE_LISTEN:
	        case BluetoothApneaService.STATE_NONE:
	        	statetv.setText(R.string.title_not_connected);
	            break;
	        }
        }    	
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "- ON PAUSE -");
        // broadcastmgr un register
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "-- ON STOP --");
    }
    
	@Override
	protected void onDestroy() {
	    super.onDestroy();
        // Stop the Bluetooth Apnea services
        /*if (mApneaService != null){ 
        	mApneaService.stop(); //[0]
        	//mApneaService[1].stop();
        }*/
        Log.d(TAG, "--- ON DESTROY ---");
	}

    //for registering the receiver of bluetoothapneaservice's connected thread
    // handler for received Intents for the "apnea-event" event 
	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
		// Extract data included in the Intent
		byte message = intent.getExtras().getByte("message");
		sendMessage(message); // => cant bcast rcvr static		
		Log.d("receiver", "Got message: " + message);
		}
		
	};
	
	//Sends a message through bluetooth
    private void sendMessage(byte message) {
        // Check that we're actually connected before trying anything
        if ( mApneaService != null &&
        		mApneaService.getState() != BluetoothApneaService.STATE_CONNECTED) {
            Toast.makeText(MTabActivity.this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        mApneaService.write(message);
        if (message == (byte)1) {
            Toast.makeText(MTabActivity.this, "apnea event detected", Toast.LENGTH_SHORT).show();
        }
    }
    
    // The Handler that gets information back from the BluetoothApneaService
    //warning change to static as leaks may occur - not poss coz of actionbar
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case BluetoothMessageConstant.MESSAGE_STATE_CHANGE:
                Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                    case BluetoothApneaService.STATE_CONNECTED:
                    	statetv.setText(getString(R.string.title_connected_to, mConnectedDeviceName));
		            	//actionBar.setSubtitle(getString(R.string.title_connected_to, mConnectedDeviceName));
		                
		                break;
		            case BluetoothApneaService.STATE_CONNECTING:
		            	statetv.setText(R.string.title_connecting);
		            	//actionBar.setSubtitle(R.string.title_connecting);
		                break;
		            case BluetoothApneaService.STATE_LISTEN:
		            case BluetoothApneaService.STATE_NONE:
		            	statetv.setText(R.string.title_not_connected);
		            	//actionBar.setSubtitle(R.string.title_not_connected);
		                break;
		        }
	            break;

            case BluetoothMessageConstant.MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(BluetoothMessageConstant.DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case BluetoothMessageConstant.MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(BluetoothMessageConstant.TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            case BluetoothMessageConstant.MESSAGE_REG_RECEIVER :
            	Log.d(TAG, "handler broadcastmgr registered");
                // Register mMessageReceiver to receive messages.
                LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mMessageReceiver,
                		new IntentFilter(BluetoothMessageConstant.APNEA_EVENT));
            	break;

            case BluetoothMessageConstant.MESSAGE_UNREG_RECEIVER :
            	Log.d(TAG, "handler broadcastmgr un registered");
                // /UnRegister mMessageReceiver to receive messages.
            	LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mMessageReceiver);
            	break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case BluetoothMessageConstant.REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK && data!=null) {
                connectDevice(data);
            }
            break;
        case BluetoothMessageConstant.REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a Apnea session
                setupApnea();
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void connectDevice(Intent data) {
    	Log.d(TAG,"connectDevice");
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        
        // Attempt to connect to the device - should this dep on the uuid or friendly name?
        if(mApneaService.getState() == BluetoothApneaService.STATE_NONE || 
        		mApneaService.getState() == BluetoothApneaService.STATE_LISTEN){ //none or listening
        		mApneaService.connect(device);
        }
    	/*else if(mApneaService[1].getState() == BluetoothApneaService.STATE_NONE){
    		mApneaService[1].connect(device);
		}*/
    }

    private void stopMonitoring() {
        // Stop the Bluetooth Apnea services
        if (mApneaService != null){ 
        	mApneaService.stop(); //[0]
        	//mApneaService[1].stop();
        }
    }
    
    
    //Options Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	getMenuInflater().inflate(R.menu.mtab, menu);
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
	        case R.id.secure_connect_scan:
	            // Launch the DeviceListActivity to see devices and do scan
	            serverIntent = new Intent(this, DeviceListActivity.class);
	            startActivityForResult(serverIntent, BluetoothMessageConstant.REQUEST_CONNECT_DEVICE_SECURE);
	            return true;
	        case R.id.preferences:
	            // Launch settings activity
	            Intent i = new Intent(this, SettingsActivity.class);
	            startActivity(i);
	        	return true;
	        case R.id.startstop:
	        	//stop bluetooth comm
	        	if(mApneaService.getState() != BluetoothApneaService.STATE_NONE){
	        		stopMonitoring();
	        		tds2.reset(); //reset
	        	}
	        	return true;
        }
        return false;
    }
	
	
    //settting up the navigation
    private void setupNavigation(){
    	actionBar = getActionBar();
    	
    	// Set up the action bar to show a dropdown list.
    	actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
    	
    	dropdownValues = getResources().getStringArray(R.array.action_list);

        // Specify a SpinnerAdapter to populate the dropdown list.
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(actionBar.getThemedContext(),
            android.R.layout.simple_spinner_item, android.R.id.text1, dropdownValues);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    	// Set up the dropdown list navigation in the action bar.
        actionBar.setListNavigationCallbacks(adapter, this);
    }
    
	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		// Create new fragment from our own Fragment class
    	Fragment newFragment = null;
    	
    	//getSupportFragmentManager().findFragmentByTag(dropdownValues[itemPosition]);
    	//searches fragments of manager's activity and the backstack, mtab has only 1 frag &
    	//we never put it into backstack since we don't want back button to take it back to last frag
    	
    	//if(newFragment == null) {
		Log.d(TAG,"creating frag "+dropdownValues[itemPosition]);
    	switch(itemPosition) {
    		case 0: newFragment = gf; //new GraphFragment();
    			break;
    		case 1: newFragment = new ApneaFragment();
    			break;
    		case 2: newFragment = new FileListFragment();
    			break;
    	}
    	//}
    	
    	FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
    	// Replace whatever is in the fragment container with this fragment
    	//  and give the fragment a tag name equal to the string at the position selected
    	ft.replace(R.id.fragment_container, newFragment, dropdownValues[itemPosition]);
    	// Apply changes
    	ft.commit();
    	return true;
	}
}

/*
class TabsPagerAdapter extends FragmentPagerAdapter 
	implements ActionBar.TabListener, ViewPager.OnPageChangeListener  
{
	private Fragment[] frags; 
    private ViewPager viewPager;
	private ActionBar actionBar;
 
	public TabsPagerAdapter(Activity act, ViewPager vpg, FragmentManager fm)
	{
		super(fm);
		actionBar = act.getActionBar();
		viewPager = vpg;
		viewPager.setAdapter(this);
		viewPager.setOnPageChangeListener(this);
		frags = new Fragment[2];
		//frags[0] = new GraphFragment();
		//frags[1] = new PillowdataFragment();
		//make it lazy
	}
	
	public void addtab(String tab_name){ //, Fragment frag
		actionBar.addTab(actionBar.newTab().setText(tab_name)
                .setTabListener(this));
		//frags[] = frag
	}
	
	//adapter stuff
	
    @Override
    public Fragment getItem(int index) {
    	Log.d("Tabs","getItem");
    	
    	//if(index==1 || index==0)
    	//	return frags[index];
    	
        switch (index) {
        case 0:
        	if(frags[0] == null)
        		frags[0] = new GraphFragment(); //getInstance
            return frags[0];
        case 1:
        	if(frags[1] == null)
        		frags[1] = new ApneaFragment();//new PillowdataFragment();
            return frags[1];
        }
        return null;
    }
 
    @Override
    public int getCount() {
        // get item count - equal to number of tabs
        return 2;
    }
    
    //ActionBar.TabListener stuff
    
    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
    }
 
    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        // on tab selected show respective fragment view
        viewPager.setCurrentItem(tab.getPosition());
    }
 
    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    }
 
    //ViewPager.OnPageChangeListener
    @Override
    public void onPageSelected(int position) {
        // on changing the page make respected tab selected
        actionBar.setSelectedNavigationItem(position);
    }

    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) {
    }

    @Override
    public void onPageScrollStateChanged(int arg0) {
    }
    
}

//in oncreate
     
    // Initialization
    viewPager = (ViewPager) findViewById(R.id.viewpgr);
    actionBar = getActionBar();
    tpAdapter = new TabsPagerAdapter(this, viewPager, getSupportFragmentManager());
 
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);        
 
    // Adding Tabs
    for (String tab_name : tabs) {
        tpAdapter.addtab(tab_name);
    }

*/


/*//not working
@Override
public boolean onPrepareOptionsMenu(Menu menu) {
	MenuItem item = menu.findItem(R.id.startstop);
	if(mApneaService != null)
	{
		int status = mApneaService.getState(); 
	    switch (status) {
	        case BluetoothApneaService.STATE_CONNECTED:
	        	item.setIcon(R.drawable.greenledindicator);                
	            break;
	        case BluetoothApneaService.STATE_CONNECTING:
	            break;
	        case BluetoothApneaService.STATE_LISTEN:
	        case BluetoothApneaService.STATE_NONE:
	        	item.setIcon(R.drawable.redledindicator);
	            break;
	    }
	}
	return super.onPrepareOptionsMenu(menu);
}*/


// handler for received Intents for the "state change" event 
	/*private BroadcastReceiver mStateChangeReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			//Extract data included in the Intent
			int msg = intent.getExtras().getInt(STATE_CHANGE);
    	//case MESSAGE_STATE_CHANGE:
        Log.i(TAG, "Receiver Got message: " + msg);
        switch (msg) {
            case BluetoothApneaService.STATE_CONNECTED:
            	actionBar.setSubtitle(getString(R.string.title_connected_to, mConnectedDeviceName));
                
                break;
            case BluetoothApneaService.STATE_CONNECTING:
            	actionBar.setSubtitle(R.string.title_connecting);
                break;
            case BluetoothApneaService.STATE_LISTEN:
            case BluetoothApneaService.STATE_NONE:
            	actionBar.setSubtitle(R.string.title_not_connected);
                break;
        }
        //break;
		}
	};*/
