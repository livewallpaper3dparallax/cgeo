package cgeo.geocaching.unifiedmap.geoitemlayer;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.models.geoitem.GeoGroup;
import cgeo.geocaching.models.geoitem.GeoIcon;
import cgeo.geocaching.models.geoitem.GeoItem;
import cgeo.geocaching.models.geoitem.GeoPrimitive;
import cgeo.geocaching.models.geoitem.GeoStyle;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.ui.TextParam;
import cgeo.geocaching.ui.dialog.SimpleDialog;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.CollectionStream;
import cgeo.geocaching.utils.ImageUtils;
import cgeo.geocaching.utils.Log;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.DrawableRes;
import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.disposables.Disposable;

/** This is a test layer to experiment with unified geoitem layers. It is only activated with a specific developer setting */
public class GeoItemTestLayer {

    private static final Geopoint CENTER = new Geopoint(40, 10);

    private GeoItemLayer<String> testLayer = new GeoItemLayer<>("test");
    private Disposable stopper;

    public void initforUnifiedMap(final GeoItemLayer<String> layer) {
        if (!Settings.enableFeatureUnifiedGeoItemLayer()) {
            return;
        }
        destroy();
        testLayer = layer;
        this.stopper = startData();
    }

    public void init(final IProviderGeoItemLayer<?> provider) {
        if (!Settings.enableFeatureUnifiedGeoItemLayer()) {
            return;
        }
        destroy();
        testLayer = new GeoItemLayer<>("test");
        testLayer.setProvider(provider, 0);
        this.stopper = startData();
    }

    public void destroy() {
        if (!Settings.enableFeatureUnifiedGeoItemLayer()) {
            return;
        }
        if (stopper != null) {
            stopper.dispose();
            stopper = null;
        }
        if (testLayer != null) {
            testLayer.destroy();
            testLayer = null;
        }
    }

    public static void handleTapTest(final GeoItemLayer<String> testLayer, final Context ctx, final Geopoint tapped, final boolean isLongTap) {
        if (!Settings.enableFeatureUnifiedGeoItemLayer() || testLayer == null) {
            return;
        }

        final Set<String> touched = testLayer.getTouched(tapped);
        if (touched.isEmpty()) {
            return;
        }

        final String message = CollectionStream.of(touched).map(s -> "* " + s + " (`" + testLayer.get(s) + "`)").toJoinedString("\n");

        if (ctx == null) {
            Log.w("Context is null! Wanted to display message: " + message);
        } else {
            SimpleDialog.ofContext(ctx).setTitle(TextParam.text("Touched items")).setMessage(TextParam.text(message).setMarkdown(true))
                    .show();
        }
    }

    private Disposable startData() {

        //static test data
        createStaticElements(CENTER.project(90, 50), testLayer);

        final int[] angleStore = { 0 };
        return AndroidRxUtils.runPeriodically(AndroidRxUtils.computationScheduler, () -> {
            final int angle = angleStore[0];
            angleStore[0] = (angleStore[0] + 20) % 360;

            createDynamicElements(CENTER, testLayer, angle);

            }, 0, 1000);
    }

