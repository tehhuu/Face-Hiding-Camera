package jp.ac.titech.tehhuu.face_hiding_camera

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark
import java.io.*
import java.io.File.separator


class CapturedView : AppCompatActivity() {
    private var photoImage: Bitmap? = null
    //var photoImage: Bitmap? = null
    private var cameraUri: Uri? = null
    private lateinit var path: String
    var image : Bitmap? = null
    var width_view : Int = 0
    var height_view : Int = 0
    var range = "Face"
    var type = "Image"
    lateinit var saveButton : Button
    lateinit var button_face_or_mouth : ToggleButton
    lateinit var button_image_or_mosaic : ToggleButton
    lateinit var progressBar : ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.captured_view)

        cameraUri = intent.data
        width_view = intent.getIntExtra("width_view", 0)
        height_view = intent.getIntExtra("height_view", 0)

        progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        showPhoto("Face", "Image")

        val backButton = findViewById<Button>(R.id.rephoto_button)
        backButton.setOnClickListener {
            val intent = Intent(this, FrontPage::class.java)
            startActivity(intent)
        }

        saveButton = findViewById<Button>(R.id.save_button)
        saveButton.setOnClickListener {
            image?.let {
               /*//val dataDir: File
                var f = createFile()
                val ops = FileOutputStream(f)
                image!!.compress(Bitmap.CompressFormat.PNG, 100, ops)
                ops.close()
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put("_data", f.absolutePath)
                }
                contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)*/
                saveImage(image!!, this, "Camera")
                Toast.makeText(this, "Saved!!", Toast.LENGTH_LONG).show()
            }
        }

        button_face_or_mouth = findViewById<ToggleButton>(R.id.switch1)
        button_face_or_mouth.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                range = "Mouth"
                drawAgain(range, type)
            }
            else{
                range = "Face"
                drawAgain(range, type)
            }
        }

        button_image_or_mosaic = findViewById<ToggleButton>(R.id.switch2)
        button_image_or_mosaic.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                type = "Mosaic"
                drawAgain(range, type)
            }
            else{
                type = "Image"
                drawAgain(range, type)
            }
        }
    }

    private fun createFile(): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DCIM)

        return File(dir, "pid.jpeg")
    }

    private fun saveImage(bitmap: Bitmap, context: Context, folderName: String) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = contentValues()
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + folderName)
            values.put(MediaStore.Images.Media.IS_PENDING, true)
            // RELATIVE_PATH and IS_PENDING are introduced in API 29.

            val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                saveImageToStream(bitmap, context.contentResolver.openOutputStream(uri))
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {
            val directory = File(getExternalFilesDir(Environment.DIRECTORY_DCIM).toString() + separator + folderName)
            // getExternalStorageDirectory is deprecated in API 29

            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = System.currentTimeMillis().toString() + ".png"
            val file = File(directory, fileName)
            saveImageToStream(bitmap, FileOutputStream(file))
            if (file.absolutePath != null) {
                val values = contentValues()
                values.put(MediaStore.Images.Media.DATA, file.absolutePath)
                // .DATA is deprecated in API 29
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
        }
    }

    private fun contentValues() : ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        return values
    }

    private fun saveImageToStream(bitmap: Bitmap, outputStream: OutputStream?) {
        if (outputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun drawAgain(range : String, type : String){
        saveButton.setEnabled(false)
        button_face_or_mouth.setEnabled(false)
        button_image_or_mosaic.setEnabled(false)
        progressBar.visibility = View.VISIBLE
        showPhoto(range, type)
        saveButton.setEnabled(true)
        button_face_or_mouth.setEnabled(true)
        button_image_or_mosaic.setEnabled(true)
    }

    private fun showPhoto(range : String, type : String){
        if (cameraUri != null) {
            lateinit var bitmap_before : Bitmap
            val stream: InputStream? = this.getContentResolver().openInputStream(cameraUri!!)
            bitmap_before = BitmapFactory.decodeStream(BufferedInputStream(stream))

            var bitmap : Bitmap = Bitmap.createScaledBitmap(bitmap_before
                    , width_view
                    , (bitmap_before.getHeight() * (width_view.toFloat() / bitmap_before.getWidth())).toInt()
                    ,true)

            val highAccuracyOpts = FirebaseVisionFaceDetectorOptions.Builder()
                    .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                    .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                    .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                    .build()

            val image = FirebaseVisionImage.fromBitmap(bitmap)

            val detector = FirebaseVision.getInstance()
                    .getVisionFaceDetector(highAccuracyOpts)

            detector.detectInImage(image)
                        .addOnSuccessListener { faces ->
                            // Task completed successfully
                            // [START_EXCLUDE]
                            // [START get_face_info]
                            var mouthPos_list = arrayOf<Array<Int>>()
                            var facePos_list = arrayOf<Array<Int>>()
                            var smileProb_list = floatArrayOf()
                            var eyeOpenProb_list = arrayOf<Array<Float>>()

                            for (face in faces) {
                                val bounds = face.boundingBox
                                //val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
                                //val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees
                                facePos_list += arrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom)

                                // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
                                // nose available):
                                val flag_mouth_bottom = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM)
                                val flag_mouth_left = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT)
                                val flag_mouth_right = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT)
                                val flag_nose = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE)
                                ifLet(flag_mouth_bottom, flag_mouth_left, flag_mouth_right, flag_nose) {
                                    (bottom, left, right, nose) ->
                                    var mouth_bottom = bottom.position.getY().toInt()
                                    var mouth_left = left.position.getX().toInt()
                                    var mouth_right = right.position.getX().toInt()
                                    //var mouth_top = min(left.position.getY(), right.position.getY()).toInt()
                                    var top = nose.position.getY().toInt()
                                    mouthPos_list += arrayOf(mouth_left, top, mouth_right, mouth_bottom)
                                }

                                // If classification was enabled:
                                if (face.smilingProbability != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                                    Log.d("debug", "smile")
                                    val smileProb = face.smilingProbability
                                    smileProb_list += smileProb
                                }

                                if (face.leftEyeOpenProbability != FirebaseVisionFace.UNCOMPUTED_PROBABILITY
                                        && face.rightEyeOpenProbability != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                                    val leftEyeOpenProb = face.leftEyeOpenProbability
                                    val rightEyeOpenProb = face.rightEyeOpenProbability
                                    eyeOpenProb_list += arrayOf(leftEyeOpenProb, rightEyeOpenProb)
                                }

                            }
                            if (range == "Face"){
                                if (type == "Image") {
                                    drawingWithMosaic(bitmap, facePos_list, smileProb_list, eyeOpenProb_list)
                                }
                                else if (type == "Mosaic") {
                                    drawingWithMask(bitmap, facePos_list)
                                }
                            }
                            else if (range == "Mouth"){
                                if (type == "Image") {
                                    drawingWithMosaic(bitmap, mouthPos_list, smileProb_list, eyeOpenProb_list)
                                }
                                else if(type == "Mosaic"){
                                    drawingWithMask(bitmap, mouthPos_list)
                                }
                            }
                            // [END get_face_info]
                            // [END_EXCLUDE]
                        }
                        .addOnFailureListener { e ->
                            // Task failed with an exception
                            // ...
                        }
        }
        Log.d("debug", "startActivityForResult()")
    }

    /*fun drawWithRect(context: Context, _image : Bitmap, _le_x : Float, _le_y : Float, _re_x : Float, _re_y : Float) {
        var paint : Paint = Paint()
        //val image : Bitmap = _image
        image = _image.copy(_image.getConfig(), true)
        var le_x = _le_x
        var le_y = _le_y
        var re_x = _re_x
        var re_y = _re_y
        image?.let {
            var canvas: Canvas = Canvas(image!!)
            //fun drawing(){
            val lineStrokeWidth = 5f
            // ペイントストロークの太さを設定
            paint.strokeWidth = lineStrokeWidth
            // Styleのストロークを設定する
            paint.style = Paint.Style.FILL

            val ratio = 1
            paint.color = Color.argb(192, 255, 0, 255)
            canvas.drawRect(le_x / ratio, le_y / ratio, re_x / ratio, re_y / ratio, paint)
            val photoView = findViewById<ImageView>(R.id.photo_view2)
            progressBar.visibility = View.GONE
            photoView.setImageBitmap(image)
        }
    }*/

    fun drawingWithMask(_image : Bitmap, pos_list : Array<Array<Int>>) {
        image = _image.copy(_image.getConfig(), true)

        val w = image!!.getWidth()
        val h = image!!.getHeight()
        var pixels =  IntArray(w * h)
        image!!.getPixels(pixels, 0, w, 0, 0, w, h)

        val kernel_size = 31
        val ks_half : Int = kernel_size / 2
        val kernel_sum = Math.pow(kernel_size.toDouble(), 2.0).toInt()

        for (pos in pos_list){
            val left = pos[0]
            val top = pos[1]
            val right = pos[2]
            val bottom = pos[3]
            for (x in left..right){
                for (y in top..bottom){
                    var average = intArrayOf(0, 0, 0, 0)
                    for (dx in (-ks_half)..ks_half) {
                        for (dy in (-ks_half)..ks_half) {
                            val pixel = pixels[(x + dx) + (y + dy) * w]
                            average[0] += Color.alpha(pixel)
                            average[1] += Color.red(pixel)
                            average[2] += Color.green(pixel)
                            average[3] += Color.blue(pixel)
                        }
                    }
                    pixels[x + y * w] = Color.argb(
                            (average[0].toFloat() / kernel_sum).toInt(),
                            (average[1].toFloat() / kernel_sum).toInt(),
                            (average[2].toFloat() / kernel_sum).toInt(),
                            (average[3].toFloat() / kernel_sum).toInt())
                }
            }
        }
        image!!.setPixels(pixels, 0, w, 0, 0, w, h)

        image?.let {

            val photoView = findViewById<ImageView>(R.id.photo_view2)

            progressBar.visibility = View.GONE

            photoView.setImageBitmap(image)
        }
    }

    fun drawingWithMosaic(_image : Bitmap, facePos_list : Array<Array<Int>>, smileProb_list : FloatArray, eyeOpenProb_list : Array<Array<Float>>){
        var paint : Paint = Paint()
        image = _image.copy(_image.getConfig(), true)
        image?.let{
            var canvas: Canvas = Canvas(image!!)
            val mask_normal = BitmapFactory.decodeResource(getResources(), R.drawable.funassi_normal)
            val mask_smile = BitmapFactory.decodeResource(getResources(), R.drawable.funassi_smile)
            val mask_eyeclosed = BitmapFactory.decodeResource(getResources(), R.drawable.funassi_eyeclosed)
            val num_faces = smileProb_list.size

            if (num_faces != 0){
                for (i in 0..(num_faces-1)) {
                    val left = facePos_list[i][0]
                    val top = facePos_list[i][1]
                    val right = facePos_list[i][2]
                    val bottom = facePos_list[i][3]

                    val dest = Rect(left, top, right, bottom)
                    if (smileProb_list[i] >= 0.7) {
                        val src = Rect(0, 0, mask_smile.getWidth(), mask_smile.getHeight())
                        canvas.drawBitmap(mask_smile, src, dest, paint)
                    } else if ((eyeOpenProb_list[i][0] < 0.2) or (eyeOpenProb_list[i][1] < 0.2)) {
                        if (eyeOpenProb_list[i][0] + eyeOpenProb_list[i][1] < 0.5) {
                            val src = Rect(0, 0, mask_eyeclosed.getWidth(), mask_eyeclosed.getHeight())
                            canvas.drawBitmap(mask_eyeclosed, src, dest, paint)
                        }

                    } else {
                        val src = Rect(0, 0, mask_normal.getWidth(), mask_normal.getHeight())
                        canvas.drawBitmap(mask_normal, src, dest, paint)
                    }
            }
                val photoView = findViewById<ImageView>(R.id.photo_view2)
                progressBar.visibility = View.GONE
                photoView.setImageBitmap(image)
            }
        }
    }

    inline fun <T: Any> ifLet(vararg elements: T?, closure: (List<T>) -> Unit) {
        if (elements.all { it != null }) {
            closure(elements.filterNotNull())
        }
    }

    override fun onResume() {
        super.onResume()
    }

}