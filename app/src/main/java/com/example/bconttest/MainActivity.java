package com.example.bconttest;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;


import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity implements BeaconConsumer, SensorEventListener {

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final String DEVICE_UUID = "50DF832E-667A-11EB-AE93-0242AC130002";
    private BeaconManager beaconManager;

    // Compass variables
    private ImageView image;
    private float currentDegree = 0f;
    private SensorManager mSensorManager;
    TextView tvHeading;
    //End

    TextToSpeech tts;
    int closestBeaconHolder = -1;

    String TAG;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {                    //On Create Start
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        image = (ImageView) findViewById(R.id.imageViewCompass);
        tvHeading = (TextView) findViewById(R.id.tvHeading);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        beaconManager = BeaconManager.getInstanceForApplication(this);
//        ALTBEACON      m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25
//        EDDYSTONE TLM  x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15
//        EDDYSTONE UID  s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19
//        EDDYSTONE URL  s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v
//        IBEACON        m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        beaconManager.bind(this);

        tts = new TextToSpeech(this,this::onInit);

        //Prompting permissions for SDKs higher than 23
        if (Build.VERSION.SDK_INT >= 23) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                        Log.d(TAG, "onDismiss: ACCESS_FINE_LOCATION =  " + Manifest.permission.ACCESS_FINE_LOCATION);
                    }
                });
                builder.show();
            }
        }
