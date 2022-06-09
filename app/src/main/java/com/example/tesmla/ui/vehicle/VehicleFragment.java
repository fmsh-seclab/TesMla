package com.example.tesmla.ui.vehicle;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;


import com.example.tesmla.R;
import com.example.tesmla.databinding.FragmentVehicleBinding;
import com.example.tesmla.utils.ToastUtils;
import com.example.tesmla.utils.Conversion;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.UUID;


/**
 * This Class show as left fragment
 * Function: 1. Send broadcast package
 *           2. Work as a BLE Server offering the same services as Tesla vehicle
 *           3. Communicate with device which connect to this server
 */
public class VehicleFragment extends Fragment {
    private EditText edit_data;
    private EditText tx_to_app;
    private TextView data_type;
    private TextView log_data;
    private EditText dev_name;
    private boolean connect_state = false;
    private boolean indicate_state = false;
    private boolean specific_data = false;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothGattServer mBluetoothGattServer;
    BluetoothDevice mBluetoothDevice;

    //Operate UI in bluetooth thread.
    private final MyHandler mhandler = new MyHandler(Looper.myLooper());

    //Declaration of the content of GATT server.
    /* Service UUID */
    public static UUID Service_car = UUID.fromString("00000211-B2D1-43F0-9B88-960CEBF8B91E");

    /* Information Characteristic */
    /* Information Characteristic */
    public static UUID Charac1 = UUID.fromString("00000212-B2D1-43F0-9B88-960CEBF8B91E");
    public static UUID Charac2 = UUID.fromString("00000213-B2D1-43F0-9B88-960CEBF8B91E");
    public static UUID Charac3 = UUID.fromString("00000214-B2D1-43F0-9B88-960CEBF8B91E");

    /* Characteristic Descriptor*/
    public static UUID USERR_DESCRP = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb");
    public static UUID CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    BluetoothGattCharacteristic To_Vehicle;
    BluetoothGattCharacteristic From_Vehicle;
    BluetoothGattCharacteristic Vehicle_Info;
    BluetoothGattDescriptor configDescriptor2;
    BluetoothGattDescriptor UserDescriptor1;
    BluetoothGattDescriptor UserDescriptor2;
    BluetoothGattDescriptor UserDescriptor3;

    private FragmentVehicleBinding binding;
    private Thread thread;

    private String pk;
    private String token;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        if(Looper.myLooper() == null){
            Looper.prepare();
        }


        binding = FragmentVehicleBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        edit_data = binding.editData;
        tx_to_app   = binding.txToPhone;
        data_type = binding.rxDataType;
        log_data   = binding.dataLog;
        dev_name  = binding.editName;

        log_data.setMovementMethod(ScrollingMovementMethod.getInstance());
        log_data.setGravity(Gravity.BOTTOM);

        Button start_Adv = binding.startAdv;
        Button stop_adv = binding.stopAdv;
        Button send = binding.sendData;

