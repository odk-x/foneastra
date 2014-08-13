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

package org.opendatakit.sensors.drivers.foneastra.bt.temperature;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.SimpleTimeZone;

import org.opendatakit.sensors.DataSeries;
import org.opendatakit.sensors.SensorDataPacket;
import org.opendatakit.sensors.SensorDataParseResponse;
import org.opendatakit.sensors.drivers.AbstractDriverBaseV2;

import java.text.ParseException;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

public class OWTemperatureSensor extends AbstractDriverBaseV2 {

	private static final String TAG = "OWTemperatureSensor";
	private static final String sensor_type = "Temperature";
	private static final String msg_type = "Celsius";
	private static final boolean DEBUG = false;
	private float SENSOR_RESOLUTION = 0.0625F; // 12 bit precision ds18b20
												// sensor
	private int DATABITSMASK = 0x7FF; // 11 bits of data excluding the sign bit
	private int SIGNBITMASK = 0x800; // 12th bit is the sign bit
	
	private static final int PAYLOAD_SIZE = 256; //including crc
	private static final int SYNC_BYTE = 0xaa; //10101010
	private static final int MAX_SYNC_BYTES = 4;
	
	//message types
	private static final int MT_SINGLE_READING = 1; //1 temp reading per msg
	private static final int MT_BULK_TRANSFER = 2; //variable size. will have more metadata
//	private static final int MT_COLLECT_ALL = 3;
//	private static final int MT_COLLECT_ONE = 4;
//	private static final int MT_DELETE_ALL = 5;
//	private static final int MT_DELETE_ONE = 6;
	
	
	int syncCounter = 0;
	int payloadCounter = 0;
	byte[] payloadBuffer;
	
	private enum ParsingState {
		SYNCING,
		SYNCED,
		PARSING_PAYLOAD
	}
	
	private FileOutputStream logger;
	private ParsingState state = ParsingState.SYNCING;

	public OWTemperatureSensor() {		
		
		if(DEBUG) {
			long now = System.currentTimeMillis();
			File directory = Environment.getExternalStorageDirectory();
			File logFile = new File(directory, TAG + now + ".txt");

			try {
				logger = new FileOutputStream(logFile, true);
			}
			catch(IOException ioe) {
				ioe.printStackTrace();
			}
		}
		String str = "PAYLOAD_SIZE: " + PAYLOAD_SIZE + " MAX_SYNC_BYTES: " + MAX_SYNC_BYTES;
		Log.d(TAG," constructed. " + str);
		writeToFile(str);
	}
	
