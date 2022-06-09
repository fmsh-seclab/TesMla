package com.example.tesmla.ui.phone;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.example.tesmla.databinding.FragmentPhoneBinding;
import com.example.tesmla.utils.Conversion;
import com.example.tesmla.utils.ToastUtils;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * This Class show as middle fragment
 * Function: 1. Scan surrounding BLE devices and show the result with the MAC address & name in the form of list
 *           2. Change Bluetooth Setting of Mac address and Bluetooth adapter Name according to the selected device
 *           3. Record MAC address, name, broadcast data of the selected device
 */
public class PhoneFragment extends Fragment {

    private FragmentPhoneBinding binding;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBLEScanner;
    private boolean mScanning = false;
    private String fake_name;

    private ListViewAdapter adapter;
    private List<ListViewAdapter.BlueTooth_item__Bean> BlueToothDevice_Info;  //蓝牙设备的信息
    private EditText addr;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentPhoneBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        Button start_scan = binding.startScan;
        Button stop_scan  = binding.stopScan;
        Button mac_change = binding.changeMac;
        ListView listview = binding.lvScan;
        addr = binding.macToBe;

        start_scan.setOnClickListener((View v) -> startBLEScan());
        stop_scan.setOnClickListener((View v) -> stopBLEScan());
        mac_change.setOnClickListener((View v) -> changeMAC());

        //If bluetooth has not been enable, start the bluetooth service
        FragmentActivity act = getActivity();
        if(act != null) {
            BluetoothManager mBluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();
        }
        if(!mBluetoothAdapter.isEnabled()){
            mBluetoothAdapter.enable();
        }


        initData();
        adapter = new ListViewAdapter(getActivity(), BlueToothDevice_Info);
        listview.setAdapter(adapter);

