package org.sagebionetworks.research.mindkind.conversation

import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TimePicker
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.sagebionetworks.research.mindkind.R
import java.util.*

class ConversationTimeOfDayDialog : BottomSheetDialogFragment() {

    private val LOG_TAG = this::class.java.canonicalName

    private lateinit var callback: Callback

    interface Callback {
        fun onDateSelected(d: Date)
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        Log.d(LOG_TAG, "onCreateView()")
        var view = inflater.inflate(R.layout.time_of_day_input, container, false)


        var picker: TimePicker = view.findViewById(R.id.time_of_day_picker)
        var cal = Calendar.getInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            picker.hour = cal.get(Calendar.HOUR_OF_DAY)
            picker.minute = cal.get(Calendar.MINUTE)
        } else {
            picker.currentHour = cal.get(Calendar.HOUR_OF_DAY)
            picker.currentMinute = cal.get(Calendar.MINUTE)
        }

        var cancel: View = view.findViewById(R.id.time_of_day_cancel)
        cancel.setOnClickListener {
            dismiss()
        }

        var submit: View = view.findViewById(R.id.time_of_day_submit)
        submit.setOnClickListener {
            var hour = picker.currentHour
            var minute = picker.currentMinute
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)

            var d: Date = cal.time
            callback.onDateSelected(d)
            Log.d(LOG_TAG, "Time: $hour:$minute")
            dismiss()
        }

        return view
    }


    companion object {
        const val TAG = "ConversationTimeOfDayDialog"
    }

    public fun setCallback(c: Callback) {
        callback = c
    }
}