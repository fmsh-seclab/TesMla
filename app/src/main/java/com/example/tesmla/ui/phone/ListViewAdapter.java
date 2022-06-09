package com.example.tesmla.ui.phone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.tesmla.R;

import java.util.List;

/**
 * This class help show the result of BLE scan. It record the name, mac address and broadcast data for each device which been scanned.
 */

public class ListViewAdapter extends BaseAdapter {
    //Info like Name and mac address which listview need
    private final List<BlueTooth_item__Bean> listDatas;
    private final LayoutInflater layoutInflater;

    ListViewAdapter(Context mcontext, List<BlueTooth_item__Bean> listDatas){
        this.listDatas = listDatas;
        layoutInflater =  LayoutInflater.from(mcontext);
    }
    @Override
    public int getCount() {
        return listDatas.size();
    }

    @Override
    public Object getItem(int position) {
        return listDatas.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("InflateParams")
    @Override
    public View getView(int position, View view, ViewGroup parent) {
        ListviewHolder listviewHolder;
        if(view ==null){
            listviewHolder = new ListviewHolder();
            view = layoutInflater.inflate(R.layout.listview_item, null);
            listviewHolder.device_Name = view.findViewById(R.id.device_name);
            listviewHolder.device_Address= view.findViewById(R.id.device_address);
            view.setTag(listviewHolder);
        }else{
            listviewHolder =  (ListviewHolder) view.getTag();
        }
        listviewHolder.device_Name.setText(listDatas.get(position).blueToothDevie_Name);
        listviewHolder.device_Address.setText(listDatas.get(position).blueToothDevie_Address);
        return view;
    }



    static class  ListviewHolder{
        private TextView device_Name;
        private TextView device_Address;
    }

    //This class is used for item of listview. Record name, mac address and broadcast data for each item
    public static class BlueTooth_item__Bean {
        String blueToothDevie_Name;
        String blueToothDevie_Address;
        String record_data;

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof  BlueTooth_item__Bean){
                if(blueToothDevie_Address.equals(((BlueTooth_item__Bean) obj).blueToothDevie_Address)){
                    return true;
                }
            }else{
                return false;
            }
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return blueToothDevie_Address.hashCode();
        }
    }
}
