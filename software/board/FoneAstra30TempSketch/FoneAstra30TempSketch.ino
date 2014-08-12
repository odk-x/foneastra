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

#include <OneWire.h>
//#include <SoftwareSerial.h>

#include "foneastrapins.h"

const uint8_t SYNC_BYTE = 0xaa; //10101010
const int NO_OF_SYNC_BYTES = 10;
const int PAYLOAD_SIZE = 256;
const int MAX = NO_OF_SYNC_BYTES + PAYLOAD_SIZE;

//payload structure of MT_SINGLE_READING is: 
//MSG_TYPE (1 byte), SEQ_NO (2 bytes), MSG_DATA (PAYLOAD_SIZE - 4 bytes) CRC (1 byte)

//XXX should be using the same payload structure, MSG_DATA_BEGIN_INDEX for both MT_SINGLE_READING & 
//MT_BULK_TRANSFER. But not doing that just yet!

const int PAYLOAD_BEGIN_INDEX = NO_OF_SYNC_BYTES;
const int MSG_DATA_BEGIN_INDEX = PAYLOAD_BEGIN_INDEX + 3;

//message types
const int MT_SINGLE_READING = 1; //1 temp reading per msg
//const int MT_BULK_TRANSFER = 2; //variable size. will have more metadata
//const int MT_COLLECT_ALL = 3;
//const int MT_COLLECT_ONE = 4;
//const int MT_DELETE_ALL = 5;
//const int MT_DELETE_ONE = 6;

const int SAMPLING_INTERVAL = 1000; //millisecs.

// 1-wire sensors are connected on ONEWIRE_PIN
OneWire  owTempSensor(ONEWIRE_PIN); 
//SoftwareSerial dbgSerial(SW_UART_RX_PIN, SW_UART_TX_PIN); // RX, TX

uint8_t dataArray[MAX];
uint16_t counter = 0;
uint8_t msgType = MT_SINGLE_READING;
uint8_t present = 0;
uint8_t owbData[12];
uint8_t addr[8];

void setup(void) {
	//set up the HW UART to communicate with the BT module
	Serial.begin(115200);
//	Serial.begin(38400);
//	Serial.begin(57600);
	// set up the sofrware UART. haven't gotten this to work at 115200
	//but 57600 is just fine for the debug console.
//	dbgSerial.begin(57600);  

	//this tells us when a BT device connects to foneastra
	pinMode(INT0_PIN, INPUT);

	//this tells us when the tactile switch is pressed
	pinMode(INT1_PIN, INPUT);

	//this tells us the battery level
	pinMode(BATT_LVL_PIN, INPUT);

	//this tells us whether or not wall power is present
	pinMode(WALL_PIN,INPUT);

	//this controls power to the BT module
	pinMode(BT_PWR_PIN,OUTPUT);
	
	//LED pins as output
	pinMode(RED_LED_PIN,OUTPUT);
	pinMode(GREEN_LED_PIN,OUTPUT);
	
	digitalWrite(GREEN_LED_PIN,HIGH);

//	showBattLvl();		
	
	memset(dataArray,0,sizeof(dataArray));
	for(int i = 0;i < PAYLOAD_BEGIN_INDEX;i++) {
		dataArray[i]=SYNC_BYTE;
	}

	findSensorAndTurnBTOn();
}

//void showBattLvl() {
//	//FA has a 4.2V Li-Ion battery. determined the hard coded values below based on experiments. 
//	
//	//read 10 bit ADC
//	uint16_t battLvl = analogRead(BATT_LVL_PIN);
//	int8_t blnkCnt = 0;
//	
//	if(battLvl >= 310) {
//		blnkCnt = 5;
//	}
//	else if((battLvl >= 290) & (battLvl < 310) ) {
//		blnkCnt = 4;
//	}
//	else if((battLvl >= 270) & (battLvl < 290)) {
//		blnkCnt = 3;
//	}
//	else if(battLvl < 270) {
//		blnkCnt = 1;
//	}
//	
////	dbgSerial.print(F("Batt Lvl: "));dbgSerial.print(battLvl);dbgSerial.print(F(" Blink cnt: "));dbgSerial.print(blnkCnt);
////	dbgSerial.println();
//	
//	for(int8_t i = 0; i< blnkCnt;i++) {
//		digitalWrite(RED_LED_PIN,HIGH);
//		delay(500);
//		digitalWrite(RED_LED_PIN,LOW);
//		delay(500);
//	}
//	
//}

