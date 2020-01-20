package tk.burdukowsky.caller;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CallLog;
import android.support.test.espresso.core.deps.guava.base.Splitter;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.ITelephony;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;

/**
 * Created by IDEA
 * User: Stanislav
 * Date: 009 09.02.17
 */

class SimpleWebServer implements Runnable {

    /**
     * The port number we listen to
     */
    private final int mPort;
    private String mIp;
    private Context mContext;

    private LocalBroadcastManager localBroadcastManager
            = LocalBroadcastManager.getInstance(mContext);
    static final String REQUEST = "tk.burdukowsky.caller.SimpleService.REQUEST_PROCESSED";
    static final String MESSAGE = "tk.burdukowsky.caller.SimpleService.MESSAGE";

    /**
     * True if the server is running.
     */
    private boolean mIsRunning;

    /**
     * The {@link java.net.ServerSocket} that we listen to.
     */
    private ServerSocket mServerSocket;

    /**
     * WebServer constructor.
     */
    SimpleWebServer(int port, Context context) {
        mPort = port;
        mContext = context;
        try {
            mIp = getLocalIpAddress();
        } catch (SocketException e) {
            e.printStackTrace();
            echo(mContext.getString(R.string.server_start_exception, e));
            mContext.stopService(new Intent(mContext, SimpleService.class));
        }
    }

    /**
     * This method starts the web server listening to the specified port.
     */
    void start() {
        mIsRunning = true;
        new Thread(this).start();
    }

    /**
     * This method stops the web server
     */
    void stop() {
        try {
            mIsRunning = false;
            if (null != mServerSocket) {
                mServerSocket.close();
                mServerSocket = null;
                echo(mContext.getString(R.string.stop_server_info));
            }
        } catch (IOException e) {
            echo(mContext.getString(R.string.server_stop_exception, e));
            mContext.stopService(new Intent(mContext, SimpleService.class));
        }
    }

    @Override
    public void run() {
        try {
            mServerSocket = new ServerSocket(mPort);
            echo(mContext.getString(R.string.start_server_info, mIp, mPort));
            while (mIsRunning) {
                Socket socket = mServerSocket.accept();
                handle(socket);
                socket.close();
            }
        } catch (SocketException e) {
            // The server was stopped; ignore.
        } catch (IOException e) {
            echo(mContext.getString(R.string.server_start_exception, e));
            mContext.stopService(new Intent(mContext, SimpleService.class));
        }
    }

