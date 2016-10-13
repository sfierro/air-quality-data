package com.samfierro.hiri;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.security.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private EditText pm25; private EditText pm10;
    private EditText temp; private EditText hum;
    private EditText lat; private EditText lon;

    private Button getButton;
    private Button eraseButton;
    private Button sendButton;
    private Button connectButton;
    private Button visualizeButton;
    private TextView connectText;
    private WebView webView;
    private TouchyWebView visualizeView;
    private Button refreshButton;
    private Button continueDataButton;

    private Boolean connected = false;
    private Boolean paired = false;
    private Boolean collectData = false;

    private BluetoothAdapter BTAdapter;
    private BluetoothDevice myDevice;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public static int REQUEST_BLUETOOTH = 1;

    private double longitude;
    private double latitude;

    private String newDate;
    private String newTime;

    private LocationManager mLocationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BTAdapter = BluetoothAdapter.getDefaultAdapter();
        setUpBluetooth();

        pm25 = (EditText) findViewById(R.id.pm25Text);
        pm10 = (EditText) findViewById(R.id.pm10Text);
        temp = (EditText) findViewById(R.id.temp);
        hum = (EditText) findViewById(R.id.humidity);
        lat = (EditText) findViewById(R.id.lat);
        lon = (EditText) findViewById(R.id.lon);

        webView = (WebView) findViewById(R.id.webView);
        connectText = (TextView) findViewById(R.id.connectText);

        continueDataButton = (Button) findViewById(R.id.keepGettingDataButton);
        continueDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                continueData();
            }
        });

        connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothButton();
            }
        });

        eraseButton = (Button) findViewById(R.id.eraseButton);
        eraseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                eraseData();
            }
        });

        sendButton = (Button) findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendData();
            }
        });

        getButton = (Button) findViewById(R.id.getDataButton);
        getButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getData();
            }
        });

        visualizeButton = (Button) findViewById(R.id.visualizeButton);
        visualizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                visualize();
            }
        });

        //loads the cartodb map into a webview that is currently hidden until "visualize" is clicked
        visualizeView = (TouchyWebView) findViewById(R.id.vizWeb);
        visualizeView.getSettings().setJavaScriptEnabled(true);