        //Click the list view item to choose the specific device.
        //when select one device, record the mac addr, name and broadcast data in /data/data/com.example.tesmla/files/fake_dev.
        listview.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id)->{
            ListViewAdapter.BlueTooth_item__Bean tesla = BlueToothDevice_Info.get(position);
            BufferedWriter writer = null;
            try{
                if(act != null) {
                    FileOutputStream out = act.openFileOutput("fake_dev", Context.MODE_PRIVATE);
                    writer = new BufferedWriter(new OutputStreamWriter(out));
                    writer.write(tesla.blueToothDevie_Name);
                    writer.append("\n");
                    writer.append(tesla.blueToothDevie_Address);
                    writer.append("\nspecific_data:");
                    writer.append(tesla.record_data);
                    writer.append("\n");
                    ToastUtils.showToast(getActivity(), "已保存", 2000);
                    addr.setText(tesla.blueToothDevie_Address);//show the mac address of selected device on the top of the fragment
                    fake_name = tesla.blueToothDevie_Name;
                }

            }
            catch(IOException e){
                e.printStackTrace();
            }finally {
                try{
                    if(writer != null){writer.close();}
                }catch (IOException e){
                    e.printStackTrace();
                }

            }
        });

        return root;
    }

    /**
     * Show the basic listview window
     */
    private void initData() {
        //Simulate the BLE device info
        BlueToothDevice_Info = new ArrayList<>();
        for(int i  = 0  ;i<10;i++){
            ListViewAdapter.BlueTooth_item__Bean bluetooth_device_item_info = new ListViewAdapter.BlueTooth_item__Bean();
            bluetooth_device_item_info.blueToothDevie_Name = "蓝牙设备名字"+i;
            bluetooth_device_item_info.blueToothDevie_Address = "蓝牙设备mac地址"+i;
            BlueToothDevice_Info.add(bluetooth_device_item_info);
        }
    }

    private void openAndInitBt() {
        if (mBluetoothAdapter == null) {
            ToastUtils.showToast(getActivity(), "不支持蓝牙", 2000);
            return;
        }//do not support the bluetooth

        FragmentActivity act = getActivity();
        if (act != null) {
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                ToastUtils.showToast(getActivity(), "不支持ble蓝牙", 2000);//do not support the BLE
            }
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
     * Change the Bluetooth MAC address and name of device
     * Pre-requirement: rebuild the Android system and open the interface to change property of ro.vendor.bt.boot.macaddr
     */
    public void changeMAC(){
        if(mScanning) {
            mScanning = false;
            stopBLEScan();
        }
        mBluetoothAdapter.setName(fake_name);

        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        }

        //get the new mac address and set to the property
        String mac_new = addr.getText().toString();
        if(stringIsMac(mac_new)){
            Process process = null;
            DataOutputStream os = null;
            try {
                process = Runtime.getRuntime().exec("su"); //switch to permission of root
                os = new DataOutputStream(process.getOutputStream());
                String cmd = "setprop ro.vendor.bt.boot.macaddr " + addr.getText().toString();
                os.writeBytes(cmd + "\n");
                os.writeBytes("exit\n");
                os.flush();
                Log.e("[MAC]", addr.getText().toString());
                process.waitFor();
            } catch (Exception e) {
                Log.e("[err]", "mac addr set failed.");
            } finally {
                try {
                    if(os!= null) os.close();
                    if(process != null) process.destroy();
                } catch (Exception e) {
                    Log.w("[ERR]", "error occur " + e);
                }
            }
            ToastUtils.showToast(getActivity(), "修改成功", 1000);//不支持ble蓝牙

        } else{
            ToastUtils.showToast(getActivity(), "MAC地址不合法", 1000);//不支持ble蓝牙
        }
    }

    private void startBLEScan(){
        openAndInitBt();
        BlueToothDevice_Info.clear();
        if(!mBluetoothAdapter.isEnabled()){
            mBluetoothAdapter.enable();
        }

        FragmentActivity act = getActivity();
        if(act != null) {
            int checkCallPhonePermission = ContextCompat.checkSelfPermission(act, Manifest.permission.ACCESS_COARSE_LOCATION);
            //Determine whether need to indicate the user allowing the permission
            if (checkCallPhonePermission != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.ACCESS_FINE_LOCATION))
                    ToastUtils.showToast(getActivity(), "开位置权限", 2000);
                ActivityCompat.requestPermissions(act, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                return;
            }
        }

        if (!mBluetoothAdapter.isEnabled()){
            ToastUtils.showToast(getActivity(), "蓝牙未开启", 2000);
        }else {
            if (mScanning) {
                ToastUtils.showToast(getActivity(), "正在扫描", 2000);
            } else {
                // Set the current state of scanning
                mScanning = true;
                if (mBLEScanner == null) {
                    mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                }
                mBLEScanner.startScan(null, mScanSetting(), mScanCallback);
                //Set stopping the process of scan automatically
                new  Handler().postDelayed(()-> {
                    if(mScanning){
                        mScanning = false;
                        mBLEScanner.stopScan(mScanCallback);
                        Log.w("[end]", "stop searching");
                }},10000);
            }
        }
    }

    public ScanSettings mScanSetting(){
        //Create object of ScanSettings' build to set params
        ScanSettings.Builder builder = new ScanSettings.Builder()
                 //Use low power mode as default.
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        //android 6.0 add the type of callback and match mode settings
        //Search for the BLE broadcast package which satisfies the filter
        builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        //Set the matching mode of bluetooth LE scan filter hardware matching
        //MATCH_MODE_STICKY Mode: For sticky mode, higher threshold of signal strength and sightings is required before reporting by hw
        builder.setMatchMode(ScanSettings.MATCH_MODE_STICKY);
        //Determine whether the bluetooth chip support the scanning in batches
        if (mBluetoothAdapter.isOffloadedScanBatchingSupported()) {
            //Set the delay of reporting the result.(unit: ms)
            //Set to 0 means report the result immediately
            builder.setReportDelay(0L);
        }
        return builder.build();
    }

    ScanCallback mScanCallback = new ScanCallback() {

        //When scanning get the broadcast package
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            //Operate the info of scanned devices (mac,name,broadcast data)
            BluetoothDevice device = result.getDevice();
            final ListViewAdapter.BlueTooth_item__Bean bean = new ListViewAdapter.BlueTooth_item__Bean();
            if(device.getName() != null) {
                bean.blueToothDevie_Name = device.getName();
            }
            else{
                bean.blueToothDevie_Name = "NULL";
            }
            bean.blueToothDevie_Address = device.getAddress();
            byte[] manufacture_data = result.getScanRecord().getManufacturerSpecificData(0x4c);
            if(manufacture_data != null) {
                bean.record_data = Conversion.Bytes2HexString(manufacture_data);
            }else{
                bean.record_data = "null";
            }
            if(!BlueToothDevice_Info.contains(bean)){
                BlueToothDevice_Info.add(bean);
                adapter.notifyDataSetChanged();
            }
        }
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            //Return scan results in batches
            //@param results the former list of scan results.
        }
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            //Called when start of BLE scan failed.
            //Scanning too often will get ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED. The APP cant be registered.
        }
    };

    public void stopBLEScan(){
        Log.w("[stop]","stop BLE scan");
        if(mScanning && mBLEScanner != null) {
            mBLEScanner.stopScan(mScanCallback);
            mScanning = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopBLEScan();
        binding = null;
    }
}