package comp5216.sydney.edu.au.mediaaccess;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    MarshmallowPermission marshmallowPermission = new MarshmallowPermission(this);
    public final String APP_TAG = "MobileComputingTutorial";
    public String photoFileName = "photo.jpg";
    public String videoFileName = "video.mp4";
    public String audioFileName = "audio.3gp";

    Switch autoBackupSwitch;
    Switch saveBatterySwitch;
    Switch saveNetworkSwitch;
    Button backupButton;

    //request codes
    private static final int MY_PERMISSIONS_REQUEST_OPEN_CAMERA = 101;
    private static final int MY_PERMISSIONS_REQUEST_READ_PHOTOS = 102;
    private static final int MY_PERMISSIONS_REQUEST_RECORD_VIDEO = 103;
    private static final int MY_PERMISSIONS_REQUEST_READ_VIDEOS = 104;
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 105;
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 106;

    private File file;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private Location mLastKnownLocation = new Location("");
    private String cityName = "Unknown";

//    FirebaseFirestore mFirebasestore;
    FirebaseStorage mFirebaseStorage;
    StorageReference storageReference;

    MediaItemDao mediaItemDao;
    MediaItemDB db;

    Boolean isAutoBackup = true;
    Boolean saveBattery = false;
    Boolean saveNetwork = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFirebaseStorage = FirebaseStorage.getInstance();
        storageReference = mFirebaseStorage.getReference();

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        autoBackupSwitch = findViewById(R.id.autoBackupSwitch);
        autoBackupSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAutoBackup = isChecked;
            Log.d("AutoBackup", "Auto backup is " + isAutoBackup);
        });
        backupButton = findViewById(R.id.backupButton);

        saveBatterySwitch = findViewById(R.id.batteryModeSwitch);
        saveBatterySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveBattery = isChecked;
            Log.d("SaveBattery", "Save battery is " + saveBattery);
            if(saveBattery) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.battery_title)
                        .setMessage(R.string.battery_msg)
                        .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                            // Nothing happens
                        })
                        .setNegativeButton(R.string.no, (dialogInterface, i) -> {
                            saveBatterySwitch.setChecked(false);
                            saveBattery = false;
                        });
                builder.create().show();
            }
        });

        saveNetworkSwitch = findViewById(R.id.wifiModeSwitch);
        saveNetworkSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveNetwork = isChecked;
            Log.d("SaveNetwork", "Save network is " + saveNetwork);
            if(saveNetwork) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.network_title)
                        .setMessage(R.string.network_msg)
                        .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                            // Nothing happens
                        })
                        .setNegativeButton(R.string.no, (dialogInterface, i) -> {
                            saveNetworkSwitch.setChecked(false);
                            saveNetwork = false;
                        });
                builder.create().show();
            }
        });

        if(!marshmallowPermission.checkPermissionForLocation()){
            marshmallowPermission.requestPermissionForLocation();
        }
        mFusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(this);

        getDeviceLocation();

        //Initialize the database
        db = MediaItemDB.getDatabase(this.getApplication().getApplicationContext());
        mediaItemDao = db.mediaItemDao();

    }

    // Returns the Uri for a photo/media stored on disk given the fileName and type
    public Uri getFileUri(String fileName, int type) {
        Uri fileUri = null;
        try {
            String typestr = "images"; //default to images type
            if (type == 1) {
                typestr = "videos";
            } else if (type != 0) {
                typestr = "audios";
            }
            // Get safe media storage directory depending on type
            File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), cityName);
//            File mediaStorageDir = new File(Environment.DIRECTORY_PICTURES, APP_TAG + "/" + typestr + "/" + cityName);

