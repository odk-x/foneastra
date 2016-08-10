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

import org.opendatakit.sensors.DataSeries;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

class WorkerThread extends AsyncTask<Void, Void, Void> {

	private static final String TAG = "WorkerThread";

	private final DisplayTempActivity displayTempActivity;
	private String sensorID;

	private static final int SLEEP_TIME = 1 * 1000; // secs

	private float currentTempForCalc, currentMaxTemp = 0;
	private Float tempForDisplay;
	private String temperatureReading;
	private long totalSecsElapsed = 0, mins = 0, secs = 0;

	private long startTime;
	
	private volatile boolean isWorkerRunning = false;

	public WorkerThread(DisplayTempActivity milkBankActivity, String tempSensor) {
		this.displayTempActivity = milkBankActivity;
		sensorID = tempSensor;
		startTime = System.currentTimeMillis();
		execute();
		isWorkerRunning = true;
	}

	public void stopWorker() {
		isWorkerRunning = false;
		cancel(true);
	}

	public boolean getIsWorkerRunning() {
		return isWorkerRunning;
	}

	@Override
	protected Void doInBackground(Void... arg0) {

		Log.d(TAG, "starting display thread");

		while (!isCancelled()) {
			totalSecsElapsed = (System.currentTimeMillis() - startTime) / 1000;
			mins = totalSecsElapsed / 60;
			secs = totalSecsElapsed % 60;

			List<Bundle> bundles = null;
			try {
				bundles = displayTempActivity.getSensorData(sensorID);
			} catch (RemoteException rex) {
				rex.printStackTrace();
			}
			if (bundles != null) {
				Log.d(TAG, bundles.size() + " samples received");
				for (Bundle aBundle : bundles) {
					temperatureReading = aBundle.getString(DataSeries.SAMPLE);

					if (temperatureReading != null) {

						currentTempForCalc = Float
								.parseFloat(temperatureReading);

						if (temperatureReading.length() > 6)
							temperatureReading = temperatureReading.substring(
									0, 6);

						tempForDisplay = Float.parseFloat(temperatureReading);

						Log.d(TAG, " current temp: " + currentTempForCalc
								+ " current max: " + currentMaxTemp);

						displayTempActivity.runOnUiThread(new Runnable() {
							public void run() {

								displayTempActivity.tempFieldView
										.setText(String.valueOf(tempForDisplay)
												+ DisplayTempActivity.DEGREE_SYMBOL
												+ " C");
								displayTempActivity.timeElapsedField
										.setText(String.format("%02d", mins)
												+ ":"
												+ String.format("%02d", secs));
							}
						});
					}
				}
			}

			try {
				Thread.sleep(SLEEP_TIME);
			} catch (InterruptedException iex) {
				Log.e(TAG, "thread interrupted");
				iex.printStackTrace();
			}

		}

		return null;
	}

}