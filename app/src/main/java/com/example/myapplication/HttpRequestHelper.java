package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

//用于处理图像上传和服务器响应
public class HttpRequestHelper {

    // 定义回调接口，用于处理上传成功和失败的情况
    public interface UploadCallback {
        void onSuccess(String response) throws JSONException;
        void onFailure(String error);
    }

    // 常量，用于构建multipart/form-data请求体
    private static final String BOUNDARY = "*****";
    private static final String TWO_HYPHENS = "--";
    private static final String LINE_END = "\r\n";

    // 上传图像到服务器
    public static void uploadImage(final Bitmap bitmap, final UploadCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                DataOutputStream outputStream = null;
                BufferedReader reader = null;
                String response;

                try {
                    // 1. 将Bitmap转换为字节数组
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                    byte[] imageBytes = byteArrayOutputStream.toByteArray();

                    // 2. 打开HTTP连接
                    URL url = new URL("http://222.201.134.240:5000/predict");
                    //设置请求
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

                    // 3. 写入请求体
                    outputStream = new DataOutputStream(connection.getOutputStream());
                    outputStream.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"files\"; filename=\"image.jpg\"" + LINE_END);
                    outputStream.writeBytes("Content-Type: image/jpg" + LINE_END);
                    outputStream.writeBytes(LINE_END);
                    outputStream.write(imageBytes);
                    outputStream.writeBytes(LINE_END);
                    outputStream.writeBytes(TWO_HYPHENS + BOUNDARY + TWO_HYPHENS + LINE_END);
                    outputStream.flush();

                    // 4. 获取服务器响应
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder responseBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBuilder.append(line);
                        }
                        response = responseBuilder.toString();
                    } else {
                        response = "Error: " + responseCode;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    response = "Exception: " + e.getMessage();
                } finally {
                    // 关闭资源
                    try {
                        if (reader != null) reader.close();
                        if (outputStream != null) outputStream.close();
                        if (connection != null) connection.disconnect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // 5. 在主线程上通知回调
                final String finalResponse = response;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (finalResponse != null) {
                            try {
                                callback.onSuccess(finalResponse);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            callback.onFailure("No response received");
                        }
                    }
                });
            }
        }).start();
    }
}
