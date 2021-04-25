package org.sagebionetworks.research.mindkind.conversation

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.DialogFragment
import kotlinx.android.synthetic.main.dialog_confirmation.view.*
import org.sagebionetworks.research.mindkind.R

class ConfirmationDialog : DialogFragment() {

    lateinit var listener: View.OnClickListener

    companion object {

        private val LOG_TAG = ConfirmationDialog::class.java.canonicalName
        const val TAG = "ConfirmationDialog"

        private const val KEY_TITLE = "KEY_TITLE"
        private const val KEY_MESSAGE = "KEY_MESSAGE"

        fun newInstance(title: String, message: String): ConfirmationDialog {
            Log.d(LOG_TAG, "newInstance()")
            val args = Bundle()
            args.putString(KEY_TITLE, title)
            args.putString(KEY_MESSAGE, message)
            val fragment = ConfirmationDialog()
            fragment.arguments = args
            return fragment
        }

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(LOG_TAG, "onCreateDialog()")

        // creating the fullscreen dialog
        val dialog = Dialog(requireActivity())

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirmation)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.setCancelable(false)

        dialog.findViewById<View>(R.id.confirmation_container)?.let {
            setupView(it)
            setupClickListeners(it)
        }

        return dialog
    }

    fun setActionListener(l: View.OnClickListener) {
        Log.d(LOG_TAG, "setActionListener()")
        listener = l
    }

    private fun setupView(view: View) {
        Log.d(LOG_TAG, "setupView()")
        view.confirmation_title.text = arguments?.getString(KEY_TITLE)
        view.confirmation_message.text = arguments?.getString(KEY_MESSAGE)
    }

    private fun setupClickListeners(view: View) {
        view.close_button.setOnClickListener {
            dismiss()
        }

        view.confirmation_continue.setOnClickListener {
            dismiss()
        }

        view.confirmation_quit?.setOnClickListener(listener)
    }

}