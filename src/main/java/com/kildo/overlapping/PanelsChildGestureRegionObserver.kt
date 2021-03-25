package com.discord.panels

import android.graphics.Rect
import android.view.View
import androidx.annotation.UiThread
import java.lang.ref.WeakReference

/**
 * Use [PanelsChildGestureRegionObserver] to register child gesture regions that should handle
 * their own horizontal scrolls rather than defaulting to [OverlappingPanelsLayout] handling
 * the horizontal scrolls as panel-swipe gestures.
 *
 * Example usage:
 * 1) Use [PanelsChildGestureRegionObserver.Provider.get] to get an Activity-scoped instance of
 * [PanelsChildGestureRegionObserver]
 * 2) Add the [PanelsChildGestureRegionObserver] instance as an android.view.OnLayoutChangeListener
 * to each child view.
 * 3) In the parent of [OverlappingPanelsLayout], e.g. in a Fragment or Activity, implement
 * [GestureRegionsListener], and add the listener via [addGestureRegionsUpdateListener]
 * 4) Inside [GestureRegionsListener.onGestureRegionsUpdate], pass the child gesture regions to
 * [OverlappingPanelsLayout].
 * 5) Remember to remove views and listeners from [PanelsChildGestureRegionObserver] with [remove]
 * and [removeGestureRegionsUpdateListener] in appropriate Android lifecycle methods.
 */
class PanelsChildGestureRegionObserver : View.OnLayoutChangeListener {

  interface GestureRegionsListener {
    fun onGestureRegionsUpdate(gestureRegions: List<Rect>)
  }

  private var viewIdToGestureRegionMap = mutableMapOf<Int, Rect>()
  private var gestureRegionsListeners = mutableSetOf<GestureRegionsListener>()

  override fun onLayoutChange(
    view: View?,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    oldLeft: Int,
    oldTop: Int,
    oldRight: Int,
    oldBottom: Int
  ) {
    if (view != null) {
      val coordinates = intArrayOf(0, 0)
      view.getLocationOnScreen(coordinates)

      val x = coordinates[0]
      val y = coordinates[1]

      val absoluteLeft = x + left
      val absoluteTop = y + top
      val absoluteRight = x + right
      val absoluteBottom = y + bottom

      viewIdToGestureRegionMap[view.id] = Rect(
        absoluteLeft,
        absoluteTop,
        absoluteRight,
        absoluteBottom
      )

      publishGestureRegionsUpdate()
    }
  }

  /**
   * Stop publishing gesture region updates based on layout changes to android.view.View
   * corresponding to [viewId].
   */
  @UiThread
  fun remove(viewId: Int) {
    viewIdToGestureRegionMap.remove(viewId)
    publishGestureRegionsUpdate()
  }

  /**
   * Add [gestureRegionsListener] to this [PanelsChildGestureRegionObserver]. This method notifies
   * that listener as soon as it adds the listener. That listener will continue to get future
   * updates from layout changes on child gesture regions.
   */
  @UiThread
  fun addGestureRegionsUpdateListener(gestureRegionsListener: GestureRegionsListener) {
    val gestureRegions = viewIdToGestureRegionMap.values.toList()
    gestureRegionsListener.onGestureRegionsUpdate(gestureRegions)
    gestureRegionsListeners.add(gestureRegionsListener)
  }

  /**
   * Remove [gestureRegionsListener] from the set of listeners to notify.
   */
  @UiThread
  fun removeGestureRegionsUpdateListener(gestureRegionsListener: GestureRegionsListener) {
    gestureRegionsListeners.remove(gestureRegionsListener)
  }

  private fun publishGestureRegionsUpdate() {
    val gestureRegions = viewIdToGestureRegionMap.values.toList()
    gestureRegionsListeners.forEach { gestureRegionsListener ->
      gestureRegionsListener.onGestureRegionsUpdate(gestureRegions)
    }
  }

  object Provider {

    private var observerWeakRef = WeakReference<PanelsChildGestureRegionObserver>(null)

    // This is a lazily instantiated singleton. There is at most one instance of
    // PanelsChildGestureRegionObserver at a time. If an Activity creates this, all other calls to get()
    // from child views will return the same instance. If the Activity and all other references to this
    // get destroyed, then observerWeakRef will hold onto null, and the next call to get() will create
    // a new instance.
    @JvmStatic
    @UiThread
    fun get(): PanelsChildGestureRegionObserver {
      val previousObserver = observerWeakRef.get()

      return if (previousObserver == null) {
        val observer = PanelsChildGestureRegionObserver()
        observerWeakRef = WeakReference(observer)
        observer
      } else {
        previousObserver
      }
    }
  }
}
