package com.astifter.drinklog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

public class MainActivity extends ActionBarActivity {
    private static String FILENAME = "ExampleDataLoggingActivityStorage.csv";
    private static final UUID OCEAN_SURVEY_APP_UUID = UUID.fromString("403D89B3-E4A6-4667-A988-632F5282979E");
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final StringBuilder mDisplayText = new StringBuilder();
    private PebbleKit.PebbleDataLogReceiver mDataLogReceiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DATE_FORMAT.setTimeZone(TimeZone.getDefault());
    }

    private void readBufferFromFile() {
        String eol = System.getProperty("line.separator");
        BufferedReader input = null;
        try {
            mDisplayText.setLength(0);
            input = new BufferedReader(new InputStreamReader(openFileInput(FILENAME)));
            String line = input.readLine();
            while (line != null) {
                mDisplayText.append(line);
                line = input.readLine();
                if (line != null)
                    mDisplayText.append(eol);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void writeBufferToFile() {
        try {
            FileOutputStream openFileOutput = openFileOutput(FILENAME, Context.MODE_WORLD_READABLE);
            openFileOutput.write(mDisplayText.toString().getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mDataLogReceiver != null) {
            unregisterReceiver(mDataLogReceiver);
            mDataLogReceiver = null;
        }
        writeBufferToFile();
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    @Override
    protected void onResume() {
        super.onResume();

        readBufferFromFile();
        updateUi();

        // To receive data logs, Android applications must register a "DataLogReceiver" to receive data.
        //
        // In this example, we're implementing a handler to receive unsigned integer data that was logged by a
        // corresponding watch-app. In the watch-app, three separate logs were created, one per animal. Each log was
        // tagged with a key indicating the animal to which the data corresponds. So, the tag will be used here to
        // look up the animal name when data is received.
        //
        // The data being received contains the seconds since the epoch (a timestamp) of when an ocean faring animal
        // was sighted. The "timestamp" indicates when the log was first created, and will not be used in this example.
        final Handler handler = new Handler();
        mDataLogReceiver = new PebbleKit.PebbleDataLogReceiver(OCEAN_SURVEY_APP_UUID) {
            @Override
            public void receiveData(Context context, UUID logUuid, Long logstarted, Long tag,
                                    byte[] data_array) {
                ByteBuffer buffer = ByteBuffer.wrap(data_array);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                int id = buffer.getInt();
                int timestamp = buffer.getInt();
                int data1 = buffer.getInt();
                int data2 = buffer.getInt();
                int data3 = buffer.getInt();
                int timestamp_ms = buffer.getShort();

                if (mDisplayText.length() > 0) {
                    String eol = System.getProperty("line.separator");
                    mDisplayText.append(eol);
                }
                //mDisplayText.append(byteArrayToHex(data_array));
                //mDisplayText.append("|");
                mDisplayText.append(getLongAsTimestamp(timestamp, timestamp_ms));
                mDisplayText.append(",");
                mDisplayText.append(id);
                mDisplayText.append(",");
                mDisplayText.append(data1);
                mDisplayText.append(",");
                if (100 <= id && id <= 102) {
                    if (data2 < 0)
                        mDisplayText.append(data2);
                    else
                        mDisplayText.append("-");
                } else {
                    mDisplayText.append(data2);
                }
                mDisplayText.append(",");
                mDisplayText.append(data3);
                mDisplayText.append("|");
                switch(id) {
                    case 1: mDisplayText.append("logged " + data1 + " glass(es)"); break;
                    case 2: mDisplayText.append("bookkeeping done (glasses reset)"); break;
                    case 3: mDisplayText.append("corrected for " + data1 + " glass(es)"); break;
                    case 4: {
                        mDisplayText.append("woken up: ");
                        switch(data1) {
                            case 0: mDisplayText.append("startup"); break;
                            case 1: mDisplayText.append("firstday"); break;
                            case 2: mDisplayText.append("timer"); break;
                            case 3: mDisplayText.append("snoozed"); break;
                            case 4: mDisplayText.append("bookkeeping"); break;
                        }
                    } break;
                    case 5: mDisplayText.append("vibrating"); break;
                    case 6: mDisplayText.append("autodismissed"); break;

                    case 100: mDisplayText.append("bookkeeping:"); break;
                    case 101: mDisplayText.append("timer:"); break;
                    case 102: mDisplayText.append("snooze:"); break;
                }
                if (100 <= id && id <= 102) {
                    switch(data1) {
                        case 0:         // scheduled
                        case 3: {       // rescheduled
                            if (data2 < 0) {
                                mDisplayText.append(" error " + data2);
                            } else {
                                if (data1 == 0)
                                    mDisplayText.append(" for " + getLongAsTimestamp(data3));
                                else
                                    mDisplayText.append(" resched " + getLongAsTimestamp(data3));
                            }
                        } break;
                        case 1:         // canceled
                            mDisplayText.append(" canceled"); break;
                        case 2:         // tolate
                            mDisplayText.append(" to late"); break;
                    }
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateUi();
                    }
                });
            }
        };

        PebbleKit.registerDataLogReceiver(this, mDataLogReceiver);

        PebbleKit.requestDataLogsForApp(this, OCEAN_SURVEY_APP_UUID);
    }

    private void updateUi() {
        TextView textView = (TextView) findViewById(R.id.main_activity_fragment_text_view);
        textView.setText(mDisplayText.toString());
    }

    private String getLongAsTimestamp(int l) {
        return getLongAsTimestamp(l, -1);
    }
    private String getLongAsTimestamp(int l, int ms) {
        String retval = DATE_FORMAT.format(new Date(l * 1000L)).toString();
        if (ms >= 0) {
            retval +=  String.format(".%04d", ms);
        }
        return retval;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }
}
