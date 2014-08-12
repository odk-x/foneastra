/*
 * Copyright (C) 2014 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.sensors.temperaturedemo;

import java.util.List;

import org.opendatakit.sensors.service.BaseActivity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class DisplayTempActivity extends BaseActivity {

	private static final String TAG = "HMBActivity";
	private final int SENSOR_CONNECTION_COUNTER = 20;		
	
	static final String TEMP_SENSOR_ID_STR = "tempSensorID";
	
	//display messages
	static final String CONN_SUCCESS = "Press 'Start' to begin";
	static final String CONN_ERROR = "Connection error.\nPlease try again.\nMake sure probe is turned on";
	static final char DEGREE_SYMBOL = '\u00B0';
	
	static final String INITIAL_TEMPERATURE = "0.0" + DEGREE_SYMBOL + "C";									
	
	TextView tempFieldView, timeElapsedField;
	
	private TextView connectionStatus;
	Button startButton;	
	
	private WorkerThread workerThread;
	private ConnectionThread connectionThread;
	private String tempSensorID;
	private boolean startState = false;	
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {		
		Log.d(TAG,"onCreate");
		super.onCreate(savedInstanceState);

		setContentView(R.layout.temperature_view);

		startButton = (Button)findViewById(R.id.startButton);
		tempFieldView = (TextView) findViewById(R.id.tempField);		
		connectionStatus = (TextView) findViewById(R.id.connectionStatus);
		timeElapsedField = (TextView) findViewById(R.id.timeElapsedField);
		
		hideWidgets();
		startButton.setEnabled(true);
		
		SharedPreferences appPreferences = getPreferences(MODE_PRIVATE);

		if(tempSensorID == null) { //this just gets me around for testing, can hard code ID before this. 
			if(appPreferences.contains(TEMP_SENSOR_ID_STR)) {
				tempSensorID = appPreferences.getString(TEMP_SENSOR_ID_STR, null);
				if(tempSensorID != null) {
					Log.d(TAG,"restored tempsensorID: " + tempSensorID);
				}
			}
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();		
		inflater.inflate(R.menu.menu, menu);		
			
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.d(TAG,item.toString() + " id: " + item.getItemId());
		switch (item.getItemId()) {
		case R.id.rediscoverFA: 
			launchSensorDiscovery();
			break;
		case R.id.stopProcedureOption: 
			stopProcedure();
			break;
		case R.id.exitMenuOption:
			applicationShutdownActions();
			break;
		}
		return true;
	}

	@Override
	public void onRestart() {
		super.onRestart();
		Log.d(TAG,"onRestart");
	}	

	@Override
	public void onStart() {
		super.onStart();
		Log.d(TAG,"onStart");		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG,"onResume");

		if(tempSensorID == null)  {
//			launchSensorDiscovery();
			this.showDiscoveryDialogMsg();

		}		
		else {

			if(workerThread == null || !workerThread.getIsWorkerRunning()) {
				startButton.setEnabled(true);
				showWidgets();
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG,"onPause");
		if(connectionThread != null && connectionThread.isAlive())
			connectionThread.stopConnectionThread();
	}

	@Override
	public void onStop() {
		super.onStop();
		Log.d(TAG,"onStop");
	}

	@Override
	public void onDestroy() {
		Log.e(TAG,"onDestroy");
//		super.onDestroy();
		applicationShutdownActions();
	}
	
	@Override 
	public void onBackPressed() {
		Log.d(TAG,"onBackPressed");
		applicationShutdownActions();
	}
	
	List<Bundle> getSensorData(String sensorID) throws RemoteException {
		
		return super.getSensorData(sensorID,1);		
	}
	
	public void enableStartButton() {
		startButton.setEnabled(true);
	}
	
	public void applicationShutdownActions() {

		Log.e(TAG,"activityShutdownActions");
		
		
		if(workerThread != null && workerThread.isAlive() ) {
			workerThread.stopWorker();			
		}
		
		if(connectionThread != null && connectionThread.isAlive() ) {
			connectionThread.stopConnectionThread();
		}
		
		try {			
			if(tempSensorID != null)
				stopSensor(tempSensorID);
		}
		catch(RemoteException rex) {
			Log.d(TAG,"in activityShutdownActions. mwProxy.stopSensor raised exception");
		}
		
//		finish();
		super.onDestroy();
		
		System.runFinalizersOnExit(true);
		System.exit(0);				
	}
		
	public void startAction(View view) {
		if(tempSensorID == null) {
//			launchSensorDiscovery();
			this.showDiscoveryDialogMsg();
		}
		else {
			try {
				if (!isConnected(tempSensorID)) {			
					startConnectionThread();
					return; 
				}

				connectionStatus.setVisibility(View.INVISIBLE);

				if (!startState) {    			
					doStartActions();
				}

				else {
//					doStopActions();
//					stopProcedure();
//					stopBeeping();
				}
			}
			catch(RemoteException rex) {
				rex.printStackTrace();
			}
		}
	}		
	
	private void stopProcedure() {
		if(workerThread != null)
			workerThread.stopWorker();
		
		try {
			doStopActions();
		}
		catch(RemoteException rex) {
			rex.printStackTrace();
		}
	}
	
//	private void stopBeeping() {
////		workerThread.disableBeeping();
//		startButton.setEnabled(false);
//	}

	private void doStartActions() throws RemoteException {
		
		startSensor(tempSensorID);		
				
		startWorkerThread();
		
		startButton.setEnabled(false);
		startState = true;
		
		tempFieldView.setText(INITIAL_TEMPERATURE);
		showWidgets();
	}

	void doStopActions() throws RemoteException {		
		stopSensor(tempSensorID);
		
		startState = false;
		
		runOnUiThread(new Runnable() {
			public void run() {
				startButton.setText("Start");
				startButton.setEnabled(true);
			}
		});	
	}
	
	private void showDiscoveryDialogMsg() {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);
		
		// Positive Button & Negative Button & handler
		alert.setMessage("Click OK to install temp probe driver");
		alert.setPositiveButton("OK", discoveryDiagListener);
		alert.setNegativeButton("Cancel", discoveryDiagListener);
		alert.show();
	}
	
	private final android.content.DialogInterface.OnClickListener discoveryDiagListener = new DialogInterface.OnClickListener() {

		public void onClick(DialogInterface dialog, int whichButton) {

			Log.d(TAG,"discovery dialog callback");
			// Lookup Views And Selector Item			

			switch (whichButton) {

			// On Positive Set The Text W/ The ListItem
			case DialogInterface.BUTTON_POSITIVE:
				launchSensorDiscovery();
				// Just Leave On Cancel
			case DialogInterface.BUTTON_NEGATIVE:
				dialog.cancel();
				break;
			}
		}
	};
		
	private void hideWidgets() {
		tempFieldView.setVisibility(View.INVISIBLE);
		connectionStatus.setVisibility(View.INVISIBLE);
	}
	
	private void showWidgets() {
		tempFieldView.setVisibility(View.VISIBLE);
		connectionStatus.setVisibility(View.INVISIBLE);
	}
	
	private void startWorkerThread() {
		workerThread = new WorkerThread(this,tempSensorID); 
		workerThread.start();
	}
	
	private void startConnectionThread() {
		if(connectionThread != null && connectionThread.isAlive()) {
			connectionThread.stopConnectionThread();
		}
		
		hideWidgets();		
		
		connectionThread = new ConnectionThread(this);
		connectionThread.start();
	}
	
	
	class ConnectionThread extends Thread {
		private String TAG = "ConnectionThread";
		private boolean isConnectedToSensor, isConnThreadRunning;
		private ProgressDialog connectingDlg;
		private Context appContext;
		
		public ConnectionThread(Context context) {
			super("Connection Thread");
			appContext = context;
		}
		
		@Override
		public void start() {
			isConnThreadRunning = true;
			
			connectingDlg = ProgressDialog.show(appContext, "Progress",
					"Connecting. Please wait...", true);
			super.start();
		}
		
		public void stopConnectionThread() {				
			isConnThreadRunning = false;

			try {
				this.interrupt();
				isConnectedToSensor = false;
				Thread.sleep(250);
			}
			catch(InterruptedException iex) {
				Log.d(TAG,"stopConnectionThread got interrupted");
			}

			if(connectingDlg != null) 
				connectingDlg.dismiss();
		}

		public void run() {
			int connectCntr = 0;
						
			try {				
				sensorConnect(tempSensorID, false);		
			
			while (isConnThreadRunning && (connectCntr++ < SENSOR_CONNECTION_COUNTER)) {						
				try {					

					if(isConnected(tempSensorID)) {
						isConnectedToSensor = true;
						break;
					}
					Log.d(TAG,"connectThread waiting to connect to sensor");
					Thread.sleep(1000);    					
				}
				catch(InterruptedException iex) {
					Log.d(TAG, "interrupted");	
				}
			}

			Log.d(TAG,"connectThread connect status: " + isConnectedToSensor);

			runOnUiThread(new Runnable() {
				public void run() {
					if(connectingDlg != null) {
						connectingDlg.dismiss();
					}
					if(isConnectedToSensor) {
//						startButton.setEnabled(true);
						connectionStatus.setText(CONN_SUCCESS);
						connectionStatus.setVisibility(View.VISIBLE);
						try {
							doStartActions();						
						}
						catch(RemoteException rex) {
							rex.printStackTrace();
						}
					}
					else {
						connectionStatus.setText(CONN_ERROR);
						connectionStatus.setVisibility(View.VISIBLE);
					}
				}});    			    			
			}
			catch(RemoteException rex) {
				rex.printStackTrace();
			}
		}
	}

	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		Log.d(TAG,"result code: " + resultCode + "  and  requestCode: "+ requestCode);

		if (requestCode == SENSOR_DISCOVERY_RETURN) {
			//from addSensorActvitity
			if (resultCode == RESULT_OK) {	
				// Get sensor id and state from result
				if (data.hasExtra("sensor_id"))
					tempSensorID = data.getStringExtra("sensor_id");
				else
					tempSensorID = null;

				if(tempSensorID != null ) {
					Log.d(TAG, "sensor discovered: " + tempSensorID);

					SharedPreferences.Editor prefsEditor = getPreferences(MODE_PRIVATE).edit();
					prefsEditor.putString(TEMP_SENSOR_ID_STR, tempSensorID);
					if(prefsEditor.commit()) 
						Log.d(TAG,"saved tempSensorID to preferences");
					else 
						Log.e(TAG,"preferences commit failed for tempSensorID");

					try {
						// Initiate connection
						sensorConnect(tempSensorID,false);
						startConnectionThread();
					}
					catch(RemoteException rex) {
						rex.printStackTrace();
					}
				}
				else {
					tempFieldView.setText("activity result returned without sensorID");
				}
			}
		}
	}		
}
