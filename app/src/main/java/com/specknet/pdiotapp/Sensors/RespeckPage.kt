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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.RESpeckPacketHandler.Companion.TAG
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

    val ESSENTIAL_5_LABELS = arrayOf(
        "Lying down",
        "Running",
        "Sitting/Standing",
        "Stairs",
        "Walking"
    )

    val THINGY_MEANS = floatArrayOf(
        -0.45488289f,
        -0.05172744f,
        0.25008215f,
        -0.24328041f,
        -0.12463361f,
        -0.54979111f
    )

    val THINGY_STDS = floatArrayOf(
        0.54147741f,
        0.47916574f,
        0.62753662f,
        54.8137468f,
        55.71598468f,
        33.80911408f
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
    var featureCount = 6
    var windowWidth = 50
    var predictFrequency = 1
    var counter = 0


    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_respeck_page)

        respeckChart = findViewById(R.id.respeck_chart)
        output = findViewById<View>(R.id.output) as TextView

        auth = FirebaseAuth.getInstance()
        store = FirebaseFirestore.getInstance()

        setupCharts()
        initWindow(windowWidth)


//      Testing block
        userID = auth!!.currentUser!!.uid
//        FirebaseDatabase.getInstance().reference.child("users").child(userID!!).child("Lying Down").setValue("200")
        val docRef: DocumentReference = store!!.collection("users").document(userID!!)
        docRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val document = task.result
                val groupHash = document.getData() as Map<String, Any>
                Log.i("test", groupHash.toString())

                if (document != null) {
                    for (x in groupHash) {
//                        toString().split("=").toTypedArray().contentToString()
                        val value: String = x.toString().substringAfterLast("=")
                        val s = value.toInt()
                        Log.i("Thist and ", s.toString())
                        store!!.collection("users").document(userID!!)
                            .update("Lying Down", s+100)
                        Log.i("Thist and ", x.toString())
                    }
                }
            }
        }
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

                    var liveData =
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
//                    window.add(data)

                    // Call to function processing the data must be here
                    // Normalize data

                    // Normalize data
                    val normData: FloatArray = normalizeData(data, RESPECK_MEANS, RESPECK_STDS)

                    window.remove()
                    window.add(normData)

                    counter++

                    if (predictFrequency==1) {
//                        counter = 0
                        // Call to function processing the data must be here
                        Log.i("is it","causing problem")
                        inference(window)
                    }

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

    private fun normalizeData(data: FloatArray, means: FloatArray, stds: FloatArray): FloatArray {
        val normData = FloatArray(featureCount)
        for (i in 0 until featureCount) {
            normData[i] = (data[i] - means[i]) / stds[i]
        }
        return normData
    }


    fun setupCharts() {
//        respeckChart = findViewById(R.id.respeck_chart)
//
//        output = findViewById<View>(R.id.output) as TextView
//
//        try {
//            tflite = Interpreter(loadModelFile())
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//        // Order for CNN
//        // [accelX, accelY, accelZ, gyroX, gyroY, gyroZ]
//        val data = FloatArray(6)

//        try {
//            tflite = Interpreter(loadModelFile(model))
//        } catch (e: java.lang.Exception) {
//            e.printStackTrace()
//        }

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
    private fun loadModelFile(model: String): MappedByteBuffer {
        val fileDescriptor = this.assets.openFd("respeck_lstm_essential5.tflite")
        val fileInputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffSets = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffSets, declaredLength)
    }

//    fun getDataPoint(): FloatArray? {
//
//        // Order for CNN
//        // [accelX, accelY, accelZ, gyroX, gyroY, gyroZ]
//        val data = FloatArray(6)
//
//        // set up the broadcast receiver
//        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)
//                val action = intent.action
//                if (action === Constants.ACTION_RESPECK_LIVE_BROADCAST) {
//                    val liveData =
//                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData?
//                    Log.d("Live", "onReceive: liveData = $liveData")
//
//                    // get all relevant intent contents
//                    data[0] = liveData!!.accelX
//                    data[1] = liveData.accelY
//                    data[2] = liveData.accelZ
//                    val (x, y, z) = liveData.gyro
//                    data[3] = x
//                    data[4] = y
//                    data[5] = z
//                    window.remove()
//                    window.add(data)
//
//                    // Call to function processing the data must be here
//                    displayData(data)
//                }
//            }
//        }
//
//        // register receiver on another thread
//        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
//        handlerThreadRespeck.start()
//        looperRespeck = handlerThreadRespeck.looper
//        val handlerRespeck = Handler(looperRespeck)
//        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)
//        return data
//    }

//    fun displayData(data: FloatArray?) {
//        inference(window)
//    }

