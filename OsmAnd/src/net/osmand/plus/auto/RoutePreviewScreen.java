package net.osmand.plus.auto;

import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Distance;
import androidx.car.app.model.DistanceSpan;
import androidx.car.app.model.DurationSpan;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import net.osmand.data.ValueHolder;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.routing.IRouteInformationListener;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.search.listitems.QuickSearchListItem;
import net.osmand.plus.track.helpers.GPXInfo;
import net.osmand.plus.track.helpers.SelectedGpxFile;
import net.osmand.search.core.SearchResult;
import net.osmand.search.core.ObjectType;
import net.osmand.util.Algorithms;
import net.osmand.IndexConstants;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * The route preview screen for the app.
 */
public final class RoutePreviewScreen extends Screen implements IRouteInformationListener,
		DefaultLifecycleObserver {

	@NonNull
	private final Action settingsAction;
	@NonNull
	private final SurfaceRenderer surfaceRenderer;
	@NonNull
	private final SearchResult searchResult;
	@NonNull
	private List<Row> routeRows = new ArrayList<>();

	private boolean calculating;

	public RoutePreviewScreen(@NonNull CarContext carContext, @NonNull Action settingsAction,
	                          @NonNull SurfaceRenderer surfaceRenderer, @NonNull SearchResult searchResult) {
		super(carContext);
		this.settingsAction = settingsAction;
		this.surfaceRenderer = surfaceRenderer;
		this.searchResult = searchResult;

		getLifecycle().addObserver(this);

		calculating = true;
		if (searchResult.objectType == ObjectType.GPX_TRACK) {
			GPXInfo gpxInfo = ((GPXInfo) searchResult.relatedObject);
			File file = new File(getApp().getAppPath(IndexConstants.GPX_INDEX_DIR), gpxInfo.getFileName());
			SelectedGpxFile selectedGpxFile = getApp().getSelectedGpxHelper().getSelectedFileByPath(file.getAbsolutePath());
			getApp().getOsmandMap().getMapLayers().getMapControlsLayer().buildRouteByGivenGpx(selectedGpxFile.getGpxFile());
		} else {
			getApp().getOsmandMap().getMapLayers().getMapControlsLayer().replaceDestination(
					searchResult.location, QuickSearchListItem.getPointDescriptionObject(getApp(), searchResult).first);
		}
	}

	@NonNull
	public OsmandApplication getApp() {
		return (OsmandApplication) getCarContext().getApplicationContext();
	}

	@Override
	public void onCreate(@NonNull LifecycleOwner owner) {
		getApp().getRoutingHelper().addListener(this);
	}

	@Override
	public void onDestroy(@NonNull LifecycleOwner owner) {
		OsmandApplication app = getApp();
		RoutingHelper routingHelper = app.getRoutingHelper();
		routingHelper.removeListener(this);
		if (routingHelper.isRoutePlanningMode()) {
			app.stopNavigation();
		}
		getLifecycle().removeObserver(this);
	}

	@NonNull
	@Override
	public Template onGetTemplate() {
		ItemList.Builder listBuilder = new ItemList.Builder();
		listBuilder
				.setOnSelectedListener(this::onRouteSelected)
				.setOnItemsVisibilityChangedListener(this::onRoutesVisible);
		for (Row row : routeRows) {
			listBuilder.addItem(row);
		}
		RoutePreviewNavigationTemplate.Builder builder = new RoutePreviewNavigationTemplate.Builder();
		if (calculating) {
			builder.setLoading(true);
		} else {
			builder.setLoading(false);
			if (!Algorithms.isEmpty(routeRows)) {
				builder.setItemList(listBuilder.build());
			}
		}
		builder
				.setTitle(getCarContext().getString(R.string.current_route))
				.setActionStrip(new ActionStrip.Builder().addAction(settingsAction).build())
				.setHeaderAction(Action.BACK)
				.setNavigateAction(
						new Action.Builder()
								.setTitle(getApp().getString(R.string.shared_string_control_start))
								.setOnClickListener(this::onNavigate)
								.build());
		return builder.build();
	}

	private void onRouteSelected(int index) {
	}

	private void onRoutesVisible(int startIndex, int endIndex) {
	}

	private void onNavigate() {
		setResult(searchResult);
		finish();
	}

	@Override
	public void newRouteIsCalculated(boolean newRoute, ValueHolder<Boolean> showToast) {
		OsmandApplication app = getApp();
		RoutingHelper rh = app.getRoutingHelper();
		Distance distance = null;
		int leftTimeSec = 0;
		if (newRoute && rh.isRoutePlanningMode()) {
			distance = TripHelper.getDistance(app, rh.getLeftDistance());
			leftTimeSec = rh.getLeftTime();
		}
		if (distance != null && leftTimeSec > 0) {
			List<Row> routeRows = new ArrayList<>();
			SpannableString description = new SpannableString("  •  ");
			description.setSpan(DistanceSpan.create(distance), 0, 1, 0);
			description.setSpan(DurationSpan.create(leftTimeSec), 4, 5, 0);

			String name = QuickSearchListItem.getName(app, searchResult);
			String typeName = QuickSearchListItem.getTypeName(app, searchResult);
			String title = Algorithms.isEmpty(name) ? typeName : name;
			routeRows.add(new Row.Builder().setTitle(title).addText(description).build());
			this.routeRows = routeRows;
			calculating = false;
			invalidate();
		}
	}

	@Override
	public void routeWasCancelled() {
	}

	@Override
	public void routeWasFinished() {
	}
}
