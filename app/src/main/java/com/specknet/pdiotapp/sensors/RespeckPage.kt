package com.specknet.pdiotapp.sensors

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.EventListener
import com.specknet.pdiotapp.login.StartActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

//essential 5 classes

class RespeckPage : AppCompatActivity() {

    val ESSENTIAL_5_LABELS = arrayOf(
        "Lying down",
        "Running",
        "Sitting,Standing",
        "Stairs",
        "Walking"
    )

    val RESPECK_MEANS = floatArrayOf(
        -0.02206804f,
        -0.64659745f,
        0.03289176f,
        0.12822682f,
        0.09448824f,
        -0.04363118f
    )

    val RESPECK_STDS = floatArrayOf(
        0.43048498f,
        0.49491601f,
        0.5036586f,
        14.27410881f,
        17.39951753f,
        9.6897829f
    )

    var tflite: Interpreter? = null

    var window: Queue<FloatArray> = LinkedList()

    fun initWindow(size: Int) {
        for (i in 0 until size) {
            window.add(FloatArray(featureCount))
        }
    }

    private var auth: FirebaseAuth? = null
    private var store: FirebaseFirestore? = null
    var userID: String? = null

    lateinit var output: TextView

    // global graph variables
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet

    var time = 0f
    lateinit var allRespeckData: LineData

    lateinit var respeckChart: LineChart
    // global broadcast receiver so we can unregister it
    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver

    lateinit var looperRespeck: Looper

//    private var liveData: RESpeckLiveData? = null

    var model = "respeck_lstm_essential5.tflite"
    var classCount = 5
//    var classCount = 14
    var featureCount = 6
//    var windowWidth = 50
    var windowWidth = 64
    var predictFrequency = 1
    var counter = 0

    lateinit var backbtn: Button

    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_respeck_page)
        backbtn = findViewById(R.id.backbtn)

        respeckChart = findViewById(R.id.respeck_chart)
        output = findViewById<View>(R.id.output) as TextView

        auth = FirebaseAuth.getInstance()
        store = FirebaseFirestore.getInstance()

        setupCharts()
        initWindow(windowWidth)

        try {
            tflite = Interpreter(loadModelFile(model))
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {

            // Order for CNN
            // [accelX, accelY, accelZ, gyroX, gyroY, gyroZ]
            val data = FloatArray(6)

            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                Log.i("thread ", "2")

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    Log.i("thread ", "3")

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.i("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    time += 1
                    updateGraph("respeck", x, y, z)

                    // get all relevant intent contents
                    data[0] = liveData.accelX
                    data[1] = liveData.accelY
                    data[2] = liveData.accelZ
                    val (i, j, k) = liveData.gyro
                    data[3] = i
                    data[4] = j
                    data[5] = k

                    // Call to function processing the data must be here
                    // Normalize data
                    val normData: FloatArray = normalizeData(data, RESPECK_MEANS, RESPECK_STDS)

                    window.remove()
                    window.add(normData)

//                    counter++

//                    if (predictFrequency==counter) {
//                        counter = 0
//                        // Call to function processing the data must be here
//                        Log.i("is it","causing problem")
                    inference(window)
                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)

        backbtn.setOnClickListener(){
            startActivity(Intent(this, StartActivity::class.java))
        }
    }

    private fun normalizeData(data: FloatArray, means: FloatArray, stds: FloatArray): FloatArray {
        val normData = FloatArray(featureCount)
        for (i in 0 until featureCount) {
            normData[i] = (data[i] - means[i]) / stds[i]
        }
        return normData
    }


    fun setupCharts() {
        // Respeck

        time = 0f
        val entries_res_accel_x = ArrayList<Entry>()
        val entries_res_accel_y = ArrayList<Entry>()
        val entries_res_accel_z = ArrayList<Entry>()

        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")

        dataSet_res_accel_x.setDrawCircles(false)
        dataSet_res_accel_y.setDrawCircles(false)
        dataSet_res_accel_z.setDrawCircles(false)

        dataSet_res_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_res_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_res_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsRes = ArrayList<ILineDataSet>()
        dataSetsRes.add(dataSet_res_accel_x)
        dataSetsRes.add(dataSet_res_accel_y)
        dataSetsRes.add(dataSet_res_accel_z)

        allRespeckData = LineData(dataSetsRes)
        respeckChart.data = allRespeckData
        respeckChart.invalidate()


    }

    fun updateGraph(graph: String, x: Float, y: Float, z: Float) {
        // take the first element from the queue
        // and update the graph with it
        if (graph == "respeck") {
            dataSet_res_accel_x.addEntry(Entry(time, x))
            dataSet_res_accel_y.addEntry(Entry(time, y))
            dataSet_res_accel_z.addEntry(Entry(time, z))
            Log.i("bhak", dataSet_res_accel_z.toString())

            runOnUiThread {
                allRespeckData.notifyDataChanged()
                respeckChart.notifyDataSetChanged()
                respeckChart.invalidate()
                respeckChart.setVisibleXRangeMaximum(150f)
                respeckChart.moveViewToX(respeckChart.lowestVisibleX + 40)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        looperRespeck.quit()
    }


    @Throws(IOException::class)
    private fun loadModelFile(model: String): MappedByteBuffer {
        val fileDescriptor = this.assets.openFd(model)
        val fileInputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffSets = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffSets, declaredLength)
    }

    fun inference(window: Queue<*>) {
        val inputValue = Array(1) {
            Array(windowWidth) {
                FloatArray(featureCount)
            }
        }
        var idx = 0
        for (o in window) {
            inputValue[0][idx] = o as FloatArray
            idx++
        }
        val outputValue = Array(1) {
            FloatArray(
                classCount
            )
        }
        tflite!!.run(inputValue, outputValue)
        Log.i("is it",outputValue[0][0].toString())
        var maxValue = outputValue[0][0]
        var maxIndex = -1
        for (i in 0 until classCount) {
            if (maxValue < outputValue[0][i]) {
                maxValue = outputValue[0][i]
                maxIndex = i
            }
        }
        if (maxIndex != -1) {
            val predictedLabel = ESSENTIAL_5_LABELS[maxIndex]
            val confidence = maxValue * 100
//            val label = String.format("%s", predictedLabel)
//            output.text = label

            userID = auth!!.currentUser!!.uid
            val act_val= mutableMapOf<String, Any>()
            val doc_ref: DocumentReference = store!!.collection("users").document(userID!!)
            doc_ref.addSnapshotListener(this, object : EventListener<DocumentSnapshot?> {
                override fun onEvent(value: DocumentSnapshot?, error: FirebaseFirestoreException?) {
                    assert(value != null)

                    val myLong: Double = value!!.getDouble(predictedLabel) as Double
                    act_val[predictedLabel] = (myLong + 1)
                    store!!.collection("users").document(userID!!)
                        .set(act_val, SetOptions.merge())
                    val label = String.format("%s", predictedLabel)
                    output.text = label

                }
            })
        }
    }
}