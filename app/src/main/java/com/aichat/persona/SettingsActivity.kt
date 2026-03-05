package com.aichat.persona

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aichat.persona.databinding.ActivitySettingsBinding
import com.aichat.persona.util.PreferenceHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefHelper: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefHelper = PreferenceHelper(this)

        setupToolbar()
        setupModelSpinner()
        loadCurrentSettings()
        setupSaveButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
    }

    private fun setupModelSpinner() {
        val models = PreferenceHelper.AVAILABLE_MODELS
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, models)
        binding.spinnerModel.setAdapter(adapter)
    }

    private fun loadCurrentSettings() {
        binding.etApiKey.setText(prefHelper.apiKey)
        binding.spinnerModel.setText(prefHelper.model, false)
        binding.sliderTemperature.value = prefHelper.temperature
        binding.tvTemperatureValue.text = String.format("%.2f", prefHelper.temperature)

        binding.sliderTemperature.addOnChangeListener { _, value, _ ->
            binding.tvTemperatureValue.text = String.format("%.2f", value)
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val apiKey = binding.etApiKey.text.toString().trim()
            val model = binding.spinnerModel.text.toString()
            val temperature = binding.sliderTemperature.value

            if (apiKey.isBlank()) {
                binding.tilApiKey.error = "API Key 不能为空"
                return@setOnClickListener
            }

            binding.tilApiKey.error = null
            prefHelper.apiKey = apiKey
            prefHelper.model = model
            prefHelper.temperature = temperature

            Toast.makeText(this, "✅ 设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnGetApiKey.setOnClickListener {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://cloud.siliconflow.cn/account/ak")
            )
            startActivity(intent)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
