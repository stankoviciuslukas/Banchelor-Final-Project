package android.lukas.advspvol3;
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

import android.app.AlertDialog;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static android.lukas.advspvol3.ControlActivity.EXTRAS_DEVICE_NAME;
import static android.lukas.advspvol3.ControlActivity.EXTRA_RANGE_STATE;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service{
    private final static String TAG = BluetoothLeService.class.getSimpleName();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;;
    private BluetoothGattCharacteristic characteristic;
    int count_zero = 0; //RSSI signalo pakeitimu skaiciavimas - kiek kartu signalas buvo aukstenis arba zemesnis uz riba
    int count_not_zero = 0;
    int globalRSSICheck = -67; //defaultinis rssi
    int globalTryCount = 1; //default bandymų skaičius
    int changeRSSICounter = 0;
    //Busenu indikacija
    public int stateRSSI = STATE_BUZZER_OFF; //public, jog kiekvienas metodas galetu pasiekti kintamaji
    public int silentCheck = 0;
    private static final int STATE_BUZZER_ON = 1;
    private static final int STATE_BUZZER_OFF = 0;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int SILENT_ENABLED = 0;

    public final static String ACTION_GATT_CONNECTED =
            "android.lukas.advspvol3.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "android.lukas.advspvol3.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "android.lukas.advspvol3.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "android.lukas.advspvol3.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "android.lukas.advspvol3.EXTRA_DATA";
    public final static String ACTION_RSSI_VALUE_READ =
            "android.lukas.advspvol3.ACTION_RSSI_VALUE_READ";
    public final static String ACTION_RANGE_SET =
            "android.lukas.advspvol3.ACTION_RANGE_SET";
    public final static String ACTION_PHONE_ALERT =
            "android.lukas.advspvol3.ACTION_PHONE_ALERT";


    private final static UUID OWN_PRIVATE_SERVICE = UUID.fromString("5e3c75a8-818a-4be8-90af-2f3e56acd402"); //Privatus servisas skirtas komunikacijai su BLE prietaisu
    private final static UUID OWN_PRIVATE_CHAR = UUID.fromString("e586bd8a-4dc1-4856-b53d-b7611d538061"); //Privati charakteristika su write ir notify parametrais
    private final static UUID OWN_PRIVATE_DES = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //Charakteristikos deskriptorius leidziantis keisti charakteristikos duomenis.

    //GATT procesu atgalinis pranesimas, kuomet esama skirtingos BLE busenos

    public final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //Būsenų gaviklis, pagal jį valdomas prietaiso garsinis signalas
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Prisijungta prie GATT serverio.");
                // Gaunami palaikomi servisai
                Log.i(TAG, "Gaunami palaikomi BLE įrenginio servisai:" +
                        mBluetoothGatt.discoverServices()); //mumis numeta i onServicesDiscovered callbacka'

                //Atitinkama laikotarpi (siuo atveju 2 sekundes) skaitomas RSSI signalas

                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            if (stateRSSI == STATE_BUZZER_OFF && silentCheck == 0) { //tikrinama, jeigu garsinis signalas isjungtas - nuskaityti RSSI signala
                                mBluetoothGatt.readRemoteRssi(); //numeta i onReadRemoteRSSI callbacka
                                Log.d(TAG,"readRemoteRssicalled");
                            }
                        }
                    };
                    Timer rssiTimer = new Timer();
                    rssiTimer.schedule(task, 2000, 2000); //apie 2 sekundes


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) { //Jeigu prietaisas atsijungia nuo mobilaus irenginio - apie tai pranesti
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Atsijungta nuo GATT serverio.");
                broadcastUpdate(intentAction); // pranesimas visoms klases (kurios klausosi)
            }
        }
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) { //RSSI signalo nuskaitymo funckija, kiekvieciama kiekviena kart
            //po mBluetoothGatt.readRemoteRssi();
            if(status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_RSSI_VALUE_READ, rssi); //gautos RSSI reiksmes pranesimas kitoms klasems
                //tikrinimas, jeigu RSSI neatitinka signalo reikšmių, įjungti arba išjungti garsinį signalą. //atitinkamai skaičiuojama tris kartus
                //siekiant išvengti RSSI signalo stipraus padidėjimo dėl pašalinių objektų.


                //TO-DO Fix Mute Setting and rename variables to be more understanding and shit brah




                    if (rssi < globalRSSICheck) {
                        changeRSSICounter++;
                        Log.d(TAG, "count_not_zero " + changeRSSICounter);
                        if(changeRSSICounter > globalTryCount){
                            Log.d(TAG, "IF_veikia");
                            String data = "enable_sound";
                            writeCharacteristic(characteristic, data); //kas 2 sekundes siunciam RSSI i prietaisa
                            playAlert();
                            changeRSSICounter = 0;

                        }
                        /*
                        if(count_not_zero >= 3){
                            playAlert();
                            Log.d(TAG, "SKAMBA_NOW");
                            String data = "not_zero";
                            writeCharacteristic(characteristic, data); //kas 2 sekundes siunciam RSSI i prietaisa
                            count_not_zero = 0;
                            count_zero = 0;
                        }
                        */
                    }
                    else if (rssi > globalRSSICheck) {
                        String data = "disable_sound";
                        writeCharacteristic(characteristic, data); //kas 2 sekundes siunciam RSSI i prietaisa
                        //count_not_zero = 0;
                    }
            }
        }
        private void playAlert() {
            final Intent intent = new Intent(BluetoothLeService.ACTION_PHONE_ALERT);
            intent.setAction(BluetoothLeService.ACTION_PHONE_ALERT);
            Log.d(TAG, "ACTION_PHONE_ALERT - ON");
            sendBroadcast(intent);
        }
        //Šis metodas labiau laikomas kaip patikra ar mūsų prietaisas aptinka privatų servisą bei charakteristikas
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) { //metodas kiekviečiamas, kai aptinkami palaiko servisai
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED); //pranešimas kitoms klasėms.
                List<BluetoothGattService> gattServices = gatt.getServices(); //Get the list of services discovered
                Log.d(TAG, "Gautų servisų skaičius" + gattServices.size());
                for (BluetoothGattService gattService : gattServices) {
                    String serviceUUID = gattService.getUuid().toString();
                    List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                    Log.d("SERVICES_DISC", "Service uuid "+serviceUUID);
                    Log.d("SERVICES_CHAR", "Service char "+gattCharacteristics.toString());
                }
                if(gattServices != null){
                    Log.d(TAG, "Rasti servisai" + gattServices.toString());
                }
                //Svarbesnis patikrinimas ar aptinkamas privatus servisas su charakteristika
                characteristic = gatt.getService(OWN_PRIVATE_SERVICE).getCharacteristic(OWN_PRIVATE_CHAR);
                //Tikrinama ar yra palaikoma notify bei write parametrai
                if((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0){
                    Log.d(TAG, "Charakteristika turi write property");
                }
                if((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    Log.d(TAG, "Charakteristika turi notify property");
                    //Deskriptorius leidžia atlikti įrašymo bei pranešimo funkcijas apie privačia charakteristiką
                    BluetoothGattDescriptor descriptor =
                            characteristic.getDescriptor(OWN_PRIVATE_DES);
                    descriptor.setValue(
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    setCharacteristicNotification(characteristic, true);
                    mBluetoothGatt.writeDescriptor(descriptor);
                }

                Log.d(TAG, "Charakteristikos paieška" + characteristic.toString());
                if(characteristic != null){
                    Log.d(TAG, "Privati charakteristika - rasta");
                }
            } else {
                Log.w(TAG, "onServicesDiscovered: " + status);
            }
        }
        //Dar vienas patikrinimas
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getCharacteristic().getUuid().equals(UUID.fromString("e586bd8a-4dc1-4856-b53d-b7611d538061"))) {
                    Log.d(TAG, "Deskriptorius rado reikiamą UUID");
                }
                BluetoothGattService Service = gatt.getService(UUID.fromString("5e3c75a8-818a-4be8-90af-2f3e56acd402"));
                BluetoothGattCharacteristic charac = Service
                        .getCharacteristic(UUID.fromString("e586bd8a-4dc1-4856-b53d-b7611d538061"));
            } else {
                Log.e(TAG, "Klaida");
            }
        }
        //Charakteristikos rašymas į BLE pretaisą
        public void writeCharacteristic(BluetoothGattCharacteristic characteristic,
                                        String data) {

            if (mBluetoothAdapter == null || mBluetoothGatt == null) {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return;
            }
            //Pagal RSSI yra įjungiamas arba išjungiamas garsinis signalizatorius
            //Charakteristikai yra priskiriama reikšme (baitų)
            if(data.equals("enable_sound")) {
                characteristic.setValue(new byte[]{02}); //signalas įjungiamas
            }
            else if(data.equals("disable_sound")){
                characteristic.setValue(new byte[]{00}); //signalas išjungiamas
            }
            try {
                Log.d(TAG, "Pranešimas: " + URLEncoder.encode(data, "utf-8"));
                mBluetoothGatt.writeCharacteristic(characteristic); //charakteristikos rašymas
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        //Charakteristikos nuskaitymas
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            }
        }
        //Charakteristikos pakeitimas
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            //broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
        //Šis metodas atsakingas už mygtuko būsenos filtravimą (kada nuspaustas, kada ne) iš Control Activity klasės
        //Mygtuko paspaudimo įjungiamas arba išjungiama prietaiso garsas.

        private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if(ControlActivity.ACTION_ENABLE_ADVSP_SOUND.equals(action)){
                    if(stateRSSI == STATE_BUZZER_OFF) {
                        Log.d(TAG, "ACTION_ENABLE");
                        String data = "enable_sound";
                        writeCharacteristic(characteristic, data);
                        stateRSSI = STATE_BUZZER_ON;
                    }
                    else if(stateRSSI == STATE_BUZZER_ON){
                        String data = "disable_sound";
                        writeCharacteristic(characteristic, data);
                        stateRSSI = STATE_BUZZER_OFF;
                    }
                }
                if(ControlActivity.ACTION_ENABLE_SILENT.equals(action)){
                    silentCheck = 1; //miego režimas įjungtas
                    Log.d(TAG, "SILENT_ENABLED");
                }
                if(ControlActivity.ACTION_DISABLE_SILENT.equals(action)){
                    silentCheck = 0; //miego režimas išjungtas
                    Log.d(TAG, "SILENT_DISABLED");
                }
                if(ControlActivity.ACTION_RANGE_SET_CHANGE.equals(action)){
                    String intentValueHolder = intent.getStringExtra(ACTION_RANGE_SET);
                    globalTryCount = Integer.parseInt(intentValueHolder);
                    Log.d(TAG, "RANGE_SET_CHANGE " + intentValueHolder);
                }
            }
        };

        //Mygtuko būsenos filtras
        private IntentFilter makeGattUpdateIntentFilter() {
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ControlActivity.ACTION_ENABLE_ADVSP_SOUND);
            intentFilter.addAction(ControlActivity.ACTION_ENABLE_SILENT);
            intentFilter.addAction(ControlActivity.ACTION_DISABLE_SILENT);
            intentFilter.addAction(ControlActivity.ACTION_RANGE_SET_CHANGE);
            Log.d(TAG, "makeGatt_intentfilter_iškviestas");
            return intentFilter;
        }
    };
    //Pranešimų skleidimas kitoms klasėms, tiksliau RSSI gautos reikšmės perdavimas ir atvaizdavimas į ControlActivity klasę
    private void broadcastUpdate(final String action, final int rssi) {
        final Intent intent = new Intent(action);
        String extra_rssi = Integer.toString(rssi);
        intent.putExtra(ControlActivity.EXTRAS_DEVICE_RSSI, extra_rssi);
        intent.setAction(BluetoothLeService.ACTION_RSSI_VALUE_READ);
        Log.d(TAG, "rssi_action = " + action);
        Log.d(TAG, "RSSI = " + rssi);
        sendBroadcast(intent);
    }
    //Priklausomai ar yra RSSI reikšmė ar ne, kviečiamas skirtingas metodas.
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

/*
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);


        //remove special handling for time being
        Log.w(TAG, "broadcastUpdate()");

        final byte[] data = characteristic.getValue();

        Log.v(TAG, "data.length: " + data.length);

        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data) {
                stringBuilder.append(String.format("%02X ", byteChar));

                Log.v(TAG, String.format("%02X ", byteChar));
            }
            intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
        }

        sendBroadcast(intent);
    }
    */



    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    public boolean initialize() {
        //Bluetooth defaultinio adapterio iniciavimas
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }
    //Prisijungimo metodas - kieviečiamas iškarto, po prisijungti mygtuko paspausdimo
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        // Iš naujo prisijungimo įgyvendinimas, jeigu nutrūksta ryšys su prietaisu.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            mConnectionState = STATE_DISCONNECTED;
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
        //Įjungiamas automatinis prisijungiamas
        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }
    //Nustatomas charakteristikos pranešimas
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }
    //Atvaizduojami palaikomi BLE prietais servisai
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}