    /**
     * Respond to a request from a client.
     *
     * @param socket The client socket.
     * @throws IOException
     */
    private void handle(Socket socket) throws IOException {
        BufferedReader reader = null;
        PrintWriter output = null;
        try {
            String route = null;

            // Read HTTP headers and parse out the route.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while (!TextUtils.isEmpty(line = reader.readLine())) {
                if (line.startsWith("GET /")) {
                    int start = line.indexOf('/') + 1;
                    int end = line.indexOf(' ', start);
                    route = line.substring(start, end);
                    break;
                }
            }

            // Output stream that we send the response to
            // output = new PrintStream(socket.getOutputStream());
            output = new PrintWriter(socket.getOutputStream(), true);

            // Prepare the content to send.
            if (null == route) {
                writeServerError(output);
                return;
            }

            String action;
            String number;
            String numberTel;
            String response;

            if (!(route.equals("favicon.ico") || route.isEmpty())) {
                String[] routeArray = route.split("\\?");
                if (routeArray.length > 1) {
                    action = routeArray[0];
                    try {
                        Map<String, String> getParameters
                                = Splitter.on("&").withKeyValueSeparator("=").split(routeArray[1]);
                        if (getParameters.containsKey("number")) {
                            number = getParameters.get("number");
                            numberTel = String.format("tel:%s", number);
                            switch (action) {
                                case "call":
                                    mContext.startActivity(
                                            new Intent(Intent.ACTION_CALL, Uri.parse(numberTel))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                    response = mContext.getString(R.string.json_response,
                                            "true",
                                            number);
                                    echo(mContext.getString(R.string.calling, number));
                                    break;
                                case "end":
                                    endOfCall();
                                    echo(mContext.getString(R.string.putting_down));
                                    String duration = (MainActivity.sIsOldAndroidVersion) ? "old" : getLastCallDuration(number);
                                    //String duration = getLastCallDuration(number);
                                    response = mContext.getString(R.string.json_response,
                                            "true",
                                            duration);
                                    break;
                                default:
                                    response = mContext.getString(R.string.json_response,
                                            "false",
                                            "wrong route");
                                    break;
                            }
                        } else {
                            response = mContext.getString(R.string.json_response,
                                    "false",
                                    "parameter 'number' required");
                        }
                    } catch (IllegalArgumentException e) {
                        response = mContext.getString(R.string.json_response,
                                "false",
                                "wrong get data: " + e.getMessage());
                    }
                } else {
                    response = mContext.getString(R.string.json_response, "false", "wrong route");
                }
            } else {
                response = mContext.getString(R.string.json_response, "false", "wrong route");
            }

            // Send out the content.
            output.println("HTTP/1.0 200 OK");
            output.println("Content-Type: application/json");
            output.println("Content-Length: " + response.getBytes("UTF-8").length);
            output.println("Access-Control-Allow-Origin: *");
            output.println();
            output.write(response);
            output.flush();
        } finally {
            if (null != output) {
                output.close();
            }
            try {
                if (null != reader) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getLastCallDuration(String phoneNumber) {
        String duration = "0";
        Uri contacts = CallLog.Calls.CONTENT_URI;
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            stop();
            mContext.stopService(new Intent(mContext, SimpleService.class));
            echo(mContext.getString(R.string.server_stopped_without_permission));
        }
        Cursor callLogCursor = mContext.getContentResolver().query(contacts,
                null,
                CallLog.Calls.NUMBER + " = ?",
                new String[]{phoneNumber},
                CallLog.Calls.DATE + " DESC");
        if (callLogCursor == null) {
            return duration;
        }
        int duration1 = callLogCursor.getColumnIndex(CallLog.Calls.DURATION);
        if (callLogCursor.moveToFirst()) {
            duration = callLogCursor.getString(duration1);
        }
        callLogCursor.close();
        return duration;
    }

    private void endOfCall() {
        TelephonyManager telephonyManager
                = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        Class clazz;
        try {
            clazz = Class.forName(telephonyManager.getClass().getName());
            @SuppressWarnings("unchecked")
            Method method = clazz.getDeclaredMethod("getITelephony");
            method.setAccessible(true);
            ITelephony telephonyService = (ITelephony) method.invoke(telephonyManager);
            telephonyService.endCall();
        } catch (ClassNotFoundException | NoSuchMethodException | RemoteException
                | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes a server error response (HTTP/1.0 500) to the given output stream.
     *
     * @param output The output stream.
     */
    private void writeServerError(PrintWriter output) {
        output.println("HTTP/1.0 500 Internal Server Error");
        output.flush();
    }

    private static String getLocalIpAddress() throws SocketException {
        String resultIpv6 = "";
        String resultIpv4 = "";

        for (Enumeration en = NetworkInterface.getNetworkInterfaces();
             en.hasMoreElements(); ) {

            NetworkInterface networkInterface = (NetworkInterface) en.nextElement();
            for (Enumeration enumIpAddress = networkInterface.getInetAddresses();
                 enumIpAddress.hasMoreElements(); ) {

                InetAddress inetAddress = (InetAddress) enumIpAddress.nextElement();
                if (!inetAddress.isLoopbackAddress()) {
                    if (inetAddress instanceof Inet4Address) {
                        resultIpv4 = inetAddress.getHostAddress();
                    } else if (inetAddress instanceof Inet6Address) {
                        resultIpv6 = inetAddress.getHostAddress();
                    }
                }
            }
        }
        return ((resultIpv4.length() > 0) ? resultIpv4 : resultIpv6);
    }

    private void echo(String message) {
        Intent intent = new Intent(REQUEST);
        if (!message.isEmpty()) {
            intent.putExtra(MESSAGE, message);
        }
        localBroadcastManager.sendBroadcast(intent);
    }

    String getIp() {
        return mIp;
    }
}
