package android.lukas.advspvol3;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "MyActivity";
    private BluetoothAdapter mBluetoothAdapter; //Pagrindinė Bluetooth klasė
    private BluetoothLeScanner mBluetoothLeScanner; //BLE prietaisų skaneris

    private boolean mScanning; //Skenavimo atvaizdavimui

    private static final int RQS_ENABLE_BLUETOOTH = 1; // Prašymas įjungti bluetooth adapterį

    Button btnScan; //IEŠKOTI mygtuko iniciavimas
    ListView listViewLE; //Listas

    List<BluetoothDevice> listBluetoothDevice; //Bluetooth listas/yra skirtumas tarp listo ir bluetooth isto
    ListAdapter adapterLeScanResult; //Skanavimo rezultatų talpinimas
    TextView mDeviceName;
    TextView mDeviceRSSI;
    private String deviceUserSetName = "";
    String mac = "00:1E:C0:59:AE:95"; //ADVSP MAC adresas
    int foundADVSP = 0;

    private Handler mHandler; //Pranešimų rašymui
    private static final long SCAN_PERIOD = 5000; // skanavimo periodas 10 sekundžių

    //Kai tik paleidžiama program visų elementų atvaizdavimas
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE); //paslepiam loadinimo bara
        mDeviceRSSI = (TextView) findViewById(R.id.get_rssi);
        mDeviceName = (TextView)findViewById(R.id.ble_name);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/nevis.ttf");
        mDeviceName.setTypeface(typeface);
        mDeviceName.setText("ADVSP");
        mDeviceName.setVisibility(View.VISIBLE);
        mDeviceRSSI.setVisibility(View.INVISIBLE);
        //BLE funkcijos patikrinimas ant mobilaus telefono
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //Pranešimas, jog prietaisas nepalaiko BLE
            Toast.makeText(this, "BLE nepalaikomas", Toast.LENGTH_SHORT).show();
            finish(); //Programo išėjimas
        }
        getBluetoothAdapterAndLeScanner();         //Gaunam prietaiso defaultini adapterį ir skanerį
        //Patikrinam ar yra adapteris
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Nerastas bluetooth adapteris", Toast.LENGTH_SHORT).show();
            finish(); //Programos išėjimas
            return;
        }

        btnScan = (Button) findViewById(R.id.btn_scan); //ID priskirimas prie IEŠKOTI mygtukas
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getBaseContext(), "Pradedama skenuoti ADVSP", Toast.LENGTH_SHORT).show(); //apvalus pranesimas
                btnScan.setVisibility(View.INVISIBLE);// paslepiam mygtuka
                checkBTPermissions(); // Marshmallow apėjimas, tikrinimas privilegijų
                scanLeDevice(true); //Skanavimas
                findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE); //Skenavimo progress bar
            }
        });
        //Telefono defaultinio ringtono paleidimas -> tikriausiai keliaus į control activity klasę
        /*
        //muzikele kai mygtukas paspaustas cia siaip - sito reikes telefono suradimo funkcijai
        Button one = (Button) this.findViewById(R.id.mediabutton);
        final MediaPlayer mp = MediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI);
        one.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                    if(mp.isPlaying()){
                        mp.pause();
                    }
                    else {
                        mp.start();
                    }
            }
        });
        */

        listViewLE = (ListView) findViewById(R.id.lelist); //List ID priskirimas
        //Sąrašų iniciavimas
        listBluetoothDevice = new ArrayList<>();
        adapterLeScanResult = new ArrayAdapter<BluetoothDevice>(this, android.R.layout.simple_expandable_list_item_1,
                listBluetoothDevice);
        listViewLE.setAdapter(adapterLeScanResult);
        // listViewLE.setOnItemClickListener(scanResultOnItemClickListener);
        mHandler = new Handler(); //Pranešimų atvaizdavimui
    }
    //-------------------------------------------------------------------------------------------------
    //Nustatomas prietaiso tipas
    private String getBTDeviceType(BluetoothDevice d){
        String type = "";
        switch (d.getType()){
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                type = "DEVICE_TYPE_CLASSIC";
                break;
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                type = "DEVICE_TYPE_DUAL";
                break;
            case BluetoothDevice.DEVICE_TYPE_LE:
                type = "DEVICE_TYPE_LE";
                break;
            case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                type = "DEVICE_TYPE_UNKNOWN";
                break;
            default:
                type = "unknown...";
        }
        return type;
    }
    //-----------------------------------------------------------------------------------------------
    //Defaultinių metodu perrašymas, siekiant išvengti klaidų
    @Override
    protected void onResume(){
        super.onResume();
        //Jeigu adapteris nėra įjungtas
        if(!mBluetoothAdapter.isEnabled()){
            Intent enableBtIntent = new Intent (BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, RQS_ENABLE_BLUETOOTH);
        }
    }
    //----------------------------------------------------------------------------------------------
    //Būsenų gaviklis - galbūt ir nereikia jo
    private final BroadcastReceiver receiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            final Intent intent_to_control = new Intent(MainActivity.this,
                    ControlActivity.class);
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                int  rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE);
                Log.d(TAG,"  Prie intent: " + rssi + "dBm");
                String RSSI = Integer.toString(rssi);
                intent_to_control.putExtra(ControlActivity.EXTRAS_RSSI,RSSI);
            }
            sendBroadcast(intent_to_control);
        }
    };
    //--------------------------------------------------------------------------------------------------
    private void getBluetoothAdapterAndLeScanner(){
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mScanning = false;
    }