	void writeToFile(String str) {
		if(DEBUG) {
			if(logger != null) {
				String ts = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM).format(new Date());
				try {
					String toWrite = ts + " : " + str + "\n";
					logger.write(toWrite.getBytes());
					logger.flush();
				}
				catch(IOException ioe) {
					ioe.printStackTrace();
				}
			}
		}
	}	

	@Override
	public SensorDataParseResponse getSensorData(long maxNumReadings, List<SensorDataPacket> rawData, byte [] remainingData) {
		
		List<Bundle> allData = new ArrayList<Bundle>();
		
//		Log.d(TAG," sensor driver. sdp list sz: " + rawData.size());
		List<Byte> dataBuffer = new ArrayList<Byte>();
		
		// Add the new raw data
		for(SensorDataPacket pkt: rawData) {
			byte [] payload = pkt.getPayload();
//			Log.d(TAG, " sdp length: " + payload.length);

			for (int i = 0; i < payload.length; i++) {
				dataBuffer.add(payload[i]);
			}			
		}
		
		parseData(dataBuffer,allData);
		
		return new SensorDataParseResponse(allData, null);
	}
	
	private void parseData(List<Byte> dataBuffer, List<Bundle> parsedDataBundles) {
		
//		Log.d(TAG,"parseData. dataBuffer len: " + dataBuffer.size());
		while(dataBuffer.size() > 0) {
			byte aByte = dataBuffer.remove(0);
			int maskedByte = aByte & 0xff;
		
			switch(state) {
			case SYNCING:
//				Log.d(TAG,"SYNCING");
				if(maskedByte == SYNC_BYTE)					
					++syncCounter;			
				else 
					syncCounter = 0;
				
				if(syncCounter >= MAX_SYNC_BYTES) {
					syncCounter = 0;
					state = ParsingState.SYNCED;
				}
				break;
			case SYNCED:
				//might have more sync bytes. ignore them
//				Log.d(TAG,"SYNCED");
				if(maskedByte != SYNC_BYTE) {
					payloadBuffer = new byte[PAYLOAD_SIZE];
					payloadBuffer[payloadCounter++] = aByte;
					state = ParsingState.PARSING_PAYLOAD;
				}
				break;
			case PARSING_PAYLOAD:
				if(payloadCounter == PAYLOAD_SIZE) {
					//we have a complete packet. process it.										
					processCompletePacket(parsedDataBundles);
					payloadCounter = 0;
					state = ParsingState.SYNCING;
				}
				else {
					payloadBuffer[payloadCounter++] = aByte;
				}
				break;
			}
		}
	}
	
	private void processCompletePacket(List<Bundle> parsedDataBundles) {
		//mt, seqNo, msg, crc
//		Log.d(TAG,"processCompletePacket payloadCounter: " + payloadCounter);
		StringBuffer strBuff = new StringBuffer();
		String logStr;
		
		int msgType = payloadBuffer[0] & 0xff;
		int seqLow = payloadBuffer[1] & 0xff;
		int seqHi = payloadBuffer[2] & 0xff;
		int seqNo = seqHi << 8 | seqLow;
		int receivedCRC = payloadBuffer[PAYLOAD_SIZE - 1] & 0xff;
		byte calcCRC = 0;
		
		for(int i = 0; i < (PAYLOAD_SIZE -1); i++) {
			strBuff.append((payloadBuffer[i] & 0xff) + " ");
			calcCRC = (byte) (calcCRC ^ payloadBuffer[i]);
		}
		strBuff.append(receivedCRC);
		
		int maskedCalcCRC = calcCRC & 0xff;
		String str = "pkt no: " + seqNo + " crc rcvd: " + receivedCRC + " crc calculated: " + maskedCalcCRC;			
		
		if(maskedCalcCRC == receivedCRC) {
			logStr = "PASSED. ";
			switch(msgType) {
			case MT_SINGLE_READING:
				Log.d(TAG,"Got SINGLE_READING msg");
				Bundle tempReading = getTempSample(payloadBuffer[4],payloadBuffer[3]);
				parsedDataBundles.add(tempReading);
;				break;
			case MT_BULK_TRANSFER:
				Log.d(TAG,"Got BULK_TRANSFER msg");
				//12 bytes of filename, START,timestamp,data, END
				break;
			default: 
				Log.d(TAG,"unknown msgType received: " + msgType);
			}
			
		}
		else {
			logStr = "FAILED. ";
		}
		
		logStr += str;
		Log.d(TAG,logStr);
		Log.d(TAG,strBuff.toString());
		writeToFile(logStr);
		writeToFile(strBuff.toString());
	}
	
	Bundle getTempSample(byte high, byte low) {

		int msByte, lsByte;
		char signchr = '+';

		msByte = (high & 0xff); 	// mask off sign bit and															 
									// prevent sign bit extension due to promotion			
		lsByte = (low & 0xff);

		int concat = ((msByte << 8) | lsByte); // 16 bit scratchpad register value
		
		concat = concat & 0xffff;
		if ((concat & SIGNBITMASK) == SIGNBITMASK) {
			// negative temp
			concat = ~concat + 1;// (concat ^ 0xffff) + 1;
			signchr = '-';
		}
		
		int databits = DATABITSMASK & concat;

		float temp = SENSOR_RESOLUTION * databits;
		// System.out.printf("temp is: %f\n",temp);

		String tempstr = signchr + Float.toString(temp);

		Log.d(TAG, " temp raw bytes: hi: " + msByte + " lo: " + lsByte + " decoded: " + tempstr);

		Bundle sample = new Bundle();

		sample.putString(DataSeries.SAMPLE, tempstr);
		sample.putInt("raw_hi", msByte);
		sample.putInt("raw_low", lsByte);
		
		// Add timestamp for the sensor reading
		String timeStamp = nanoSecondsFromMillis(new Date().getTime());
		// CAL: The ODK Sensors jar may have to be rebuilt to use this
		sample.putString("timestamp", timeStamp);
		
		// Add sensor type for the sensor reading
		sample.putString(DataSeries.SENSOR_TYPE, sensor_type);
		
		// Add in unit for temperature reading 
		sample.putString(DataSeries.MSG_TYPE, msg_type);
		return sample;
	}
	
	  // nanosecond-extended iso8601-style UTC date yyyy-mm-ddTHH:MM:SS.sssssssss
	  private static final String MILLI_TO_NANO_TIMESTAMP_EXTENSION = "000000";

	  public static String nanoSecondsFromMillis(Long timeMillis) {
	    if ( timeMillis == null ) return null;
	    // convert to a nanosecond-extended iso8601-style UTC date yyyy-mm-ddTHH:MM:SS.sssssssss
	    Calendar c = GregorianCalendar.getInstance(new SimpleTimeZone(0,"UT"));
	    SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
	    sf.setCalendar(c);
	    Date d = new Date(timeMillis);
	    String v = sf.format(d) + MILLI_TO_NANO_TIMESTAMP_EXTENSION;
	    return v;
	  }

	  public static Long milliSecondsFromNanos(String timeNanos ) {
	    if ( timeNanos == null ) return null;
	    // convert from a nanosecond-extended iso8601-style UTC date yyyy-mm-ddTHH:MM:SS.sssssssss
	    Calendar c = GregorianCalendar.getInstance(new SimpleTimeZone(0,"UT"));
	    SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
	    sf.setCalendar(c);
	    String truncated = timeNanos.substring(0, timeNanos.length()-MILLI_TO_NANO_TIMESTAMP_EXTENSION.length());
	    Date d;
	    try {
			d = sf.parse(truncated);
	    } catch (ParseException e) {
	      e.printStackTrace();
	      throw new IllegalArgumentException("Unrecognized time format: " + timeNanos);
	    }
	    Long v = d.getTime();
	    return v;
	  }
}
