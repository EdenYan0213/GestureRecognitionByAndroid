package com.example.myapplication;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

//用于加载和运行 TensorFlow Lite 模型
public class TFLiteModel {

    private Interpreter tflite; // TensorFlow Lite 解释器实例

    // 构造函数，接收一个 Context 对象，并加载模型
    public TFLiteModel(Context context) {
        // 加载模型
        try {
            tflite = new Interpreter(loadModelFile(context, "best-fp16.tflite"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 从 assets 文件夹中加载模型文件
    private MappedByteBuffer loadModelFile(Context context, String modelFilename) throws IOException, IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFilename); // 打开模型文件
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);  // 将模型文件映射为只读字节缓冲区
    }

    //cnn预测
//    public int predict(float[][][][] inputData) {
//        float[][] outputData = new float[1][10]; // 假设输出为11个类别
//        tflite.run(inputData, outputData);
//        int predictedClass = getHighestConfidenceClass(outputData);
//        return predictedClass; // 返回预测结果
//    }


    //使用yolov5预测
    public int predict(float[][][][] inputData) {
        float[][][] outputData = new float[1][1575][15]; // 假设输出数据的形状
        tflite.run(inputData, outputData);// 运行模型
        int predictedClass = getHighestConfidenceClass(outputData);// 获取最高置信度的类别
        System.out.println(outputData);// 打印输出数据（调试用）
        return predictedClass; // 返回预测结果
    }

    // 获取最大值的索引（针对cnn模型的）
    public int getMaxIndex(float[] output) {
        int maxIndex = 0;
        float maxValue = output[0];
        for (int i = 1; i < output.length; i++) {
            if (output[i] > maxValue) {
                maxValue = output[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    // 从预测结果中获取最高置信度的类别索引
    public int getHighestConfidenceClass(float[][][] predictions) {
        float[][] pre=predictions[0];

        int highestClassId = 0;

        for (float[] pred : pre) {
            // 提取类别分数，从索引 5 开始（前五个是位置信息）
            float[] classScores = new float[pred.length - 5];
            System.arraycopy(pred, 5, classScores, 0, classScores.length);

            // 找到置信度最高的类别及其得分
            float highestClassScore = classScores[0];

            for (int i = 1; i < classScores.length; i++) {
                if (classScores[i] > highestClassScore) {
                    highestClassScore = classScores[i];
                    highestClassId = i;
                }
            }
        }
        return highestClassId; // 返回置信度最高的类别索引
    }

    // 关闭 TensorFlow Lite 解释器，释放资源
    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}

