package com.simplemobiletools.dialer.fragments

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.dialogs.CallConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.interfaces.RecyclerScrollCallback
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.adapters.RecentCallsAdapter
import com.simplemobiletools.dialer.extensions.*
import com.simplemobiletools.dialer.helpers.DIALPAD_TONE_LENGTH_MS
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.helpers.ToneGeneratorHelper
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import com.simplemobiletools.dialer.models.RecentCall
import com.simplemobiletools.dialer.models.SpeedDial
import kotlinx.android.synthetic.main.activity_dialpad.*
import kotlinx.android.synthetic.main.dialpad.*
import kotlinx.android.synthetic.main.dialpad.view.*
import kotlinx.android.synthetic.main.fragment_contacts.contacts_fragment
import kotlinx.android.synthetic.main.fragment_recents.main_toolbar
import kotlinx.android.synthetic.main.fragment_recents.recents_fragment
import kotlinx.android.synthetic.main.fragment_recents.view.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

class RecentsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), RefreshItemsListener {
    private var allRecentCalls = ArrayList<RecentCall>()
    private var allContacts = java.util.ArrayList<SimpleContact>()
    private var speedDialValues = java.util.ArrayList<SpeedDial>()
    private var privateCursor: Cursor? = null
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        recents_placeholder.text = context.getString(placeholderResId)
        recents_placeholder_2.apply {
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }

        setupOptionsMenu()

        dialpad_list_wrapper.beGone()
        recents_list_wrapper.beVisible()
//        setDialpadVisibility(false)

        speedDialValues = activity!!.config.getSpeedDialValues()
        privateCursor = activity!!.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)

        toneGeneratorHelper = ToneGeneratorHelper(activity!!, DIALPAD_TONE_LENGTH_MS)

        setupCharClick(dialpad_1_holder, '1')
        setupCharClick(dialpad_2_holder, '2')
        setupCharClick(dialpad_3_holder, '3')
        setupCharClick(dialpad_4_holder, '4')
        setupCharClick(dialpad_5_holder, '5')
        setupCharClick(dialpad_6_holder, '6')
        setupCharClick(dialpad_7_holder, '7')
        setupCharClick(dialpad_8_holder, '8')
        setupCharClick(dialpad_9_holder, '9')
        setupCharClick(dialpad_0_holder, '0')
        setupCharClick(dialpad_asterisk_holder, '*', longClickable = false)
        setupCharClick(dialpad_hashtag_holder, '#', longClickable = false)

        dialpad_wrapper.dialpad_clear.setOnClickListener { clearChar(it) }
        dialpad_wrapper.dialpad_clear.setOnLongClickListener { clearInput(); true }
        dialpad_wrapper.dialpad_call.setOnClickListener { initCall(dialpad_input.value, 0) }
        dialpad_wrapper.dialpad_shortcut.setOnClickListener { setDialpadVisibility(false) }
        main_dialpad_button.setOnClickListener{ setDialpadVisibility(true) }

        SimpleContactsHelper(activity!!).getAvailableContacts(false) { gotContacts(it) }
        dialpad_input.disableKeyboard()
        dialpad_input.onTextChangeListener { dialpadValueChanged(it) }

        val properPrimaryColor = activity!!.getProperPrimaryColor()
//        val callIconId = if (activity!!.areMultipleSIMsAvailable()) {
//            val callIcon = resources.getColoredDrawableWithColor(R.drawable.ic_phone_two_vector, properPrimaryColor.getContrastColor())
//            dialpad_call_two_button.setImageDrawable(callIcon)
//            dialpad_call_two_button.background.applyColorFilter(properPrimaryColor)
//            dialpad_call_two_button.beVisible()
//            dialpad_call_two_button.setOnClickListener {
//                initCall(dialpad_input.value, 1)
//            }
//
//            R.drawable.ic_phone_one_vector
//        } else {
//            R.drawable.ic_phone_vector
//        }
//
//        val callIcon = resources.getColoredDrawableWithColor(callIconId, properPrimaryColor.getContrastColor())
//        dialpad_wrapper.dialpad_call.setImageDrawable(callIcon)
//        dialpad_wrapper.dialpad_call.background.applyColorFilter(properPrimaryColor)

        letter_fastscroller.textColor = activity!!.getProperTextColor().getColorStateList()
        letter_fastscroller.pressedTextColor = properPrimaryColor
        letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
        letter_fastscroller_thumb.textColor = properPrimaryColor.getContrastColor()
        letter_fastscroller_thumb.thumbColor = properPrimaryColor.getColorStateList()

