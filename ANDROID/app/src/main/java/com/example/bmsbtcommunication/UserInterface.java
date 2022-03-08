package com.example.bmsbtcommunication;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class UserInterface extends AppCompatActivity {
    public static final String TAG = "BluetoothLeService";
    private static final long SCAN_PERIOD = 3000;
    private final int REQUEST_ENABLE_BT = 1;
    private final int REQUEST_ENABLE_LOCATION = 2;
    private final int REQUEST_LOCATION_PERMISSION_APPROVE = 3;
    private TextView connectedDeviceText;
    private final String ENVIRONMENTAL_SERVICE_UUID = "1234";
    private ScanSettings scanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();
    private Set<BluetoothDevice> devicesFoundedDuringScanning = new HashSet<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private LocationManager locationManager;
    private BluetoothGatt bluetoothGatt; //it’s the gateway to other BLE operations such as service discovery, reading and writing data, and even performing a connection teardown
    private boolean scanning;
    private Handler handler = new Handler();

    private ScanCallback leScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    devicesFoundedDuringScanning.add(result.getDevice());
                }
            };

    private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if(status == BluetoothGatt.GATT_SUCCESS){
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    bluetoothGatt = gatt; 
                    String info = "Connected to " + gatt.getDevice().getName();
                    connectedDeviceText.setText(info);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close();
                }
            } else {
                Log.w("BluetoothGattCallback", "Error"+ status+ " encountered for " + gatt.getDevice().getAddress()+ "! Disconnecting...");
                connectedDeviceText.setText("");
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            System.out.println("-------------------------------------------------" + gatt.getServices() + " status: " + status);
            for (BluetoothGattService service : gatt.getServices()) {
                for (BluetoothGattCharacteristic characteristic   : service.getCharacteristics()) {
                    System.out.println("read " + gatt.readCharacteristic(characteristic));
                    System.out.println(Arrays.toString(characteristic.getValue()));
                    System.out.println("write " + gatt.writeCharacteristic(characteristic));
                    for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                        System.out.println(descriptor);

                    }
                }
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            switch(status){
                case(BluetoothGatt.GATT_SUCCESS):
                    Log.i("BluetoothGattCallback", "Read characteristic : " + characteristic.getUuid().toString());
                break;
                case(BluetoothGatt.GATT_READ_NOT_PERMITTED):
                    Log.e("BluetoothGattCallback", "Read not permitted for" + characteristic.getUuid().toString());
                    break;
                default:
                    Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status");
                break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_interface);

        Button scanButton = findViewById(R.id.scan_button);
        Button startButton = findViewById(R.id.start_interaction_button);

        TextView resultOfScanning = findViewById(R.id.result_of_scanning);
        connectedDeviceText = findViewById(R.id.connected_device);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askForLocationPermission();
                startScanning(resultOfScanning);

            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(bluetoothGatt != null) {
                    bluetoothGatt.discoverServices();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!bluetoothAdapter.isEnabled()) {
            askToEnableBT();
        }

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            askToEnableLocation();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case (REQUEST_ENABLE_BT):
                if (resultCode == Activity.RESULT_OK) {
                    return;
                } else {
                    askToEnableBT();
                }
                break;

            case (REQUEST_ENABLE_LOCATION):
                if (resultCode == Activity.RESULT_OK) {
                    return;
                } else {
                    askToEnableLocation();
                }
                break;
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION_APPROVE) {
            return;
        } else {
            askForLocationPermission();
        }
    }


//ASKING USER TO ENABLE THE NECESSARY STUFF
    private void askToEnableBT() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    public void askToEnableLocation() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Do You want to turn on your location")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void askForLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                REQUEST_LOCATION_PERMISSION_APPROVE);
    }


//SCANNING
    private void startScanning(TextView resultOfScanning) {
        devicesFoundedDuringScanning.clear();
        if (!scanning) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                    Toast.makeText(getApplicationContext(), "Scanning was finished", Toast.LENGTH_LONG).show();
                    String result = "";

                    for (BluetoothDevice device : devicesFoundedDuringScanning) {

                        if(device.getName() != null) {
                            result += device.getName() + "\n";
                            System.out.print(device.getName() + " ");
                        }else{
                            result += device.getAddress() +"\n";
                            System.out.print(device.getAddress() + " ");
                        }
                        if ("WiPy 3.0".equals(device.getName())) {
                            device.connectGatt(UserInterface.this, false, bluetoothGattCallback);
                        }

                    }
                    System.out.println("");
                    resultOfScanning.setText(result);
                }
            }, SCAN_PERIOD);
            //after scan period the new thread starts
            scanning = true;
            bluetoothLeScanner.startScan(null, scanSettings, leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }
}


