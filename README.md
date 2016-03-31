# android_ble

##  android ble 低耗蓝牙
 
采用Android bluetootheBle api  需要安卓系统4.3以上，并且需要硬件支持。随着物联网的发展，可穿戴设备、遥控设备是一个重要组成部分
，蓝牙部分必不可少。 安卓在4.3 以后，才用了低耗蓝牙协议，有点就是耗能少，快速、缺点就是传输速度没有普通蓝牙快。
 
我来简单介绍下低耗蓝牙的使用

###检查设备是否支持蓝牙

// 检查当前手机是否支持ble 蓝牙,如果不支持退出程序
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
			finish();
		}

		// 初始化 Bluetooth adapter, 通过蓝牙管理器得到一个参考蓝牙适配器(API必须在以上android4.3或以上和版本)
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// 检查设备上是否支持蓝牙
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
			finish();
			return;
			
			
			###打开蓝牙
			
			// 为了确保设备上蓝牙能使用, 如果当前蓝牙设备没启用,弹出对话框向用户要求授予权限来启用
		if (!mBluetoothAdapter.isEnabled()) {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}
		
		###扫描蓝牙
		
		private void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					mScanning = false;
					mBluetoothAdapter.stopLeScan(mLeScanCallback);
					invalidateOptionsMenu();
				}
			}, SCAN_PERIOD);

			mScanning = true;
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		} else {
			mScanning = false;
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
		}
		invalidateOptionsMenu();
	}
		
		
		
		扫描后的回调
		private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mLeDeviceListAdapter.addDevice(device);
					mLeDeviceListAdapter.notifyDataSetChanged();
				}
			});
		}
	};
	
	
	###连接蓝牙
BLE分为三部分Service、Characteristic、Descriptor，这三部分都由UUID作为唯一标示符。一个蓝牙4.0的终端可以包含多个Service，一个Service可以包含多个Characteristic，一个Characteristic包含一个Value和多个Descriptor，一个Descriptor包含一个Value。一般来说，Characteristic是手机与BLE终端交换数据的关键，Characteristic有较多的跟权限相关的字段，例如PERMISSION和PROPERTY，而其中最常用的是PROPERTY，本文所用的BLE蓝牙模块竟然没有标准的Characteristic的PERMISSION。Characteristic的PROPERTY可以通过位运算符组合来设置读写属性，例如READ|WRITE、READ|WRITE_NO_RESPONSE|NOTIFY，因此读取PROPERTY后要分解成所用的组合.

#####连接蓝牙
	public boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
			return false;
		}
		// Previously connected device.  Try to reconnect.
		if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
				&& mBluetoothGatt != null) {
			Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
			if (mBluetoothGatt.connect()) {
				mConnectionState = STATE_CONNECTING;
				return true;
			} else {
				return false;
			}
		}
		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.  Unable to connect.");
			return false;
		}
		// We want to directly connect to the device, so we are setting the autoConnect
		// parameter to false.
		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;
		mConnectionState = STATE_CONNECTING;
		return true;
	}

#####获取权限部分 ---- 特性
 当蓝牙连接上后，需要发送条广播
 if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				// Show all the supported services and characteristics on the user interface.
				displayGattServices(mBluetoothLeService.getSupportedGattServices());
}

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
		
	
	###写数据 读数据
	连接上后，要根据特定的特性，去读写，因为有的特性没有读写权限，这时需要知道蓝牙的uuid 去匹配
	
	byte[] bytes=new byte[]{(byte) 0xDD};
					//	byte[] bytes=new byte[]{(byte) 0xCC};
					mNotifyCharacteristic.setValue(bytes);
					//mNotifyCharacteristic.setValue(sendmsg);
					BluetoothLeService.mBluetoothGatt
					.writeCharacteristic(mNotifyCharacteristic);
	
	}
	
	###  国产安卓蓝牙机器，千变万化，开发起来虽然不难，但兼容性，却是个大问题，利用安卓APi开发。
	
	
			