//        recents_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
//            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
//                super.onScrollStateChanged(recyclerView, newState)
//            }
//            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
//            }
//        })
        recents_list.recyclerScrollCallback = object : RecyclerScrollCallback {
            override fun onScrolled(scrollY: Int) {
                return
            }
        }
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)

    }

    private fun setupOptionsMenu() {
        activity!!.setupSearch(main_toolbar.menu)
        main_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.clear_call_history -> activity!!.clearCallHistory()
                R.id.create_new_contact -> activity!!.launchCreateNewContactIntent()
                R.id.sort -> activity!!.showSortingDialog(showCustomSorting = false)
                R.id.settings -> activity!!.launchSettings()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
        main_toolbar.menu.apply {
            findItem(R.id.clear_call_history).isVisible = true
            findItem(R.id.sort).isVisible = false
            findItem(R.id.create_new_contact).isVisible = false
//            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(R.bool.hide_google_relations)
        }
    }

    private fun setDialpadVisibility(visible: Boolean) {
        val transition = AutoTransition()
        transition.duration = 130
        TransitionManager.beginDelayedTransition(dialpad_holder_group, transition)
        dialpad_wrapper.beVisibleIf(visible)
        main_dialpad_button.beGoneIf(visible)
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        recents_placeholder.setTextColor(textColor)
        recents_placeholder_2.setTextColor(properPrimaryColor)

        (recents_list?.adapter as? RecentCallsAdapter)?.apply {
            initDrawables()
            updateTextColor(textColor)
        }
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(false, true)
        val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
        RecentsHelper(context).getRecentCalls(groupSubsequentCalls) { recents ->
            SimpleContactsHelper(context).getAvailableContacts(false) { contacts ->
                val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)

                recents.filter { it.phoneNumber == it.name }.forEach { recent ->
                    var wasNameFilled = false
                    if (privateContacts.isNotEmpty()) {
                        val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
                        if (privateContact != null) {
                            recent.name = privateContact.name
                            wasNameFilled = true
                        }
                    }

                    if (!wasNameFilled) {
                        val contact = contacts.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }
                        if (contact != null) {
                            recent.name = contact.name
                        }
                    }
                }

                allRecentCalls = recents
                activity?.runOnUiThread {
                    gotRecents(recents)
                }
            }
        }
    }

    private fun gotRecents(recents: ArrayList<RecentCall>) {
        if (recents.isEmpty()) {
            recents_placeholder.beVisible()
            recents_placeholder_2.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))
            recents_list.beGone()
        } else {
            recents_placeholder.beGone()
            recents_placeholder_2.beGone()
            recents_list.beVisible()

            val currAdapter = recents_list.adapter
            if (currAdapter == null) {
                RecentCallsAdapter(activity as SimpleActivity, recents, recents_list, this, true) {
                    val recentCall = it as RecentCall
                    if (context.config.showCallConfirmation) {
                        CallConfirmationDialog(activity as SimpleActivity, recentCall.name) {
                            activity?.launchCallIntent(recentCall.phoneNumber)
                        }
                    } else {
                        activity?.launchCallIntent(recentCall.phoneNumber)
                    }
                }.apply {
                    recents_list.adapter = this
                }

                if (context.areSystemAnimationsEnabled) {
                    recents_list.scheduleLayoutAnimation()
                }
            } else {
                (currAdapter as? RecentCallsAdapter)?.updateItems(recents)
            }
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                recents_placeholder.text = context.getString(R.string.no_previous_calls)
                recents_placeholder_2.beGone()

                val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
                RecentsHelper(context).getRecentCalls(groupSubsequentCalls) { recents ->
                    activity?.runOnUiThread {
                        gotRecents(recents)
                    }
                }
            }
        }
    }

    override fun onSearchClosed() {
        recents_placeholder.beVisibleIf(allRecentCalls.isEmpty())
        (recents_list.adapter as? RecentCallsAdapter)?.updateItems(allRecentCalls)
    }

    override fun onSearchQueryChanged(text: String) {
        val recentsCalls = allRecentCalls.filter {
            it.name.contains(text, true) || it.doesContainPhoneNumber(text)
        }.sortedByDescending {
            it.name.startsWith(text, true)
        }.toMutableList() as ArrayList<RecentCall>

        recents_placeholder.beVisibleIf(recentsCalls.isEmpty())
        (recents_list.adapter as? RecentCallsAdapter)?.updateItems(recentsCalls, text)
    }

    private fun checkDialIntent(): Boolean {
        return if ((activity!!.intent.action == Intent.ACTION_DIAL || activity!!.intent.action == Intent.ACTION_VIEW) && activity!!.intent.data != null && activity!!.intent.dataString?.contains("tel:") == true) {
            val number = Uri.decode(activity!!.intent.dataString).substringAfter("tel:")
            dialpad_input.setText(number)
            dialpad_input.setSelection(number.length)
            true
        } else {
            false
        }
    }

    private fun dialpadPressed(char: Char, view: View?) {
        dialpad_input.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearChar(view: View) {
        dialpad_input.dispatchKeyEvent(dialpad_input.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInput() {
        dialpad_input.setText("")
    }

    private fun gotContacts(newContacts: java.util.ArrayList<SimpleContact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getSimpleContacts(activity!!, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        activity!!.runOnUiThread {
            if (!checkDialIntent() && dialpad_input.value.isEmpty()) {
                dialpadValueChanged("")
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun dialpadValueChanged(text: String) {
        val len = text.length

        if (len == 0) {
            dialpad_list_wrapper.beGone()
            recents_list_wrapper.beVisible()
            return
        }
        dialpad_list_wrapper.beVisible()
        recents_list_wrapper.beGone()

        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            val secretCode = text.substring(4, text.length - 4)
            if (isOreoPlus()) {
                if (activity!!.isDefaultDialer()) {
                    activity!!.getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
                } else {
                    activity!!._launchSetDefaultDialerIntent()
                }
            } else {
                val intent = Intent(Telephony.Sms.Intents.SECRET_CODE_ACTION, Uri.parse("android_secret_code://$secretCode"))
                activity!!.sendBroadcast(intent)
            }
            return
        }

        (dialpad_list.adapter as? ContactsAdapter)?.finishActMode()

        val filtered = allContacts.filter {
            val convertedName = PhoneNumberUtils.convertKeypadLettersToDigits(it.name.normalizeString())

//            if (hasRussianLocale) {
//                var currConvertedName = ""
//                convertedName.toLowerCase().forEach { char ->
//                    val convertedChar = russianCharsMap.getOrElse(char) { char }
//                    currConvertedName += convertedChar
//                }
//                convertedName = currConvertedName
//            }

            it.doesContainPhoneNumber(text) || (convertedName.contains(text, true))
        }.sortedWith(compareBy {
            !it.doesContainPhoneNumber(text)
        }).toMutableList() as java.util.ArrayList<SimpleContact>

        letter_fastscroller.setupWithRecyclerView(dialpad_list, { position ->
            try {
                val name = filtered[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.toUpperCase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })

        ContactsAdapter(activity!!, filtered, dialpad_list, null, text) {
            activity!!.startCallIntent((it as SimpleContact).phoneNumbers.first().normalizedNumber)
        }.apply {
            dialpad_list.adapter = this
        }

        dialpad_placeholder.beVisibleIf(filtered.isEmpty())
        dialpad_list.beVisibleIf(filtered.isNotEmpty())
    }

    private fun initCall(number: String = dialpad_input.value, handleIndex: Int) {
        if (number.isNotEmpty()) {
            if (handleIndex != -1 && activity!!.areMultipleSIMsAvailable()) {
                activity!!.callContactWithSim(number, handleIndex == 0)
            } else {
                activity!!.startCallIntent(number)
            }
        }
    }

    private fun speedDial(id: Int): Boolean {
        if (dialpad_input.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, -1)
                return true
            }
        }
        return false
    }

    private fun startDialpadTone(char: Char) {
        if (activity!!.config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (activity!!.config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (activity!!.config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else {
            val result = speedDial(char.digitToInt())
            if (result) {
                stopDialpadTone(char)
                clearChar(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed({
                            performLongClick(view, char)
                        }, longPressTimeout)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }
}
