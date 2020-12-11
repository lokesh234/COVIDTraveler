package edu.wpi.cs528finalproject

import android.graphics.Bitmap
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils


class MaskClassifier(modelPath: String?) {
    var model: Module = Module.load(modelPath)
    var mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    var std = floatArrayOf(0.229f, 0.224f, 0.225f)
    fun setMeanAndStd(mean: FloatArray, std: FloatArray) {
        this.mean = mean
        this.std = std
    }

    private fun preprocess(bitmap: Bitmap?, size: Int): Tensor {
        var imageBitmap = bitmap
        imageBitmap = Bitmap.createScaledBitmap(imageBitmap!!, size, size, false)
        return TensorImageUtils.bitmapToFloat32Tensor(imageBitmap, mean, std)
    }

    private fun argMax(inputs: FloatArray): Int {
        var maxIndex = -1
        var maxvalue = 0.0f
        for (i in inputs.indices) {
            if (inputs[i] > maxvalue) {
                maxIndex = i
                maxvalue = inputs[i]
            }
        }
        return maxIndex
    }

    fun predict(bitmap: Bitmap?): Int {
        val tensor = preprocess(bitmap, 224)
        val inputs = IValue.from(tensor)
        val outputs = model.forward(inputs).toTensor()
        val scores = outputs.dataAsFloatArray
        return argMax(scores)
    }

}