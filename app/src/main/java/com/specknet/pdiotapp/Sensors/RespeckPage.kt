package com.specknet.pdiotapp.Sensors

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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class RespeckPage : AppCompatActivity() {

    var labels = arrayOf(
        "Climbing stairs",
        "Descending stairs",
        "Desk work",
        "Lying down left",
        "Lying down on back",
        "Lying down on stomach",
        "Lying down right",
        "Movement",
        "Running",
        "Sitting",
        "Sitting bent backward",
        "Sitting bent forward",
        "Standing",
        "Walking at normal speed"
    )

    var tflite: Interpreter? = null

    var window: Queue<FloatArray> = LinkedList()

    fun initWindow(size: Int) {
        for (i in 0 until size) {
            window.add(FloatArray(6))
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


    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_respeck_page)


        auth = FirebaseAuth.getInstance()
        store = FirebaseFirestore.getInstance()

        setupCharts()



        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    time += 1
                    updateGraph("respeck", x, y, z)

                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)

    }


    fun setupCharts() {
        respeckChart = findViewById(R.id.respeck_chart)

        output = findViewById<View>(R.id.output) as TextView

        try {
            tflite = Interpreter(loadModelFile())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        getDataPoint()

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
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = this.assets.openFd("respeck_cnn.tflite")
        val fileInputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffSets = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffSets, declaredLength)
    }

    fun getDataPoint(): FloatArray? {

        // Order for CNN
        // [accelX, accelY, accelZ, gyroX, gyroY, gyroZ]
        val data = FloatArray(6)

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)
                val action = intent.action
                if (action === Constants.ACTION_RESPECK_LIVE_BROADCAST) {
                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData?
                    Log.d("Live", "onReceive: liveData = $liveData")

                    // get all relevant intent contents
                    data[0] = liveData!!.accelX
                    data[1] = liveData.accelY
                    data[2] = liveData.accelZ
                    val (x, y, z) = liveData.gyro
                    data[3] = x
                    data[4] = y
                    data[5] = z
                    window.remove()
                    window.add(data)

                    // Call to function processing the data must be here
                    displayData(data)
                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)
        return data
    }

    fun displayData(data: FloatArray?) {
        inference(window)
    }

    fun inference(window: Queue<*>): Float {
        val inputValue = Array(1) {
            Array(50) {
                FloatArray(6)
            }
        }
        var idx = 0
        for (o in window) {
            inputValue[0][idx] = o as FloatArray
            idx++
        }
        val outputValue = Array(1) {
            FloatArray(
                14
            )
        }
        tflite!!.run(inputValue, outputValue)
        var maxValue = outputValue[0][0]
        var maxIndex = -1
        for (i in 0..13) {
            if (maxValue < outputValue[0][i]) {
                maxValue = outputValue[0][i]
                maxIndex = i
            }
        }
        if (maxIndex != -1) {
            val predictedLabel = labels[maxIndex]
            output.text = predictedLabel

        }
        userID = auth!!.currentUser!!.uid
        val doc_ref = store!!.collection("users").document(userID!!)
        val user:HashMap<String,String> = HashMap<String,String>()
        user["Action"] = labels[maxIndex]
        user["Confidence"] = maxValue.toString()

        return maxValue
    }
}

