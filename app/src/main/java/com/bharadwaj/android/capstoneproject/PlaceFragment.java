package com.bharadwaj.android.capstoneproject;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bharadwaj.android.capstoneproject.adapters.PlacesAdapter;
import com.bharadwaj.android.capstoneproject.api_utils.GooglePlacesAPIResponse;
import com.bharadwaj.android.capstoneproject.constants.Constants;
import com.bharadwaj.android.capstoneproject.favorites.FavoriteAsyncTaskLoader;
import com.bharadwaj.android.capstoneproject.network.NetworkUtils;
import com.bharadwaj.android.capstoneproject.utils.ExtractionUtils;
import com.bharadwaj.android.capstoneproject.utils.CustomPlace;
import com.bharadwaj.android.capstoneproject.widget.UpdatePlacesWidgetService;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBufferResponse;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBufferResponse;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

import static com.bharadwaj.android.capstoneproject.adapters.PlacesAdapter.FAVORITES_LOADER_ID;
import static com.bharadwaj.android.capstoneproject.favorites.FavoriteContract.Favorites;

public class PlaceFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>{

    // TODO: Customize parameter argument names
    private static final String ARG_COLUMN_COUNT = "column-count";
    // TODO: Customize parameters
    private int mColumnCount = 1;
    private String mPlaceType = "";

    PlacesAdapter placeAdapter;
    Parcelable layoutManagerSavedState;
    List<CustomPlace> placesArray;
    Cursor saveCursor;

    boolean mConfigurationChanged = false;
    boolean mLocationPermissionGranted = false;

    @BindView(R.id.noPlacesExplanationView)
    TextView mNoPlacesExplanationView;
    @BindView(R.id.progress_bar)
    ProgressBar mProgressBar;
    @BindView(R.id.placeList)
    RecyclerView mPlacesRecyclerView;
    @BindView(R.id.adView)
    AdView mAdView;


    ItemTouchHelper.SimpleCallback itemTouchHelperCallback;

    public PlaceFragment() {
    }

    public static PlaceFragment newInstance(String typeOfPlaces) {
        PlaceFragment fragment = new PlaceFragment();
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TYPE_OF_PLACE, typeOfPlaces);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.v("Entering onCreate");

        setRetainInstance(true);

        Timber.v("Leaving onCreate");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Timber.v("Entering onCreateView (Context : " + getActivity() + " )" );
        View view = inflater.inflate(R.layout.fragment_place_list, container, false);
        ButterKnife.bind(this, view);

