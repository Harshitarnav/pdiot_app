package com.specknet.pdiotapp.Sensors

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
import com.specknet.pdiotapp.Login.StartActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.reflect.typeOf


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
        "Sitting,Standing",
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

    var model = "respeck_cnn-lstm_14.tflite"
//    var classCount = 5
    var classCount = 14
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

                    // Call to function processing the data must be here
                    // Normalize data
                    val normData: FloatArray = normalizeData(data, RESPECK_MEANS, RESPECK_STDS)

                    window.remove()
                    window.add(normData)

                    counter++

                    if (predictFrequency==counter) {
                        counter = 0
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
        val fileDescriptor = this.assets.openFd("respeck_cnn-lstm_14.tflite")
        val fileInputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffSets = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffSets, declaredLength)
    }

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
            val predictedLabel = labels[maxIndex]
            val confidence = maxValue * 100
            val label = String.format("%.2f :: %s", confidence, predictedLabel)
            output.text = label
            Log.i("hell", predictedLabel)

            userID = auth!!.currentUser!!.uid
            val act_val= mutableMapOf<String, Any>()
            val doc_ref: DocumentReference = store!!.collection("users").document(userID!!)
            doc_ref.addSnapshotListener(this, object : EventListener<DocumentSnapshot?> {
                override fun onEvent(value: DocumentSnapshot?, error: FirebaseFirestoreException?) {
                    assert(value != null)
//                    if (predictedLabel == "Lying Down") {
//                        val myLong: Double = value!!.getDouble("Lying Down") as Double
//                        act_val["Lying Down"] = (myLong + 2.5)
//                        store!!.collection("users").document(userID!!)
//                            .set(act_val, SetOptions.merge())
//                    }
//                    else if (predictedLabel == "Running") {
//                        val myLong: Double = value!!.getDouble("Running") as Double
//                        act_val["Running"] = (myLong + 2.5)
//                        store!!.collection("users").document(userID!!)
//                            .set(act_val, SetOptions.merge())
//                    }
//                    else if (predictedLabel == "Sitting,Standing") {
//                        val myLong: Double = value!!.getDouble("Sitting,Standing") as Double
//                        act_val["Sitting,Standing"] = (myLong + 2.5)
//                        store!!.collection("users").document(userID!!)
//                            .set(act_val, SetOptions.merge())
//                    }
//                    else if (predictedLabel == "Stairs") {
//                        val myLong: Double = value!!.getDouble("Stairs") as Double
//                        act_val["Stairs"] = (myLong + 2.5)
//                        store!!.collection("users").document(userID!!)
//                            .set(act_val, SetOptions.merge())
//                    }
//                    else if (predictedLabel == "Walking") {
//                        val myLong: Double = value!!.getDouble("Walking") as Double
//                        act_val["Walking"] = (myLong + 2.5)
//                        store!!.collection("users").document(userID!!)
//                            .set(act_val, SetOptions.merge())
//                    }



                    if (predictedLabel == "Climbing stairs") {
                        val myLong: Double = value!!.getDouble("Climbing stairs") as Double
                        act_val["Climbing stairs"] = (myLong + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Descending stairs") {
                        val myLong: Double? = value!!.getDouble("Descending stairs") as Double
                        act_val["Descending stairs"] = (myLong!! + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Desk work") {
                        val myLong: Double? = value!!.getDouble("Desk work") as Double
                        act_val["Desk work"] = (myLong!! + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Lying down left") {
                        val myLong: Double? = value!!.getDouble("Lying down left") as Double
                        act_val["Lying down left"] = (myLong!! + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Lying down on back") {
                        val myLong: Double? = value!!.getDouble("Lying down on back") as Double
                        act_val["Lying down on back"] = (myLong!! + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Lying down on stomach") {
                        val myLong: Double? = value!!.getDouble("Lying down on stomach") as Double
                        act_val["Lying down on stomach"] = (myLong!! + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Lying down right") {
                        val myLong: Double = value!!.getDouble("Lying down right") as Double
                        act_val["Lying down right"] = (myLong + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Movement") {
                        val myLong: Double = value!!.getDouble("Movement") as Double
                        act_val["Movement"] = (myLong + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Running") {
                        val myLong: Double = value!!.getDouble("Running") as Double
                        act_val["Running"] = (myLong + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Sitting") {
                        val myLong: Double = value!!.getDouble("Sitting") as Double
                        act_val["Sitting"] = (myLong + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Sitting bent backward") {
                        val myLong: Double = value!!.getDouble("Sitting bent backward") as Double
                        act_val["Sitting bent backward"] = (myLong + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Sitting bent forward") {
                        val myLong: Double = value!!.getDouble("Sitting bent forward") as Double
                        act_val["Sitting bent forward"] = (myLong + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Standing") {
                        val myLong: Double = value!!.getDouble("Standing") as Double
                        act_val["Standing"] = (myLong + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                    else if (predictedLabel == "Walking at normal speed") {
                        val myLong: Double = value!!.getDouble("Walking at normal speed") as Double
                        act_val["Walking at normal speed"] = (myLong + 2.5)
                        store!!.collection("users").document(userID!!)
                            .set(act_val, SetOptions.merge())
                    }
                }
            });
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

