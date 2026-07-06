package com.ai.guardian.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class MobileFaceNet(context: Context) {
    private var interpreter: Interpreter

    init {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(loadModelFile(context, "mobilefacenet.tflite"), options)
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun getFaceEmbedding(bitmap: Bitmap, faceRect: Rect, rotationDegrees: Int = 0, isFrontCamera: Boolean = true): FloatArray? {
        try {
            val croppedFace = cropFace(bitmap, faceRect)
            val uprightFace = if (rotationDegrees != 0 || isFrontCamera) {
                val matrix = android.graphics.Matrix().apply {
                    if (rotationDegrees != 0) {
                        postRotate(rotationDegrees.toFloat())
                    }
                    if (isFrontCamera) {
                        postScale(-1f, 1f)
                    }
                }
                Bitmap.createBitmap(croppedFace, 0, 0, croppedFace.width, croppedFace.height, matrix, true)
            } else {
                croppedFace
            }
            
            // Image Processing for MobileFaceNet (112x112, Normalized)
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(112, 112, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(127.5f, 127.5f))
                .build()

            var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
            tensorImage.load(uprightFace)
            tensorImage = imageProcessor.process(tensorImage)

            val output = Array(1) { FloatArray(192) }
            interpreter.run(tensorImage.buffer, output)
            
            return normalizeL2(output[0])
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun cropFace(bitmap: Bitmap, rect: Rect): Bitmap {
        var x = rect.left
        var y = rect.top
        var w = rect.width()
        var h = rect.height()

        x = x.coerceAtLeast(0)
        y = y.coerceAtLeast(0)
        w = w.coerceAtMost(bitmap.width - x)
        h = h.coerceAtMost(bitmap.height - y)

        if (w <= 0 || h <= 0) {
            throw IllegalArgumentException("Invalid face bounding box")
        }

        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    private fun normalizeL2(embedding: FloatArray): FloatArray {
        var sum = 0f
        for (value in embedding) {
            sum += value * value
        }
        val norm = sqrt(sum)
        for (i in embedding.indices) {
            embedding[i] = embedding[i] / norm
        }
        return embedding
    }

    fun close() {
        interpreter.close()
    }
}