//-----------------------------------------------------------------------------------------------------------------------
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            listBluetoothDevice.clear(); //Listo ištrinimas
            listViewLE.invalidateViews(); //Listo atnaujinimas
            // Stabdomas skenavimas po SCAN_PERIOD
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeScanner.stopScan(scanCallback);
                    findViewById(R.id.loadingPanel).setVisibility(View.GONE); //Progress baro paslėpimas
                    listViewLE.invalidateViews(); //Listo atnaujinimas

                    Toast.makeText(MainActivity.this,
                            "Skanavimas baigtas",
                            Toast.LENGTH_LONG).show();

                    btnScan.setVisibility(View.VISIBLE); //matomas
                    if(foundADVSP == 1) {
                        btnScan.setText("PRISIJUNGTI");
                        btnScan.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                builder.setTitle("Nustatykite ADVSP varda ir spauskite prisijungti");
                                final EditText input = new EditText(MainActivity.this);
                                input.setInputType(InputType.TYPE_CLASS_TEXT);
                                builder.setView(input);
                                //builder.setMessage(msg)
                                builder.setPositiveButton("PRISIJUNGTI", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if(deviceUserSetName == ""){
                                            deviceUserSetName = "ADVSP";
                                        }else{
                                            deviceUserSetName = input.getText().toString();
                                        }
                                        final Intent intent = new Intent(MainActivity.this, ControlActivity.class);
                                        intent.putExtra(ControlActivity.EXTRAS_DEVICE_NAME, deviceUserSetName);
                                        intent.putExtra(ControlActivity.EXTRAS_DEVICE_ADDRESS, mac);
                                        if (mScanning) {
                                            mBluetoothLeScanner.stopScan(scanCallback);
                                            mScanning = false;
                                            btnScan.setEnabled(true);
                                        }
                                        startActivity(intent);
                                    }
                                })
                                        .setNeutralButton("IŠEITI", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                            }
                                        })
                                        .show();

                            }
                        });
                    }
                    mScanning = false; //neskenuojama
                    btnScan.setEnabled(true);
                }
            }, SCAN_PERIOD);

            mBluetoothLeScanner.startScan(scanCallback);
            mScanning = true;
            btnScan.setEnabled(false); //kai pradedama skenuoni, nebeleidzia vartotojui random spaudineti mygtuko, kad nepareitu viskas
        } else {
            mBluetoothLeScanner.stopScan(scanCallback);
            mScanning = false;
            btnScan.setEnabled(true); //leidziama skenuoti
        }
    }
    //-------------------------------------------------------------------------------------------------------------
    //Po kiekvieno skenavimo kviečiamas metodas
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            final int rssi = result.getRssi(); // Gaunam pradinę RSSI vertę
            addBluetoothDevice(result.getDevice(), rssi);
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(receiver, discoverDevicesIntent);
        }

//------------------------------------------------------------------------------------------------------
        @Override
        public void onScanFailed(int errorCode) { //Klaidų aptikimui
            super.onScanFailed(errorCode);
            Toast.makeText(MainActivity.this,
                    "onScanFailed: " + String.valueOf(errorCode),
                    Toast.LENGTH_LONG).show();
        }
        private void addBluetoothDevice(BluetoothDevice device, int new_rssi){ //Prietaisų pridėjimas į sąrašą

            if(!listBluetoothDevice.contains(device)) {
                //Jeigu prietaiso nėra sąraše, jį pridedam
                if (mac.equals(device.getAddress())) { //MAC filtras, jog tik ADVSP prietaisas būtų rastas
                    foundADVSP = 1;
                    mBluetoothLeScanner.stopScan(scanCallback);
                    mScanning = false;
                    findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
                    //Klaidų tikrinimui
                    Log.d(TAG, "Device Name:" + device.getName());
                    Log.d(TAG, "device MAC" + device.getAddress());
                    Log.d(TAG, "Add bluetoothDevice funkcija:" + new_rssi);
                    listBluetoothDevice.add(device); //Prietaiso pridėjimas į sąrašą
                    //mDeviceName.setText("Rastas " + device.getName());
                    String found_rssi = Integer.toString(new_rssi);
                    mDeviceRSSI.setText("Prietaiso signalo lygis: " + found_rssi + " dBm");
                    mDeviceName.setVisibility(View.VISIBLE);
                    mDeviceRSSI.setVisibility(View.VISIBLE);
                    listViewLE.invalidateViews(); //Sąrašo atnaujinimas
                    //
                }
            }
        }
    };
    //---------------------------------------------------------------------------------------------------------------------------
    //API 18+ versijos apėjimas dėl privilegijų skyrimo
    private void checkBTPermissions() {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            if (permissionCheck != 0) {

                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
            }
        }
        }
    }