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
    Button oneMeter, twoMeter, threeMeter, resetBtn;
    private ProgressBar firstPR, secondPR, thirdPR;
    public static final String DEVICE_RSSI = "DEVICE_RSSI";
    public static final String ACTION_SEND_MEAN_RSSI =
            "android.lukas.advspvol3.ACTION_SEND_MEAN_RSSI";
    int gotRSSI;
    int intRSSI = 0, sumRSSI = 0, rssiHold;
    CountDownTimer waitTimer;
    private static final int BUTTON_OFF = 0;
    private static final int BUTTON_ON = 1;
    public int FIRST_BUTTON_STATE = BUTTON_OFF;
    public int SECOND_BUTTON_STATE = BUTTON_OFF;
    public int THIRD_BUTTON_STATE = BUTTON_OFF;
    public int RESET_BUTTON_STATE = BUTTON_OFF;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog);
        oneMeter = (Button) findViewById(R.id.oneButton);
        twoMeter = (Button) findViewById(R.id.twoButton);
        threeMeter = (Button) findViewById(R.id.threeButton);
        resetBtn = (Button) findViewById(R.id.resetButton);
        firstPR = (ProgressBar) findViewById(R.id.progressBar);
        secondPR = (ProgressBar) findViewById(R.id.progressBartwo);
        thirdPR = (ProgressBar) findViewById(R.id.progressBarthree);
        firstPR.setVisibility(View.INVISIBLE);
        secondPR.setVisibility(View.INVISIBLE);
        thirdPR.setVisibility(View.INVISIBLE);
        oneMeter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FIRST_BUTTON_STATE = BUTTON_ON;
                oneMeter.setVisibility(View.INVISIBLE);
                firstPR.setVisibility(View.VISIBLE);
                countRSSIvalues();
                //checkState();
            }
        });

        twoMeter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SECOND_BUTTON_STATE = BUTTON_ON;
                twoMeter.setVisibility(View.INVISIBLE);
                secondPR.setVisibility(View.VISIBLE);
                countRSSIvalues();
                //checkState();
            }
        });
        threeMeter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                THIRD_BUTTON_STATE = BUTTON_ON;
                threeMeter.setVisibility(View.INVISIBLE);
                thirdPR.setVisibility(View.VISIBLE);
                countRSSIvalues();
                //checkState();
            }
        });
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(DialogActivity.this, "Atstatyti gamikliniai nustatymai", Toast.LENGTH_SHORT)
                        .show();
                FIRST_BUTTON_STATE = BUTTON_OFF;
                SECOND_BUTTON_STATE = BUTTON_OFF;
                THIRD_BUTTON_STATE = BUTTON_OFF;
                RESET_BUTTON_STATE = BUTTON_ON;
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

    private void countRSSIvalues() {
        waitTimer = new CountDownTimer(3000,1000) {
            @Override
            public void onTick(long l) {
            }

            @Override
            public void onFinish() {
                Log.d(TAG, "onFinish_called");
                waitTimer.cancel();
                checkState();
            }
        }.start();

    }

    private void checkState() {

        if (FIRST_BUTTON_STATE == BUTTON_ON) {
            Log.d(TAG, "meanRSSI = " + gotRSSI);
            firstPR.setVisibility(View.GONE);
            oneMeter.setVisibility(View.VISIBLE);
            oneMeter.setText("OK");
            oneMeter.setClickable(false);
            sendRSSIValue(gotRSSI);
        }
        if (SECOND_BUTTON_STATE == BUTTON_ON){
            Log.d(TAG, "meanRSSI = " + gotRSSI);
            secondPR.setVisibility(View.GONE);
            twoMeter.setVisibility(View.VISIBLE);
            twoMeter.setText("OK");
            twoMeter.setClickable(false);
            sendRSSIValue(gotRSSI);
        }
        if (THIRD_BUTTON_STATE == BUTTON_ON){
            Log.d(TAG, "meanRSSI = " + gotRSSI);
            thirdPR.setVisibility(View.GONE);
            threeMeter.setVisibility(View.VISIBLE);
            threeMeter.setText("OK");
            threeMeter.setClickable(false);
            sendRSSIValue(gotRSSI);
        }
        if (RESET_BUTTON_STATE == BUTTON_ON){
            int defaultConst = 0;
            RESET_BUTTON_STATE = BUTTON_OFF;
            sendRSSIValue(defaultConst);
            Log.d(TAG, "sendRSSI" + defaultConst);
        }
    }

    private void sendRSSIValue(int rssi) {
        final Intent intent = new Intent(ACTION_SEND_MEAN_RSSI);
        String hold = Integer.toString(rssi);
        intent.putExtra(ControlActivity.EXTRAS_MEAN_RSSI, hold);
        intent.setAction(DialogActivity.ACTION_SEND_MEAN_RSSI);
        Log.d(TAG,"sendRSSIValue_called" + hold);
        sendBroadcast(intent);
    }

    private final BroadcastReceiver mGattUpdateReceiverDialog = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(BluetoothLeService.ACTION_RSSI_VALUE_READ.equals(action)) {
                gotRSSI = Integer.valueOf(intent.getStringExtra(DEVICE_RSSI));
                Log.d(TAG, "BroadcastReceiver: meanRSSI" + gotRSSI);
            }
        }
    };
    protected void onResume() {
        super.onResume();
        //Įjungiamas veiksmų gaviklis, pagal jį atnaujinami tekstiniai (būsenos laukai)
        registerReceiver(mGattUpdateReceiverDialog, makeGattUpdateIntentFilterDialog());
    }
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiverDialog);
    }
    private static IntentFilter makeGattUpdateIntentFilterDialog() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_RSSI_VALUE_READ);
        return intentFilter;
    }



}