        start_Adv.setOnClickListener((View v) -> {
            try {
                startAction();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        stop_adv.setOnClickListener((View v) -> stopAction());
        send.setOnClickListener((View v) -> {
            try {
                send_resp(1,"");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        FragmentActivity act = getActivity();
        if(act != null) {
            mBluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();
            if(!mBluetoothAdapter.isEnabled()){
                mBluetoothAdapter.enable();
                ToastUtils.showToast(getActivity(), "打开蓝牙", 2000);
            }

        }

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if(mBluetoothGattServer!=null) {
        stopAction();
        }
    }

    public void  startAction() throws IOException {
        openAndInitBt();
        createGattServer();
        startAdvertising();
    }

    public void stopAction() {
        if(mBluetoothGattServer!=null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            mBluetoothGattServer.close();
            ToastUtils.showToast(getActivity(), "已停止广播", 2000);
        }
    }

    /**
     * Send the response data to connected device by GATT Indicate and record data in /data/data/com.example.tesmla/files/log
     * @param type int: 0: Authentication-level requirement  1: other response data
     * @param data String: The data need to be send
     * @throws IOException record file does not exist or permission denied.
     */
    public void send_resp(int type, String data) throws IOException {
        if(connect_state) {
            Message msg = new Message();
            msg.what = 5;
            if(type != 0) {//Determine whether User has input the specific data
                String temp = tx_to_app.getText().toString();
                msg.obj = "";
                if (temp.length() > 0) {//If User has input
                    data = temp;
                }
            }
            if(!data.equals("")) {
                //Send indication to connected device
                From_Vehicle.setValue(Conversion.hexStringToBytes(data));
                mBluetoothGattServer.notifyCharacteristicChanged(mBluetoothDevice, From_Vehicle, true);
                //Show the data on the screen
                msg.obj = "<<" + data + "\n";
                if (type == 1) {
                    //record data in files
                    writeLog("<<" + data + "\n");
                    Log.d("[send]", data + "\n");
                    indicate_state = false;
                }
            }
            mhandler.sendMessage(msg);
        }
    }


    /**
     * Log the communicated data in /data/data/com.example.tesmla/files/log
     * @param data String: The data to be logged
     * @throws IOException : file does not exist or permission denied.
     */
    private void writeLog(String data) throws IOException {
        BufferedWriter writer;
        FragmentActivity act = getActivity();
        if(act != null) {
            FileOutputStream out = act.openFileOutput("log", Context.MODE_APPEND);
            writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(data);
            writer.flush();
        }
    }

    /**
     * Record signed msgs in fake_app /data/data/com.example.tesmla/files/file
     * @param data String: The data to be record
     * @throws IOException : file does not exist or permission denied.
     */
    private void record(String data) throws IOException {
        BufferedWriter writer;
        FragmentActivity act = getActivity();
        if(act != null) {
            FileOutputStream out = act.openFileOutput("fake_app", Context.MODE_APPEND);
            writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(data);
            writer.flush();
        }
    }

    /**
     * 1.Start and initial bluetooth service
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void openAndInitBt(){
        if (mBluetoothAdapter==null){
            ToastUtils.showToast(getActivity(), "不支持蓝牙", 2000);
            return;}

        FragmentActivity act = getActivity();
        if(act != null) {
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                ToastUtils.showToast(getActivity(), "不支持ble蓝牙", 2000);
            }
        }
    }


    /**
     *  2.Create GATT server working as Tesla vehicle
     */
    private void createGattServer() {
        //2.1.Create new service
        BluetoothGattService service_vehicle = new BluetoothGattService(Service_car,BluetoothGattService.SERVICE_TYPE_PRIMARY);

        //2.2 Create new Characteristic
        To_Vehicle = new BluetoothGattCharacteristic(Charac1,
                //Write-only characteristic, not supports notifications
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        From_Vehicle = new BluetoothGattCharacteristic(Charac2,
                //only supports indications
                BluetoothGattCharacteristic.PROPERTY_INDICATE,
                BluetoothGattCharacteristic.PERMISSION_READ);
        Vehicle_Info = new BluetoothGattCharacteristic(Charac3,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        Vehicle_Info.setValue("version for test 1.0");

        //2.3 Create Descriptor for characteristic
        configDescriptor2 = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
        UserDescriptor1 = new BluetoothGattDescriptor(USERR_DESCRP,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_WRITE );
        UserDescriptor2 = new BluetoothGattDescriptor(USERR_DESCRP,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_WRITE);
        //configDescriptor2.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        UserDescriptor3 = new BluetoothGattDescriptor(USERR_DESCRP,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_WRITE);

        To_Vehicle.setValue("To Vehicle");
        To_Vehicle.addDescriptor(UserDescriptor1);
        From_Vehicle.addDescriptor(UserDescriptor2);
        From_Vehicle.addDescriptor(configDescriptor2);
        Vehicle_Info.addDescriptor(UserDescriptor3);

        //2.4 Configure the characteristics to service
        service_vehicle.addCharacteristic(To_Vehicle);
        service_vehicle.addCharacteristic(From_Vehicle);
        service_vehicle.addCharacteristic(Vehicle_Info);


        //2.5 Enable Gatt server and add callback function
        mBluetoothGattServer = mBluetoothManager.openGattServer(getActivity(), mGattServerCallback);
        if (mBluetoothGattServer == null) {
            return;
        }
        mBluetoothGattServer.addService(service_vehicle);
    }


    /**
     * 3. Start Advertisement
     * @throws IOException file not found or permission denied
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startAdvertising() throws IOException {
        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();

        Message msg1 = new Message();
        msg1.what = 3;
        if (mBluetoothLeAdvertiser == null) {
            ToastUtils.showToast(getActivity(), "不支持LE广播", 2000);
            return;
        }

        //Set advertisement params: low latency, high power, timeout, etc.
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)//must set true
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        String name = dev_name.getText().toString();
        if (!name.equals("NULL") && name.length() > 1) {//If user has set own name. use the input name
            mBluetoothAdapter.setName(name);
            msg1.obj = String.format(getResources().getString(R.string.edit_Data), name);
            Log.w("[dev name]", "manual set: " + name);
        }else {
            try {//If not set, read the record file in scanning process
                File file = new File(String.format(getResources().getString(R.string.dir), "fake_dev"));
                if (file.exists()) {
                    FileInputStream fin = new FileInputStream(file);
                    byte[] buffer = new byte[40];
                    if (fin.read(buffer) > 0) {
                        String s = new String(buffer);
                        name = s.substring(0, s.indexOf("\n"));
                        Log.w("[read]", name);
                        if (!name.equals("NULL")) {
                            mBluetoothAdapter.setName(name);//Set bluetooth adapter name
                            msg1.obj = name;
                            Log.w("[dev name]", "file read: " + name);
                        } else {//If record file does not record the name, use default name.
                            mBluetoothAdapter.setName("S000001234512345bC");
                            msg1.obj = String.format(getResources().getString(R.string.default_data), mBluetoothAdapter.getName());
                            Log.w("[dev name]", "default" + mBluetoothAdapter.getName());
                        }
                    }
                }else {//If record file does not exist, use default data and name.
                    mBluetoothAdapter.setName("S000001234512345bC");
                    msg1.obj = String.format(getResources().getString(R.string.default_data), mBluetoothAdapter.getName());
                    Log.w("[dev name]", "default " + mBluetoothAdapter.getName());
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        mhandler.sendMessage(msg1);

        Message msg2 = new Message();
        msg2.what = 4;
        String str = edit_data.getText().toString();
        if (str.length() < 1) {//If user has not input manufacture specific data, use the record data in scanning process.
            File file = new File(String.format(getResources().getString(R.string.dir), "fake_dev"));
            if (file.exists()) {
                FileInputStream fin = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                if (fin.read(buffer) > 0) {
                    String s = new String(buffer);
                    if(s.indexOf("specific_data:")>10){
                        str = s.substring(s.indexOf("specific_data:")+14, s.lastIndexOf("\n"));
                        if (!str.equals("NULL")) {
                            msg2.obj = str;
                            specific_data = true;
                        }
                    }
                }
            }
            if(!specific_data) {
                msg2.obj = String.format(getResources().getString(R.string.default_data), "5d8f");
                str = "021574278BDAB64445208F0C720EAF05993500005D1234";
            }
            byte []broadcast = Conversion.hexStringToBytes(str);


            //Add broadcast data
            AdvertiseData adv_data = new AdvertiseData.Builder()
                    .addManufacturerData(0x004C,broadcast)
                    .build();

            AdvertiseData adv_resp_data = new AdvertiseData.Builder()
                    .addServiceUuid( ParcelUuid.fromString("00001122-0000-1000-8000-00805f9b34fb"))
                    .setIncludeDeviceName(true)
                    .build();

            mBluetoothLeAdvertiser.startAdvertising(settings, adv_data, adv_resp_data, mAdvertiseCallback);
        }
        else{//Use user input manufacture data
            final byte[] broadcastData = Conversion.hexStringToBytes(str);
            msg2.obj = str;
            AdvertiseData define_data = new AdvertiseData.Builder()
                    .addManufacturerData(0x004C,broadcastData)
                    .build();
            AdvertiseData adv_resp_data = new AdvertiseData.Builder()
                    .addServiceUuid( ParcelUuid.fromString("00001122-0000-1000-8000-00805f9b34fb"))
                    .setIncludeDeviceName(true)
                    .build();

            mBluetoothLeAdvertiser.startAdvertising(settings, define_data, adv_resp_data, mAdvertiseCallback);
        }
        mhandler.sendMessage(msg2);
    }

    /**
     * advertisement callback function. Show toast of advertisement state
     */
    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i("", "LE Advertise Started.");
            ToastUtils.showToast(getActivity(), "开启广播成功", 2000);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w("", "LE Advertise Failed: "+errorCode);
            ToastUtils.showToast(getActivity(), "开启广播失败 errorCode：" + errorCode, 2000);
        }
    };

    /**
     * Callback to handle incoming requests to the GATT server.
     * All write and read of characteristics or descriptors will be operated here.
     */
    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        /**
         * Callback indicating when a remote device has been connected or disconnected..
         * @param device BluetoothDevice: Remote device that has been connected or disconnected.
         * @param status int: Status of the connect or disconnect operation.
         * @param newState int: Returns the new connection state. Can be one of BluetoothProfile.STATE_DISCONNECTED or BluetoothProfile#STATE_CONNECTED
         */
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("[connection state changed]", "BluetoothDevice CONNECTED: " + device);
                mBluetoothDevice = device;
                connect_state = true;

                //If new connection created, delete the former record file.
                File file = new File(String.format(getResources().getString(R.string.dir),"fake_app"));
                if (file.exists()) {
                    if(!file.delete()){
                        Log.e("[err]","fake_app file not exist.");
                    }
                }

                try {
                    writeLog("############# fake as tesla ##############\n");
                    //read the public key and token from the file which be recorded during the process of fake as app
                    file = new File(String.format(getResources().getString(R.string.dir),"fake_tesla"));
                    if (file.exists()) {
                        FileInputStream fin = new FileInputStream(file);
                        byte[] buffer = new byte[4096];
                        if (fin.read(buffer) > 50) {
                            String s = new String(buffer);
                            if(s.lastIndexOf("pk:") >= 0) {
                                //Log.e("[!!!!!!!!!]",""+s.lastIndexOf("pk:"));
                                pk = s.substring(s.lastIndexOf("pk:") + 3, s.lastIndexOf("pk:") + 145);
                            }
                            /*else{//if public key has not found in file, use default public key
                                pk = "004512431a4104a64f09b8a4041e98ad7dca0c198f5def6e4753b74d74195d4aaaf743c58560b0aeeabcb643249617567a6c76ceba1067ebe57df9e6123b4af769382651ee3a5a";
                                pk = pk.toUpperCase();
                            }*/

                            if(s.indexOf("token:")>=0){
                                token = s.substring(s.indexOf("token:") + 6, s.indexOf("token:") + 66);
                            }
                            /*else{
                                token = "001C1A1A12160A148A39B3ADCCED4DFA817B91EDEA8DD681043312341802";
                                token = token.toUpperCase();
                            }*/
                        }
                    }
                   /* else{
                        pk = "004512431a4104a64f09b8a4041e98ad7dca0c198f5def6e4753b74d74195d4aaaf743c58560b0aeeabcb643249617567a6c76ceba1067ebe57df9e6123b4af769382651ee3a5a";
                        pk = pk.toUpperCase();
                        token = "001C1A1A12160A148A39B3ADCCED4DFA817B91EDEA8DD681043312341802";
                        token = token.toUpperCase();
                    }*/
                }catch (IOException e) {
                    e.printStackTrace();
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("", "BluetoothDevice DISCONNECTED: " + device);
                connect_state = false;
                indicate_state = false;
                thread = null;
            }

            //Define the new thread to send authentication-level data every second
            thread = new Thread(()->{
                while (connect_state) {
                    try {
                        Thread.sleep(1000);
                        if(!indicate_state) send_resp(0, "00041a021802");
                    } catch (InterruptedException |IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        /**
         * Callback indicating the MTU for a given device connection has changed.
         * @param device BluetoothDevice: The remote device that requested the MTU change
         * @param mtu int: The new MTU size
         */
        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.w("[MTU]", "change MTU to "+mtu);
            Message msg = new Message();
            msg.what = 5;
            msg.obj = "mtu size changed\n";
            mhandler.sendMessage(msg);
        }

        /**
         * Connected device read server's characteristic
         * @param device BluetoothDevice: The remote device that has requested the read operation
         * @param requestId int: The Id of the request
         * @param offset int: Offset into the value of the characteristic
         * @param characteristic BluetoothGattCharacteristic: Characteristic to be read
         */
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            //Only has one characteristic can be read
            if(Charac3.equals(characteristic.getUuid())){
                Log.w("[READ]","UUID:"+characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device,requestId, BluetoothGatt.GATT_SUCCESS,0, Vehicle_Info.getValue());
            }

        }

        /**
         * A remote client has requested to read a local descriptor.
         * @param device  BluetoothDevice: The remote device that has requested the read operation
         * @param requestId int: The Id of the request
         * @param offset int: Offset into the value of the characteristic
         * @param descriptor BluetoothGattDescriptor: Descriptor to be read
         */
        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.w("[Read]","descriptor"+descriptor.getUuid()+descriptor.getPermissions());

            byte[] a = {0x12,0x34};
            mBluetoothGattServer.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0,a//Conversion.intToByteArray(descriptor.getPermissions())
            );
        }

        /**
         * A remote client has requested to write to a local descriptor.
         * @param device BluetoothDevice: The remote device that has requested the write operation
         * @param requestId int: The Id of the request
         * @param descriptor BluetoothGattDescriptor: Descriptor to be written to.
         * @param preparedWrite boolean: true, if this write operation should be queued for later execution.
         * @param responseNeeded boolean: true, if the remote device requires a response
         * @param offset int: The offset given for the value
         * @param value byte: The value the client wants to assign to the descriptor
         */
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,BluetoothGattDescriptor descriptor,boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {

            super.onDescriptorWriteRequest(device, requestId, descriptor,preparedWrite,responseNeeded,offset,value);
            Message msg = new Message();
            msg.what = 0;
            msg.obj = descriptor.getUuid() + ":\n";
            if(!thread.isAlive()){
                thread.start();
            }
            if (CLIENT_CONFIG.equals(descriptor.getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    msg.obj += "ENABLE_NOTIFICATION_VALUE";
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    msg.obj += "DISABLE_NOTIFICATION_VALUE";
                }else if(Arrays.equals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE, value)) {
                    msg.obj += "ENABLE_INDICATION_VALUE";
                }else if(Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    msg.obj += "DISABLE_NOTIFICATION_VALUE";
                }

                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null);
                }
            } else {
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null);
                }
            }
            if(Looper.myLooper() == null){
                Looper.prepare();
            }

            mhandler.sendMessage(msg);
            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        /**
         * A remote client has requested to write to a local characteristic.
         * @param device BluetoothDevice: The remote device that has requested the write operation
         * @param requestId int: The Id of the request
         * @param characteristic BluetoothGattCharacteristic: Characteristic to be written to.
         * @param preparedWrite boolean: true, if this write operation should be queued for later execution.
         * @param responseNeeded boolean: true, if the remote device requires a response
         * @param offset int: The offset given for the value
         * @param value byte: The value the client wants to assign to the characteristic
         */
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            indicate_state = true;

            //Send Data to UI Interface
            String recv = Conversion.Bytes2HexString(value);
            Message msg = new Message();
            msg.what = 1;
            msg.obj = recv;
            Log.d("[recv]", recv);

            //if authentication process end, stop the authentication-level requirement thread
            //if(recv.equals("00051203C00101")){thread = null;}

            //record data in log files
            try {
                writeLog(">>" + recv + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(Looper.myLooper() == null){ Looper.prepare(); }
            mhandler.sendMessage(msg);
            mBluetoothDevice = device;

            if (Charac1.equals(characteristic.getUuid())) {
                byte[] info_req_get_pk = {0x0a, 0x0a, 0x08, 0x03};//Command: send public key of vehicle
                byte[] info_req_get_capabilities = {0x0a, 0x0a, 0x08, 0x10};//Command: require get vehicle capabilities
                String tx_data;

                if (value.length> 10 && value[2] == 0x12) {//Normal Command
                    byte[] pattern = new byte[4];
                    System.arraycopy(value, 4, pattern, 0, 4);
                    if (Arrays.equals(pattern, info_req_get_pk)) { //Command: send public key of vehicle
                        tx_data = pk;
                        Log.w("[send pk]", tx_data);
                    } else if (Arrays.equals(pattern, info_req_get_capabilities)) {//Command: get vehicle capabilities
                        byte[] IND_pattern2 = {0x00, 0x07, -102, 0x01, 0x04, 0x08, 0x01, 0x10, 0x01};
                        tx_data = Conversion.Bytes2HexString(IND_pattern2);
                        Log.w("[send]", tx_data);
                    } else {//Other command
                        byte[] IND_pattern3 = {0x00, 0x02, 0x0a, 0x00}; //vehicle state = 0
                        tx_data = Conversion.Bytes2HexString(IND_pattern3);
                        Log.w("[send]", tx_data);
                    }
                    try {
                        send_resp(1, tx_data);
                        if(value[1] == 0x0C){//Command: get vehicle lock state
                            record("cmd:" + recv +"\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (value[2] == 0x0a) {//Signed Message
                    //Get Counter Byte Length
                    int len = recv.indexOf("2A04", recv.indexOf("30"))/2 - 5;

                    //Set Indication data, response the counter
                    byte[] cnt = new byte[len];
                    System.arraycopy(value, 5, cnt, 0, len);
                    byte[] IND_pattern = {0x00, 0x06, 0x22, 0x04, 0x12, 0x02, 0x08};
                    if (len == 2) {
                        IND_pattern[1] += 1;
                        IND_pattern[3] += 1;
                        IND_pattern[5] += 1;
                    }
                    byte[] cnt_resp = new byte[len + IND_pattern.length];
                    System.arraycopy(IND_pattern, 0, cnt_resp, 0, IND_pattern.length);
                    System.arraycopy(cnt, 0, cnt_resp, IND_pattern.length, len);
                    tx_data = Conversion.Bytes2HexString(cnt_resp);

                    try {
                        send_resp(1, tx_data);
                        if(value[1] < 0x24) { //if receive msg len less than 0x24, it will be the first signed msg
                            record("sign1:" + recv +"\n");
                            if(token != null) send_resp(1, token);
                        }else{
                            record("sign2:" + recv + "\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                Log.w("", "无效写特性操作");
            }


            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null);
            }
        }

        /**
         * Callback invoked when a notification or indication has been sent to a remote device.
         * When multiple notifications are to be sent, an application must wait for this callback to be received before sending additional notifications.
         * @param device BluetoothDevice: The remote device the notification has been sent to
         * @param status int: BluetoothGatt#GATT_SUCCESS if the operation was successful
         */
        @Override
        public void onNotificationSent(BluetoothDevice device, int status){
            super.onNotificationSent(device, status);
        }


    };//Bluetooth GATT callback function end


    /**
     *   Bluetooth operation often work as asynchronous thread. This handler operate the UI in non-main thread
     */
    class MyHandler extends Handler {
       public MyHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    data_type.setText(getResources().getString(R.string.write_dsp));
                    break;
                case 1:
                    data_type.setText(getResources().getString(R.string.write_chara));
                    log_data.append(">>" + msg.obj +"\n");
                    break;
                case 3:
                    dev_name.setText((String)msg.obj);
                    break;
                case 4:
                    edit_data.setText((String)msg.obj);
                    break;
                case 5:
                    log_data.append(msg.obj + "");
                    break;
                default:
                    data_type.setText(getResources().getString(R.string.unknown_op));
            }
        }
    }
}