        if(savedInstanceState != null){
            mConfigurationChanged = savedInstanceState.getBoolean(Constants.CONFIGURATION_CHANGED);
            Timber.v("Retrieved configurationChanged : %s", mConfigurationChanged);

            if(savedInstanceState.containsKey(Constants.SAVED_PLACES_ARRAY)){
                placesArray = Parcels.unwrap(savedInstanceState.getParcelable(Constants.SAVED_PLACES_ARRAY));
            }

        }

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
            if(getArguments().containsKey(Constants.TYPE_OF_PLACE)){
                mPlaceType = getArguments().getString(Constants.TYPE_OF_PLACE);
            }
        }

        if (mColumnCount <= 1) {
            mPlacesRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        } else {
            mPlacesRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), mColumnCount));
        }
        placeAdapter = new PlacesAdapter(getActivity());
        mPlacesRecyclerView.setAdapter(placeAdapter);

        if(placeAdapter.isFavoriteContext()){
            mProgressBar.setVisibility(View.GONE);
            showFavoritePlaces();
        }else{
            showProgressBarAndHideRecyclerView();
            if(!NetworkUtils.isConnectedToInternet(getActivity())){
                Toast.makeText(getActivity(), getString(R.string.not_connected), Toast.LENGTH_LONG).show();
            }else{
                if(isLocationDefaultNotSet()){
                    Timber.v("Location default not set");
                    checkRequestPermissionsAndSetDefaults();
                }else{
                    if(mConfigurationChanged){
                        Timber.v("Need not load places");
                        showRecyclerViewAndHideProgressBar();
                        placeAdapter.fillPlacesData(placesArray);
                        mPlacesRecyclerView.getLayoutManager().onRestoreInstanceState(layoutManagerSavedState);
                    }else{
                        loadPlaces(mPlaceType, "");
                    }
                }

                loadMobileAds();
            }
        }
        UpdatePlacesWidgetService.startFavoritePlacesIntentService(getActivity(),
                ExtractionUtils.getPlacesNamesListFromCursor(getActivity()));


        Timber.v("Leaving onCreateView (Context : " + getActivity() + " )" );

        return view;
    }

    private void showFavoritePlaces() {
        Timber.v("Starting Loader for loading Favorite CustomPlace");
        setupItemTouchHelperCallback();
        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(mPlacesRecyclerView);

        getActivity().getSupportLoaderManager().restartLoader(FAVORITES_LOADER_ID, null, this);
    }

    public void loadPlaces(String type, String rankBy) {
        showProgressBarAndHideRecyclerView();
        Map<String, String> preferenceMap = getSharedPreferences(type, rankBy);
        Call<GooglePlacesAPIResponse> recipesList = NetworkUtils.getPlaces(preferenceMap);
        Timber.v("Request: %s", recipesList.request().url());
        //Using enqueue because data is supposed to be loaded on another thread (asynchronously)
        recipesList.enqueue(new Callback<GooglePlacesAPIResponse>() {
            @Override
            public void onResponse(Call<GooglePlacesAPIResponse> call, Response<GooglePlacesAPIResponse> response) {
                GooglePlacesAPIResponse googlePlacesAPIResponse = response.body();
                Timber.v("CustomPlace size: %s", googlePlacesAPIResponse.getResults().size());
                if (googlePlacesAPIResponse.getStatus().equalsIgnoreCase(Constants.OK)) {
                    Timber.v("CustomPlace API response : OK");
                    getPlaceDataFromPlaceID(googlePlacesAPIResponse);
                } else {
                    Timber.v("CustomPlace API response : %s", googlePlacesAPIResponse.getStatus());
                }

                if(googlePlacesAPIResponse.getResults().isEmpty()){
                    placeAdapter.fillPlacesData(null);
                    mNoPlacesExplanationView.setVisibility(View.VISIBLE);
                    mNoPlacesExplanationView.setText(Constants.NO_PLACES_EXPLANATION_TEXT);
                }else {
                    mNoPlacesExplanationView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(Call<GooglePlacesAPIResponse> call, Throwable throwable) {
                Timber.v("In onFailure : %s", throwable.getMessage());
                Timber.v("Stack Trace : %s", throwable.getStackTrace());
                Toast.makeText(getActivity(), getString(R.string.places_load_failed), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getPlaceDataFromPlaceID(GooglePlacesAPIResponse googlePlacesAPIResponse) {
        GeoDataClient mGeoDataClient;

        Timber.v("In getPlaceDataFromPlaceID");
        if (getActivity() != null) {
            mGeoDataClient = Places.getGeoDataClient(getActivity());
            Timber.v("GeoDataClient : %s", mGeoDataClient.toString());
            mGeoDataClient.getPlaceById(getPlaceIdsAsArray(googlePlacesAPIResponse)).addOnCompleteListener(new OnCompleteListener<PlaceBufferResponse>() {

                @Override
                public void onComplete(@NonNull Task<PlaceBufferResponse> task) {
                    Timber.v("In OnCompleteListener's onComplete. Task successful : %s", task.isSuccessful());
                    showRecyclerViewAndHideProgressBar();
                    if (task.isSuccessful()) {
                        PlaceBufferResponse places = task.getResult();
                        Timber.v("CustomPlace count: %s", places.getCount());

                        placesArray  = new ArrayList<>();
                        CustomPlace customPlace;
                        for (Place place : places) {
                            //Timber.v("Place found: %s", place.getName());

                            customPlace = new CustomPlace(place.getId(),
                                String.valueOf(place.getName()),
                                String.valueOf(place.getRating()),
                                String.valueOf(place.getPriceLevel()),
                                String.valueOf(place.getAddress()),
                                String.valueOf(place.getPhoneNumber()),
                                String.valueOf(place.getWebsiteUri()),
                                ExtractionUtils.getFormattedLocationString(place.getLatLng()));
                            placesArray.add(customPlace);
                        }
                        placeAdapter.fillPlacesData(placesArray);
                    } else {
                        Timber.e("getPlaceById operation failed.");
                        mNoPlacesExplanationView.setVisibility(View.VISIBLE);
                        mNoPlacesExplanationView.setText(Constants.PLACES_LOAD_FAILED);
                        Toast.makeText(getActivity(), getString(R.string.operation_failed), Toast.LENGTH_LONG).show();
                    }
                }
            });
        }else Timber.e("Activity is NULL");
    }

    private void showRecyclerViewAndHideProgressBar() {
        mPlacesRecyclerView.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    private void showProgressBarAndHideRecyclerView() {
        mPlacesRecyclerView.setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private String[] getPlaceIdsAsArray(GooglePlacesAPIResponse googlePlacesAPIResponse) {
        List<String> placeIDs = new ArrayList<>();
        for (int i = 0; i < googlePlacesAPIResponse.getResults().size(); i++) {
            Timber.v("%s : ID: %s", i, googlePlacesAPIResponse.getResults().get(i).getPlaceId());
            placeIDs.add(googlePlacesAPIResponse.getResults().get(i).getPlaceId());
        }
        return placeIDs.toArray(new String[placeIDs.size()]);
    }


    private Map<String, String> getSharedPreferences(String selectedPlaceType, String rankBy){

        Timber.v("Retrieving Shared Preferences:");
        Map<String, String> preferenceMap = new HashMap<>();

        Integer defaultRadius = Integer.valueOf(getString(R.string.radius_default));
        Integer radius = PreferenceManager.getDefaultSharedPreferences(getActivity()).
                getInt(getString(R.string.radius_seekbar_key), defaultRadius);


        String location = getLocationData(Constants.LOCATION_COORDINATES);

        Bundle bundle = getApplicationInfoBundle();

        String apiKey = bundle.getString(Constants.PLACES_API_KEY_FROM_MANIFEST);
        Timber.v("API Key : %s", apiKey);

        if(TextUtils.isEmpty(rankBy) || rankBy.equalsIgnoreCase(Constants.PROMINENCE)){
            preferenceMap.put(Constants.RADIUS, String.valueOf(radius*Constants.RADIUS_MULTIPLIER));
        }else if(rankBy.equalsIgnoreCase(Constants.DISTANCE)){
            preferenceMap.put(Constants.RANK_BY, Constants.DISTANCE);
        }
        preferenceMap.put(Constants.LOCATION, location);
        preferenceMap.put(Constants.TYPE_OF_PLACE, selectedPlaceType);
        preferenceMap.put(Constants.PLACES_API_KEY, apiKey);



        Timber.v("Query Map : %s", preferenceMap);

        return preferenceMap;
    }

    private Bundle getApplicationInfoBundle() {


        ApplicationInfo applicationInfo;
        try {

            applicationInfo = getActivity().getPackageManager().getApplicationInfo(
                    getActivity().getPackageName(), PackageManager.GET_META_DATA);

            return applicationInfo.metaData;

        } catch (NullPointerException e){
            e.printStackTrace();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }


    private boolean isLocationDefaultNotSet(){

        /*String defaultLocation = findPreference(getString(R.string.pref_location_key))
                .getSharedPreferences().getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));*/

        String defaultLocation = getLocationData(Constants.LOCATION_NAME);

        Timber.v("Default Location : %s", defaultLocation);

        return defaultLocation.equalsIgnoreCase(getString(R.string.pref_location_default));
    }

    private void checkRequestPermissionsAndSetDefaults() {
        Timber.d("Entering checkRequestPermissionsAndSetDefaults");

        //Checking and Requesting ACCESS_FINE_LOCATION Permissions.
        if (ContextCompat.checkSelfPermission(getActivity().getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Timber.v("Location Permissions : %s", mLocationPermissionGranted);
            mLocationPermissionGranted = true;
            getCurrentPlaceAndSetAsDefault();

        } else {
            Timber.v("Location Permissions : %s. Requesting Location Permission...", mLocationPermissionGranted);

            //Targeting API 23 where Runtime permissions were introduced
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        Constants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
        }

        Timber.d("Leaving checkRequestPermissionsAndSetDefaults");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        Timber.v("Entering onRequestPermissionsResult");
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case Constants.PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }else{
                    mLocationPermissionGranted = false;
                    Timber.v("Location Permission request denied by the User.");
                }
            }
        }
        if (mLocationPermissionGranted) {
            getCurrentPlaceAndSetAsDefault();
        }else{
            Toast.makeText(getActivity(), getString(R.string.select_location), Toast.LENGTH_LONG).show();
        }
        Timber.v("Leaving onRequestPermissionsResult");

    }

    private void getCurrentPlaceAndSetAsDefault(){

        PlaceDetectionClient mPlaceDetectionClient = Places.getPlaceDetectionClient(getActivity());

        @SuppressLint("MissingPermission")
        Task<PlaceLikelihoodBufferResponse> placeResult = mPlaceDetectionClient.getCurrentPlace(null);

        placeResult.addOnCompleteListener(new OnCompleteListener<PlaceLikelihoodBufferResponse>() {
            @Override
            public void onComplete(@NonNull Task<PlaceLikelihoodBufferResponse> task) {
                Timber.v("Is Task successful : %s", task.isSuccessful());
                if(task.isSuccessful()){
                    PlaceLikelihoodBufferResponse likelyPlaces = task.getResult();
                    //Timber.v("Likely CustomPlace Count : %s", likelyPlaces.getCount());

                    PlaceLikelihood placeLikelihood = likelyPlaces.get(0);
                    if(likelyPlaces.getCount()!=0){
                        Timber.v("Place : " + placeLikelihood.getPlace().getName()
                                + " has likelihood: " + placeLikelihood.getLikelihood());
                        editLocationDefault(placeLikelihood.getPlace());
                    }
                    likelyPlaces.release();
                }else{
                    Toast.makeText(getActivity(), getString(R.string.operation_failed), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void editLocationDefault(Place currentPlace) {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPreferences.edit().putString(getString(R.string.pref_location_key), prepareLocationData(currentPlace)).apply();

        loadPlaces(mPlaceType, "");
    }

    private String getFormattedLocationString(LatLng latLng) {
        return latLng.latitude + "," + latLng.longitude;
    }

    public String prepareLocationData(Place currentPlace) {
        return String.valueOf(currentPlace.getAddress())
                + Constants.LOCATION_SPECIFICS_SAPERATOR
                + getFormattedLocationString(currentPlace.getLatLng());
    }

    public String getLocationData(String position){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String preferredLocation = sharedPreferences.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        if(preferredLocation.contains(Constants.LOCATION_SPECIFICS_SAPERATOR)){
            String[] preferredLocationSpecifics = preferredLocation.split(Constants.LOCATION_SPECIFICS_SAPERATOR);
            if(position.equalsIgnoreCase(Constants.LOCATION_NAME)){
                return preferredLocationSpecifics[0];
            }
            if(position.equalsIgnoreCase(Constants.LOCATION_COORDINATES)){
                return preferredLocationSpecifics[1];
            }
        }
        return getString(R.string.pref_location_default);
    }

    private void loadMobileAds(){
        Bundle applicationInfoBundle = getApplicationInfoBundle();

        String adMobAppId = applicationInfoBundle.getString(Constants.ADMOB_APP_ID_FROM_MANIFEST);
        Timber.v("ADMob API Key : %s", adMobAppId);
        MobileAds.initialize(getActivity(), adMobAppId);

        AdRequest adRequest = new AdRequest.Builder()
                .build();
        mAdView.loadAd(adRequest);
        Timber.v("Is test device : %s", adRequest.isTestDevice(getActivity()));
    }


    private void setupItemTouchHelperCallback() {
        itemTouchHelperCallback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

                Timber.v("Item Swipe detected.");
                if (viewHolder instanceof PlacesAdapter.PlaceViewHolder) {
                    PlacesAdapter.PlaceViewHolder placeViewHolder = (PlacesAdapter.PlaceViewHolder) viewHolder;
                    String selection = Favorites.COLUMN_PLACE_ID + "= ?";
                    String[] selectionArgs = {placeViewHolder.placeId};

                    int rowsDeleted = getActivity().getContentResolver().delete(Favorites.PLACES_CONTENT_URI,
                            selection,
                            selectionArgs);
                    if (rowsDeleted != 0) {
                        Timber.v("Rows deleted : %s", rowsDeleted);
                        Cursor cursor = getActivity().getContentResolver().query(Favorites.PLACES_CONTENT_URI,
                                null,
                                null,
                                null,
                                null);
                        if(cursor.getCount() == 0){
                            mNoPlacesExplanationView.setVisibility(View.VISIBLE);
                            mNoPlacesExplanationView.setText(Constants.NO_FAVORITES_EXPLANATION_TEXT);
                        }else{
                            placeAdapter.setCursor(cursor);
                        }
                        UpdatePlacesWidgetService.startFavoritePlacesIntentService(getActivity(),
                                ExtractionUtils.getPlacesNamesListFromCursor(getActivity()));
                    }
                }

            }
        };
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Timber.v("onSaveInstanceState");
        outState.putBoolean(Constants.CONFIGURATION_CHANGED, true);
        outState.putParcelable(Constants.RECYCLER_VIEW_STATE, mPlacesRecyclerView.getLayoutManager().onSaveInstanceState());
        outState.putParcelable(Constants.SAVED_PLACES_ARRAY, Parcels.wrap(placesArray));
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        Timber.v("onViewStateRestored");

        if(savedInstanceState != null){
            if(savedInstanceState.containsKey(Constants.RECYCLER_VIEW_STATE)){
                layoutManagerSavedState = savedInstanceState.getParcelable(Constants.RECYCLER_VIEW_STATE);
                mPlacesRecyclerView.getLayoutManager().onRestoreInstanceState(layoutManagerSavedState);
                Timber.v("layoutManagerSavedState in onViewStateRestored : " + layoutManagerSavedState);

            }
        }

    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {
        Timber.d("Entering onCreateLoader");

        Timber.v("LOADER ID : %s", loaderID);
        Loader loader = null;
        switch (loaderID) {
            case FAVORITES_LOADER_ID:
                loader = new FavoriteAsyncTaskLoader(getActivity(), placeAdapter);
                break;
            default:
        }
        Timber.d("Leaving onCreateLoader");
        return loader;
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {

        Timber.d("Entering onLoadFinished");
        Timber.v("LOADER ID : %s", loader.getId());

        if (cursor == null) {
            Timber.v("No CustomPlace to show");
            return;
        }

        switch (loader.getId()) {
            case FAVORITES_LOADER_ID:

                saveCursor = cursor;

                Timber.v("Cursor length : %s", cursor.getCount());
                placeAdapter.setCursor(cursor);
                mPlacesRecyclerView.getLayoutManager().onRestoreInstanceState(layoutManagerSavedState);
                if (cursor.getCount() == 0) {
                    mNoPlacesExplanationView.setVisibility(View.VISIBLE);
                    mNoPlacesExplanationView.setText(Constants.NO_FAVORITES_EXPLANATION_TEXT);
                } else {
                    mNoPlacesExplanationView.setVisibility(View.GONE);
                }
                break;
            default:
        }
        Timber.d("Leaving onLoadFinished");
    }

    @Override
    public void onLoaderReset(Loader loader) {
        Timber.d("Entering onLoaderReset");
        Timber.d("Leaving onLoaderReset");
    }

}
