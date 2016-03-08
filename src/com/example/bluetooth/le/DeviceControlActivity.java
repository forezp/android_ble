/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.bluetooth.le;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
	private final static String TAG = DeviceControlActivity.class.getSimpleName();

	public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

	private TextView mConnectionState;
	private TextView mDataField;
	private String mDeviceName;
	private String mDeviceAddress;
	private ExpandableListView mGattServicesList;
	private BluetoothLeService mBluetoothLeService;
	private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
			new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
	private boolean mConnected = false;
	private BluetoothGattCharacteristic mNotifyCharacteristic;

	private final String LIST_NAME = "NAME";
	private final String LIST_UUID = "UUID";

	/**
	 * 发送指令
	 */
	private LinearLayout llControl;
	private Button btnOpen1;
	private Button btnOpen2;
	private Button btnClose;

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			// Automatically connects to the device upon successful start-up initialization.
			mBluetoothLeService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
	//                        or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mConnected = true;
				llControl.setVisibility(View.VISIBLE);
				updateConnectionState(R.string.connected);
				invalidateOptionsMenu();
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				mConnected = false;
				llControl.setVisibility(View.INVISIBLE);
				updateConnectionState(R.string.disconnected);
				invalidateOptionsMenu();
				clearUI();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				// Show all the supported services and characteristics on the user interface.
				displayGattServices(mBluetoothLeService.getSupportedGattServices());
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
			}else if (BluetoothLeService.ACTION_READ_OVER.equals(action)) {
				dis_recive_msg(intent.getByteArrayExtra("value"));
				return;
			}

		}
	};

	private void dis_recive_msg(byte[] tmp_byte) {

		String tmp = "";



		for (int i = 0; i < tmp_byte.length; i++) {
			String hex = Integer.toHexString(tmp_byte[i] & 0xFF);
			if (hex.length() == 1) {
				hex = '0' + hex;
			}
			tmp += ' ';
			tmp = tmp + hex;
		}

		mDataField.setText(tmp);



		//		ChatMsgFmt entity2 = new ChatMsgFmt("Device", tmp, MESSAGE_FROM.OTHERS);
		//		chat_list.add(entity2);
		//		chat_list_adapter.notifyDataSetChanged();
	}

	// If a given GATT characteristic is selected, check for supported features.  This sample
	// demonstrates 'Read' and 'Notify' features.  See
	// http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
	// list of supported characteristic features.
	private final ExpandableListView.OnChildClickListener servicesListClickListner =
			new ExpandableListView.OnChildClickListener() {
		@SuppressLint("NewApi")
		@Override
		public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
				int childPosition, long id) {
			if (mGattCharacteristics != null) {
				final BluetoothGattCharacteristic characteristic =
						mGattCharacteristics.get(groupPosition).get(childPosition);
				final int charaProp = characteristic.getProperties();
				if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
					// If there is an active notification on a characteristic, clear
					// it first so it doesn't update the data field on the user interface.
					if (mNotifyCharacteristic != null) {
						mBluetoothLeService.setCharacteristicNotification(
								mNotifyCharacteristic, false);
						mNotifyCharacteristic = null;
					}
					mBluetoothLeService.readCharacteristic(characteristic);
				}
				if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
					mNotifyCharacteristic = characteristic;
					mBluetoothLeService.setCharacteristicNotification(
							characteristic, true);
				}
				return true;
			}
			return false;
		}
	};

	private void clearUI() {
		mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
		mDataField.setText(R.string.no_data);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gatt_services_characteristics);

		final Intent intent = getIntent();
		mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
		mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

		// Sets up UI references.
		((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
		mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
		mGattServicesList.setOnChildClickListener(servicesListClickListner);
		mConnectionState = (TextView) findViewById(R.id.connection_state);
		mDataField = (TextView) findViewById(R.id.data_value);

		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		llControl=(LinearLayout)findViewById(R.id.ll_control);
		btnOpen1=(Button) findViewById(R.id.btn_open1);		
		btnOpen1.setOnClickListener(new OnClickListener() {

			@SuppressLint("NewApi")
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				//				byte[] sendmsg = getMsgEdit();
				//				if (sendmsg == null) {
				//					return;
				//				}
				if(mGattCharacteristics.size()==0){
					return;
				}else{
					for(int i=0;i<mGattCharacteristics.size();i++){
						List<BluetoothGattCharacteristic> list=mGattCharacteristics.get(i);
						for(int j=0;j<list.size();j++){
							BluetoothGattCharacteristic gattCharacteristic=list.get(j);
							String uuid = gattCharacteristic.getUuid().toString();
							String canReadAndWriteUuid="0000fff1-0000-1000-8000-00805f9b34fb";
							if(uuid.endsWith(canReadAndWriteUuid)){
								mNotifyCharacteristic=list.get(j);
								break;
							}
						}
					}
					if(mNotifyCharacteristic==null){
						if(mGattCharacteristics.size()==3){
							mNotifyCharacteristic=mGattCharacteristics.get(3).get(0);
						}else{
							return;
						}
					}
					//byte v1 = (byte) Integer.parseInt("0xDD", 16);
					//byte v2=(byte)Integer.parseInt("0xEE", 16);
					//byte[] sendmsg=new  byte[]{v1, v2};
					//Integer.decode("0xDD");
					byte[] bytes=new byte[]{(byte) 0xDD};
					//	byte[] bytes=new byte[]{(byte) 0xCC};
					mNotifyCharacteristic.setValue(bytes);
					//mNotifyCharacteristic.setValue(sendmsg);
					BluetoothLeService.mBluetoothGatt
					.writeCharacteristic(mNotifyCharacteristic);
				}
			}
		});

		btnOpen2=(Button) findViewById(R.id.btn_open2);		
		btnOpen2.setOnClickListener(new OnClickListener() {

			@SuppressLint("NewApi")
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				//				byte[] sendmsg = getMsgEdit();
				//				if (sendmsg == null) {
				//					return;
				//				}
				if(mGattCharacteristics.size()==0){
					return;
				}else{
					for(int i=0;i<mGattCharacteristics.size();i++){
						List<BluetoothGattCharacteristic> list=mGattCharacteristics.get(i);
						for(int j=0;j<list.size();j++){
							BluetoothGattCharacteristic gattCharacteristic=list.get(j);
							String uuid = gattCharacteristic.getUuid().toString();
							String canReadAndWriteUuid="0000fff1-0000-1000-8000-00805f9b34fb";
							if(uuid.endsWith(canReadAndWriteUuid)){
								mNotifyCharacteristic=list.get(j);
								break;
							}
						}
					}
					if(mNotifyCharacteristic==null){
						if(mGattCharacteristics.size()==3){
							mNotifyCharacteristic=mGattCharacteristics.get(3).get(0);
						}else{
							Toast.makeText(DeviceControlActivity.this, "连接设备没有读写权限", Toast.LENGTH_SHORT).show();
							return;
						}
					}
					//byte v1 = (byte) Integer.parseInt("0xDD", 16);
					//byte v2=(byte)Integer.parseInt("0xEE", 16);
					//byte[] sendmsg=new  byte[]{v1, v2};
					//Integer.decode("0xDD");
					byte[] bytes=new byte[]{(byte) 0xEE};
					//	byte[] bytes=new byte[]{(byte) 0xCC};
					mNotifyCharacteristic.setValue(bytes);
					//mNotifyCharacteristic.setValue(sendmsg);
					BluetoothLeService.mBluetoothGatt
					.writeCharacteristic(mNotifyCharacteristic);
				}
			}
		});

		btnClose=(Button) findViewById(R.id.btn_close);		
		btnClose.setOnClickListener(new OnClickListener() {

			@SuppressLint("NewApi")
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				//				byte[] sendmsg = getMsgEdit();
				//				if (sendmsg == null) {
				//					return;
				//				}
				if(mGattCharacteristics.size()==0){
					return;
				}else{
					for(int i=0;i<mGattCharacteristics.size();i++){
						List<BluetoothGattCharacteristic> list=mGattCharacteristics.get(i);
						for(int j=0;j<list.size();j++){
							BluetoothGattCharacteristic gattCharacteristic=list.get(j);
							String uuid = gattCharacteristic.getUuid().toString();
							String canReadAndWriteUuid="0000fff1-0000-1000-8000-00805f9b34fb";
							if(uuid.endsWith(canReadAndWriteUuid)){
								mNotifyCharacteristic=list.get(j);
								break;
							}
						}
					}
					if(mNotifyCharacteristic==null){
						if(mGattCharacteristics.size()==3){
							mNotifyCharacteristic=mGattCharacteristics.get(3).get(0);
						}else{
							return;
						}
					}
					//byte v1 = (byte) Integer.parseInt("0xDD", 16);
					//byte v2=(byte)Integer.parseInt("0xEE", 16);
					//byte[] sendmsg=new  byte[]{v1, v2};
					//Integer.decode("0xDD");
					byte[] bytes=new byte[]{(byte) 0xCC};
					//	byte[] bytes=new byte[]{(byte) 0xCC};
					mNotifyCharacteristic.setValue(bytes);
					//mNotifyCharacteristic.setValue(sendmsg);
					BluetoothLeService.mBluetoothGatt
					.writeCharacteristic(mNotifyCharacteristic);
				}
			}
		});


	}

	//	private byte[] getMsgEdit(){
	//		String tmp_str = "";
	//		byte[] tmp_byte = null;
	//		byte[] write_msg_byte = null;
	//		tmp_str = etSend.getText().toString();
	//		if (0 == tmp_str.length())
	//			return null;
	//
	//		tmp_byte = tmp_str.getBytes();
	//		write_msg_byte = new byte[tmp_byte.length / 2 + tmp_byte.length % 2];
	//		for (int i = 0; i < tmp_byte.length; i++) {
	//			if ((tmp_byte[i] <= '9') && (tmp_byte[i] >= '0')) {
	//				if (0 == i % 2)
	//					write_msg_byte[i / 2] = (byte) (((tmp_byte[i] - '0') * 16) & 0xFF);
	//				else
	//					write_msg_byte[i / 2] |= (byte) ((tmp_byte[i] - '0') & 0xFF);
	//			} else {
	//				if (0 == i % 2)
	//					write_msg_byte[i / 2] = (byte) (((tmp_byte[i] - 'a' + 10) * 16) & 0xFF);
	//				else
	//					write_msg_byte[i / 2] |= (byte) ((tmp_byte[i] - 'a' + 10) & 0xFF);
	//			}
	//		}
	//		if (0 == tmp_str.length())
	//			return null;
	//
	//		return write_msg_byte;
	//	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null) {
			final boolean result = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "Connect request result=" + result);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.gatt_services, menu);
		if (mConnected) {
			menu.findItem(R.id.menu_connect).setVisible(false);
			menu.findItem(R.id.menu_disconnect).setVisible(true);
		} else {
			menu.findItem(R.id.menu_connect).setVisible(true);
			menu.findItem(R.id.menu_disconnect).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_connect:
			mBluetoothLeService.connect(mDeviceAddress);
			return true;
		case R.id.menu_disconnect:
			mBluetoothLeService.disconnect();
			finish();
			return true;
		case android.R.id.home:
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void updateConnectionState(final int resourceId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mConnectionState.setText(resourceId);
			}
		});
	}

	private void displayData(String data) {
		if (data != null) {
			mDataField.setText(data);
		}
	}

	// Demonstrates how to iterate through the supported GATT Services/Characteristics.
	// In this sample, we populate the data structure that is bound to the ExpandableListView
	// on the UI.
	@SuppressLint("NewApi")
	private void displayGattServices(List<BluetoothGattService> gattServices) {
		if (gattServices == null) return;
		String uuid = null;
		String unknownServiceString = getResources().getString(R.string.unknown_service);
		String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
		ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
		ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
		= new ArrayList<ArrayList<HashMap<String, String>>>();
		mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

		// Loops through available GATT Services.
		for (BluetoothGattService gattService : gattServices) {
			HashMap<String, String> currentServiceData = new HashMap<String, String>();
			uuid = gattService.getUuid().toString();
			currentServiceData.put(
					LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
			currentServiceData.put(LIST_UUID, uuid);
			gattServiceData.add(currentServiceData);

			ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
					new ArrayList<HashMap<String, String>>();
			List<BluetoothGattCharacteristic> gattCharacteristics =
					gattService.getCharacteristics();
			ArrayList<BluetoothGattCharacteristic> charas =
					new ArrayList<BluetoothGattCharacteristic>();

			// Loops through available Characteristics.
			for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

				charas.add(gattCharacteristic);
				HashMap<String, String> currentCharaData = new HashMap<String, String>();
				uuid = gattCharacteristic.getUuid().toString();
				currentCharaData.put(
						LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
				currentCharaData.put(LIST_UUID, uuid);
				gattCharacteristicGroupData.add(currentCharaData);
			}
			mGattCharacteristics.add(charas);
			gattCharacteristicData.add(gattCharacteristicGroupData);
		}

		SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
				this,
				gattServiceData,
				android.R.layout.simple_expandable_list_item_2,
				new String[] {LIST_NAME, LIST_UUID},
				new int[] { android.R.id.text1, android.R.id.text2 },
				gattCharacteristicData,
				android.R.layout.simple_expandable_list_item_2,
				new String[] {LIST_NAME, LIST_UUID},
				new int[] { android.R.id.text1, android.R.id.text2 }
				);
		mGattServicesList.setAdapter(gattServiceAdapter);
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}
}
