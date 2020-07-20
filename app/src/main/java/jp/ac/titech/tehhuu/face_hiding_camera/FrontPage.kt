package jp.ac.titech.tehhuu.face_hiding_camera

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class FrontPage : AppCompatActivity() {
    //private var photoImage: Bitmap? = null
    private var cameraUri: Uri? = null
    private lateinit var path: String
    var width_view = 0
    var height_view = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.front_page)

        val photoButton = findViewById<Button>(R.id.photo_button)
        photoButton.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraUri = createSaveFileUri()
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
            val manager = packageManager
            val activities: List<*> = manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (!activities.isEmpty()) {
                startActivityForResult(intent, REQ_PHOTO)
            } else {
                Toast.makeText(this@FrontPage, R.string.toast_no_activities, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createSaveFileUri(): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date())
        val imagefilename = timestamp

        val storageDir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
        //if (!storageDir?.exists()){
        //storageDir?.mkdir()
        //}
        val file = File.createTempFile(imagefilename, ".jpg", storageDir)
        path = file.absolutePath
        return FileProvider.getUriForFile(this, "jp.ac.titech.tehhuu.face_hiding_camera", file)
    }

    /*override fun onActivityResult(reqCode: Int, resCode: Int, data: Intent?) {
        super.onActivityResult(reqCode, resCode, data)
        if (reqCode == REQ_PHOTO) {
            if (resCode == Activity.RESULT_OK) {
                photoImage = data?.extras?.get("data") as Bitmap
            }
        }
    }*/

    override fun onResume() {
        super.onResume()
        if (cameraUri != null) {
            val intent = Intent(this, CapturedView::class.java)
            intent.setData(cameraUri)
            Log.d("debug", width_view.toString() +  " " + height_view.toString())
            intent.putExtra("width_view", width_view)
            intent.putExtra("height_view", height_view)

            cameraUri = null
            startActivity(intent)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        width_view = findViewById<View>(R.id.photo_view).width
        height_view = findViewById<View>(R.id.photo_view).height
    }

    companion object {
        private const val REQ_PHOTO = 1234
    }
}