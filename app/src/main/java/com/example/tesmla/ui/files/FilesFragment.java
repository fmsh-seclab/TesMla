package com.example.tesmla.ui.files;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.example.tesmla.R;
import com.example.tesmla.databinding.FragmentFilesBinding;
import com.example.tesmla.utils.Conversion;
import com.example.tesmla.utils.ToastUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.UUID;

/**
 * This Class show as right fragment
 * Function: 1. Connect the specified device
 *           2. Work as a BLE Client sending the similar command as Tesla App
 *           3. Communicate with connected device and show the data
 */

public class FilesFragment extends Fragment {

    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice;

    private String mac;
    private boolean isServiceConnected;

    private TextView log_data;
    private TextView addr;
    private EditText tx_to_car;
    private Button connect;
    private Button disconn;

    private String state_cmd;
    private String sign1;
    private String sign2;
    private String counter = "NULL";
    private boolean epoch;

    UUID SER_UUID    = UUID.fromString("00000211-B2D1-43F0-9B88-960CEBF8B91E");
    UUID TX_UUID     = UUID.fromString("00000212-B2D1-43F0-9B88-960CEBF8B91E");
    UUID RX_UUID     = UUID.fromString("00000213-B2D1-43F0-9B88-960CEBF8B91E");
    UUID DESPCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    BluetoothGattService car_service;
    BluetoothGattCharacteristic characteristic_TX;
    BluetoothGattCharacteristic characteristic_RX;

    private final MyHandler mhandler = new MyHandler(Looper.myLooper());

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        FragmentFilesBinding binding = FragmentFilesBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        addr = binding.addr;
        Message msg = new Message();
        msg.what = 4;
        mhandler.sendMessage(msg);

        isServiceConnected = false;

        log_data  = binding.log;
        connect   = binding.connect;
        disconn   = binding.disconnect;
        tx_to_car = binding.txToCar;

        Button send    = binding.send;
        Button getPK   = binding.getPk;
        Button change  = binding.changeMtu;

        log_data.setMovementMethod(ScrollingMovementMethod.getInstance());
        log_data.setGravity(Gravity.BOTTOM);

        if(!isServiceConnected){
            disconn.setEnabled(false);
        }

        FragmentActivity act = getActivity();
        if(act != null) {
            BluetoothManager mBluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();
            if(!mBluetoothAdapter.isEnabled()){
                mBluetoothAdapter.enable();
            }
        }

        try {
            mac = getAddress();
        } catch (IOException e) {
            e.printStackTrace();
        }
        addr.setText(mac);

        connect.setOnClickListener((View v) -> startConnect());
        disconn.setOnClickListener((View v) -> stopConnect());
        change.setOnClickListener((View v) -> changeMTU());

