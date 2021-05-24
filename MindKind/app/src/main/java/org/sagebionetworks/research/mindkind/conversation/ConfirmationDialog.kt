package org.sagebionetworks.research.mindkind.conversation

import android.app.Dialog
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
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
        private const val KEY_CONTINUE_BUTTON_TEXT = "KEY_CONTINUE_BUTTON_TEXT"
        private const val KEY_QUIT_BUTTON_TEXT = "KEY_QUIT_BUTTON_TEXT"

        fun newInstance(title: String, message: String, continueText: String, quitText: String): ConfirmationDialog {
            Log.d(LOG_TAG, "newInstance()")
            val args = Bundle()
            args.putString(KEY_TITLE, title)
            args.putString(KEY_MESSAGE, message)
            args.putString(KEY_CONTINUE_BUTTON_TEXT, continueText)
            args.putString(KEY_QUIT_BUTTON_TEXT, quitText)
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
        view.confirmation_continue.text = arguments?.getString(KEY_CONTINUE_BUTTON_TEXT)
        view.confirmation_continue.paintFlags = view.confirmation_continue.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        view.confirmation_quit.text = arguments?.getString(KEY_QUIT_BUTTON_TEXT)
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