    private static void createStaticElements(final Geopoint center, final GeoItemLayer<String> layer) {

        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                final Geopoint point = center.project(90, x * 20).project(180, y * 20);
                final GeoItem gi = testIconWithPoly(point, Color.GREEN, x * 0.5f, y * 0.5f, 0);
                layer.put("test-MarkerOffset-" + x + "-" + y, gi);
            }
        }

        layer.put("test-MarkerOffsetRightTop", testIconWithPoly(center.project(90, 100), Color.GRAY, GeoIcon.Hotspot.UPPER_RIGHT_CORNER.xAnchor, GeoIcon.Hotspot.UPPER_RIGHT_CORNER.yAnchor, 0));

        final Geopoint circleCenter = center.project(90, 200);
        for (int a = 0; a < 360; a += 40) {
            final Geopoint point = circleCenter.project(a, 40);
            final GeoItem gi = testIconWithPoly(point, Color.RED, GeoIcon.Hotspot.CENTER.xAnchor, GeoIcon.Hotspot.CENTER.yAnchor, a);
            layer.put("test-MarkerAngle-" + a, gi);
        }

        final Geopoint staticPolylineCenter = center.project(180, 50);
        layer.put("test-staticPolyline", polygonAround(staticPolylineCenter, 50, Color.RED));

        final Geopoint staticCircle = staticPolylineCenter.project(90, 50);
        layer.put("test-staticCircle", GeoPrimitive.createCircle(staticCircle, 40, GeoStyle.builder().setStrokeColor(Color.DKGRAY).setFillColor(Color.YELLOW).build()));

        final Geopoint staticPolygon = staticCircle.project(90, 50);
        layer.put("test-staticPolygon", GeoPrimitive.createPolygon(geoGridPoints(
                staticPolygon, 10, 0, 0, 3, 0, 3, 1, 2, 1, 2, 2, 3, 2, 3, 3, 1, 3, 0, 2
        ), GeoStyle.builder().setStrokeColor(Color.YELLOW).setStrokeWidth(5f).setFillColor(Color.GREEN).build()));

        final Geopoint staticPolWithHole = staticPolygon.project(90, 50);
        final GeoPrimitive pol = GeoPrimitive.builder().setType(GeoItem.GeoType.POLYGON)
                .setStyle(GeoStyle.builder().setStrokeColor(Color.YELLOW).setStrokeWidth(5f).setFillColor(Color.GREEN).build())
                .addPoints(geoGridPoints(staticPolWithHole, 10, 0, 0, 7, 0, 7, 7, 0, 7))
                .addHole(geoGridPoints(staticPolWithHole, 10, 1, 1, 1, 2, 2, 2, 2, 1))
                .addHole(geoGridPoints(staticPolWithHole, 10, 4, 4, 4, 5, 5, 5, 5, 4))
                .build();
        layer.put("test-staticPolygonWithTwoHoles", pol);

        final Geopoint zLevelStuff = staticPolygon.project(180, 50);
        final GeoPrimitive one = GeoPrimitive.createPolygon(geoGridPoints(zLevelStuff, 10, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0), GeoStyle.builder().setStrokeColor(Color.BLUE).setStrokeWidth(5f).setFillColor(Color.GREEN).build());
        final GeoPrimitive two = GeoPrimitive.createPolygon(geoGridPoints(zLevelStuff.project(110, 8), 10, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0), GeoStyle.builder().setStrokeColor(Color.BLUE).setStrokeWidth(5f).setFillColor(Color.YELLOW).build());
        final GeoPrimitive three = GeoPrimitive.createPolygon(geoGridPoints(zLevelStuff.project(140, 6), 10, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0), GeoStyle.builder().setStrokeColor(Color.BLUE).setStrokeWidth(5f).setFillColor(Color.RED).build());

        layer.put("test-zOne", one.buildUpon().setZLevel(0).build());
        layer.put("test-zTwo", two.buildUpon().setZLevel(2).build());
        layer.put("test-zThree", three.buildUpon().setZLevel(1).build());


    }

    private static GeoGroup testIconWithPoly(final Geopoint point, final int polyColor, final float xAnchor, final float yAnchor, final float angle) {


        final GeoItem poly = polygonAround(point, 20, polyColor);
        final GeoItem marker = GeoPrimitive.createMarker(point,
                GeoIcon.builder()
                        .setXAnchor(xAnchor)
                        .setYAnchor(yAnchor)
                        .setRotation(angle)
                        .setBitmap(createBitmap(R.drawable.cgeo_borderless)).build());

        return GeoGroup.builder().addItems(poly, marker).build();

    }

    private static GeoPrimitive polygonAround(final Geopoint center, final float distance, final int color) {
        final Geopoint start = center.project(0, distance / 2).project(270, distance / 2);
        return GeoPrimitive.builder().setType(GeoItem.GeoType.POLYLINE).addPoints(
                start,
                start.project(90, distance),
                start.project(90, distance).project(180, distance),
                start.project(180, distance),
                start).setStyle(GeoStyle.builder().setStrokeColor(color).build()).build();
    }

    private static List<Geopoint> geoGridPoints(final Geopoint start, final float dist, final int ... coords) {
        final List<Geopoint> result = new ArrayList<>();
        for (int i = 0; i < coords.length; i += 2) {
            final int x = coords[i];
            final int y = coords[i + 1];
            result.add(start.project(90, x * dist).project(180, y * dist));
        }
        return result;
    }


    private static void createDynamicElements(final Geopoint center, final GeoItemLayer<String> layer, final int angle) {
        final double distance = 20; //km

        //dynamic test data
        final GeoItem poly = GeoPrimitive.builder().setType(GeoItem.GeoType.POLYLINE).addPoints(
                center.project(angle, distance),
                center.project((angle + 90) % 360, distance),
                center.project((angle + 180) % 360, distance),
                center.project((angle + 270) % 360, distance),
                center.project(angle, distance)).build();
        final GeoItem point = GeoPrimitive.createPoint(center.project(angle, distance), GeoStyle.builder().setStrokeColor(Color.GREEN).build());
        final GeoItem group = GeoGroup.builder().addItems(point, poly).build();

        layer.put("test-Quad", group);

        final GeoItem flowMarker = GeoPrimitive.createMarker(center.project(angle, distance * 0.8), GeoIcon.builder()
                .setBitmap(createBitmap(R.drawable.type_event))
                .setRotation(angle).build());
        layer.put("test-FlowMarker", flowMarker);

    }

    private static Bitmap createBitmap(@DrawableRes final int drawableId) {
        return ImageUtils.convertToBitmap(ResourcesCompat.getDrawable(CgeoApplication.getInstance().getResources(), drawableId, null));

    }

}
