package com.matejdr.admanager;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.facebook.react.views.view.ReactViewGroup;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.ads.mediation.facebook.FacebookMediationAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerAdView;
import com.google.android.gms.ads.admanager.AppEventListener;
import com.google.android.gms.ads.formats.OnAdManagerAdViewLoadedListener;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAd.OnNativeAdLoadedListener;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.nativead.NativeCustomFormatAd;
import com.google.android.gms.ads.nativead.NativeCustomFormatAd.OnCustomFormatAdLoadedListener;
import com.matejdr.admanager.customClasses.CustomTargeting;
import com.matejdr.admanager.utils.Targeting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.bumptech.glide.Glide;

public class NativeAdViewContainer extends ReactViewGroup implements AppEventListener,
    LifecycleEventListener, OnNativeAdLoadedListener,
    OnAdManagerAdViewLoadedListener, OnCustomFormatAdLoadedListener {
    public static final String AD_TYPE_BANNER = "banner";
    public static final String AD_TYPE_NATIVE = "native";
    public static final String AD_TYPE_TEMPLATE = "template";
    /**
     * @{RCTEventEmitter} instance used for sending events back to JS
     **/
    private final RCTEventEmitter mEventEmitter;
    protected AdLoader adLoader;
    protected ReactApplicationContext applicationContext;
    protected NativeAdView nativeAdView;
    protected AdManagerAdView adManagerAdView;
    protected NativeCustomFormatAd nativeCustomTemplateAd;
    protected String nativeCustomTemplateAdClickableAsset;
    protected ThemedReactContext context;
    String[] testDevices;
    String adUnitID;
    AdSize[] validAdSizes;
    AdSize adSize;
    String[] customTemplateIds;
    String[] validAdTypes = new String[]{AD_TYPE_BANNER, AD_TYPE_NATIVE, AD_TYPE_TEMPLATE};
    // Targeting
    Boolean hasTargeting = false;
    CustomTargeting[] customTargeting;
    String[] categoryExclusions;
    String[] keywords;
    String content_url;
    String publisherProvidedID;
    Location location;
    String correlator;
    List<String> customClickTemplateIds;
    String adTheme = "dark";

    NativeAd _nativeAd;

    /**
     * Creates new NativeAdView instance and retrieves event emitter
     *
     * @param context
     */
    public NativeAdViewContainer(ThemedReactContext context, ReactApplicationContext applicationContext) {
        super(context);
        this.context = context;
        this.applicationContext = applicationContext;
        this.applicationContext.addLifecycleEventListener(this);

        this.nativeAdView = new NativeAdView(context);
        this.adManagerAdView = new AdManagerAdView(context);

        mEventEmitter = context.getJSModule(RCTEventEmitter.class);
    }

    private boolean isFluid() {
        return AdSize.FLUID.equals(this.adSize);
    }

    public void loadAd(RNAdManageNativeManager.AdsManagerProperties adsManagerProperties) {
        this.testDevices = adsManagerProperties.getTestDevices();
        this.adUnitID = adsManagerProperties.getAdUnitID();
    }

    private void setupAdLoader() {
        if (adLoader != null) {
            return;
        }

        final ReactApplicationContext reactContext = this.applicationContext;

        VideoOptions videoOptions = new VideoOptions.Builder()
            .setStartMuted(true)
            .build();

        NativeAdOptions adOptions = new NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .build();

        ArrayList<AdSize> adSizes = new ArrayList<AdSize>();
        if (adSize != null) {
            adSizes.add(adSize);
        }
        if (validAdSizes != null) {
            for (int i = 0; i < validAdSizes.length; i++) {
                if (!adSizes.contains(validAdSizes[i])) {
                    adSizes.add(validAdSizes[i]);
                }
            }
        }

        if (adSizes.size() == 0) {
            adSizes.add(AdSize.BANNER);
        }

        AdSize[] adSizesArray = adSizes.toArray(new AdSize[adSizes.size()]);

        List<String> validAdTypesList = Arrays.asList(validAdTypes);

        Log.e("validAdTypes", validAdTypesList.toString());
        AdLoader.Builder builder = new AdLoader.Builder(reactContext, adUnitID);
        if (validAdTypesList.contains(AD_TYPE_NATIVE)) {
            Log.e("validAdTypes", AD_TYPE_NATIVE);
            builder.forNativeAd(NativeAdViewContainer.this);
        }
        if (adSizesArray.length > 0 && validAdTypesList.contains(AD_TYPE_BANNER)) {
            Log.e("validAdTypes", AD_TYPE_BANNER);
            builder.forAdManagerAdView(NativeAdViewContainer.this, adSizesArray);
        }
        if (customTemplateIds != null && customTemplateIds.length > 0 && validAdTypesList.contains(AD_TYPE_TEMPLATE)) {
            Log.e("validAdTypes", AD_TYPE_TEMPLATE);
            for (int i = 0; i < customTemplateIds.length; i++) {
                String curCustomTemplateID = customTemplateIds[i];
                if (!curCustomTemplateID.isEmpty()) {
                    if (customClickTemplateIds != null && customClickTemplateIds.contains(curCustomTemplateID)) {
                        builder.forCustomFormatAd(curCustomTemplateID,
                            NativeAdViewContainer.this,
                            new NativeCustomFormatAd.OnCustomClickListener() {
                                @Override
                                public void onCustomClick(NativeCustomFormatAd ad, String assetName) {
                                    WritableMap customClick = Arguments.createMap();
                                    customClick.putString("assetName", assetName);
                                    for (String adAssetName : ad.getAvailableAssetNames()) {
                                        if (ad.getText(adAssetName) != null) {
                                            customClick.putString(adAssetName, ad.getText(adAssetName).toString());
                                        }
                                    }
                                    sendEvent(RNAdManagerNativeViewManager.EVENT_AD_CUSTOM_CLICK, customClick);
                                }
                            });
                    } else {
                        builder.forCustomFormatAd(curCustomTemplateID, NativeAdViewContainer.this, null);
                    }
                }
            }
        }
        builder.withAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(LoadAdError adError) {
                String errorMessage = "Unknown error";
                switch (adError.getCode()) {
                    case AdManagerAdRequest.ERROR_CODE_INTERNAL_ERROR:
                        errorMessage = "Internal error, an invalid response was received from the ad server.";
                        break;
                    case AdManagerAdRequest.ERROR_CODE_INVALID_REQUEST:
                        errorMessage = "Invalid ad request, possibly an incorrect ad unit ID was given.";
                        break;
                    case AdManagerAdRequest.ERROR_CODE_NETWORK_ERROR:
                        errorMessage = "The ad request was unsuccessful due to network connectivity.";
                        break;
                    case AdManagerAdRequest.ERROR_CODE_NO_FILL:
                        errorMessage = "The ad request was successful, but no ad was returned due to lack of ad inventory.";
                        break;
                }
                WritableMap event = Arguments.createMap();
                WritableMap error = Arguments.createMap();
                error.putString("message", errorMessage);
                event.putMap("error", error);
                sendEvent(RNAdManagerNativeViewManager.EVENT_AD_FAILED_TO_LOAD, event);
            }

            @Override
            public void onAdLoaded() {
                // sendEvent(RNAdManagerNativeViewManager.EVENT_AD_LOADED, null);
            }

            @Override
            public void onAdClicked() {
                super.onAdClicked();
                sendEvent(RNAdManagerNativeViewManager.EVENT_AD_CLICKED, null);
            }

            @Override
            public void onAdOpened() {
                WritableMap event = Arguments.createMap();
                sendEvent(RNAdManagerNativeViewManager.EVENT_AD_OPENED, event);
            }

            @Override
            public void onAdClosed() {
                WritableMap event = Arguments.createMap();
                sendEvent(RNAdManagerNativeViewManager.EVENT_AD_CLOSED, event);
            }
        }).withNativeAdOptions(adOptions);

        adLoader = builder.build();
    }

    public void reloadAd() {
        this.setupAdLoader();

        if (adLoader != null) {
            UiThreadUtil.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AdManagerAdRequest.Builder adRequestBuilder = new AdManagerAdRequest.Builder();

                    List<String> testDevicesList = new ArrayList<>();
                    if (testDevices != null && testDevices.length > 0) {
                        for (int i = 0; i < testDevices.length; i++) {
                            String testDevice = testDevices[i];
                            if (testDevice == "SIMULATOR") {
                                testDevice = AdManagerAdRequest.DEVICE_ID_EMULATOR;
                            }
                            testDevicesList.add(testDevice);
                        }
                        RequestConfiguration requestConfiguration
                            = new RequestConfiguration.Builder()
                            .setTestDeviceIds(testDevicesList)
                            .build();
                        MobileAds.setRequestConfiguration(requestConfiguration);
                    }


                    if (correlator == null) {
                        correlator = (String) Targeting.getCorelator(adUnitID);
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("correlator", correlator);

                    adRequestBuilder.addNetworkExtrasBundle(AdMobAdapter.class, bundle);
                    adRequestBuilder.addNetworkExtrasBundle(FacebookMediationAdapter.class, bundle);

                    // Targeting
                    if (hasTargeting) {
                        if (customTargeting != null && customTargeting.length > 0) {
                            for (int i = 0; i < customTargeting.length; i++) {
                                String key = customTargeting[i].key;
                                if (!key.isEmpty()) {
                                    if (customTargeting[i].value != null && !customTargeting[i].value.isEmpty()) {
                                        adRequestBuilder.addCustomTargeting(key, customTargeting[i].value);
                                    } else if (customTargeting[i].values != null && !customTargeting[i].values.isEmpty()) {
                                        adRequestBuilder.addCustomTargeting(key, customTargeting[i].values);
                                    }
                                }
                            }
                        }
                        if (categoryExclusions != null && categoryExclusions.length > 0) {
                            for (int i = 0; i < categoryExclusions.length; i++) {
                                String categoryExclusion = categoryExclusions[i];
                                if (!categoryExclusion.isEmpty()) {
                                    adRequestBuilder.addCategoryExclusion(categoryExclusion);
                                }
                            }
                        }
                        if (keywords != null && keywords.length > 0) {
                            for (int i = 0; i < keywords.length; i++) {
                                String keyword = keywords[i];
                                if (!keyword.isEmpty()) {
                                    adRequestBuilder.addKeyword(keyword);
                                }
                            }
                        }
                        if (content_url != null) {
                            adRequestBuilder.setContentUrl(content_url);
                        }
                        if (publisherProvidedID != null) {
                            adRequestBuilder.setPublisherProvidedId(publisherProvidedID);
                        }
                        if (location != null) {
                            // adRequestBuilder.setLocation(location);
                        }
                    }

                    AdManagerAdRequest adRequest = adRequestBuilder.build();
                    if (adLoader != null) {
                        adLoader.loadAd(adRequest);
                    }
                }
            });
        }
    }

    public void registerViewsForInteraction(List<View> clickableViews) {
        if (nativeCustomTemplateAd != null && nativeCustomTemplateAdClickableAsset != null) {
            try {
                for (View view : clickableViews) {
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            nativeCustomTemplateAd.performClick(nativeCustomTemplateAdClickableAsset);
                        }
                    });
                }
            } catch (Exception e) {
            }
        } else if (nativeAdView != null) {
            int viewWidth = this.getMeasuredWidth();
            int viewHeight = this.getMeasuredHeight();

            int left = 0;
            int top = 0;

            if (viewHeight <= 0) {
                viewHeight = 1500;
            }
            Log.i("NativeAd", "w:"+viewWidth+" h:"+viewHeight);

            View frame = new View(context);
            frame.layout(0, 0, viewWidth, viewHeight);
            nativeAdView.addView(frame);
            frame.getLayoutParams().width = viewWidth;
            frame.getLayoutParams().height = viewHeight;

            nativeAdView.getLayoutParams().width = viewWidth;
            nativeAdView.getLayoutParams().height = viewHeight;

            nativeAdView.measure(viewWidth, viewHeight);
            nativeAdView.layout(left, top, left + viewWidth, top + viewHeight);

            if (this.adUnitID.indexOf("Feed") < 0) {
                bannerView(viewWidth, viewHeight);

                frame.setBackgroundColor(0xFFF8F9FA);
            } else {
                feedView(viewWidth, viewHeight);

                int backgroundColor =
                    this.adTheme.equals("light") ?
                    0xFFF8F9FA : 0xFF212529;
                frame.setBackgroundColor(backgroundColor);
            }

            nativeAdView.layout(left, top, left + viewWidth, top + viewHeight);
        }
    }

    private void bannerView(int viewWidth, int viewHeight) {
        int left = 0;
        int top = 0;
        int midWidth = viewWidth / 2;

        TextView advertiserView = new TextView(context);
        advertiserView.setText(_nativeAd.getAdvertiser());
        advertiserView.setMaxLines(1);
        advertiserView.setEllipsize(TextUtils.TruncateAt.END);
        advertiserView.setTextColor(0xff000000);
        advertiserView.setTextSize(10.5f);
        advertiserView.setPadding(16, 0, 0, 0);
        advertiserView.layout(midWidth + 40, 20, viewWidth - 40, 65);
        nativeAdView.addView(advertiserView);
        nativeAdView.setAdvertiserView(advertiserView);

        TextView titleView = new TextView(context);
        titleView.setText(_nativeAd.getHeadline());
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextColor(0xff000000);
        titleView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titleView.setGravity(Gravity.LEFT);
        titleView.setPadding(0, 0, 0, 0);
        titleView.layout(midWidth, 65, viewWidth, 190);
        nativeAdView.addView(titleView);
        nativeAdView.setHeadlineView(titleView);

        TextView ctaView = new TextView(context);
        String cta = _nativeAd.getCallToAction();
        if (cta == null || cta.equals("")) {
            cta = "Learn More";
        }
        ctaView.setText(cta);
        ctaView.setTextColor(0xff000000);
        ctaView.layout(midWidth, top + viewHeight - 108, left + viewWidth - 20, top + viewHeight - 20);
        ctaView.setGravity(Gravity.CENTER);
        nativeAdView.addView(ctaView);
        nativeAdView.setCallToActionView(ctaView);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(44);
        drawable.setStroke(1, Color.BLACK);
        ctaView.setBackground(drawable);
        ctaView.setPadding(0, 15, 0, 10);

        NativeAd.Image icon = _nativeAd.getIcon();
        if (icon != null && icon.getUri() != null) {
            ImageView iconView = new ImageView(context);
            int iconLeft = midWidth;
            int iconTop = 20;
            int iconWidth = 40;
            int iconHeight = 45;
            iconView.setPadding(4, 6, 4, 7);
            iconView.layout(iconLeft, iconTop, iconLeft + iconWidth, iconTop + iconHeight);
            nativeAdView.addView(iconView);
            nativeAdView.setIconView(iconView);

            try {
                Glide.with(context)
                    .load(icon.getUri())
                    .into(iconView);
            } catch (Exception e) {
                Log.e("NativeAd", e.toString());
            }
        }
        if (_nativeAd.getImages().size() != 0) {
            NativeAd.Image image = _nativeAd.getImages().get(0);
            if (image != null && image.getUri() != null) {
                ImageView imageView = new ImageView(context);
                imageView.layout(0, 20, midWidth - 30, viewHeight - 20);

                nativeAdView.addView(imageView);
                nativeAdView.setImageView(imageView);

                try {
                    Glide.with(context)
                        .load(image.getUri())
                        .into(imageView);
                } catch (Exception e) {
                    Log.e("NativeAd", e.toString());
                }
            }
        }

        TextView textView = new TextView(context);
        textView.setText("Ad");
        textView.setTextSize(11);
        textView.setTextColor(0xFF868E96);
        textView.setGravity(Gravity.CENTER);
        textView.layout(0, viewHeight - 36, 50, viewHeight);
        nativeAdView.addView(textView);
        textView.setBackgroundColor(0xFFFFFFFF);
    }

    private void feedView(int viewWidth, int viewHeight) {
        int left = 0;
        int top = 0;
        int textColor = this.adTheme.equals("light") ? 0xFF000000 : 0xFFFFFFFF;

        NativeAd.Image icon = _nativeAd.getIcon();
        if (icon != null && icon.getUri() != null) {
            ImageView iconView = new ImageView(context);
            int iconLeft = left+20;
            int iconTop = 20;
            int iconWidth = 80;
            int iconHeight = 80;
            iconView.layout(iconLeft, iconTop, iconLeft + iconWidth, iconTop + iconHeight);
            nativeAdView.addView(iconView);
            nativeAdView.setIconView(iconView);

            try {
                Glide.with(context)
                    .load(icon.getUri())
                    .into(iconView);
            } catch (Exception e) {
                Log.e("NativeAd", e.toString());
            }
        }

        TextView advertiserView = new TextView(context);
        advertiserView.setText(_nativeAd.getAdvertiser());
        advertiserView.setMaxLines(1);
        advertiserView.setEllipsize(TextUtils.TruncateAt.END);
        advertiserView.setTextColor(textColor);
        advertiserView.setTextSize(18);
        advertiserView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        advertiserView.setPadding(20, 0, 0, 0);
        advertiserView.layout(100, 20, viewWidth - 100, 100);
        nativeAdView.addView(advertiserView);
        nativeAdView.setAdvertiserView(advertiserView);

        TextView titleView = new TextView(context);
        titleView.setText(_nativeAd.getHeadline());
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextSize(16);
        titleView.setTextColor(textColor);
        titleView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titleView.setGravity(Gravity.LEFT);
        titleView.setPadding(20, 0, 20, 0);
        titleView.layout(left, 115, viewWidth, 235);
        nativeAdView.addView(titleView);
        nativeAdView.setHeadlineView(titleView);

        TextView bodyView = new TextView(context);
        bodyView.setText(_nativeAd.getBody());
        bodyView.setMaxLines(2);
        bodyView.setTextSize(14);
        bodyView.setTextColor(textColor);
        bodyView.setGravity(Gravity.LEFT);
        bodyView.setPadding(20, 0, 20, 0);
        bodyView.layout(left, 235, viewWidth, 360);
        nativeAdView.addView(bodyView);
        nativeAdView.setBodyView(bodyView);

        if (_nativeAd.getImages().size() != 0) {
            NativeAd.Image image = _nativeAd.getImages().get(0);
            if (image != null && image.getUri() != null) {
                ImageView imageView = new ImageView(context);
                imageView.layout(0, 360, viewWidth, top + viewHeight - 210);
                imageView.setBackgroundColor(0xFFFFFFFF);

                nativeAdView.addView(imageView);
                nativeAdView.setImageView(imageView);

                try {
                    Glide.with(context)
                        .load(image.getUri())
                        .into(imageView);
                } catch (Exception e) {
                    Log.e("NativeAd", e.toString());
                }
            }
        }

        TextView ctaView = new TextView(context);
        String cta = _nativeAd.getCallToAction();
        if (cta == null || cta.equals("")) {
            cta = "Learn More";
        }
        ctaView.setText(cta);
        ctaView.setTextSize(18);
        ctaView.setTextColor(0xFFFFFFFF);
        ctaView.layout(left + 50, top + viewHeight - 175, left + viewWidth - 50, top + viewHeight - 35);
        ctaView.setGravity(Gravity.CENTER);
        nativeAdView.addView(ctaView);
        nativeAdView.setCallToActionView(ctaView);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(0xFFFF477F);
        drawable.setCornerRadius(20);
        // drawable.setStroke(1, Color.BLACK);
        ctaView.setBackground(drawable);
        ctaView.setPadding(0, 30, 0, 10);

        TextView textView = new TextView(context);
        textView.setText("Ad");
        textView.setTextSize(11);
        textView.setTextColor(0xFF868E96);
        textView.setGravity(Gravity.CENTER);
        textView.layout(0, top + viewHeight - 246, 50, top + viewHeight - 210);
        nativeAdView.addView(textView);
        textView.setBackgroundColor(0xFFFFFFFF);
    }

    @Override
    public void onNativeAdLoaded(NativeAd nativeAd) {
        nativeAdView.setNativeAd(nativeAd);
        removeAllViews();
        addView(nativeAdView);

        setNativeAd(nativeAd);
    }

    @Override
    public void onAdManagerAdViewLoaded(AdManagerAdView adView) {
        this.adManagerAdView = adView;
        removeAllViews();
        this.addView(adView);
        if (adView == null) {
            WritableMap event = Arguments.createMap();
            sendEvent(RNAdManagerNativeViewManager.EVENT_AD_LOADED, event);
            return;
        }

        int width, height, left, top;

        if (isFluid()) {
            AdManagerAdView.LayoutParams layoutParams = new AdManagerAdView.LayoutParams(
                ReactViewGroup.LayoutParams.MATCH_PARENT,
                ReactViewGroup.LayoutParams.WRAP_CONTENT
            );
            adView.setLayoutParams(layoutParams);

            top = 0;
            left = 0;
            width = getWidth();
            height = getHeight();
        } else {
            top = adView.getTop();
            left = adView.getLeft();
            width = adView.getAdSize().getWidthInPixels(context);
            height = adView.getAdSize().getHeightInPixels(context);
        }

        if (isFluid()) {
            adView.measure(
                MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY)
            );
        } else {
            adView.measure(width, height);
        }
        adView.layout(left, top, left + width, top + height);

        if (!isFluid()) {
            sendOnSizeChangeEvent(adView);
        }
        WritableMap ad = Arguments.createMap();
        ad.putString("type", AD_TYPE_BANNER);

        WritableMap gadSize = Arguments.createMap();
        gadSize.putString("adSize", adView.getAdSize().toString());
        gadSize.putDouble("width", adView.getAdSize().getWidth());
        gadSize.putDouble("height", adView.getAdSize().getHeight());
        ad.putMap("gadSize", gadSize);

        ad.putString("isFluid", String.valueOf(isFluid()));

        WritableMap measurements = Arguments.createMap();
        measurements.putInt("adWidth", width);
        measurements.putInt("adHeight", height);
        measurements.putInt("width", getMeasuredWidth());
        measurements.putInt("height", getMeasuredHeight());
        measurements.putInt("left", left);
        measurements.putInt("top", top);
        ad.putMap("measurements", measurements);

        sendEvent(RNAdManagerNativeViewManager.EVENT_AD_LOADED, ad);
    }

    @Override
    public void onCustomFormatAdLoaded(NativeCustomFormatAd nativeCustomTemplateAd) {
        this.nativeCustomTemplateAd = nativeCustomTemplateAd;
        removeAllViews();

        setNativeAd(nativeCustomTemplateAd);
    }

    /**
     * Called by the view manager when ad is loaded. Sends serialised
     * version of a native ad back to Javascript.
     *
     * @param nativeCustomTemplateAd
     */
    private void setNativeAd(NativeCustomFormatAd nativeCustomTemplateAd) {
        if (nativeCustomTemplateAd == null) {
            WritableMap event = Arguments.createMap();
            sendEvent(RNAdManagerNativeViewManager.EVENT_AD_LOADED, event);
            return;
        }

        WritableMap ad = Arguments.createMap();
        ad.putString("type", AD_TYPE_TEMPLATE);
        ad.putString("templateID", nativeCustomTemplateAd.getCustomFormatId());
        for (String assetName : nativeCustomTemplateAd.getAvailableAssetNames()) {
            if (nativeCustomTemplateAd.getText(assetName) != null) {
                if (nativeCustomTemplateAdClickableAsset == null && nativeCustomTemplateAd.getText(assetName).length() > 0) {
                    nativeCustomTemplateAdClickableAsset = assetName;
                }
                ad.putString(assetName, nativeCustomTemplateAd.getText(assetName).toString());
            } else if (nativeCustomTemplateAd.getImage(assetName) != null) {
                WritableMap imageMap = Arguments.createMap();
                imageMap.putString("uri", nativeCustomTemplateAd.getImage(assetName).getUri().toString());
                imageMap.putInt("width", nativeCustomTemplateAd.getImage(assetName).getDrawable().getIntrinsicWidth());
                imageMap.putInt("height", nativeCustomTemplateAd.getImage(assetName).getDrawable().getIntrinsicHeight());
                imageMap.putDouble("scale", nativeCustomTemplateAd.getImage(assetName).getScale());
                ad.putMap(assetName, imageMap);
            }
        }

        sendEvent(RNAdManagerNativeViewManager.EVENT_AD_LOADED, ad);

        nativeCustomTemplateAd.recordImpression();
    }

    private void setNativeAd(NativeAd nativeAd) {
        if (nativeAd == null) {
            WritableMap event = Arguments.createMap();
            sendEvent(RNAdManagerNativeViewManager.EVENT_AD_LOADED, event);
            return;
        }
        _nativeAd = nativeAd;

        WritableMap ad = Arguments.createMap();
        ad.putString("type", AD_TYPE_NATIVE);
        if (nativeAd.getHeadline() == null) {
            ad.putString("headline", null);
        } else {
            ad.putString("headline", nativeAd.getHeadline());
        }

        if (nativeAd.getBody() == null) {
            ad.putString("bodyText", null);
        } else {
            ad.putString("bodyText", nativeAd.getBody());
        }

        if (nativeAd.getCallToAction() == null) {
            ad.putString("callToActionText", null);
        } else {
            ad.putString("callToActionText", nativeAd.getCallToAction());
        }

        if (nativeAd.getAdvertiser() == null) {
            ad.putString("advertiserName", null);
        } else {
            ad.putString("advertiserName", nativeAd.getAdvertiser());
        }

        if (nativeAd.getStarRating() == null) {
            ad.putString("starRating", null);
        } else {
            ad.putDouble("starRating", nativeAd.getStarRating());
        }

        if (nativeAd.getStore() == null) {
            ad.putString("storeName", null);
        } else {
            ad.putString("storeName", nativeAd.getStore());
        }

        if (nativeAd.getPrice() == null) {
            ad.putString("price", null);
        } else {
            ad.putString("price", nativeAd.getPrice());
        }

        if (nativeAd.getIcon() == null) {
            ad.putString("icon", null);
        } else {
            WritableMap icon = Arguments.createMap();
            try {
                icon.putString("uri", nativeAd.getIcon().getUri().toString());
                icon.putInt("width", nativeAd.getIcon().getDrawable().getIntrinsicWidth());
                icon.putInt("height", nativeAd.getIcon().getDrawable().getIntrinsicHeight());
                icon.putDouble("scale", nativeAd.getIcon().getScale());
                ad.putMap("icon", icon);
            } catch (Exception e) {
                Log.e("NativeAd", e.toString());
            }
        }

        if (nativeAd.getImages() == null || nativeAd.getImages().size() == 0) {
            ad.putArray("images", null);
        } else {
            WritableArray images = Arguments.createArray();
            for (NativeAd.Image image : nativeAd.getImages()) {
                try {
                    WritableMap imageMap = Arguments.createMap();
                    imageMap.putString("uri", image.getUri().toString());
                    imageMap.putDouble("scale", image.getScale());
                    images.pushMap(imageMap);
                } catch (Exception e) {
                    Log.e("NativeAd", e.toString());
                }
            }
            ad.putArray("images", images);
        }

        Bundle extras = nativeAd.getExtras();
        if (extras.containsKey(FacebookMediationAdapter.KEY_SOCIAL_CONTEXT_ASSET)) {
            String socialContext = (String) extras.get(FacebookMediationAdapter.KEY_SOCIAL_CONTEXT_ASSET);
            ad.putString("socialContext", socialContext);
        }

        sendEvent(RNAdManagerNativeViewManager.EVENT_AD_LOADED, ad);
    }


    private void sendOnSizeChangeEvent(AdManagerAdView adView) {
        int width;
        int height;
        ReactContext reactContext = (ReactContext) getContext();
        WritableMap event = Arguments.createMap();
        AdSize adSize = adView.getAdSize();
        width = adSize.getWidth();
        height = adSize.getHeight();
        event.putString("type", "banner");
        event.putDouble("width", width);
        event.putDouble("height", height);
        sendEvent(RNAdManagerNativeViewManager.EVENT_SIZE_CHANGE, event);
    }

    private void sendEvent(String name, @Nullable WritableMap event) {
        mEventEmitter.receiveEvent(getId(), name, event);
    }

    public void setCustomTemplateIds(String[] customTemplateIds) {
        this.customTemplateIds = customTemplateIds;
    }

    public void setAdSize(AdSize adSize) {
        this.adSize = adSize;
    }

    public void setAdTheme(String theme) {
        if (theme != null) {
            this.adTheme = theme;
        }
    }

    public void setValidAdSizes(AdSize[] adSizes) {
        this.validAdSizes = adSizes;
    }

    public void setValidAdTypes(String[] adTypes) {
        Log.e("validAdTypes_s", adTypes.toString());
        this.validAdTypes = adTypes;
    }

    // Targeting
    public void setCustomTargeting(CustomTargeting[] customTargeting) {
        this.customTargeting = customTargeting;
    }

    public void setCategoryExclusions(String[] categoryExclusions) {
        this.categoryExclusions = categoryExclusions;
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords;
    }

    public void setContentURL(String content_url) {
        this.content_url = content_url;
    }

    public void setPublisherProvidedID(String publisherProvidedID) {
        this.publisherProvidedID = publisherProvidedID;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setCorrelator(String correlator) {
        this.correlator = correlator;
    }

    public void setCustomClickTemplateIds(String[] customClickTemplateIds) {
        this.customClickTemplateIds = Arrays.asList(customClickTemplateIds);
    }

    @Override
    public void onAppEvent(String name, String info) {
        WritableMap event = Arguments.createMap();
        event.putString("name", name);
        event.putString("info", info);
        sendEvent(RNAdManagerNativeViewManager.EVENT_APP_EVENT, event);
    }

    @Override
    public void onHostResume() {
        if (this.adManagerAdView != null) {
            this.adManagerAdView.resume();
        }
    }

    @Override
    public void onHostPause() {
        if (this.adManagerAdView != null) {
            this.adManagerAdView.pause();
        }
    }

    @Override
    public void onHostDestroy() {
        if (this.nativeAdView != null) {
            this.nativeAdView.destroy();
        }
        if (this.adManagerAdView != null) {
            this.adManagerAdView.destroy();
        }
        if (this.nativeCustomTemplateAd != null) {
            this.nativeCustomTemplateAd.destroy();
        }
        if (this.nativeCustomTemplateAd != null) {
            this.nativeCustomTemplateAd.destroy();
        }
        if (this.adLoader != null) {
            this.adLoader = null;
        }
    }
}