void loop(void) {

	byte i; 
	float celsius;

	if(present && (digitalRead(INT0_PIN) == HIGH)) {

		unsigned long startreading = millis();

		owTempSensor.reset();
		owTempSensor.select(addr);
		owTempSensor.write(0x44,1);         // start conversion, with parasite power on at the end

		byte status = owTempSensor.read();

		while (!status) {
			delay(10);
			status = owTempSensor.read();
		}

		owTempSensor.reset();
		owTempSensor.select(addr);    
		owTempSensor.write(0xBE);         // Read Scratchpad

		for ( i = 0; i < 9; i++) {           // we need 9 bytes
			owbData[i] = owTempSensor.read();
		}

		unsigned long sensorReadTime = millis() - startreading;

		// convert the data to actual temperature
		unsigned int raw = (owbData[1] << 8) | owbData[0];

		celsius = (float)raw / 16.0;

		//send data over BT if there is an active connection
		if(digitalRead(INT0_PIN) == HIGH)  {
			
			++counter;

			dataArray[PAYLOAD_BEGIN_INDEX] = msgType;
			memcpy(&dataArray[PAYLOAD_BEGIN_INDEX + 1], &counter,sizeof(counter));
			dataArray[MSG_DATA_BEGIN_INDEX] = owbData[0];
			dataArray[MSG_DATA_BEGIN_INDEX+1] = owbData[1];
			uint8_t crc = getCRC(dataArray);
			dataArray[MAX -1] = crc;					

			Serial.write(dataArray,sizeof(dataArray));		
			Serial.flush();

//			dbgSerial.print("pkt no: "); dbgSerial.print(counter);
//			dbgSerial.print(" CRC: ");dbgSerial.print(crc);
//			dbgSerial.print(" hi : ");dbgSerial.print(owbData[1]);dbgSerial.print("lo : ");dbgSerial.print(owbData[0]);
//			dbgSerial.print(" raw: "); dbgSerial.print(raw,DEC);
//			dbgSerial.print(" decoded: ");dbgSerial.print(celsius);dbgSerial.print(" C ");
//			dbgSerial.println();
		}

		delay(SAMPLING_INTERVAL - sensorReadTime);
	}

	else {
		if(!present) 
			findSensorAndTurnBTOn();

		delay(SAMPLING_INTERVAL);   
	}
}

void findSensorAndTurnBTOn(void) {
	//find the temp sensor
	present = 0;
	present = owTempSensor.search(addr);

	if (OneWire::crc8(addr, 7) != addr[7]) {
//		dbgSerial.println("owTempSensor->CRC not valid ");
		present = 0;
	}

	if (addr[0] != 0x28) {
//		dbgSerial.println("owTempSensor-> not a DS18b20 device ");
		present = 0;
	}

	if(!present) {
//		dbgSerial.println("ERROR! temp sensor not found ");    
		digitalWrite(RED_LED_PIN, HIGH);
		digitalWrite(BT_PWR_PIN, LOW);
	}
	else {
//		dbgSerial.println("found temp sensor ");    
		digitalWrite(RED_LED_PIN, LOW);
		digitalWrite(BT_PWR_PIN, HIGH);
	}        
}

uint8_t getCRC(uint8_t *buff) {
	uint8_t crc = 0;

	for(int i = PAYLOAD_BEGIN_INDEX; i< MAX-1; i++) {
		crc ^= buff[i]; 
	}
	return crc;
}