//    fun inference(window: Queue<*>): Float {
//        val inputValue = Array(1) {
//            Array(50) {
//                FloatArray(6)
//            }
//        }
//        var idx = 0
//        for (o in window) {
//            inputValue[0][idx] = o as FloatArray
//            idx++
//        }
//        val outputValue = Array(1) {
//            FloatArray(
//                14
//            )
//        }
//        tflite!!.run(inputValue, outputValue)
//        var maxValue = outputValue[0][0]
//        var maxIndex = -1
//        for (i in 0..13) {
//            if (maxValue < outputValue[0][i]) {
//                maxValue = outputValue[0][i]
//                maxIndex = i
//            }
//        }
//        if (maxIndex != -1) {
//            val predictedLabel = labels[maxIndex]
//            output.text = predictedLabel
//
//        }
//        userID = auth!!.currentUser!!.uid
//        val doc_ref = store!!.collection("users").document(userID!!)
//        val user:HashMap<String,String> = HashMap<String,String>()
//        user["Action"] = labels[maxIndex]
//        user["Confidence"] = maxValue.toString()
//
//        doc_ref.set(user).addOnSuccessListener {
//            Log.d(
//                "TAG",
//                "onSuccess: User profile created for user $userID"
//            )
//        }
//
//        return maxValue
//    }

    fun inference(window: Queue<*>): Float {
        Log.i("is it","causing problem 1")
        val inputValue = Array(1) {
            Array(windowWidth) {
                FloatArray(featureCount)
            }
        }
        Log.i("is it","causing problem 2")
        var idx = 0
        for (o in window) {
            inputValue[0][idx] = o as FloatArray
            idx++
        }
        Log.i("is it","causing problem 3")
        val outputValue = Array(1) {
            FloatArray(
                classCount
            )
        }
        Log.i("is it","causing problem 3")
        tflite!!.run(inputValue, outputValue)
        Log.i("is it","causing problem 4")
        var maxValue = outputValue[0][0]
        var maxIndex = -1
        for (i in 0 until classCount) {
            if (maxValue < outputValue[0][i]) {
                maxValue = outputValue[0][i]
                maxIndex = i
            }
        }
        Log.i("is it","causing problem5")
        if (maxIndex != -1) {
            val predictedLabel = ESSENTIAL_5_LABELS[maxIndex]
            val confidence = maxValue * 100
            val label = String.format("%.2f :: %s", confidence, predictedLabel)
            output.text = label

            userID = auth!!.currentUser!!.uid
            val docRef: DocumentReference = store!!.collection("users").document(userID!!)
            docRef.get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val document = task.result
                    val groupHash = document.getData() as Map<String, Any>
                    if (document != null) {
                        for (x in groupHash) {
                            if (x.toString() == predictedLabel) {
                                val value: String = x.toString().substringAfterLast("=")
                                val s = value.toInt()
                                Log.i("Thist and ", s.toString())
//                                store!!.collection("users").document(userID!!)
//                                    .update("Lying Down", 100)
                                Log.i("Thist and ", x.toString())
                                store!!.collection("users").document(userID!!)
                                    .update(predictedLabel, s + 2.5)
                            }
                        }
                    }
                }
            }
        }
        Log.i("is it","causing problem 6")
        return maxValue
    }

}
//                        val activity = document.getString("Lying Down")
//                        if (activity == )
//                        Log.i("LOGGER", "First " + document.getString("first"))
//                        Log.i("LOGGER", "Last " + document.getString("last"))
//                        Log.i("LOGGER", "Born " + document.getString("born"))
//                    } else {
//                        Log.d("LOGGER", "No such document")
//                    }
//                } else {
//                    Log.d("LOGGER", "get failed with ", task.exception)
//                }
//                    }

//            if (predictedLabel == "Lying Down") {
//            }
//            }
//                val doc_ref = store!!.collection("users").whereEqualTo(userID!!,true).get("Lying Down")
//                val activity1 = doc_ref.getString()
//                val activity:HashMap<String,String> = HashMap<String,String>()
//                doc_ref["Lying Down"].set(activity).addOnSuccessListener {
//                    Log.d(
//                        "TAG",
//                        "onSuccess: User profile created for user $userID"
//                    )
//                }
//                doc_ref.get()
//                    .addOnSuccessListener { activities ->
//                        for (activity in activities) {
//                            if (activity != null) {
//                                if (activity.toString() == "Walking"){
//                                    activity.set(predictedLabel, doc_ref.toString()+1)
//                                }
//                                Log.d(TAG, "DocumentSnapshot data: ${activity.data}")
//                            } else {
//                                Log.d(TAG, "No such document")
//                            }
//                        }
//                    }
//                    .addOnFailureListener { exception ->
//                        Log.d(TAG, "get failed with ", exception)
//                    }
//
//                store!!.collection("users").whereEqualTo(userID!!, true)
//                .getDocuments() { (querySnapshot, err) in
//                    if let err = err {
//                        print("Error getting documents: \(err)")
//                    } else {
//                        self.events = querySnapshot!.documents.map { document in
//                                return Event(eventName: (document.get("event_name") as? String) ?? "")
//                        }
//            }
//
//            user["Action"] = ESSENTIAL_5_LABELS[maxIndex]
//            user["Confidence"] = maxValue.toString()
//                }
//
//            }
//        }
//        Log.i("is it","causing problem 6")
//        return maxValue
//    }
//
//}

