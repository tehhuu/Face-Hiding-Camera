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

        // intentから撮影画像のURI, ImageViewのサイズを取得
        cameraUri = intent.data
        width_view = intent.getIntExtra("width_view", 0)
        height_view = intent.getIntExtra("height_view", 0)

        progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        // 描画
        showImage("Face", "Image")

        // 起動時のページに戻る
        val backButton = findViewById<Button>(R.id.rephoto_button)
        backButton.setOnClickListener {
            val intent = Intent(this, FrontPage::class.java)
            startActivity(intent)
        }

        // 撮影した写真を保存する
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

        // 隠す範囲の顔全体 or 口周辺のみの切り替え
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

        // ふなっしー描画 or モザイク処理 の切り替え
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


    // 写真をギャラリーに保存
    // Save the image in the Gallety.
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


    // 切り替えボタンが押されたら再描画する
    // Redraw when the ToggleButons are pressed.
    fun drawAgain(range : String, type : String){
        // 他のボタンを押せないようにする
        saveButton.setEnabled(false)
        button_face_or_mouth.setEnabled(false)
        button_image_or_mosaic.setEnabled(false)
        // プログレスバーを表示
        progressBar.visibility = View.VISIBLE
        // 描画
        showImage(range, type)
        // 描画が終わったら他のボタンを有効にする
        saveButton.setEnabled(true)
        button_face_or_mouth.setEnabled(true)
        button_image_or_mosaic.setEnabled(true)
    }


    // 描画処理
    private fun showImage(range : String, type : String){
        cameraUri?.let{
            // 撮影画像の URI から Bitmap を取得
            // Get the bitmap of the captured image from URI.
            val stream: InputStream? = this.getContentResolver().openInputStream(cameraUri!!)
            var image_org = BitmapFactory.decodeStream(BufferedInputStream(stream))

            // 画像の横幅が端末のそれと同じサイズになるよう縮小する
            // これにより描画時間が大幅に削減される
            // In order to significantly reduce drawing time,
            // Shrink the image so that its width is the same as that of the screen.
            var image_resized = Bitmap.createScaledBitmap(image_org
                    , width_view
                    , (image_org.getHeight() * (width_view.toFloat() / image_org.getWidth())).toInt()
                    ,true)

            // Firebase の Detector の Option を設定
            val highAccuracyOpts = FirebaseVisionFaceDetectorOptions.Builder()
                    .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                    .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                    .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                    .build()

            // FirebaseVisionImage に変換
            val inputImage = FirebaseVisionImage.fromBitmap(image_resized)

            // detectorを作成
            val detector = FirebaseVision.getInstance()
                    .getVisionFaceDetector(highAccuracyOpts)

            // 顔検出の処理
            detector.detectInImage(inputImage)
                        .addOnSuccessListener { faces ->
                            // Task completed successfully
                            var mouthPos_list = arrayOf<Array<Int>>()
                            var facePos_list = arrayOf<Array<Int>>()
                            var smileProb_list = floatArrayOf()
                            var eyeOpenProb_list = arrayOf<Array<Float>>()
                            var mouth_flag = false

                            for (face in faces) {

                                // 各顔の口周りの座標を保存
                                val flag_mouth_bottom = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM)
                                val flag_mouth_left = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT)
                                val flag_mouth_right = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT)
                                val flag_nose = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE)
                                ifLet(flag_mouth_bottom, flag_mouth_left, flag_mouth_right, flag_nose) {
                                    (bottom, left, right, nose) ->
                                    mouth_flag = true
                                    var mouth_bottom = bottom.position.getY().toInt()
                                    var mouth_left = left.position.getX().toInt()
                                    var mouth_right = right.position.getX().toInt()
                                    var top = nose.position.getY().toInt()
                                    mouthPos_list += arrayOf(mouth_left, top, mouth_right, mouth_bottom)
                                }
                                if (!mouth_flag && type == "Mouth"){
                                    continue
                                }

                                //各顔の笑顔確率を保存
                                if (face.smilingProbability != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                                    Log.d("debug", "smile")
                                    val smileProb = face.smilingProbability
                                    smileProb_list += smileProb
                                }
                                else{
                                    smileProb_list +=  0f
                                }

                                // 各顔の左目・右目の開いている確率を保存
                                if (face.leftEyeOpenProbability != FirebaseVisionFace.UNCOMPUTED_PROBABILITY
                                        && face.rightEyeOpenProbability != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                                    val leftEyeOpenProb = face.leftEyeOpenProbability
                                    val rightEyeOpenProb = face.rightEyeOpenProbability
                                    eyeOpenProb_list += arrayOf(leftEyeOpenProb, rightEyeOpenProb)
                                }
                                else{
                                    eyeOpenProb_list += arrayOf(0.5f, 0.5f)
                                }

                                // 各顔の顔周りの座標を保存
                                val bounds = face.boundingBox
                                facePos_list += arrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
                            }

                            // range, type に応じた描画処理を行う
                            if (range == "Face"){
                                if (type == "Image") {
                                    drawingWithMask(image_resized, facePos_list, smileProb_list, eyeOpenProb_list)
                                }
                                else if (type == "Mosaic") {
                                    drawingWithMosaic(image_resized, facePos_list)
                                }
                            }
                            else if (range == "Mouth"){
                                if (type == "Image") {
                                    drawingWithMask(image_resized, mouthPos_list, smileProb_list, eyeOpenProb_list)
                                }
                                else if(type == "Mosaic"){
                                    drawingWithMosaic(image_resized, mouthPos_list)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            // Task failed with an exception
                            // ...
                        }
        }
    }


    // モザイク処理して描画
    // Show the image after mosaicing
    private fun drawingWithMosaic(_image : Bitmap, pos_list : Array<Array<Int>>) {

        // mutable な画像の取得
        // Get the mutable image
        image = _image.copy(_image.getConfig(), true)

        // 画像のサイズ取得
        val w = image!!.getWidth()
        val h = image!!.getHeight()

        // 画像の画素情報を取得
        var pixels =  IntArray(w * h)
        image!!.getPixels(pixels, 0, w, 0, 0, w, h)

        val num_pos = pos_list.size
        if (num_pos != 0) {
            // 平均化フィルタのサイズ
            val kernel_size = 31
            val ks_half : Int = kernel_size / 2
            val kernel_sum = Math.pow(kernel_size.toDouble(), 2.0).toInt()

            // 平均化フィルタ
            // average filter
            for (pos in pos_list) {
                val left = pos[0]
                val top = pos[1]
                val right = pos[2]
                val bottom = pos[3]
                for (x in left..right) {
                    for (y in top..bottom) {
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
        }
        // モザイク処理後の画素情報を image にセット
        image!!.setPixels(pixels, 0, w, 0, 0, w, h)
        // プログレスバーを非表示にする
        progressBar.visibility = View.GONE
        // ImageView に描画
        val photoView = findViewById<ImageView>(R.id.photo_view2)
        photoView.setImageBitmap(image)
    }


    //　顔に表情に応じたふなっしーを被せた写真を描画
    // Draw the image which faces are covered with some facial expressions of funassi.
    fun drawingWithMask(_image : Bitmap, facePos_list : Array<Array<Int>>,
                          smileProb_list : FloatArray, eyeOpenProb_list : Array<Array<Float>>){

        var paint : Paint = Paint()

        // mutale な画像を取得し Canvas を作成
        // Get the mutable image and make a Canvas
        image = _image.copy(_image.getConfig(), true)
        var canvas: Canvas = Canvas(image!!)

        // 各表情に対応したふなっしー画像を Bitmap で取得
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

                // ふなっしーの描画範囲
                val dest = Rect(left, top, right, bottom)

                // 笑ってたら笑顔のふなっしーを被せる
                // If a person is smiling, put the smiling funassi.
                if (smileProb_list[i] >= 0.7) {
                    val src = Rect(0, 0, mask_smile.getWidth(), mask_smile.getHeight())
                    canvas.drawBitmap(mask_smile, src, dest, paint) //
                }
                // 目が閉じてたら目をとじたふなっしーを描画
                // If a person's eyes are closed, put the eyes-closed funassi.
                else if ((eyeOpenProb_list[i][0] < 0.2) or (eyeOpenProb_list[i][1] < 0.2)) {
                    if (eyeOpenProb_list[i][0] + eyeOpenProb_list[i][1] < 0.5) {
                        val src = Rect(0, 0, mask_eyeclosed.getWidth(), mask_eyeclosed.getHeight())
                        canvas.drawBitmap(mask_eyeclosed, src, dest, paint)
                    }
                }
                // それ以外の場合は普通の表情のふなっしーを被せる
                // In another Cases, put the normal funassi.
                else {
                    val src = Rect(0, 0, mask_normal.getWidth(), mask_normal.getHeight())
                    canvas.drawBitmap(mask_normal, src, dest, paint)
                }
            }
        }
        // プログレスバーを非表示
        progressBar.visibility = View.GONE
        // 描画
        val photoView = findViewById<ImageView>(R.id.photo_view2)
        photoView.setImageBitmap(image)
    }


    // nullかどうかをまとめて確認
    inline fun <T: Any> ifLet(vararg elements: T?, closure: (List<T>) -> Unit) {
        if (elements.all { it != null }) {
            closure(elements.filterNotNull())
        }
    }

}