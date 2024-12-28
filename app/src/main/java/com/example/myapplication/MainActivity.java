package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import android.Manifest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Base64;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
//import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    public static final int TAKE_PHOTO = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 10;
    private static final int STORAGE_PERMISSION_REQUEST_CODE =5 ;
    private ImageView picture;
    private ImageView recentPicture;
    private TextView textview;
    private Uri imageUri;
    private TFLiteModel tfliteModel;
    private ExecutorService executorService;
    private HttpRequestHelper httpRequestHelper;
    private static final String TAG = "MyActivity";
    private int nums=0;



    // 初始化界面和变量
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button takePhoto = findViewById(R.id.take_photo);
        picture = findViewById(R.id.picture);
        recentPicture = findViewById(R.id.recent_picture);
        textview = findViewById(R.id.text_view);
        tfliteModel = new TFLiteModel(this);
        nums=0;

        // 初始化 ExecutorService，使用单线程池
        executorService = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE);
        }

        // 设置拍照按钮的点击事件
        takePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    // 请求摄像头权限
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                } else {
                    openCamera(); // 权限已经被授予，打开相机
                }
            }
        });
    }

    // 打开相机并拍照
    private void openCamera() {
        // 创建图片文件
        File outputImage = new File(getExternalCacheDir(), "output_image.jpg");
        try {
            if (outputImage.exists()) {
                outputImage.delete();
            }
            outputImage.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 设置图片URI
        if (Build.VERSION.SDK_INT >= 24) {
            imageUri = FileProvider.getUriForFile(MainActivity.this,
                    "com.example.myapplication", outputImage);
        } else {
            imageUri = Uri.fromFile(outputImage);
        }
        // 启动相机
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, TAKE_PHOTO);
    }

    //访问权限
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera(); // 权限被授予，打开相机
            } else {
                // 权限被拒绝，可以给用户提示
                Log.d(TAG,"拒绝打开摄像机");
            }
        }
    }

    //主要功能函数 处理拍照结果
    @SuppressLint("SetTextI18n")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case TAKE_PHOTO:
                if (resultCode == RESULT_OK) {
                    try {
                        // 显示拍出来的照片
                        Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                        picture.setImageBitmap(bitmap);
                        Bitmap[] preBitmap = {null};
                        int[] predictedClass = {1};
                        // 调用上传和预测函数
                        if(nums<=3) {
                            // 上传图片并获取预测结果
                            int[] finalPredictedClass = predictedClass;
                            long startTime = System.currentTimeMillis(); // 获取当前时间戳
                            HttpRequestHelper.uploadImage(bitmap, new HttpRequestHelper.UploadCallback() {
                                @SuppressLint("SetTextI18n")
                                @Override
                                public void onSuccess(String response) throws JSONException {
                                    Log.d(TAG,response);
                                    // 成功处理
                                    try {
                                        JSONObject jsonResponse = new JSONObject(response);
                                        JSONArray results = jsonResponse.getJSONArray("results");
                                        JSONObject data = results.getJSONObject(0);

                                        String imageBase64 = data.getString("image");

                                        finalPredictedClass[0] = data.getInt("predictions");

                                        byte[] decodedString = Base64.decode(imageBase64, Base64.DEFAULT);
                                        preBitmap[0] = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                                        long endTime = System.currentTimeMillis(); // 获取结束时间戳
                                        long elapsedTime = endTime - startTime;  // 计算时间差
                                        if (picture != null) {
                                            picture.setImageBitmap(preBitmap[0]);
                                        }
                                        if (recentPicture != null && preBitmap[0] != null) {
                                            recentPicture.setImageBitmap(preBitmap[0]);
                                            textview.setText("预测类型为: " + finalPredictedClass[0] + '\n' +
                                                    "执行时间: " + elapsedTime + " 毫秒");
                                        }
                                        if (elapsedTime >= 2000) {
                                            nums++;
                                        }
                                    }catch (JSONException e) {
                                        Log.d(TAG,e.getMessage());

                                        // 调整大小并转换为模型输入格式
                                        long startTime = System.currentTimeMillis(); // 获取当前时间戳
                                        float[][][][] inputArray = preprocessImage(bitmap);
                                        finalPredictedClass[0]= tfliteModel.predict(inputArray);
                                        long endTime = System.currentTimeMillis(); // 获取结束时间戳
                                        long elapsedTime = endTime - startTime;  // 计算时间差
                                        recentPicture.setImageBitmap(bitmap);
                                        textview.setText("预测类型为: " + finalPredictedClass[0]+'\n'+
                                                "执行时间: " + elapsedTime + " 毫秒");
                                    }

                                }
                                @Override
                                public void onFailure(String error) {
                                    // 失败打印日志
                                    Log.d(TAG, "Upload failed: " + error);

                                }
                            });
                        }
                        else{
                            // 使用本地模型进行预测
                            // 调整大小并转换为模型输入格式
                            long startTime = System.currentTimeMillis(); // 获取当前时间戳
                            float[][][][] inputArray = preprocessImage(bitmap);
                            predictedClass= new int[]{tfliteModel.predict(inputArray)};
                            long endTime = System.currentTimeMillis(); // 获取结束时间戳
                            long elapsedTime = endTime - startTime;  // 计算时间差
                            recentPicture.setImageBitmap(bitmap);
                            textview.setText("预测类型为: " + predictedClass[0]+'\n'+
                                    "执行时间: " + elapsedTime + " 毫秒");
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;
            default:
                break;
        }
    }

    //调整Bitmap大小
    private Bitmap resizeBitmap(Bitmap bitmap, int width, int height) {
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }




    //cnn模型:图片预处理，转换为模型输入格式
//    private float[][][][] bitmapToInputArray(Bitmap bitmap) {
//        int width = bitmap.getWidth();
//        int height = bitmap.getHeight();
//        float[][][][] inputArray = new float[1][64][64][3]; // [1, 64, 64, 3]
//
//        for (int x = 0; x < 64; x++) {
//            for (int y = 0; y < 64; y++) {
//                int pixel = bitmap.getPixel(x, y);
//                // 归一化到0-1
//                inputArray[0][x][y][0] = Color.red(pixel) / 255.0f;   // R
//                inputArray[0][x][y][1] = Color.green(pixel) / 255.0f; // G
//                inputArray[0][x][y][2] = Color.blue(pixel) / 255.0f;  // B
//            }
//        }
//        return inputArray;
//    }

    //yolov5:图片预处理，转换为模型输入格式
    private float[][][][] preprocessImage(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 160, 160, true);
        int width = resizedBitmap.getWidth();
        int height = resizedBitmap.getHeight();
        float[][][][] input =  new float[1][160][160][3];
        for (int i = 0; i < 160; i++) {
            for (int j = 0; j < 160; j++) {
                int pixel = resizedBitmap.getPixel(j, i);
                input[0][i][j][0] = Color.red(pixel) / 255.0f;   // R
                input[0][i][j][1] = Color.green(pixel) / 255.0f; // G
                input[0][i][j][2] = Color.blue(pixel) / 255.0f;  // B
            }
        }
        return input;
    }

    // onDestroy 方法：关闭线程池
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在 Activity 销毁时，关闭 ExecutorService，避免内存泄漏
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}