        getPK.setOnClickListener((View v) -> {
            try {
                getPublicKey();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        send.setOnClickListener((View v) -> {
            try {
                epoch = true;
                startSend(state_cmd);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return root;

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mBluetoothGatt != null) {
            stopConnect();
        }
    }

    /**
     * log data in data/data/com.example.tesmla/files/log
     * @param data String: data to be recorded
     * @throws IOException : file not found or permission denied
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
     * record pk and token info in data/data/com.example.tesmla/files/fake_vehicle
     * @param data String: pk and token to be recorded
     * @throws IOException : file not found or permission denied
     */
    private void record(String data) throws IOException {
        BufferedWriter writer;
        FragmentActivity act = getActivity();
        if(act != null) {
            FileOutputStream out = act.openFileOutput("fake_tesla", Context.MODE_APPEND);
            writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(data);
            writer.flush();
        }
    }

    private void getPublicKey() throws IOException {
        if(isServiceConnected){
            startSend("000E120C0A0A080312060A04AB5794C2");
        }
        else{
            ToastUtils.showToast(getActivity(), "未连接设备", 2000);
        }
    }

    /**
     * Determine whether the input string satisfies MAC address format requirement
     * @param val String :The string to be determined
     * @return boolean: true if satisfied
     */
    private boolean stringIsMac(String val) {
        String trueMacAddress = "([A-Fa-f0-9]{2}:){5}[A-Fa-f0-9]{2}";
        return val.matches(trueMacAddress);
    }


    /**
     * Start connect to the device by specific mac address and add callback
     */
    public void startConnect() {
        if(stringIsMac(mac)) {
            mDevice = mBluetoothAdapter.getRemoteDevice(mac);
            if (mDevice != null) {
                if (mBluetoothGatt != null) {
                    mBluetoothGatt.disconnect();
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                }
                mBluetoothGatt = mDevice.connectGatt(getActivity(), false, mGattCallback);
            }
        }
    }

    /**
     * Return the bluetooth mac address recorded in data/data/com.example.tesmla/files/fake_dev
     * @return String Bluetooth MAC address
     * @throws IOException file not found or permission denied
     */
    private  String getAddress() throws IOException {
        try {
                File file = new File(String.format(getResources().getString(R.string.dir),"fake_dev"));
                if (file.exists()) {
                    FileInputStream fin = new FileInputStream(file);
                    byte[] buffer = new byte[80];
                    if (fin.read(buffer) > 0) {
                        String s = new String(buffer);
                        return s.substring(s.indexOf("\n") + 1, s.indexOf("\n") + 18);
                    }
                }
            } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Send the data to GATT server
     * @param tx the command data need to be send
     * @throws IOException file not found or permission denied
     */
    public void startSend(String tx) throws IOException {
        if (mBluetoothGatt != null && isServiceConnected) {
            String tx_from_screen = tx_to_car.getText().toString();
            Message msg = new Message();
            msg.what = 0;
            msg.obj = "";
            if(tx_from_screen.length()>0){//If user input command on screen, send the input data.
                byte[] send_data = Conversion.hexStringToBytes(tx_from_screen);
                characteristic_TX.setValue(send_data);
                characteristic_TX.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                mBluetoothGatt.writeCharacteristic(characteristic_TX);
                msg.obj = "<<" + tx_from_screen + "\n";
                try {
                    writeLog("<<" + tx_from_screen + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else{//send the specific data
                byte[] send_data = Conversion.hexStringToBytes(tx);
                msg.obj = "<<" + tx + "\n";
                writeLog("<<" + tx + "\n");
                new Thread(){
                    @Override
                    public void run() {
                        super.run();

                        characteristic_TX.setValue(send_data);
                        characteristic_TX.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        boolean writeTrue = mBluetoothGatt.writeCharacteristic(characteristic_TX);
                        try {
                            sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }

            mhandler.sendMessage(msg);
        }
    }

    /**
     * Request an MTU size used for a given connection.
     */
    public void changeMTU(){
        if(mBluetoothGatt!= null  && isServiceConnected) {
            mBluetoothGatt.requestMtu(259);
            Message msg = new Message();
            msg.what = 2;
            msg.obj = "mtu size changed\n";
            mhandler.sendMessage(msg);
        }
    }


    public void stopConnect() {
        if (mBluetoothGatt != null) {
            isServiceConnected = false;
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            try {
                mac = getAddress();
                addr.setText(mac);
            } catch (IOException e) {
                e.printStackTrace();
            }
            connect.setEnabled(true);
        }
    }


    /**
     * Callback to handle incoming response from the GATT server.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /**
         * Callback indicating when GATT client has connected/disconnected to/from a remote GATT server.
         * @param gatt BluetoothGatt: GATT client
         * @param status int: Status of the connect or disconnect operation. BluetoothGatt.GATT_SUCCESS if the operation succeeds.
         * @param newState int: Returns the new connection state. Can be one of BluetoothProfile.STATE_DISCONNECTED or BluetoothProfile#STATE_CONNECTED
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            FragmentActivity act = getActivity();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                //discover the service of GATT server
                if (act != null) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        isServiceConnected = true;
                        gatt.discoverServices();
                        Message msg = new Message();
                        msg.what = 3;
                        mhandler.sendMessage(msg);
                        epoch = true;

                        try {
                            writeLog("############# fake as app ##############\n");
                            //read the public key and token from the file which be recorded during the process of fake as app
                            File file = new File(String.format(getResources().getString(R.string.dir),"fake_app"));
                            if (file.exists()) {
                                FileInputStream fin = new FileInputStream(file);
                                byte[] buffer = new byte[4096];
                                if (fin.read(buffer) > 50) {
                                    String s = new String(buffer);
                                    int index = 0;
                                    int tmp = s.indexOf("sign2:");
                                    if(tmp > 0) {
                                        while (s.indexOf("sign2:", tmp) > 0 && tmp > 0) {
                                            index = s.indexOf("sign1:", index + 1);
                                            tmp = s.indexOf("sign2:", tmp + 1);
                                            Log.e("!!!!!!!!", index + "  " + tmp);
                                        }
                                    }else{
                                        index = s.lastIndexOf("sign1:");
                                    }

                                    if(index >= 0) {
                                        sign1 = s.substring(index + 6, s.indexOf("\n", index));
                                    }
                                    index = s.lastIndexOf("sign2:");
                                    if(index >= 0) {
                                        sign2 = s.substring(index + 6, s.indexOf("\n", index));
                                    }
                                    index = s.lastIndexOf("cmd:");
                                    if(index >= 0) {
                                        state_cmd = s.substring(index + 4, s.indexOf("\n", index));
                                    }
                                }
                            }
                        }catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    else{//Connection failed
                        String err = "Cannot connect device with error status: " + status;
                        gatt.close();
                        if (mBluetoothGatt != null) {
                            mBluetoothGatt.disconnect();
                            mBluetoothGatt.close();
                            mBluetoothGatt = null;
                        }
                        if (mDevice != null) {
                            mBluetoothGatt = mDevice.connectGatt(act, false, mGattCallback);
                        }
                        Log.e("[ERR]", err);
                    }
                }
            }
            //Device connection state change to disconnected.
            else if (newState == BluetoothProfile.STATE_DISCONNECTED ||newState == BluetoothProfile.STATE_DISCONNECTING) {
                Log.i("", "BluetoothDevice DISCONNECTED: " );
                isServiceConnected = false;
                Message msg = new Message();
                msg.what = 4;
                mhandler.sendMessage(msg);

                if (mBluetoothGatt != null) {
                    mBluetoothGatt.disconnect();
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                }
                gatt.close();
                if (mDevice != null) {
                    mBluetoothGatt = mDevice.connectGatt(act, false, mGattCallback);
                }
            }
        }

        /**
         * Callback triggered as a result of a remote characteristic notification/indication.
         * Note that the value within the characteristic object may have changed since receiving the remote characteristic notification,
         * so check the parameter value for the value at the time of notification.
         * @param gatt BluetoothGatt: GATT client the characteristic is associated with
         * @param characteristic BluetoothGattCharacteristic: Characteristic that has been updated as a result of a remote notification event.
         */
        @Override
        public final void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] value = characteristic.getValue();
            String data = Conversion.Bytes2HexString(value).toUpperCase();
            //Show the received response on the screen
            Message msg = new Message();
            msg.what = 2;
            msg.obj = ">>" + Conversion.Bytes2HexString(value) + "\n";
            mhandler.sendMessage(msg);
            String cnt_resp = "null";

            String auto_pattern = "00041A021802"; //Response: Authentication-level requirement
            if (!data.startsWith(auto_pattern) && epoch) { //Not Authentication-level requirement
                //record the data in log_app file
                Log.d("[recv]", "receive:" + data);

                int index = data.lastIndexOf("08") + 2;
                if(index > 3){
                    cnt_resp = data.substring(index);
                }

                try {
                    writeLog(">>" + data + "\n");
                    if (data.startsWith("004512431A4104")) {//If received the public key, record in pk file
                        record("pk:" + data + "\n");
                    }
                    else if(data.startsWith("001C1A1A12160A14")){//received token
                        record("token:" + data + "\n");
                        startSend(sign2);
                        epoch = false;
                    }
                    else if(!counter.equals(cnt_resp)){
                        counter = sign1.substring(sign1.indexOf("30") +2, sign1.indexOf("2A04",sign1.indexOf("30")));
                        startSend(sign1);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Callback invoked when the list of remote services, characteristics and descriptors for the remote device have been updated,
         * ie new services have been discovered.
         * @param gatt BluetoothGatt: GATT client invoked BluetoothGatt#discoverServices
         * @param status int: BluetoothGatt#GATT_SUCCESS if the remote device has been explored successfully.
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            List<BluetoothGattService> mServiceList;
            mServiceList = gatt.getServices();
            if (mServiceList != null) {
                System.out.println(mServiceList);
                System.out.println("Services num:" + mServiceList.size());

                for (BluetoothGattService service : mServiceList){
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    System.out.println("扫描到Service：" + service.getUuid());

                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                        System.out.println("characteristic: " + characteristic.getUuid() );
                    }

                    if(service.getUuid().equals(SER_UUID)){
                        car_service = service;
                        characteristic_TX = car_service.getCharacteristic(TX_UUID);
                        characteristic_RX = car_service.getCharacteristic(RX_UUID);

                        BluetoothGattDescriptor descriptor = characteristic_RX.getDescriptor(DESPCRIPTOR);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(descriptor);
                        mBluetoothGatt.setCharacteristicNotification(characteristic_RX, true);
                    }
                }
            }
        }

        /**
         * Callback indicating the result of a descriptor write operation.
         * @param gatt BluetoothGatt: GATT client invoked BluetoothGatt#writeDescriptor
         * @param descriptor BluetoothGattDescriptor: Descriptor that was write to the associated remote device.
         * @param status 	int: The result of the write operation BluetoothGatt#GATT_SUCCESS if the operation succeeds.
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }
    };

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
                case 4:
                    try {
                        mac = getAddress();
                        addr.setText(mac);
                        connect.setEnabled(true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case 3:
                    addr.setText(getResources().getString(R.string.connected));
                    connect.setEnabled(false);
                    disconn.setEnabled(true);
                    break;
                case 2:
                    log_data.append(""+msg.obj);
                    break;
                case 1:
                    tx_to_car.setText((String)msg.obj);
                    break;
                case 0:
                    log_data.append(""+msg.obj);
                    tx_to_car.setText("");
                    break;
                default:
                    Log.e("[err]", "unknown handler");
            }
        }
    }
}