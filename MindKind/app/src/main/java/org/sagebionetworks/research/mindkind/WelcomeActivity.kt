package org.sagebionetworks.research.mindkind

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_task_list.*
import kotlinx.android.synthetic.main.activity_welcome.*
import kotlinx.android.synthetic.main.welcome_6.view.*
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.settings.SettingsActivity
import org.sagebionetworks.research.mindkind.viewmodel.TaskListViewModel
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.threeten.bp.LocalDateTime
import javax.inject.Inject

open class WelcomeActivity: AppCompatActivity() {

    var bullets = ArrayList<ImageView>()

    companion object {
        private val TAG = WelcomeActivity::class.java.simpleName
        private var NUM_ITEMS = 6
        fun logInfo(msg: String) {
            Log.i(WelcomeActivity::class.simpleName, msg)
        }
    }

    @Inject
    lateinit var reportRepo: ReportRepository

    @Inject
    lateinit var appConfigRepo: AppConfigRepository

    private lateinit var initializeAppViewModel: TaskListViewModel
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_welcome)
        val bullet = ResourcesCompat.getDrawable(
                resources, R.drawable.welcome_bullet, null)
        val activeBullet = ResourcesCompat.getDrawable(
                resources, R.drawable.welcome_bullet_active, null)

        viewPager.adapter = WelcomeAdapter(this)
        viewPager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

            }
            override fun onPageSelected(position: Int) {
                bullets.forEach() {
                    it.setImageDrawable(bullet)
                }

                bullets[position].setImageDrawable(activeBullet)
            }
        })

        val pageTransformer: ParallaxPageTransformer = ParallaxPageTransformer()
                .addViewToParallax(ParallaxPageTransformer.ParallaxTransformInformation(
                        R.id.welcome_butterflies, 2.0f, 2.0f))
                .addViewToParallax(ParallaxPageTransformer.ParallaxTransformInformation(
                        R.id.welcome_wing, 1.0f, 1.0f))

         viewPager.setPageTransformer(true, pageTransformer) //set page transformer

        val container: ViewGroup = findViewById<View>(R.id.welcome_bullet_container) as ViewGroup
        val padding = resources.getDimension(R.dimen.welcome_bullet_padding).toInt()
        for (i in 0 until NUM_ITEMS) {
            val img = ImageView(this)
            if (i == 0) {
                img.setImageDrawable(activeBullet)
            } else {
                img.setImageDrawable(bullet)
            }
            bullets.add(img)
            img.setPadding(padding, padding, padding, padding)
            container.addView(img)
        }

        sharedPrefs = BackgroundDataService.createSharedPrefs(this)

        // View Model logic is empty, but set them up to pre-load all the data
        initializeAppViewModel = ViewModelProvider(this, TaskListViewModel.Factory(
                appConfigRepo, reportRepo)).get()
        initializeAppViewModel.taskListLiveData(sharedPrefs).observe(this, Observer {
            Log.i(TAG, "At ${LocalDateTime.now()} AI state is ${it.aiState}")
        })
        initializeAppViewModel.studyProgressLiveData().observe(this, Observer {
            // Progress in study is null until the day after they completed their baseline
            val progressInStudy = it ?: run {
                return@Observer
            }
            if (!sharedPrefs.contains(TaskListViewModel.studyStartDateKey)) {
                val localStartStr = progressInStudy.localStart.toString()
                Log.i(TAG, "First save of existing study date $localStartStr")
                sharedPrefs.edit().putString(TaskListViewModel.studyStartDateKey, localStartStr).apply()
            }
        })
    }

    fun onQuitClicked() {
        finish()
    }

    fun onContinueClicked() {
        SettingsActivity.startInitialDataTracking(this)
    }

    inner class WelcomeAdapter(private val mContext: Context) : PagerAdapter() {
        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view === `object`
        }

        override fun getCount(): Int {
            return NUM_ITEMS
        }

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            val resId = when(position) {
                0 -> R.layout.welcome_1
                1 -> R.layout.welcome_2
                2 -> R.layout.welcome_3
                3 -> R.layout.welcome_4
                4 -> R.layout.welcome_5
                else /*4*/ -> R.layout.welcome_6
            }
            val inflater = LayoutInflater.from(mContext)
            val layout = inflater.inflate(resId, collection, false) as ViewGroup

            if (resId == R.layout.welcome_6) {
                layout.confirmation_continue.setOnClickListener {
                    onContinueClicked()
                }
            }

            if (resId == R.layout.welcome_2) {
                // Make the link in the textView clickable
                val textView: TextView = layout.findViewById(R.id.welcome_2_detailText_textView)
                textView.movementMethod = LinkMovementMethod.getInstance()
            }

            collection.addView(layout)
            return layout
        }

        override fun destroyItem(collection: ViewGroup, position: Int, view: Any) {
            collection.removeView(view as View)
        }
    }
}
