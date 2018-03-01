package android.lukas.advspvol3;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanCallback;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SeekBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class ControlActivity extends Activity {

    private final static String TAG = ControlActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_RSSI = "DEVICE_RSSI";
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRAS_RSSI = "EXTRA_RSSI";
    public static final String EXTRA_RANGE_STATE = "EXTRA_RANGE_STATE";
    public static final String ACTION_ENABLE_ADVSP_SOUND =
            "android.lukas.advspvol3.ACTION_ENABLE_ADVSP_SOUND";
    public static final String ACTION_ENABLE_SILENT =
            "android.lukas.advspvol3.ACTION_ENABLE_SILENT";
    public static final String ACTION_DISABLE_SILENT =
            "android.lukas.advspvol3.ACTION_DISABLE_SILENT";
    public static final String ACTION_RANGE_SET_CHANGE =
            "android.lukas.advspvol3.ACTION_RANGE_SET_CHANGE";

    private String mDeviceName;
    private String mDeviceAddress;
    private String mDeviceRSSI;
    private Button mFindDevice;
    public int btn_state = STATE_BUZZER_OFF;
    private static final int STATE_BUZZER_ON = 1;
    private static final int STATE_BUZZER_OFF = 0;

    private BluetoothLeService mBluetoothLeService;
    TextView textViewState;
    TextView textViewName; //reikia, kad jie butu public, kitaip funkcijos ju neras
    TextView textViewRSSI;
    TextView textViewDeviceAddr;
    TextView textViewRangeSet;
    Switch silentTime;
    SeekBar rangeSet;
    int tryCount = 0; //Bandymų skaičiaus indikacija
    int tryRSSICount = 0;
    int setRangeValue;


    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    private enum State {STARTING, ENABLING, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, DISCONNECTING};
    State state = State.STARTING;


    // Kontrolės serviso įgyvendinimas
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Jeigu viskas gerai - patikrinama ar yra bluetooth ir palaikomas BLE, tai automatiskai bandoma prisijungti
            mBluetoothLeService.connect(mDeviceAddress);
            state = State.CONNECTING; //nustatom kokia cia busena
            updateConnectionState(); //jungiamasi pranesimas
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            state = State.DISCONNECTED; //nustatom kokia cia busena
            Log.d(TAG, "ON_SERVICE iskviestas");
            updateConnectionState(); //jungiamasi pranesimas
        }
    };
    //Pranešimų gaviklis, pagal atitinkamus pranešimus, vykdo funkcijas
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                state = State.CONNECTED;
                updateConnectionState(); //Atnaujinama prisijungimo būsena
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                state = State.DISCONNECTED;
                Log.d(TAG, "ACTION_GATT_DIS iskviestas");
                updateConnectionState();
                //Iš naujo prisijungimas, jeigu yra atsijungiam nuo serverio - prietaiso.
                if(tryCount >= 4){
                    tryCount++;
                    mBluetoothLeService.connect(mDeviceAddress); //reconnectas
                    Log.d(TAG, "TRY_COUNT" + Integer.toString(tryCount));
                }
                //Jungiamas prie to paties MAC adreso.
            //Gautos RSSI reikšmės atvaizdavimas žodžiais
            } else if (BluetoothLeService.ACTION_RSSI_VALUE_READ.equals(action)) {
                mDeviceRSSI = intent.getStringExtra(EXTRAS_DEVICE_RSSI);
                updateRSSIValue(mDeviceRSSI);
                //playAlert(mDeviceRSSI);
                Log.d(TAG, "mDeviceRSSI = " + mDeviceRSSI);
            }
            else if (BluetoothLeService.ACTION_PHONE_ALERT.equals(action)){
                Toast.makeText(ControlActivity.this, "ADVSP dingo", Toast.LENGTH_LONG).show();
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(5000);
            }
        }
    };


    //Atgalinio mygtuko paspaudimas, kuris sukuria Alert pranešimą su pasirinkimo variantais.
    public void onBackPressed(){
        new android.app.AlertDialog.Builder(ControlActivity.this)
                .setTitle("Ar tikrai norite išeiti?")
                .setMessage("Jeigu išeisite prietaisas atsijungs nuo mobilaus įrenginio")
                .setPositiveButton("Išeiti", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                        //čia dar reikės pagalvoti, kad programa nelūžtų
                        //Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        //intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
                        //startActivity(intent);
                    }
                })
                .setNeutralButton("Grįžti", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();

    }
    //RSSI signalo atvaizdavimas
    //Čia dar reikės su atstumu kažkaip pažaisti??
    private void updateRSSIValue(String get_rssi){
        double rssi_int = Integer.parseInt(get_rssi);
        double A = (rssi_int+33)/-20;
        Log.d(TAG, "paskaiciuotas A = " + A);
        double distance = Math.pow(10,A);
        Log.d(TAG, "Atsumas paskaiciuots: " + distance + " m");
        if(rssi_int > -30) {
            Log.d(TAG, "Signalo stiprumas: " + get_rssi);
            textViewRSSI.setText("Signalo stiprumas: Nuostabus");
        } else if (-30 >rssi_int && rssi_int > -67){
            Log.d(TAG, "Signalo stiprumas: " + get_rssi);
            textViewRSSI.setText("Signalo stiprumas: Labai geras");
        } else if (-67 >rssi_int && rssi_int > -70){
            Log.d(TAG, "Signalo stiprumas: " + get_rssi);
            textViewRSSI.setText("Signalo stiprumas: Geras");
        } else if (-70 >rssi_int && rssi_int > -80){
            Log.d(TAG, "Signalo stiprumas: " + get_rssi);
            textViewRSSI.setText("Signalo stiprumas: Nelabai");
        } else if (-80 >rssi_int){
            Log.d(TAG, "Signalo stiprumas: " + get_rssi);
            textViewRSSI.setText("Signalo stiprumas: Prie mirties");
        }
    }

    //private void clearUI() {
    //    mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
    //}
    //Pagrindinis metodas sukuriamas, kai paleidžiama ControlActivity
    //Sukuriami reikalingi elementai informacijos atvaizdavimui.
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mDeviceRSSI = intent.getStringExtra(EXTRAS_RSSI);
        mFindDevice = (Button) findViewById(R.id.findDevice);
        textViewDeviceAddr = (TextView)findViewById(R.id.textDeviceAddress);
        textViewRSSI = (TextView)findViewById(R.id.device_rssi);
        textViewState = (TextView)findViewById(R.id.textState);
        textViewName = (TextView)findViewById(R.id.textDeviceName);
        textViewRangeSet = (TextView)findViewById(R.id.rangeSetView);
        textViewRangeSet.setText("Nuotolio nustatymas: Arti - Toli");
        textViewName.setText("Vardas: " + mDeviceName); //cia kad rastu sita dalyka
        textViewDeviceAddr.setText("MAC: " + mDeviceAddress);
        textViewRSSI.setText(mDeviceRSSI);
        silentTime = (Switch) findViewById(R.id.silent_time);
        rangeSet = (SeekBar) findViewById(R.id.rangeSet);
        //Atstumo nustatymo vienas iš įgyvendinimo būdų
        silentTime.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked) {
                    final Intent intent = new Intent(ControlActivity.ACTION_ENABLE_SILENT);
                    intent.setAction(ControlActivity.ACTION_ENABLE_SILENT);
                    sendBroadcast(intent);
                }
                else{
                    final Intent intent = new Intent(ControlActivity.ACTION_DISABLE_SILENT);
                    intent.setAction(ControlActivity.ACTION_DISABLE_SILENT);
                    sendBroadcast(intent);
                }
            }
        });
        rangeSet.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean isChanged) {
                if(isChanged){
                    setRangeValue = seekBar.getProgress();
                    if(setRangeValue > 50){
                        tryRSSICount = 1; //Jeigu prietaisas yra toli - RSSI šuoliai bus mažesni
                        //darom prielaidą, jog mažiau kliūčių kažkokių gali atsirasti
                    }
                    else{
                        tryRSSICount = 3; //Jeigu prietaisas yra arti - RSSI šuolių tikimybė yra didesnė dėl kliūčių kiekio
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(ControlActivity.this, "Pakitimai išsaugoti", Toast.LENGTH_LONG).show();
                final Intent intent = new Intent(ControlActivity.ACTION_RANGE_SET_CHANGE);
                intent.setAction(ControlActivity.ACTION_RANGE_SET_CHANGE);
                String tryCountString = Integer.toString(tryRSSICount);
                intent.putExtra(BluetoothLeService.ACTION_RANGE_SET, tryCountString);
                sendBroadcast(intent);
            }
        });



        //Rankinis prietaiso garsinio signalo įjungimas
        //Pagal atitinkams prietaiso buzzerio būseną, signalas išjungiamas arba įjungiamas.
        mFindDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(btn_state == STATE_BUZZER_OFF) {
                    Toast.makeText(getBaseContext(), "Įjungiam ADVSP signalą", Toast.LENGTH_SHORT).show();
                    final Intent intent = new Intent(ControlActivity.ACTION_ENABLE_ADVSP_SOUND);
                    intent.setAction(ControlActivity.ACTION_ENABLE_ADVSP_SOUND);
                    sendBroadcast(intent);
                    btn_state = STATE_BUZZER_ON;
                }
                else if(btn_state == STATE_BUZZER_ON){
                    Toast.makeText(getBaseContext(), "Išjungiam ADVSP signalą", Toast.LENGTH_SHORT).show();
                    final Intent intent = new Intent(ControlActivity.ACTION_ENABLE_ADVSP_SOUND);
                    intent.setAction(ControlActivity.ACTION_ENABLE_ADVSP_SOUND);
                    sendBroadcast(intent);
                    btn_state = STATE_BUZZER_OFF;
                }
            }
        });

        //Paleidžiama BluetoothLeService klasė, kuri iš esmės atsakinga už visą sujungimo su prietaisu valdymą
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        // sukuria ir sujungia service'a su bluetoothleservice objektu, kuris prijungia ir isjungia visus profilius(servisus)
    }
    @Override
    protected void onResume() {
        super.onResume();
        //Įjungiamas veiksmų gaviklis, pagal jį atnaujinami tekstiniai (būsenos laukai)
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
    protected void onDestroy() { //cia terminuojam procesa, kai viska uzbaigiam
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }
    //klausomes ka klase bluetoothleservice broadcastina, tuos broadcastus pagaunam ir pagal tai vykdom kitas funkcijas
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_RSSI_VALUE_READ);
        intentFilter.addAction(BluetoothLeService.ACTION_PHONE_ALERT);
        return intentFilter;
    }

    private static HashMap<String, String> attributes = new HashMap();

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }

    //Busenu atnaujinimas, priklausomai nuo broadcastu
    private void updateConnectionState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (state) {
                    case STARTING:
                    case ENABLING:
                    case SCANNING:
                    case DISCONNECTED:
                        textViewState.setText(R.string.not_connected);
                        setProgressBarIndeterminateVisibility(false);                              //Hide circular progress bar
                        break;
                    case CONNECTING:
                        textViewState.setText(R.string.connecting);
                        setProgressBarIndeterminateVisibility(true);                                //Show circular progress bar
                        break;
                    case CONNECTED:
                        textViewState.setText(R.string.connected);
                        setProgressBarIndeterminateVisibility(false);                               //Hide circular progress bar
                        break;
                    case DISCONNECTING:
                        textViewState.setText(R.string.disconnecting);
                        setProgressBarIndeterminateVisibility(false);                               //Hide circular progress bar
                        break;
                    default:
                        state = State.STARTING;
                        textViewState.setText(R.string.enabling_bluetooth);
                        setProgressBarIndeterminateVisibility(false);                               //Hide circular progress bar
                        break;
                }
                if (mDeviceName != null) {                                                        //Patikrinam ar yra vardas
                    textViewName.setText(mDeviceName);                                //Jeigu yra parasom
                }
                else {
                    textViewName.setText(R.string.unknown);                             //jeigu ne - tai nezinomas
                }
            }
        });
    }
}