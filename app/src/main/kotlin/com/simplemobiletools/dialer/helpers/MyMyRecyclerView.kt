package com.simplemobiletools.dialer.helpers

import android.content.Context
import android.util.AttributeSet
import com.simplemobiletools.commons.interfaces.RecyclerScrollCallback
import com.simplemobiletools.commons.views.MyRecyclerView

class MyMyRecyclerView : MyRecyclerView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var recyclerScrollCallback2: RecyclerScrollCallback? = null
    var mPrevFirstVisiblePosition2 = 0
    var mPrevScrolledChildrenHeight2 = 0
    var mPrevFirstVisibleChildHeight2 = -1
    var mScrollY2 = 0

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (recyclerScrollCallback2 != null) {
            if (childCount > 0) {
                val firstVisiblePosition = getChildAdapterPosition(getChildAt(0))
                val firstVisibleChild = getChildAt(0)
                if (firstVisibleChild != null) {
                    if (mPrevFirstVisiblePosition2 < firstVisiblePosition) {
                        mPrevScrolledChildrenHeight2 += mPrevFirstVisibleChildHeight2
                    }

                    if (firstVisiblePosition == 0) {
                        mPrevFirstVisibleChildHeight2 = firstVisibleChild.height
                        mPrevScrolledChildrenHeight2 = 0
                    }

                    if (mPrevFirstVisibleChildHeight2 < 0) {
                        mPrevFirstVisibleChildHeight2 = 0
                    }

                    mScrollY2 = mPrevScrolledChildrenHeight2 - firstVisibleChild.top
                }
            }
            recyclerScrollCallback2?.onScrolled(mScrollY2)
        }
    }
}
