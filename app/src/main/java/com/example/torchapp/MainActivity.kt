package com.example.torchapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isTorchOn: Boolean = false
    private var hasCameraFlash: Boolean = false

    private lateinit var btnTorch: ImageButton
    private lateinit var tvStatus: TextView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            toggleTorch()
        } else {
            Toast.makeText(
                this,
                "La permission caméra est nécessaire pour utiliser le flash.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(camId: String, enabled: Boolean) {
            super.onTorchModeChanged(camId, enabled)
            if (camId == cameraId) {
                isTorchOn = enabled
                updateUI()
            }
        }

        override fun onTorchModeUnavailable(camId: String) {
            super.onTorchModeUnavailable(camId)
            if (camId == cameraId) {
                isTorchOn = false
                updateUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnTorch = findViewById(R.id.btnTorch)
        tvStatus = findViewById(R.id.tvStatus)

        hasCameraFlash = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!hasCameraFlash) {
            tvStatus.text = "AUCUN FLASH DÉTECTÉ"
            btnTorch.isEnabled = false
            Toast.makeText(this, "Cet appareil ne dispose pas d'un flash matériel.", Toast.LENGTH_LONG).show()
            return
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        initCameraWithFlash()

        cameraManager.registerTorchCallback(torchCallback, null)

        btnTorch.setOnClickListener {
            checkPermissionAndToggleTorch()
        }

        updateUI()
    }

    private fun initCameraWithFlash() {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    break
                }
            }

            if (cameraId == null) {
                for (id in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    if (hasFlash) {
                        cameraId = id
                        break
                    }
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur caméra : ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndToggleTorch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            toggleTorch()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun toggleTorch() {
        val targetCamId = cameraId
        if (targetCamId == null) {
            Toast.makeText(this, "Flash indisponible.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val newState = !isTorchOn
            cameraManager.setTorchMode(targetCamId, newState)
            isTorchOn = newState
            updateUI()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Impossible d'activer le flash : ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        if (isTorchOn) {
            btnTorch.isSelected = true
            btnTorch.setBackgroundResource(R.drawable.bg_torch_button_on)
            tvStatus.text = "ALLUMÉ"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FBBF24"))
        } else {
            btnTorch.isSelected = false
            btnTorch.setBackgroundResource(R.drawable.bg_torch_button_off)
            tvStatus.text = "ÉTEINT"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        }
    }

    override fun onStop() {
        super.onStop()
        if (isTorchOn && cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
                isTorchOn = false
                updateUI()
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.unregisterTorchCallback(torchCallback)
    }
}