//            File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
//                    Environment.DIRECTORY_PICTURES), APP_TAG);
//            File mediaStorageDir = new File(Environment.DIRECTORY_PICTURES, APP_TAG);
            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists()) {
                mediaStorageDir.mkdirs();
            }
            // Create the file target for the media based on filename
            file = new File(mediaStorageDir, fileName);
            // Wrap File object into a content provider, required for API >= 24
            // See https://guides.codepath.com/android/Sharing-Content-withIntents#sharing-files-with-api-24-or-higher
            Log.i("getFileUri", file.getAbsolutePath());
            if (Build.VERSION.SDK_INT >= 24) {
                fileUri = FileProvider.getUriForFile(
                        this.getApplicationContext(),
                        "au.edu.sydney.comp5216.mediaaccess.fileProvider", file);
            } else {
                fileUri = Uri.fromFile(mediaStorageDir);
            }
        } catch (Exception ex) {
            Log.d("getFileUri", ex.getStackTrace().toString());
        }
        return fileUri;
    }

    public void onTakePhotoClick(View v) {
        // Check permissions
        if (!marshmallowPermission.checkPermissionForCamera()) {
            marshmallowPermission.requestPermissionForCamera();
        }
        else {
            // create Intent to take a picture and return control to the calling application
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // set file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.getDefault()).format(new Date());
            photoFileName = "IMG_" + timeStamp + ".jpg";
            // Create a photo file reference
            Uri file_uri = getFileUri(photoFileName, 0);
            // Add extended data to the intent
            intent.putExtra(MediaStore.EXTRA_OUTPUT, file_uri);
            // If you call startActivityForResult() using an intent that no app can handle, your app will crash.
            // So as long as the result is not null, it's safe to use the intent.

            if (intent.resolveActivity(getPackageManager()) != null) {
                // Start the image capture intent to take photo
                startActivityForResult(intent, MY_PERMISSIONS_REQUEST_OPEN_CAMERA);
            }
        }
    }



    public void onLoadPhotoClick(View view) {
//        if(!marshmallowPermission.checkPermissionForReadfiles()){
//            marshmallowPermission.requestPermissionForReadfiles();
//        }
//        else{
            // Create intent for picking a photo from the gallery
            Intent intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            // Bring up gallery to select a photo
            startActivityForResult(intent, MY_PERMISSIONS_REQUEST_READ_PHOTOS);
//        }
    }

    public void onLoadVideoClick(View view) {
        // Create intent for picking a video from the gallery
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        // Bring up gallery to select a video
        startActivityForResult(intent, MY_PERMISSIONS_REQUEST_READ_VIDEOS);
    }

    public void onRecordVideoClick(View v) {
        // Check permissions
        if (!marshmallowPermission.checkPermissionForCamera()) {
            marshmallowPermission.requestPermissionForCamera();
        } else {
            // create Intent to capture a video and return control to the calling application
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

            // set file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.getDefault()).format(new Date());
            videoFileName = "VIDEO_" + timeStamp + ".mp4";

            // Create a video file reference
            Uri file_uri = getFileUri(videoFileName, 1);

            // add extended data to the intent
            intent.putExtra(MediaStore.EXTRA_OUTPUT, file_uri);

            // Start the video record intent to capture video
            startActivityForResult(intent, MY_PERMISSIONS_REQUEST_RECORD_VIDEO);
        }
    }

    //Backup all non-backup media files to Firebase Storage
    @SuppressLint("SetTextI18n")
    public void onBackupClick(View view) {
        backupButton.setEnabled(false);
        backupButton.setText("Uploading...");

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // Get non-backup all media files from the local database
            List<MediaItem> mediaItems = mediaItemDao.listAllNotBackup();
            List<UploadTask> uploadTasks = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(mediaItems.size());  // 为每个媒体文件创建一个计数

            for (MediaItem mediaItem : mediaItems) {
                final StorageReference fileRef = storageReference.child(mediaItem.getFileType() + "/" + mediaItem.getCity() + "/" + mediaItem.getFileName());

                // Check if the file exists before uploading
                fileRef.getMetadata().addOnSuccessListener(storageMetadata -> {
                    // File already exists, skip uploading
//                    mediaItemDao.updateBackup(mediaItem.getID());
                    Executors.newSingleThreadExecutor().execute(() -> {
                        mediaItemDao.updateBackup(mediaItem.getID());
                        Log.d("Upload", mediaItem.getFileName() + " already exists, skipping upload.");
                    });
                    latch.countDown();
                }).addOnFailureListener(exception -> {
                    // File does not exist, proceed with upload
                    UploadTask uploadTask = fileRef.putFile(Uri.fromFile(new File(mediaItem.getLocalPath())));
                    uploadTasks.add(uploadTask);
                    Log.d("Upload", "Uploading " + mediaItem.getFileName());

                    // Optionally handle individual file upload success/failure
                    uploadTask.addOnSuccessListener(taskSnapshot ->{
                                        Log.d("Upload",
                                                mediaItem.getFileName() + " uploaded successfully.");
                                        latch.countDown();
                                    }


                            )
                            .addOnFailureListener(exception1 ->{
                                        Log.e("Upload",
                                                "Error uploading " + mediaItem.getFileName() +
                                                        ": " + exception1.getMessage());
                                        latch.countDown();
                                    }

                            );
                });
            }
            try {
                latch.await();  // wait for all uploads to complete
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // Check if there are files to upload
            if (uploadTasks.isEmpty()) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "No files to upload.", Toast.LENGTH_SHORT).show();
                    backupButton.setEnabled(true);
                    backupButton.setText("Backup Manually");
                });
                Log.d("Upload", "No files to upload.");
                return;
            }

            // Handle all uploads after the loop
            Log.d("Upload", "Uploaded " + uploadTasks.size() + " files.");
            Tasks.whenAllSuccess(uploadTasks)
                    .addOnSuccessListener(results ->
                            {
                                Log.d("Upload", uploadTasks.size() + "files uploaded successfully.");
                                Toast.makeText(MainActivity.this,
                                        "All files uploaded successfully.",
                                        Toast.LENGTH_SHORT).show();
                                for (MediaItem mediaItem : mediaItems) {
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        mediaItemDao.updateBackup(mediaItem.getID());
                                    });
                                }
                                backupButton.setEnabled(true);
                                backupButton.setText("Backup Manually");
                            }
                    )
                    .addOnFailureListener(exception ->
                            {
                                Log.e("Upload",
                                        "Error uploading files: " + exception.getMessage());
                                Toast.makeText(MainActivity.this,
                                        "Error uploading files failed, please try again.",
                                        Toast.LENGTH_SHORT).show();
                                backupButton.setEnabled(true);
                                backupButton.setText("Backup Manually");
                            }
                    );

        });
    }

    private void getDeviceLocation() {
        try {
            if (marshmallowPermission.checkPermissionForLocation()) {
                Task<Location> locationResult =
                        mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Obtain the current location of the device
                        mLastKnownLocation = task.getResult();

                        // get latitude and longitude
                        double latitude = mLastKnownLocation.getLatitude();
                        double longitude = mLastKnownLocation.getLongitude();
                        Log.d("Location", "Latitude: " + latitude + " Longitude: " + longitude);
                        // use Geocoder to covert latitude and longitude to city name
                        Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
                        try {
                            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                            if (addresses != null && addresses.size() > 0) {
                                String city = addresses.get(0).getLocality();
                                cityName = city.replaceAll("\\s+", "_");
                                Log.d("Location", "Current city: " + cityName);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.d("Location", "Can't get current location.");
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    public void autoBackupToFirebase(File file, int type) {
        getDeviceLocation();
        String typestr = "images"; //type 0 is images, 1 is videos
        String fileName = file.getName();
        if (type == 1) {
            typestr = "videos";
        }
        MediaItem mediaItem = new MediaItem(
                fileName,
                file.getAbsolutePath(),
                typestr,
                cityName,
                true);
        if(saveBattery || saveNetwork) {
            // Check if the device is charging
            IntentFilter chargefilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = this.registerReceiver(null, chargefilter);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;
            Log.d("AutoBackup", "Is charging: " + isCharging);
            //Check if the device battery is below 50%
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level / (float) scale;
            if (saveBattery && batteryPct < 0.5 && !isCharging) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // add to the database
                    mediaItem.setBackup(false);
                    mediaItemDao.insert(mediaItem);
                });
                try {
                    future.get();
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                Log.d("AutoBackup", "On power saving mode. Battery is below 50% and no Charging, skipping backup.");
                return;
            }

            //Check if the device is in power saving mode
            if (saveBattery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                if (powerManager.isPowerSaveMode()) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        // add to the database
                        mediaItem.setBackup(false);
                        mediaItemDao.insert(mediaItem);
                    });
                    try {
                        future.get();
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    Log.d("AutoBackup", "On power saving mode. Device is in power saving mode, skipping backup.");
                    return;
                }
            }

            // Check if the device is connected to a Wi-Fi network
            ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (saveNetwork && !wifi.isConnected()) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // add to the database
                    mediaItem.setBackup(false);
                    mediaItemDao.insert(mediaItem);
                });
                try {
                    future.get();
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                Log.d("AutoBackup", "On flow saving mode. Device is not connected to Wi-Fi, skipping backup.");
                return;
            }
        }
        // Create a storage reference from our app
        StorageReference storageRef = storageReference.child(typestr + "/" + cityName + "/" + file.getName());
        // Upload file to Firebase Storage
        storageRef.putFile(Uri.fromFile(file))
                .addOnSuccessListener(taskSnapshot -> {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        // add to the database
                        mediaItemDao.insert(mediaItem);
                    });
                    try {
                        future.get();
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    // Get a URL to the uploaded content
                    Toast.makeText(MainActivity.this, "Upload successful", Toast.LENGTH_SHORT).show();
                    Log.d("Firebase", "Upload successful");
                })
                .addOnFailureListener(exception -> {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        // add to the database
                        mediaItem.setBackup(false);
                        mediaItemDao.insert(mediaItem);
                    });
                    try {
                        future.get();
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    // Handle unsuccessful uploads
                    Toast.makeText(MainActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                    Log.d("Firebase", "Upload failed");
                });

    }

    private void scanFile(String path) {

        MediaScannerConnection.scanFile(MainActivity.this,
                new String[] { path }, null,
                (path1, uri) -> Log.i("TAG", "Finished scanning " + path1));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final VideoView mVideoView = findViewById(R.id.videoview);
        ImageView ivPreview = findViewById(R.id.photopreview);
        mVideoView.setVisibility(View.GONE);
        ivPreview.setVisibility(View.GONE);
        if (requestCode == MY_PERMISSIONS_REQUEST_OPEN_CAMERA) {
            Log.i("resultCode", String.valueOf(resultCode));
            if (resultCode == RESULT_OK) {
                // by this point we have the camera photo on disk
                Bitmap takenImage = BitmapFactory.decodeFile(file.getAbsolutePath());
                // scan the image to make it appear in the gallery
                scanFile(file.getAbsolutePath());
                // Load the taken image into a preview
                ivPreview.setImageBitmap(takenImage);
                ivPreview.setVisibility(View.VISIBLE);
                if(isAutoBackup){
                    autoBackupToFirebase(file, 0);
                }
                else {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        MediaItem mediaItem = new MediaItem(
                                file.getName(),
                                file.getAbsolutePath(),
                                "images",
                                cityName,
                                false);
                        // add to the database
                        mediaItemDao.insert(mediaItem);
                    });
                    try {
                        future.get();
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else { // Result was a failure
                Toast.makeText(this, "Picture wasn't taken AAA!",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == MY_PERMISSIONS_REQUEST_READ_PHOTOS) {
            if (resultCode == RESULT_OK) {
                Uri photoUri = data.getData();
                Bitmap selectedImage;
                try {
                    selectedImage = MediaStore.Images.Media.getBitmap(
                            this.getContentResolver(), photoUri);
                    ivPreview.setImageBitmap(selectedImage);
                    ivPreview.setVisibility(View.VISIBLE);
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else if (requestCode == MY_PERMISSIONS_REQUEST_READ_VIDEOS) {
            if (resultCode == RESULT_OK) {
                Uri videoUri = data.getData();
                mVideoView.setVisibility(View.VISIBLE);
                mVideoView.setVideoURI(videoUri);
                mVideoView.requestFocus();
                // Close the progress bar and play the video
                mVideoView.setOnPreparedListener(mp -> mVideoView.start());
            }
        } else if (requestCode == MY_PERMISSIONS_REQUEST_RECORD_VIDEO) {
            //if you are running on emulator remove the if statement
            if (resultCode == RESULT_OK) {
                Uri takenVideoUri = getFileUri(videoFileName, 1);
                // scan the image to make it appear in the gallery
                scanFile(file.getAbsolutePath());
                mVideoView.setVisibility(View.VISIBLE);
                mVideoView.setVideoURI(takenVideoUri);
                mVideoView.requestFocus();
                // Close the progress bar and play the video
                mVideoView.setOnPreparedListener(mp -> mVideoView.start());
                if(isAutoBackup){
                    autoBackupToFirebase(file, 1);
                }
                else {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        MediaItem mediaItem = new MediaItem(
                                file.getName(),
                                file.getAbsolutePath(),
                                "videos",
                                cityName,
                                false);
                        // add to the database
                        mediaItemDao.insert(mediaItem);
                    });
                    try {
                        future.get();
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
