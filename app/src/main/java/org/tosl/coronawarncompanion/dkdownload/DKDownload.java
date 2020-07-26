package org.tosl.coronawarncompanion.dkdownload;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.Objects;

public class DKDownload {
    private static final String TAG = "DKDownload";

    private static final String CWA_URL = "https://svc90.main.px.t-online.de/version/v1/diagnosis-keys/country/DE/date";

    private static String cachePathStr;

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    private final Context context;

    private final RequestQueue queue;
    private final Response.ErrorListener errorResponseListener;

    public DKDownload(Context appContext) {
        context = appContext;
        cachePathStr = Objects.requireNonNull(context.getExternalCacheDir()).getPath();

        // Instantiate the Volley RequestQueue.
        queue = Volley.newRequestQueue(context);

        errorResponseListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //TODO
                Log.e(TAG, "VolleyError "+error);
            }
        };
    }

    public static class FileResponse {
        public URL url;
        public byte[] fileBytes;
    }

    void startHttpRequestForStringResponse(String urlStr, Listener<String> responseListener,
                                           Response.ErrorListener errorResponseListener) {
        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET,
                urlStr, responseListener, errorResponseListener);
        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    public interface CallbackCommand {
        void execute(Object data);
    }

    public static void doCallback(CallbackCommand callbackCommand, Object data) {
        callbackCommand.execute(data);
    }

    String[] parseCwaListResponse(String str) {
        String reducedStr = str.replace("\"","");
        reducedStr = reducedStr.replace("[","");
        reducedStr = reducedStr.replace("]","");
        return reducedStr.split(",");
    }

    public void availableDatesRequest(CallbackCommand callbackCommand) {

        Listener<String> responseListener = new Listener<String>() {
            @Override
            public void onResponse(String availableDatesStr) {
                //Log.d(TAG, "Available Dates: "+availableDatesStr);
                String[] dateStringArray = parseCwaListResponse(availableDatesStr);
                LinkedList<Date> result = new LinkedList<>();
                for (String str : dateStringArray) {
                    //Log.d(TAG, "Date: "+str);
                    Date date = null;
                    try {
                        date = dateFormatter.parse(str);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    //Log.d(TAG, "Date: "+date);
                    result.add(date);
                }
                doCallback(callbackCommand, result);
            }
        };
        startHttpRequestForStringResponse(CWA_URL+"", responseListener, errorResponseListener);
    }

    public void availableHoursForDateRequest(Date date, CallbackCommand callbackCommand) {

        Listener<String> responseListener = new Listener<String>() {
            @Override
            public void onResponse(String availableHoursStr) {
                //Log.d(TAG, "Available Hours: "+availableHoursStr);
                String[] hourStringArray = parseCwaListResponse(availableHoursStr);
                LinkedList<String> result = new LinkedList<>();
                Collections.addAll(result, hourStringArray);
                doCallback(callbackCommand, result);
            }
        };
        startHttpRequestForStringResponse(CWA_URL+"/"+getStringFromDate(date)+"/"+"hour", responseListener, errorResponseListener);
    }

    void startHttpRequestForByteArrayResponse(String urlStr, Listener<byte[]> responseListener,
                                              Response.ErrorListener errorResponseListener) {
        // Request a byte[] response from the provided URL.
        ByteArrayRequest byteArrayRequest = new ByteArrayRequest(Request.Method.GET,
                urlStr, responseListener, errorResponseListener);
        // Add the request to the RequestQueue.
        queue.add(byteArrayRequest);
    }

    public void dkFileRequest(URL url, CallbackCommand callbackCommand) {
        FileResponse fileResponse = new FileResponse();
        fileResponse.url = url;

        Listener<byte[]> responseListener = new Listener<byte[]>() {
            @Override
            public void onResponse(byte[] fileBytes) {
                //Log.d(TAG, "File received, Length: "+fileBytes.length);
                fileResponse.fileBytes = fileBytes;
                doCallback(callbackCommand, fileResponse);
            }
        };
        startHttpRequestForByteArrayResponse(url.toString(), responseListener, errorResponseListener);
    }

    private String getStringFromDate(Date date) {
        StringBuffer stringBuffer = new StringBuffer();
        return dateFormatter.format(date, stringBuffer, new FieldPosition(0)).toString();
    }

    public URL getDailyDKsURLForDate(Date date) {
        URL result = null;
        try {
            result = new URL(CWA_URL+"/"+getStringFromDate(date));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        //Log.d(TAG, "getDailyDKsURLForDate: URL: "+result);
        return result;
    }

    public URL getHourlyDKsURLForDateAndHour(Date date, String hour) {
        URL result = null;
        try {
            result = new URL(CWA_URL+"/"+getStringFromDate(date)+"/hour/"+hour);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        //Log.d(TAG, "getHourlyDKsURLForDateAndHour: URL: "+result);
        return result;
    }
}