//      #############replace with correct embedded map.##########################################################
        visualizeView.loadUrl("https://samfierro.cartodb.com/viz/2942f7d2-3980-11e6-9d7a-0ecfd53eb7d3/embed_map");

        refreshButton = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshMap();
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        setUpBluetooth();
    }

    //calls function to start or stop getting data constantly
    private void continueData() {
        if (!collectData) {
            collectData = true;
            continueDataButton.setText("No Continuar Recibiendo Datos");
            handler.post(runSendData);

        } else {
            continueDataButton.setText("Continuar Recibiendo Datos");
            collectData = false;
            handler.removeCallbacks(runSendData);
        }
    }

    //gets and sends data every 5 seconds
    Handler handler = new Handler();
    private final Runnable runSendData = new Runnable(){
        public void run(){
            try {
                //prepare and send the data here..
                getData();
                sendData();
//###############scheduled to run every 5 seconds. change the 5 to another number to change the seconds.
//###############for example, 30000 would be 30 seconds.
                handler.postDelayed(this, 5000);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    /**
     * Finds paired bluetooth device for sensor
     */
    private void setUpBluetooth() {
        // Phone does not support Bluetooth so let the user know and exit.
        if (BTAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Your device does not support Bluetooth")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            System.exit(0);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
        if (!BTAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, REQUEST_BLUETOOTH);
        }

        Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
//              ########################## BLUETOOTH DEVICE NAME #####################
                if (device.getName().equals("HC-06")) {
                    paired = true;
                    myDevice = device;
                    break;
                }

            }
        }
    }

    /**
     * Connects paired bluetooth sensor
     */
    private void bluetoothButton() {
        if (!paired) {
            pairDialog();
        }
        else if (!connected) {
            connectText.setText("Conectando...");
            try {
                socket = myDevice.createRfcommSocketToServiceRecord(PORT_UUID);
                socket.connect();
            } catch (IOException e) {System.out.println(e);}
            connected = true;
            connectButton.setText("Desconéctate");
            connectText.setText("Conectado" + " " + myDevice.getName().toString());
            getButton.setEnabled(true);
            continueDataButton.setEnabled(true);
        } else {
            try {socket.close();} catch (IOException e) {System.out.println(e);}
            connected = false;
            connectButton.setText("Conéctate");
            connectText.setText("No Conectado");
            getButton.setEnabled(false);
            continueDataButton.setEnabled(false);
        }
    }

    private Location getLastKnownLocation() {
        mLocationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            try{
                Location l = mLocationManager.getLastKnownLocation(provider);
                if (l == null) {
                    continue;
                }
                if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                    // Found best last known location: %s", l);
                    bestLocation = l;
                }} catch (SecurityException e) {}
        }
        return bestLocation;
    }

    /**
     * Reads data from bluetooth sensor and gets geocoordinate
     */
    private void getData() {
        try {
            String data = "";
            inputStream = socket.getInputStream();
            long end = System.currentTimeMillis() + 1000;
            while (System.currentTimeMillis() < end) {
                int byteCount = inputStream.available();
                if (byteCount > 0) {
                    byte[] rawBytes = new byte[byteCount];
                    inputStream.read(rawBytes);
                    final String dataString = new String(rawBytes, "UTF-8");
                    data += dataString;
                }
            }
//            Toast.makeText(MainActivity.this)
            List<String> dataList = Arrays.asList(data.split(","));
            if (dataList.size() > 5) {
                String num = dataList.get(dataList.size() - 5);
                if (Character.isWhitespace(num.charAt(0))) {
                    num = num.substring(2, num.length());
                    pm25.setText(num);
                }
            }
        } catch (IOException e) {
            System.out.println(e);
        }
        try {
            //gets location
            Location location = getLastKnownLocation();
            longitude = location.getLongitude();
            latitude = location.getLatitude();
            lon.setText("" + longitude);
            lat.setText("" + latitude);
        } catch (SecurityException e) {
            System.out.println(e);
        }
    }

    private void getTime() {
        //gets date and time
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        newDate = dateFormat.format(date);
        List<String> dateTime = Arrays.asList(newDate.split(" "));
        newDate = dateTime.get(0);
        newDate = "'" + newDate + "'";
        newTime = dateTime.get(1);
        newTime = "'" + newTime + "'";
        //####TO SEND A STRING TO CARTODB YOU NEED TO ADD WRAP IT IN SINGLE QUOTES LIKE THIS: 'myString' ####
    }

    /**
     * Sends the data to cartoDB
     */
    private void sendData() {
        getTime();
        //variable names for entered text
        String lat_coord = lat.getText().toString();
        String long_coord = lon.getText().toString();
        String pm25String = pm25.getText().toString();
        String pm10String = pm10.getText().toString();
        String tempString = temp.getText().toString();
        String humString = hum.getText().toString();
        if (lat_coord.equals("") && long_coord.equals("") && pm25String.equals("")
                && pm10String.equals("") && tempString.equals("") && humString.equals("")) {
            sendDialog();
        } else {
//          ################replace link with correct cartoDB user name and api key.################################
//          ################can change what values are sent depending on cartoDB table.#############################
            String link = "https://samfierro.cartodb.com/api/v2/sql?q=INSERT INTO test (pm_25, date, time, the_geom) VALUES ("+pm25String+", "+newDate+", "+newTime+", ST_SetSRID(ST_Point("+long_coord+", "+lat_coord+"),4326))&api_key=02e8c4a7c19b20c6dd81015ea2af533aeadf19de";
            webView.loadUrl(link);
            Toast.makeText(MainActivity.this,"Datos enviado",Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Loads cartoDB map into web view and makes it visible
     */
    private void visualize() {
        if (visualizeButton.getText().equals("Visualizar")) {
            visualizeView.setVisibility(View.VISIBLE);
            refreshMap();
            visualizeButton.setText("No Visualizar");
            refreshButton.setVisibility(View.VISIBLE);
        }
        else {
            visualizeView.setVisibility(View.GONE);
            visualizeButton.setText("Visualizar");
            refreshButton.setVisibility(View.GONE);
        }
    }

    private void refreshMap() {
        visualizeView.reload();
    }

    private void eraseData() {
        pm25.setText("");
        pm10.setText("");
        temp.setText("");
        hum.setText("");
        lat.setText("");
        lon.setText("");
    }

    private void pairDialog() {
        //dialog appears the bluetooth device isn't paired, so press OK to go to bluetooth screen in settings!
        new AlertDialog.Builder(this)
                .setTitle("No Bluetooth Device Paired")
                .setMessage("Please pair device in settings")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intentOpenBluetoothSettings = new Intent();
                        intentOpenBluetoothSettings.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                        startActivity(intentOpenBluetoothSettings);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void sendDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No datos para enviar")
                .setMessage("Por favor ponga datos antes de enviar")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

}