//        ====================================================================================================================
    }       //On Create End

    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
        } else {
            Log.e("TAG", "TTS Initialization failed");
        }

    }

    //For compass
    @Override
    protected void onResume() {
        super.onResume();

        // System's orientation sensor registered listener
        mSensorManager.registerListener(this,mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // To save batter we stop the listener here
        mSensorManager.unregisterListener(this);
    }
    //end

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location permission granted");

                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.removeAllMonitorNotifiers();
        beaconManager.removeAllRangeNotifiers();
        beaconManager.unbind(this);
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                Log.i(TAG, "didEnterRegion: Beacon Spotted...");
            }

            @Override
            public void didExitRegion(Region region) {
                Log.i(TAG, "didExitRegion: Beacon Lost");
            }

            @Override
            public void didDetermineStateForRegion(int i, Region region) {
                Log.i(TAG, "didDetermineStateForRegion: Just Switched States -> " + i);
            }
        });
        try {
            beaconManager.startMonitoringBeaconsInRegion(new Region(DEVICE_UUID,null,null,null));
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        beaconManager.addRangeNotifier(new RangeNotifier() {
            String name;
            int rSSi;
            double avG;
            List<Integer> avgList = new ArrayList<>();                                              // A list that stores RSSI values

            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> collection, Region region) {             // Detects beacon(s)
                if (collection.size() == 1) {                                                               // Detected only 1 beacon

                    Log.i(TAG, "Name: " + collection.iterator().next().getBluetoothName());
                    name = collection.iterator().next().getBluetoothName();

                    Log.i(TAG, "RSSI: " + collection.iterator().next().getRssi());
                    rSSi = collection.iterator().next().getRssi();
                    rSSi = rSSi * (-1);                                                             // RSSI values are negative, so we convert them to positive numbers
                    avgList.add(rSSi);                                                              // Add RSSI value to our list
                    Log.i(TAG, "        List Size: " + avgList.size());
                    if (avgList.size() == 4) {                                                      // On list size 4, 4 seconds have passed
                        avG = getAvgRssI(avgList);                                                  // Call the get avg function
                        speech(avG);                                                                // Call speech function
                        avgList.clear();                                                            // Clear the list
                        Log.i(TAG, "List size reset to: " + avgList.size());
                    }
                    Log.i(TAG, "Address: " + collection.iterator().next().getBluetoothAddress());
                }
                else if (collection.size() > 1){                                                             // Detected 2 or more beacons
                    compareBeaconsInRegion(collection,region);                                               // Call the comparision beacon function
                }
                else {
                    Log.i(TAG, "didRangeBeaconsInRegion: Error -> No Beacons Detected");                // No beacons detected
                }
            }

            List <List<Integer>> list = new ArrayList<>();                                          // A list of lists to add Beacons into
            @RequiresApi(api = Build.VERSION_CODES.N)
            public void compareBeaconsInRegion(Collection<Beacon> collection, Region region){                 // Compares beacons
                Iterator<Beacon> iterator = collection.iterator();
                List<Integer> rssiList;                                                             // List for RSSI

                for(int i = 0; i < collection.size(); i++) {                                        // Here we make arraylists for each beacons detected
                    if (iterator.hasNext() && list.size() < collection.size()) {
                        rssiList = new ArrayList<>();                                               // Dynamic initialization of lists
                        Beacon beacon = iterator.next();
                        int rs = beacon.getRssi();
                        rs = rs * (-1);
                        rssiList.add(rs);                                                           // Adding RSSI value of the beacons
                        list.add(rssiList);                                                         // Adding beacon list into our list of lists
                    }
                    else if (iterator.hasNext() && list.size() >= collection.size()){
                        if (list.get(i).size() == 4){
                            break;
                        }
                        Beacon beacon = iterator.next();
                        int rs = beacon.getRssi();
                        rs = rs * (-1);
                        list.get(i).add(rs);                                                        // Adding RSSI values using our list of lists if there are more RSSI values for that beacon
                    }
                    else{
                        Log.i(TAG, "compareBeaconsInRegion: list: " + list);
                        break;
                    }
                }

                // Here we start comparing beacons
                double bestBeacon = 10000;                  // Holds best RSSI value
                int indexOfBestBeacon = -1;                 // Holds closest beacon index
                for(int i = 0; i < list.size(); i++) {      // This loop checks the avg RSSI values of each beacon
                    if (list.get(i).size() == 4){
                        Log.i(TAG, "compareBeaconsInRegion: list size on index " + i + " is 4. list = " + list.get(i));
                        if (bestBeacon > getAvgRssI(list.get(i)) && getAvgRssI(list.get(i)) <= 72.00) {
                            bestBeacon = getAvgRssI(list.get(i));
                            indexOfBestBeacon = i;
                        }
                        else if (bestBeacon > getAvgRssI(list.get(i)) && getAvgRssI(list.get(i)) > 72.00){
                            bestBeacon = getAvgRssI(list.get(i));
                            indexOfBestBeacon = i;
                            Log.i(TAG, "Closest Beacon " + indexOfBestBeacon + ", but out of proximity, range = -" + getAvgRssI(list.get(i)));
                        }
                    }
                    else{
                        Log.i(TAG, "compareBeaconsInRegion: Need more values at list index " + i + " where list = " + list.get(i));
                    }
                }

                if (indexOfBestBeacon > -1){                                // FOUND THE CLOSEST BEACON
                    Iterator<Beacon> it = collection.iterator();            // creating an iterator to get values of the beacon that is on our 'closest index'
                    Beacon best = null;                                     // creating a beacon variable to extract values of the closest beacon
                    for(int i = 0; i <= indexOfBestBeacon; i++){            // For loop to get to the beacon on our 'closest index'
                        best = it.next();
                    }

                    list.clear();                                           // Since we found the closest beacon, we clear our list of lists
                    
                    Log.i(TAG, "Name: " + best.getBluetoothName());
                    Log.i(TAG, "RSSI: " + best.getRssi());
                    rSSi = best.getRssi();
                    rSSi = rSSi * (-1);
                    avgList.add(rSSi);                                      // Use the beacon variable to get its avg RSSI value
                    Log.i(TAG, "        List Size: " + avgList.size());

                    avG = bestBeacon;
                    if (indexOfBestBeacon != closestBeaconHolder) {         // If closest beacon is not the same, we tts which one is the closest and update closest beacon holder.
                        if (avG < 72.00) {
                            compSpeech(indexOfBestBeacon + 1);
                        } else {
                            compSpeech((indexOfBestBeacon + 1) * 1000); // Closest beacon is detected but out of proximity
                        }
                        closestBeaconHolder = indexOfBestBeacon;            // The closest Beacon Holder variable is updated to the newest closest beacon index
                    }
                    else {                                                 // If the closest beacon is the same just say if within or out of proximity.
                        speech(avG);
                    }
                    avgList.clear();                                       // reset list size of our RSSI list
                    Log.i(TAG, "List size reset to: " + avgList.size());

                    Log.i(TAG, "Address: " + best.getBluetoothAddress());
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.N)
            public double getAvgRssI(List<Integer> list){
                double average = list.stream().mapToInt(value -> value).average().orElse(0.0);
                Log.i(TAG, "        getAvgRssI: Average RSSI = -" + average);
                return average;
            }

            public void speech (double check){
                onInit(0);
                if (check >= 72.00 && check < 1000) {   // We get a beacon that is out of proximity
                    tts.speak("Out of proximity", TextToSpeech.QUEUE_FLUSH, null);
                }
                else if (check < 72.00 && check > 15.00){ // We get a beacon that is within proximity
                    tts.speak("Within proximity", TextToSpeech.QUEUE_FLUSH, null);
                }
            }

            public void compSpeech(double closestBeaconIndex){
                onInit(0);
                if (closestBeaconIndex < 15.00){  // We get the nearest beacon that is within proximity
                    tts.speak("Nearest beacon is " + (int)(closestBeaconIndex) + " and within proximity", TextToSpeech.QUEUE_FLUSH, null);
                }
                else if (closestBeaconIndex >= 1000.00){      // We get the nearest beacon that is out of proximity
                    tts.speak("Nearest beacon is " + (int)((closestBeaconIndex/1000)) + " but out of proximity", TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        });
        try {
            beaconManager.startRangingBeaconsInRegion(new Region(DEVICE_UUID, null, null, null));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    // Compass Functions
    @Override
    public void onSensorChanged(SensorEvent event) {
        // get the angle around the z-axis rotated
        float degree = Math.round(event.values[0]);

        tvHeading.setText("Readings: " + Float.toString(degree) + " degrees");

        // create a rotation animation (reverse turn degree degrees)
        RotateAnimation ra = new RotateAnimation(
                currentDegree,
                -degree,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f);

        // how long the animation will take place
        ra.setDuration(210);

        // set the animation after the end of the reservation status
        ra.setFillAfter(true);

        // Start the animation
        image.startAnimation(ra);
        currentDegree = -degree;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Currently not in use
    }
}