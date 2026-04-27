package com.example.iot

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private var isFirstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)

        swipeRefreshLayout.setOnRefreshListener {
            fetchEventLogs()
            fetchPackageStatus()
        }

        // เรียกใช้ตอนเปิดแอป
        fetchPackageStatus()
        fetchEventLogs()

        // Popup สำหรับสแกนหน้า
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {

                // ผ่าน
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext, "กำลังเชื่อมต่อตู้ล็อกเกอร์...", Toast.LENGTH_SHORT).show()

                    val databaseUrl = "YOUR_FIREBASE_URL_HERE"
                    FirebaseDatabase.getInstance(databaseUrl).getReference("locker/unlock").setValue(1)
                }

                // ไม่ผ่าน
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "สแกนไม่ผ่าน ลองใหม่อีกครั้ง", Toast.LENGTH_SHORT).show()
                }

                // ยกเลิก
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "ยกเลิกการสแกน: $errString", Toast.LENGTH_SHORT).show()
                }
            })

        // ข้อความ์บนหน้าต่าง Popup
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ปลดล็อกตู้ล็อกเกอร์")
            .setSubtitle("กรุณาสแกนใบหน้าหรือลายนิ้วมือเพื่อเปิดตู้")
            .setNegativeButtonText("ยกเลิก")
            .build()

        // ผูกคำสั่งกับปุ่มbtnUnlock
        btnUnlock.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    // ดึงสถานะพัสดุ
    private fun fetchPackageStatus() {
        // ชี้เป้าไปที่ "locker/status" ใน Firebase
        val statusRef = FirebaseDatabase.getInstance("YOUR_FIREBASE_URL_HERE").getReference("locker/status")

        // สั่งให้ฟังการเปลี่ยนแปลงตลอดเวลา
        statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // ดึงค่ามาเป็นตัวเลข ไม่มี = 0
                val status = snapshot.getValue(Int::class.java) ?: 0
                val tvPackageStatus = findViewById<TextView>(R.id.tvPackageStatus)

                if (status == 1) {
                    tvPackageStatus.text = "📦 มีพัสดุในตู้"
                    tvPackageStatus.setTextColor(android.graphics.Color.parseColor("#27AE60")) // สีเขียว
                    if (!isFirstLoad){
                        showNotification("มีพัสดุมาส่ง!", "พัสดุมาส่งแล้ว!!")
                    }
                } else {
                    tvPackageStatus.text = "ตู้ว่าง"
                    tvPackageStatus.setTextColor(android.graphics.Color.parseColor("#333333")) // สีเทา
                }
                isFirstLoad = false
            }

            override fun onCancelled(error: DatabaseError) {
                // ถ้าอ่านข้อมูลไม่ได้
            }
        })
    }

    private fun fetchEventLogs() {
        // ชี้ไปที่ "locker/logs" เอาแค่ 7 logล่าสุด
        val logsRef = FirebaseDatabase.getInstance("YOUR_FIREBASE_URL_HERE").getReference("locker/logs")

        logsRef.limitToLast(7).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                //println("FirebaseData: ${snapshot.value}")
                val logList = mutableListOf<LogItem>()

                // loopดึงข้อมูลจาก Firebase มาใส่ List
                for (logSnapshot in snapshot.children) {
                    val time = logSnapshot.child("time").getValue(String::class.java) ?: ""
                    val event = logSnapshot.child("event").getValue(String::class.java) ?: ""

                    // ใส่ไว้ที่ index 0 เพื่อให้ข้อมูลใหม่สุดอยู่ด้านบน
                    logList.add(0, LogItem(time, event))
                }

                // แสดงในการ์ด
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewLogs)
                recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                recyclerView.adapter = LogAdapter(logList)

                // โหลดเสร็จแล้ว == ไอคอน SwipeRefresh หยุดหมุน
                findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout).isRefreshing = false
            }

            override fun onCancelled(error: DatabaseError) {
                findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout).isRefreshing = false
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Locker Alerts"
            val descriptionText = "แจ้งเตือนสถานะตู้ล็อกเกอร์"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("LOCKER_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, "LOCKER_CHANNEL")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // ไอคอนแอป
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 200, 500)) // สั่น
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        }
    }
}