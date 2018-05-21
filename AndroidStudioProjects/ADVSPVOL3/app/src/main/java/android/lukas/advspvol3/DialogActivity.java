package android.lukas.advspvol3;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
public class DialogActivity extends Activity{
    private final static String TAG = DialogActivity.class.getSimpleName();
    //Klasės veiksmų iniciavimas
    public static final String DEVICE_RSSI = "DEVICE_RSSI";
    public static final String ACTION_SEND_MEAN_RSSI =
            "android.lukas.advspvol3.ACTION_SEND_MEAN_RSSI";
    private static final int BUTTON_OFF = 0;
    private static final int BUTTON_ON = 1;
    public int oneMeterButtonState = BUTTON_OFF;
    public int twoMeterButtonState = BUTTON_OFF;
    public int threeMeterButtonState = BUTTON_OFF;
    public int resetButtonState = BUTTON_OFF;
    int receivedRSSI;
    //Mygtukų iniciavimas
    Button oneMeter, twoMeter, threeMeter, resetBtn;
    private ProgressBar firstProgressBar, secondProgressBar, thirdProgressBar;
    CountDownTimer waitTimer;
    //DialogActivy klasės sukūrimas su mygtukai ir progreso apskritimais proceso vizualizacijai
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog);
        oneMeter = findViewById(R.id.oneButton);
        twoMeter = findViewById(R.id.twoButton);
        threeMeter = findViewById(R.id.threeButton);
        resetBtn = findViewById(R.id.resetButton);
        firstProgressBar = findViewById(R.id.progressBar);
        secondProgressBar = findViewById(R.id.progressBartwo);
        thirdProgressBar = findViewById(R.id.progressBarthree);
        firstProgressBar.setVisibility(View.INVISIBLE);
        secondProgressBar.setVisibility(View.INVISIBLE);
        thirdProgressBar.setVisibility(View.INVISIBLE);
        //Mygtukų būsenos stebėjimas, jeigu nuspaustas - siunčiama rssi reikšmė
        oneMeter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                oneMeterButtonState = BUTTON_ON;
                oneMeter.setVisibility(View.INVISIBLE);
                firstProgressBar.setVisibility(View.VISIBLE);
                countRSSIvalues();
            }
        });
        twoMeter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                twoMeterButtonState = BUTTON_ON;
                twoMeter.setVisibility(View.INVISIBLE);
                secondProgressBar.setVisibility(View.VISIBLE);
                countRSSIvalues();
            }
        });
        threeMeter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                threeMeterButtonState = BUTTON_ON;
                threeMeter.setVisibility(View.INVISIBLE);
                thirdProgressBar.setVisibility(View.VISIBLE);
                countRSSIvalues();
            }
        });
        //Iš naujo nustatyto mygtukas, kuris sugrąžina pradinę atstumo konstantą
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(DialogActivity.this, "Atstatyti gamikliniai nustatymai", Toast.LENGTH_SHORT)
                        .show();
                oneMeterButtonState = BUTTON_OFF;
                twoMeterButtonState = BUTTON_OFF;
                threeMeterButtonState = BUTTON_OFF;
                resetButtonState = BUTTON_ON;
                oneMeter.setVisibility(View.VISIBLE);
                oneMeter.setText("NUSTATYTI");
                oneMeter.setClickable(true);
                twoMeter.setVisibility(View.VISIBLE);
                twoMeter.setText("NUSTATYTI");
                twoMeter.setClickable(true);
                threeMeter.setVisibility(View.VISIBLE);
                threeMeter.setText("NUSTATYTI");
                threeMeter.setClickable(true);
                checkState();
            }
        });
    }
    //Nuskaitoma rssi vertė, kas 10 sekundžių. Toks laikas pasirinktas dėl signalo lygio stabilizavimo
    private void countRSSIvalues() {
        waitTimer = new CountDownTimer(10000,1000) {
            @Override
            public void onTick(long l) {
            }
            @Override
            public void onFinish() {
                Log.d(TAG, "onFinish_called");
                Log.d(TAG, "receivedRSSI: "+ receivedRSSI);
                waitTimer.cancel();
                checkState();
            }
        }.start();
    }
    //Mygtukų būsenos tikrinimas, jeigu buvo nors vienas nuspaustas nusiunčiama signalo vertė ir nebeleidžiama daugiau skenuoti to paties taško
    //Nebent yra paspaudžiamas iš naujo nustatymo mygtukas
    private void checkState() {

        if (oneMeterButtonState == BUTTON_ON) {
            Log.d(TAG, "meanRSSI = " + receivedRSSI);
            firstProgressBar.setVisibility(View.GONE);
            oneMeter.setVisibility(View.VISIBLE);
            oneMeter.setText("OK");
            oneMeter.setClickable(false);
            sendMeanValue(receivedRSSI);
        }
        if (twoMeterButtonState == BUTTON_ON){
            Log.d(TAG, "meanRSSI = " + receivedRSSI);
            secondProgressBar.setVisibility(View.GONE);
            twoMeter.setVisibility(View.VISIBLE);
            twoMeter.setText("OK");
            twoMeter.setClickable(false);
            sendMeanValue(receivedRSSI);
        }
        if (threeMeterButtonState == BUTTON_ON){
            Log.d(TAG, "meanRSSI = " + receivedRSSI);
            thirdProgressBar.setVisibility(View.GONE);
            threeMeter.setVisibility(View.VISIBLE);
            threeMeter.setText("OK");
            threeMeter.setClickable(false);
            sendMeanValue(receivedRSSI);
        }
        if (resetButtonState == BUTTON_ON){
            int defaultConst = 0;
            resetButtonState = BUTTON_OFF;
            sendMeanValue(defaultConst);
            Log.d(TAG, "sendRSSI" + defaultConst);
        }
    }
    //Vidurkinta signalo lygio reikšmė grąžinama į ControlActivity klasę
    private void sendMeanValue(int rssi) {
        final Intent intent = new Intent(ACTION_SEND_MEAN_RSSI);
        String hold = Integer.toString(rssi);
        intent.putExtra(ControlActivity.EXTRAS_MEAN_RSSI, hold);
        intent.setAction(DialogActivity.ACTION_SEND_MEAN_RSSI);
        sendBroadcast(intent);
    }
    //Iš BluetoothLeService suvidurkintos reikšmės gavimas
    private final BroadcastReceiver mGattUpdateReceiverDialog = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(BluetoothLeService.ACTION_RSSI_VALUE_READ.equals(action)) {
                receivedRSSI = Integer.valueOf(intent.getStringExtra(DEVICE_RSSI));
                Log.d(TAG, "BroadcastReceiver: meanRSSI" + receivedRSSI);
            }
        }
    };
    protected void onResume() {
        super.onResume();
        //Įjungiamas veiksmų gaviklis, pagal jį atnaujinami tekstiniai (būsenos laukai)
        registerReceiver(mGattUpdateReceiverDialog, makeGattUpdateIntentFilterDialog());
    }
    //Jeigu klasė sustabdoma išjungiamas kitų klasių veiksmų aptikimas
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiverDialog);
    }
    //Veiksmų filtro iniciavimas
    private static IntentFilter makeGattUpdateIntentFilterDialog() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_RSSI_VALUE_READ);
        return intentFilter;
    }
}
